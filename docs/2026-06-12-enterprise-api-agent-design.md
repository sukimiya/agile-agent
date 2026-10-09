# Enterprise API Agent — 设计文档

## 项目目标

构建一个企业内部 API 智能助手，通过自然语言查询 MES、CRM 等企业服务的接口数据，并归纳总结返回结果。

用户在前端输入自然语言问题 → Agent 自动检索相关 API → 调用接口 → 归纳数据 → 返回自然人话回答。

---

## 整体架构

```
┌──────────────┐     ┌──────────────────────────────────────┐     ┌─────────────┐
│  React 前端   │────▶│         Spring Boot 后端              │────▶│   Ollama    │
│  (Kimi 风格)  │◀────│                                      │◀────│ deepseek-r1 │
└──────────────┘     │  ┌─────────┐  ┌─────────┐  ┌───────┐ │     │ nomic-embed │
                     │  │  RAG    │  │  Tool   │  │ Chat  │ │     └─────────────┘
                     │  │ 检索    │─▶│ Calling │─▶│ Memory│ │
                     │  └─────────┘  └─────────┘  └───────┘ │     ┌─────────────┐
                     │       │            │                  │────▶│  PGVector   │
                     │       ▼            ▼                  │◀────│ PostgreSQL  │
                     └───────┼────────────┼──────────────────┘     └─────────────┘
                             │            │
                             ▼            ▼
                     ┌──────────────────────────┐
                     │  企业服务 (测试环境)        │
                     │  MES-Service :8888        │
                     │  CRM-Service :xxxx        │
                     │  x-access-token 认证       │
                     └──────────────────────────┘
```

---

## 核心流程

```
用户输入 "最近一周有哪些未完成的工单？"
         │
    ┌────▼────┐
    │ 1. RAG  │  问题向量化 → 从 PGVector 检索最相关的 3 条 API 文档
    │   检索   │  命中: "分页查询生产工单" / "工单状态统计" / "生产工单排程"
    └────┬────┘
         │  Top-3 接口定义
    ┌────▼────┐
    │ 2. Tool │  AI 收到 3 个工具（而非全部 40+ 个）
    │ Calling │  AI 判断: 选 "分页查询生产工单" — status=未完成 + 时间范围
    └────┬────┘
         │  API 名称 + 参数
    ┌────▼────┐
    │ 3. 调用 │  HTTP 请求 → MES Service (x-access-token)
    │   API   │  返回 JSON: [{工单号: WO-001, 状态: 未完成, ...}, ...]
    └────┬────┘
         │  原始 JSON 数据
    ┌────▼────┐
    │ 4. 归纳 │  AI 将 JSON 数据 + 用户问题 → 自然语言总结
    │   总结   │  "最近一周共有 5 个未完成工单: WO-001(生产中, 进度60%)..."
    └────┬────┘
         │  自然语言回答
    ┌────▼────┐
    │ 5. 渲染 │  React 前端渲染回答（支持 Markdown / 表格）
    │   前端   │
    └─────────┘
```

### 为什么用 RAG 先过滤而非全量 Tool Calling？

- MES 有 40+ 接口，加上 CRM 会更多
- 全量 Tool Calling 每次请求携带所有工具定义，token 浪费严重（~8000+ tokens）
- 工具多时 deepseek-r1:8b 容易选错（之前实测出现过幻觉）
- RAG 过滤后只剩下 3 个精准工具，token 省、准确率高

---

## React 前端设计

### 页面布局

```
┌─────────────────────────────────────────┐
│  🔧 Agile Agent           [设置] [清空] │  顶栏
├─────────────────────────────────────────┤
│                                         │
│  ┌─────────────────────────────────┐    │
│  │ 👤 最近一周有哪些未完成的工单？  │    │  用户消息（右对齐气泡）
│  └─────────────────────────────────┘    │
│                                         │
│  ┌──────────────────────┐               │
│  │ 🤖 最近一周共有 5 个  │               │  助手消息（左对齐气泡）
│  │    未完成工单：       │               │  Markdown 渲染
│  │    | 工单号 | 状态 |  │               │
│  │    |--------|------|  │               │
│  │    | WO-001 | 生产中|  │               │
│  │    ...                │               │
│  └──────────────────────┘               │
│                                         │
│  ┌─────────────────────────────────────┐│
│  │ 输入您的问题...               [发送]││  输入框
│  └─────────────────────────────────────┘│
└─────────────────────────────────────────┘
```

### 核心交互

- 支持流式输出（SSE），逐字显示回答
- 支持 Markdown 渲染（表格、列表、代码块）
- 对话历史保存在后端（Chat Memory），刷新页面后保留
- 左侧可展开"可用服务列表"（MES / CRM 等）

### 技术选型

- React 18 + TypeScript
- 样式方案：CSS Modules 或 Tailwind（团队自行决定）
- HTTP 客户端：fetch（SSE） + 普通 JSON 请求
- 状态管理：useState + useEffect（聊天状态简单，不需要 Redux）

---

## Spring Boot 后端设计

### 项目结构

