# Memory 运维说明（M1）

> M1 运维说明。故障行为由 `MemoryFailureTest`（H2）与 `MemoryFailureIntegrationTest`（MySQL 8.4）验证；恢复手册只用于隔离环境演练，生产恢复目标尚未通过演练，见 [phase-1-acceptance.md](phase-1-acceptance.md)。

## 附件存储与保留

- 附件保存在本地目录 `${app.upload.dir}/{conversationId}/`。目录名就是会话 ID（小写规范 UUID），同一会话的附件在同一目录，不同会话不会共用目录。
- **M1 不承诺附件原件可永久恢复。** `UploadCleanupTask` 在启动时和此后每小时运行一次，删除最后修改时间早于 `app.upload.ttl-hours`（默认 24 小时）的会话目录。会话消息默认永久保留：消息正文和其中提到的文件名会保留，附件原件可能已被清理。
- 清理任务只处理名称符合会话 ID 规则的目录。其他目录（例如 M1 之前按 sanitize 规则创建的 `conv-1`、`a_b` 等）不会被自动删除，需要运维确认后手动清理。M1 之前的这类旧目录也无法再通过下载接口访问。
- 附件不在 M1 的数据库备份与恢复范围内（RPO/RTO 只覆盖会话、消息与执行记录）。归档与 MinIO 存储见 spec 14.1。

## 会话删除清理（M2b 起全部为软删除）

- **软删除原则（2026-10-27 用户确定）：** 会话、执行、消息等数据删除后仍保留在数据库中、可以查询；删除只把 `is_valid` 置 0（V13 迁移为相关表增加该列），所有正常读取、Recall、提示词和用户 API 都排除 `is_valid = 0` 的数据，所以已删除的内容不会再到达模型或用户接口。
- `DELETE /api/conversations/{id}` 在一个事务内：`sessions.is_valid = 0` 并写 `deleted_at`（标题保留）、使执行权失效、删除该会话尚未执行（PENDING）的 `INDEX_CHUNKS` 任务（任务记录不是会话数据）、把来自该会话的条目来源置为无效、把失去全部有效来源的条目和该会话的会话级条目置为 INACTIVE 并写 `source_deleted_at`（不再被召回，成为清除候选），并在 `memory_jobs` 登记 `CONVERSATION_CLEANUP` 任务（`dedupe_key` 为 `cleanup:<conversationId>`）。后台处理器由 `MemoryMaintenance` 注册给 `MemoryJobWorker`，在启动时以及此后每隔 `app.memory.cleanup-scan-interval`（默认 30 秒）扫描一次；`app.memory.recovery-enabled=false` 时与恢复扫描一起关闭。
- 清理任务内容（全部是 `UPDATE ... SET is_valid = 0`，不删除行）：`chat_messages`、`chat_tool_events`、`chat_turns`、`memory_chunks`、`memory_summaries`、`memory_recall_events`；Redis 中该会话的缓存条目替换为删除标记；已有的用户概要快照按剩余条目重写。`memory_index_progress` 保持不变；附件目录 `${app.upload.dir}/{conversationId}/` **不随会话删除而删除**（用户 2026-10-27 确认保持该行为；下载按会话归属判定，已删除会话一律 404；附件仍受 `app.upload.ttl-hours` 的临时清理约束）。清理前会再次确认该会话已标记删除。
- 失败重试：失败的任务回到 PENDING，退避时间从 30 秒开始指数增长，上限 30 分钟，不限次数。`last_error` 只记录异常类型，不保存消息或路径，原始异常只写服务端日志（`memory.job failed ... type=CONVERSATION_CLEANUP`）。进程在清理中途退出时，任务停留在 RUNNING，5 分钟租约到期后会被重新领取。
- 排查：`SELECT status, attempts, last_error, available_at FROM memory_jobs WHERE job_type = 'CONVERSATION_CLEANUP' AND status <> 'DONE'`。长期 PENDING 且 attempts 持续增长，通常是数据库或 Redis 不可用。
- 实例冲突（`memory.instance.conflict` = 1）期间不领取任何后台任务（包括清理），冲突解除后继续；任务和尝试次数不受影响。
- 删除标记（`sessions.is_valid = 0` 与 `deleted_at`）与已完成的任务记录永久保留，不要手动清除，否则同一会话 ID 可能被重新创建，或无法确认清理已完成。

### 派生索引清除（M2b 起，`app.memory.purge.*`）

