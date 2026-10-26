# Memory 评测基线（M1 与 M2a）

本文记录 M1 的评测基线（第 1–3 节）与 M2a 的结果（第 4 节，与 M1 同模型、同用例对比）。按计划 Task 9，基线分两类，**分别记录，不合并为一个通过率**：

1. **组件状态**：工程测试（fake LLM）验证 M1 已实现组件；Recall、摘要、上下文预算、LLM 记忆判断等未实现组件记为“不适用”，不计为失败。
2. **当前系统端到端回答结果**：真实模型在当前“完整历史注入”路径上的回答与判定，只能由 `memory-eval-live` 产生，fake LLM 的结果不能代替。2026-10-23 已用 deepseek-v4-flash 实测，见第 2 节。

评测集与判定规则见 `agent-server/src/test/resources/memory-eval/`。M1 时为 14 个中立用例（12 个参加回答评测，2 个纯工程用例）；M2a 追加 6 个（5 个回答用例、1 个工程用例），共 20 个，见第 4 节。

## 如何运行

| 内容 | 命令 | 输出 |
| --- | --- | --- |
| 组件状态（H2，默认测试的一部分） | `mvn -B -pl agent-server -am -Dtest=MemoryEvalBaselineTest test` | `agent-server/target/memory-eval/components-h2/component-status.{md,json}` |
| 组件状态（真实 MySQL 8.4） | `mvn -B -Pmemory-mysql-it -pl agent-server -am -Dtest=MemoryEvalBaselineMySqlIntegrationTest test` | `agent-server/target/memory-eval/components-mysql/` |
| 端到端回答结果（真实模型，仅手动） | `mvn -B -Pmemory-eval-live -pl agent-server -am test` | `agent-server/target/memory-eval/answer-results.{md,json}`（同时写一份组件状态、工程阈值扫描 `recall-threshold-sweep.*` 与本次数据上的阈值回放 `recall-threshold-replay.*`） |
| 只跑部分用例（真实模型） | 上一行命令加 `-Dmemory.eval.cases=recall-01,window-01,...` | 同上；`-Dmemory.eval.minMatchedTerms=N` 临时改弱命中阈值，`-Dmemory.eval.output=目录` 改输出位置 |

`memory-eval-live` 不在默认构建或 `memory-mysql-it` 中运行。它需要以下环境变量，缺任何一个都会直接失败，错误信息只列变量名，不打印变量值：

| 变量 | 必需 | 说明 |
| --- | --- | --- |
| `MEMORY_EVAL_LLM_BASE_URL` | 是 | OpenAI 兼容接口的基础地址（含 `/v1`），请求发往 `{BASE_URL}/chat/completions`，与租户模型预设的 `base_url` 含义相同 |
| `MEMORY_EVAL_LLM_API_KEY` | 是 | 该接口的 API key。只用于请求头，不写入任何报告或日志；不要提交到仓库 |
| `MEMORY_EVAL_LLM_MODEL` | 是 | 模型名称，与租户模型预设的 `model` 相同 |
| `MEMORY_EVAL_LLM_MODEL_VERSION` | 否 | 模型版本或快照标识，写入报告；不提供时记为“未提供” |

运行方式：使用与生产相同的 `Orchestrator`、`DomainAgent`、`TurnLifecycle`、`ConversationHistory` 和 `OpenAiCompatibleLlmClient`，数据存在隔离的 H2（MODE=MySQL）与本地测试 Redis 中。每个用例在新会话里逐轮发送，每轮都真实调用模型（路由一次，再回答一次；工具用例走中立的“样例库存查询”能力，数据固定），最后提出探针问题。没有质量门槛；只有用例无法执行（判定为“未能执行”）时运行才失败，此时结果文件已经写出。每次完整运行约 88 次模型调用（12 个回答用例共 43 轮，每轮路由与回答各一次，工具轮另加两次能力内调用）；long-01 含长消息的调用每次约 1.1 万估算 Token。

## 1. 组件状态（已实测）

