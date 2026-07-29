# Agent 角色化 + Orchestrator 路由 + 结构化日志 — 设计文档

日期：2026-07-28

## 背景与动机

用户上传并审核通过了一份知识库文档（`lib/0302375a-….md`，【智搭】低代码 Stage API 使用说明），
但提问时 agent 完全不理它。排查结论：不是没触发，是功能没实现——

- 旧 `AgentService` 只注册 `getApiDetail` / `callEnterpriseApi` 两个工具，system prompt 只携带
  `ApiCatalog.compactIndex()`（MES/CRM/WMS/QMS/SRM 全量 715 个接口索引，不分片）
- `ApiCatalog` 只扫 `classpath:api-docs/{service}/*.md`，从不读 `./lib`
- 系统里没有子 agent——`docs/goal/2026-07-15-reactive-multi-agent-design.md` 里的 Orchestrator /
  域 agent / Redis Streams 是阶段二设计，当时未实施

同时缺少可用的服务器端调用日志：`getApiDetail`/`callEnterpriseApi` 有零散 `log.info`，但没有
traceId 串联、没有路由决策记录、没有 logback 配置。

本子项目是阶段二（`docs/goal/2026-07-15-reactive-multi-agent-design.md` 第 5-9 节）的**第一段**：
角色化 + 路由 + 进程内扇出 + 日志。Redis Streams 传输、trace 查询端点、限流/投机扇出是后续独立子项目。

## 架构

```
ChatController ──▶ Orchestrator.chat(ownerId, token, message)
                     │
              ① 路由：域目录(~7行 routingBlurb) + history → 一次 LLM（无工具，输出 JSON 数组）
                     │   ["KNOWLEDGE"] / ["MES","CRM"] / []
                     │
                 []  └──▶ 直接回答（精简 prompt，无索引）──▶ SSE
                     │
                     ▼
              TaskDispatcher.dispatch(DomainTask) ── InProcessTaskDispatcher
                     │   Flux.merge 并行，每个 .timeout(90s).onErrorResume(→ Failed)
                     ▼
              DomainAgent.run(task)   ← 按 AgentRole 取 system prompt / 索引 / 工具集
                     │   tool-calling 循环，MAX_ROUNDS=8
                     ▼   Flux<AgentEvent>
              ② 聚合：各域 Finished/Failed 文本 → 一次 LLM 总结 → SSE 逐字流出
```

## 核心组件

- **`AgentRole`**（record）：`code / displayName / routingBlurb / index(Supplier<String>) /
  tools / promptTemplate`。6 个实例：MES/CRM/WMS/QMS/SRM（工具 = `getApiDetail` +
  `callEnterpriseApi`，索引 = `ApiCatalog.compactIndex(code)` 分片）、KNOWLEDGE（工具 =
  `readKnowledgeDoc`，索引 = `KnowledgeCatalog.compactIndex()`）。由 `AgentRoleRegistry` 装配。
- **`KnowledgeCatalog`**（新，对标 `ApiCatalog`）：扫 `./lib/*.meta.json` 生成
  `{id} | {title} | {summary}` 摘要索引；`content(id)` 读全文（`boundedElastic` 调度，避免阻塞
  响应式链路）；`reload()` 由 `KnowledgeDocumentService.approve()` 成功后调用，新审文档立即可查，
  不需重启——这是修复用户报告问题的关键一环。
- **`DomainAgent`**：原 `AgentService.round()` 的 tool-calling 循环搬迁并泛化为按 `AgentRole`
  驱动；一次性上下文（不带 `ChatMemory` 历史，历史只在 Orchestrator 的路由/聚合阶段使用）；
  产出 `AgentEvent`（`ToolCalled` / `Delta` / `Finished` / `Failed`）。
- **`TaskDispatcher`**（接口）+ **`InProcessTaskDispatcher`**：`Flux.merge` 并行扇出，单个域
  90s 超时降级为 `Failed`，不拖垮其余域。接口预留给未来 Redis Streams 实现替换，
  `Orchestrator`/`DomainAgent` 不感知传输层。
- **`Orchestrator`**：取代 `AgentService.chat()`。路由 LLM 判断域（空数组走精简 prompt 直答，
  不扇出）→ 扇出各域（域内 `Delta` 不透传，仅 `tool_call` 控制行让用户看到进度，避免多域并发
  逐字交错乱码）→ 聚合成一次总结 LLM 调用 → SSE 输出。控制行格式（`thinking`/`delta`/`tool_call`/
  `error`/`done` + 最终纯文本行）与旧 `AgentService` 完全一致，前端无需改动。总预算 180s。

## 日志与链路追踪

- `logback-spring.xml`：console + `logs/agent.log`（按天/100MB 滚动，`maxHistory=7`），
  pattern 携带 `[%X{traceId}] [%X{role}]`。
- `io.micrometer:context-propagation` + `Hooks.enableAutomaticContextPropagation()`：把
  Reactor `Context` 里的 `traceId`（`Orchestrator.chat` 入口生成）、`role`（`DomainAgent.run`
  按任务角色设置）通过注册的 `ThreadLocalAccessor` 桥接到 SLF4J MDC，响应式链路里无论请求在
  哪个线程被处理（`parallel-*` / `boundedElastic-*`），`log.info(...)` 都能取到正确的 MDC 值。
- 埋点：路由决策（问题+选中角色+耗时）、dispatch、每轮工具调用、`readKnowledgeDoc` 命中情况、
  聚合（各域 OK/FAILED 状态+总耗时）。`grep "[traceId]" logs/agent.log` 串起一次完整会话。

## 非目标（留给后续子项目）

- Redis Streams / 跨实例任务分发与进度回传
- trace HTTP 查询端点、SSE 把 `tool_call` 之外的进度事件透传到前端界面
- Resilience4j 限流、投机扇出、跨进程取消（hedged request）
- 知识库文档切片、向量检索（沿用既有决策，见 `docs/2026-06-12-enterprise-api-agent-design.md`）

## 验证

- 单测：`KnowledgeCatalogTest` / `ApiCatalogTest` / `AgentRoleRegistryTest` /
  `InProcessTaskDispatcherTest` / `DomainAgentTest` / `OrchestratorTest` / `AgentLoggingTest`
  （用 Logback `ListAppender` 验证跨线程 MDC 传播）/ `ReactiveChainBlockHoundTest`（全链路无阻塞
  调用，含 `KnowledgeCatalog.content` 的真实文件 IO）。
- 端到端（`local` profile，真实 DeepSeek + 真实内网 MES 服务）：
  - 提问 Stage API 用法 → 路由到 `KNOWLEDGE` → `readKnowledgeDoc` 命中已审核文档 →
    回答内容准确引用文档中的代码示例
  - 提问 MES 5S 接口 → 路由到 `MES`，system prompt 只带 MES 分片索引（而非全量 715 接口）
  - 闲聊类问题 → 路由返回空数组，直接回答，不扇出
  - 新上传并 approve 一份文档，不重启进程，立即可被查到（验证 `reload()` 接线）
