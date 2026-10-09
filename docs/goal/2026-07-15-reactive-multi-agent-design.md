# 响应式多 Agent 协作改造 — 设计文档

日期：2026-07-15

## 背景与动机

当前系统是单 Agent 循环：`AgentService` 内跑一个最多 8 轮的 tool-calling 循环，
system prompt 携带 `ApiCatalog.compactIndex()` 全量索引（MES/CRM/WMS/QMS/SRM 共 715 个接口，
约 5.4 万字符 ≈ 3.5 万 tokens），每次请求全量发送。MDM/EAM/EMS 接入后索引还会继续增长。

改造要解决的两个问题（按优先级）：

1. **Token 成本**：每次请求携带全量索引，成本高且随服务数线性恶化。
2. **学习目标**：以本项目为载体，完整实践一个高并发响应式 + 分布式多 Agent 协作的架构案例。
   在业务规模与架构复杂度的取舍上，**有意选择偏工程化的方案**（全链路响应式、Redis Streams
   任务总线），这是明确的学习性决策，不是业务必需。

## 总体架构

```
用户 (SSE)
   │
   ▼
Orchestrator（路由 + 聚合）
   │  服务级目录（~8 行）做一次轻量 LLM 路由 → 1~N 个域任务
   │
   ▼  XADD
Redis Streams: agent:tasks  ──── consumer group（全体实例同构抢活）
   │
   ▼
域 Agent（MES / CRM / WMS / QMS / SRM / MDM / EAM / EMS）
   │  每个域 agent = system prompt + 本域索引分片 + 工具集（配置级差异，非新类层级）
   │
   ├─ 进度事件 ──▶ Redis pub/sub: agent:events:{sessionId} ──▶ 持有 SSE 连接的实例转发给用户
   └─ 最终结果 ──▶ Orchestrator 聚合 ──▶ 总结回答
```

部署形态：**同构实例**。所有实例加载全部角色，加入同一 consumer group，谁闲谁消费。
不按角色拆部署单元（避免容量碎片和多套部署配置）。

Agent 职能划分原则：**按业务域拆分，不按功能环节拆分**。
按功能环节（规划→检索→执行→总结）不能分片索引，无法解决 token 问题，故不采用。

预期 token 收益：单域问题（绝大多数）只携带本域索引——最大的 MES 约 1.4 万 tokens，
CRM 仅约 4 千，对比现在全量 3.5 万，降低 60%~90%。

## 阶段一：全链路响应式化

独立可交付：完成后系统是"流式输出的响应式单 Agent"，不依赖阶段二。

### 1. Web 层：spring-boot-starter-web → starter-webflux（Tomcat → Netty）

- `ChatController` SSE 出口已是 `Flux<String>`，基本不动。
- 迁移适配件：`CurrentUserArgumentResolver`（MVC 与 WebFlux 的 resolver 接口不同）、
  `AuthExceptionHandler`、`WebConfig`。
- `UploadController` multipart 改用 `FilePart`（非阻塞文件写入）。
- 认证保留 `@CurrentUser` 注入模式，`CurrentUserArgumentResolver` 迁移为 WebFlux 版
  `HandlerMethodArgumentResolver`（`resolveArgument` 返回 `Mono<Object>`，未认证走 `Mono.error`）。

### 2. LLM 客户端：RestClient → WebClient + 流式

- `LlmClient` 接口改为响应式：返回 `Flux<LlmEvent>`（事件 = token 增量 / tool_call / 完成）。
- 开启 DeepSeek `stream: true`，SSE 逐 token 解析。用户可感知收益：逐字输出，不再干等整段回答。
- 技术难点：流式下 tool_call 的增量拼装（arguments 分片到达）。

### 3. 存储层

- `ChatMemory` / `AuthSessionStore`：`StringRedisTemplate` → `ReactiveStringRedisTemplate`。
- `SessionRepository`：MySQL JDBC → **R2DBC**（`r2dbc-mysql` 驱动）。
  Flyway 保留 JDBC，仅在启动时跑迁移（启动阻塞可接受）。
  （备选：JDBC 包在 `boundedElastic` 调度器中。选 R2DBC 是学习性决策，若实施中生态问题过多可回退备选。）

