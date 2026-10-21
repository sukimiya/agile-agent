# Memory Persistence Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **修订（2026-10-21）：** 按用户对设计 review 的决定修订：附件改为用户归属、存放于 MinIO 私有 bucket 并通过预签名 URL/STS 直连；取消改为事件驱动（显式取消接口 + 服务端 TurnRegistry + 按 turnId 重新订阅）；删除旧 Redis 历史迁移，新版本直接切换到新表；以最终版本为准，不保留过渡兼容；重复计费问题延后到下一版本。

**Goal:** 在 agile-agent 首先实现租户隔离、可恢复的原始会话与执行记录、事件驱动的取消，以及用户归属的 MinIO 附件，为后续分层记忆提供可信数据源。

**Architecture:** MySQL 是原始消息、执行状态和文件归属的事实源，Redis 仅缓存有界近期窗口并承担跨实例取消通知。HTTP 成功响应前持久化用户输入与执行票据；数据库租约和版本约束并发，流式输出保存检查点；执行由服务端 TurnRegistry 持有，SSE 只是订阅者。附件字节存放在 MinIO 私有 bucket，服务只鉴权和签名，客户端直连 MinIO。旧 Redis 历史不读取、不迁移。

**Tech Stack:** JDK 21、Spring Boot 3.5、WebFlux/Reactor、R2DBC、MySQL、Flyway、Redis（含 pub/sub）、MinIO（S3 兼容 API、预签名 URL、STS AssumeRole、标签生命周期）、JUnit 5、现有本地 Redis 测试设施。

**Spec:** `docs/superpowers/specs/2026-10-21-memory-runtime-design.md`

## Global Constraints

- agile-agent 是主线，设计、执行计划和验收记录均保存在本仓库；mcp-tool 仅在主线里程碑验收后适配移植。
- 使用 JDK 21 和现有 Spring Boot/WebFlux、R2DBC、Flyway、MySQL、Redis，附件新增 MinIO；首版不增加向量数据库、消息中间件或新的外部记忆服务。跨实例取消通知使用 Redis pub/sub。
- 以最终版本为准：产品未正式部署，不迁移旧会话历史与旧本地上传文件，不保留旧的路径式下载接口或其他过渡兼容。
- 首版不扩展公共 `agent-spi`；内部任务记录可以扩展，但 provider 的已发布方法签名保持兼容。
- 原始会话默认不自动过期；近期消息缓存闲置 7 天，用户概要缓存 1 小时属于后续 P3，P1 不创建概要缓存。
- 身份从服务端 IdentityContext 解析；租户缺失、归属不明或已删除的资源不能进入记忆读取或执行。
- 不修改任何已发布 Flyway 文件；实施前再次核对迁移版本。本文按当前 V7 之后的 V8/V9 规划。新迁移必须同时通过 H2（`MODE=MySQL`）与真实 MySQL：长文本用 `MEDIUMTEXT` 并在应用层限长，身份列用 `utf8mb4_bin` 排序规则或 `VARBINARY`。
- 附件归属于 `(tenantId, subject)`，MySQL `files` 表是归属权威；bucket 私有，object key 为 `{tenantId}/{sha256(subject)}/{fileId}`；接口只返回 fileId，不返回 bucket、object key 或路径。被长期记忆实际引用或用户明确要求保留的附件归档；其他临时附件由 MinIO 标签生命周期 24 小时过期；P1 不增加多模态理解。
- 生产恢复目标为 RPO ≤ 15 分钟、RTO ≤ 4 小时；尚未通过真实演练时不能标记为已达标。
- 不记录访问令牌、API key 或模型内部推理链；工具记录只保存脱敏后的必要内容。
- 两个聊天入口的流式事件格式保持不变；新增分页、请求幂等、取消、重新订阅和文件接口需写入接口说明。
- 重复计费（OpenAI 入口无 conversationId 时幂等失效）延后到下一版本，见 main 分支 `docs/known-issues/2026-10-21-idempotency-duplicate-billing.md`；P1 不修复，但测试需记录该行为。

## Review Focus

- 同一个 subject 出现在不同租户，包括只差大小写的身份值：不能共享会话、文件、缓存或执行票据；任务 1/3/4/7 覆盖。
- 请求已接受但客户端从未订阅 SSE、双重订阅、断开后重新订阅或租约过期：只能运行一次并能回收，重连不会被 409 锁住；任务 2/4 覆盖。
- 显式取消落在非持有实例、取消与完成竞争、已发出的副作用工具调用：数据库先写 CANCEL_REQUESTED，先写入的终态生效，工具调用标记不确定；任务 2/4/5 覆盖。
- 最终 Completed 后仍有 Usage，或没有 Completed 就断流：正确记录使用量与失败状态；任务 4 覆盖。
- 附件跨用户访问、预签名范围过宽、上传未确认即被引用、删除后仍能签发：任务 7 覆盖。
- 删除与缓存重建、附件生命周期过期、进程重启并发：不能复活记录或误删归档；任务 3/6/7/8 覆盖。

---

## 0. 边界、基线与文件结构

本计划覆盖 P0/P1，推荐由主代理直接逐任务实现；具体执行方法在实施前确认。暂不实施 LLM 摘要、Rules 执行器、用户图谱、向量检索、跨会话开关 UI 或 mcp-tool 移植。

