# Memory Persistence M1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **修订（2026-10-21）：** 经用户与外部 reviewer review 后确定范围基线，本计划只覆盖 M1“可靠记忆底座”。MinIO 存储、附件归档、STS/分片上传、独立取消接口、服务端执行注册表、跨实例取消、断线续流和转码取消移入 spec 第 14 节“后置设计”，不在本计划中实施。保留：不迁移旧会话历史；活跃执行冲突返回 409 并带 turnId；以最终版本为准，不保留过渡兼容。

**Goal:** 在 agile-agent 实现 M1：租户隔离、可恢复的原始会话与执行记录、幂等、检查点、缓存回源数据库、终态持久化、附件归属修复、单实例保护，以及评测基线与基础指标，为 M2 分层记忆提供可信数据源。

**Architecture:** MySQL 是原始消息和执行状态的事实源，Redis 仅缓存有界近期窗口。HTTP 成功响应前持久化用户输入与执行票据；数据库租约和 fence 约束并发，流式输出保存检查点；执行跟随 SSE 订阅，断开即取消，终态由受管收尾器写入、恢复扫描兜底。单实例部署，数据库心跳检测冲突实例。附件保留现有本地存储，只补齐归属校验并去除路径泄露。旧 Redis 历史不读取、不迁移。

**Tech Stack:** JDK 21、Spring Boot 3.5、WebFlux/Reactor、R2DBC、MySQL、Flyway、Redis、Micrometer、JUnit 5、现有本地 Redis 测试设施。

**Spec:** `docs/superpowers/specs/2026-10-21-memory-runtime-design.md`

## Global Constraints

- agile-agent 是主线，设计、执行计划和验收记录均保存在本仓库；mcp-tool 仅在主线里程碑验收后适配移植。
- 使用 JDK 21 和现有 Spring Boot/WebFlux、R2DBC、Flyway、MySQL、Redis；不增加向量数据库、对象存储、消息中间件或新的外部记忆服务。
- 以最终版本为准：产品未正式部署，不迁移旧会话历史，不保留旧的过渡兼容接口。
- 单实例部署（replicas=1），不建设高可用；检测到同一数据库命名空间内的其他活跃实例时告警并拒绝接受新执行。
- 本期不扩展公共 `agent-spi`；内部任务记录可以扩展，但 provider 的已发布方法签名保持兼容。
- 原始会话消息默认不自动过期；近期消息缓存闲置 7 天；用户概要缓存属于 M2b，M1 不创建。
- 身份从服务端 IdentityContext 解析；租户缺失、归属不明或已删除的资源不能进入记忆读取、执行或附件访问。
- 不修改任何已发布 Flyway 文件；实施前再次核对迁移版本。本文按当前 V7 之后的 V8 规划。新迁移必须同时通过 H2（`MODE=MySQL`）与真实 MySQL：长文本用 `MEDIUMTEXT` 并在应用层限长，身份列用 `utf8mb4_bin` 排序规则或 `VARBINARY`。
- 附件保留现有本地存储与 24 小时临时清理；M1 不承诺附件原件永久可恢复（区别于消息永久保留）；不增加多模态理解。
- 生产恢复目标为 RPO ≤ 15 分钟、RTO ≤ 4 小时；尚未通过真实演练时不能标记为已达标。
- 不记录访问令牌、API key 或模型内部推理链；工具记录只保存脱敏后的必要内容；消息与材料标注来源类型。
- 两个聊天入口的流式事件格式保持不变；新增分页、请求幂等、409 turnId 和附件接口变化需写入接口说明。
- 重复计费（未带 conversationId 的首个请求重试）不是 M1 承诺，但是生产上线门槛，见 spec 第 13 节。

## Review Focus

- 同一个 subject 出现在不同租户，包括只差大小写的身份值：不能共享会话、附件、缓存或执行票据；任务 1/3/4/7 覆盖。
- 请求已接受但客户端从未订阅 SSE、双重订阅、客户端断开或租约过期：只能运行一次，终态符合 CANCELLED/INTERRUPTED/FAILED 映射；任务 2/4 覆盖。
- 终态收尾器失败或超时：恢复扫描兜底，不在 `doFinally` 中做未受管理的写入；任务 2/10 覆盖。
- 最终 Completed 后仍有 Usage，或没有 Completed 就断流：正确记录使用量与失败状态；任务 4 覆盖。
- 附件上传和下载越权、响应或错误体泄露服务器路径：任务 7 覆盖。
- 第二个实例连接同一数据库：告警并拒绝接受新执行；任务 8 覆盖。
- 指标不夸大：未实现的 Recall 不记 0ms，未启用的整理不记 0 积压；任务 9 覆盖。
- 删除与缓存重建、进程重启并发：不能复活记录；任务 3/6/10 覆盖。

---

## 0. 边界、基线与文件结构

本计划只覆盖 M1。不实施上下文预算、摘要、Recall、LLM + Rules、用户概要/图谱、跨会话开关、MinIO、附件归档、独立取消接口、执行注册表、断线续流或 mcp-tool 移植。

