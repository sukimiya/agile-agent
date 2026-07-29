# 前端文件上传 + AI 识别 / 附件设计

**日期**: 2026-06-16
**范围**: mcp-tool 项目（Spring Boot 后端 + React 前端 + OpenClaw agent）
**目标**: 让用户在聊天界面上传图片 / PDF，OpenClaw 直接识别；AI 在需要时自动把文件落到 MinIO 并关联到业务记录（CRM 反馈、MES 工艺文件等）。

## 1. 需求与约束

- **文件类型（MVP）**: 仅 image/jpeg、image/png、image/gif、image/webp、application/pdf
  - 上传 Word/Excel 前端拒绝并提示「请先导出为 PDF」
- **多文件**: 一次最多 10 个；单文件 ≤ 10 MB
- **触发模型**: 一个上传按钮（📎），AI 看上下文自动决定是识别还是落附件
- **识别能力依靠 Claude 原生**: 图片 vision、PDF（分页读，> 10 页时用 `pages` 参数循环）
- **会话隔离**: 文件按 `conversationId` 分目录存放
- **生命周期**: 文件 TTL 24h；过期目录每小时清理一次

## 2. 总体架构

```
React 前端 (Chat.tsx)
  │
  │ ① POST /api/files/upload (multipart)
  │   sessionId + files[]
  ▼
Spring Boot (UploadController + FileStorageService)
  │ ② 落盘到 ~/.openclaw/workspace/uploads/<sessionId>/
  │   返回 [{ name, path, size }]
  │
  │ ③ POST /api/chat/completions
  │   messages[-1].content = "[已上传文件]\n- <abs path>\n...\n\n<原问题>"
  ▼
openclaw CLI (Claude)
  │ ④ Read 工具读图 / 读 PDF
  │
  │ ⑤（可选）bash + curl 完成 MinIO 上传 + 业务接口调用
  ▼
CRM file/request-upload → MinIO presigned PUT → 业务接口 (feedback/create 等)
```

三个职责清晰的边界：

- **前端**: 选文件 / 校验 / 上传 / chip 渲染 / SSE 接收 / 下载按钮
- **后端**: 文件落盘 + 路径注入 message + 透传 openclaw 输出（不懂业务、不懂 MinIO）
- **OpenClaw + enterprise-api skill**: 识别 + 业务判断 + MinIO 上传（全部文档驱动 + bash）

## 3. 组件清单

### 3.1 前端

| 组件 | 改动 |
|---|---|
| `Chat.tsx` | 输入栏左侧加 `📎` 多选；上方加 chip 区（含 ❌ 移除）；助手消息右下角加 `💾 下载` 按钮 |
| `store.ts` (zustand) | 新增 `pendingFiles: File[]` 与 `uploadedRefs: { name, path }[]`，发送后清空 |
| 新文件 `Attachment.tsx` | 单文件 chip 组件（图标 + 文件名 + size + ❌） |
| 新文件 `DownloadButton.tsx` | 扫描消息 markdown 链接（扩展名 `.xlsx/.pdf/.csv/.zip/.docx/.md`）；无 URL → blob save `.md`；多 URL → 下拉菜单 |
| 新函数 `uploadFiles(sessionId, files): Promise<UploadedRef[]>` | 调后端 `/api/files/upload`（FormData：`sessionId` 字段 + `files` 字段多个文件），返回服务器绝对路径数组 |
| 发送逻辑 | 先 upload，**前端**再把返回路径拼成 `[已上传文件]` 块拼到 `messages[-1].content` 头部一同发送（见 4.1） |

### 3.2 后端

| 文件 | 改动 |
|---|---|
| 新文件 `UploadController.java` | `POST /api/files/upload`（multipart `files[]` + `sessionId`），返回 `[{ name, path, size }]` |
| 新文件 `FileStorageService.java` | 落盘 + 同名冲突追加 `-1/-2` + magic byte 二次校验（用 Apache Tika `Detector`，pom 加 `tika-core` 依赖）+ path traversal 防护 |
| 新文件 `UploadCleanupTask.java` | `@Scheduled(fixedRate = 3600000)` 每小时清理 > TTL 的会话目录；启动时执行一次 |
| `ChatController.java` | 不改 |
| `AgentService.java` | 不改 |
| `application.yml` | 新增 `app.upload.*` 配置段；放开 multipart 上限 |

### 3.3 enterprise-api skill 扩展