调研基线：文档分支 `codex/memory-runtime-design`，产品代码基线 `600e3e8`，已存在设计提交 `e0a83de`、`1c8d299`、`bc9f43d`。当前未跟踪的两张 `docs/mermaid-diagram*.png` 属于用户，不能加入提交或清理。

实施时使用 superpowers:using-git-worktrees 从 agile-agent 的已提交设计/计划分支建立隔离工作树，确认工作目录是 agile-agent 而不是 mcp-tool，避免在错误仓库上创建基线。只在 agile-agent 完成验收后安排副本适配。

生产包前缀为 `agent-server/src/main/java/io/github/sukimiya/agileagent/`，测试前缀为 `agent-server/src/test/java/io/github/sukimiya/agileagent/`。下文文件均相对仓库根目录，Java 包严格与目录对应。

| 单元 | 文件/目录 | 责任 |
| --- | --- | --- |
| 契约/配置 | `agent-server/src/main/java/io/github/sukimiya/agileagent/memory/{MemoryScope,MemoryModel,MemoryProperties,MemoryException}.java` | 范围、状态、票据、检查点/租约/缓存参数、稳定错误码 |
| 数据访问 | 同包 `{ConversationRepository,TurnRepository,ToolEventRepository}.java` | 会话/消息、租约/幂等、工具记录 |
| 执行生命周期 | 同包 `{TurnLifecycle,TurnRecorder,TurnRegistry,TurnCancelBus,ExpiredTurnRecovery}.java` | 接受、执行、检查点、完成/失败/取消/过期、本实例执行持有与重新订阅、跨实例取消通知 |
| 历史/缓存 | 同包 `{ConversationHistory,HotMemoryCache}.java` | 归属验证、有界缓存、原文分页与提示历史 |
| 删除/清理 | 同包 `MemoryMaintenance.java` | 会话删除标记、持久删除任务、重入恢复 |
| 附件 | `agent-server/src/main/java/io/github/sukimiya/agileagent/file/{FileRepository,FileService,ObjectStorage,MinioObjectStorage,FileProperties,FileDeletionJob}.java` | files/conversation_files 归属、预签名/STS 签发、上传确认、生命周期标签、归档、删除 |
| HTTP | `controller/{ChatController,TurnController,FileController}.java` | 接受请求、分页、取消与重新订阅、文件签名与确认 |
| 运行集成 | `agent/{Orchestrator,DomainTask,DomainAgent}.java`、`service/SessionService.java`；删除 `service/{ChatMemory,UploadCleanupTask,FileStorageService}.java` 和 `controller/UploadController.java` 的旧路径 | 连接现有业务流程；不改变 provider SPI |
| 数据迁移 | `agent-server/src/main/resources/db/migration/V8__memory_persistence.sql`、`V9__user_files.sql` | 增量表和索引 |
| 测试基础 | `agent-server/src/test/java/io/github/sukimiya/agileagent/memory/{MemoryTestFixture,MemoryMySqlTestSupport}.java`、`file/MinioTestSupport.java` | 隔离 H2/Redis/MinIO、可控时间和真实 MySQL 测试 |
| 运维 | `docker/docker-compose.memory-test.yml`（MySQL + MinIO）、`docs/memory/{api,operations,phase-1-acceptance}.md` | 测试服务、接口变化、恢复手册与证据 |

## 1. 固定的阶段行为

