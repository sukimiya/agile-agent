# Memory M2a 验收记录（agile-agent 主线）

> 依据 spec `docs/superpowers/specs/2026-10-21-memory-runtime-design.md`（第 11 节验收 11–13）与计划 `docs/superpowers/plans/2026-10-23-memory-m2.md`（M2a Task 1–6）。分支 `codex/memory-m2a`，记录日期 2026-10-26（UTC+8）。只记录主线结果；mcp-tool 适配待 M2 验收后另行进行。

## 1. 环境

| 项 | 值 |
| --- | --- |
| JDK | OpenJDK 21.0.12.1（Linux） |
| Maven | 3.9.9 |
| 真实 MySQL | MySQL 8.4.8，仅回环地址（端口 33306），测试库名以 `_it` 结尾；`ngram_token_size=2`（D7） |
| Redis | 测试专用的本地 `redis-server` |
| 真实模型 | `deepseek-v4-flash`（`api.deepseek.com`，OpenAI 兼容流式接口），只用于 `memory-eval-live` |
| 迁移版本 | 新增 **V10**（`db/migration/V10__memory_m2a.sql`：任务去重与载荷、`memory_summaries`、`memory_chunks`、`memory_index_progress`、`user_memory_settings`、预设窗口字段）与 **V10_1**（`db/vendor/{h2,mysql}/V10_1__memory_m2a_fulltext.sql`：MySQL 为 ngram FULLTEXT，H2 无全文索引、走 LIKE）。V1–V9 未修改。V10 在上线前被直接修改过（D21），已跑过旧 V10 的开发库需要重建，见 `operations.md` |

## 2. 各任务提交

| Task | 内容 | 提交 |
| --- | --- | --- |
| 1 | 数据契约、V10 迁移与持久任务分发 | `3e2bb68` |
| 2 | 上下文预算与 ContextAssembler；输出预留作为 max_tokens | `5568e9a`、`1dd516f` |
| 3 | DomainAgent 背景传递 | `35650be` |
| 4 | 轮次记忆记录（检索索引）；按轮续租 | `f71b880`、`48c57d7`、`2bd7680`（设计文档 `b4d22a1`、`9f3517e`） |
| 5 | 基础 Recall（确定性 + ngram 全文）与跨会话开关 | `c0a8705` |
| 6 | 评测扩展、缓存 TTL 验证与本记录 | 本提交 `test: extend memory eval for m2a and record acceptance` |

## 3. 验证命令与结果（2026-10-26，JDK 21）

数字取自 Maven 汇总行。agent-server 的默认测试分主执行与单独 fork 的 BlockHound 执行两次。默认运行不包含 `mysql-it` 与 `memory-eval-live` 组（被排除，不是 SKIPPED）。

| 命令 | 结果 |
| --- | --- |
| `mvn -B -pl agent-server -am clean test` | PASS：agent-spi 17、mock-oauth2 25、agent-server 709 + BlockHound 3，0 失败，0 错误，0 跳过 |
| `mvn -B -Pmemory-mysql-it -pl agent-server -am test` | PASS：agent-spi 17、mock-oauth2 25、agent-server（mysql-it 组）169，0 失败，0 错误，1 跳过（`M2aSchemaMySqlIntegrationTest` 继承的 H2 专用升级检查 `v10RenamesPendingM1CleanupJobs`（需要停在 V9 的独立 H2 库），在 MySQL 上按设计跳过；之前各次推送相同） |
| `mvn -B -pl agent-server -am -Dtest=MemoryEvalBaselineTest,HotMemoryCacheTest test` | PASS：MemoryEvalBaselineTest 14、HotMemoryCacheTest 11 |
| `mvn -B -Pmemory-mysql-it -pl agent-server -am -Dtest=MemoryEvalBaselineMySqlIntegrationTest test` | PASS：14，真实 MySQL |
| `mvn -B -Pmemory-eval-live -pl agent-server -am test`（真实模型，完整 20 个用例） | 2026-10-26 17:58 运行一次，157 次模型调用、116519 Token；结果见 `eval-baseline.md` 第 4.3 节 |
| 同上加 `-Dmemory.eval.cases=recall-01,window-01,recall-02,recall-03,recall-04` | 2026-10-26 约 18:05 复跑召回用例，57 次调用、45319 Token；见 `eval-baseline.md` 第 4.4 节 |

H2 结果不代替 MySQL 迁移与中文全文检索的验收；fake LLM 结果不代替真实模型的回答质量。

## 4. M2a 验收项（spec 第 11 节）