⚠️ **跨仓改动**：enterprise-api skill 在另一个 git 仓库（`git@codeup.aliyun.com:.../l7-skill.git`，本地 `~/.openclaw/workspace/.openclaw/skills/enterprise-api/`），不在 mcp-tool 仓里。实施时分两次提交，分别 push。

在 `enterprise-api/crm.md` 顶部加一段「**文件上传通用流程**」（不绑 CRM，所有服务复用），讲清楚：

- 何时上传（用户已上传文件 + 任务需要把文件登记到业务记录里）
- 两步流程的 bash 模板：
  ```bash
  # 1) 申请上传 URL
  RESP=$(curl -s "http://<gateway>/crm-service/api/file/request-upload?fileNum=1&parentDir=<dir>&fileNames=<name>" \
    -H "x-access-token: $X_ACCESS_TOKEN")
  URL=$(echo "$RESP" | python3 -c "import json,sys;print(json.load(sys.stdin)['data'][0]['url'])")
  KEY=$(echo "$RESP" | python3 -c "import json,sys;print(json.load(sys.stdin)['data'][0]['key'])")
  # 2) PUT 文件
  curl -s -X PUT --data-binary @<本地路径> -H 'Content-Type: <mime>' "$URL"
  # 3) 把 $KEY 填进业务接口的 attachments 字段
  ```
- 何时**不**上传（纯识别问题，用户只是问"这是什么"）

Tier 2 文档 `crm/file-request-upload.md` 已由生成器覆盖，不需要重写。

## 4. 数据流

### 4.1 路径注入约定

后端拼好的 `--message` 文本：

```
[已上传文件]
- /Users/.../uploads/conv-123/photo.jpg
- /Users/.../uploads/conv-123/quality-report.pdf

<用户原始问题>
```

方括号块是给 AI 看的"系统提示"，告知文件就绪 + 用 Read 工具读取。

### 4.2 场景 A：纯识别（不落 MinIO）

```
用户：📎 photo.jpg + "这张图里设备状态正常吗？"
  → 后端落盘 + 路径注入
  → Claude 用 Read 读 photo.jpg
  → SSE 流式回复："这张图显示一台运转中的车床..."
```

文件留在磁盘等下次同会话引用 / 24h 清理。零 MinIO 调用。

### 4.3 场景 B：识别 + 落附件（AI 自动判断）

```
用户：📎 defect.jpg + "帮多明客户提个反馈：设备报警有延迟，附上现场照"
  → 后端落盘 + 路径注入
  → Claude 多步 tool call：
     1. Read defect.jpg 描述图片（内部）
     2. crm/customer-quick-search 找"多明" → customerId
     3. 读 enterprise-api 的「文件上传通用流程」
     4. bash: curl GET request-upload → 解析 presigned URL + key
     5. bash: curl -X PUT --data-binary @/uploads/.../defect.jpg "<presigned>"
     6. bash: curl POST feedback/create 带 attachments: ["<key>"]
     7. SSE 回复："已为多明提交反馈，附件已上传"
```

### 4.4 AI 判定要不要落 MinIO

写进 skill 文档的指引：

| 信号 | 上传 MinIO？ |
|---|---|
| 用户说"创建/提交/记录/反馈/工单/工艺文件 + 附上" | ✓ |
| 用户问"这是什么/识别一下/什么意思/帮我看看" | ✗ |
| 用户说"把这个文件存下" | ✓ 反问哪个业务域 |
| 模棱两可 | 反问："这张图需要附在哪条业务记录里吗？还是只看一下？" |

### 4.5 下载场景

**约定**：AI 想让用户下载某个文件时，必须以标准 markdown 链接格式输出 `[显示名.ext](url)`，扩展名落在白名单（`.xlsx/.xls/.pdf/.csv/.zip/.docx/.doc/.md/.txt`）。前端 `DownloadButton` 按此规则匹配。

```
用户："导出最近一周的销售订单表"
  → Claude 调对应导出接口 → 拿到 MinIO URL
  → SSE 回复："已生成：[2026-06-16-sales.xlsx](http://minio.../xxx.xlsx)
     点击右下角 💾 下载"
  → 前端 DownloadButton 扫描出 .xlsx 链接 → 渲染为下载按钮，点击触发浏览器下载
```

不在白名单内的链接（普通 http 链接、anchor 链接）不参与下载按钮逻辑，正常渲染为可点击链接。

## 5. 错误处理

### 5.1 前端校验（上传前）

