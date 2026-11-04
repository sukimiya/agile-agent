# 实体关系写入路径设计（M2b 后续项）

> 状态：设计已于 2026-11-04 经用户确认（“继续”，16 项决定全部按建议），按 T1–T5 分步实施；T1 进行中（分支 `codex/memory-relations`）。依据：spec `docs/superpowers/specs/2026-10-21-memory-runtime-design.md` 第 7.2 节（轻量关系图，一跳、有上限、带范围与来源、替代不作为当前事实），计划 `docs/superpowers/plans/2026-10-23-memory-m2.md` Task 4 与 Task 8 决定 4（关系写入延后）。

## 1. 现状（2026-11-04，main `9fa3d6e`）

- 表：`memory_entities`（`(tenant_id, subject, normalized_key)` 唯一，`is_valid`）、`memory_relations`（from/to、自由文本 `relation`、可空 `item_id`、`valid_from/until`、`status` ACTIVE/SUPERSEDED/INACTIVE、`is_valid`）。
- 仓储 `EntityGraphRepository` 只有测试调用 `saveRelation`；评测 relations 组件预置一条关系验证读取。
- 读取：`MemoryRetriever.relationLines` 以问题中的编码与连续汉字为键调用 `oneHop`（上限原为 `app.memory.profile.max-relations`），`ContextAssembler` 注入“以下是相关记忆关系，仅供参考：”，每轮解释记 `RELATION / ONE_HOP`，不刷新召回时间。
- 删除与遗忘：删除条目置其关系 INACTIVE + `is_valid = 0`；删除概要使整个图失效；分档清除随条目物理删除关系；`oneHop` 排除指向 FORGOTTEN 条目的关系。
- 问题：①无写入路径；②可见性只排除 FORGOTTEN，条目 SUPERSEDED / INACTIVE（衰减、回放、会话删除失去来源）/ CONFLICT 时关系仍被召回；③`oneHop` 不看跨会话开关与条目所属会话，开关关闭时会话级关系会跨会话泄露；④`oneHop` 读出该用户全部 ACTIVE 关系后在内存过滤；⑤实体匹配要求问题片段与实体键完全相等；⑥关系类型自由文本、无去重约束。

## 2. 设计

1. **写入来源**：只由记忆判断随条目产出（REMEMBER / UPDATE / MERGE / SUPERSEDE 可带 `relations: [{from:{name,type}, relation, to:{name,type}}]`），不额外调用模型、不做确定性抽取；用户 API 只读。
2. **关系挂在条目上**：`item_id` 必填；可见性跟随条目（条目 ACTIVE 且有效 → 可见）。遗忘与恢复、删除、更正、衰减、回放、会话删除、分档清除因此自动生效，不再分别维护关系状态。UPDATE / MERGE 用新集合替换该条目原有关系（旧的 SUPERSEDED）；SUPERSEDE 由新条目带新关系，旧条目被替代后其关系自然不可见。
3. **校验**（T2）：两端实体名须在来源消息中逐字出现（“用户本人”除外，Rules 第 5 条）；关系类型与实体类型须在配置词表内，词表外只丢弃关系、条目照写；from ≠ to；每条目关系数、实体名长度有上限；敏感信息/密钥拒绝；继承条目的断言等级（粘贴、引用、假设、角色设定不能产生“用户—X”）；拒绝计入 `memory.relations.rejected{reason}`。
4. **数据模型 V18**（不考虑兼容旧数据）：`memory_relations.item_id` NOT NULL（删除无条目的旧行，生产上没有写入路径，只可能是测试数据）；唯一约束 `(tenant_id, subject, item_id, from_entity, relation, to_entity)`；索引 `(tenant_id, subject, from_entity)`、`(tenant_id, subject, to_entity)` 取代原 `(from_entity, status)` / `(to_entity, status)`；保留“用户本人”实体：保留键 `@self`、类型 `SELF`、显示为“用户”，每个用户首次使用时创建，普通实体名不得规范化为该键。会话范围取条目的 `conversation_id`，关系表不加会话列。
5. **召回**：实体匹配为“实体名被问题包含”（最短长度、扫描上限为配置），问题含第一人称词（配置）时命中“用户本人”（T3）；关系查询在 SQL 中按实体 id 与条目可见性过滤；用户级条目（`conversation_id` 为空）只在跨会话开关打开时可见，会话级条目只在同一会话可见；同一（from, 关系, to）只取最新一条；行格式“名称（类型）—关系→ 名称”（T3）；计入召回 Token 预算；仍只一跳；每轮解释与“关系召回不刷新召回时间”不变。
6. **Token 与额度**：回答路径不增加模型调用；判断提示词增加词表与字段说明约 150–250 Token，输出约多 10–20%，仍在每租户额度与 `max-output-tokens` 内，以评测实测；给判断的已有条目附带其关系（精简格式），计入 `existing-max-tokens`。
7. **配置** `app.memory.relations.*`（启动校验，不写死数值）：`enabled`（默认 true，判断开启时才生效）、`types`（默认 负责、属于、使用、采用、位于、拥有、合作、偏好）、`entity-types`（PERSON、PROJECT、ORG、PLACE、PRODUCT、TOPIC、OTHER）、`max-per-item`（3）、`max-entity-name-chars`（64）、`entity-match-min-chars`（2）、`entity-scan-limit`（500）、`max-recalled`（10，由 `app.memory.profile.max-relations` 迁移，不保留旧名）、`first-person-words`（我、我的、本人）。
8. **指标**：`memory.relations.written`、`memory.relations.rejected{reason}`、`memory.relations.recalled`。
9. **评测**：rel-01 汇总（两个会话分别说负责星河、北斗项目，开启跨会话后新会话问“我负责哪些项目？”）；rel-02 更正（PostgreSQL → MySQL，只召回 MySQL）；rel-03 投毒（“我同事负责天枢项目”不得产生“用户—负责→天枢”，新字段 `forbiddenRelation`）；rel-04 删除或遗忘条目后关系不再召回；rel-05 跨会话关闭不泄露。工程评测用假判断器全覆盖，真实模型跑 rel-01–03。