- 测量方式：`MemoryEvalBaselineTest`（H2）与 `MemoryEvalBaselineMySqlIntegrationTest`（MySQL 8.4），fake LLM 驱动真实 Orchestrator 路径。
- 测量时间：2026-10-23 11:29（H2）与 11:30（MySQL），UTC+8。
- 代码：Task 9 工作树，基于 `82e6df2`，与本文同一提交。
- 环境：JDK 21.0.12.1、Linux、本地测试 Redis；MySQL 8.4.8（loopback 隔离测试库）。

| 存储 | PASS | FAIL | 不适用 |
| --- | --- | --- | --- |
| H2（MODE=MySQL） | 37 | 0 | 7 |
| MySQL 8.4 | 37 | 0 | 7 |

按组件汇总（两种存储结果相同）：

| 组件 | 阶段 | 用例数 | 结果 | 检查内容 |
| --- | --- | --- | --- | --- |
| persistence | M1 | 11 | 11 PASS | 用户输入与回答按顺序入库，原文逐字一致（含 1.2 万字长消息） |
| terminal | M1 | 11 | 11 PASS | 每轮执行均为 COMPLETED |
| history-injection | M1 | 11 | 11 PASS | 探针的回答提示包含全部已完成历史原文、顺序一致，不含当前执行 |
| paging | M1 | 1 | 1 PASS | 每页 3 条向前翻页，拼接结果与 14 条原文一致 |
| tool-provenance | M1 | 1 | 1 PASS | 工具调用记录为 SUCCEEDED、来源 TOOL_RESULT，结果已保存 |
| size-limit | M1 | 1 | 1 PASS | 超过 1 MiB 的消息返回 413 MESSAGE_TOO_LARGE，未截断、未入库 |
| isolation | M1 | 1 | 1 PASS | 同一 subject 在另一租户读取与续写均为 404，原会话未变 |
| summary | M2a | 1 | 不适用 | 摘要未实现 |
| context-budget | M2a | 1 | 不适用 | 上下文预算未实现 |
| recall | M2a | 1 | 不适用 | Recall 未实现；召回率、召回延迟均不适用 |
| memory-judgement | M2b | 4 | 不适用 | LLM + Rules 记忆判断未实现 |

“不适用”表示该能力在 M1 没有实现，没有可测的组件指标，**不是 0，也不是失败**。逐用例明细在上述输出文件中。

## 2. 当前系统端到端回答结果（已实测）

- 运行时间：2026-10-23 13:25:56 开始（UTC+8），回答评测合计耗时约 175 秒（Maven 整体约 184 秒）。
- 提交：`d3aea8c`（工作树无未提交的已跟踪改动）。
- 命令：`mvn -B -Pmemory-eval-live -pl agent-server -am test`。
- 模型：`deepseek-v4-flash`（版本标识 `deepseek-v4-flash`，由 `MEMORY_EVAL_LLM_MODEL_VERSION` 提供；未采集服务商响应中的模型字段）。
- 接口：`api.deepseek.com`（OpenAI 兼容 `/v1/chat/completions`，流式）。API key 通过环境变量提供，未写入任何报告、日志或仓库。
- 采样参数：未发送 temperature 等参数，使用服务商默认值（与当前系统行为一致）。
- 上下文输入方式：完整历史注入（FULL_HISTORY）：已完成执行的全部原文消息，按顺序注入路由与回答两次调用；无预算、摘要或召回。
- 环境：H2（MODE=MySQL）+ 本地测试 Redis，JDK 21.0.12.1，Linux。
- 模型调用次数：88 次（43 轮 × 路由与回答各 1 次，工具轮的回答改为 2 次能力内调用 + 1 次汇总），由运行日志核对。
- Token：探针执行的服务商上报 prompt_tokens 合计 23202（每个探针为路由与回答两次调用之和）；探针注入历史的估算 Token 合计 15308（启发式）。非探针轮的用量未汇总记录。

判定汇总（关键词规则 + 逐条人工复核，复核未改变任何规则判定）：

| 所需阶段 | 用例数 | 正确 | 部分正确 | 错误 | 未能执行 |
| --- | --- | --- | --- | --- | --- |
| M1 | 6 | 6 | 0 | 0 | 0 |
| M2a | 2 | 1 | 0 | 1 | 0 |
| M2b | 4 | 4 | 0 | 0 | 0 |
| 合计 | 12 | 11 | 0 | 1 | 0 |