调研基线：文档分支 `codex/memory-runtime-design`，产品代码基线 `600e3e8`。当前未跟踪的 `docs/mermaid-diagram*.png` 属于用户，不能加入提交或清理。

实施时使用 superpowers:using-git-worktrees 从 agile-agent 的已提交设计/计划分支建立隔离工作树，确认工作目录是 agile-agent 而不是 mcp-tool。只在 agile-agent 完成验收后安排副本适配。

生产包前缀为 `agent-server/src/main/java/io/github/sukimiya/agileagent/`，测试前缀为 `agent-server/src/test/java/io/github/sukimiya/agileagent/`。下文文件均相对仓库根目录，Java 包严格与目录对应。

| 单元 | 文件/目录 | 责任 |
| --- | --- | --- |
| 契约/配置 | `memory/{MemoryScope,MemoryModel,MemoryProperties,MemoryException,SourceType}.java` | 范围、状态、票据、检查点/租约/缓存参数、稳定错误码、来源类型 |
| 数据访问 | 同包 `{ConversationRepository,TurnRepository,ToolEventRepository}.java` | 会话/消息、租约/幂等、工具记录 |
| 执行生命周期 | 同包 `{TurnLifecycle,TurnRecorder,TurnFinalizer,ExpiredTurnRecovery}.java` | 接受、执行、检查点、受管终态收尾、过期恢复 |
| 单实例保护 | 同包 `InstanceGuard.java` | 数据库心跳、冲突检测、告警与拒绝接受 |
| 历史/缓存 | 同包 `{ConversationHistory,HotMemoryCache}.java` | 归属验证、有界缓存、原文分页与提示历史 |
| 删除/清理 | 同包 `MemoryMaintenance.java` | 会话删除标记、持久删除任务、重入恢复 |
| 指标/评测 | 同包 `MemoryMetrics.java`；测试资源 `agent-server/src/test/resources/memory-eval/` | 基础指标、固定中立多轮评测集与基线 |
| HTTP | `controller/{ChatController,UploadController}.java`、`web/MemoryExceptionHandler.java` | 接受请求、分页、409 turnId、附件归属 |
| 运行集成 | `agent/{Orchestrator,DomainTask,DomainAgent}.java`、`service/{SessionService,FileStorageService}.java`；删除 `service/ChatMemory.java` | 连接现有业务流程；不改变 provider SPI |
| 数据迁移 | `agent-server/src/main/resources/db/migration/V8__memory_persistence.sql` | 增量表和索引 |
| 测试基础 | 测试包 `memory/{MemoryTestFixture,MemoryMySqlTestSupport}.java` | 隔离 H2/Redis、可控时间和真实 MySQL 测试 |
| 运维 | `docker/docker-compose.yml`（replicas=1）、`docker/docker-compose.memory-test.yml`、README.md、`docs/memory/{api,operations,phase-1-acceptance}.md` | 部署约束、测试服务、接口变化、恢复手册与证据 |

## 1. 固定的阶段行为

- 用户输入成功入库后才能提交 HTTP 200 SSE；缺模型配置继续返回既有 428 JSON；未知/他人资源（会话、附件）返回 404；缺租户返回 403；写库不可用返回 503；检测到实例冲突返回 503 `INSTANCE_CONFLICT`。
- 支持可选 `Idempotency-Key` 请求头，长度 1–128、仅允许 ASCII 字母数字和 `._:-`。承诺范围：在已建立的会话内（请求带已存在且属于当前用户的 conversationId），同一 key 不会被接受两次；相同 key + 相同文本不重复执行，不同文本返回 409 `IDEMPOTENCY_CONFLICT`。未带 conversationId 的首个请求重试不在承诺内（spec 6.4/13）。
- 重复已完成请求重放已存文本与已观察到的 Usage；重复活跃请求或同会话已有活跃执行时返回 409 `TURN_IN_PROGRESS`，响应体带 `turnId`，前端显示“正在生成”；重复失败/取消/中断请求返回 409 `TURN_TERMINATED`，新执行需新 key。无 key 时生成新请求 ID，不凭相同文本去重。M1 不提供按 turnId 续流或取消的接口。
- 状态为 ACCEPTED → RUNNING → COMPLETED / FAILED / CANCELLED / INTERRUPTED，终态不能降级或重新执行。终态映射：客户端断开或取消订阅 → CANCELLED；进程退出或失去执行权（租约过期、fence 失效）→ INTERRUPTED；普通执行失败 → FAILED；正常结束且有 Completed → COMPLETED。断开即取消，没有“客户端断开、后端继续运行”。
- 终态由 TurnFinalizer 写入：被订阅、有超时上限（建议 5 秒）的响应式完成阶段，带 fence；失败或超时时由恢复扫描在租约过期后标记 INTERRUPTED。禁止在 `doFinally` 中做 fire-and-forget 异步写库。已发出的外部写操作标记 UNCERTAIN，不声称已撤销。
- 租约 60 秒，每 15 秒续期，恢复扫描 30 秒一次并在启动时执行；租约依据数据库时间。写入携带递增 fence，过期执行不能覆盖新执行。
- 检查点 5 秒或累计新增 8 KiB UTF-8 文本，先到者触发；正常完成提交完整文本。尾部恢复边界以最后成功检查点为准。
- 缓存上限为最近 100 条消息且序列化负载不超过 256 KiB；单条过大则绕过缓存。缓存值按会话 revision 校验，TTL 闲置 7 天。此上限不截断数据库原文，也不替代 M2a Token 预算。
- 兼容消息端点继续返回数组，默认最近 100 条、按 sequence 升序；`beforeSequence` 读取更早内容，limit 为 1–200。`X-Next-Cursor` 与 `X-Has-More` 指示翻页，写入 API 说明。
- 默认提示历史只包含已完成执行，排除当前 turn 和不完整执行，避免重复注入本次用户消息；M2a 之前不宣称已解决超长上下文。
- 实例心跳 10 秒一次，30 秒未续期视为失效；同一部署命名空间内出现其他未失效心跳即为冲突。

