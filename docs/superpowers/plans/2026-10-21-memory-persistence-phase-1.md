# Memory Persistence Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 agile-agent 首先实现租户隔离、可恢复的原始会话与执行记录，完成旧历史迁移和受控附件归档，为后续分层记忆提供可信数据源。

**Architecture:** MySQL 是原始消息和执行状态的事实源，Redis 仅缓存有界近期窗口。HTTP 成功响应前持久化用户输入与执行票据；数据库租约和版本约束并发，流式输出保存检查点。附件以受控文件 ID 归档，旧 Redis 历史通过离线映射和快照迁移。

**Tech Stack:** JDK 21、Spring Boot 3.5、WebFlux/Reactor、R2DBC、MySQL、Flyway、Redis、JUnit 5、现有本地 Redis 测试设施。

**Spec:** `docs/superpowers/specs/2026-10-21-memory-runtime-design.md`

## Global Constraints

- agile-agent 是主线，设计、执行计划和验收记录均保存在本仓库；mcp-tool 仅在主线里程碑验收后适配移植。
- 使用 JDK 21 和现有 Spring Boot/WebFlux、R2DBC、Flyway、MySQL、Redis；首版不增加向量数据库、消息中间件或新的外部记忆服务。
- 首版不扩展公共 `agent-spi`；内部任务记录可以扩展，但 provider 的已发布方法签名保持兼容。
- 原始会话默认不自动过期；近期消息缓存闲置 7 天，用户概要缓存 1 小时属于后续 P3，P1 不创建概要缓存。
- 身份从服务端 IdentityContext 解析；租户缺失、归属不明或已删除的资源不能进入记忆读取或执行。
- 不修改任何已发布 Flyway 文件；实施前再次核对迁移版本。本文按当前 V7 之后的 V8/V9 规划。
- 被长期记忆实际引用或用户明确要求保留的附件归档；其他临时附件沿用默认 24 小时清理；P1 不增加多模态理解。
- 生产恢复目标为 RPO ≤ 15 分钟、RTO ≤ 4 小时；尚未通过真实演练时不能标记为已达标。
- 不记录访问令牌、API key 或模型内部推理链；工具记录只保存脱敏后的必要内容。
- 两个聊天入口与流式事件格式保持兼容；新增分页和请求幂等能力需写入接口说明。

## Review Focus

- 同一个 subject 出现在不同租户，包括只差大小写的身份值：不能共享会话、文件、缓存或执行票据；任务 1/3/4/7 覆盖。
- 请求已接受但客户端从未订阅 SSE、双重订阅或租约过期：只能运行一次并能回收；任务 2/4 覆盖。
- 最终 Completed 后仍有 Usage，或没有 Completed 就断流：正确记录使用量与失败状态；任务 4 覆盖。
- 旧 Redis 内容在扫描时变化、损坏或缺少可信租户映射：不能猜测归属或静默切换；任务 6 覆盖。
- 删除与缓存重建、附件清理、进程重启并发：不能复活记录或误删归档；任务 3/6/7/8 覆盖。

---

## 0. 边界、基线与文件结构

本计划覆盖 P0/P1，推荐由主代理直接逐任务实现；具体执行方法在实施前确认。暂不实施 LLM 摘要、Rules 执行器、用户图谱、向量检索、跨会话开关 UI 或 mcp-tool 移植。

调研基线：文档分支 `codex/memory-runtime-design`，产品代码基线 `600e3e8`，已存在设计提交 `e0a83de`、`1c8d299`。当前未跟踪的两张 `docs/mermaid-diagram*.png` 属于用户，不能加入提交或清理。

实施时使用 superpowers:using-git-worktrees 从 agile-agent 的已提交设计/计划分支建立隔离工作树；当前聊天工作目录属于 mcp-tool，不能让工具默认在 mcp-tool 上创建错误基线。只在 agile-agent 完成验收后安排副本适配。

生产包前缀为 `agent-server/src/main/java/io/github/sukimiya/agileagent/`，测试前缀为 `agent-server/src/test/java/io/github/sukimiya/agileagent/`。下文文件均相对仓库根目录，Java 包严格与目录对应。

