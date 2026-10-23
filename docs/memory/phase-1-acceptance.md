# Memory M1 验收记录（agile-agent 主线）

> 依据 spec `docs/superpowers/specs/2026-10-21-memory-runtime-design.md`（第 11、12、13 节）与计划 `docs/superpowers/plans/2026-10-21-memory-persistence-phase-1.md`（Task 1–10）。分支 `codex/memory-m1`，记录日期 2026-10-23（UTC+8）。本文件只记录主线结果；mcp-tool 适配另行记录（见第 6 节）。

## 1. 环境

| 项 | 值 |
| --- | --- |
| JDK | OpenJDK 21.0.12.1（Linux） |
| Maven | 3.9.9 |
| 真实 MySQL | MySQL 8.4.8，仅回环地址（`docker/docker-compose.memory-test.yml` 同配置，端口 33306），测试库名以 `_it` 结尾 |
| Redis | 测试专用、随机回环端口的本地 `redis-server`；“Redis 不可用”场景指向不监听的 `127.0.0.1:1` |
| 迁移版本 | 新增 Flyway **V8**（`V8__memory_persistence.sql`）；V1–V7 未修改；历史表 `l7_skill_flyway_schema_history` |

## 2. 各任务提交

| Task | 内容 | 提交 |
| --- | --- | --- |
| 1 | 数据契约、租户隔离、V8 迁移、MySQL 测试基线 | `aa7c5c8`、`c6587a5`；API 契约文档 `9b5623c` |
| 2 | 原子接受、幂等、租约与终态收尾 | `9a13c82`、`f8ed6c4` |
| 3 | 数据库历史、分页与可重建缓存 | `76a72cc` |
| 4 | 两个聊天入口与流式输出集成、断开即取消 | `6d34ff8`、`3f763cc` |
| 5 | 工具执行记录、来源类型与凭证过滤 | `c04b8a3` |
| 6 | 附件归属、会话 ID 统一规则与路径去除 | `a7c00c4` |
| 7 | 会话删除与持久清理 | `87b5a6c` |
| 8 | 单实例保护与部署配置 | `82e6df2` |
| 9 | 评测集、基线与基础指标；真实模型端到端基线 | `d3aea8c`、`bc18dbe` |
| 10 | 故障验收、恢复手册与本记录 | `test: verify durable memory failure and recovery paths`（本文件所在提交） |

## 3. 验证命令与结果（2026-10-23，JDK 21）

数字取自 Maven 汇总行。agent-server 的默认测试分两次执行：主执行与单独 fork 的 BlockHound 执行。默认运行不包含 `mysql-it` 与 `memory-eval-live` 组，这些测试是被排除，不是 SKIPPED。

| 命令 | 结果 |
| --- | --- |
| `mvn -pl agent-spi -am test` | PASS：17 个测试，0 失败，0 错误，0 跳过 |
| `mvn -pl agent-server -am clean test` | PASS：agent-spi 17、mock-oauth2 25、agent-server 596 + BlockHound 2，0 失败，0 错误，0 跳过 |
| `mvn -Pmemory-mysql-it -pl agent-server -am -Dtest=MemoryFailureIntegrationTest,TurnConcurrencyIntegrationTest test` | PASS：25（TurnConcurrencyIntegrationTest 18、MemoryFailureIntegrationTest 7），真实 MySQL |
| `mvn -Pmemory-mysql-it -pl agent-server -am test`（全部 mysql-it） | PASS：102，真实 MySQL |
| `mvn -pl agent-server -am -DskipTests package` | PASS（只编译打包，不算验收） |
| `mvn -Pmemory-eval-live …`（真实模型端到端评测） | 2026-10-23 运行一次，结果见 `eval-baseline.md` 第 2 节；M1 不设质量门槛 |

H2 测试和构建成功都不能算作 MySQL 迁移或并发验收。并发与故障行为以上面的真实 MySQL 结果为准。

**Task 10 故障场景**（`MemoryFailureTest`：H2，默认测试集；`MemoryFailureIntegrationTest`：MySQL）：Redis 完全不可用、检查点后进程退出、收尾器或终态提交失败、旧 fence、接受前和接受后的数据库写失败、删除重试不复活，共 7 个场景，H2 和 MySQL 都通过。这些恢复行为在 Task 2–7 已经实现，所以首次运行没有出现因“行为未实现”导致的失败。首次运行唯一的失败来自测试自身的错误假设：清理会先删除消息再删除文件，所以文件删除失败时消息已经删掉，测试已按实际行为修正。为了证明这些测试确实能发现问题，做了一次变异检查：把恢复扫描写入的状态临时从 `INTERRUPTED` 改成 `COMPLETED`，3 个场景失败，随后已还原。

## 4. M1 验收项（spec 第 11 节）