## Task 1: 数据契约、租户隔离、迁移及测试基线

**Files:** 创建结构表中的契约/配置、`V8__memory_persistence.sql` 和两类测试基础；修改 `repository/SessionRepository.java`、`service/SessionService.java`、`agent-server/pom.xml`；新增 `memory/MemoryScopeTest.java`、`memory/MemorySchemaTest.java`、`memory/MemoryMySqlIntegrationTest.java`（均在对应生产/测试前缀下）。估算 1–1.5 人日。

**Interfaces:**
- `MemoryScope(String tenantId, String subject)`：拒绝空白范围；不改变大小写或修剪后当作另一个身份。
- `MemoryModel` 中定义 `TurnCommand(MemoryScope scope, String conversationId, String requestId, String text)`、`TurnTicket(MemoryScope scope, String conversationId, String turnId, long fence)`、`TokenUsage(boolean observed, long promptTokens, long completionTokens, long totalTokens)`、`AcceptedTurn(TurnTicket ticket, boolean replay, String answer, TokenUsage usage)`。
- 同文件定义 `StoredMessage(String id, long sequence, LlmClient.Message payload, SourceType source, TurnStatus status, Instant occurredAt)`、`HistoryPage(List<StoredMessage> items, Long nextBeforeSequence, boolean hasMore)`、`HistoryWindow(List<StoredMessage> items, boolean hasMore, long revision)` 和 `TurnStatus`。`SourceType` 至少区分 USER_INPUT、ASSISTANT、TOOL_RESULT、KNOWLEDGE_DOC、WEB、MODEL_SUMMARY；M1 只做标注，M2b 才做“用户亲口说”的语义判断（role=user 不等于用户亲口确认）。
- `SessionService.findOwned(String conversationId, MemoryScope scope): Mono<SessionRow>`、`listForUser(MemoryScope scope): Flux<SessionRow>`、`createOwned(String conversationId, MemoryScope scope, String title): Mono<SessionRow>`；SessionRow 增加租户、revision、deletedAt。所有产品调用改用范围接口，旧 String userId 接口删除。

- [ ] 写失败测试：`rejectsBlankScope`、`sameSubjectAcrossTenantsCannotRead`、`caseDistinctScopesRemainDistinct`、`missingTenantIsForbidden`、`longMessageBodyIsNotTruncated`。代表断言：`assertThatThrownBy(() -> new MemoryScope(" ", "u1")).isInstanceOf(IllegalArgumentException.class);`；两租户同 subject 查询分别只得自己的 session；只差大小写的 subject 在 H2 和 MySQL 上都不能互相看到；超过 64 KB 的正文完整保存，超过应用层上限明确拒绝。
- [ ] 在 JDK 21 下运行 `mvn -pl agent-server -am -Dtest=MemoryScopeTest,MemorySchemaTest test`，记录新增契约不存在所致的 FAIL；既有基线失败单独记录。
- [ ] 实现契约和配置，sessions 追加 tenant_id、revision、deleted_at；创建 chat_turns、chat_messages（含 source_type）、chat_tool_events、memory_jobs、service_instances。约束 `(conversation_id, request_id)`、`(conversation_id, sequence)` 唯一，所有数据读取和更新校验范围。身份列使用 `VARCHAR ... COLLATE utf8mb4_bin`（或 `VARBINARY`）；消息正文和工具结果用 `MEDIUMTEXT` 保存 JSON 文本并在应用层限长。先做 H2 兼容性验证，必要时按 vendor 拆分 Flyway `locations`，不能删去 MySQL 侧约束。旧会话行不做租户回填，新版本不读取旧数据。
- [ ] 新增仅本地测试用 MySQL 8.4 Compose 文件 `docker/docker-compose.memory-test.yml`，独立服务/volume，端口仅绑定 loopback；`memory-mysql-it` Maven profile 必须使用明确测试库配置，缺配置失败，不连接生产。H2 运行快速测试，真实 MySQL 验证 Flyway、范围比较和并发约束；运行上面的测试及 `mvn -Pmemory-mysql-it -pl agent-server -am -Dtest=MemoryMySqlIntegrationTest test`，期望 PASS。
- [ ] 定向暂存本任务文件并提交 `feat: add scoped durable memory schema`；不提交测试凭证或用户文件。

