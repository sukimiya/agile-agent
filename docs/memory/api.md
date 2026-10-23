# Memory 接口说明（M1）

> 本文随 M1 各任务逐步补充。当前包含稳定错误码、两个聊天入口（`Idempotency-Key`、409 turnId、流内错误事件、重放）与历史消息分页（Task 4）、工具调用记录（Task 5）、附件上传/下载与会话 ID 规则（Task 6）、会话删除（Task 7）以及单实例保护（Task 8）。设计依据见 `docs/superpowers/specs/2026-10-21-memory-runtime-design.md`，执行计划见 `docs/superpowers/plans/2026-10-21-memory-persistence-phase-1.md`。

## 错误码

错误响应只返回稳定错误码和通用说明，不包含底层异常信息、服务器路径或凭证。代码定义见 `agent-server/src/main/java/io/github/sukimiya/agileagent/memory/MemoryException.java`。

| 错误码 | HTTP | 含义 |
| --- | --- | --- |
| `INVALID_ARGUMENT` | 400 | 请求参数不合法，例如历史分页 `limit` 不在 1–200 内 |
| `INVALID_CONVERSATION_ID` | 400 | 会话 ID 不是小写规范 UUID（36 字符）；不做替换或修剪。两个聊天入口与附件上传/下载使用同一规则 |
| `INVALID_IDEMPOTENCY_KEY` | 400 | `Idempotency-Key` 不符合格式：长度 1–128，仅允许 ASCII 字母数字和 `._:-` |
| `TENANT_REQUIRED` | 403 | 服务端身份无法解析租户，不能创建会话、读取记忆或访问附件 |
| `CONVERSATION_NOT_FOUND` | 404 | 会话不存在、属于他人、已删除或为旧版本无租户数据；不区分具体原因 |
| `IDEMPOTENCY_CONFLICT` | 409 | 同一用户的同一 `Idempotency-Key`（保留期内）对应不同请求：入口、`conversationId`、消息内容或附件 ID 不同 |
| `TURN_IN_PROGRESS` | 409 | 该会话已有活跃执行，或同一 key 的原请求仍在执行；响应体带活跃 `turnId` 及其 `conversationId`，前端显示“正在生成” |
| `TURN_TERMINATED` | 409 | 同一 key 的执行已失败/取消/中断；新执行需使用新 key |
| `TURN_INTERRUPTED` | SSE error 事件 | 流式执行中失去执行权（租约过期或 fence 失效，例如恢复扫描或会话删除）；以该错误事件报告失败（其后的 done 只是协议终止符，不表示成功），执行终态为 INTERRUPTED |
| `TURN_FAILED` | SSE error 事件 | 流式执行普通失败：模型/工具错误、超时、上游未给出 Completed；执行终态为 FAILED |
| `MESSAGE_TOO_LARGE` | 413 | 单条消息（序列化后的 JSON 文本，含工具记录）按 UTF-8 字节超过 `app.memory.max-message-bytes`，拒绝而不截断 |
| `STORAGE_UNAVAILABLE` | 503 | 数据库不可用，无法可靠保存；不会静默降级为仅内存会话 |
| `INSTANCE_CONFLICT` | 503 | 检测到同一部署命名空间内另一个活跃实例，拒绝接受新执行（包括相同 key 的重放）；已在运行的执行正常结束。同时 `/actuator/health` 为 `OUT_OF_SERVICE`（503），见运维说明“单实例保护” |

缺少模型配置仍返回既有的 428 JSON，不属于上表。`TURN_INTERRUPTED`、`TURN_FAILED` 不是 HTTP 状态：响应头已提交，只能以流内错误事件告知。

新版本只读写数据库中的会话与消息，不读取、不迁移旧的 Redis `chatmemory:*` 历史；升级前的对话在新版本中不可见。

HTTP 错误响应体统一为 `{"code": "...", "message": "..."}`；仅 `TURN_IN_PROGRESS` 额外带 `"turnId"` 和 `"conversationId"`。

## 聊天入口（`POST /api/chat`、`POST /api/chat/completions`）

两个入口共用同一接受流程，顺序固定：