### 4. Agent 循环

- `runLoop` 的 while 循环 → Reactor `expand` / 递归 Mono。
- `MAX_ROUNDS` 用 `.take()`，总超时用 `.timeout(Duration.ofMinutes(3))`。
- **BlockHound** 集成测试守门：响应式链路中出现任何阻塞调用，测试失败。

## 知识库文档上传审核（阶段二前置子项目，已完成）

从"主 agent 重构为纯调度者 + 域 agent + Redis Streams"这个大方向里拆分出的**第一个独立子项目**，
完整设计见 `docs/superpowers/specs/2026-07-27-knowledge-base-upload-review-design.md`，
实施计划见 `docs/superpowers/plans/2026-07-27-knowledge-base-upload-review-plan.md`。

上传 → 临时目录 → 审核 → approve/reject → 落库 `./lib` 的完整流水线，业务/流程类文本文档
（区别于第 5 节要拆分的 MES/CRM/WMS/QMS/SRM 这类 API 接口文档）。approve 时用 `LlmClient` 生成
的摘要，成为域 agent 路由用的索引条目。**接入 Orchestrator 路由（第 5 节）已实施**——
`./lib` 目录里的内容（每条 `{id}.md` 原文 + `{id}.meta.json` 元数据）现由 `KnowledgeCatalog`
扫描为摘要索引，`KNOWLEDGE` 域 agent 参与路由与问答。

