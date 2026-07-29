# 后端内置 LLM Agent 设计（替代 OpenClaw 运行时）

> 背景：以 OpenClaw 作为 LLM 载体、经 Portainer 每用户动态起容器的方案，被评审否决。
> 否决理由（明确勾选）：**任意代码执行**、**第三方软件不可控**、**容器供应链/隔离**。
> 注意：**数据出境/外部 LLM 不是顾虑**——推理引擎继续用外部 API 可接受。
> 因此问题的本质不是"换一个类似 OpenClaw 的运行时"，而是**换架构**：把 agent 循环收回到自己可控的后端代码，只暴露安全工具。
> 本设计正是原设计文档（`docs/2026-06-12-enterprise-api-agent-design.md` 末尾"架构演进建议"）写下的长期路线：
> "将成熟的 Skill 逻辑迁移回 Spring Boot，但保留外部 LLM API 作为推理引擎。"

## 目标

保留 OpenClaw 方案让团队选它的全部理由——**LLM 直接读精简 API 文档、自己选接口、不用 RAG/嵌入、汇总呈现好**——同时彻底消除被否决的三个安全风险，并支持失败重试（"自循环"）。

## 非目标（本次范围外）

- 企业统一登录系统本身（后续接入；本设计只预留 seam）。
- 真正的每租户运行时/资源物理隔离（多租户在应用层用会话池解决）。
- LLM 回答的 token 级流式（沿用 OpenClaw 现状："思考 → 一次性完整答案"；真流式为后续增强）。

---

## 1. 总体架构

```
┌─────────┐   SSE    ┌──────────── Spring Boot 后端（唯一可控运行时） ─────────────┐
│ React   │◀────────▶│  ChatController                                             │
│ 前端     │          │      │                                                       │
└─────────┘          │      ▼                                                       │
                     │  AgentService  ── 有界 tool-use 循环（≤8 轮 / ≤180s） ──┐    │
                     │      │  ▲                                               │    │
                     │      ▼  │ tool_result / error 回喂                      │    │
                     │  LlmClient ──HTTP──▶ DeepSeek（OpenAI 兼容 function-calling）│
                     │      │                                                  │    │
                     │      ▼ 两个安全工具                                     │    │
                     │  ┌ getApiDetail(service,path)  ─ 只读，返回文档片段 ─────┤    │
                     │  └ callEnterpriseApi(...)      ─ 白名单校验后发 HTTP ────┼─▶ MES/CRM/... │
                     │      │                                                  │    │
                     │  ApiCatalog（读 src/main/resources/api-docs → 精简索引 + 白名单）│
                     │  ChatMemory（按 ownerId 隔离会话）                       │    │
                     └──────────────────────────────────────────────────────────────┘
```

核心：agent 循环是**自己写的、可审计的 Java 代码**；运行时**只暴露两个安全工具**（一个只读查文档，一个白名单 HTTP 调用），**没有 shell、没有文件系统、没有代码执行**。

---

## 2. 请求流程（agent 循环）

1. **组装消息**：系统提示（由现 `SKILL.md` 的角色/流程/认证/输出规则改写）+ **精简 API 索引**（每接口一行）+ 该 owner 的会话历史 + 本次提问。
2. **进入有界循环**（最多 8 轮工具调用 **且** 单请求总预算 180s）：
   - 调 `LlmClient`，带上两个工具定义。
   - LLM 发起 `getApiDetail` → `ApiCatalog` 返回该接口在 `api-docs` 中的完整片段（只读），作为 `tool_result` 回喂。
   - LLM 发起 `callEnterpriseApi` → 后端**先按白名单校验** `(service, method, path)` 合法 →
     `EnterpriseApiClient` 带 `x-access-token` 调企业接口（单次 15s 超时，瞬时故障退避重试 0.5/1/2s 共 3 次）→
     把结果（或穷尽重试后的错误）作为 `tool_result` 回喂。
   - LLM 返回纯文本（不再调工具）→ 视为最终答案，经 SSE 吐给前端，结束。
3. **边界收敛**：轮数或总时长任一到限 → 返回"已尽力，未能得到结果，请换个问法"。

> **为何两个工具而非把全部文档塞进提示**：`api-docs/` 是 RAG 时代的完整文档（如 MES 289 个接口、2000+ 行），全量入 prompt 成本过高。
> 因此系统提示只放**精简索引**（每接口一行：`服务 | 名称 | METHOD 路径 | 一句话用途`），LLM 需要细节时用只读的 `getApiDetail` 现取——
> 这正是 OpenClaw"直接读参考文档"的忠实还原，既省 token 又保留其取胜的行为。两个工具都安全：`getApiDetail` 只服务于打包进 jar 的 `api-docs`，不触达任意文件。

### SSE 事件

沿用原设计的事件类型，让前端显示进度：`thinking` / `tool_call` / `answer` / `error` / `done`。

---

## 3. 组件清单

### 新增

