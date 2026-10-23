# Memory 评测基线（M1）

本文记录 M1 的评测基线，作为 M2 的对比起点。按计划 Task 9，基线分两类，**分别记录，不合并为一个通过率**：

1. **组件状态**：工程测试（fake LLM）验证 M1 已实现组件；Recall、摘要、上下文预算、LLM 记忆判断等未实现组件记为“不适用”，不计为失败。
2. **当前系统端到端回答结果**：真实模型在当前“完整历史注入”路径上的回答与判定，只能由 `memory-eval-live` 产生，fake LLM 的结果不能代替。2026-10-23 已用 deepseek-v4-flash 实测，见第 2 节。

评测集与判定规则见 `agent-server/src/test/resources/memory-eval/`（14 个中立用例：12 个参加回答评测，2 个纯工程用例）。

## 如何运行

| 内容 | 命令 | 输出 |
| --- | --- | --- |
| 组件状态（H2，默认测试的一部分） | `mvn -B -pl agent-server -am -Dtest=MemoryEvalBaselineTest test` | `agent-server/target/memory-eval/components-h2/component-status.{md,json}` |
| 组件状态（真实 MySQL 8.4） | `mvn -B -Pmemory-mysql-it -pl agent-server -am -Dtest=MemoryEvalBaselineMySqlIntegrationTest test` | `agent-server/target/memory-eval/components-mysql/` |
| 端到端回答结果（真实模型，仅手动） | `mvn -B -Pmemory-eval-live -pl agent-server -am test` | `agent-server/target/memory-eval/answer-results.{md,json}`（同时写一份组件状态） |

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
| `memory.recall.items` / `memory.recall.latency` | **不适用**：M1 没有 Recall，这两个指标不注册，绝不报告为 0 条或 0ms |
| `memory.consolidation.backlog` | **功能未启用**：M1 没有整理任务（删除清理任务不属于整理），该指标不注册，绝不报告为 0 积压 |
| `memory.feature.enabled{feature=recall\|consolidation}` | 显式标记功能未启用（值为 0） |

估算 Token 为启发式：每个中日韩字符计 1，其余非空白字符每 4 个计 1。它不是分词器结果，只用于描述提示规模；真实用量以服务商上报的 `prompt_tokens` 为准。
