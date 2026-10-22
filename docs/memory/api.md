# Memory 接口说明（M1）

> 本文随 M1 各任务逐步补充。当前包含稳定错误码；分页、`Idempotency-Key` 承诺范围、409 turnId、附件与删除接口在 Task 4/6/7 补充。设计依据见 `docs/superpowers/specs/2026-10-21-memory-runtime-design.md`，执行计划见 `docs/superpowers/plans/2026-10-21-memory-persistence-phase-1.md`。

## 错误码

错误响应只返回稳定错误码和通用说明，不包含底层异常信息、服务器路径或凭证。代码定义见 `agent-server/src/main/java/io/github/sukimiya/agileagent/memory/MemoryException.java`。

| 错误码 | HTTP | 含义 |
| --- | --- | --- |
| `INVALID_CONVERSATION_ID` | 400 | 会话 ID 不是小写规范 UUID（36 字符）；不做替换或修剪 |
| `INVALID_IDEMPOTENCY_KEY` | 400 | `Idempotency-Key` 不符合格式：长度 1–128，仅允许 ASCII 字母数字和 `._:-` |
| `TENANT_REQUIRED` | 403 | 服务端身份无法解析租户，不能创建会话、读取记忆或访问附件 |
| `CONVERSATION_NOT_FOUND` | 404 | 会话不存在、属于他人、已删除或为旧版本无租户数据；不区分具体原因 |
| `IDEMPOTENCY_CONFLICT` | 409 | 同一会话内同一 `Idempotency-Key` 对应不同输入 |
| `TURN_IN_PROGRESS` | 409 | 该会话已有活跃执行；响应体带活跃 `turnId`，前端显示“正在生成” |
| `TURN_TERMINATED` | 409 | 同一 key 的执行已失败/取消/中断；新执行需使用新 key |
| `TURN_INTERRUPTED` | SSE error 事件 | 流式执行中失去执行权（租约过期或 fence 失效，例如恢复扫描或会话删除）；响应以该错误事件结束，不发送成功的 done，执行终态为 INTERRUPTED（Task 4 实现） |
| `MESSAGE_TOO_LARGE` | 413 | 单条消息（序列化后的 JSON 文本，含工具记录）按 UTF-8 字节超过 `app.memory.max-message-bytes`，拒绝而不截断 |
| `STORAGE_UNAVAILABLE` | 503 | 数据库不可用，无法可靠保存；不会静默降级为仅内存会话 |
| `INSTANCE_CONFLICT` | 503 | 检测到同一部署命名空间内另一个活跃实例，拒绝接受新执行 |

缺少模型配置仍返回既有的 428 JSON，不属于上表。`TURN_INTERRUPTED` 不是 HTTP 状态：响应头已提交，只能以流内错误事件告知。

## 执行冲突与终态补充

- 同会话已有活跃执行且租约未过期：返回 409 `TURN_IN_PROGRESS`，带 `turnId`。
- 活跃执行的租约已过期（例如进程退出后无人续期）：新请求先将其标记为 INTERRUPTED，再正常接受，不返回 409。
- 用同一 `Idempotency-Key` 重试已中断、失败或取消的请求返回 409 `TURN_TERMINATED`；需使用新 key。

## 消息大小上限

- 配置项 `app.memory.max-message-bytes`，默认 1 MiB（1048576 字节），按序列化后 JSON 文本的 UTF-8 字节数计算。
- 取值必须大于 0 且不超过 MySQL `MEDIUMTEXT` 容量（16 MiB − 1 字节），否则启动失败。
- 用户输入超限返回 413 `MESSAGE_TOO_LARGE`，不截断保存。
- 模型最终回答超限时，执行记为 FAILED（`error_code=MESSAGE_TOO_LARGE`），不保存回答正文；流式响应以失败错误事件结束。该上限只限制单条消息，不截断数据库中已保存的历史，也不替代 M2a 的上下文 Token 预算。