- 用户输入成功入库后才能提交 HTTP 200 SSE；缺模型配置继续返回既有 428 JSON；未知/他人资源（会话、turn、文件）返回 404；缺租户返回 403；写库不可用返回 503。
- 支持可选 `Idempotency-Key` 请求头，长度 1–128、仅允许 ASCII 字母数字和 `._:-`。同会话相同 key + 相同文本不重复执行；不同文本返回 409 `IDEMPOTENCY_CONFLICT`。
- 重复已完成请求重放已存文本与已观察到的 Usage；重复活跃请求返回 409 `TURN_IN_PROGRESS`，响应体带 `turnId`，客户端用它重新订阅或取消；重复失败/取消/中断请求返回 409 `TURN_TERMINATED`，新执行需新 key。无 key 时生成新请求 ID，不凭相同文本去重。key 只在会话内生效，OpenAI 入口不带 conversationId 时的重复计费问题延后到下一版本。
- 同会话只允许一个活跃执行；状态为 ACCEPTED → RUNNING → COMPLETED/FAILED/CANCELLED/INTERRUPTED，取消路径为 ACCEPTED/RUNNING → CANCEL_REQUESTED → CANCELLED；终态不能降级或重新执行，取消与完成竞争时以先写入数据库的终态为准。
- 执行由服务端 TurnRegistry 持有（`Disposable` + 可回放 sink），SSE 只是订阅者；客户端断开只取消订阅，不取消执行。`GET /api/turns/{turnId}/stream` 先回放数据库检查点，再接上实时输出；执行在其他实例时轮询检查点到终态。
- 显式取消 `POST /api/turns/{turnId}/cancel`：在请求自己的响应式链中校验归属 → 带 fence 写 CANCEL_REQUESTED → 本实例 dispose 或经 Redis pub/sub 通知持有实例 → 写 CANCELLED。CANCEL_REQUESTED 写库成功返回 200（响应含当前状态），终态返回 409 `TURN_TERMINATED`，写库失败返回 503。前端通过 SSE 状态事件看到 `CANCEL_REQUESTED → CANCELLED`。pub/sub 丢失时，持有方续期时读到 CANCEL_REQUESTED 后停止。
- 租约 60 秒，每 15 秒续期，恢复扫描 30 秒一次并在启动时执行；租约依据数据库时间。写入携带递增 fence，过期执行不能覆盖新执行。
- 检查点 5 秒或累计新增 8 KiB UTF-8 文本，先到者触发；正常完成提交完整文本。尾部恢复边界以最后成功检查点为准。
- 缓存上限为最近 100 条消息且序列化负载不超过 256 KiB；单条过大则绕过缓存。缓存值按会话 revision 校验，TTL 闲置 7 天。此上限不截断数据库原文，也不替代 P2 Token 预算。
- 兼容消息端点继续返回数组，默认最近 100 条、按 sequence 升序；`beforeSequence` 读取更早内容，limit 为 1–200。`X-Next-Cursor` 与 `X-Has-More` 指示翻页，写入 API 说明。
- 默认提示历史只包含已完成执行，排除当前 turn 和不完整执行，避免重复注入本次用户消息；P2 之前只供测试，不宣称已解决超长上下文。
- 文件：上传预签名 PUT 默认 20 分钟、下载预签名 GET 默认 1 小时；STS 上传 `DurationSeconds=1200`、下载 3600，不低于 MinIO 最小值 900，均可配置。已签发凭证不可撤回，删除/撤权最迟在过期时生效。

## Task 1: 数据契约、范围隔离、迁移及测试基线

**Files:** 创建结构表中的契约/配置、`V8__memory_persistence.sql` 和两类测试基础；修改 `repository/SessionRepository.java`、`service/SessionService.java`、`agent-server/pom.xml`；新增 `memory/MemoryScopeTest.java`、`memory/MemorySchemaTest.java`、`memory/MemoryMySqlIntegrationTest.java`（均在对应生产/测试前缀下）。估算 1–1.5 人日。

**Interfaces:**
- `MemoryScope(String tenantId, String subject)`：拒绝空白范围；不改变大小写或修剪后当作另一个身份。
- `MemoryModel` 中定义 `TurnCommand(MemoryScope scope, String conversationId, String requestId, String text)`、`TurnTicket(MemoryScope scope, String conversationId, String turnId, long fence)`、`TokenUsage(boolean observed, long promptTokens, long completionTokens, long totalTokens)`、`AcceptedTurn(TurnTicket ticket, boolean replay, String answer, TokenUsage usage)`。
- 同文件定义 `StoredMessage(String id, long sequence, LlmClient.Message payload, TurnStatus status, Instant occurredAt)`、`HistoryPage(List<StoredMessage> items, Long nextBeforeSequence, boolean hasMore)`、`HistoryWindow(List<StoredMessage> items, boolean hasMore, long revision)` 和上述 `TurnStatus`（含 CANCEL_REQUESTED）。不定义 legacy 存储状态，所有会话都在新表中。
- `SessionService.findOwned(String conversationId, MemoryScope scope): Mono<SessionRow>`、`listForUser(MemoryScope scope): Flux<SessionRow>`；SessionRow 增加租户、revision、deletedAt。所有产品调用改用范围接口，旧 String userId 接口删除。

- [ ] 写失败测试：`rejectsBlankScope`、`sameSubjectAcrossTenantsCannotRead`、`caseDistinctScopesRemainDistinct`、`missingTenantIsForbidden`、`longMessageBodyIsNotTruncated`。代表断言：`assertThatThrownBy(() -> new MemoryScope(" ", "u1")).isInstanceOf(IllegalArgumentException.class);`；两租户同 subject 查询分别只得自己的 session；只差大小写的 subject 在 H2 和 MySQL 上都不能互相看到；超过 64 KB 的正文完整保存，超过应用层上限明确拒绝。
- [ ] 在 JDK 21 下运行 `mvn -pl agent-server -am -Dtest=MemoryScopeTest,MemorySchemaTest test`，记录新增契约不存在所致的 FAIL；既有基线失败单独记录。
- [ ] 实现契约和配置，sessions 追加 tenant_id、revision、deleted_at 字段；创建 chat_turns、chat_messages、chat_tool_events、memory_jobs。约束 `(conversation_id, request_id)`、`(conversation_id, sequence)` 唯一，所有数据读取和更新校验范围。身份列使用 `VARCHAR ... COLLATE utf8mb4_bin`（或 `VARBINARY`），不依赖不区分大小写的默认排序规则；数据过长明确拒绝而不截断身份。messages 正文和工具结果用 `MEDIUMTEXT` 保存 JSON 文本，保留完整 Message 结构，应用层设置上限。先做 H2 兼容性验证，必要时按 vendor 拆分 Flyway `locations`，不能删去 MySQL 侧约束。旧会话行不做租户回填，新版本不读取旧数据。
- [ ] 新增仅本地测试用 MySQL 8.4 与 MinIO 的 Compose 文件，独立服务/volume，端口仅绑定 loopback；`memory-mysql-it` Maven profile 必须使用明确测试库配置，缺配置失败，不连接生产。H2 运行快速测试，真实 MySQL 验证 Flyway、范围比较和并发约束；运行上面的测试及 `mvn -Pmemory-mysql-it -pl agent-server -am -Dtest=MemoryMySqlIntegrationTest test`，期望 PASS。
- [ ] 自查迁移兼容现有 H2 测试后，定向暂存本任务文件并提交 `feat: add scoped durable memory schema`；不提交测试凭证或用户文件。

