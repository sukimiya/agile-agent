# Agile Agent 开源多模块迁移设计

- 日期：2026-10-09
- 状态：已确认，待实施计划
- 目标分支：`codex/open-source-multimodule-migration`

## 1. 背景

`agile-agent` 最初从内部项目 `mcp-tool` 导入，因此当前核心仍包含 Jowo 命名、
MES/CRM/WMS/QMS/SRM 领域角色、企业 API 客户端和业务 API 文档。与此同时，
`mcp-tool` 后续新增了通用 OpenAI-compatible LLM、模型预置、API Key 加密、提示语管理和
知识库范围控制等能力，其中一部分适合进入独立的开源 Agent 项目。

本次迁移不是继续同步两个仓库，而是明确建立开源边界：保留和重构通用能力，删除内部业务
耦合，并提供稳定的扩展接口，让具体业务系统以外部 adapter 的形式接入。

## 2. 目标

1. 将仓库重构为 Maven 多模块单仓库。
2. 清除 Jowo、l7-skill 和内部制造业务耦合。
3. 提供稳定、领域无关的 Agent capability 扩展接口。
4. 保留 OAuth2 Authorization Code + PKCE，并让 endpoint 和 userinfo claim 映射可配置。
5. 提供独立的开发用 mock OAuth2 服务，模拟用户、租户、组织和角色。
6. LLM 配置按租户共享，支持 OpenAI-compatible 模型预置、加密 API Key、usage 和可选余额查询。
7. 知识库支持全局、租户和组织三级范围，并在服务端强制授权。
8. 引入后端聊天提示语管理。
9. 保留现有数据库并通过 Flyway 增量迁移。
10. 修复当前 JDK 25 下 BlockHound 基线测试无法运行的问题。

## 3. 非目标

- 不迁移 `mcp-tool` 的 SmartLink 用户名密码登录或 SmartLink SSO。
- 不迁移企业服务 base URL 管理、MES/CRM/WMS/QMS/SRM/MDM/EAM/EMS 或任何业务 API 文档。
- 不实现运行时下载、上传或热加载任意第三方 JAR。
- 不在本仓库中提供具体企业系统 adapter。
- 不直接复制 `mcp-tool` 的编译后前端静态资源。
- 不在本次迁移中引入完整的策略引擎或通用 RBAC 管理后台。

## 4. 方案选择

### 4.1 采用方案：Maven 多模块单仓库

```text
agile-agent/
├── pom.xml
├── agent-spi/
├── agent-server/
├── mock-oauth2/
├── docs/
└── docker/
```

- `agent-spi`：无 Spring Boot 运行时依赖的公共契约、通用身份和工具模型。
- `agent-server`：OAuth2、会话、Agent 编排、LLM、知识库、提示语和 HTTP API。
- `mock-oauth2`：独立的本地开发与集成测试身份服务。

根 `pom.xml` 使用 `packaging=pom` 聚合三个模块，并统一 Java、Spring Boot、依赖和插件版本。

### 4.2 未采用方案

**单模块包级隔离**改动较少，但外部 adapter 必须依赖整个服务端，不能形成稳定的开源扩展面。

**动态插件目录和自定义类加载器**隔离更强，但会引入签名校验、依赖冲突、生命周期和安全问题，
超出当前需求。

## 5. 中性命名迁移

统一使用以下命名：

| 项目 | 新值 |
|---|---|
| Java 根包 | `io.github.sukimiya.agileagent` |
| Maven groupId | `io.github.sukimiya` |
| 服务端 artifactId | `agile-agent-server` |
| SPI artifactId | `agile-agent-spi` |
| Mock artifactId | `agile-agent-mock-oauth2` |
| Spring application name | `agile-agent-server` |
| Docker service/container | `agile-agent-server` |

源码、测试、配置、脚本、Docker、Jenkins 和文档中不得再出现 `com.jowo` 或 `l7-skill`。

## 6. Capability SPI

### 6.1 公共身份

`agent-spi` 提供不可变的 `IdentityContext`：

```java
public record IdentityContext(
        String subject,
        String displayName,
        String tenantId,
        String tenantName,
        String primaryOrgId,
        String primaryOrgName,
        Set<String> roles,
        Set<String> manageableOrgIds,
        Set<String> readableOrgIds,
        boolean platformAdmin,
        boolean tenantAdmin) {}
```