| 单元 | 文件/目录 | 责任 |
| --- | --- | --- |
| 契约/配置 | `agent-server/src/main/java/io/github/sukimiya/agileagent/memory/{MemoryScope,MemoryModel,MemoryProperties,MemoryException}.java` | 范围、状态、票据、检查点/租约/缓存参数、稳定错误码 |
| 数据访问 | 同包 `{ConversationRepository,TurnRepository,ToolEventRepository}.java` | 会话/消息、租约/幂等、工具记录 |
| 执行生命周期 | 同包 `{TurnLifecycle,TurnRecorder,ExpiredTurnRecovery}.java` | 接受、执行、检查点、完成/失败/取消/过期 |
| 历史/缓存 | 同包 `{ConversationHistory,HotMemoryCache}.java` | 归属验证、有界缓存、原文分页与提示历史 |
| 迁移/清理 | 同包 `{LegacyHistoryImporter,MemoryMigrationCommand,MemoryMaintenance}.java` | 显式映射迁移、持久删除任务、重入恢复 |
| 附件 | 同包 `{AttachmentRepository,AttachmentArchiveService,ArchiveFileStore}.java` | 文件 ID、状态、校验、归档、受控下载/删除 |
| HTTP | `controller/{ChatController,MemoryArchiveController,UploadController}.java` | 接受请求、兼容响应、分页、归档入口 |
| 运行集成 | `agent/{Orchestrator,DomainTask,DomainAgent}.java`、`service/{SessionService,ChatMemory,UploadCleanupTask,FileStorageService}.java` | 连接现有业务流程；不改变 provider SPI |
| 数据迁移 | `agent-server/src/main/resources/db/migration/V8__memory_persistence.sql`、`V9__memory_attachments.sql` | 增量表和索引 |
| 测试基础 | `agent-server/src/test/java/io/github/sukimiya/agileagent/memory/{MemoryTestFixture,MemoryMySqlTestSupport}.java` | 隔离 H2/Redis、可控时间和真实 MySQL 测试 |
| 运维 | `docker/docker-compose.memory-test.yml`、`docs/memory/{api,operations,phase-1-acceptance}.md` | 测试服务、接口变化、恢复手册与证据 |

## 1. 固定的阶段行为

- 用户输入成功入库后才能提交 HTTP 200 SSE；缺模型配置继续返回既有 428 JSON；未知/他人资源返回 404；缺租户返回 403；写库不可用返回 503。
- 支持可选 `Idempotency-Key` 请求头，长度 1–128、仅允许 ASCII 字母数字和 `._:-`。同会话相同 key + 相同文本不重复执行；不同文本返回 409 `IDEMPOTENCY_CONFLICT`。
- 重复已完成请求重放已存文本与已观察到的 Usage；重复活跃请求返回 409 `TURN_IN_PROGRESS`；重复失败/取消/中断请求返回 409 `TURN_TERMINATED`，新执行需新 key。无 key 时生成新请求 ID，不凭相同文本去重。
- 同会话只允许一个活跃执行；状态为 ACCEPTED → RUNNING → COMPLETED/FAILED/CANCELLED/INTERRUPTED，终态不能降级或重新执行。
- 租约 60 秒，每 15 秒续期，恢复扫描 30 秒一次并在启动时执行；租约依据数据库时间。写入携带递增 fence，过期执行不能覆盖新执行。
- 检查点 5 秒或累计新增 8 KiB UTF-8 文本，先到者触发；正常完成提交完整文本。尾部恢复边界以最后成功检查点为准。
- 缓存上限为最近 100 条消息且序列化负载不超过 256 KiB；单条过大则绕过缓存。缓存值按会话 revision 校验，TTL 闲置 7 天。此上限不截断数据库原文，也不替代 P2 Token 预算。
- 兼容消息端点继续返回数组，默认最近 100 条、按 sequence 升序；`beforeSequence` 读取更早内容，limit 为 1–200。`X-Next-Cursor` 与 `X-Has-More` 指示翻页，写入 API 说明。
- 默认提示历史只包含已完成执行和有效 legacy 消息，排除当前 turn 和不完整执行，避免重复注入本次用户消息；P2 之前只供测试/灰度，不宣称已解决超长上下文。