## Task 2: 原子接受、幂等请求和执行租约

**Files:** 创建生产包下 `memory/{ConversationRepository,TurnRepository,TurnLifecycle,TurnRecorder,TurnRegistry,TurnCancelBus,ExpiredTurnRecovery}.java`；新增测试包下 `memory/{TurnLifecycleTest,TurnCancelTest,TurnConcurrencyIntegrationTest,ExpiredTurnRecoveryTest}.java`。估算 3–4.5 人日（与 Task 4 合计 5–8 人日）。

**Interfaces:**
- 消费 Task 1 的 MemoryModel 和作用域。
- `TurnLifecycle.accept(TurnCommand command): Mono<AcceptedTurn>`：事务内验证/创建归属会话、幂等检查、取得会话执行权、写用户消息和 ACCEPTED 执行。
- `TurnLifecycle.execute(TurnTicket ticket, Function<TurnRecorder, Flux<String>> operation): Flux<String>`；本任务同时实现 `TurnRecorder.delta(String delta): Mono<Void>` 和 `completed(String fullText, TokenUsage usage): Mono<Void>`，后续 Task 4 直接消费。
- `ExpiredTurnRecovery.runOnce(): Mono<Integer>`；`TurnRepository.renew(TurnTicket ticket): Mono<RenewResult>`（RenewResult 区分续期成功、失去 fence、已 CANCEL_REQUESTED）、`markTerminal(TurnTicket ticket, TurnStatus status): Mono<Boolean>`、`requestCancel(MemoryScope scope, String turnId): Mono<TurnStatus>`；事务/SQL 内部方法可私有拆分。
- `TurnRegistry.register(TurnTicket ticket, Flux<String> execution): Flux<String>`：订阅并持有执行（`Disposable`），输出写入可回放 sink 并返回给首个 SSE 订阅者；`attach(String turnId): Optional<Flux<String>>`、`dispose(String turnId): boolean`。
- `TurnCancelBus.publish(String turnId): Mono<Void>`、`subscribe(): Flux<String>`：Redis pub/sub，频道含部署命名空间；收到消息时调用本实例 `TurnRegistry.dispose`。
- `TurnLifecycle.cancel(MemoryScope scope, String turnId): Mono<TurnStatus>`：带 fence 写 CANCEL_REQUESTED → 本地 dispose 或 publish → 执行停止后写 CANCELLED；全部写库在调用方订阅的链上完成。

- [ ] 写失败测试：`sameKeyDoesNotDuplicateUserMessage`、`differentTextForSameKeyConflicts`、`onlyOneConcurrentTurnAccepted`、`expiredFenceCannotWrite`、`acceptedWithoutSubscriptionIsRecovered`、`terminalStateCannotRegress`、`cancelWritesCancelRequestedBeforeDispose`、`cancelOnOtherInstanceDisposesViaPubSub`、`lostPubSubStopsOnNextRenew`、`cancelVersusCompleteFirstWriteWins`、`clientDisconnectDoesNotCancelExecution`、`cancelForeignTurnReturnsNotFound`。断言并发接受只成功一次，重复后用户消息数仍为 1；直接将测试租约时间置为过去再恢复，结果为 INTERRUPTED；写库失败时取消返回错误且执行未被 dispose。
- [ ] 运行 `mvn -pl agent-server -am -Dtest=TurnLifecycleTest,TurnCancelTest,ExpiredTurnRecoveryTest test`，预期新增实现缺失导致 FAIL。
- [ ] 实现 R2DBC TransactionalOperator 事务与数据库 CAS/唯一约束，以及 recorder 的持久检查点/完成写入；终态变化和会话 revision 一并提交。续期只对当前 fence 的活跃执行成功；续期失去执行权或读到 CANCEL_REQUESTED 即停止后续模型/工具调用。取消通过 Flux 取消信号向上传播，模型流和 `concatMap` 中未开始的工具调用不再执行。CANCELLED 由取消请求链或执行方的受管链写入，禁止依靠 `doFinally` 中未管理的异步写入。
- [ ] 用真实 MySQL 两个独立连接执行 `mvn -Pmemory-mysql-it -pl agent-server -am -Dtest=TurnConcurrencyIntegrationTest test`；快速测试命令同上，全部 PASS，确认已完成重试只重放、不重新调用模型。
- [ ] 定向提交 `feat: persist idempotent turns with fenced leases and cancellation`。

## Task 3: 数据库历史、分页与可重建缓存