集合必须不可变且非 null。`tenantId`、主组织字段允许为 null，但访问租户资源时必须拒绝缺失身份。

### 6.2 Provider 契约

```java
public interface AgentCapabilityProvider {
    String code();
    String displayName();
    String routingDescription();
    Mono<CapabilityDefinition> definition(AgentExecutionContext context);
    Mono<ToolResult> execute(ToolInvocation invocation, AgentExecutionContext context);
}
```

公共模型包括：

- `CapabilityDefinition`：动态 system prompt 和工具定义。
- `ToolDefinition`：名称、说明和 JSON Schema 参数。
- `ToolInvocation`：工具名、调用 ID 和参数 JSON。
- `ToolResult`：成功或可展示的失败结果。
- `AgentExecutionContext`：身份、conversationId，以及受控的 OAuth2 access token 访问器。

access token 不属于 `IdentityContext`，不得写入日志、异常消息或工具结果。

### 6.3 注册与执行

`agent-server` 收集 Spring 容器中的 `AgentCapabilityProvider` Bean。启动时：

- capability code 为空或重复时启动失败；
- 同一 capability 内工具名重复时启动失败；
- 路由器只依赖 Provider 的通用描述；
- Domain Agent 只调用选定 Provider 的 `definition` 和 `execute`；
- Provider 抛出的内部异常被转换为不泄漏凭证的稳定失败事件。

外部 adapter 依赖 `agent-spi`，通过 Spring Boot auto-configuration 注册 Provider。

### 6.4 内置能力

仅内置领域无关的 `KnowledgeCapabilityProvider`。删除硬编码企业领域角色、`ApiCatalog`、
`EnterpriseApiClient`、`HttpEnterpriseApiClient`、企业 API 配置及其测试和资源。

## 7. OAuth2 与身份映射

### 7.1 协议

保留现有 Authorization Code + PKCE S256、state 浏览器绑定、refresh token、撤销和刷新单飞锁。
外部路径继续使用：

- `GET /api/auth/oauth2/authorize`
- `GET /api/auth/oauth2/callback`
- `POST /api/auth/logout`
- `GET /api/auth/me`

### 7.2 配置驱动的 userinfo 映射

OAuth2 endpoint 继续由环境变量配置。userinfo 使用 JSON Pointer 映射：

```yaml
oauth2:
  claims:
    subject: /sub
    display-name: /name
    tenant-id: /tenant_id
    tenant-name: /tenant_name
    primary-org-id: /primary_org/id
    primary-org-name: /primary_org/name
    roles: /roles
    manageable-org-ids: /manageable_org_ids
    readable-org-ids: /readable_org_ids
  roles:
    platform-admin: platform_admin
    tenant-admin: tenant_admin
```

所有 Pointer 和角色名均可配置。无法通过字段映射表达的供应商可替换
`OAuth2IdentityMapper` Bean，不需要修改控制器或业务服务。

### 7.3 会话和身份刷新

认证会话保存：

- user subject 和 display name；
- access token、refresh token 和 access token 过期时间；
- `IdentityContext`；
- identity 获取时间。

access token 延续现有提前 30 秒刷新策略。身份最多缓存 5 分钟；过期后重新调用 userinfo。
access token 刷新成功时也重新获取身份。身份刷新失败时关闭权限，不使用过期角色继续执行管理或
租户资源操作。旧结构的 Redis 会话视为无效，升级后用户重新登录。

### 7.4 授权服务

`AuthorizationService` 统一提供：

- `requirePlatformAdmin(identity)`；
- `requireTenantAdmin(identity)`；
- `requireTenant(identity)`；
- `canManageOrg(identity, orgId)`；
- `canReadOrg(identity, orgId)`。

控制器隐藏菜单不构成授权；所有写入、下载、审批和读取都在服务端检查。

## 8. Mock OAuth2 模块

`mock-oauth2` 是独立 Spring Boot 应用，默认只绑定 loopback 地址，不作为
`agent-server` 生产依赖。它实现：

- `/oauth2/authorize`；
- `/oauth2/token`；
- `/oauth2/userinfo`；
- `/oauth2/revoke`；
- PKCE S256 校验；
- 有 TTL 且只能消费一次的 authorization code；
- access token 和 refresh token；
- refresh token 轮换；
- 简单的本地用户选择页。

种子身份至少包括：

