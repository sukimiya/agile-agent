# 知识库文档上传审核流程设计

## 背景与动机

`mcp-tool` 当前的知识边界只有 `ApiCatalog` 静态编译进 classpath 的 MES/CRM/WMS/QMS/SRM
接口文档（`src/main/resources/api-docs/*.md`），更新走独立脚本流程
（见 `docs/superpowers/specs/2026-07-07-api-docs-refresh-design.md`），需要重新打包部署。

用户希望能"在线"扩充知识库：业务/运营人员上传业务流程类文档（比如某个业务规则说明、
FAQ、SOP），经审核通过后固化为 agent 可用的知识。这类知识是纯陈述性的参考资料，
不涉及调用外部系统（区别于"新系统 API 接口文档"——那类文档涉及运行时动态注册 HTTP
client、安全白名单等完全不同量级的问题，**明确排除在本次范围外**，留作独立需求）。

## 与多 Agent 协作规划的关系

这是一次更大的"主 agent 重构为纯调度者 + 域 agent（MES/CRM/WMS/QMS/SRM + 知识库）+
Redis Streams 跨进程分发"讨论中拆分出来的**第一个子项目**，也是唯一在本次
brainstorming 中定稿设计的部分。拆分出的 5 个子项目及顺序：

1. **知识库文档上传审核链路**（本文档范围）—— 完全自包含，不依赖后续任何一项。
2. 域 Agent 配置模型 + Orchestrator 路由（同进程内，`ApiCatalog` 按服务分片 +
   知识库条目并入"域"的概念）。
3. Redis Streams 任务总线（consumer group、跨进程分发）。
4. 进度回传 pub/sub + 扇出聚合降级。
5. 投机扇出提前终止 + Resilience4j 限流 + Gatling 压测。

**本文档不涉及**："审核通过的文档如何被 agent 消费"这件事——`/lib` 目录里的内容
在子项目 2 定稿前只是"静静地待在那里"，暂不接入任何 agent 调用路径。

## 目标（本次范围）

上传 → 临时目录 → 列表审核（可预览 LLM 生成的摘要）→ approve/reject → 落库
`./lib` 目录，形成一条完整、可独立验收的审核流水线。

## 非目标

- 不支持"新系统 API 接口文档"类型的知识（涉及动态 HTTP client 注册、白名单扩展、
  外部系统调用权限，安全模型需要独立设计）。
- 不做 RAG / 向量检索（此前已验证中文嵌入匹配不可靠，见 2026-06-12 设计文档，
  与 `docs/goal/2026-07-15-reactive-multi-agent-design.md` 的既有决策一致）。
- 不支持 PDF/Word 等富文本格式，只接受纯文本/Markdown。
- 不做审核权限分级——上传和 approve/reject 都只要求登录（`@CurrentUser`），不引入
  SmartLink 的 `userType`/`permissions` 角色判断（那是"新系统 API 文档审核"独立需求
  里才需要的更严格门槛）。
- 不做过期自动清理——待审核文件没有 TTL，一直等审核（与现有 `UploadProperties.ttlHours`
  声明了但从未真正实现清理逻辑的现状一致）。
- 不实现"审核通过后如何被 agent 消费"——见上一节。

## 数据模型

新增 Flyway 迁移，新表 `knowledge_documents`（R2DBC 访问，与 `SessionRepository` 同一模式）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | 主键，服务端生成的 UUID 字符串 | 同时作为 `/lib` 里文件名前缀 |
| `title` | varchar | 上传时可选填，不填则用原始文件名 |
| `original_filename` | varchar | 原始文件名 |
| `temp_path` | varchar | 临时目录里的实际存储路径 |
| `status` | varchar | `PENDING` / `APPROVED` / `REJECTED` |
| `summary` | text，可为空 | 上传时 LLM 生成的摘要；approve 时可被覆盖；生成失败时留空 |
| `uploader_user_id` | varchar | 上传人 |
| `uploaded_at` | timestamp | 上传时间 |
| `reviewed_by_user_id` | varchar，可为空 | 审核人（approve 或 reject 时写入） |
| `reviewed_at` | timestamp，可为空 | 审核时间 |

## 模块划分

新建独立包 `com.jowo.agent.knowledge`（不复用现有 `UploadController`/
`FileStorageService`——那是给聊天附件用的，按 `sessionId` 分目录、无审核状态机、
生命周期完全不同，混在一起会把两个不相关的关注点耦合起来）：