| # | 验收项 | 状态 | 主要证据 |
| --- | --- | --- | --- |
| 11 | 逐轮验证上下文预算，覆盖超长工具结果、长消息和合法工具消息配对；DomainAgent 能拿到必要背景，指代追问正确 | **满足** | 预算：`ContextBudgetTest`、`ContextAssemblerTest`、`OrchestratorBudgetTest`（`oversizedToolResultReplacedWithPlaceholderButStoredInFull`、`budgetRecheckedAfterEachToolRound`、`oversizedCapabilityAnswerIsOmittedFromAggregate`）；评测组件 context-budget 5/5 PASS（每次调用的估算都在输入预算内；window-01 等小窗口用例的旧轮次被移出窗口；tool-02 约 2 万字的工具结果以占位说明发送、完整结果保存在工具记录中；long-01 的 1.2 万字长消息在默认窗口内）。工具消息配对：近期窗口按整轮截取，能力背景按整轮连续保留（`DomainAgentContextTest.backgroundFitsCapabilityBudgetWithToolSchemas`、`backgroundNeverContainsCurrentTurn`）。背景：`DomainAgentContextTest.capabilityReceivesRecentReferenceForFollowUp`；评测 capability-background PASS；真实模型上 tool-03 的 2 次能力调用都带上一轮背景并继续工具调用，回答正确（Task 3 遗留问题 2 已验证）。指代追问：coref-01、coref-02 真实模型正确 |
| 12 | 摘要带源消息范围且可回到原文；基础 Recall 的中文全文与实体精确匹配分别评测，基础 RAG 不依赖向量服务，索引滞后不丢失近期背景 | **满足**（换一种说法的召回有已知边界，见第 5 节） | 记录（原“摘要”，Task 4 改为检索用的轮次记录）：评测组件 summary 2/2 PASS（每条记录的 from/to sequence 与原文一致，`SummaryRepository.originalTurn` 取回原文）、`SummaryJobHandlerTest`。中文全文与精确匹配分别评测：`RecallMySqlIntegrationTest.chineseFulltextFindsPhraseOnMySql`（ngram）、`MemoryRetrieverTest.exactEntityMatchFindsCodes`；评测中 window-01（CHUNK_FULLTEXT）与 recall-02（ENTITY_EXACT）分开统计，真实模型均答对。无向量服务：只用 MySQL FULLTEXT / H2 LIKE 与正则编号匹配。索引滞后：近期窗口始终从数据库历史读取，不依赖索引；窗口外未建索引的轮次在限额内补查（`unindexedRangeBeyondRecentWindowIsBackfilledWithinLimit`），覆盖不足时如实报告（`coverageLimitedIsReportedNotTreatedAsAbsent`）；会话删除后即使索引尚在也不召回（`deletedConversationNeverRecalledEvenIfIndexLags`） |
| 13 | 使用可控时间验证近期窗口闲置 7 天的缓存建议值 | **满足** | `HotMemoryCacheTest.recentWindowIdleTtlSevenDaysWithControlledClock`：写入后 TTL 为 7 天；模拟闲置 6 天 23 小时后仍命中，命中把 TTL 续回 7 天；再闲置 6 天 23 小时仍命中；连续 7 天无访问后键过期，读取未命中（由数据库提供）。Redis 的过期由服务器时钟决定，测试通过改写键的剩余 TTL 来推进“经过的时间”，最后一步让键真实过期。另有 `cacheHasSevenDayIdleTtlRenewedOnHit` |

## 5. 评测结果摘要

- 组件状态（fake LLM，H2 与 MySQL 相同）：PASS 60，FAIL 0，不适用 4（全部是 M2b 的 memory-judgement）。工程路径 Recall@5 = 1.00、来源正确率 = 1.00；检索延迟 p50 / p95：H2 3.9 / 10.5 ms，MySQL 16.7 / 26.6 ms。
- 真实模型（deepseek-v4-flash，17 个回答用例）：规则判定 15 正确、1 部分正确、1 错误；人工复核 16 正确、1 错误。M1 已答对的 11 个用例没有回退；recall-01（跨会话，开关开启）由 M1 的错误变为正确；window-01、recall-02、recall-04、tool-03 正确。
- 真实模型的检索：Recall@5 = 0.75，来源正确率 = 1.00，检索 p50 / p95 = 4.2 / 11.7 ms（H2）；探针首字延迟 p50 / p95 = 3180 / 6197 ms。
- 轮次记录的生成成本：13 次调用（每轮一次），prompt 7547、completion 2539、total 10086 Token，全部一次通过校验。
- 参数最终值：`min-matched-terms=2`、D24 召回占用 ≤ 25% 且最多 5 条、D19 闲置 30 分钟且每 5 分钟扫描，均保持不变（依据见 `eval-baseline.md` 第 4.4 节）。

**未通过项：** recall-03（换一种说法）在真实模型上答错。模型生成的轮次记录关键词是“口腔医院、复诊、下周四、下午三点”，没有“看牙”这类口语说法；问题和记录只共有一个常见词，低于弱命中阈值，所以没有召回。不依赖向量服务的字面检索只能靠记录里的关键词覆盖同义说法。这是验收 12 允许范围内的已知边界，不影响 11–13 的结论；改进方向待确认（第 6 节）。

## 6. 限制与待确认

- **换一种说法的召回**：可选方向是在记录提示词中要求关键词包含口语化的同义说法（改记录提示词，需重新生成已有记录才对旧轮次生效），或等 M2b 的查询改写 / 向量后端（计划中均后置）。未改动。
- **记录生成的 Token 用量未在产品中记录**：记录生成调用在第一个 `Completed` 事件后就结束读取，OpenAI 兼容流的用量块在其后，因此产品里读不到这部分用量（评测的计数包装独立读完整个流，所以评测数字准确）。是否在产品中改为读完整个流并记录用量，待确认。
- 评测中记录由评测直接调用 `SummaryJobHandler.summarize` 生成，不经过闲置扫描（D19 的 30 分钟闲置由 Task 4 的测试覆盖）。
- mcp-tool 适配：待 M2 验收后进行。
- M2a 按 D17 单独开 PR，开 PR 前需用户确认。