- `MemoryIndexPurger` 每 `app.memory.purge.interval`（默认 1 小时）扫描一次（`app.memory.purge.enabled` 与 `app.memory.recovery-enabled` 同时开启时）。候选：① 有 `source_deleted_at` 且状态不是 ACTIVE、DELETED（用户删除）或 FORGOTTEN（用户遗忘）的记忆条目——用户删除和遗忘的条目永久保留；② 已删除会话中 `is_valid = 0` 的原文片段（`memory_chunks`）与轮次记录（`memory_summaries`）。
- **“清除”的含义：** 物理删除上述**派生索引行**（条目连同其来源与关系、片段、轮次记录）。原始会话数据（会话、执行、消息、工具记录）和召回记录（`memory_recall_events`）从不物理删除，只保持 `is_valid = 0`。这样数据库与 MySQL ngram 全文索引里不会长期残留已删除会话的副本，原始数据仍可按软删除原则查询。
- 保留期按**召回引用数**分档，从来源被删除的时间算起（条目用 `source_deleted_at`，片段与轮次记录用会话的 `deleted_at`，数据库时间）。引用数 = 召回记录中指向该对象的不同轮次数（条目 `kind=ITEM`，片段 `CHUNK`，轮次记录 `SUMMARY` 按轮次 id），含已软删除的召回记录；条目再减去手动减少的数量（`recall_ref_offset`），最低为 0。
- 分档在 `application.yml` 中配置，档数只靠配置即可改变（例如 5 档或 9 档），由 `RetentionTiers.retentionFor(count)` 统一解析：

  ```yaml
  app:
    memory:
      purge:
        enabled: true
        interval: 1h
        batch: 200          # 每页候选数（键集分页，一次扫描处理全部候选）
        recall-record-ttl: 30d   # 召回记录过期时间（只标记、不删除；过期后不再计入引用数）
        tiers:              # 从 from-count 起（含）保留 keep；升序、首档从 0 开始、不重叠
          - from-count: 0
            keep: 1d
          - from-count: 2
            keep: 7d
          - from-count: 5
            keep: 30d
  ```

  配置不合法（为空、首档不从 0 开始、`from-count` 不严格递增、`keep` 不为正）时服务启动失败并给出具体档位。
- **召回记录过期**（用户 2026-10-27 决定）：同一周期任务每次先把创建时间早于 `app.memory.purge.recall-record-ttl`（默认 `30d`）的召回记录标记为过期（写 `memory_recall_events.expired_at`，**从不删除**）。过期记录不再计入引用数，因此保留档位会随时间下降；同时把条目的手动减少量（`recall_ref_offset`）截到剩余有效引用数，之后的新召回从 0 起重新计数。每轮记忆解释接口仍返回过期记录（它们只是不再计数）。
- 指标：`memory.purge.purged{kind=ITEM|CHUNK|SUMMARY}`、`memory.recall.records.expired`；有清除或过期时写一行 `memory.purge items=… chunks=… summaries=… expiredRecallRecords=…` INFO 日志（不含内容）。
- 恢复后的行为：从备份恢复出的已清除行仍是无效或 INACTIVE 状态，下一次扫描会按同样规则再次清除，不需要单独的清除清单。

## 对话中遗忘与恢复（M2b，`app.memory.forget.*`）

- 用户本人当前输入是“忘记…/把…忘掉”（恢复：“记起来…/把…记起来”）时，在路由前由确定性规则识别并直接回答，不调用模型；规则与边界见 `docs/memory/api.md`。
- 配置：

  ```yaml
  app:
    memory:
      forget:
        pending-ttl: 5m       # 多条匹配时编号选择的有效期（数据库时间）
        max-candidates: 5     # 最多列出的候选数
        min-score: 0.5        # 条目至少包含话题切分词的比例
        scan-limit: 500       # 每次扫描该范围内最近更新的条目数
  ```

- 数据：遗忘只把条目状态改为 `FORGOTTEN`（版本号加 1，不置无效、不删除），写 `memory_audit_log`（`FORGET_IN_CHAT` / `RESTORE_IN_CHAT` / `RESTORE`，不含正文）和 `memory.audit` 日志；待选编号存于 `memory_command_pending`（每会话一条，下一轮即消费，过期行在该会话下一轮时删除）；被识别为指令的轮次在 `chat_turns.memory_command` 标记，记忆判断跳过这些轮次。
- 日志：`memory.command kind=FORGET|RESTORE candidates=n`（不含话题和正文）。

### 后台任务（M2a 起）

- `memory_jobs.job_type` 取值见 `JobType`：`CONVERSATION_CLEANUP`、`INDEX_CHUNKS`、`SUMMARIZE`。同一 `(job_type, dedupe_key)` 只登记一次；`payload` 是任务参数 JSON。
- 每个 COMPLETED 的轮次在写入终态的同一事务内登记一个 `INDEX_CHUNKS` 任务（`dedupe_key` 为 turnId）。终态写入回滚时任务也不存在。
- 没有注册处理器的类型不会被领取，保持 PENDING、`attempts = 0`。`INDEX_CHUNKS` 的处理器从 M2a Task 5 起始终注册（与 `app.memory.recall.enabled` 无关），升级前累积的任务会按登记顺序逐个处理。
- 排查：`SELECT job_type, status, COUNT(*) FROM memory_jobs GROUP BY job_type, status`。

### 删除审计日志（受保护的删除清单）

- 每次删除事务**提交后**，专用 logger `memory.deletion-audit` 以 INFO 级别写一行 JSON，这个日志流就是恢复时使用的删除清单：

  ```
  14:15:29.521 [] [] INFO  m.deletion-audit - {"event":"CONVERSATION_DELETED","tenantId":"tenant-a","subject":"user-123","conversationId":"0b7e4c1a-5f2d-4c3e-9a61-2d8f0e6b7c45","deletedAt":"2026-10-23T14:15:29.521226","revision":3}
  ```

  默认日志格式会把 logger 名缩写为 `m.deletion-audit`，筛选时请按 `"event":"CONVERSATION_DELETED"` 匹配。