0. 请求带 `conversationId` 时，必须是小写规范 UUID（如 `3f2c8a1e-9b4d-4c7a-8e21-5d6f7a8b9c0d`），否则返回 400 `INVALID_CONVERSATION_ID`；大写、带空白、带括号或无连字符的形式一律拒绝，不做转换。未带时，在第 5 步查找 key 之后（且只在没有命中时）才由服务端生成同一格式的新 ID。
1. 从服务端身份解析租户与 subject，缺租户返回 403 `TENANT_REQUIRED`。
2. 校验可选请求头 `Idempotency-Key`（长度 1–128，仅 ASCII 字母数字和 `._:-`），不合法返回 400 `INVALID_IDEMPOTENCY_KEY`；未带 key 的请求都是新请求，不凭相同文本去重。
3. 只读检查会话（仅当请求带 `conversationId`）：未被使用（将在接受时创建）或属于当前用户则继续；属于他人、旧版本无租户数据或已删除返回 404 `CONVERSATION_NOT_FOUND`。
4. 解析租户模型配置；缺失返回 428 `LLM_CONFIG_REQUIRED`，此时不写入任何会话、执行或消息。
5. 在一个事务内：带 key 时先在当前用户范围内查找 key（见下文）；没有命中才确定会话 ID（请求给出的或新生成的），保存用户消息与 ACCEPTED 执行，然后才返回 200 SSE。冲突返回 409（见下文），超限 413，写库失败 503。

`Idempotency-Key` 作用域与保留期：

- **作用域：** key 在 `(tenant, subject, key)` 范围内唯一，与会话无关。不同用户或不同租户使用同一 key 互不影响。未带 `conversationId` 的首个请求带同一 key 重试时，同样能命中原执行，不会新建会话，也不会再次调用模型。
- **请求指纹：** 对入口（`POST /api/chat` 或 `POST /api/chat/completions`）、客户端给出的 `conversationId`（未给出时为空，不使用服务端生成的 ID）、消息内容（`/api/chat` 的 `message`；`/api/chat/completions` 的最后一条消息 `content`）和附件 ID 列表计算 SHA-256。当前两个入口的请求体都不携带附件 ID（附件通过上传绑定到会话），所以这一项暂时恒为空列表。
- 相同 key + 相同指纹，且原执行已完成：不调用模型，重放已存回答，并返回原执行所在会话的内容。`/api/chat` 输出一条 `delta`、已观察到的 `usage` 和 `done`；`/api/chat/completions` 输出一条内容块、usage 块和 `[DONE]`。
- 相同 key + 相同指纹，原执行仍在进行：409 `TURN_IN_PROGRESS`，带 `turnId` 和 `conversationId`。未带 `conversationId` 重试首个请求的客户端可以由此得知原执行所在的会话。多个相同 key 的首个请求同时到达时，只有一个被接受，其余返回 409 `TURN_IN_PROGRESS`（带胜出者的 `turnId` 与 `conversationId`），不会留下多余的会话。
- 相同 key + 不同指纹（包括换入口、换或增减 `conversationId`、改文本）：409 `IDEMPOTENCY_CONFLICT`。
- 相同 key，原执行已失败/取消/中断：409 `TURN_TERMINATED`，需使用新 key。
- 原执行所在会话已删除：404 `CONVERSATION_NOT_FOUND`，不重放已删除的内容。
- **保留期：** `app.memory.idempotency-key-retention`，默认 24 小时，从原请求被接受时算起，使用数据库时间。超过保留期后，同一 key 视为新请求，会重新执行。清理扫描（与删除清理同周期，`app.memory.cleanup-scan-interval`，默认 30 秒）只释放已结束执行的 key，执行记录和消息本身保留。客户端应在保留期内完成重试。

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

- 同会话已有活跃执行且租约未过期：返回 409 `TURN_IN_PROGRESS`，带 `turnId` 和 `conversationId`，例如 `{"code":"TURN_IN_PROGRESS","message":"…","turnId":"…","conversationId":"…"}`。其他错误码的响应体不含这两个字段。
- 活跃执行的租约已过期（例如进程退出后无人续期）：新请求先将其标记为 INTERRUPTED，再正常接受，不返回 409。
- 用同一 `Idempotency-Key` 重试已中断、失败或取消的请求返回 409 `TURN_TERMINATED`；需使用新 key。