## Task 2: 原子接受、幂等请求、租约与终态收尾

**Files:** 创建生产包下 `memory/{ConversationRepository,TurnRepository,TurnLifecycle,TurnRecorder,TurnFinalizer,ExpiredTurnRecovery}.java`；新增测试包下 `memory/{TurnLifecycleTest,TurnFinalizerTest,TurnConcurrencyIntegrationTest,ExpiredTurnRecoveryTest}.java`。估算 2–3 人日。

**Interfaces:**
- 消费 Task 1 的 MemoryModel 和作用域。
- `TurnLifecycle.accept(TurnCommand command): Mono<AcceptedTurn>`：事务内验证/创建归属会话、幂等检查、取得会话执行权、写用户消息和 ACCEPTED 执行；活跃冲突抛出带 turnId 的 `MemoryException(TURN_IN_PROGRESS)`。
- `TurnLifecycle.execute(TurnTicket ticket, Function<TurnRecorder, Flux<String>> operation): Flux<String>`：执行跟随订阅；取消订阅映射为 CANCELLED，失去 fence 映射为 INTERRUPTED，错误映射为 FAILED，均交给 TurnFinalizer。
- `TurnRecorder.delta(String delta): Mono<Void>`、`completed(String fullText, TokenUsage usage): Mono<Void>`，供 Task 4 消费。
- `TurnFinalizer.finish(TurnTicket ticket, TurnStatus status, String partialText): Mono<Boolean>`：被订阅、`timeout` 有上限、带 fence 的终态写入；在取消场景通过受管的调度方式订阅（例如有界的专用 Scheduler 与 sink 队列），并记录成功/失败指标，不在 `doFinally` 中直接 fire-and-forget。
- `ExpiredTurnRecovery.runOnce(): Mono<Integer>`；`TurnRepository.renew(TurnTicket ticket): Mono<Boolean>`、`markTerminal(TurnTicket ticket, TurnStatus status): Mono<Boolean>`；事务/SQL 内部方法可私有拆分。

- [ ] 写失败测试：`sameKeyDoesNotDuplicateUserMessage`、`differentTextForSameKeyConflicts`、`onlyOneConcurrentTurnAccepted`、`activeConflictCarriesTurnId`、`expiredFenceCannotWrite`、`acceptedWithoutSubscriptionIsRecovered`、`terminalStateCannotRegress`、`unsubscribeMarksCancelled`、`lostFenceMarksInterrupted`、`executionErrorMarksFailed`、`finalizerTimeoutFallsBackToRecoveryScan`。断言并发接受只成功一次，重复后用户消息数仍为 1；直接将测试租约时间置为过去再恢复，结果为 INTERRUPTED；收尾器被注入超时后，恢复扫描把执行标记为 INTERRUPTED 且不覆盖已有终态。
- [ ] 运行 `mvn -pl agent-server -am -Dtest=TurnLifecycleTest,TurnFinalizerTest,ExpiredTurnRecoveryTest test`，预期新增实现缺失导致 FAIL。
- [ ] 实现 R2DBC TransactionalOperator 事务与数据库 CAS/唯一约束（参照 `KnowledgeDocumentRepository` 显式 `R2dbcTransactionManager` 的做法），以及 recorder 的持久检查点/完成写入；终态变化和会话 revision 一并提交。续期只对当前 fence 的活跃执行成功；续期失去执行权即停止后续模型/工具调用。
- [ ] 用真实 MySQL 两个独立连接执行 `mvn -Pmemory-mysql-it -pl agent-server -am -Dtest=TurnConcurrencyIntegrationTest test`；快速测试命令同上，全部 PASS，确认已完成重试只重放、不重新调用模型。
- [ ] 定向提交 `feat: persist idempotent turns with fenced leases and managed finalizer`。

## Task 3: 数据库历史、分页与可重建缓存

**Files:** 创建生产包下 `memory/{ConversationHistory,HotMemoryCache}.java`；删除 `service/ChatMemory.java` 及 `service/ChatMemoryTest.java`（新版本不读取旧 `chatmemory:*` key），修改 `repository/SessionRepository.java`；新增测试包下 `memory/{ConversationHistoryTest,HotMemoryCacheTest}.java`。估算 1–1.5 人日。

**Interfaces:**
- 消费 Task 1/2 的 scopes、票据、session revision 和消息。
- `ConversationHistory.page(MemoryScope scope, String conversationId, Long beforeSequence, int limit): Mono<HistoryPage>`。
- `ConversationHistory.contextBefore(TurnTicket ticket): Mono<List<LlmClient.Message>>`：排除该 turn，筛选完整历史。
- `HotMemoryCache.get(MemoryScope scope, String conversationId, long revision): Mono<HistoryWindow>`、`put(MemoryScope scope, String conversationId, long revision, HistoryWindow window): Mono<Void>`、`evict(MemoryScope scope, String conversationId): Mono<Void>`。窗口携带稳定消息 ID、sequence 和 hasMore。

