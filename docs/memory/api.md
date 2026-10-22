# Memory 接口说明（M1）

> 本文随 M1 各任务逐步补充。当前包含稳定错误码、两个聊天入口（`Idempotency-Key`、409 turnId、流内错误事件、重放）与历史消息分页（Task 4）；附件与删除接口在 Task 6/7 补充。设计依据见 `docs/superpowers/specs/2026-10-21-memory-runtime-design.md`，执行计划见 `docs/superpowers/plans/2026-10-21-memory-persistence-phase-1.md`。

## 错误码

错误响应只返回稳定错误码和通用说明，不包含底层异常信息、服务器路径或凭证。代码定义见 `agent-server/src/main/java/io/github/sukimiya/agileagent/memory/MemoryException.java`。

| 错误码 | HTTP | 含义 |
| --- | --- | --- |
| `INVALID_ARGUMENT` | 400 | 请求参数不合法，例如历史分页 `limit` 不在 1–200 内 |
| `INVALID_CONVERSATION_ID` | 400 | 会话 ID 不是小写规范 UUID（36 字符）；不做替换或修剪 |
| `INVALID_IDEMPOTENCY_KEY` | 400 | `Idempotency-Key` 不符合格式：长度 1–128，仅允许 ASCII 字母数字和 `._:-` |
| `TENANT_REQUIRED` | 403 | 服务端身份无法解析租户，不能创建会话、读取记忆或访问附件 |
| `CONVERSATION_NOT_FOUND` | 404 | 会话不存在、属于他人、已删除或为旧版本无租户数据；不区分具体原因 |
| `IDEMPOTENCY_CONFLICT` | 409 | 同一会话内同一 `Idempotency-Key` 对应不同输入 |
| `TURN_IN_PROGRESS` | 409 | 该会话已有活跃执行；响应体带活跃 `turnId`，前端显示“正在生成” |
| `TURN_TERMINATED` | 409 | 同一 key 的执行已失败/取消/中断；新执行需使用新 key |
| `TURN_INTERRUPTED` | SSE error 事件 | 流式执行中失去执行权（租约过期或 fence 失效，例如恢复扫描或会话删除）；以该错误事件报告失败（其后的 done 只是协议终止符，不表示成功），执行终态为 INTERRUPTED |
| `TURN_FAILED` | SSE error 事件 | 流式执行普通失败：模型/工具错误、超时、上游未给出 Completed；执行终态为 FAILED |
| `MESSAGE_TOO_LARGE` | 413 | 单条消息（序列化后的 JSON 文本，含工具记录）按 UTF-8 字节超过 `app.memory.max-message-bytes`，拒绝而不截断 |
| `STORAGE_UNAVAILABLE` | 503 | 数据库不可用，无法可靠保存；不会静默降级为仅内存会话 |
| `INSTANCE_CONFLICT` | 503 | 检测到同一部署命名空间内另一个活跃实例，拒绝接受新执行 |

缺少模型配置仍返回既有的 428 JSON，不属于上表。`TURN_INTERRUPTED`、`TURN_FAILED` 不是 HTTP 状态：响应头已提交，只能以流内错误事件告知。

HTTP 错误响应体统一为 `{"code": "...", "message": "..."}`；仅 `TURN_IN_PROGRESS` 额外带 `"turnId"`。

## 聊天入口（`POST /api/chat`、`POST /api/chat/completions`）

两个入口共用同一接受流程，顺序固定：

1. 从服务端身份解析租户与 subject，缺租户返回 403 `TENANT_REQUIRED`。
2. 校验可选请求头 `Idempotency-Key`（长度 1–128，仅 ASCII 字母数字和 `._:-`），不合法返回 400 `INVALID_IDEMPOTENCY_KEY`；未带时服务端生成新的请求 ID，不凭相同文本去重。
3. 只读检查会话：`conversationId` 未被使用（将在接受时创建）或属于当前用户则继续；属于他人、旧版本无租户数据或已删除返回 404 `CONVERSATION_NOT_FOUND`。未带 `conversationId` 时服务端生成新会话。
4. 解析租户模型配置；缺失返回 428 `LLM_CONFIG_REQUIRED`，此时不写入任何会话、执行或消息。
5. 在一个事务内保存用户消息与 ACCEPTED 执行，然后才返回 200 SSE。冲突返回 409（见下文），超限 413，写库失败 503。