## 消息大小上限

- 配置项 `app.memory.max-message-bytes`，默认 1 MiB（1048576 字节），按序列化后 JSON 文本的 UTF-8 字节数计算。
- 取值必须大于 0 且不超过 MySQL `MEDIUMTEXT` 容量（16 MiB − 1 字节），否则启动失败。
- 用户输入超限返回 413 `MESSAGE_TOO_LARGE`，不截断保存。
- 模型最终回答超限时，执行记为 FAILED（`error_code=MESSAGE_TOO_LARGE`），不保存回答正文；流式响应发送 `code` 为 `MESSAGE_TOO_LARGE` 的错误事件。该上限只限制单条消息，不截断数据库中已保存的历史，也不替代 M2a 的上下文 Token 预算。

## 工具调用记录（`chat_tool_events`）

不新增 HTTP 接口，也不改变 `tool_call` 事件格式（其参数摘要现在先经脱敏）。每次能力工具调用都按执行（turn）记录：能力、工具名、call id、脱敏后的参数、脱敏后的结果或错误类型、来源类型（`TOOL_RESULT`）以及开始、更新、结束时间。

- 同一执行内重复的 call id 更新同一条记录；不同执行中相同的 call id 是不同记录。
- 开始记录在工具执行之前写入，并校验执行权（fence、活跃状态、租约）。写入失败时工具不执行：失去执行权报 `TURN_INTERRUPTED`，存储故障报 `STORAGE_UNAVAILABLE`，参数超过 `app.memory.max-message-bytes` 报 `MESSAGE_TOO_LARGE`。
- 状态：`STARTED` → `SUCCEEDED` / `FAILED`；错误类型为 `TOOL_REPORTED_FAILURE`、`UNKNOWN_TOOL`、`INVALID_ARGUMENTS` 或异常类名，不保存异常信息。
- 工具已执行但结果记录失败：本次执行以 `STORAGE_UNAVAILABLE` 失败结束，不自动重放该工具。
- 执行进入任何终态（完成、失败、取消或断开、中断、恢复扫描）时，仍为 `STARTED` 的调用在同一事务内标记为 `UNCERTAIN`：已发出，但是否生效未知。之后补录到的真实结果会覆盖 `UNCERTAIN`。
- 脱敏会移除敏感字段（token、password、secret、API key、Authorization、Cookie 等，任意嵌套深度），以及文本中的结构化凭据（Bearer、JWT、`sk-`/`ghp_`/`AKIA` 等常见密钥格式、PEM 私钥、`key=value` 形式的密钥）。这是纵深防御，不能把任意内容变为可安全展示。日志不打印原始参数或结果。
- 工具结果只作为 tool 消息提供给模型，不获得指令权限，也不能扩大允许调用的工具范围。

## 会话 ID 规则

`ConversationIds.validate`：只接受小写规范 UUID 字符串，即 36 个字符的 8-4-4-4-12 格式，只含 `0-9a-f` 与连字符，不限 UUID 版本。原值要么原样使用，要么以 400 `INVALID_CONVERSATION_ID` 拒绝，不做修剪、大小写转换或字符替换，所以两个不同的输入不会指向同一会话或同一附件目录（只用小写也避免了大小写不敏感文件系统上的碰撞）。两个聊天入口、附件上传/下载与会话删除使用该规则。历史消息端点 `GET /api/conversations/{id}/messages` 不做格式校验：不合规的 ID 不可能属于当前用户，统一返回 404 `CONVERSATION_NOT_FOUND`。

## 附件（`POST /api/files/upload`、`GET /api/files/download`）

附件仍保存在本地 `${app.upload.dir}/{conversationId}/{文件名}`。目录名直接使用通过校验的会话 ID，不再对会话 ID 做 sanitize（sanitize 只用于文件名，只影响同一会话目录内的文件）。校验顺序：