## Task 1: 数据契约、范围隔离、迁移及测试基线

**Files:** 创建结构表中的契约/配置、`V8__memory_persistence.sql` 和两类测试基础；修改 `repository/SessionRepository.java`、`service/SessionService.java`、`agent-server/pom.xml`；新增 `memory/MemoryScopeTest.java`、`memory/MemorySchemaTest.java`、`memory/MemoryMySqlIntegrationTest.java`（均在对应生产/测试前缀下）。

**Interfaces:**
- `MemoryScope(String tenantId, String subject)`：拒绝空白范围；不改变大小写或修剪后当作另一个身份。
- `MemoryModel` 中定义 `TurnCommand(MemoryScope scope, String conversationId, String requestId, String text)`、`TurnTicket(MemoryScope scope, String conversationId, String turnId, long fence)`、`TokenUsage(boolean observed, long promptTokens, long completionTokens, long totalTokens)`、`AcceptedTurn(TurnTicket ticket, boolean replay, String answer, TokenUsage usage)`。
- 同文件定义 `StoredMessage(String id, long sequence, LlmClient.Message payload, TurnStatus status, Instant occurredAt)`、`HistoryPage(List<StoredMessage> items, Long nextBeforeSequence, boolean hasMore)`、`HistoryWindow(List<StoredMessage> items, boolean hasMore, long revision)`、上述 `TurnStatus` 和 `StorageState {PENDING_IDENTITY, LEGACY, DATABASE}`。legacy 消息的 status/occurredAt 可以为空，不能伪造当时执行状态。
- `SessionService.findOwned(String conversationId, MemoryScope scope): Mono<SessionRow>`、`listForUser(MemoryScope scope): Flux<SessionRow>`；SessionRow 增加租户、storageState、revision、deletedAt。所有产品调用改用范围接口，旧 String userId 接口不保留到 HTTP 路径。

- [ ] 写失败测试：`rejectsBlankScope`、`sameSubjectAcrossTenantsCannotRead`、`caseDistinctScopesRemainDistinct`、`oldSessionsRemainPendingIdentity`、`migrationKeepsExistingRows`。代表断言：`assertThatThrownBy(() -> new MemoryScope(" ", "u1")).isInstanceOf(IllegalArgumentException.class);`；两租户同 subject 查询分别只得自己的 session，旧记录 tenant 为空且 state=PENDING_IDENTITY。
- [ ] 运行 `JAVA_HOME=/Library/Java/JavaVirtualMachines/graalvm-21.jdk/Contents/Home mvn -pl agent-server -am -Dtest=MemoryScopeTest,MemorySchemaTest test`，记录新增契约不存在所致的 FAIL；既有基线失败单独记录。
- [ ] 实现契约和配置，追加 sessions 范围/状态/revision/删除字段；创建 chat_turns、chat_messages、chat_tool_events、memory_jobs 和 legacy_memory_imports。约束 `(conversation_id, request_id)`、`(conversation_id, sequence)` 唯一，所有数据读取和更新校验范围，身份比较不依赖不区分大小写的数据库排序规则；数据过长明确拒绝而不截断身份。messages 用 JSON 文本保留完整 Message 结构；legacy occurredAt 可为空。
- [ ] 新增仅本地测试用 MySQL 8.4 Compose 文件，独立服务/volume，端口仅绑定 loopback；`memory-mysql-it` Maven profile 必须使用明确测试库配置，缺配置失败，不连接生产。H2 运行快速测试，真实 MySQL 验证 Flyway、范围比较和并发约束；运行上面的测试及 `mvn -Pmemory-mysql-it -pl agent-server -am -Dtest=MemoryMySqlIntegrationTest test`，期望 PASS。
- [ ] 自查迁移兼容现有 H2 测试后，定向暂存本任务文件并提交 `feat: add scoped durable memory schema`；不提交测试凭证或用户文件。