| 用例 | 类别 | 所需阶段 | 判定 | 回答 | 注入消息数 | 估算 Token | 实际 prompt_tokens | 耗时 ms | 人工复核 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| coref-01 | COREFERENCE | M1 | 正确 | 青岛，3000元 | 4 | 1069 | 2051 | 19520 | 正确：城市与预算均来自第一轮 |
| coref-02 | COREFERENCE | M1 | 正确 | 小 | 4 | 434 | 899 | 14264 | 正确：“芝麻”对应小的那只 |
| early-01 | EARLY_DETAIL | M1 | 正确 | A7-3921 | 12 | 379 | 845 | 22805 | 正确：隔五轮无关对话仍给出编号 |
| early-02 | EARLY_DETAIL | M2a | 正确 | 北辰，11 月 14 日。 | 16 | 807 | 1479 | 36445 | 正确：隔七轮，代号与日期都对 |
| corr-01 | CORRECTION | M1 | 正确 | 重庆 | 6 | 379 | 827 | 16130 | 正确：采用更正后的城市 |
| corr-02 | CORRECTION | M2b | 正确 | 绿色 | 4 | 124 | 449 | 9705 | 正确：采用更正后的颜色 |
| long-01 | LONG_MESSAGE | M1 | 正确 | W-5518 | 2 | 11386 | 14069 | 6881 | 正确：从 1.2 万字中取出插入的编号 |
| tool-01 | TOOL_RESULT | M1 | 正确 | 37 | 2 | 96 | 497 | 9371 | 正确：日志确认该轮实际路由到 EVAL_INVENTORY 并调用 lookup_inventory，数字来自上一轮最终回答 |
| paste-01 | PASTED_ARTICLE | M2b | 正确 | 茶，一杯。 | 4 | 159 | 495 | 12502 | 正确：区分文章观点与本人说法 |
| quote-01 | QUOTE | M2b | 正确 | 你刚才说的是**你同事**下个月要搬去**杭州**；不是你要搬。如果你自己也有搬家计划，得再告诉我具体信息，我才能知道你要搬去哪里。 | 2 | 78 | 377 | 7423 | 正确：指出搬家的是同事，不是用户 |
| hypo-01 | HYPOTHETICAL | M2b | 正确 | 广州 | 4 | 397 | 923 | 13231 | 正确：区分假设身份与真实情况 |
| recall-01 | CROSS_CONVERSATION | M2a | 错误 | 不知道 | 0 | 0 | 291 | 6710 | 错误，符合预期：M1 没有跨会话记忆，新会话无注入历史；模型如实回答“不知道”而非编造 |

解读（作为 M2 的对比基线，不是质量门槛）：

- 在这组较短的会话里（最多 9 轮、最长约 1.1 万估算 Token），完整历史注入足以答对所有单会话问题，包括标注为 M2a/M2b 的 early-02、corr-02、paste-01、quote-01、hypo-01。这些用例在 M1 答对，依赖的是“全部原文都在提示里”；会话变长、超出上下文窗口后不再成立，M2 应在相同用例上至少保持这一结果。
- 唯一错误是 recall-01：M1 没有跨会话记忆，这是预期结果，也是 M2a Recall 的主要对比点。
- long-01 的探针 prompt_tokens 为 14069，约为其他用例（291–2051）的 7–48 倍：完整历史注入的成本随历史长度线性增长，这是 M2a 上下文预算的对比点。
- 规则判定有局限：例如回答“重庆（不是成都）”会被判为部分正确。本次没有出现这种情况，但后续运行仍需逐条复核。

## 3. 基础指标口径

指标由 `MemoryMetrics`（Micrometer）记录，只记录真实发生的事件。默认只暴露 `/actuator/health`；需要采集指标时，由部署方另外开放 metrics 端点或配置导出。