新增包 `io.github.sukimiya.agileagent.knowledge`（`KnowledgeController`/`KnowledgeDocumentService`/
`KnowledgeDocumentRepository`/`KnowledgeFileStorage`），5 个端点，均走现有 `@CurrentUser`
认证（任何登录用户，无角色区分——审核门槛刻意设低，与"新系统 API 接口文档审核"未来需要的
SmartLink admin 门槛是两套体系）：

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/api/knowledge/upload` | multipart 上传文本/Markdown 文档，服务端生成 LLM 摘要，落 `PENDING` |
| `GET` | `/api/knowledge?status=` | 列表，默认 `PENDING`，可查 `APPROVED`/`REJECTED` 历史 |
| `GET` | `/api/knowledge/{id}/download` | 下载原文（审核中读临时目录，已批准读 `./lib`） |
| `POST` | `/api/knowledge/{id}/approve` | 落库 `./lib`（先写文件再改状态，避免写文件失败时文档卡死在"已批准但无文件"）；可选 `summary` 覆盖自动摘要 |
| `POST` | `/api/knowledge/{id}/reject` | 标记拒绝，原文件留痕不删 |

非目标（与本文档"非目标"一节一致的取舍）：不支持 PDF/Word、不做 RAG/向量检索、不做审核权限
分级、不做过期自动清理。

## 阶段二：多 Agent + Redis Streams

### 5. Agent 职能划分

**已实施（进程内扇出版本）**，设计与实现见
`docs/superpowers/specs/2026-07-28-orchestrator-domain-agent-design.md`。

- `ApiCatalog` 按服务分片，提供 `compactIndex(service)`。
- 域 agent（`DomainAgent`）= 一份配置（`AgentRole`：system prompt + 本域索引分片 + 工具集），
  由 `AgentRoleRegistry` 装配 6 个角色：MES/CRM/WMS/QMS/SRM + KNOWLEDGE。
- `Orchestrator`：携带服务级目录（~7 行 `routingBlurb`）做一次轻量 LLM 路由，产出 1~N 个域任务，
  经 `TaskDispatcher` 扇出（当前实现 `InProcessTaskDispatcher` 用 `Flux.merge` 进程内并行，
  单域 90s 超时降级，接口已预留给下一节的 Redis Streams 实现替换）。
- 知识库条目（上一节，`./lib` 目录）已并入"域"的概念——approve 时生成的摘要即索引条目
  （`KnowledgeCatalog`），和 MES/CRM 等系统级域一样参与 Orchestrator 路由，"域"的内容来自
  运行时上传审核而非静态编译的 `api-docs/`。approve 成功后立即 `reload()`，无需重启。

**尚未实施**：第 6-9 节（Redis Streams 任务总线、跨实例 pub/sub 进度回传、扇出限流、
投机扇出与取消）——这些依赖 `TaskDispatcher` 接口的 Redis 实现，是后续独立子项目。

### 6. 任务总线：Redis Streams

- Orchestrator `XADD` 任务到 `agent:tasks`；全体实例加入同一 consumer group 消费。
- 实践点：`XAUTOCLAIM` 接管宕机实例的 pending 任务、消费幂等、死信队列。

### 7. 进度回传：Redis pub/sub

- 域 agent 的 token / tool_call 事件发布到 `agent:events:{sessionId}`，
  持有该用户 SSE 连接的实例订阅并转发。
- 背压策略：中间进度事件可丢（`onBackpressureDrop`），最终结果不可丢。

### 8. 扇出聚合与限流

- 跨服务问题：`Flux.merge` 并行扇出多个域任务，每个独立 timeout + `onErrorResume`。
- 聚合容忍部分失败：如"CRM 结果已返回，MES 超时"也要给用户答案。
- Resilience4j reactive `RateLimiter` / `Bulkhead` 保护 DeepSeek 并发配额
  （多 agent 扇出特有的问题：并行 LLM 调用可能打爆供应商配额）。

### 9. 投机扇出与提前终止（cancellation）

适用场景：路由不确定时（如"客户欠款的单子"可能在 CRM 或 SRM），同时派发多个候选域任务，
第一个有效结果胜出，其余任务提前终止（hedged request 模式）。
注意：常规跨服务任务是互补关系（各查各的域），不适用提前终止，仅投机扇出适用。

- **进程内**：依赖 Reactor 取消传播——`Flux.merge(...).filter(有效).next()`，
  胜出后其余订阅自动 cancel，WebClient 在途 LLM 请求随之中断（流式下服务端停止生成，token 止损）。
  需手动触发时用 `Sinks.one()` + `takeUntilOther`。
- **跨进程**：Sinks 不出 JVM，取消信号走 Redis pub/sub `agent:cancel:{taskGroupId}`；
  worker 执行任务期间订阅该频道，收到信号后触发本地 Sink 终止管道。
- **队列中未被领取的任务**：pub/sub 通知不到未来的消费者，需在 Redis 写任务组状态键
  （`agent:group:{id}:done`），worker 领任务前先查，已完成则直接 ACK 跳过。

## 错误处理原则

- 域 agent 失败/超时 → 聚合器收到降级标记，最终回答注明"XX 服务查询超时"。
- SSE 断连 → 取消订阅；任务继续执行并落库，用户刷新后可见结果。
- Stream 消息消费失败 → 重投 2 次后进死信，进度频道发 error 事件。

## 测试与验证

- 单测：流式 tool_call 增量拼装、路由决策、聚合降级路径。
- BlockHound 集成测试：守全链路非阻塞。
- **Gatling 压测**（LLM 用 WireMock 打桩慢响应）：
  对比改造前后同等内存配置下的最大并发 SSE 会话数，产出有数字的结论。

## 非目标

- 不做按角色拆分的独立部署（微服务化/A2A 协议）。
- 不引入 RAG / 向量检索（此前已验证中文嵌入匹配不可靠，见 2026-06-12 设计文档）。
- 不做前端改造（SSE 协议格式保持兼容，新增 token 增量事件类型除外）。

## 实施顺序

1. 阶段一（全链路响应式化）→ 验证：BlockHound 通过、SSE 逐字输出、现有功能回归。
2. 阶段二（Redis Streams 多 Agent）→ 验证：多实例下任务分发/进度回传正确、跨服务扇出聚合正确。
3. Gatling 压测贯穿两阶段结束后进行，形成对比数据。