## Task 2: 原子接受、幂等请求和执行租约

**Files:** 创建生产包下 `memory/{ConversationRepository,TurnRepository,TurnLifecycle,TurnRecorder,ExpiredTurnRecovery}.java`；新增测试包下 `memory/{TurnLifecycleTest,TurnConcurrencyIntegrationTest,ExpiredTurnRecoveryTest}.java`。

**Interfaces:**
- 消费 Task 1 的 MemoryModel 和作用域。
- `TurnLifecycle.accept(TurnCommand command): Mono<AcceptedTurn>`：事务内验证/创建归属会话、幂等检查、取得会话执行权、写用户消息和 ACCEPTED 执行；新会话 state=DATABASE。
- `TurnLifecycle.execute(TurnTicket ticket, Function<TurnRecorder, Flux<String>> operation): Flux<String>`；本任务同时实现 `TurnRecorder.delta(String delta): Mono<Void>` 和 `completed(String fullText, TokenUsage usage): Mono<Void>`，后续 Task 4 直接消费。
- `ExpiredTurnRecovery.runOnce(): Mono<Integer>`；`TurnRepository.renew(TurnTicket ticket): Mono<Boolean>`、`markTerminal(TurnTicket ticket, TurnStatus status): Mono<Boolean>`；事务/SQL 内部方法可私有拆分。

- [ ] 写失败测试：`sameKeyDoesNotDuplicateUserMessage`、`differentTextForSameKeyConflicts`、`onlyOneConcurrentTurnAccepted`、`expiredFenceCannotWrite`、`acceptedWithoutSubscriptionIsRecovered`、`terminalStateCannotRegress`。断言并发接受只成功一次，重复后用户消息数仍为 1；直接将测试租约时间置为过去再恢复，结果为 INTERRUPTED。
- [ ] 运行 `mvn -pl agent-server -am -Dtest=TurnLifecycleTest,ExpiredTurnRecoveryTest test`，预期新增实现缺失导致 FAIL。
- [ ] 实现 R2DBC TransactionalOperator 事务与数据库 CAS/唯一约束，以及 recorder 的持久检查点/完成写入；终态变化和会话 revision 一并提交。续期只对当前 fence 的活跃执行成功；续期失去执行权即停止后续模型/工具调用。资源清理通过被订阅的响应式生命周期执行，禁止依靠 `doFinally` 中未管理的异步写入。
- [ ] 用真实 MySQL 两个独立连接执行 `mvn -Pmemory-mysql-it -pl agent-server -am -Dtest=TurnConcurrencyIntegrationTest test`；快速测试命令同上，全部 PASS，确认已完成重试只重放、不重新调用模型。
- [ ] 定向提交 `feat: persist idempotent turns with fenced leases`。

## Task 3: 数据库历史、分页与可重建缓存

**Files:** 创建生产包下 `memory/{ConversationHistory,HotMemoryCache}.java`；修改 `service/ChatMemory.java`、`repository/SessionRepository.java`；新增测试包下 `memory/{ConversationHistoryTest,HotMemoryCacheTest}.java`，调整现有 `service/ChatMemoryTest.java`。

**Interfaces:**
- 消费 Task 1/2 的 scopes、票据、session revision 和消息。
- `ConversationHistory.page(MemoryScope scope, String conversationId, Long beforeSequence, int limit): Mono<HistoryPage>`。
- `ConversationHistory.contextBefore(TurnTicket ticket): Mono<List<LlmClient.Message>>`：排除该 turn，筛选完整历史。
- `HotMemoryCache.get(MemoryScope scope, String conversationId, long revision): Mono<HistoryWindow>`、`put(MemoryScope scope, String conversationId, long revision, HistoryWindow window): Mono<Void>`、`evict(MemoryScope scope, String conversationId): Mono<Void>`。窗口携带稳定消息 ID、sequence 和 hasMore，不能只缓存丢失这些信息的 Message 列表。