```
mcp-tool/
├── pom.xml
├── agent-server/src/main/java/io/github/sukimiya/agileagent/
│   ├── AgentApplication.java          # Spring Boot 入口
│   ├── config/
│   │   └── AgentConfig.java           # ChatClient / Memory / VectorStore 配置
│   ├── controller/
│   │   └── ChatController.java        # REST API 端点
│   ├── rag/
│   │   └── ApiDocVectorLoader.java    # API 文档加载 → PGVector
│   │   └── ApiDocService.java         # API 文档检索
│   ├── tool/
│   │   └── ApiCallingTool.java        # 动态 API 调用工具
│   ├── model/
│   │   ├── ApiEndpoint.java           # API 端点元数据模型
│   │   ├── ChatMessage.java           # 对话消息
│   │   └── ChatHistory.java           # 对话历史
│   └── service/
│       └── AgentService.java          # 核心 Agent 逻辑
├── src/main/resources/
│   ├── application.properties
│   └── api-docs/                      # 用户编写的 API 文档（Markdown）
│       ├── mes/
│       │   ├── producing-work-orders.md
│       │   ├── work-center.md
│       │   └── ...
│       └── crm/
│           └── ...
└── docs/
    └── api-doc-template.md            # API 文档编写模板
```

### REST API 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/chat` | POST | 发送消息，返回流式 SSE 响应 |
| `/api/conversations` | GET | 获取历史对话列表 |
| `/api/conversations/{id}` | GET | 获取指定对话详情 |
| `/api/conversations/{id}` | DELETE | 删除对话 |
| `/api/services` | GET | 获取可用服务列表 |

### Chat 请求/响应

请求：
```json
{
  "conversationId": "uuid-optional",
  "message": "最近一周有哪些未完成的工单？"
}
```

响应（SSE 流式）：
```
data: {"type":"thinking","content":"正在检索相关接口..."}
data: {"type":"tool_call","content":"调用 MES:分页查询生产工单"}
data: {"type":"answer","content":"最近一周共有 5 个未完成工单..."}
data: {"type":"done"}
```

### Spring AI 配置

复用 adoptions 项目的配置模式：
- Ollama Chat: deepseek-r1:8b（后续可切 llama3.2）
- Ollama Embedding: nomic-embed-text
- PGVector: dimensions=768
- Chat Memory: JDBC PostgreSQL（conversationId 隔离用户会话）

### Tool Calling 设计

不同于 adoptions 项目的固定 `@Tool` 注解方法，本项目需要**动态生成工具**。

方案：不使用 `@Tool` 注解，改用 `ToolCallback` 动态注册：

```java
// 每次请求，根据 RAG 结果动态注册
ToolCallback tool = new DefaultToolCallback(
    "queryWorkOrders",           // 工具名（从 API 文档解析）
    "分页查询生产工单",            // 描述
    params -> apiGateway.call(apiEndpoint, params),  // 实际调用
    Map.of("status", "string", "pageNo", "int")     // 参数 schema
);
```

`ApiCallingTool` 负责持有 x-access-token，统一加认证头转发请求到 MES/CRM。

---

## API 文档规范

### 用户需要编写的文档

每个 API 接口一个 Markdown 文件，存放在 `src/main/resources/api-docs/{service}/` 下。

### 模板（`docs/api-doc-template.md`）

```markdown
# 服务：MES

## 接口：分页查询生产工单

- **路径：** POST /api/mes/producing-work-orders/page
- **所属模块：** 生产工单
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询生产工单列表，支持按工单号、状态、计划日期等筛选。

**请求参数：**
- query: 查询条件对象
  - orderNo: 工单号（可选）
  - status: 工单状态（可选，草稿/已下达/生产中/已完成）
  - startDate: 计划开始日期（可选）
  - endDate: 计划结束日期（可选）
  - pageNo: 页码
  - pageSize: 每页条数

**返回字段：**
- id: 工单ID
- orderNo: 工单号
- productName: 产品名称
- status: 工单状态
- plannedQty: 计划数量
- completedQty: 已完成数量
- startDate: 计划开始日期

**适用问题示例：**
- "有哪些工单还没完成？"
- "最近一周创建了哪些工单？"
- "某个产品的工单进度怎么样？"
- "查一下生产工单的状态"
- "还有多少工单在生产中？"
```

### RAG 索引策略

将每个 Markdown 文档拼接为一段文本后向量化：

```
文本 = "{服务名} {HTTP方法} {路径} {接口名} {功能说明} {参数列表} {返回字段} {适用问题}"
```

存入 PGVector 的 `vector_store` 表时，metadata 字段保存：
```json
{
  "service": "MES",
  "path": "/api/mes/producing-work-orders/page",
  "method": "POST",
  "name": "分页查询生产工单",
  "module": "生产工单"
}
```

检索时用 metadata 过滤，返回 topK=3，精确率和召回率均衡。

---

## 数据模型（PostgreSQL）

### 表结构

