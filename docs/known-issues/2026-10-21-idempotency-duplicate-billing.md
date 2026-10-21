# 已知问题：OpenAI 兼容入口重试导致重复执行与重复计费

日期：2026-10-21

状态：已确认，计划在下一版本修复；当前版本（记忆持久化 P0/P1）只记录，不修复。

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