| 组件 | 职责 | 可测性 |
|---|---|---|
| `LlmClient`（接口 + `DeepSeekLlmClient` 实现） | 瘦封装 DeepSeek chat/completions（function-calling）；输入消息+工具定义，输出"工具调用"或"终答文本"。手写 `java.net.http.HttpClient` + Jackson，风格对齐现有 `PortainerClient` | 接口化，单测可 mock |
| `EnterpriseApiClient`（接口 + 实现） | 带 `x-access-token`、15s 超时、瞬时故障退避重试（0.5/1/2s ×3）的企业 API 调用；按 service→baseUrl 组装 URL | 接口化，单测可 mock |
| `ApiCatalog` | 加载 `src/main/resources/api-docs/`：① 生成精简索引（供提示）② 提供 `(service, method, path)` 白名单（供校验）③ `getApiDetail` 的文档片段来源 | 纯逻辑，单测直接覆盖 |
| `AgentService`（重写） | 有界 tool-use 循环 + 两层重试 + 边界收敛；纯逻辑抽成静态方法便于单测 | 循环用 mock 的 `LlmClient`/`EnterpriseApiClient` 驱动测试 |

### 改动

- `ChatController`：去掉"实例绑定 / 404 if unbound / bind 端点"，会话改为按 `ownerId` 隔离（现阶段 `ownerId = conversationId`，预留用户身份 seam）。SSE 保持。
- `application.yml`：删除 `portainer`、`app.instance-registry`；新增 `deepseek`（base-url、api-key 由环境变量注入、model）与 `enterprise-api`（各服务 base-url、`x-access-token` 环境变量注入）。
- Dockerfile：基础镜像已是纯 JDK，无需改动；镜像标签不再依赖 `openclaw-base`。

### 退役（实现的正是被否决的方案）

- Java：`InstanceService`、`InstanceRegistry`、`PortainerClient`、`InstanceController`、原 shell-out 版 `AgentService` 及相关测试。
- 前端：`InstancePicker.tsx`、`api/instances.ts` 及新建会话前的"选实例"门槛；`App.tsx` 回退到直接开始新会话。
- 文档：`multi-instance-routing` 计划标记为归档。

**处置方式（评审决定）**：退役代码**不删除历史**——先建归档分支（如 `archive/openclaw-multi-instance`）指向当前提交予以保留，再在特性分支上移除这些文件。可随时从归档分支找回。

---

## 4. 安全（逐条回应否决理由）

| 顾虑 | 本方案如何消除 |
|---|---|
| 任意代码执行 | 运行时仅暴露两个工具：一个**只读**查文档、一个**白名单** HTTP 调用。无 shell、无文件系统、无代码执行——该类攻击面不存在。 |
| 第三方软件不可控 | 删除 OpenClaw 运行时；agent 循环是团队自己的 Java 代码，可审计、可 CI、可版本控制。外部依赖仅剩 DeepSeek 的 HTTP API（数据出境已确认非顾虑）。 |
| 容器供应链/隔离 | 不再动态起容器、不再 Portainer、不再暴露 gateway 端口。就是现有那个已上线的 Spring Boot 服务，多了几个类。 |

**白名单是安全边界**：`callEnterpriseApi` 只接受 `ApiCatalog` 中登记的 `(service, method, path)`；LLM 无法凭幻觉构造出打向任意主机/路径的请求。`x-access-token` 由后端持有并注入，绝不下发前端、绝不进日志（沿用现有不打敏感值的规则）。

---

## 5. 重试与边界（"耐心/复杂查询"组）

- **机械重试（工具层）**：企业 API 瞬时故障（超时 / 5xx / 网络）→ 自动退避重试 3 次（0.5 / 1 / 2s），单次调用超时 15s。LLM 无感知。
- **语义自循环（LLM 层，即 /loop 的部分）**：选错接口 / 路径不存在 / 空结果 → 把错误信息回喂，LLM 自纠正后再试。
- **停止边界**：最多 **8 轮**工具调用 **且** 单请求总预算 **180s**，任一到限即收敛并友好告知，杜绝无限重试与成本失控。

---

## 6. 会话与多用户（为"之后的企业登录"预留 seam）

- **现阶段**：会话历史按 `ownerId` 存后端，`AgentService` 经 `ChatMemory` 读写；`ownerId = conversationId`。
- **Seam**：`ChatMemory` 以 `ownerId` 为主键。将来接入企业登录后，`ownerId = 认证用户 id`，即实现"每用户独立会话池"，无需改动 agent 核心。
- **登录系统本身不在本次范围。**

---

## 7. 测试策略

- `LlmClient` / `EnterpriseApiClient` 均为接口 → 单测 mock，不发真实网络请求。
- **循环测试**：假 `LlmClient` 按脚本先返回工具调用、再返回终答；假 `EnterpriseApiClient`"前 N 次失败后成功"——断言：
  - 机械重试生效（第 3 次成功）；
  - 语义自纠正生效（回喂错误后换正确接口）；
  - 8 轮 / 180s 边界能正确收敛并产出友好提示。
- `ApiCatalog`：精简索引生成正确；非法 `(service, path)` 被白名单拒绝；`getApiDetail` 只返回登记文档。
- `ChatController`：WebMvcTest 测 SSE 契约（OpenAI 兼容 chunk + `[DONE]`）。

---

## 8. 技术选型确认

| 项 | 选择 |
|---|---|
| 推理模型 | **DeepSeek**（已付费、已在流水线）；`LlmClient` 接口化，将来换 Claude 只需换实现 |
| LLM 客户端 | 手写瘦客户端（`java.net.http.HttpClient` + Jackson），对齐现有 `PortainerClient` 风格，不引 Spring AI（可完全掌控循环/重试/边界，且避开 Spring AI 1.0 已知问题） |
| 前端 | 保持 React + SSE 渲染，移除选实例门槛 |
| 退役代码 | 归档到 `archive/openclaw-multi-instance` 分支后，从特性分支移除 |