- 一个平台管理员；
- Tenant A 与 Tenant B；
- 每个租户一个租户管理员和一个普通用户；
- 每个租户至少两级组织关系；
- `manageable_org_ids` 和 `readable_org_ids` 的不同组合。

`agent-server` 提供 `local-mock` profile，endpoint 指向本机 mock 服务。正式部署只更换 endpoint、
client 配置和 claim 映射。

## 9. 租户级 LLM

### 9.1 数据模型

`llm_provider_preset`：

- `id`、`name`、`base_url`、`model`；
- 可空的 `balance_path`；
- 创建人和时间戳。

`tenant_llm_config`：

- `id`；
- 唯一的 `tenant_id`；
- `preset_id`；
- `api_key_encrypted`；
- 时间戳。

配置归属键必须是 `tenant_id`，不得退化为 user ID。

### 9.2 权限

- 平台管理员可创建、编辑和删除 provider preset。
- 租户管理员可创建、更新和删除当前租户配置。
- 普通用户只能读取当前租户的预置名称、模型和脱敏 Key。
- 无 tenantId 的身份不得读取、配置或使用租户模型。
- 任何请求不得接受客户端提交的 tenantId 作为授权依据；只使用 `IdentityContext.tenantId`。

### 9.3 LLM 客户端

将 DeepSeek 专用客户端替换为通用 OpenAI-compatible streaming 客户端：

- base URL、model 和 API Key 来自租户配置；
- 无工具时不发送 `tools` 和 `tool_choice`；
- 请求 usage chunk，并汇总多轮和多 Agent token；
- 错误消息使用中性 `LLM` 前缀；
- 可按 preset 的 `balance_path` 查询当前租户 Key 的余额；
- 缓存使用短 TTL，配置修改立即清除本实例缓存，其他实例最多在 TTL 后收敛。

未配置模型时返回 HTTP 428：

```json
{
  "code": "LLM_CONFIG_REQUIRED",
  "message": "Current tenant has no LLM configuration"
}
```

### 9.4 Key 与 endpoint 安全

- API Key 使用 AES-256-GCM 和随机 12 字节 IV。
- 主密钥由 base64 编码的 32 字节环境变量提供；缺失或非法时启动失败。
- Compose 和源码不得提供固定默认密钥。
- provider base URL 默认只接受 HTTPS；mock/test profile 可显式允许 HTTP。
- API Key 不出现在 GET 响应、日志或远端错误回显中。

## 10. 知识库范围

### 10.1 数据模型

`knowledge_documents` 增加：

- `scope_type`：`GLOBAL`、`TENANT` 或 `ORG`；
- `tenant_id`、`tenant_name`；
- `org_id`、`org_name`。

名称是上传时快照，授权判断只使用 ID。

### 10.2 上传与审批

| 上传者 | 范围 | 初始状态 |
|---|---|---|
| 平台管理员 | GLOBAL | APPROVED |
| 租户管理员 | TENANT | APPROVED |
| 普通用户 | ORG | PENDING |

普通用户缺少 tenantId 或 primaryOrgId 时上传返回 400，不创建无归属记录。

平台管理员可管理全部文档。租户管理员只能管理同租户文档，以及组织位于
`manageableOrgIds` 中的 ORG 文档。普通用户只能查看自己的管理记录，不能审批。

### 10.3 消费范围

聊天可用知识为：

1. 所有 GLOBAL 文档；
2. 当前 tenantId 的 TENANT 文档；
3. 当前 tenantId 下、orgId 位于 `readableOrgIds` 中的 ORG 文档。

同一过滤规则用于索引、全文读取和工具执行，避免只过滤索引但可猜 ID 越权读取。

## 11. 聊天提示语

新增 `chat_hints` 表和 `/api/chat/hints` CRUD：

- 所有登录用户可读；
- 仅平台管理员可创建、编辑和删除；
- content 为空返回 400；
- 不存在的 ID 返回 404；
- 本次不提供排序、分页、审计或软删除。

## 12. API 与错误格式

保留聊天、会话、认证、文件和知识库主要路径。删除业务性的 `/api/services`。

新增和重构后的错误统一为：

```json
{
  "code": "STABLE_MACHINE_CODE",
  "message": "Human-readable message"
}
```

关键状态：

- 400：无效输入或缺少组织归属；
- 401：无会话、OAuth2 token 无效；
- 403：身份存在但无权操作；
- 404：资源不存在或需要隐藏其存在；
- 409：状态冲突；
- 428：租户未配置 LLM；
- 502：身份或 LLM provider 暂时不可用。

