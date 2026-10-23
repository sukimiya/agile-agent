# Memory 运维说明（M1）

> M1 运维说明。故障行为由 `MemoryFailureTest`（H2）与 `MemoryFailureIntegrationTest`（MySQL 8.4）验证；恢复手册只用于隔离环境演练，生产恢复目标尚未通过演练，见 [phase-1-acceptance.md](phase-1-acceptance.md)。

## 附件存储与保留

- 附件保存在本地目录 `${app.upload.dir}/{conversationId}/`。目录名就是会话 ID（小写规范 UUID），同一会话的附件在同一目录，不同会话不会共用目录。
- **M1 不承诺附件原件可永久恢复。** `UploadCleanupTask` 在启动时和此后每小时运行一次，删除最后修改时间早于 `app.upload.ttl-hours`（默认 24 小时）的会话目录。会话消息默认永久保留：消息正文和其中提到的文件名会保留，附件原件可能已被清理。
- 清理任务只处理名称符合会话 ID 规则的目录。其他目录（例如 M1 之前按 sanitize 规则创建的 `conv-1`、`a_b` 等）不会被自动删除，需要运维确认后手动清理。M1 之前的这类旧目录也无法再通过下载接口访问。
- 附件不在 M1 的数据库备份与恢复范围内（RPO/RTO 只覆盖会话、消息与执行记录）。归档与 MinIO 存储见 spec 14.1。

## 会话删除清理

- `DELETE /api/conversations/{id}` 只写删除标记、使执行权失效，并在 `memory_jobs` 登记 `DELETE_CONVERSATION` 任务。物理清理由 `MemoryMaintenance` 执行，它在启动时以及此后每隔 `app.memory.cleanup-scan-interval`（默认 30 秒）扫描一次；`app.memory.recovery-enabled=false` 时与恢复扫描一起关闭。
- 清理内容：`chat_messages`、`chat_tool_events`、Redis 中该会话的缓存条目（替换为删除标记），以及 `${app.upload.dir}/{conversationId}/`。清理前会再次确认该会话已标记删除，不会清理仍在使用的会话。
- 失败重试：失败的任务回到 PENDING，退避时间从 30 秒开始指数增长，上限 30 分钟，不限次数。`last_error` 只记录异常类型，不保存消息或路径，原始异常只写服务端日志（`memory.delete cleanup failed`）。进程在清理中途退出时，任务停留在 RUNNING，5 分钟租约到期后会被重新领取。
- 排查：`SELECT status, attempts, last_error, available_at FROM memory_jobs WHERE job_type = 'DELETE_CONVERSATION' AND status <> 'DONE'`。长期 PENDING 且 attempts 持续增长，通常是附件目录权限或 Redis 不可用。
- 删除标记（`sessions.deleted_at`）与已完成的任务记录永久保留，不要手动清除，否则同一会话 ID 可能被重新创建，或无法确认清理已完成。

## 单实例保护

- M1 只支持单实例部署（`replicas=1`），多实例不受支持。`InstanceGuard` 在启动时于 `service_instances` 登记本实例，此后每隔 `app.memory.instance-heartbeat-interval`（默认 10 秒）续期。心跳与判定都使用数据库时间。
- 判定规则：同一 `app.memory.namespace`（默认 `default`）中存在另一实例，且其心跳在 `app.memory.instance-stale-after`（默认 30 秒）以内，即为冲突。不同命名空间互不影响；超过 30 秒未续期的记录（例如崩溃的旧进程）不算冲突，超过 1 天的此类记录会被自动删除。
- 冲突期间：每次心跳都以 ERROR 级别记录 `memory.instance INSTANCE_CONFLICT`；指标 `memory.instance.conflict` 为 1（否则为 0）；`/actuator/health` 返回 `OUT_OF_SERVICE`（HTTP 503）；新执行请求（`POST /api/chat`、`POST /api/chat/stream`）返回 503 `INSTANCE_CONFLICT`，包括相同 `X-Request-Id` 的重放。已在运行的执行不受影响，会正常结束。所有实例都会拒绝新执行，直到只剩一个实例。
- 恢复：停掉多余实例。正常停止会删除自己的登记记录，剩余实例在下一次心跳（最多 10 秒）时恢复，日志出现 `conflict cleared`；被强制终止的实例要等其心跳超过 30 秒才不再计入。
- 心跳失败（数据库不可用）时记录 WARN `memory.instance heartbeat failed`，并保持上一次的判定结果，不会因此误判冲突或解除冲突。
- 健康检查只暴露 `/actuator/health`，默认不显示组件与详情；`/actuator/health/memoryInstance` 等子路径也不可访问。不要把它配置成会重启容器的存活探针（liveness），否则冲突时两个实例会被反复重启；用作就绪探针或告警即可。
- 为什么冲突时拒绝而不只是告警：多实例下取消信号、执行权和清理任务的可靠性都没有保证，只告警不拒绝仍属于不受支持的部署。
- `app.memory.instance-guard-enabled=false` 会关闭心跳（仅用于没有 memory 表的测试），生产环境保持默认值 `true`。