- 字段只有：`event`、`tenantId`、`subject`、`conversationId`、`deletedAt`、`revision`。`deletedAt` 和 `revision` 是同一事务写入 `sessions` 的值；`deletedAt` 是数据库时间，与 `sessions.deleted_at` 同一时区，不带时区偏移。日志中不包含消息内容、标题、附件路径或密钥。值经过 JSON 转义，所以每次删除只占一行。
- `tenantId` 与 `subject` 记录原始 ID，与 `sessions.tenant_id`/`user_id` 一致，恢复时可以直接按行核对归属。代价是日志存储中包含用户标识，需要按个人数据控制访问权限和保留期限。
- 只有第一次实际删除会写日志；对已删除会话的重复删除、他人或不存在的会话（404）以及回滚的事务都不会写。
- **软删除后是否仍需要（2026-10-27 评估）：** 仍需要，格式不变。软删除让删除标记留在数据库里，但从删除之前的备份恢复时，该会话会回到 `is_valid = 1`；只有独立于数据库故障域的这条日志能把恢复点之后的删除复放回去。复放步骤改为置 `is_valid = 0`（见恢复手册第 4 步），不再清空标题，也不删除任何内容。
- 手动调整记忆（例如减少召回引用数）写入数据库表 `memory_audit_log`（操作者、动作、目标、数量、前后值、数据库时间，不含内容），同时在 logger `memory.audit` 写一行 JSON。
- **运维要求：** 将这一行投递到与数据库**不同故障域**的日志存储，投递延迟和 binlog 一样要为 15 分钟 RPO 留出余量。保留期不短于“最早可恢复的全量备份”到现在的时间跨度（本地 `logs/` 只保留 7 天，不能替代日志存储）。另外需要能够查到每个实例最后一条日志的接收时间，用来判断日志覆盖到哪个时间点。

## 单实例保护

- M1 只支持单实例部署（`replicas=1`），多实例不受支持。`InstanceGuard` 在启动时于 `service_instances` 登记本实例，此后每隔 `app.memory.instance-heartbeat-interval`（默认 10 秒）续期。心跳与判定都使用数据库时间。
- 判定规则：同一 `app.memory.namespace`（默认 `default`）中存在另一实例，且其心跳在 `app.memory.instance-stale-after`（默认 30 秒）以内，即为冲突。不同命名空间互不影响；超过 30 秒未续期的记录（例如崩溃的旧进程）不算冲突，超过 1 天的此类记录会被自动删除。
- 冲突期间：每次心跳都以 ERROR 级别记录 `memory.instance INSTANCE_CONFLICT`；指标 `memory.instance.conflict` 为 1（否则为 0）；`/actuator/health` 返回 `OUT_OF_SERVICE`（HTTP 503）；新执行请求（`POST /api/chat`、`POST /api/chat/stream`）返回 503 `INSTANCE_CONFLICT`，包括相同 `X-Request-Id` 的重放。已在运行的执行不受影响，会正常结束。所有实例都会拒绝新执行，直到只剩一个实例。
- 恢复：停掉多余实例。正常停止会删除自己的登记记录，剩余实例在下一次心跳（最多 10 秒）时恢复，日志出现 `conflict cleared`；被强制终止的实例要等其心跳超过 30 秒才不再计入。
- 心跳失败（数据库不可用）时记录 WARN `memory.instance heartbeat failed`，并保持上一次的判定结果，不会因此误判冲突或解除冲突。
- 健康检查只暴露 `/actuator/health`，默认不显示组件与详情；`/actuator/health/memoryInstance` 等子路径也不可访问。不要把它配置成会重启容器的存活探针（liveness），否则冲突时两个实例会被反复重启；用作就绪探针或告警即可。
- 为什么冲突时拒绝而不只是告警：多实例下取消信号、执行权和清理任务的可靠性都没有保证，只告警不拒绝仍属于不受支持的部署。
- `app.memory.instance-guard-enabled=false` 会关闭心跳（仅用于没有 memory 表的测试），生产环境保持默认值 `true`。

## 上下文预算（M2a 起）

- 配置：`app.memory.context.enabled`（默认 `true`）、`default-context-window-tokens`（默认 32768）、`default-max-output-tokens`（默认 4096）、`estimate-factor`（默认 1.3，必须 ≥ 1，否则启动失败）、`capability-background-share`（默认 0.5，取值 (0, 1]：能力调用的背景最多占扣除固定部分后预算的比例，其余留给工具结果）。按模型的窗口在模型预设上设置（`contextWindowTokens`、`maxOutputTokens`，可空）。
- 输出预留不小于窗口时（例如预设只填了很小的窗口、输出预留用默认值，或两个默认值配反了），预算改用窗口的 1/4 作为输出预留（同时作为 `max_tokens` 发送），每个预设的每种配置只记一次 WARN 日志（预设改成另一种仍放不下的配置时再记一次）。
- 历史被截断时记一行 INFO 日志 `memory.context history truncated purpose=… keptTurns=… droppedTurns=… budgetTokens=…`（不含内容）。
- 能力背景被截断时记 INFO 日志 `memory.context background truncated role=… keptMessages=… droppedTurns=…`（不含内容），并计入 `purpose=CAPABILITY`（每个能力任务一次）。
- 指标：`memory.context.history.truncated{purpose}` 是丢掉了旧轮次的模型调用次数；`memory.context.estimate.ratio{purpose}` 是服务商返回的 `prompt_tokens` 除以同一次调用的原始估算（未乘系数）。比值持续高于 `estimate-factor` 说明系数偏小，调用可能超出窗口。
- `memory.history.injected.*` 从 M2a 起只统计最终回答调用（直接回答或汇总）实际带上的历史，每轮一次。

