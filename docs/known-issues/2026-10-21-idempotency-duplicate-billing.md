# 已知问题：OpenAI 兼容入口重试导致重复执行与重复计费

日期：2026-10-21

状态：**已修复**（2026-10-23，[5ac08b3](https://github.com/sukimiya/agile-agent/commit/5ac08b3)，分支 `codex/idempotency-user-scope`，Flyway V9）。最初记录时为“已确认，计划在下一版本修复”；以下第 1–5 节保留原始记录，修复方式见第 6 节。

关联设计：`codex/memory-runtime-design` 分支 `docs/superpowers/specs/2026-10-21-memory-runtime-design.md` 第 13 节，以及 `docs/superpowers/plans/2026-10-21-memory-persistence-phase-1.md` 第 1 节的 `Idempotency-Key` 规则。

## 1. 现象

使用 OpenAI 兼容客户端（如 chatbot-ui）调用 `POST /api/chat/completions` 时，如果请求体没有携带 `conversationId`，网络超时或客户端自动重试后，同一条用户输入会被当作全新的对话再执行一次：重新路由、重新调用模型和能力工具，并产生新的会话记录。

## 2. 触发条件

- 代码位置（main `600e3e8`）：`agent-server/src/main/java/io/github/sukimiya/agileagent/controller/ChatController.java:56-79`。
- `ChatController.java:64` 使用 `body.getOrDefault("conversationId", NonBlockingIds.uuid())` 取会话 ID：请求未带 `conversationId` 时，每次请求都会生成一个新的 UUID。
- 随后 `SessionService.touchOrCreate`（`service/SessionService.java:24-31`）发现该 ID 不存在，直接新建会话并返回 OWNED，请求进入完整执行。
- 自定义入口 `POST /api/chat`（`ChatController.java:150-168`，ID 生成见 `:153-155`）在未带 `conversationId` 时行为相同。
- 记忆持久化 P1 计划新增的可选 `Idempotency-Key` 以 `(conversation_id, request_id)` 唯一约束去重，只在同一会话内生效。会话 ID 每次都不同，所以即使客户端重试时带上相同的 key，也无法命中，去重失效。

满足以下条件即会触发：请求不带 `conversationId`，且客户端或网关对同一请求进行了重试（超时重试、用户点击“重新生成”、代理层重放）。

## 3. 影响

- 重复执行：同一输入被完整执行多次，会话列表中出现多条重复会话。
- 重复计费：每次重试都会重新调用租户配置的模型，Token 用量和费用按次累计。
- 副作用重复：如果本轮触发了有副作用的能力工具（写业务数据、发消息等），重试会再次调用，业务侧幂等只能依赖各适配器自身。
- 当前产品处于开发阶段，没有正式线上部署，暂无实际计费损失；正式上线前必须修复。

## 4. 建议方案

以下两种方式可单独或组合使用，最终方案在下一版本设计时确定：

1. **幂等键按用户唯一**：唯一约束改为 `(tenant_id, subject, idempotency_key)`，不再依赖会话 ID。同时保存请求指纹 `sha256(endpoint, conversationId, 消息内容, 附件 fileId 列表)`：
   - 相同 key、相同指纹，执行已完成：返回已保存的结果（重放文本与已观察到的 Usage），不重新调用模型；
   - 相同 key、相同指纹，执行仍在进行：返回 409 `TURN_IN_PROGRESS` 并带 `turnId`，客户端可按 turnId 重新订阅；
   - 相同 key、不同指纹：返回 409 `IDEMPOTENCY_CONFLICT`。
   - key 需设置保留期（例如 24 小时），过期后可清理。
2. **带 key 时必须带 conversationId**：请求携带 `Idempotency-Key` 但没有 `conversationId` 时返回 400，要求客户端先创建会话或在首个响应中拿到会话 ID 后再重试。实现简单，但对不携带 conversationId 的通用 OpenAI 客户端不友好，建议作为方案 1 的补充约束而非唯一手段。

不论采用哪种方案，都不应仅凭消息文本相同去重，以免误吞用户有意重复发送的消息。

## 5. 计划版本

下一版本（记忆持久化 P1 之后）。修复时需同步：

- 更新 P1 计划中的幂等规则与 `openAiRetryWithoutConversationIdCreatesNewTurn` 测试（该测试当前记录的是本问题的现有行为，修复后改为断言不重复执行）；
- 在 `docs/memory/api.md` 中说明 `Idempotency-Key` 的作用域与保留期；
- 增加两个聊天入口在无 `conversationId` 时重试不重复调用模型的回归测试。

## 6. 修复（已修复）

采用第 4 节方案 1（用户 2026-10-23 批准，只采用方案 1，不要求带 key 时必须带 conversationId）：

- **唯一约束：** V9 迁移删除按会话的 `uq_chat_turns_request (conversation_id, request_id)` 和 `request_id` 列，新增可为空的 `idempotency_key` 与 `uq_chat_turns_idempotency (tenant_id, subject, idempotency_key)`。不带 key 的请求和已过保留期的 key 存为 NULL，互不冲突。
- **先查 key，后生成会话 ID：** 两个入口都不再自行生成会话 ID。`TurnLifecycle.accept` 在事务内先按 `(tenant, subject, key)` 查找；只有没有命中时，才使用请求给出的 conversationId 或生成新的 ID。
- **请求指纹：** `request_hash` 改为保存 SHA-256(入口、客户端给出的 conversationId（未给出为空）、消息内容、附件 ID 列表)。目前两个入口的请求体都不带附件 ID，这一项暂时为空列表。
- **结果：**
  - 相同 key、相同指纹：已完成则重放，不调用模型；进行中返回 409 `TURN_IN_PROGRESS` 并带 turnId；已失败、取消或中断返回 409 `TURN_TERMINATED`；所在会话已删除返回 404。
  - 相同 key、不同指纹：返回 409 `IDEMPOTENCY_CONFLICT`。
  - 同一 key 的并发首个请求只接受一个，其余返回 409 `TURN_IN_PROGRESS`，不留下多余会话。
- **保留期：** `app.memory.idempotency-key-retention` 默认 24 小时，从接受时算起，使用数据库时间。查找时把过期 key 视为不存在；`MemoryMaintenance` 的清理扫描释放已结束执行的过期 key，执行记录和消息保留。
- **不凭文本去重：** 不带 key 的相同文本请求仍然各自执行。
- **测试：** `TurnStreamingIntegrationTest` 及其 MySQL 变体 `TurnStreamingMySqlIntegrationTest` 覆盖以下场景：
  - 两个入口无 conversationId 的重试重放（原 `retryWithoutConversationIdCreatesNewTurnOnBothEndpoints` 已改为断言不重复执行）；
  - 指纹冲突；进行中返回 409；同一 key 的并发首个请求；
  - 按租户和用户隔离；已删除会话；保留期过后重新执行，以及保留期清理；
  - 无 key 时不去重。
- 接口说明见 `docs/memory/api.md`“`Idempotency-Key` 作用域与保留期”；设计见 spec 6.4 与第 13 节。