- [ ] 写失败测试：`redisMissErrorOrCorruptJsonFallsBackToDatabase`、`staleRevisionIsIgnored`、`cacheHasSevenDayIdleTtlAndSizeLimit`、`paginationKeepsStableIdsAndOrdering`、`currentAndIncompleteTurnsExcludedFromPrompt`、`deletedSessionCannotRefillCache`、`legacyChatmemoryKeyIsIgnored`。测试至少存 205 条消息，断言翻页无重叠且 ID 不变，缓存条数 ≤100、负载 ≤262144 字节，数据库仍有全部消息。
- [ ] 运行 `mvn -pl agent-server -am -Dtest=ConversationHistoryTest,HotMemoryCacheTest test`，预期 FAIL。
- [ ] 实现按 scope/revision 的分页与缓存；缓存 key 含部署命名空间，`contextBefore` 在 M1 直接读取数据库完整有效历史，M2a 再加入预算。窗口截取到字节上限后必须保留 hasMore。写入旧版本缓存不能遮蔽新版本；删除标记检查先于缓存命中。
- [ ] 运行上述命令；使用可控时间/Redis TTL 检查，无需实际等待 7 天，全部 PASS。
- [ ] 定向提交 `feat: read conversation history from durable storage`。

## Task 4: 两个聊天入口与流式输出的可靠集成

**Files:** 创建生产包下 `web/MemoryExceptionHandler.java`；修改 `controller/ChatController.java`、`agent/Orchestrator.java`、`service/SessionService.java`；修改现有 ChatControllerTest/OrchestratorTest，新增测试包下 `memory/TurnStreamingIntegrationTest.java`。估算 1.5–2.5 人日。

**Interfaces:**
- 消费 Task 2 的 TurnRecorder/execute；execute 管理定时检查点、续期和终态收尾，不向 provider SPI 暴露 recorder。
- `Orchestrator.chat(AcceptedTurn accepted, String accessToken, IdentityContext identity, LlmClient llm): Flux<String>`；controller 在返回 200 前先解析身份、只读验证归属、解析模型，再 accept。
- GET 消息接口消费 Task 3 的 page；两个 POST 接收可选 Idempotency-Key；MemoryExceptionHandler 将稳定错误码映射到第 1 节 HTTP 状态，409 `TURN_IN_PROGRESS` 响应体带 turnId，不输出底层错误/凭证。

- [ ] 写失败测试：`userPersistedBeforeSseAccepted`、`missingModelReturnsExact428WithoutTurn`、`bothEndpointsReplayCompletedTurn`、`activeTurnReturns409WithTurnId`、`clientDisconnectStopsModelAndMarksCancelled`、`timeoutAndDatabaseFailureNeverEmitSuccessfulDone`、`usageAfterCompletedIsNotLost`、`missingCompletedIsFailure`、`doubleSubscriptionRunsModelOnce`、`retryWithoutConversationIdCreatesNewTurnOnBothEndpoints`（记录已知问题的现有行为，作为上线门槛修复前的对照，修复后改为断言不重复执行）。至少用 8 KiB 多字节文本验证按 UTF-8 大小触发检查点。
- [ ] 运行 `mvn -pl agent-server -am -Dtest=ChatControllerTest,OrchestratorTest,TurnStreamingIntegrationTest test`，预期新增契约与断言 FAIL。
- [ ] 改造现有控制流，移除正常结束后的旧 memory.append。最终正文在 Completed 后暂存，继续消费尾部 Usage，只有上游正常结束且存在 Completed 才事务提交 COMPLETED。客户端断开时取消信号向上传播，停止模型流和未开始的工具调用，由 TurnFinalizer 保存最后检查点并写 CANCELLED。落库失败发稳定 error，协议可发送 done 作为流终止，但不得把它解释为成功。无订阅由过期扫描处理；已消费票据不得第二次启动。
- [ ] 已完成重放输出一条 delta、已观察到的 usage 和 done；OpenAI 转换仍输出 `[DONE]`。运行上述测试及 `ReactiveChainBlockHoundTest`，PASS 后确认无未管理后台订阅或重复模型调用。
- [ ] 定向提交 `feat: persist chat lifecycle with disconnect-as-cancel`。

## Task 5: 可追溯工具执行记录、来源类型与凭证过滤

**Files:** 创建生产包下 `memory/{ToolEventRepository,TurnToolRecorder,ToolRecordSanitizer}.java`；修改 `agent/{DomainTask,DomainAgent,Orchestrator}.java`；新增测试包下 `memory/TurnToolRecorderTest.java`，修改现有 DomainAgentTest 与 ExternalCapabilityIntegrationTest 所需内部构造。估算 1 人日。

**Interfaces:**
- 内部 DomainTask 增加 TurnTicket；公共 AgentExecutionContext 和 provider.execute 签名不变。
- `TurnToolRecorder.started(TurnTicket ticket, String capability, LlmClient.ToolCall call): Mono<Void>`、`finished(TurnTicket ticket, String capability, String callId, String result, boolean success): Mono<Void>`、`markUncertain(TurnTicket ticket, String callId): Mono<Void>`。
- `ToolRecordSanitizer.sanitize(String content): String`：过滤已知敏感字段与结构化凭证；调用日志不输出原始参数/结果。