## 用户记忆概要（M2b Task 4）

配置前缀 `app.memory.profile.*`：

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `max-tokens` | `800` | 重建概要的估算 Token 上限（D14） |
| `cache-ttl` | `1h` | Redis 概要快照 TTL；命中时刷新；版本变化立即失效 |
| `max-relations` | `10` | 召回时注入的一跳关系上限 |

跨会话开关关闭时不读不写用户级概要；开启后由 `REBUILD_PROFILE` 任务从 ACTIVE 用户级条目增量重建（不自动扫描既往会话）。

## 轮次记忆记录（M2a Task 4，默认关闭）

轮次记忆记录是长期记忆的**检索索引**，不是上下文压缩：会话结束（闲置）后，为每个尚未建立记录的已完成轮次生成一条结构化记录，存入 `memory_summaries`。记录**不进入提示词**；检索命中后交给模型的是该轮原文（见下文“基础 Recall”）。决定见计划 D18–D25（用户已确认 2026-10-26）。

- 配置 `app.memory.summary.*`（闲置口径 **D19，2026-10-26 修订**，与 M2b 记忆判断共用）：`enabled`（默认 `false`，D4）、`idle-timeout`（默认 **1m**：无新消息且无进行中的轮次达到此时长即视为闲置并登记生成）、`idle-deadline`（默认 **30m**：最迟兜底，到点仍有待建记录的会话必须被扫到并执行）、`idle-scan-interval`（默认 **30s**，可配 30–60s；原 5m 在 1m 闲置下过粗）、`idle-scan-batch`（默认 100：每次扫描最多登记的会话数）、`max-source-tokens`（默认 12000：一轮送给模型的对话记录上限，按估算 Token 计）、`max-output-tokens`（默认 800，作为 `max_tokens` 发送）、`job-timeout`（默认 120 秒：单次模型调用上限）、`max-invalid-attempts`（默认 3）。
  - **实现现状（M2b Task 1）：** 产品默认已落地为 `idle-timeout=1m`、`idle-deadline=30m`、`idle-scan-interval=30s`；扫描实际使用 `min(idle-timeout, idle-deadline)` 作为截止时间。
- 闲置扫描：每 `idle-scan-interval` 按数据库时间查找闲置会话（未删除；没有进行中的轮次；没有在 `idle-timeout` 内创建或结束的轮次，失败或取消的新轮次也算新消息；或已满 `idle-deadline` 的兜底队列；有记录位置之后的已完成消息），每个会话登记一个 `SUMMARIZE` 任务（M2b 起同一次扫描还可登记 `JUDGE_MEMORY`），去重键 `summarize:<会话 ID>:<最后一条已完成消息的序号>`：同一次闲置只登记一次，之后有新消息、再次闲置时才会登记新任务。**不**按“累计 4 轮”提前触发。关闭时不扫描、不注册处理器，已有的 `SUMMARIZE` 任务保持 PENDING、不消耗尝试次数。另有实例冲突时暂停。全局并发为 1：单实例的任务处理器逐个执行任务。
- 生成：用租户自己的模型，每轮一次调用（JSON 模式，`response_format: json_object`），只读已完成（COMPLETED）的轮次；一轮超过输入上限时截断后生成，这一轮仍算已记录；租户窗口较小时上限再收紧到窗口能容纳的大小。
- 记录格式 `memory-record-v1`（`record_json`）：`summary`（≤ 80 字）、`entities`（专名，逐字）、`key_facts`（编号、数字、版本、名称、日期、金额，逐字）、`decision`、`keywords`（≤ 10）、`memory_type`（`FACT` / `EVENT`）、`source`（轮次 ID 与序号范围，由服务端填写）。`search_text` 由概括、专名、要点和关键词拼成，MySQL 上建 ngram FULLTEXT 索引，H2 用 LIKE。
- 校验：模型返回空内容、不是 JSON 对象、缺字段或类型不对都视为不合规；`entities[].name` 与 `key_facts[].text` 必须能在该轮原文中逐字找到（区分大小写和空格），找不到的条目丢弃；`summary` 与 `keywords` 不做原文核对，只截断长度和条数。所有文本写入前经过与工具记录相同的脱敏（密码、密钥、令牌）；含密钥的专名或要点保留，密钥部分替换为 `[REDACTED]`。
- 租约：一个任务按顺序处理会话中所有待建记录的轮次，每轮模型调用前把任务租约续到“现在 + 5 分钟”，积压很多轮也不会超出租约；续期失败（任务已不归本实例）时停止该任务。
- 写入：记录与处理位置（`memory_index_progress.summarized_through_sequence`）在同一事务中按位置比较后写入；重复或并发的任务不会写出重复记录。缺少租户模型、模型报错或超时都不推进位置，任务按退避重试（30 秒起倍增，最多 30 分钟）。
- 不合规输出：本次尝试失败并按退避重试，同一轮的连续不合规次数记在 `memory_index_progress.summary_invalid_attempts`；达到 `max-invalid-attempts`（默认第 3 次）时跳过这一轮（不写记录，位置越过这一轮，`memory.summary.skipped` 加 1），任务继续处理后面的轮次。被跳过的轮次原文仍在，Task 5 起可通过原文片段召回。模型报错和超时不计入不合规次数。
- 已删除的会话：记录、原文和检索命中立即不可用（读取时校验删除标记），清理任务删除记录与处理位置；晚到的任务直接跳过，不调用模型。
- 迁移：本功能直接修改了尚未上线的 V10（D21）。已在本地跑过旧 V10 的开发库需要重建（或删库后重新迁移）；`flyway repair` 只修校验和，不会改表结构。
- 指标：`memory.summary.generated`（写入的记录数）、`memory.summary.failures{reason}`（`MODEL_MISSING`、`MODEL_ERROR`、`TIMEOUT`、`INVALID_OUTPUT`）、`memory.summary.skipped`（不合规而跳过的轮次）、`memory.summary.lag`（轮次结束到读取该轮准备生成记录的时间，数据库时间，不含模型调用时间）、`memory.summary.backlog`（最近一次闲置扫描找到的会话中尚无记录的已完成轮次数，受 `idle-scan-batch` 限制；只在启用时注册）。
- 上线前跟进项：闲置扫描目前每次对 `chat_turns` 与 `chat_messages` 分组查询（每次最多 `idle-scan-batch` 个会话），内部验收规模足够；上线前改为只扫描最近有更新的会话，避免全表分组。
- 排查：`SELECT status, attempts, last_error FROM memory_jobs WHERE job_type = 'SUMMARIZE' AND status <> 'DONE'`；`last_error = 'InvalidRecordException'` 表示模型输出不合规，`SELECT summary_invalid_turn_id, summary_invalid_attempts FROM memory_index_progress WHERE conversation_id = ?` 查看当前计数。