`Idempotency-Key` 承诺范围：在已建立的会话内（请求带已存在且属于当前用户的 `conversationId`），同一 key 不会被接受两次：

- 相同 key + 相同文本，且原执行已完成：不调用模型，重放已存回答——`/api/chat` 输出一条 `delta`、已观察到的 `usage` 和 `done`；`/api/chat/completions` 输出一条内容块、usage 块和 `[DONE]`。
- 相同 key + 相同文本，原执行仍在进行：409 `TURN_IN_PROGRESS`，带 `turnId`。
- 相同 key + 不同文本：409 `IDEMPOTENCY_CONFLICT`。
- 相同 key，原执行已失败/取消/中断：409 `TURN_TERMINATED`。
- 未带 `conversationId` 的首个请求重试会进入新会话并再次执行（已知问题，生产上线门槛，见 spec 第 13 节）。

### 流式事件

事件格式与此前一致：控制行 `{"type":"thinking"|"tool_call"|"delta"|"usage"|"done", ...}` 与一行最终正文。新增内容只有错误事件中的稳定 `code` 字段：

```json
{"type":"error","content":"处理出错","code":"TURN_FAILED"}
```

- 成功：最终正文与 usage（包含 Completed 之后到达的 usage）之后，仅在 COMPLETED 已持久提交后才发送 `done`。
- 失败：发送一条 `error` 事件（`code` 为 `TURN_FAILED`、`TURN_INTERRUPTED`、`MESSAGE_TOO_LARGE`、`STORAGE_UNAVAILABLE`、`TURN_IN_PROGRESS`、`TURN_TERMINATED` 之一），随后发送 `done` 作为协议终止符；客户端必须以是否出现 `error` 判断失败，不能把该 `done` 当作成功。错误事件不包含底层异常信息。
- `/api/chat/completions` 把 `error` 的 `content` 作为普通内容块输出，最后仍输出 `[DONE]`。
- 客户端断开即取消：模型流、能力分派与尚未开始的工具调用都会停止，执行记为 CANCELLED，已生成的部分正文随终态一并保存（收尾写入失败时以最后检查点为准，恢复扫描在租约过期后标记 INTERRUPTED）。同一执行票据不会被第二次启动。

## 历史消息（`GET /api/conversations/{id}/messages`）

- 返回数组，元素为 `{"id","role","content"}`（仅 user/assistant 消息），按 `sequence` 升序；`id` 为稳定的消息 ID。
- 查询参数：`limit` 默认 100，取值 1–200，超出返回 400 `INVALID_ARGUMENT`；`beforeSequence` 可选，读取该序号之前（更早）的消息。
- 响应头：`X-Has-More`（`true`/`false`）表示是否还有更早的消息；`X-Next-Cursor` 为下一页应传的 `beforeSequence`，最早一页不返回该头。两个头已通过 CORS 暴露。
- 会话不存在、属于他人、旧版本无租户数据或已删除返回 404 `CONVERSATION_NOT_FOUND`。

## 执行冲突与终态补充

- 同会话已有活跃执行且租约未过期：返回 409 `TURN_IN_PROGRESS`，带 `turnId`。
- 活跃执行的租约已过期（例如进程退出后无人续期）：新请求先将其标记为 INTERRUPTED，再正常接受，不返回 409。
- 用同一 `Idempotency-Key` 重试已中断、失败或取消的请求返回 409 `TURN_TERMINATED`；需使用新 key。

## 消息大小上限

- 配置项 `app.memory.max-message-bytes`，默认 1 MiB（1048576 字节），按序列化后 JSON 文本的 UTF-8 字节数计算。
- 取值必须大于 0 且不超过 MySQL `MEDIUMTEXT` 容量（16 MiB − 1 字节），否则启动失败。
- 用户输入超限返回 413 `MESSAGE_TOO_LARGE`，不截断保存。
- 模型最终回答超限时，执行记为 FAILED（`error_code=MESSAGE_TOO_LARGE`），不保存回答正文；流式响应发送 `code` 为 `MESSAGE_TOO_LARGE` 的错误事件。该上限只限制单条消息，不截断数据库中已保存的历史，也不替代 M2a 的上下文 Token 预算。
