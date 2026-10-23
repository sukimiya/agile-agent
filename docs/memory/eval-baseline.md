# Memory 评测基线（M1）

本文记录 M1 的评测基线，作为 M2 的对比起点。按计划 Task 9，基线分两类，**分别记录，不合并为一个通过率**：

1. **组件状态**：工程测试（fake LLM）验证 M1 已实现组件；Recall、摘要、上下文预算、LLM 记忆判断等未实现组件记为“不适用”，不计为失败。
2. **当前系统端到端回答结果**：真实模型在当前“完整历史注入”路径上的回答与判定，只能由 `memory-eval-live` 产生。fake LLM 的结果不能代替。

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

## 2. 当前系统端到端回答结果（待运行）

**状态：待运行。** 本次实现所在环境没有可用的模型接口与 API key，因此没有运行 `memory-eval-live`，**尚无任何端到端数据**；在真实运行之前，下表不得填写或估算。

运行前已确认的事项：

- 缺配置时运行失败，并列出缺少的变量名。
- 用本地桩接口（OpenAI 兼容 SSE，固定回答）做过一次冒烟运行，确认请求、流式解析、入库与报告写出都正常。桩接口的回答不是模型回答，没有记录为基线。

真实运行后，按 `answer-results.md` 填写以下内容，并对每条判定人工复核：

- 运行信息：日期（UTC+8）、提交、模型名称与版本、接口主机（不写 key）、温度等采样参数（当前系统不发送，使用服务商默认值）、上下文输入方式（完整历史）、环境。
- 判定汇总：正确 / 部分正确 / 错误 / 未能执行，并按所需阶段（M1 / M2a / M2b）分别统计。
- 逐用例：回答、判定（规则 + 人工复核）、注入消息数、注入历史估算 Token、服务商上报的 prompt_tokens（路由与回答合计）、耗时。

| 用例 | 类别 | 所需阶段 | 判定 | 回答 | 注入消息数 | 估算 Token | 实际 prompt_tokens | 耗时 ms |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| （待运行） | | | | | | | | |

M1 不要求端到端结果达到质量门槛。预期 recall-01（跨会话）在 M1 答不出，这正是 M2a 的对比点。

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
| `memory.history.injected.messages` / `.tokens` | 每次执行注入的历史消息条数和估算 Token。这是“历史注入”，与长期记忆召回分开计数 |
| `memory.recall.items` / `memory.recall.latency` | **不适用**：M1 没有 Recall，这两个指标不注册，绝不报告为 0 条或 0ms |
| `memory.consolidation.backlog` | **功能未启用**：M1 没有整理任务（删除清理任务不属于整理），该指标不注册，绝不报告为 0 积压 |
| `memory.feature.enabled{feature=recall\|consolidation}` | 显式标记功能未启用（值为 0） |

估算 Token 为启发式：每个中日韩字符计 1，其余非空白字符每 4 个计 1。它不是分词器结果，只用于描述提示规模；真实用量以服务商上报的 `prompt_tokens` 为准。