- [ ] 写失败测试：`toolStartRecordedBeforeExecute`、`sameCallIdUpsertsWithinSameTurnOnly`、`tokenFieldsAreRedacted`、`toolResultTaggedWithSourceType`、`lostFenceStopsToolExecution`、`externalSuccessDatabaseFailureIsNotRetried`、`cancelMarksInFlightToolCallUncertain`。断言跨 turn 的相同 callId 是不同事件，失败记录不触发第二次 provider.execute。
- [ ] 运行 `mvn -pl agent-server -am -Dtest=TurnToolRecorderTest,DomainAgentTest test`，预期 FAIL。
- [ ] 记录 capability、工具名、调用 ID、脱敏参数、结果/错误类型、来源类型、时间；写开始记录失败不执行工具。工具成功但记录失败时保留不确定状态并终止，不自动重放有副作用调用；断开或中断时已发出但未返回的调用标记 UNCERTAIN，不声称已撤销。工具结果进入本轮上下文前仍保持现有允许工具检查，其内容不获得指令权限。
- [ ] 运行上面命令及 `ExternalCapabilityIntegrationTest`、`ReactiveChainBlockHoundTest`，全部 PASS；检查日志 fixture 不含测试令牌字面值。
- [ ] 定向提交 `feat: record scoped tool execution provenance`。

## Task 6: 会话删除与持久清理

**Files:** 创建生产包下 `memory/MemoryMaintenance.java`；修改 `controller/ChatController.java`（实现当前为空的 `DELETE /api/conversations/{id}`）；新增测试包下 `memory/MemoryDeletionTest.java`；创建 `docs/memory/api.md` 和 `docs/memory/operations.md` 初稿。估算 1–1.5 人日。

**Interfaces:**
- `MemoryMaintenance.deleteConversation(MemoryScope scope, String conversationId): Mono<Void>`、`runPending(int limit): Mono<Integer>`；DELETE 成功返回 204，重复已删除返回 204，未知/他人返回 404。

- [ ] 写失败测试：`deleteHidesConversationImmediately`、`deleteInterruptsActiveTurnAndInvalidatesFence`、`deleteWinsOverCacheRefill`、`deleteRetryIsIdempotent`、`deleteForeignConversationReturnsNotFound`、`deletedConversationAttachmentsAreNotDownloadable`。
- [ ] 运行 `mvn -pl agent-server -am -Dtest=MemoryDeletionTest test`，预期 FAIL。
- [ ] 删除事务先 tombstone 并递增 fence 使活跃执行失去执行权（终态 INTERRUPTED），登记持久清理任务；读取/续期/检查点/附件下载均检查 tombstone。物理清理（消息、缓存、该会话的本地附件目录）可重试，永久保留无正文的删除标记，防止后台任务或缓存重建复活。运行上述测试与真实 MySQL 删除竞态测试，PASS。
- [ ] 文档写清分页、Idempotency-Key 承诺范围、409 turnId 和删除接口，并注明新版本不读取旧 Redis 历史；定向提交 `feat: delete conversations with durable cleanup`。

## Task 7: 附件归属修复（上传与下载）与路径去除

现状（已核对代码）：附件存于 `${app.upload.dir}/{sessionId}/{name}`。上传 `UploadController.java:77-98` 直接使用请求中的 sessionId，不校验会话归属；下载 `:46-75` 只检查路径字符，不校验归属。上传成功响应 `FileStorageService.Ref(name, path, size)` 返回服务器绝对路径（`FileStorageService.java:20,77`）；`STORAGE_ERROR` 响应体返回 `e.getMessage()`（`UploadController.java:96-97`）。本任务保留现有本地存储与清理任务，不引入新的存储架构。

**Files:** 修改 `controller/UploadController.java`、`service/FileStorageService.java`；更新测试包下 `controller/UploadControllerTest.java`、`service/FileStorageServiceTest.java`、`controller/FileStorageServiceTestData.java`，以及 `docs/memory/api.md`。估算 1–1.5 人日。

**Interfaces:**
- 上传与下载都先解析 `MemoryScope`（缺租户 403），再调用 `SessionService.findOwned`；上传时会话不存在则 `createOwned` 后写入，会话属于他人或已删除返回 404；下载不匹配统一 404。
- `FileStorageService.Ref` 改为 `Ref(String name, long size)`，不含路径；响应可附带下载所需的 sessionId 与 name，不含任何服务器路径。
- 错误响应只返回稳定错误码和通用说明：`STORAGE_ERROR` 不再携带 `e.getMessage()`，原始异常写服务端日志；`FILE_REJECTED`/`MIME_MISMATCH` 的 reason 保留大小/类型信息，但不得包含路径。