| 指标 | 含义 |
| --- | --- |
| `memory.turn.accepted{replay=false\|true}` | 新接受的执行 / 已完成请求的重放；被拒绝的请求（409、413、503 等）不计 |
| `memory.turn.terminal{status}` | 实际写入的终态，按 COMPLETED / FAILED / CANCELLED / INTERRUPTED 统计。来源包括收尾器、恢复扫描、接受时处理的租约过期和会话删除；同一执行只计一次 |
| `memory.checkpoint.latency` | 流式检查点写入耗时（成功持有执行权的写入） |
| `memory.cache.requests{result=hit\|miss}` | 最新一页历史由缓存提供 / 回源数据库（包括缓存过期或不可用） |
| `memory.finalizer.failures` | 终态写入失败、超时或队列已满；执行权已失去不算失败 |
| `memory.instance.conflict` | 实例冲突（0/1），由 Task 8 的 InstanceGuard 注册 |
| `memory.history.injected.messages` / `.tokens` | 每次执行注入的历史消息条数和估算 Token。这是“历史注入”，与长期记忆召回分开计数。M2a 起只统计最终回答调用在预算内实际带上的历史（未乘 1.3 系数） |
| `memory.recall.items` / `memory.recall.latency` | M1：**不适用**，不注册，绝不报告为 0 条或 0ms。M2a 起在启用 Recall（`app.memory.recall.enabled`，默认开启）时注册：每轮一次召回的条数与耗时；关闭时仍为“不适用” |
| `memory.consolidation.backlog` | **功能未启用**：M1 没有整理任务（删除清理任务不属于整理），该指标不注册，绝不报告为 0 积压 |
| `memory.feature.enabled{feature=recall\|consolidation}` | 功能是否启用：M2a 起 recall 启用时为 1；consolidation 在 M2b 之前为 0 |

估算 Token 为启发式：每个中日韩字符计 1，其余非空白字符每 4 个计 1。它不是分词器结果，只用于描述提示规模；真实用量以服务商上报的 `prompt_tokens` 为准。

## 4. M2a 结果（2026-10-26，与 M1 同模型对比）

M2a 的路径：每次模型调用按模型窗口做上下文预算（近期完整轮次从新到旧放到预算内为止）；基础 Recall（原文片段与轮次记录的中文全文检索、编号精确匹配，按轮次合并，窗口外命中的原始轮次作为参考资料消息注入，占历史预算 ≤ 25%、最多 5 条，D24）；轮次记录只用于检索，不进入提示词；能力调用带近期背景（Task 3）。

### 4.1 新增用例（`cases.jsonl` 第 15–20 行）

| 用例 | 类别 | 回答评测 | 内容 | 窗口 |
| --- | --- | --- | --- | --- |
| window-01 | LONG_CONVERSATION | 是 | 第一轮给出集合地点，之后 8 轮约 600 字的闲聊，探针问集合地点：第一轮已不在近期窗口，只能经原文全文召回找回 | 3000 / 输出 512 |
| recall-02 | EXACT_CODE | 是 | 第一轮说报销单 `BX-7731` 的金额，之后 4 轮闲聊，探针按编号问金额：编号精确召回 | 3000 / 512 |
| recall-03 | PARAPHRASE | 是 | 第一轮说“下周四下午三点去口腔医院复诊”，之后 4 轮闲聊，探针问“什么时候去看牙”：原文全文太弱，只能经轮次记录（关键词）命中 | 3000 / 512 |
| recall-04 | CROSS_CONVERSATION | 是 | 跨会话开关关闭（默认）时，新会话问另一会话里说过的工位号，应答“不知道” | 默认 |
| tool-02 | TOOL_RESULT | 否（工程） | 工具返回约 2 万字的明细：模型看到占位说明，完整结果保存在工具记录中 | 4096 / 512 |
| tool-03 | TOOL_RESULT | 是 | 上一轮查过库存，探针要求再查一次：能力调用带上一轮背景并继续工具调用（Task 3 遗留问题 2 在真实模型上的验证） | 默认 |

另外，`recall-01` 增加了一个**前置条件字段** `crossConversation: true`（开启该用例所属用户的跨会话开关）和 `recallSource: 0`。问题与期望没有改：M1 里这个用例本来就是“跨会话召回”的对比点，M2a 的跨会话召回默认关闭，不开开关这个用例就没有意义；“开关关闭时不应召回”由新用例 recall-04 覆盖。

