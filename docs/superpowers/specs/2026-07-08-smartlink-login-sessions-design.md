# SmartLink 登录 + 用户会话隔离设计

## 背景

当前聊天应用（后端 `mcp-tool` / 前端 `l7-skill-frontend`）没有用户概念：`conversationId`
（=前端 URL `?id=` 参数）由浏览器端 `genId()` 随机生成，`ChatMemory`（Redis）按这个 id
存对话历史，不区分是谁在用。任何人拿到一个 id 就能看到对应对话，且没有"我的历史会话
列表"这种视图。

`doc/api-doc/smartlink.md`（`l7-skill-frontend` 仓库）记录了企业内部 SmartLink 认证系统
（`http://192.168.3.234:9000`）的用户名密码登录接口和用户信息接口。本设计接入这个登录
系统，让不同用户只能看到自己的历史会话，并在前端加一个左侧会话历史列表。

顺带说明一个已确认的既有事实：`EnterpriseApiClient`（MES/CRM/WMS/QMS/SRM 调用）用的是
写死在配置里的服务级 `access-token`，跟具体登录用户无关。SmartLink 登录在本设计里**只**
用来确定"当前是谁在用聊天界面"，不影响、也不参与后续企业接口调用的鉴权。

## 目标

- 用户必须用 SmartLink 账号密码登录才能使用聊天功能（不支持匿名使用）。
- 不同用户只能看到、访问自己创建的历史会话；访问他人会话一律当作不存在处理。
- 前端左侧展示当前用户的历史会话列表，点击切换到对应会话（URL `?id=` 保持现有语义）。
- 单个会话消息条数不设上限（去掉现有 `ChatMemory` 20 条截断逻辑）。

## 非目标

- 会话删除、会话改名功能。
- 历史消息里的附件重新渲染成可下载卡片（重新加载后附件仍以发送时那段
  `[已上传文件]\n- path` 纯文本形式显示，不做结构化持久化）。
- SmartLink 用户注册、密码重置、账号管理——只对接登录，账号必须已在 SmartLink 存在。
- 后端签发 token 的滑动续期——固定 30 天 TTL，过期后重新登录即可。
- 用 SmartLink 权限接口（`/api/authorize/me`）过滤 AI 可调用的企业接口范围——文档里
  提到这是"备用"，本次不做。
- 把 SmartLink 原始 token 转发给企业接口调用链路——如上所述两者无关。
- 文件上传/下载接口（`/api/files/upload`、`/api/files/download`）不做会话归属校验——只要求
  登录，不检查 `sessionId` 是否属于当前用户。已知风险：`sessionId` 一旦泄露，任何登录用户都能
  下载该会话下的文件。接受这个风险是因为 `sessionId` 是随机不可猜的 `genId()`，不会通过任何
  界面暴露给其他用户；且上传发生在会话行落库之前，无法用同一种方式做归属校验。这是有意识做出
  的取舍，不是遗漏。

## 认证架构

后端代理 SmartLink 登录，并签发自己的一次性 opaque token：

```
Frontend                    Backend (mcp-tool)              SmartLink (192.168.3.234:9000)
   │  POST /api/auth/login        │                                │
   │  {username, password} ─────► │                                │
   │                               │  POST /api/authorize/login ──►│
   │                               │◄──────── {token, userId} ─────│
   │                               │  GET /api/user/detail ───────►│
   │                               │◄──────── {name, email, ...} ──│
   │                               │  生成 opaque token，写入 Redis │
   │                               │  authsession:{token} →         │
   │                               │  {userId, name}, TTL 30 天     │
   │◄── {token, userId, name} ─────│                                │
   │                               │                                │
   │  后续请求带 Authorization: Bearer {token}                      │
```

- 除 `POST /api/auth/login` 外，所有 `/api/**` 请求都必须带 `Authorization: Bearer {token}`，
  由一个 `HandlerInterceptor` 统一校验（查 Redis `authsession:{token}`，查不到/过期即 401）。
- SmartLink 自己签发的 token 只在登录握手这一次性调用里使用，不转发给前端，也不在后续
  任何请求里复用。
- 登录强制：前端没有有效本地 token 时只展示登录页，不能发消息。

## 数据模型

### MySQL：`sessions` 表（会话元数据，供"我的历史会话"列表使用）

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | VARCHAR(64) PK | 会话 id，沿用前端现有 `genId()` 格式（即 URL `?id=` 的值） |
| `user_id` | VARCHAR(64) NOT NULL | SmartLink 返回的 userId，所有权归属 |
| `title` | VARCHAR(255) NOT NULL | 会话标题，取首条用户消息截断（约 24 字） |
| `created_at` | DATETIME NOT NULL | 创建时间 |
| `updated_at` | DATETIME NOT NULL | 最后一次有新消息的时间 |

索引：`(user_id, updated_at DESC)`，用于按用户查会话列表并按活跃时间排序。

不新建 `users` 表——SmartLink 是用户身份的唯一权威来源，不在本地持久化用户资料，只在
Redis 登录会话里临时缓存 `{userId, name}`。

表结构通过 Flyway 管理：新增 `flyway-core` 依赖，迁移文件放在
`src/main/resources/db/migration/V1__create_sessions_table.sql`，Spring Boot 在启动时
自动执行。后续任何 schema 变更都走新的 `V2__...sql` 等文件，不再手动跑 DDL。

**影响**：Flyway 在启动时会尝试连接并执行迁移，即使有
`hikari.initialization-fail-timeout: -1`（原本是为了让 `local` profile 在没有本地 MySQL
时也能先启动应用）也无法绕开——迁移失败会直接导致启动失败。这是本设计已确认可接受的
代价：既然登录已经是强制的，会话存储（MySQL）也就是启动的硬依赖，`local` profile 现在
需要一个真实可连接的 MySQL 才能启动应用。