- [ ] 写失败测试：`uploadToForeignSessionReturns404`、`uploadWithoutTenantReturns403`、`uploadCreatesOwnedSessionWhenAbsent`、`downloadForeignSessionReturns404`、`sameSubjectOtherTenantCannotDownload`、`uploadResponseHasNoServerPath`、`storageErrorBodyHasNoExceptionMessageOrPath`、`deletedSessionFilesNotDownloadable`。断言响应 JSON 中不存在 `path` 字段，且不包含 `app.upload.dir` 的任何前缀。
- [ ] 运行 `mvn -pl agent-server -am -Dtest=UploadControllerTest,FileStorageServiceTest,UploadCleanupTaskTest test`，预期新增断言 FAIL。
- [ ] 实现归属校验与响应/错误体改造；`UploadCleanupTask` 的 24 小时清理保持不变。依赖返回路径拼接消息的前端改为使用文件名与下载引用，在 api.md 写明变化。
- [ ] 在 api.md 与 operations.md 明确：M1 不承诺附件原件永久可恢复，临时附件 24 小时后清理，这与会话消息默认永久保留不同；归档与 MinIO 见 spec 14.1。运行上述测试，PASS 后定向提交 `fix: enforce attachment ownership and stop leaking server paths`。

## Task 8: 单实例保护与部署配置

**Files:** 创建生产包下 `memory/InstanceGuard.java`；修改 `memory/TurnLifecycle.java`（接受前检查）、`docker/docker-compose.yml`、README.md；新增测试包下 `memory/InstanceGuardTest.java`。估算 0.5–1 人日。

现状：`docker/docker-compose.yml` 的 `agile-agent-server` 使用固定 `container_name`，未声明副本数；`Jenkinsfile` 只构建和推送镜像，不负责部署；仓库内没有其他编排清单。README 关于 `LLM_CONFIG_SECRET` 的说明提到“every server instance”，与单实例约束不一致。

**Interfaces:**
- `InstanceGuard.heartbeat(): Mono<Void>`（启动时注册、每 10 秒续期 `service_instances`）、`conflict(): boolean`（同一部署命名空间内存在其他 30 秒内有心跳的实例）。
- TurnLifecycle.accept 在冲突时返回 `MemoryException(INSTANCE_CONFLICT)` → 503；健康检查暴露冲突状态；冲突时输出 ERROR 级告警日志与指标 `memory.instance.conflict`。

- [ ] 写失败测试：`secondInstanceInSameNamespaceIsDetected`、`conflictRefusesNewTurns`、`runningTurnsFinishNormallyDuringConflict`、`staleHeartbeatIsNotConflict`、`otherNamespaceIsNotConflict`、`conflictClearsWhenPeerStops`。
- [ ] 运行 `mvn -pl agent-server -am -Dtest=InstanceGuardTest test`，预期 FAIL。
- [ ] 实现心跳与冲突检测（数据库时间）；`docker/docker-compose.yml` 的 `agile-agent-server` 与 `agile-agent-server-local-mock` 增加 `deploy.replicas: 1` 并加注释说明本期只支持单实例；README 增加“部署限制：单实例（replicas=1），多实例不受支持”，并把 “every server instance” 一句改为不暗示多实例。说明如果只告警不拒绝，多实例不受支持且不承诺取消可靠性。
- [ ] 运行上述测试，PASS 后定向提交 `feat: guard single-instance deployment`。

## Task 9: 评测集、基线与基础指标

**Files:** 创建测试资源 `agent-server/src/test/resources/memory-eval/{cases.jsonl,README.md}`、测试包下 `memory/MemoryEvalBaselineTest.java`、生产包下 `memory/MemoryMetrics.java`；创建 `docs/memory/eval-baseline.md`。估算 1.5–2 人日。

**Interfaces:**
- 评测集：固定的中立多轮对话（不含真实用户或企业数据），覆盖指代追问、早期细节、冲突修正、长消息、工具结果、粘贴文章/引用/假设（为 M2b 防投毒语义预留）等类别；每个用例标注所需能力（M1 / M2a / M2b）。
- `MemoryEvalBaselineTest` 在 M1 运行全部用例并保存基线结果（通过/未通过/不适用及原因），不要求 M2a/M2b 能力的 Recall 用例通过，只要求 M1 能力用例（持久化、分页、隔离、终态）通过。
- `MemoryMetrics` 基础指标：接受/终态计数（按 COMPLETED/FAILED/CANCELLED/INTERRUPTED）、检查点延迟、缓存命中/回源、收尾器失败次数、实例冲突；“历史消息注入条数/Token”与“长期记忆召回条数”分开计数；Recall 延迟在未实现时记为“不适用”（不上报或上报为 N/A 标记），不得记为 0ms；没有整理任务时，整理积压指标标记为“功能未启用”，不得记为 0。