## 基础 Recall（M2a Task 5，默认开启）

接口层面的行为见 `docs/memory/api.md`“基础 Recall”与“记忆设置”。

- 配置 `app.memory.recall.*`：`enabled`（默认 `true`；关闭后不检索、不注入，片段照常建立）、`max-items`（默认 5）、`budget-share`（默认 0.25，取值 (0, 1]）、`candidate-limit`（默认 20：合并后保留的候选轮次）、`min-matched-terms`（默认 2）、`max-conversations`（默认 50：跨会话时最多检索的其他会话数）、`backfill-turns`（默认 20：当前会话未切块轮次的直接扫描上限）、`entity-scan-records`（默认 200：扫描专名的最新记录数）、`chunk-max-tokens`（默认 1500：单个片段的估算 Token 上限）。取值不合法时启动失败。
- 切块：每个 COMPLETED 轮次的 `INDEX_CHUNKS` 任务把该轮的用户消息和最终回答写成一个片段（`用户：…\n助手：…`，工具调用过程不入片段），超过 `chunk-max-tokens` 时按段落拆分（单段过长再硬切）。片段带上编号类字符串（`entities_json`）、来源类别，并与同一会话的前后片段双向链接；片段与 `memory_index_progress.indexed_through_sequence` 在同一事务中写入，会话行先加锁（与删除事务同一顺序）。重复执行、轮次未完成或会话已删除时不写入。
- 全文索引（D7）：MySQL 8.4 上 `ft_memory_chunks_content` 与 `ft_memory_summaries_search_text` 使用 ngram 解析器，`ngram_token_size=2`（服务器默认值，这是选定值；在 `my.cnf` 的 `[mysqld]` 中显式写上 `ngram_token_size=2` 以免被改动）。检索用自然语言模式，查询为问题的双字词；结果再由服务端按命中的不同词数重新计分，所以 MySQL 与 H2 的取舍规则一致。H2 没有 ngram，用 LIKE 代替，只用于开发与测试，不适合生产数据量。
- 重建全文索引（修改 `ngram_token_size` 之后必须执行；该参数只能在启动时设置，需要重启 MySQL）：
  1. 停止写入（或在维护窗口内执行），确认 `SHOW VARIABLES LIKE 'ngram_token_size'` 为新值。
  2. `ALTER TABLE memory_chunks DROP INDEX ft_memory_chunks_content, ADD FULLTEXT INDEX ft_memory_chunks_content (content) WITH PARSER ngram;`
  3. `ALTER TABLE memory_summaries DROP INDEX ft_memory_summaries_search_text, ADD FULLTEXT INDEX ft_memory_summaries_search_text (search_text) WITH PARSER ngram;`
  4. 大量删除后只想整理索引时可用 `SET GLOBAL innodb_optimize_fulltext_only=ON; OPTIMIZE TABLE memory_chunks; SET GLOBAL innodb_optimize_fulltext_only=OFF;`。