**Files:** 创建生产包下 `memory/{ConversationHistory,HotMemoryCache}.java`；删除 `service/ChatMemory.java` 及 `service/ChatMemoryTest.java`（新版本不读取旧 `chatmemory:*` key），修改 `repository/SessionRepository.java`；新增测试包下 `memory/{ConversationHistoryTest,HotMemoryCacheTest}.java`。估算 1–1.5 人日。

**Interfaces:**
- 消费 Task 1/2 的 scopes、票据、session revision 和消息。
- `ConversationHistory.page(MemoryScope scope, String conversationId, Long beforeSequence, int limit): Mono<HistoryPage>`。
- `ConversationHistory.contextBefore(TurnTicket ticket): Mono<List<LlmClient.Message>>`：排除该 turn，筛选完整历史。
- `HotMemoryCache.get(MemoryScope scope, String conversationId, long revision): Mono<HistoryWindow>`、`put(MemoryScope scope, String conversationId, long revision, HistoryWindow window): Mono<Void>`、`evict(MemoryScope scope, String conversationId): Mono<Void>`。窗口携带稳定消息 ID、sequence 和 hasMore，不能只缓存丢失这些信息的 Message 列表。

- [ ] 写失败测试：`redisMissErrorOrCorruptJsonFallsBackToDatabase`、`staleRevisionIsIgnored`、`cacheHasSevenDayIdleTtlAndSizeLimit`、`paginationKeepsStableIdsAndOrdering`、`currentAndIncompleteTurnsExcludedFromPrompt`、`deletedSessionCannotRefillCache`、`legacyChatmemoryKeyIsIgnored`。测试至少存 205 条消息，断言翻页无重叠且 ID 不变，缓存条数 ≤100、负载 ≤262144 字节，数据库仍有全部消息。
- [ ] 运行 `mvn -pl agent-server -am -Dtest=ConversationHistoryTest,HotMemoryCacheTest test`，预期 FAIL。
- [ ] 实现按 scope/revision 的分页与缓存；缓存 key 含部署命名空间，近期分页可使用窗口，`contextBefore` 在 P1 直接读取数据库完整有效历史，P2 再加入预算，不能把缓存 100 条上限当成上下文截断。窗口截取到字节上限后必须保留 hasMore。通过授权后的轻量会话版本检查防止读取旧 revision；写入旧版本缓存不能遮蔽新版本。用户删除标记检查先于缓存命中。
- [ ] 运行上述命令；使用可控时间/Redis TTL 检查，无需实际等待 7 天，全部 PASS。
- [ ] 定向提交 `feat: read conversation history from durable storage`。

## Task 4: 两个聊天入口与流式输出的可靠集成

**Files:** 创建生产包下 `web/MemoryExceptionHandler.java`、`controller/TurnController.java`；修改 `controller/ChatController.java`、`agent/Orchestrator.java`、`service/SessionService.java`；修改现有 Controller/Orchestrator 测试，新增测试包下 `memory/TurnStreamingIntegrationTest.java`、`controller/TurnControllerTest.java`。估算 2–3.5 人日。

**Interfaces:**
- 消费 Task 2 的 TurnRecorder/execute；execute 管理定时检查点、心跳和终止清理，不向 provider SPI 暴露 recorder。
- `Orchestrator.chat(AcceptedTurn accepted, String accessToken, IdentityContext identity, LlmClient llm): Flux<String>`；controller 在返回 200 前先解析身份、只读验证归属、解析模型，再 accept。
- GET 消息接口消费 Task 3 的 page；两个 POST 接收可选 Idempotency-Key，并经 TurnRegistry.register 启动执行；`TurnController` 提供 `POST /api/turns/{turnId}/cancel` 与 `GET /api/turns/{turnId}/stream`；MemoryExceptionHandler 将稳定错误码映射到第 1 节 HTTP 状态，不输出底层错误/凭证。

- [ ] 写失败测试：`userPersistedBeforeSseAccepted`、`missingModelReturnsExact428WithoutTurn`、`bothEndpointsReplayCompletedTurn`、`explicitCancelKeepsPartialAndMarksCancelled`、`cancelEmitsCancelRequestedThenCancelledEvents`、`disconnectThenReattachByTurnIdWithout409Lock`、`activeTurnConflictReturnsTurnId`、`timeoutAndDatabaseFailureNeverEmitSuccessfulDone`、`usageAfterCompletedIsNotLost`、`missingCompletedIsFailure`、`doubleSubscriptionRunsModelOnce`、`openAiRetryWithoutConversationIdCreatesNewTurn`（记录已知问题的当前行为，下一版本修复后更新）。至少用 8 KiB 多字节文本验证按 UTF-8 大小触发检查点。
- [ ] 运行 `mvn -pl agent-server -am -Dtest=ChatControllerTest,TurnControllerTest,OrchestratorTest,TurnStreamingIntegrationTest test`，预期新增契约与断言 FAIL。
- [ ] 改造现有控制流，移除正常结束后的旧 memory.append。最终正文在 Completed 后暂存，继续消费尾部 Usage，只有上游正常结束且存在 Completed 才事务提交 COMPLETED。显式取消和错误保存检查点并标记状态；客户端断开只取消该订阅，执行继续由 TurnRegistry 持有直至终态。落库失败发稳定 error，协议可发送 done 作为流终止，但不得把它解释为成功。无订阅由过期扫描处理；已消费票据不得第二次启动，重新订阅只回放检查点并接上实时输出。
- [ ] 已完成重放输出一条 delta、已观察到的 usage 和 done；OpenAI 转换仍输出 `[DONE]`。GET messages 默认数组/分页头行为按第 1 节；运行上述测试及 `ReactiveChainBlockHoundTest`，PASS 后确认无未管理后台订阅或重复模型调用。
- [ ] 定向提交 `feat: persist chat lifecycle with event-driven cancel and reattach`。