- [ ] 写失败测试：`baselineFileIsWrittenWithPerCaseStatus`、`unimplementedRecallCasesAreNotApplicableNotFailed`、`historyInjectionCountedSeparatelyFromRecall`、`recallLatencyIsNotReportedAsZero`、`consolidationBacklogShowsDisabledWithoutJobs`。
- [ ] 运行 `mvn -pl agent-server -am -Dtest=MemoryEvalBaselineTest test`，预期 FAIL。
- [ ] 实现评测集与指标；在 eval-baseline.md 记录环境、提交、模型配置（或 fake LLM）、每类用例结果与“不适用”说明，作为 M2 对比基线。运行上述测试 PASS 后定向提交 `test: add neutral memory eval set and baseline metrics`。

## Task 10: 故障验收、恢复手册与 M1 里程碑

**Files:** 创建测试包下 `memory/MemoryFailureIntegrationTest.java`；完善 `docker/docker-compose.memory-test.yml`、`docs/memory/operations.md`，创建 `docs/memory/phase-1-acceptance.md`；修改 README.md 中的验证入口与本阶段限制。估算 1–1.5 人日。

**Interfaces:** 消费前九项任务的内部接口和 HTTP 接口；不引入新产品 API。验收记录列出环境、提交、迁移版本、测试命令、结果和未通过项。

- [ ] 编写失败场景：Redis 完全不可用仍能读写数据库历史；进程在检查点后退出可恢复到最后成功记录并标记 INTERRUPTED；收尾器失败由恢复扫描兜底；旧 fence 无法覆盖；数据库写失败不启动模型；终态提交失败不能假报成功；删除重试不复活。数据库测试仅操作已确认的隔离测试 schema，不运行 FLUSHDB 或删除广域目录。
- [ ] 运行 `mvn -Pmemory-mysql-it -pl agent-server -am -Dtest=MemoryFailureIntegrationTest,TurnConcurrencyIntegrationTest test`，先记录待实现恢复行为的 FAIL，再完成必要修正。
- [ ] 编写恢复手册：全备与连续日志/归档应位于不同故障域，日志交付延迟须为 15 分钟 RPO 留出余量；加密配置/密钥恢复方式一起列入演练。恢复前回放受保护的删除清单，清单不完整的范围保持不可访问；恢复后重建 Redis。写明 M1 不包含附件原件的长期恢复。所有恢复步骤限定到隔离环境，无自动修改生产调度、备份配置或生产数据的脚本。
- [ ] JDK 21 下运行 `mvn -pl agent-spi -am test`、`mvn -pl agent-server -am test`、上述真实 MySQL 命令及 `mvn -pl agent-server -am -DskipTests package`；记录 PASS/FAIL/SKIPPED 原因，不把 H2 或构建成功当作 MySQL 并发验收通过。
- [ ] 在 acceptance.md 明确 M1 已实现/未实现、尚未验证的生产 RPO/RTO、单实例限制、附件不保证长期恢复，以及作为上线门槛的重复计费问题（两个入口均需修复并验证）。记录各任务提交与 V8；mcp-tool 记录“待主线验收后适配”，不伪造副本提交或结果。定向提交 `test: verify durable memory failure and recovery paths`。

## 2. 自查与后续交付

- Spec 的 M1 范围（持久化、隔离、幂等、检查点、缓存回源、附件归属、终态、单实例、评测与指标、恢复）分别由 Task 1–10 覆盖；M2a/M2b 与 spec 第 14 节后置设计不在本计划执行范围。
- 上述契约名只在本计划定义一次，后续任务复用；编码时内部私有方法可合理拆分，变更公共内部契约先更新计划及所有消费者。
- 任务 4 保留尾部 Usage 消费；任务 7 保持上传早于首条聊天消息的现有使用方式（上传时为当前用户创建空会话）。
- 工作量：Task 1 1–1.5、Task 2 2–3、Task 3 1–1.5、Task 4 1.5–2.5、Task 5 1、Task 6 1–1.5、Task 7 1–1.5、Task 8 0.5–1、Task 9 1.5–2、Task 10 1–1.5，M1 合计 11.5–17 人日，含约 20% 缓冲约 14–21 人日；与 spec 第 9 节一致。
- 阶段完成后先在 agile-agent 验收，M2a 计划再解决长会话预算；mcp-tool 按独立迁移编号/身份映射同步，不复制 OAuth2/SPI 架构覆盖企业实现。
- 本文件是执行计划；当前没有新增数据库迁移文件、产品实现、备份任务或运行验收结果。

## 3. 已执行的代码基线检查

2026-10-21 在 agile-agent 根目录使用 JDK 21 和隔离的本地 Redis/H2 执行（最初在 JDK 21.0.11 上运行；review 时在 Linux 上用 OpenJDK 21.0.12 复现，结果相同，不依赖特定 JDK 发行版）：

```sh
mvn -q -pl agent-server -am -Dtest=ChatMemoryTest,ChatControllerTest,OrchestratorTest,SessionRepositoryTest,DomainAgentTest test
```

ChatMemoryTest 4 项、ChatControllerTest 9 项、OrchestratorTest 7 项、SessionRepositoryTest 4 项、DomainAgentTest 8 项，合计 32 项，Failures=0、Errors=0、Skipped=0，命令退出码 0。这是现有代码的定向基线，不代表本计划功能、全量回归、真实 MySQL 或生产恢复目标已经验证。