- `KnowledgeController`（WebFlux `@RestController`）
- `KnowledgeDocumentService`（业务逻辑：状态机、摘要生成调度、落库编排）
- `KnowledgeDocumentRepository`（R2DBC `DatabaseClient`，模式同 `SessionRepository`）
- `KnowledgeFileStorage`（临时目录/`./lib` 目录的文件读写；校验、路径穿越防护逻辑
  参考 `FileStorageService` 现成实现，但不共享类）

## API 端点

沿用现有 `@CurrentUser` 认证模式（任何登录用户即可，无角色区分）：

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/api/knowledge/upload` | multipart 上传（`FilePart`）。请求带可选 `title`；服务端落临时目录，同步调 LLM 生成摘要，写入 `knowledge_documents`（`PENDING`），返回文档 id + 摘要。 |
| `GET` | `/api/knowledge?status=PENDING` | 列表，默认 `PENDING`，可传 `APPROVED`/`REJECTED` 查历史。返回 id/title/uploader/summary/uploadedAt/下载链接。 |
| `GET` | `/api/knowledge/{id}/download` | 下载原文件（PENDING/REJECTED 读 `temp_path`；APPROVED 读 `/lib` 里的原文）。 |
| `POST` | `/api/knowledge/{id}/approve` | 请求体可选 `summary` 覆盖自动生成的摘要。落库到 `/lib`，状态转 `APPROVED`。 |
| `POST` | `/api/knowledge/{id}/reject` | 状态转 `REJECTED`，原文件留在临时目录不删（供追溯），从默认 list 里消失。 |

## 文件流转与存储

**上传阶段**：校验 content-type（仅 `text/plain`/`text/markdown`）与大小上限
（新增 `app.knowledge.max-size-mb` 配置）。落到新的临时目录（`app.knowledge.temp-dir`，
区别于聊天附件的 `app.upload.dir`），文件名按 `{id}.md` 存放（`id` 生成后即确定），
文件名/路径穿越防护复用 `FileStorageService.sanitize` 的思路。

上传成功后**同步**调用一次 `LlmClient` 生成摘要（新增一个非流式的便捷方法，比如
`LlmClient.summarize(String content): Mono<String>`，内部收敛 `chatStream` 的
`Completed` 事件），随文档记录一起存库。**该步骤失败时降级**：记录警告日志，
摘要存空字符串，不阻塞上传本身——上传应该总能成功，摘要只是锦上添花，管理员
在 approve 时可以手动补一段摘要。

**Approve 阶段**：读临时文件内容，连同（可能被覆盖的）摘要一起写入两个文件：
- `./lib/{id}.md`（原文）
- `./lib/{id}.meta.json`（`{title, summary, approvedBy, approvedAt}`）

写完后更新数据库状态为 `APPROVED`。`temp_path` 保留原值不动（审核记录留痕），
`/lib` 是唯一会被后续域 agent 消费的"生产知识库"目录，自描述、不依赖数据库
（子项目 2 实现"域 agent 读取 `/lib`"时，直接扫目录就能拿到全部已批准条目的
索引信息，无需查库）。

**Reject 阶段**：只改数据库状态，不碰文件系统。

## 错误处理

- 上传：非白名单 content-type → 400；超过大小限制 → 400。
- approve/reject 一个不存在的 id → 404。
- **并发保护**：approve/reject 使用条件更新
  `UPDATE knowledge_documents SET status = ? ... WHERE id = ? AND status = 'PENDING'`，
  影响行数为 0（已被处理过，或 id 不存在但先前已被上面的存在性检查排除）→ 返回
  409 Conflict，避免同一份文档被写进 `/lib` 两次或状态被覆盖。
- download：根据当前状态决定读 `temp_path` 还是 `/lib`，文件系统层面不存在
  （理论上不该发生，防御性处理）→ 404。

## 测试

- `KnowledgeDocumentRepositoryTest`：仿 `SessionRepositoryTest` 模式（R2DBC + H2
  内存库 + Flyway 迁移跑一遍新 migration），覆盖插入/查询/条件更新（含"并发场景下
  影响 0 行"）。
- `KnowledgeDocumentServiceTest`：mock repository + mock `LlmClient`，测状态机
  转换（approve/reject 对 PENDING/非 PENDING 文档的行为）、summary 覆盖逻辑、
  摘要生成失败时的降级路径。
- `KnowledgeControllerTest`：`@WebFluxTest` + `WebTestClient`（仿
  `ChatControllerTest`/`UploadControllerTest` 模式），覆盖 multipart 上传、
  list 的 status 过滤、approve/reject 的 404/409 分支。
- 不做 LLM 摘要生成的真实端到端调用测试——mock `LlmClient`，验证摘要文本被正确
  存库/覆盖即可（与现有 `AgentServiceTest` mock `LlmClient` 的方式一致）。