| # | 验收项 | 状态 | 主要证据 |
| --- | --- | --- | --- |
| 1 | Redis 清空、损坏或不可用时从 MySQL 分页读取；跨用户和租户隔离 | 满足 | `ConversationHistoryTest`、`MemoryFailureTest`、`MemoryScopeTest` 及 MySQL 变体 |
| 2 | 终态映射：正常结束、断开（两个分支）、超时、模型异常、进程退出、失去 fence、数据库失败；收尾器失败由恢复扫描兜底；外部写操作标记为不确定 | 满足 | `TurnLifecycleTest`/`TurnConcurrencyIntegrationTest`、`TurnStreamingIntegrationTest`、`TurnFinalizerTest`、`TurnToolRecorderTest`、`MemoryFailureTest` |
| 3 | 会话内幂等；不同输入复用 key 返回冲突；同会话并发；活跃冲突返回 409 并带 turnId | 满足（仅限已建立的会话，见第 5 节上线门槛） | Task 2 测试及 MySQL 并发测试 |
| 4 | 两个聊天入口回归，流式事件格式和 capability 契约不变 | 满足 | `ChatControllerTest`、`TurnStreamingIntegrationTest` |
| 5 | 附件归属、ID 规则、404、不泄露路径、24 小时清理，并写明附件原件不保证永久可恢复 | 满足 | `FileStorageServiceTest`、`UploadCleanupTaskTest`、`ChatControllerTest`、`operations.md` |
| 6 | 部署配置声明 replicas=1；第二个实例产生告警并拒绝新执行 | 满足 | `InstanceGuardTest` 等（Task 8） |
| 7 | 删除后原文和缓存不可读取，也不会被重建复活 | 满足 | `MemoryDeletionTest`、`MemoryFailureTest` |
| 8 | 不读取旧 `chatmemory:*` key | 满足 | `ConversationHistoryTest` |
| 9 | 中立评测集与基线；组件状态和端到端结果分开记录；未实现组件记为“不适用”或“功能未启用” | 满足 | `eval-baseline.md`、`MemoryMetricsTest` |
| 10 | 来源类型、租户隔离与凭证过滤 | 满足 | `ToolRecordSanitizerTest`、`TurnToolRecorderTest` |
| 18 | 主线测试与 mcp-tool 适配测试分别记录 | 主线已记录；mcp-tool 待主线验收后适配 | 第 6 节 |
| 19 | RPO ≤ 15 分钟、RTO ≤ 4 小时，需数据库、密钥和删除清单的恢复演练都通过 | **未满足（未验证）** | 只有恢复手册（`operations.md`），演练尚未执行；删除清单存在缺口，见第 5 节 |

## 5. 未实现、限制与上线门槛

**M1 不包含：** 上下文预算、摘要、Recall/RAG、LLM + Rules 长期记忆、用户概要和图谱、跨会话开关、MinIO 与附件归档、独立的取消接口、执行注册表、断线续流、Recall 和整理相关指标（分别为“不适用”和“功能未启用”）、metrics 端点暴露（只开放 `/actuator/health`）、LLM 自动评判（评测结论用关键词判定加人工复核），以及 mcp-tool 移植。

**限制：**

- **生产 RPO/RTO 未验证。** 恢复演练尚未在隔离环境执行，不能宣称达到“RPO ≤ 15 分钟、RTO ≤ 4 小时”。
- **删除清单缺口。** M1 的删除记录（删除标记和删除任务）只保存在数据库中。如果只能恢复到故障前的某个时间点，这段时间内的删除无法从数据库外部核实。按 spec 第 12 节，这种情况下恢复的数据保持不可访问。在宣称达到恢复目标之前，需要确定一种独立于数据库故障域的删除清单（待决策）。
- **单实例。** 只支持 `replicas=1`。出现第二个实例时，所有实例都会拒绝新执行（503 `INSTANCE_CONFLICT`）。
- **附件。** 附件原件不保证长期可恢复，24 小时后会被清理，也不在数据库恢复范围内。

**上线门槛（spec 第 13 节）：重复执行和重复计费。** 未携带 conversationId 的首个请求被重试时，`POST /api/chat/completions`（`ChatController.java:64`）和 `POST /api/chat`（`ChatController.java:153-155`）都会生成新的会话 ID，`Idempotency-Key` 无法命中，可能导致重复调用模型和重复计费。这不是 M1 的功能承诺，但**正式生产上线前，两个入口都必须修复并通过验证**。问题说明见 [PR #1](https://github.com/sukimiya/agile-agent/pull/1)，尚未合入 main。

## 6. mcp-tool

待主线验收后适配。目前没有副本提交，也没有副本测试结果，迁移版本映射待适配时记录。

## 7. 恢复演练记录

| 日期 | 全量备份标识 | 目标时间点 | Tlast / 实际 RPO | 实际 RTO | 密钥版本 | 删除核对 | 结果 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| —（尚未执行） | | | | | | | 未验证 |