### Redis

- 新增 `authsession:{token}` → `{userId, name}`，TTL 30 天，不做滑动续期。
- 已有的 `chatmemory:{ownerId}`（`ownerId` = sessionId）保持 key 结构不变，只移除
  `ChatMemory.append()` 里 `MAX_MESSAGES = 20` 的截断逻辑（含"截断点落在孤立 tool 消息
  前则跳过"那段代码一并删除），满足"单会话条数不设上限"。

`sessions` 表（元数据+归属）和 `chatmemory:*`（完整对话内容，供 LLM 上下文使用）是两条
平行的存储，通过 sessionId 关联，不合并。

## 配置与依赖

- 新增 `SmartLinkProperties`（仿照现有 `EnterpriseApiProperties` 写法）：单个
  `smartlink.base-url`。`local` profile 默认值 `http://192.168.3.234:9000`；`dev` 走
  Nacos 上已预留的 `smartlink` 配置段（此前 Nacos 集成设计里已经准备好这个 key，本次
  直接使用）。
- 新增 `flyway-core`（Spring Boot 自动在启动时执行 `src/main/resources/db/migration/`
  下的迁移）。
- MySQL 这次是第一次真正写业务代码用到它——`sessions` 表用 Spring JDBC
  （`JdbcTemplate`，已在 classpath 上）写一个简单的 `SessionRepository`，不引入
  JPA/MyBatis 等更重的 ORM。

## 后端 API 契约

### 新增

- `POST /api/auth/login` `{username, password}`
  → `200 {token, userId, name}`
  → `401` 用户名或密码错误（SmartLink 登录接口返回非 200）
  → `502` SmartLink 服务不可达（网络错误/超时）
- `GET /api/auth/me` → `200 {userId, name}` / `401`（前端用来校验本地 token 是否还有效）
- `POST /api/auth/logout` → `204`，删除 Redis 里的 `authsession:{token}`
- `GET /api/conversations/{id}/messages` → `200 [{id, role, content}]`，从 `ChatMemory`
  读取并过滤成 `user`/`assistant` 两种角色（`system`/`tool` 消息不返回给前端），`id` 由
  后端按顺序合成。若 `id` 不存在或不属于当前用户 → `404`

### 改造既有的

- `GET /api/conversations` → 真正实现：按当前用户 `user_id` 查 `sessions` 表，按
  `updated_at DESC` 返回 `[{id, title, updatedAt}]`（替换现在的空列表 stub）。
- `POST /api/chat/completions`、`POST /api/chat` → 通过拦截器解析出当前 `userId`；
  - 若请求带的 `conversationId` 在 `sessions` 表中已存在但 `user_id` 不是当前用户
    → 直接 `404`，不进入 agent 逻辑；
  - 若不存在 → 首次消息落库时 `INSERT` 一行到 `sessions`（`title` = 用户消息截断）；
  - 每次消息都更新该行 `updated_at`。

### 不变 / 不做

- `DELETE /api/conversations/{id}`：保留现有空 stub，不接删除功能。

## 错误处理

- 登录失败（凭证错误）→ 401，前端展示"用户名或密码错误"。
- SmartLink 不可达 → 502，前端提示"登录服务暂时不可用"。
- 越权访问他人会话（无论查消息还是发消息）→ 统一 404（不暴露"存在但不是你的"），前端
  遇到 404 当作无效会话处理，回退到新建一个本地会话。
- token 过期/无效 → 任意 `/api/**`（除登录）请求校验不过 → 401，前端全局拦截 401，清
  本地 token，跳转登录页。

## 前端改造（`l7-skill-frontend`）

- **登录页** `Login.tsx`：用户名/密码表单，调 `POST /api/auth/login`；成功后把
  `{token, userId, name}` 存 `localStorage`。`App.tsx` 启动时若本地没有 token，或
  `GET /api/auth/me` 返回 401，渲染登录页而非聊天界面。
- **统一带 token**：新增 `api/client.ts` 封装 `fetch`，统一加
  `Authorization: Bearer {token}` 头，并在收到 401 时清 token 跳登录页；现有裸 `fetch`
  调用（`/api/chat/completions`、`/api/files/*`）都改用它。
- **左侧会话历史栏** `Sidebar.tsx`：进入即调 `GET /api/conversations` 拉列表，渲染成
  可点击项（标题 + 时间）。点击某一项：`setConvId(id)` 并同步 URL `?id=`（复用现有
  逻辑），调 `GET /api/conversations/{id}/messages` 拉历史替换当前显示的消息列表。
  `localStorage` 消息缓存机制整段删除，后端成为消息历史的唯一权威来源。
- **"新对话"按钮**：行为不变（本地生成新 id，不建后端记录），第一条消息发出去后端才
  会落库；发送成功后刷新一次 sidebar 列表，让新会话出现在列表里。
- **Header**：显示登录用户名（`name`），加"退出登录"按钮，调 `POST /api/auth/logout`
  后清本地 token 回到登录页。

## 测试思路

- `ChatMemoryTest`：去掉截断相关的用例（因为截断逻辑被删除）。
- 新增 `SessionRepository` / Flyway 迁移的集成测试连真实测试库（`local` 已要求真实
  MySQL，不需要"没有 MySQL 也能跑"的分支）。
- `AuthController` / 认证拦截器：用 `MockMvc` + mock SmartLink HTTP 调用做单元测试
  （登录成功/密码错误/SmartLink 不可达三种路径）。
- 会话归属校验：集成测试覆盖"访问他人会话返回 404"这条关键路径。