1. `sessionId` 不符合会话 ID 规则：400 `INVALID_CONVERSATION_ID`。
2. 服务端身份没有租户：403 `TENANT_REQUIRED`。
3. 会话归属：
   - 上传：会话属于当前用户则写入；ID 尚未使用时先在当前用户名下创建空会话（标题“新对话”）再写入；属于他人、已删除或为旧版本无租户数据时返回 404 `CONVERSATION_NOT_FOUND`，不写任何文件。
   - 下载：会话不属于当前用户（不存在、他人、其他租户的同名 subject、已删除）或文件不存在，统一返回 404 `{"code":"CONVERSATION_NOT_FOUND","message":"conversation or file not found"}`，不暴露文件是否存在。只有在磁盘上存在目录、但没有对应会话记录的 ID 同样返回 404。
4. 下载的 `name` 含 `/`、`\`、`..` 或为空：400 `INVALID_PATH`（在归属校验之后）。

**上传响应变化：** 成功时返回数组，元素为 `{"sessionId","name","size"}`。其中 `name` 是去重后实际保存的文件名，如 `photo-1.png`。`sessionId` + `name` 就是下载引用：`GET /api/files/download?sessionId=…&name=…`。响应不再包含 `path`（服务器绝对路径）；依赖 `path` 拼接消息的前端改为使用文件名和下载引用。

**错误体：**

- `FILE_REJECTED`、`MIME_MISMATCH`（400）：`{"code","reason","file"}`。`reason` 只说明数量、大小或类型，`file` 是客户端提交的原文件名，都不包含服务器路径。
- `STORAGE_ERROR`（500）：`{"code":"STORAGE_ERROR","reason":"附件保存失败，请稍后重试"}`，不再包含异常信息。原始异常只写入服务端日志。

**保留期限：** M1 不承诺附件原件可永久恢复。临时附件由现有清理任务在目录最后修改 24 小时后删除（`app.upload.ttl-hours`）。这与会话消息默认永久保留不同：消息正文和其中提到的文件名会保留，附件原件可能已被清理。清理只处理名称符合会话 ID 规则的目录，其他目录不会匹配到任何会话，也不会被删除。归档与 MinIO 见 spec 14.1。

## 会话删除（`DELETE /api/conversations/{id}`）

| 情况 | 响应 |
| --- | --- |
| 删除当前用户的会话 | 204（无响应体） |
| 重复删除自己已删除的会话 | 204（幂等） |
| `id` 不符合会话 ID 规则 | 400 `INVALID_CONVERSATION_ID` |
| 服务端身份没有租户 | 403 `TENANT_REQUIRED` |
| 会话不存在、属于他人（含其他租户的同名 subject）或为旧版本无租户数据 | 404 `CONVERSATION_NOT_FOUND` |
| 未登录 | 401 `UNAUTHORIZED` |
| 数据库不可用 | 503 `STORAGE_UNAVAILABLE` |

返回 204 时，以下内容已在同一事务中生效：

- 会话写入删除标记，标题清空，revision 递增。会话列表、历史消息、两个聊天入口、附件上传和下载都立即把它当作不存在，返回 404 `CONVERSATION_NOT_FOUND`。
- 正在执行的回答立即失去执行权：fence 递增，执行记为 INTERRUPTED，`error_code=CONVERSATION_DELETED`。流式响应在下一次续期或检查点时收到 `TURN_INTERRUPTED` 错误事件，已发出但未返回的工具调用标记为 UNCERTAIN。
- 登记一个持久清理任务。

物理清理在后台进行，失败会重试：删除消息、工具调用记录和该会话的本地附件目录，并把缓存条目替换为不含内容的删除标记，之后的缓存回填一律被拒绝。附件目录名就是会话 ID，所以不会删到其他会话或其他用户的文件。删除标记（会话 ID、归属、删除时间，不含正文）永久保留，同一 ID 不能再被聊天或上传重新创建，任何后台任务或缓存重建都不会使已删除内容复活。执行记录 `chat_turns` 只含状态、用量与请求 ID，不含正文，作为删除标记的一部分保留。