## 基础指标与评测

- 内存相关指标由 `MemoryMetrics` 记录（Micrometer），完整口径见 `docs/memory/eval-baseline.md` 第 3 节。默认只暴露 `/actuator/health`；需要采集时由部署方另外开放 metrics 端点或配置导出，不要开放其他 actuator 端点。
- 未实现的能力不以数字上报：Recall 相关指标为“不适用”（不注册），整理积压为“功能未启用”（不注册）。看板上缺这些指标属于正常情况，不要用 0 补齐。
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

- MySQL：`sessions`（含删除标记）、`chat_turns`、`chat_messages`、`chat_tool_events`、`memory_jobs`、`service_instances`，以及 `tenant_llm_config` 等原有业务表。这是唯一的权威数据。
- 密钥：`LLM_CONFIG_SECRET`。`tenant_llm_config` 中的租户 API key 用它加密，缺少对应版本的密钥就无法解密，只能让租户重新录入。
- Redis 只是可重建缓存，不备份，也不从生产复制。
- 附件原件（`${app.upload.dir}`）**不在 M1 恢复范围内**，不保证长期可恢复。恢复后消息中的文件名仍在，原件可能不存在。

### 1. 备份前提（演练前核对）

- 全量备份与连续 binlog 归档（`binlog_format=ROW`，建议开启 GTID）存放在**与数据库不同的故障域**（不同主机、可用区或账号）。只放在数据库同一台机器或同一磁盘上的备份不算数。
- 日志交付延迟必须给 15 分钟 RPO 留出余量：binlog 至少每 5 分钟轮转或流式上传一次，并对“最新已归档 binlog 的时间距现在超过 10 分钟”告警。全量备份周期由 RTO 决定：全量恢复加 binlog 回放的实测时间必须远小于 4 小时。
- `LLM_CONFIG_SECRET` 保存在密钥管理系统中，与数据库备份分属不同故障域，并保留历史版本；记录每个版本的生效时间段，以便确定恢复点对应哪个版本。
- 删除记录：M1 的删除标记（`sessions.deleted_at`）和 `DELETE_CONVERSATION` 任务与删除请求在同一事务内写入数据库，所以会随全量备份和 binlog 一起恢复。**M1 没有独立于数据库故障域的删除清单**，见第 4 步。

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

- 恢复点之前提交的删除都已包含在数据库中：删除标记的会话返回 404，未完成的 `DELETE_CONVERSATION` 任务在服务启动后继续执行物理清理。核对：`SELECT COUNT(*) FROM sessions WHERE deleted_at IS NOT NULL` 与 `SELECT status, COUNT(*) FROM memory_jobs WHERE job_type = 'DELETE_CONVERSATION' GROUP BY status`。
- 如果 binlog 一直回放到故障时刻（Tlast 等于故障时间），就没有遗漏的删除。
- 如果 Tlast 早于故障时间，那么 (Tlast, 故障时间] 之间的删除请求已经丢失，这些会话会被恢复出来。spec 要求“删除清单不完整时，不开放无法核实的范围”，而 M1 没有独立保存的删除清单，无法确定这段时间删除了哪些会话。这种情况下**不开放恢复后的会话数据**，由负责人决定后续处理。这是 M1 的已知缺口，见验收记录中的未完成项。

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
