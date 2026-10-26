# Memory 评测集（M1、M2a）

`cases.jsonl` 是固定的中立多轮评测集：每行一个用例，内容均为虚构，不含真实用户或企业数据。结果与基线说明见 `docs/memory/eval-baseline.md`。

## 字段

| 字段 | 含义 |
| --- | --- |
| `id` | 稳定用例 ID，基线按 ID 对比，不要复用或改义 |
| `category` | COREFERENCE（指代追问）、EARLY_DETAIL（早期细节）、CORRECTION（冲突修正）、LONG_MESSAGE（长消息）、TOOL_RESULT（工具结果）、PASTED_ARTICLE（粘贴文章）、QUOTE（引用）、HYPOTHETICAL（假设）、CROSS_CONVERSATION（跨会话）、ISOLATION（隔离）；M2a 新增 LONG_CONVERSATION（长会话超出窗口）、EXACT_CODE（编号精确召回）、PARAPHRASE（换一种说法） |
| `requires` | 可靠答对所需的阶段：M1 / M2a / M2b |
| `answer` | 是否参加端到端回答评测；`false` 为纯工程用例 |
| `components` | 用例涉及的组件。M1：persistence、terminal、history-injection、paging、isolation、tool-provenance、size-limit；M2a：context-budget、summary（轮次记录）、recall、capability-background（能力调用带近期背景）；未实现（记为“不适用”）：memory-judgement（M2b） |
| `turns` | 探针之前的用户消息；`filler` 追加确定性中性填充文本（`chars` 字，`insert` 放在 `at` 比例处）；`tool: true` 表示该轮在工程运行中走样例库存能力 |
| `probe` | 最后提出的问题；`probeInNewConversation: true` 表示在新会话中提出 |
| `expect` | 关键词判定规则：`all` 全部包含、`any` 至少其一、`none` 不应包含 |
| `note` | 用例意图 |
| `window` | （M2a，可选）模型窗口 `contextWindowTokens` / `maxOutputTokens`；不填用默认值 |
| `crossConversation` | （M2a，可选）为该用例的用户打开跨会话召回开关；默认关闭 |
| `recallSource` | （M2a，可选）探针应召回的轮次下标（从 0 开始）；不填表示不应召回任何内容 |
| `recallReason` | （M2a，可选）该轮的期望召回原因：ENTITY_EXACT、CHUNK_FULLTEXT、RECORD_FULLTEXT |
| `probeTool` | （M2a，可选）工程运行中探针也走样例库存能力 |

## 判定规则

- 正确（CORRECT）：满足 `all` 与 `any`，且不含 `none`。
- 部分正确（PARTIAL）：满足期望但同时出现 `none` 关键词，或只满足部分期望关键词。
- 错误（WRONG）：其他情况。
- 未能执行（ERROR）：执行未完成（模型或存储错误），不是回答质量判定。

匹配忽略大小写、空白和数字中的千位分隔符。规则判定只是初筛，记录基线时需人工复核每条回答。

## 修改规则

- 新增用例使用新 ID；前置条件字段（`window`、`crossConversation` 等）可以给已有用例补上，但问题与期望不变（`recall-01` 在 M2a 补了 `crossConversation: true`，原因见 eval-baseline.md 第 4.1 节）；不要修改已有用例的问题或期望，否则与已记录基线不可比。确需修改时在 `docs/memory/eval-baseline.md` 记录原因并重新运行两类基线。
- 不要加入真实个人信息、企业数据或凭证。

## 运行方式（M2a）

每个用例在自己的用户下运行（`eval-<用例>-<随机后缀>`），每轮结束后执行待处理的索引任务；名单含 `summary` 的用例在探针前为每个已完成轮次生成轮次记录（真实模型运行时用同一模型，成本单独统计）。