- 重建某个会话的片段（例如修复切块错误后）：删除该会话的片段，再把它已完成的切块任务改回待执行，处理器会重新切块（任务幂等，去重键仍是 turnId）：`DELETE FROM memory_chunks WHERE conversation_id = ?;` 然后 `UPDATE memory_jobs SET status = 'PENDING', attempts = 0, available_at = CURRENT_TIMESTAMP(6), last_error = NULL WHERE job_type = 'INDEX_CHUNKS' AND conversation_id = ? AND status = 'DONE';`。在此期间当前会话的最新 `backfill-turns` 轮仍可通过直接扫描召回。
- 跨会话开关存于 `user_memory_settings`（按租户 + subject），每次检索时读取，不缓存，因此修改无需清理任何缓存。
- 指标（仅在启用时注册，同时 `memory.feature.enabled{feature=recall}` 为 1）：`memory.recall.latency`（每个问题的检索耗时）、`memory.recall.items`（每个问题采用的候选轮次数，注入前；最终注入多少还受预算限制）、`memory.recall.coverage.limited`（存在未切块且未被扫描的轮次的检索次数）。
- 排查：`SELECT status, COUNT(*) FROM memory_jobs WHERE job_type = 'INDEX_CHUNKS' GROUP BY status`；长期 PENDING 的积压会让 `memory.recall.coverage.limited` 上升。检索失败时日志为 `memory.recall failed, answering without recall errorType=…`（不含内容），该轮不带召回资料继续回答。



## 记忆判断（M2b Task 3，D10，默认关闭）

闲置后对未判断轮次调用租户模型 + classpath Rules，写入 `memory_items` / `memory_item_sources`。

- 配置 `app.memory.judgement.*`：`enabled`（默认 `false`）、`max-source-tokens`（默认 12000）、`max-output-tokens`（默认 1200）、`job-timeout`（默认 120s）。
- 触发：与轮次记录共用 `IdleConversationScanner`（D19）；启用 judgement 或 summary 任一即可扫描；去重键 `judge:<conversationId>:<lastSeq>`。
- 进度：`memory_index_progress.judged_through_sequence`（V12）。
- 跨会话开关关闭时只写 `conversation_id` 非空的会话级条目。
- 删除会话：事务内移除该会话来源；无剩余来源的条目标为 `INACTIVE`；物理清理删除会话级条目。
- 指标：`memory.judgement.written`、`memory.judgement.rejected{reason}`、`memory.judgement.failures{reason}`。

## 整理 Token 额度（M2b Task 2，D11=A）

后台 LLM 整理任务（`SUMMARIZE`、`JUDGE_MEMORY`、`REBUILD_PROFILE`、`REPLAY_RULES`）受每租户每日 Token 上限约束。

- 配置 `app.memory.consolidation.*`：`enabled`（默认 `false`：只控制 `memory.consolidation.backlog` 与 `memory.feature.enabled{feature=consolidation}` 是否注册；**额度强制始终生效**）、`daily-token-limit`（默认 `200000`）、`max-concurrent-per-tenant`（默认 `1`，本实例进程内）、`reserve-tokens`（默认 `12800`：领取任务时的预留估算，结束后按实际用量结算）。
- 租户覆盖：表 `memory_tenant_quotas(tenant_id, daily_token_limit, updated_at)`，缺行则用全局默认；仅管理员写入（本任务不提供 API）。
- 日期边界：用量记在 `memory_token_usage.usage_date = CURRENT_DATE`，以**数据库会话时区**为准（MySQL IT 使用 UTC）。跨日以库钟为准，不按应用 JVM 时区切日。
- 超限：任务保持 `PENDING`，`available_at` 推到次日 0 点（`TIMESTAMPADD(DAY, 1, CAST(CURRENT_DATE AS DATETIME(6)))`），**不丢弃、不增加 attempts**；指标 `memory.consolidation.quota.postponed`。
- 结算：工人预留 `reserve-tokens`；handler 若观察到用量则 `settle(reserved, actual)` 替换为实际值；成功结束时工人再 `settle(reserved, notObserved)`（若已替换则净变为 0）。失败则退还预留以便重试。未观察到用量时保留预留（偏保守）。
- 指标：`memory.consolidation.backlog`（仅 `enabled=true` 时注册）、`memory.consolidation.quota.postponed`、`memory.feature.enabled{feature=consolidation}`。

## 基础指标与评测

- 内存相关指标由 `MemoryMetrics` 记录（Micrometer），完整口径见 `docs/memory/eval-baseline.md` 第 3 节。默认只暴露 `/actuator/health`；需要采集时由部署方另外开放 metrics 端点或配置导出，不要开放其他 actuator 端点。
- 未启用的能力不以数字上报：Recall 关闭时其指标为“不适用”（不注册，`memory.feature.enabled{feature=recall}` 为 0），整理积压为“功能未启用”（不注册，M2b 才启用）。看板上缺这些指标属于正常情况，不要用 0 补齐。
- `memory.finalizer.failures` 持续增长时，检查数据库可用性；这些执行由恢复扫描在租约过期后标记为 INTERRUPTED。
- 真实模型评测（`-Pmemory-eval-live`）只在隔离环境手动运行，API key 通过环境变量提供，不写入仓库或报告。

## 故障行为（M1 已验证）