- [ ] 写失败测试：`redisMissErrorOrCorruptJsonFallsBackToDatabase`、`staleRevisionIsIgnored`、`cacheHasSevenDayIdleTtlAndSizeLimit`、`paginationKeepsStableIdsAndOrdering`、`currentAndIncompleteTurnsExcludedFromPrompt`、`deletedSessionCannotRefillCache`。测试至少存 205 条消息，断言翻页无重叠且 ID 不变，缓存条数 ≤100、负载 ≤262144 字节，数据库仍有全部消息。
- [ ] 运行 `mvn -pl agent-server -am -Dtest=ConversationHistoryTest,HotMemoryCacheTest test`，预期 FAIL。
- [ ] 实现按 scope/revision 的分页与缓存；缓存 key 含部署命名空间，近期分页可使用窗口，`contextBefore` 在 P1 直接读取数据库完整有效历史，P2 再加入预算，不能把缓存 100 条上限当成上下文截断。窗口截取到字节上限后必须保留 hasMore。通过授权后的轻量会话版本检查防止读取旧 revision；写入旧版本缓存不能遮蔽新版本。用户删除标记检查先于缓存命中。
- [ ] 运行上述命令及现有 ChatMemoryTest；使用可控时间/Redis TTL 检查，无需实际等待 7 天，全部 PASS。
- [ ] 定向提交 `feat: read conversation history from durable storage`。

## Task 4: 两个聊天入口与流式输出的可靠集成

**Files:** 创建生产包下 `web/MemoryExceptionHandler.java`；修改 `controller/ChatController.java`、`agent/Orchestrator.java`、`service/SessionService.java`；修改现有 Controller/Orchestrator 测试，新增测试包下 `memory/TurnStreamingIntegrationTest.java`。

**Interfaces:**
- 消费 Task 2 的 TurnRecorder/execute；execute 管理定时检查点、心跳和终止清理，不向 provider SPI 暴露 recorder。
- `Orchestrator.chat(AcceptedTurn accepted, String accessToken, IdentityContext identity, LlmClient llm): Flux<String>`；controller 在返回 200 前先解析身份、只读验证归属、解析模型，再 accept。
- GET 消息接口消费 Task 3 的 page；两个 POST 接收可选 Idempotency-Key；MemoryExceptionHandler 将稳定错误码映射到第 1 节 HTTP 状态，不输出底层错误/凭证。

- [ ] 写失败测试：`userPersistedBeforeSseAccepted`、`missingModelReturnsExact428WithoutTurn`、`bothEndpointsReplayCompletedTurn`、`cancelKeepsPartialAndMarksCancelled`、`timeoutAndDatabaseFailureNeverEmitSuccessfulDone`、`usageAfterCompletedIsNotLost`、`missingCompletedIsFailure`、`doubleSubscriptionRunsModelOnce`。至少用 8 KiB 多字节文本验证按 UTF-8 大小触发检查点。
- [ ] 运行 `mvn -pl agent-server -am -Dtest=ChatControllerTest,OrchestratorTest,TurnStreamingIntegrationTest test`，预期新增契约与断言 FAIL。
- [ ] 改造现有控制流，移除正常结束后的旧 memory.append。最终正文在 Completed 后暂存，继续消费尾部 Usage，只有上游正常结束且存在 Completed 才事务提交 COMPLETED。取消和错误保存检查点并标记状态；落库失败发稳定 error，协议可发送 done 作为流终止，但不得把它解释为成功。无订阅由过期扫描处理；已消费票据不得第二次启动。
- [ ] 已完成重放输出一条 delta、已观察到的 usage 和 done；OpenAI 转换仍输出 `[DONE]`。GET messages 默认数组/分页头行为按第 1 节；运行上述测试及 `ReactiveChainBlockHoundTest`，PASS 后确认无未管理后台订阅或重复模型调用。
- [ ] 定向提交 `feat: persist chat lifecycle across streaming failures`。

## Task 5: 可追溯工具执行记录与凭证过滤

**Files:** 创建生产包下 `memory/{ToolEventRepository,TurnToolRecorder,ToolRecordSanitizer}.java`；修改 `agent/{DomainTask,DomainAgent,Orchestrator}.java`；新增测试包下 `memory/TurnToolRecorderTest.java`，修改现有 DomainAgentTest 与 ExternalCapabilityIntegrationTest 所需内部构造。