## 3. 用户决定（2026-11-04，全部按建议）

1. 关系只由记忆判断随条目产出；用户接口只读，不提供手工增删，不做确定性抽取。
2. 关系一律挂在条目上（`item_id` 必填），可见性完全跟随条目。
3. 关系类型用配置的封闭词表；词表外只丢弃该关系，条目照写。
4. 引入“用户本人”保留实体，问题含第一人称时作为命中。
5. 坚持只一跳；两跳问题交给条目与原文召回。
6. INFERRED 条目可以带关系，但只在条目 ACTIVE 时召回（CANDIDATE、CONFLICT 不召回）。
7. 跨会话关闭时会话级关系只在本会话可见、用户级关系不可见（同时修复现有泄露）。
8. UPDATE / MERGE 整体替换该条目的关系，旧的标为 SUPERSEDED。
9. 召回实体匹配改为“实体名被问题包含”。
10. Rules 升为 rules-version 2、提示词升为 v2，不自动回放，需要时由管理员手动发起。
11. 关系行计入召回 Token 预算；`profile.max-relations` 改名为 `relations.max-recalled`，不保留旧名。
12. 关系被召回仍不刷新条目的最近召回时间。
13. 条目接口返回该条目的关系（只读）。
14. 给判断的已有条目附带其关系，计入 `existing-max-tokens`。
15. `relations.enabled` 默认 true（判断开启时才生效）。
16. 评测门槛不变（不回退、投毒 0 条、Recall@5 ≥ 0.9）；新用例首跑作为新基线，之后纳入“不回退”。

## 4. 任务

| Task | 内容 | 人日 |
| --- | --- | --- |
| T1 | V18 迁移（H2 / MySQL）；仓储重写：SQL 内过滤、可见性跟随条目、跨会话与会话范围、去重、“用户本人”实体；`app.memory.relations.*` 配置与启动校验 | 1–1.5 |
| T2 | 判断写入：输出结构、提示词 v2、Rules v2、校验、各动作写入与替换、已有条目附带关系、指标 | 1.5–2 |
| T3 | 召回：实体名包含匹配、“用户本人”、格式与去重、预算、解释 | 1 |
| T4 | 条目接口返回关系；`api.md`、`operations.md` | 0.5 |
| T5 | 评测用例与组件检查、真实模型补跑、验收记录与基线 | 1 |

每个 Task 测试先行，提交推送前两套测试各跑一次；每个 Task 完成后向用户汇报再开始下一个。