| 故障 | 行为 | 验证 |
| --- | --- | --- |
| Redis 完全不可用 | 历史读写都直接走数据库，多轮上下文和分页正常，只有缓存命中率下降 | `redisUnavailableStillReadsAndWritesDatabaseHistory` |
| 进程在检查点后退出 | 租约过期后由恢复扫描（新进程启动时或每隔扫描周期）标记 INTERRUPTED，保留最后一次检查点写入的文本，释放活跃槽位；这一轮不进入后续上下文 | `processExitAfterCheckpointRecoversLastCheckpointAndInterrupts` |
| 收尾器失败或终态提交失败 | 客户端收到 `STORAGE_UNAVAILABLE` 错误事件，不报成功；记录 `memory.finalizer.failures`；租约过期后由恢复扫描标记 INTERRUPTED | `failedTerminalCommitIsNotReportedAsSuccessAndRecoveryBacksItUp` |
| 旧 fence | 被接管的执行再写检查点或终态都会失败（`ExecutionRightLost`），不覆盖已有记录和新一轮 | `staleFenceCannotOverwriteAfterTheTurnWasTakenOver` |
| 数据库写失败 | 接受失败返回 `STORAGE_UNAVAILABLE`，不创建会话或消息；接受后存储失败不调用模型 | `databaseWriteFailure*NeverStartsTheModel` |
| 删除清理失败 | 任务回到 PENDING 并在退避后重试（重启后同样）；重试期间和完成后，分页、缓存和同 ID 新请求都返回 404，删除标记保留 | `deletionRetriesNeverResurrectTheConversation` |

## 恢复手册（仅限隔离环境演练）

**适用范围。** 本手册用于在隔离环境演练数据库恢复，并作为验收条件“RPO ≤ 15 分钟、RTO ≤ 4 小时”的演练依据。M1 不提供任何修改生产调度、备份配置或生产数据的脚本；对生产执行恢复需由负责人另行批准，并按同样步骤人工操作。**目前尚未完成演练，生产 RPO/RTO 未验证，不能宣称达标。**

**恢复对象。**

- MySQL：`sessions`（含删除标记）、`chat_turns`、`chat_messages`、`chat_tool_events`、`memory_jobs`、`service_instances`、M2a 的 `memory_summaries`、`memory_chunks`、`memory_index_progress`、`user_memory_settings`，以及 `tenant_llm_config` 等原有业务表。这是唯一的权威数据。
- 密钥：`LLM_CONFIG_SECRET`。`tenant_llm_config` 中的租户 API key 用它加密，缺少对应版本的密钥就无法解密，只能让租户重新录入。
- Redis 只是可重建缓存，不备份，也不从生产复制。
- 附件原件（`${app.upload.dir}`）**不在 M1 恢复范围内**，不保证长期可恢复。恢复后消息中的文件名仍在，原件可能不存在。

### 1. 备份前提（演练前核对）

- 全量备份与连续 binlog 归档（`binlog_format=ROW`，建议开启 GTID）存放在**与数据库不同的故障域**（不同主机、可用区或账号）。只放在数据库同一台机器或同一磁盘上的备份不算数。
- 日志交付延迟必须给 15 分钟 RPO 留出余量：binlog 至少每 5 分钟轮转或流式上传一次，并对“最新已归档 binlog 的时间距现在超过 10 分钟”告警。全量备份周期由 RTO 决定：全量恢复加 binlog 回放的实测时间必须远小于 4 小时。
- `LLM_CONFIG_SECRET` 保存在密钥管理系统中，与数据库备份分属不同故障域，并保留历史版本；记录每个版本的生效时间段，以便确定恢复点对应哪个版本。
- 删除清单：删除标记（`sessions.is_valid = 0` 与 `deleted_at`）和 `CONVERSATION_CLEANUP` 任务与删除请求在同一事务内写入数据库，随备份恢复。独立于数据库的副本是删除审计日志流（`memory.deletion-audit`，见“删除审计日志”），它必须已经投递到不同故障域的日志存储，并且保留期覆盖最早可恢复的全量备份。

### 2. 准备隔离环境

- 新建 MySQL 8.4 实例，与生产网络隔离；新建空的 Redis 实例；新建空的附件目录。不要连接生产 Redis，也不要在任何共享 Redis 上执行 FLUSHDB。
- 服务端使用隔离环境自己的配置（数据库、Redis、`app.upload.dir`），不开放外部访问，直到第 6 步验证完成。
- 记录演练开始时间 T0，用于计算 RTO。

### 3. 恢复数据库到指定时间点

1. 在隔离实例上恢复最近一次早于目标时间点的全量备份。
2. 用 `mysqlbinlog --stop-datetime=<目标时间点>`（或按 GTID）回放归档 binlog，直到目标时间点或最后一个可用事件。
3. 记录最后一个回放事件的时间 Tlast。实际 RPO = 故障时间 − Tlast，不是 binlog 文件的修改时间。
4. 检查 Flyway：`l7_skill_flyway_schema_history`（`spring.flyway.table`）最新版本应为 V8，且没有失败记录。不要修改历史记录。

### 4. 核对删除记录（开放访问前）