**Interfaces:**
- 内部 DomainTask 增加 TurnTicket；公共 AgentExecutionContext 和 provider.execute 签名不变。
- `TurnToolRecorder.started(TurnTicket ticket, String capability, LlmClient.ToolCall call): Mono<Void>`、`finished(TurnTicket ticket, String capability, String callId, String result, boolean success): Mono<Void>`。
- `ToolRecordSanitizer.sanitize(String content): String`：过滤已知敏感字段与结构化凭证；调用日志不输出原始参数/结果。

- [ ] 写失败测试：`toolStartRecordedBeforeExecute`、`sameCallIdUpsertsWithinSameTurnOnly`、`tokenFieldsAreRedacted`、`lostFenceStopsToolExecution`、`externalSuccessDatabaseFailureIsNotRetried`。断言跨 turn 的相同 callId 是不同事件，失败记录不触发第二次 provider.execute。
- [ ] 运行 `mvn -pl agent-server -am -Dtest=TurnToolRecorderTest,DomainAgentTest test`，预期 FAIL。
- [ ] 记录 capability、工具名、调用 ID、脱敏参数、结果/错误类型、时间与来源；写开始记录失败不执行工具。工具成功但记录失败时保留不确定状态并终止，不尝试自动重放有副作用调用。工具结果进入本轮上下文前仍保持现有允许工具检查。
- [ ] 运行上面命令及 `ExternalCapabilityIntegrationTest`、`ReactiveChainBlockHoundTest`，全部 PASS；检查日志 fixture 不含测试令牌字面值。
- [ ] 定向提交 `feat: record scoped tool execution provenance`。

## Task 6: 旧 Redis 历史迁移、删除与持久清理

**Files:** 创建生产包下 `memory/{LegacyHistoryImporter,MemoryMigrationCommand,MemoryMaintenance}.java`；修改 `controller/ChatController.java`；新增测试包下 `memory/{LegacyHistoryImporterTest,MemoryDeletionTest}.java`；创建 `docs/memory/api.md` 和迁移说明初稿 `docs/memory/operations.md`。

**Interfaces:**
- `LegacyHistoryImporter.preview(Path mappingCsv): Mono<MigrationReport>`、`apply(Path mappingCsv): Mono<MigrationReport>`；MigrationReport/MappingRow 为该类嵌套 record，包含会话 ID、映射租户/subject、快照 hash、数量、状态和失败码，不含正文。
- CSV 列固定为 `conversationId,tenantId,subject,expectedLegacyUserId,evidenceRef`，重复/冲突映射拒绝；不能根据第一次登录者自动认领旧历史。
- `MemoryMaintenance.deleteConversation(MemoryScope scope, String conversationId): Mono<Void>`、`runPending(int limit): Mono<Integer>`；DELETE 成功返回 204，重复已删除返回 204，未知/他人返回 404。

- [ ] 写失败测试：`dryRunDoesNotMutate`、`unmappedHistoryRemainsHidden`、`sameSnapshotImportsOnce`、`corruptOrChangedSnapshotDoesNotCutOver`、`legacyToolStructureAndUnknownTimeArePreserved`、`deleteWinsOverImportAndCacheRefill`。两次 apply 后消息数量与原快照一致，occurredAt 不伪造，工具调用/结果顺序可还原。
- [ ] 运行 `mvn -pl agent-server -am -Dtest=LegacyHistoryImporterTest,MemoryDeletionTest test`，预期 FAIL。
- [ ] 实现禁用 Web 的 opt-in `memory-migration` 执行入口，默认 preview，apply 需显式参数；先离线停止旧写入者、排空活跃执行，再逐会话快照校验。数据库事务提交快照记录、稳定 legacy 消息 ID、计数/hash 与 storageState=DATABASE；保留旧 key 供观察，不自动删除。空 Redis 且存在旧 session 标记 SOURCE_MISSING，不能当作成功导入空历史。
- [ ] 删除事务先 tombstone 并作废活跃 fence，登记持久清理任务；读取/导入/续期/检查点均检查 tombstone。物理清理和缓存失效可重试，永久保留无正文的删除标记以防旧快照复活。运行上述测试与真实 MySQL 删除竞态测试，PASS。
- [ ] 文档写清停写窗口、preview/apply 参数、UNKNOWN 时间、未映射记录、分页变化及回滚边界；定向提交 `feat: migrate legacy history with resumable deletion`。