## Task 5: 可追溯工具执行记录与凭证过滤

**Files:** 估算 1 人日。创建生产包下 `memory/{ToolEventRepository,TurnToolRecorder,ToolRecordSanitizer}.java`；修改 `agent/{DomainTask,DomainAgent,Orchestrator}.java`；新增测试包下 `memory/TurnToolRecorderTest.java`，修改现有 DomainAgentTest 与 ExternalCapabilityIntegrationTest 所需内部构造。

**Interfaces:**
- 内部 DomainTask 增加 TurnTicket；公共 AgentExecutionContext 和 provider.execute 签名不变。
- `TurnToolRecorder.started(TurnTicket ticket, String capability, LlmClient.ToolCall call): Mono<Void>`、`finished(TurnTicket ticket, String capability, String callId, String result, boolean success): Mono<Void>`。
- `ToolRecordSanitizer.sanitize(String content): String`：过滤已知敏感字段与结构化凭证；调用日志不输出原始参数/结果。

- [ ] 写失败测试：`toolStartRecordedBeforeExecute`、`sameCallIdUpsertsWithinSameTurnOnly`、`tokenFieldsAreRedacted`、`lostFenceStopsToolExecution`、`externalSuccessDatabaseFailureIsNotRetried`、`cancelMarksInFlightToolCallUncertain`。断言跨 turn 的相同 callId 是不同事件，失败记录不触发第二次 provider.execute。
- [ ] 运行 `mvn -pl agent-server -am -Dtest=TurnToolRecorderTest,DomainAgentTest test`，预期 FAIL。
- [ ] 记录 capability、工具名、调用 ID、脱敏参数、结果/错误类型、时间与来源；写开始记录失败不执行工具。工具成功但记录失败时保留不确定状态并终止，不尝试自动重放有副作用调用；取消时已发出但未返回的工具调用标记为 UNCERTAIN，并在前端事件中展示。工具结果进入本轮上下文前仍保持现有允许工具检查。
- [ ] 运行上面命令及 `ExternalCapabilityIntegrationTest`、`ReactiveChainBlockHoundTest`，全部 PASS；检查日志 fixture 不含测试令牌字面值。
- [ ] 定向提交 `feat: record scoped tool execution provenance`。

## Task 6: 会话删除与持久清理

**Files:** 创建生产包下 `memory/MemoryMaintenance.java`；修改 `controller/ChatController.java`（实现当前为空的 `DELETE /api/conversations/{id}`）；新增测试包下 `memory/MemoryDeletionTest.java`；创建 `docs/memory/api.md` 和 `docs/memory/operations.md` 初稿。估算 1–1.5 人日。

**Interfaces:**
- `MemoryMaintenance.deleteConversation(MemoryScope scope, String conversationId): Mono<Void>`、`runPending(int limit): Mono<Integer>`；DELETE 成功返回 204，重复已删除返回 204，未知/他人返回 404。
- 删除会话时调用 Task 7 的 `FileService.unlinkConversation`，移除会话对文件的引用；文件本身归属用户，不随会话自动删除。

- [ ] 写失败测试：`deleteHidesConversationImmediately`、`deleteCancelsActiveTurnAndInvalidatesFence`、`deleteWinsOverCacheRefill`、`deleteRetryIsIdempotent`、`deleteForeignConversationReturnsNotFound`、`deleteRemovesConversationFileLinksOnly`。
- [ ] 运行 `mvn -pl agent-server -am -Dtest=MemoryDeletionTest test`，预期 FAIL。
- [ ] 删除事务先 tombstone 并作废活跃 fence（运行中的 turn 经 TurnLifecycle.cancel 停止），登记持久清理任务；读取/续期/检查点/重新订阅均检查 tombstone。物理清理和缓存失效可重试，永久保留无正文的删除标记，防止后台任务或缓存重建复活。运行上述测试与真实 MySQL 删除竞态测试，PASS。
- [ ] 文档写清分页、Idempotency-Key、取消/重新订阅和删除接口，并注明新版本不读取旧 Redis 历史；定向提交 `feat: delete conversations with durable cleanup`。

## Task 7: 用户归属附件、MinIO 直连与生命周期

当前上传存于本地磁盘 `{dir}/{sessionId}/{name}`，`UploadController.java:46-75` 的下载只检查路径字符、不校验归属，且上传响应返回服务器绝对路径；这是 P1 必须修复的安全问题。按最终设计整体替换为 MinIO，不保留旧下载接口，也不导入旧本地文件。