- 恢复点之前提交的删除都已包含在数据库中：删除标记的会话返回 404，未完成的 `CONVERSATION_CLEANUP` 任务在服务启动后继续执行软删除清理。核对：`SELECT COUNT(*) FROM sessions WHERE is_valid = 0` 与 `SELECT status, COUNT(*) FROM memory_jobs WHERE job_type = 'DELETE_CONVERSATION' GROUP BY status`。
- 如果 binlog 一直回放到故障时刻（Tlast 等于故障时间），就没有遗漏的删除。
- 如果 Tlast 早于故障时间，(Tlast, 故障时间] 之间的删除不在数据库中，需要在**服务启动前**（服务停止、隔离环境、不开放访问）按删除审计日志人工复放：
  1. **确认日志覆盖范围。** 在日志存储中确认这个部署所有实例的日志都已经接收到故障时间。删除并不频繁，没有删除日志不能证明期间没有删除，所以只能以日志管道的接收时间为准。如果覆盖范围早于故障时间，那么从覆盖终点到故障时间这段无法核实，按第 4 条处理。
  2. **导出删除行。** 导出 `"event":"CONVERSATION_DELETED"` 且 `deletedAt` 不早于所用全量备份开始时间的全部行。复放是幂等的，范围取宽一些比只取 Tlast 之后更安全，可以避免日志时间与 binlog 时间之间的误差。
  3. **逐行核对并复放。** 对每一行按 `conversationId` 查询 `SELECT tenant_id, user_id, deleted_at FROM sessions WHERE id = ?`：
     - 没有这一行：会话创建于恢复点之后，已不存在，记录下来即可。
     - `is_valid` 已经是 0：已包含在恢复的数据中，跳过。
     - `tenant_id`、`user_id` 与日志不一致：停止复放并调查，不开放访问。
     - `is_valid` 为 1：在一个事务中执行：

       ```sql
       UPDATE sessions SET deleted_at = '<deletedAt>', is_valid = 0, revision = revision + 1, updated_at = CURRENT_TIMESTAMP(6)
        WHERE id = '<conversationId>' AND tenant_id = '<tenantId>' AND user_id = '<subject>' AND is_valid = 1;
       INSERT INTO memory_jobs (id, tenant_id, subject, conversation_id, job_type, status, attempts, dedupe_key, available_at, created_at, updated_at)
       VALUES (UUID(), '<tenantId>', '<subject>', '<conversationId>', 'CONVERSATION_CLEANUP', 'PENDING', 0,
               CONCAT('cleanup:', '<conversationId>'), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6));
       ```

       服务启动后，清理任务会把消息、工具记录、执行、片段、轮次记录和召回记录置为无效并写缓存删除标记（不删除行或附件）。复放不会使派生条目失效；如需一致，另行执行 `UPDATE memory_item_sources SET is_valid = 0 WHERE conversation_id = '<conversationId>'`，并把只剩无效来源或属于该会话的条目置为 INACTIVE、写 `source_deleted_at`。这些会话中仍处于活跃状态的执行，在租约过期后由恢复扫描标记为 INTERRUPTED。
  4. **无法核实的时间段。** 如果日志覆盖不到故障时间，或者有行无法核对，这些数据保持不可访问（spec 第 12 节）：不启动服务，或者不开放访问，由负责人决定后续处理。
  5. **记录结果。** 记录复放行数、各类结果的数量，以及日志覆盖终点。
- 以上 SQL 只在隔离环境中人工执行；仓库不提供修改生产数据的脚本。

### 5. 恢复密钥与启动服务

- 按恢复点选择对应版本的 `LLM_CONFIG_SECRET`，通过密钥管理系统或 shell 注入，不写入文件或仓库。密钥格式错误时服务启动失败（`LLM_CONFIG_SECRET must be valid Base64 encoding of exactly 32 bytes`）。
- 启动服务，Redis 为空即可，缓存在读取时从数据库重建，旧 `chatmemory:*` key 不会被读取。
- 启动后会自动发生以下变化，属于预期行为：
  - 备份时仍在运行的执行，在租约（`app.memory.lease-duration`，默认 60 秒）过期后被标记为 INTERRUPTED。
  - 未完成的删除任务会继续执行。
  - 恢复出来的生产实例登记记录心跳已过期（超过 30 秒），不算实例冲突。

### 6. 验证

- `/actuator/health` 为 `UP`。
- 抽样会话分页结果与恢复点一致，抽样的已删除会话返回 404。
- 租约时长过后，`SELECT COUNT(*) FROM chat_turns WHERE status IN ('ACCEPTED','RUNNING')` 为 0，或只剩新产生的执行。
- 使用租户 LLM 配置的请求能够正常调用模型，证明密钥能解密。
- `memory_jobs` 中没有长期 PENDING 的任务。
- 记录验证完成时间 T1。实际 RTO = T1 − 故障时间；演练中按 T1 − T0 加上故障发现时间估算。

### 7. 记录

把以下信息填入 [phase-1-acceptance.md](phase-1-acceptance.md) 的恢复演练记录：演练日期、全量备份标识、目标时间点、Tlast 与实际 RPO、实际 RTO、密钥版本、删除核对结果（含 Tlast 早于故障时间时的处理），以及是否通过。只有数据库、密钥和删除记录三项全部通过，才能宣称达到 RPO/RTO 目标。