## Task 7: 附件归属、受控归档与清理隔离

**Files:** 创建 `agent-server/src/main/resources/db/migration/V9__memory_attachments.sql`；创建生产包下 `memory/{AttachmentRepository,AttachmentArchiveService,ArchiveFileStore}.java` 和 `controller/MemoryArchiveController.java`；修改 `controller/UploadController.java`、`service/{FileStorageService,UploadCleanupTask}.java`、`config/UploadProperties.java`、`agent-server/src/main/resources/application.yml`；新增测试包下 `memory/AttachmentArchiveServiceTest.java` 并更新现有 UploadControllerTest/FileStorageServiceTest/UploadCleanupTaskTest。

**Interfaces:**
- `AttachmentRef(String fileId, String name, long size, String sha256, AttachmentStatus status)`，状态 TEMP/PROMOTING/ARCHIVED/DELETE_PENDING/DELETED，定义于 AttachmentArchiveService 内；路径不由请求或 LLM 指定。
- `AttachmentArchiveService.archive(MemoryScope scope, String conversationId, String fileId): Mono<AttachmentRef>`、`delete(MemoryScope scope, String conversationId, String fileId): Mono<Void>`。
- 同类提供 `linkReference(MemoryScope scope, String conversationId, String fileId, String sourceType, String sourceId): Mono<Void>`、`unlinkReference(MemoryScope scope, String conversationId, String fileId, String sourceType, String sourceId): Mono<Void>`。V9 包含来源关联表；用户显式归档创建 USER_PIN 引用，P3 的记忆写入复用此接口，不先创建 P3 表。
- 新增 `POST /api/conversations/{id}/attachments/{fileId}/archive`、`DELETE /api/conversations/{id}/attachments/{fileId}`；现有下载入口保留，但通过数据库文件归属验证后读取，不能只校验路径字符。

- [ ] 写失败测试：`foreignTenantCannotUploadDownloadOrArchive`、`uploadBeforeFirstChatCreatesOwnedSession`、`archiveSurvivesTemporaryCleanup`、`concurrentArchiveAndCleanupCannotLoseSource`、`copyCrashCanResumeOrFailWithoutClaimingArchived`、`deleteCannotBeUndoneByPromotion`、`sharedSourceIsNotRemovedUntilNoLiveReference`。
- [ ] 运行 `mvn -pl agent-server -am -Dtest=AttachmentArchiveServiceTest,UploadControllerTest,UploadCleanupTaskTest test`，预期 FAIL。
- [ ] 为新上传分配服务端 fileId 并绑定 scope/session；上传前可创建该用户的空会话，不允许认领未映射旧会话。临时文件保留现有大小/类型校验和 24 小时 TTL；归档目录为 `${app.data-dir}/memory-archive`，必须位于临时 uploads 根目录之外。现有响应新增 fileId，原下载协议兼容；记忆源只引用 fileId/校验和，不能引用主机绝对路径。
- [ ] 归档先 CAS 标记 PROMOTING，再复制到服务端临时文件、校验 SHA-256、原子重命名并提交 ARCHIVED；清理器仅通过受控状态领取 TEMP 过期文件，不能递归删除含 PROMOTING/ARCHIVED 引用的目录。未登记的历史上传不自动视为归档来源。后续 P3 必须在归档成功后才能提交长期文件来源；运行上述测试与 FileStorageServiceTest，全部 PASS。
- [ ] 移除单个来源引用不删除仍有有效引用的文件；用户明确删除整个文件时，先使文件和其全部来源引用不可用，再异步清理，不能把“仍被引用”变成无法删除的理由。范围错误统一 404。写入 operations.md 的归档目录/备份约束，定向提交 `feat: retain authorized memory attachment sources`。