**Files:** 创建 `agent-server/src/main/resources/db/migration/V9__user_files.sql`；创建生产包下 `file/{FileRepository,FileService,ObjectStorage,MinioObjectStorage,FileProperties,FileDeletionJob}.java` 和 `controller/FileController.java`；删除 `controller/UploadController.java`、`service/{FileStorageService,UploadCleanupTask}.java`、`config/UploadProperties.java` 及对应测试；修改 `agent-server/pom.xml`（MinIO Java SDK）、`agent-server/src/main/resources/application.yml`；新增测试包下 `file/{FileServiceTest,FileControllerTest,MinioObjectStorageIntegrationTest}.java`。估算 3–4 人日。

**Interfaces:**
- V9 创建 `files(file_id, tenant_id, subject, bucket, object_key, original_name, sha256, size, content_type, status, created_at, confirmed_at, archived_at, deleted_at)` 与 `conversation_files(conversation_id, file_id, source_type, source_id, created_at)`；身份列 `utf8mb4_bin`，`file_id` 为服务端生成的 UUID，status 为 TEMP/ARCHIVED/DELETE_PENDING/DELETED。
- `FileRef(String fileId, String name, long size, String sha256, String contentType, FileStatus status)`，定义于 FileService 内；不包含 bucket、object key 或路径。
- `FileService.createUpload(MemoryScope scope, String name, long size, String contentType, String sha256): Mono<UploadGrant>`（UploadGrant 含 fileId、预签名 PUT URL、所需请求头、过期时间）；`createBatchUpload(MemoryScope scope, List<...>): Mono<StsGrant>`（STS 临时凭证 + 允许的 key 列表）；`confirm(MemoryScope scope, String fileId): Mono<FileRef>`；`downloadUrl(MemoryScope scope, String fileId): Mono<SignedUrl>`。
- `FileService.archive(MemoryScope scope, String fileId): Mono<FileRef>`、`delete(MemoryScope scope, String fileId): Mono<Void>`、`linkReference(MemoryScope scope, String conversationId, String fileId, String sourceType, String sourceId): Mono<Void>`、`unlinkReference(...)`、`unlinkConversation(MemoryScope scope, String conversationId): Mono<Void>`。用户显式保留创建 USER_PIN 引用；P3 的记忆写入复用此接口，不先创建 P3 表。
- `ObjectStorage` 封装 `presignPut/presignGet/stat/setTags/removeTags/copy/remove/assumeRole`，`MinioObjectStorage` 为实现；单元测试用假实现，集成测试用 Compose 中的 MinIO。
- HTTP：`POST /api/files`（创建上传，返回 fileId 与预签名 URL）、`POST /api/files/batch`（STS）、`POST /api/files/{fileId}/confirm`、`GET /api/files/{fileId}/download-url`、`POST /api/files/{fileId}/archive`、`DELETE /api/files/{fileId}`；会话引用通过 `POST /api/conversations/{id}/files/{fileId}` 建立。

- [ ] 写失败测试：`foreignUserCannotConfirmDownloadArchiveOrDelete`（统一 404）、`responsesNeverExposeObjectKeyOrPath`、`objectKeyIsTenantSubjectHashFileId`、`presignedUrlsUseConfiguredTtl`、`stsPolicyIsScopedToObjectOrUserPrefix`、`stsDurationNotBelow900Seconds`、`unconfirmedFileCannotBeLinked`、`confirmRejectsSizeOrChecksumMismatch`、`tempObjectIsTaggedForLifecycle`、`archiveRemovesTempTagAndSetsArchived`、`deleteTombstonesBeforeObjectRemoval`、`deletedFileIsNotSignedAgain`、`deleteCannotBeUndoneByArchiveRetry`、`sharedSourceIsNotRemovedUntilNoLiveReference`。
- [ ] 运行 `mvn -pl agent-server -am -Dtest=FileServiceTest,FileControllerTest test`，预期 FAIL。
- [ ] 实现 files 归属与签名：tenant_id/subject 只取自 IdentityContext；object key `{tenantId}/{sha256(subject)}/{fileId}`，原文件名只入库；bucket 私有、启动时校验无公开策略。预签名 PUT 默认 20 分钟、GET 默认 1 小时；STS AssumeRole 的 session policy 只允许该对象或该用户前缀上的 `s3:PutObject`/`s3:GetObject`（分片上传附加所需 multipart 动作），DurationSeconds 默认 1200/3600、不低于 900。服务不代理文件字节。保留现有大小/类型校验，在签发前检查声明值、在 confirm 时用 `statObject` 核对实际大小和校验和。
- [ ] 生命周期与归档：TEMP 对象上传时带 `retention=temp` 标签，配置 MinIO 按标签 24 小时过期的生命周期规则（运维文档给出 `mc ilm` 配置）；归档在 CAS 状态检查后去除标签（或复制到 archive 前缀并校验 SHA-256），与 ARCHIVED 状态在同一事务边界内提交；后续 P3 必须在归档成功后才能提交长期文件来源。
- [ ] 删除：数据库先写 DELETE_PENDING，立即 404 且停止签发，再由 `FileDeletionJob` 删除对象并写 DELETED，可重试且不复活。移除单个来源引用不删除仍有有效引用的文件；用户明确删除文件时，先使文件及其全部引用不可用，再异步清理。API 文档写明已签发凭证不可撤回、最迟过期时生效。
- [ ] 用 Compose 中的 MinIO 运行 `mvn -Pmemory-mysql-it -pl agent-server -am -Dtest=MinioObjectStorageIntegrationTest test`，验证预签名直传、STS 越权访问被拒、标签和 statObject 行为；全部 PASS 后写入 operations.md 的 bucket 策略、生命周期规则与备份约束，定向提交 `feat: store user-owned files in MinIO with presigned access`。