## 13. Flyway 迁移

保留已有 V1 和 V2。新增：

- V3：`chat_hints`；
- V4：`llm_provider_preset`；
- V5：`tenant_llm_config`；
- V6：知识库 scope、tenant 和 org 字段及索引。

V6 将存量知识文档设置为 `GLOBAL`，保持升级前可见性。迁移必须同时在 MySQL 和 H2 MySQL mode
测试。迁移文件移动到 `agent-server` 后保持版本号和 Flyway history table 名称不变。

## 14. 业务代码和资源清理

删除或替换：

- MES/CRM/WMS/QMS/SRM 角色和服务列表；
- `ApiCatalog`、`EnterpriseApiClient`、`HttpEnterpriseApiClient`；
- `EnterpriseApiProperties` 和 `enterprise-api.*` 配置；
- `api-docs/` 与 `src/main/resources/api-docs/`；
- CRM/MES/QMS/SRM/WMS 等业务文档；
- API 文档抓取和转换脚本；
- 与上述代码一一对应的测试；
- Jowo、SmartLink、l7-skill、DeepSeek 专有命名。

保留仍通用的文件上传、会话、聊天记忆、知识库和 OAuth2 能力，并迁移到中性包名。

## 15. 部署与开发体验

- 根目录 `mvn clean test` 构建全部模块。
- `agent-server` 和 `mock-oauth2` 各自产生可执行 Spring Boot JAR。
- 默认 Compose 只启动 `agent-server`；开发 profile 可额外启动 `mock-oauth2`。
- 所有 endpoint、client ID、client secret、claim Pointer、角色名和加密密钥均可由环境变量覆盖。
- 更新 README，提供本地双服务启动、种子用户、登录 URL、第三方 IdP 接入和 capability adapter 指南。

## 16. 测试策略

### 16.1 agent-spi

- 公共 record 的不变集合和输入约束；
- capability code 和工具名校验；
- SPI 不依赖 `agent-server`。

### 16.2 mock-oauth2

- 正确和错误 PKCE；
- authorization code 只能使用一次且会过期；
- refresh token 轮换；
- userinfo claim 完整性；
- revoke 后 token 不可用。

### 16.3 agent-server

- OAuth2 callback、Token 刷新单飞锁和身份 5 分钟刷新；
- claim Pointer 和角色映射；
- 平台管理员、租户管理员、普通用户权限矩阵；
- 租户 LLM 配置隔离、加密、掩码、428 和缓存失效；
- OpenAI-compatible streaming、空工具列表和 usage；
- GLOBAL/TENANT/ORG 知识上传、审批、列表、下载和聊天读取；
- 提示语 CRUD 权限；
- capability 动态注册、路由与工具循环。

### 16.4 跨模块与迁移

- 启动真实 mock OAuth2，覆盖 authorize、callback、userinfo、refresh 和 revoke；
- 从空库运行 V1-V6；
- 从 V2 schema 升级并验证存量知识为 GLOBAL；
- 根目录执行全量测试和两个可执行 JAR 的 package；
- 扫描受版本控制的源码和资源，禁止业务领域名、`com.jowo` 和 `l7-skill` 残留。

## 17. 既有基线问题

迁移前基线在 JDK 25 下执行 186 个测试，185 个通过，
`ReactiveChainBlockHoundTest.installBlockHound` 因 BlockHound 1.0.10 内置 Byte Buddy 不支持
JDK 25 而报 instrumentation error。实施时必须升级兼容依赖或调整测试 JVM 配置，并用实际
阻塞调用验证 BlockHound gate 仍然有效；不得简单删除测试或跳过。

## 18. 完成标准

1. 根目录全量测试在项目声明支持的 JDK 上无失败、无错误。
2. 三个模块可独立构建，两个应用模块可独立启动。
3. mock OAuth2 到 agent-server 的真实登录和身份刷新链路通过。
4. 两个租户使用不同模型配置，互相无法读取、修改或使用对方配置。
5. 知识库三种范围的列表、下载、审批和聊天读取均无越权。
6. 外部测试 Provider 可只依赖 `agent-spi` 注册并完成一次工具调用。
7. 版本控制中的产品源码、资源和部署配置不再包含内部业务系统或旧品牌命名。
8. README 包含本地开发、生产 IdP 替换、数据库升级和 capability adapter 文档。