## Task 8: 故障验收、恢复手册与主线里程碑

**Files:** 创建测试包下 `memory/MemoryFailureIntegrationTest.java`、`docker/docker-compose.memory-test.yml`（若 Task 1 已创建则完善）；完善 `docs/memory/operations.md`、创建 `docs/memory/phase-1-acceptance.md`；修改 README.md 中的验证入口与本阶段限制。

**Interfaces:** 消费前七项任务的公开内部接口和 HTTP 接口；不引入新产品 API。验收记录列出环境、提交、迁移版本、测试命令、结果和未通过项。

- [ ] 编写失败场景：Redis 完全不可用仍能读写数据库历史；进程在检查点后退出可恢复到最后成功记录；旧 fence 无法覆盖；数据库写失败不启动模型；终态提交失败不能假报成功；删除和附件 promotion 重试不复活来源。数据库测试仅操作已确认的隔离测试 schema，不运行 FLUSHDB 或删除广域目录。
- [ ] 运行 `mvn -Pmemory-mysql-it -pl agent-server -am -Dtest=MemoryFailureIntegrationTest,TurnConcurrencyIntegrationTest test`，先记录待实现恢复行为的 FAIL，再完成必要修正。
- [ ] 编写恢复手册：全备与连续日志/归档应位于不同故障域，日志交付延迟须为 15 分钟 RPO 留出余量；附件归档、加密配置/密钥恢复方式一起列入演练。恢复前回放受保护的删除清单，清单不完整的范围保持不可访问；恢复后重建 Redis，P2/P3 索引按各阶段进度重建。所有恢复步骤限定到隔离环境，无自动修改生产调度、备份配置或生产数据的脚本。
- [ ] JDK 21 下运行 `mvn -pl agent-spi -am test`、`mvn -pl agent-server -am test`、上述真实 MySQL 命令及 `mvn -pl agent-server -am -DskipTests package`；记录 PASS/FAIL/SKIPPED 原因，不把 H2 或构建成功当作 MySQL 并发验收通过。
- [ ] 在 acceptance.md 明确 P1 已实现/未实现、尚未验证的生产 RPO/RTO、停写迁移窗口和回滚版本。记录各任务提交与 V8/V9；mcp-tool 记录“待主线验收后适配”，不伪造副本提交或结果。定向提交 `test: verify durable memory failure and recovery paths`。

## 2. 自查与后续交付

- Spec 的 P0/P1 数据可靠性、归属、并发、迁移、附件和恢复规则分别由 Task 1–8 覆盖；P2/P3 明确不在本计划执行范围。
- 上述契约名只在本计划定义一次，后续任务复用；编码时内部私有方法可合理拆分，变更公共内部契约先更新计划及所有消费者。
- 任务 4 保留尾部 Usage 消费，任务 6 明确迁移期间旧写入者停写，任务 7 处理上传早于首条聊天消息的现有使用方式。
- 阶段完成后先在 agile-agent 验收，P2 计划再解决长会话预算；mcp-tool 按独立迁移编号/身份映射同步，不复制 OAuth2/SPI 架构覆盖企业实现。
- 本文件是执行计划；当前没有新增数据库迁移文件、产品实现、备份任务或运行验收结果。

## 3. 已执行的代码基线检查

2026-10-21 在 agile-agent 根目录使用 JDK 21.0.11 和隔离的本地 Redis/H2 执行：

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/graalvm-21.jdk/Contents/Home mvn -q -pl agent-server -am -Dtest=ChatMemoryTest,ChatControllerTest,OrchestratorTest,SessionRepositoryTest,DomainAgentTest test
```

ChatMemoryTest 4 项、ChatControllerTest 9 项、OrchestratorTest 7 项、SessionRepositoryTest 4 项、DomainAgentTest 8 项，合计 32 项，Failures=0、Errors=0、Skipped=0，命令退出码 0。这是现有代码的定向基线，不代表本计划功能、全量回归、真实 MySQL 或生产恢复目标已经验证。