| 校验 | 行为 |
|---|---|
| MIME / 后缀不在白名单 | 拒绝 + chip 提示「仅支持图片和 PDF」 |
| 单文件 > 10 MB | 拒绝 + chip 提示「超出 10 MB 上限」 |
| 选了 > 10 个文件 | 拒绝 + 提示「一次最多 10 个文件」 |
| Word/Excel/其他 | 拒绝 + 提示「请先导出为 PDF 后上传」 |

### 5.2 后端错误响应

| 情况 | HTTP | body |
|---|---|---|
| 类型不允许 / 超过上限 | 400 | `{ code: "FILE_REJECTED", reason: "..." }` |
| 落盘 IO 失败 / 磁盘满 | 500 | `{ code: "STORAGE_ERROR", reason: "..." }` |
| Magic byte 校验失败（恶意改后缀） | 400 | `{ code: "MIME_MISMATCH", reason: "..." }` |

前端收到非 200 → 失败 chip 变红 + 提示，**已成功的文件保留**，让用户重选失败的，不整批回滚。

### 5.3 AI 侧错误

| 场景 | AI 行为 |
|---|---|
| `request-upload` 返回 5xx | 重试 1 次；失败告诉用户「文件上传到 MinIO 失败，反馈已创建但附件未关联，错误码 XXX」 |
| `PUT` presigned URL 超时 / 网络中断 | 同上 |
| `feedback/create` 失败但 MinIO 已传成功 | 告诉用户「文件已存档但反馈创建失败，请稍后重试 — 不需要重新上传」 |

依赖 enterprise-api skill 规则 R1（失败查文档，禁止盲猜重试）。

## 6. 文件生命周期与配置

### 6.1 位置与生命周期

- 位置：`~/.openclaw/workspace/uploads/<conversationId>/<sanitized-name>`
- 同名冲突：追加 `-1`、`-2` 后缀
- TTL：24 小时（按目录 mtime）
- 清理时机：Spring Boot 启动时扫一遍 + `@Scheduled(fixedRate = 3600000)`（每小时）
- 不提供手动清理接口

### 6.2 配置（`application.yml`）

```yaml
app:
  upload:
    dir: ${user.home}/.openclaw/workspace/uploads
    ttl-hours: 24
    max-size-mb: 10
    max-files-per-request: 10
    allowed-content-types:
      - image/jpeg
      - image/png
      - image/gif
      - image/webp
      - application/pdf

spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 110MB  # 10 files * 10MB + headroom
```

### 6.3 安全 / 隐私

- **会话隔离**：upload endpoint 必须传 `sessionId`，落到对应目录，不允许跨会话读取
- **Path traversal 防护**：原始文件名去掉 `../` 和绝对路径分隔符，只保留 basename
- **Magic byte 二次校验**：单纯靠 Content-Type / 后缀不够，落盘后用 magic byte 验真实类型
- **不向用户透露后端绝对路径**：前端 chip 只显示文件名；AI 输出由 SOUL.md 规则 R3 兜底
- **CORS**：现有 `@CrossOrigin(origins = "*")` 保持

## 7. 测试范围

| 层 | 测试点 |
|---|---|
| 后端单元 | `FileStorageService`：落盘、冲突重命名、过期清理、类型校验、size 校验、magic byte 校验 |
| 后端集成 | `UploadController`：multipart 多文件、错误码、目录隔离、sessionId 缺失返回 400 |
| 前端 | `Chat.tsx`：chip 渲染/移除、超额校验、上传失败 chip 变红、发送时清空 pendingFiles |
| 前端 | `DownloadButton`：纯文本消息 → 存 `.md`；含 1 个 `.xlsx` 链接 → 直接下载；含多 URL → 下拉菜单 |
| 端到端（手测） | 场景 A：上传图 + 识别问题；场景 B：上传图 + 让 AI 创建反馈（验证 MinIO 真的传了文件）；场景 C：让 AI 导出 Excel 验证下载按钮 |

## 8. 不做的事（明确划界）

- 不做 Word/Excel 原生上传（用户先转 PDF）
- 不做断点续传 / 分片上传（单文件 ≤ 10 MB 用不上）
- 不做用户手动选「识别还是附件」的 UI（统一交给 AI 判断）
- 不做手动清理 / 文件管理界面（24h TTL 足够）
- 不做跨会话引用文件
- 不做上传进度条（单文件 10 MB 内毫秒级，不必要）