## Task 8: 故障验收、恢复手册与主线里程碑

**Files:** 估算 1–1.5 人日。创建测试包下 `memory/MemoryFailureIntegrationTest.java`、`docker/docker-compose.memory-test.yml`（若 Task 1 已创建则完善）；完善 `docs/memory/operations.md`、创建 `docs/memory/phase-1-acceptance.md`；修改 README.md 中的验证入口与本阶段限制。

**Interfaces:** 消费前七项任务的公开内部接口和 HTTP 接口；不引入新产品 API。验收记录列出环境、提交、迁移版本、测试命令、结果和未通过项。

- [ ] 编写失败场景：Redis 完全不可用仍能读写数据库历史，取消在 pub/sub 不可用时由续期兜底；进程在检查点后退出可恢复到最后成功记录；旧 fence 无法覆盖；数据库写失败不启动模型；终态提交失败不能假报成功；MinIO 不可用时上传/下载明确失败而不写入有效引用；删除和附件归档重试不复活来源。数据库测试仅操作已确认的隔离测试 schema，不运行 FLUSHDB 或删除广域目录。
- [ ] 运行 `mvn -Pmemory-mysql-it -pl agent-server -am -Dtest=MemoryFailureIntegrationTest,TurnConcurrencyIntegrationTest test`，先记录待实现恢复行为的 FAIL，再完成必要修正。
- [ ] 编写恢复手册：全备与连续日志/归档应位于不同故障域，日志交付延迟须为 15 分钟 RPO 留出余量；MinIO bucket（版本化或复制）、生命周期规则、STS 配置与加密配置/密钥恢复方式一起列入演练，恢复后核对 `files` 与对象的一致性。恢复前回放受保护的删除清单，清单不完整的范围保持不可访问；恢复后重建 Redis，P2/P3 索引按各阶段进度重建。所有恢复步骤限定到隔离环境，无自动修改生产调度、备份配置或生产数据的脚本。
- [ ] JDK 21 下运行 `mvn -pl agent-spi -am test`、`mvn -pl agent-server -am test`、上述真实 MySQL 命令及 `mvn -pl agent-server -am -DskipTests package`；记录 PASS/FAIL/SKIPPED 原因，不把 H2 或构建成功当作 MySQL 并发验收通过。
- [ ] 在 acceptance.md 明确 P1 已实现/未实现、尚未验证的生产 RPO/RTO，以及延后到下一版本的重复计费问题。记录各任务提交与 V8/V9；mcp-tool 记录“待主线验收后适配”，不伪造副本提交或结果。定向提交 `test: verify durable memory failure and recovery paths`。

## 2. 自查与后续交付

- Spec 的 P0/P1 数据可靠性、归属、并发、取消、删除、附件和恢复规则分别由 Task 1–8 覆盖；P2/P3 和媒体转码等 IO 任务的取消实现不在本计划执行范围。
- 上述契约名只在本计划定义一次，后续任务复用；编码时内部私有方法可合理拆分，变更公共内部契约先更新计划及所有消费者。
- 任务 4 保留尾部 Usage 消费并处理断开重连；文件归属用户而非会话，因此上传可以早于首条聊天消息，无需预建空会话。
- 工作量：Task 1 1–1.5、Task 2 3–4.5、Task 3 1–1.5、Task 4 2–3.5、Task 5 1、Task 6 1–1.5、Task 7 3–4、Task 8 1–1.5，P1 合计 13–19 人日，与 spec 第 9 节一致；不含 20% 缓冲。初稿 6–9 人日已作废。
- 阶段完成后先在 agile-agent 验收，P2 计划再解决长会话预算；mcp-tool 按独立迁移编号/身份映射同步，不复制 OAuth2/SPI 架构覆盖企业实现。
- 本文件是执行计划；当前没有新增数据库迁移文件、产品实现、备份任务或运行验收结果。

## 3. 已执行的代码基线检查

2026-10-21 在 agile-agent 根目录使用 JDK 21 和隔离的本地 Redis/H2 执行（最初在 JDK 21.0.11 上运行；review 时在 Linux 上用 OpenJDK 21.0.12 复现，结果相同，不依赖特定 JDK 发行版）：

```sh
mvn -q -pl agent-server -am -Dtest=ChatMemoryTest,ChatControllerTest,OrchestratorTest,SessionRepositoryTest,DomainAgentTest test
```

ChatMemoryTest 4 项、ChatControllerTest 9 项、OrchestratorTest 7 项、SessionRepositoryTest 4 项、DomainAgentTest 8 项，合计 32 项，Failures=0、Errors=0、Skipped=0，命令退出码 0。这是现有代码的定向基线，不代表本计划功能、全量回归、真实 MySQL 或生产恢复目标已经验证。