每个用例在自己的用户下运行（`eval-<用例>-<随机后缀>`），跨会话召回只看得到本用例的会话。

### 4.2 组件状态（已实测）

- 测量时间：2026-10-26 17:57（H2 与 MySQL 8.4，UTC+8），代码为本次提交的工作树（基于 `c0a8705`）。
- 汇总：H2 与 MySQL 都是 **PASS 60，FAIL 0，不适用 4**（不适用的全部是 M2b 的 memory-judgement）。

| 组件 | 阶段 | 用例数 | 结果 | 检查内容 |
| --- | --- | --- | --- | --- |
| persistence / terminal | M1 | 16 / 16 | 全部 PASS | 同 M1 |
| history-injection | M1 | 11 | 11 PASS | 默认窗口下探针提示包含全部已完成历史（召回资料消息不算历史） |
| paging / isolation / tool-provenance / size-limit | M1 | 各 1 | 全部 PASS | 同 M1 |
| context-budget | M2a | 5 | 5 PASS | 每次模型调用的估算（含 1.3 安全系数）都在输入预算内；小窗口用例的第一轮不在探针的近期窗口；tool-02 的超长结果以占位说明发送，完整结果在工具记录中 |
| summary | M2a | 2 | 2 PASS | 每个已完成轮次一条记录，来源范围与原文一致，`originalTurn` 取回的原文与库中一致 |
| recall | M2a | 5 | 5 PASS | 来源轮次在前 5 条、召回原因符合预期（CHUNK_FULLTEXT / ENTITY_EXACT / RECORD_FULLTEXT）、原文作为参考资料 user 消息注入且不在系统消息中；开关关闭时不召回 |
| capability-background | M2a | 1 | 1 PASS | 能力调用依次为能力提示词、上一轮背景、当前问题，并在同一请求中继续工具调用 |
| memory-judgement | M2b | 4 | 不适用 | M2b 实现 |

工程路径的检索质量（fake LLM 生成的记录带有“看牙”关键词，所以 recall-03 在这里能命中；真实模型的结果见 4.4）：Recall@5 = 1.00，来源正确率 = 1.00；检索延迟 p50 / p95：H2 3.9 / 10.5 ms，MySQL 16.7 / 26.6 ms（71 次召回，含每个轮次）。

### 4.3 端到端回答结果（真实模型，已实测）

- 运行时间：2026-10-26 17:58:45 开始（UTC+8），回答评测约 5 分钟（Maven 测试阶段 295 秒）。
- 代码：基于 `c0a8705` 的本次工作树（报告中记为 `c0a8705ca056-dirty`）。随后的召回用例复跑（4.4）只多了报告字段和回放功能，检索逻辑相同。
- 命令：`MEMORY_EVAL_LLM_BASE_URL=https://api.deepseek.com/v1 MEMORY_EVAL_LLM_MODEL=deepseek-v4-flash MEMORY_EVAL_LLM_MODEL_VERSION=deepseek-v4-flash mvn -B -Pmemory-eval-live -pl agent-server -am test`（API key 只来自环境变量，已核对日志与报告中都没有出现）。
- 模型与采样参数：同 M1（`deepseek-v4-flash`，`api.deepseek.com`，不发送 temperature）。
- 上下文输入方式：`M2A_BUDGET_RECALL`。参数：`recall.min-matched-terms=2, max-items=5, budget-share=0.25, candidate-limit=20, chunk-max-tokens=1500; summary.max-source-tokens=12000, max-output-tokens=800, max-invalid-attempts=3; context.estimate-factor=1.3`。
- 模型调用与 Token（服务商上报，按阶段）：

| 阶段 | 调用次数 | prompt | completion | total |
| --- | --- | --- | --- | --- |
| 用例轮次（52 轮） | 108 | 62350 | 10568 | 72918 |
| 探针（17 个） | 36 | 31014 | 2501 | 33515 |
| 轮次记录生成（early-02 的 8 轮、recall-03 的 5 轮） | 13 | 7547 | 2539 | 10086 |
| 合计 | 157 | 100911 | 15608 | 116519 |