```sql
-- Chat Memory（Spring AI 自动管理）
-- 由 spring.ai.chat.memory.repository.jdbc.initialize-schema=always 自动创建

-- Vector Store（Spring AI 自动管理）
-- 由 spring.ai.vectorstore.pgvector.initialize-schema=true 自动创建
-- 额外字段: id, content, metadata (JSON), embedding (vector(768))
```

无需额外建表，Spring AI 自动管理。

---

## 生产化考虑

### 安全
- x-access-token 通过环境变量注入，不硬编码
- 前端不暴露企业服务的认证信息
- 后端校验 conversationId 归属

### 性能
- Ollama 依赖本地 GPU，响应时间取决于模型推理速度
- 企业 API 需加超时控制（10s），避免前端等待过久
- PGVector 索引: 40+ 个文档，IVFFlat 索引即可
- 考虑缓存热点问题的 RAG 检索结果（Redis / Caffeine）

### 可选扩展
- 权限：不同用户看到不同服务的 API
- MCP 暴露：每个企业服务封装为独立 MCP Server
- WebSocket 替代 SSE：双向通信更灵活
- 多模型支持：简单问题用小模型，复杂问题用 deepseek-r1

---

## 技术栈总览

| 层 | 技术 |
|------|------|
| 前端 | React 18 + TypeScript |
| 后端 | Spring Boot 3.5 + Spring AI 1.0 |
| AI 模型 | Ollama: deepseek-r1:8b (chat) + nomic-embed-text (embedding) |
| 向量库 | PostgreSQL + PGVector |
| 对话记忆 | JDBC Chat Memory (PostgreSQL) |
| 构建 | Maven + JDK 21 |

---

## 实践结论（2026-06-15 更新）

经过完整的 Spring Boot + RAG 方案实现和 OpenClaw + Skill 方案对比测试，最终推荐 **OpenClaw + Skill** 方案。

### 两种方案对比

#### 方案 A：Spring Boot + RAG（已实现）

Spring Boot 3.5 + Spring AI 1.0 + DeepSeek API (chat) + Ollama bge-m3 (embedding) + PGVector。

**遇到的核心问题：**

1. **嵌入模型中文匹配差** — nomic-embed-text 英文模型完全无法匹配中文查询。换成 bge-m3 后有改善但仍不稳定，"有多少个客户"匹配到 WMS 成品出入库单。

2. **PGVectorStore.similaritySearch 返回 0 结果** — Spring AI 1.0 的 PGVectorStore 存在 bug，`SearchRequest` 的相似度查询持续返回空列表，即使原始 SQL 在 psql 中正常执行。

3. **示例问题是嵌入匹配的唯一有效信号** — 嵌入模型只能通过"适用问题示例"中的自然语言问句匹配，功能说明和返回字段的正式描述对语义匹配几乎无效。

4. **本地模型效果差** — deepseek-r1:8b 在 API 调用场景下编造不存在路径（如 `/api/customer/count`），改用 DeepSeek API 后改善但仍需路径校验重试。

**代码量：** 7 个 Java 类 + 713 个向量化 API 文档，嵌入耗时约 40 秒/次。

#### 方案 B：OpenClaw + Skill（最终采用）

OpenClaw 作为 agent 运行时，通过 SKILL.md + API 参考 Markdown 实现企业 API 查询。

**实现方式：**

```
~/.openclaw/workspace/.openclaw/skills/enterprise-api/
├── SKILL.md      # 主 skill：流程、认证、调用规则
├── crm-api.md    # CRM 81 个接口（精简参考）
├── mes-api.md    # MES 289 个接口
├── wms-api.md    # WMS 108 个接口
├── qms-api.md    # QMS 121 个接口
└── srm-api.md    # SRM 114 个接口
```

**核心优势：**

- **无需 RAG** — Claude 直接从 API 参考文档查找接口，不依赖嵌入向量匹配
- **无需嵌入模型** — 省去 bge-m3、PGVector 的维度和 JDBC 兼容问题
- **接口选择准确** — Claude 不编造路径，或在 SKILL.md 规则约束下自行纠正
- **数据呈现优秀** — 自动生成表格、分组、汇总，附带业务洞察（如标记过期工单）
- **代码量极少** — 1 个 SKILL.md + 5 个 API 参考文件

**实测结果：**

| 查询 | 结果 |
|------|------|
| "帮我看看当前有多少个客户？" | 48 个客户，按状态和等级分类 |
| "有哪些未完成的工单？" | 21 个未完工单，按状态分组，标记过期工单 |

**已知限制：**

- OpenClaw 作为个人/团队工具，不适合直接作为商业软件产品交付
- 如果需要 Web 前端，可在 OpenClaw 之上包装一层 HTTP API
- Memory 的向量搜索功能需要 OpenAI key（不影响核心查询功能）

### 架构演进建议

**短期（MVP）：** OpenClaw + Skill，通过 CLI 或 IM 渠道使用。

**长期（商业化）：** 在 OpenClaw 验证完交互流程和 API 覆盖后，可考虑：
1. 用 OpenClaw 的 Gateway API 包装 Web 前端
2. 或将成熟的 Skill 逻辑迁移回 Spring Boot，但保留 Claude API 作为推理引擎（不依赖本地小模型和 PGVector）