轮次记录的生成成本：每轮一次调用，平均约 580 prompt + 195 completion Token；13 次全部一次通过校验，写入 13 条记录。

判定汇总（关键词规则 + 逐条人工复核）：

| 所需阶段 | 用例数 | 正确 | 部分正确（规则） | 错误 | 未能执行 |
| --- | --- | --- | --- | --- | --- |
| M1 | 6 | 6 | 0 | 0 | 0 |
| M2a | 7 | 6 | 0 | 1 | 0 |
| M2b | 4 | 3 | 1 | 0 | 0 |
| 合计 | 17 | 15 | 1 | 1 | 0 |

人工复核后：16 正确、1 错误（paste-01 的“部分正确”是规则局限，见下表）。

| 用例 | M1 判定 | M2a 判定 | M2a 回答 | M1 → M2a prompt_tokens | 召回（原因；*=已注入） | 首字 ms | 人工复核 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| coref-01 | 正确 | 正确 | 青岛，3000元 | 2051 → 1687 | 0 | 2781 | 正确 |
| coref-02 | 正确 | 正确 | 小 | 899 → 895 | 2（CHUNK，窗口内） | 3188 | 正确 |
| early-01 | 正确 | 正确 | A7-3921 | 845 → 807 | 1（CHUNK，窗口内） | 2623 | 正确 |
| early-02 | 正确 | 正确 | 北辰，11 月 14 日。 | 1479 → 1571 | 4（CHUNK，窗口内） | 3180 | 正确 |
| corr-01 | 正确 | 正确 | 重庆 | 827 → 959 | 3（CHUNK，窗口内） | 2734 | 正确：采用更正后的城市 |
| corr-02 | 正确 | 正确 | 绿色 | 449 → 467 | 2（CHUNK，窗口内） | 3123 | 正确 |
| long-01 | 正确 | 正确 | W-5518 | 14069 → 14069 | 1（CHUNK，窗口内） | 6197 | 正确；Token 只记录，不作为目标 |
| tool-01 | 正确 | 正确 | 37 | 497 → 489 | 1（ENTITY，窗口内） | 2667 | 正确 |
| paste-01 | 正确 | 部分正确 | 茶：1杯；咖啡：0杯。 | 495 → 493 | 1（CHUNK，窗口内） | 3292 | **正确**：规则要求“一杯”，回答写成“1杯”；没有采用文章里的“八杯” |
| quote-01 | 正确 | 正确 | 要搬去杭州的是你同事，不是你…… | 377 → 357 | 1（CHUNK，窗口内） | 4246 | 正确 |
| hypo-01 | 正确 | 正确 | 广州 | 923 → 847 | 0 | 3038 | 正确 |
| recall-01 | 错误（预期） | **正确** | 6 | 291 → 439 | 1（CHUNK，跨会话）* | 4248 | 正确：开关开启后从另一会话召回原文 |
| window-01 | — | 正确 | 西湖文化广场北门 | — → 1955 | 1（CHUNK）* | 2476 | 正确：第一轮已出窗口，经召回找回 |
| recall-02 | — | 正确 | 860元 | — → 1999 | 1（ENTITY）* | 3181 | 正确 |
| recall-03 | — | **错误** | 暂无相关信息。 | — → 2137 | 0 | 4325 | 错误：没有召回，见 4.4 |
| recall-04 | — | 正确 | 不知道 | — → 291 | 0 | 2590 | 正确：开关关闭不召回 |
| tool-03 | — | 正确 | 37 | — → 1552 | 1（ENTITY，窗口内） | 6010 | 正确：见下 |

- 首字延迟（探针，从发出请求到第一段回答）：p50 3180 ms，p95 6197 ms。检索延迟（每轮一次，69 次）：p50 4.2 ms，p95 11.7 ms（H2）。探针 prompt_tokens 合计 31014（M1 为 12 个用例 23202；按相同 12 个用例算，M2a 为 23080）。
- Recall@5 = 0.75（4 个有来源的用例中 recall-03 未命中），来源正确率 = 1.00（召回的 4 条全部指向正确轮次，recall-04 没有召回任何内容）。
- **M1 已答对的 11 个用例没有回退**（paste-01 复核为正确）。recall-01 在开关开启时由错误变为正确。long-01 在默认窗口（32768 / 输出 4096）内放得下，prompt_tokens 与 M1 相同；按计划这一项只记录，不作为目标。
- **能力调用携带历史（Task 3 遗留问题 2）已在 DeepSeek 上验证**：tool-03 探针的 2 次能力调用都带上一轮背景（上一轮用户消息与最终回答）和工具定义，第 2 次在同一请求中带工具结果（tool 消息）继续，服务商接受这一消息序列并给出正确回答 37；tool-01 的工具轮同样正常完成。说明：对话历史只保存用户消息和最终回答（工具细节在 `chat_tool_events`），所以背景里没有上一轮的工具调用消息；验证的是“背景 + 当前一轮的工具调用往返”这一序列。
- 窗口内的召回（coref-02、early-02 等）按设计不注入：命中的轮次已经在近期窗口里。

### 4.4 召回用例复跑与弱命中阈值（T5 遗留问题 3 的调参）

为了诊断 recall-03 并在真实数据上调弱命中阈值，只复跑了 5 个召回用例（`-Dmemory.eval.cases=recall-01,window-01,recall-02,recall-03,recall-04`，2026-10-26 约 18:05，UTC+8）：57 次调用（用例轮次 42、探针 10、轮次记录 5），total 45319 Token。结果与完整运行一致：4 正确，recall-03 错误（回答“未知”）。

recall-03 的来源轮次由模型生成的记录：要点“用户告知下周四下午三点要去口腔医院复诊，助手确认……”，关键词“口腔医院、复诊、下周四、下午三点”。问题“我什么时候要去看牙？”的检索词（去掉虚词后的双字词）与这条记录只共有“要去”一个词，低于阈值 2，所以没有召回。**换一种说法的召回目前取决于记录里有没有口语化的同义说法**；fake LLM 的记录带“去看牙”所以工程路径能命中，真实模型没有写。

在这次运行写入的原文与记录上回放探针检索（不调用模型，`recall-threshold-replay.md`）：

| min-matched-terms | Recall@5 | 来源正确率 | 召回条数 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | 1.00 | 1.00 | 4 | recall-03 经“要去”一个词命中（巧合，不是语义匹配） |
| 2（当前） | 0.75 | 1.00 | 3 | recall-03 未命中 |
| 3 | 0.75 | 1.00 | 3 | 同 2 |

结论与最终参数：**阈值保持 2**。改成 1 能让这一个用例命中，但只是因为共有一个常见词；在 20 个用例的完整运行里，阈值 2 时 M1 用例的探针已经会召回 1–4 条只是话题相近的轮次（都在窗口内，所以没有注入）；阈值 1 只要共有一个常见双字词就算命中，长会话里窗口外的这类噪声条目会进入提示。D24（≤ 25%、最多 5 条）保持不变：所有命中用例的原文都放进了参考资料，回答正确，没有出现预算不足或被挤掉的情况。D19（闲置 30 分钟后建记录、每 5 分钟扫描）评测中不触发（评测直接调用记录生成），保持不变。recall-03 的改进方向（记录提示词要求写出口语化同义说法，或 M2b 的查询改写）作为待确认问题记录在 `phase-2a-acceptance.md`。

### 4.5 解读

- 在这组用例上，M2a 的预算和召回没有让任何 M1 已答对的用例变差；跨会话召回、超窗口召回、编号召回三类新能力都在真实模型上答对。
- 唯一错误是“换一种说法”的召回（recall-03）：基础 RAG 不依赖向量服务（spec 验收 12），字面检索只能靠记录里的关键词覆盖同义说法。这是当前设计的已知边界，不是实现缺陷。
- 本项目不以省 Token 为目标；prompt_tokens 只作对比记录。小窗口用例的探针 prompt_tokens 约 2000（窗口 3000），说明预算按窗口生效。
