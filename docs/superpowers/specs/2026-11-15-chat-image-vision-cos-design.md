# 聊天图片识别：COS 对象存储 + image_url 设计

日期：2026-11-15

状态：用户已确认设计（2026-11-15）。已实现并通过单元测试（后端全量 953 项，仅 JDK 25 环境检查因本机只有 JDK 24 失败）和真实存储桶联调；尚未提交，尚未与真实 Grok 租户做端到端验证。本文以最终设计为准。

## 1. 问题与根因

用户上传图片后提问，模型（Grok 4.7，经 OpenAI 兼容接口）回答“能看到你上传了 `xxx.jpeg`，但看不到图片内容”。模型的回答准确反映了它收到的内容：

1. 前端 `web/src/App.tsx` 先调用 `POST /api/files/upload` 把文件存到本地 `${app.upload.dir}/{conversationId}/{name}`，再把文件名拼进消息文本：`[已上传文件]\n- <name>\n\n<问题>`。
2. `ChatController` 只读取最后一条消息的 `content` 字符串，`TurnCommand.attachmentIds` 固定为空。
3. `LlmClient.Message.content` 是 `String`，`OpenAiCompatibleLlmClient.toWireMessage` 只发送字符串 `content`，从未构造多模态 `content` 数组。
4. 编排器与能力工具都不读取已上传的文件。

即图片字节从未离开服务器。历史原因：`2026-06-16-frontend-file-upload-design.md` 假设由 OpenClaw/Claude CLI 用自带的 Read 工具按路径读文件；改为后端直连 OpenAI 兼容 HTTP 接口后，没有任何环节再读取文件。

## 2. 用户决定

| 事项 | 决定 |
| --- | --- |
| 图片如何交给模型 | 上传到腾讯云 COS，以 `image_url` 传给模型（不使用 base64 内联） |
| 存储桶权限 | **公有读、私有写**（用户有意设置）。任何人拿到对象 URL 即可下载 |
| URL 形式 | 普通对象 URL，不签名、不过期 |
| 对象生命周期 | 上传后**永不删除**；不配置 COS 生命周期规则 |
| 遗忘语义 | “遗忘”只作用于记忆（历史上下文、召回、记忆条目），不删除 COS 对象 |
| 历史图片重发 | 历史窗口内的**所有**图片在每次模型调用时重发，不设上限 |
| PDF | 本期不处理，仍只有文件名进入上下文 |

## 3. 总体流程

```
React (App.tsx)
  │ ① POST /api/files/upload (multipart: sessionId + files[])
  ▼
UploadController
  │ ② FileStorageService.save → 本地磁盘（不变，24h TTL 清理不变）
  │ ③ ChatImageStore.mirror → CosClient.put（仅 image/*，签名 PUT）
  │    返回 [{sessionId, name, size}]（响应格式不变）
  │
  │ ④ POST /api/chat/completions
  │    { messages:[{role:user, content:"[已上传文件]\n- name\n\n问题"}],
  │      conversationId, attachments:["name", ...] }
  ▼
ChatController.startTurn
  │ ⑤ verifyAccess → ChatImageStore.imageUrls(conversationId, attachments)
  │ ⑥ TurnCommand(attachmentIds = attachments, imageUrls = urls) → TurnLifecycle.accept
  │    用户消息持久化为 Message.user(text).withImageUrls(urls)
  ▼
Orchestrator
  │ ⑦ 当前问题所在的 user 消息附加 urls；历史消息从库中反序列化时自带 imageUrls
  ▼
OpenAiCompatibleLlmClient.toWireMessage
    content = [{type:text,text}, {type:image_url,image_url:{url}}, ...]
```

## 4. 组件设计

路径相对 `agent-server/src/main/java/io/github/sukimiya/agileagent/`。

### 4.1 配置 `config/CosProperties`

`@ConfigurationProperties(prefix = "app.cos")`，`application.yml`：

```yaml
app:
  cos:
    endpoint: ${COS_BUCKET_ENDPOINT:}
    secret-id: ${COS_SECRET_ID:}
    secret-key: ${COS_SECRET_KEY:}
    key-prefix: chat-uploads
```

- `endpoint` 为完整存储桶地址，例如 `https://<bucket>-<appid>.cos.<region>.myqcloud.com`，末尾 `/` 会被去掉。
- `enabled()`：endpoint、secret-id、secret-key 均非空。未配置时功能整体关闭，行为与改造前一致（只发文件名文本），启动后首次使用记一条 info 日志。
- `COS_ACCOUNT_ID` 不需要：endpoint 已包含存储桶名与 APPID。
- `docker/docker-compose.yml` 的服务端服务透传 `COS_BUCKET_ENDPOINT`、`COS_SECRET_ID`、`COS_SECRET_KEY`。

### 4.2 `service/CosClient`

不引入 COS SDK，手写 XML API 签名，复用现有 `WebClient.Builder`。

- `put(key, bytes, contentType)`：`PUT {endpoint}/{key}`，请求头 `Authorization`、`Content-Type`、`Content-Length`、`Content-MD5`（16 字节 MD5 的 Base64）。非 2xx 以 `UncheckedIOException` 失败。
- 签名（[请求签名](https://cloud.tencent.com/document/product/436/7778)），只签 `host` 头、不签 URL 参数：
  - `KeyTime = {now-60};{now+10min}`（Unix 秒，留 60s 时钟偏差）
  - `SignKey = hex(HMAC-SHA1(SecretKey, KeyTime))`
  - `HttpString = lower(method) + "\n/" + key + "\n\nhost=" + urlencode(host) + "\n"`
  - `StringToSign = "sha1\n" + KeyTime + "\n" + hex(SHA1(HttpString)) + "\n"`
  - `Signature = hex(HMAC-SHA1(SignKey 十六进制字符串的 UTF-8 字节, StringToSign))`
  - `Authorization = q-sign-algorithm=sha1&q-ak={SecretId}&q-sign-time={KeyTime}&q-key-time={KeyTime}&q-header-list=host&q-url-param-list=&q-signature={Signature}`
- 注意：`q-sign-time`/`q-key-time` 中的 `;` 必须原样发送；联调中将其编码为 `%3B` 会得到 `403 SignatureDoesNotMatch`。
- 日志与异常信息不包含签名、密钥或响应体。
- 读取不需要签名（公有读）。第一阶段实现过的预签名 URL 已移除。

### 4.3 `service/ChatImageStore`

- 对象键：`{keyPrefix}/{conversationId}/{sha256(name) 前 32 位十六进制}{ext}`。`ext` 为文件名的小写后缀，仅当匹配 `\.[a-z0-9]{1,5}` 时保留。键只含 ASCII，签名和 URL 均无需编码；同一会话内的同名文件得到同一个键（本地存储已用 `resolveUnique` 保证会话内文件名唯一）。
- `mirror(conversationId, name, path, contentType)`：仅当 COS 已配置且 Tika 检测到的类型为 `image/*` 时上传；否则为空操作。失败沿用上传接口已有的 `500 STORAGE_ERROR`（本地文件已保存，按 TTL 清理）。
- `imageUrls(conversationId, names)`：返回 `{endpoint}/{key}`。跳过：后缀不是 jpg/jpeg/png/gif/webp、文件名非法（`RejectedException`）或本地文件不存在。只记 debug 日志，不抛异常。

### 4.4 上传接口

`FileStorageService.Ref` 增加 `contentType`（已有的 Tika 检测结果）。`UploadController.upload` 在每个文件 `save` 之后调用 `mirror`。响应格式不变。

### 4.5 聊天接口

- `POST /api/chat/completions` 读取可选字段 `attachments`：字符串数组，忽略非字符串项，最多取 `app.upload.max-files-per-request` 个。字段不是数组时忽略。
- `attachments` 写入 `TurnCommand.attachmentIds`，因此幂等指纹覆盖附件：同一 Idempotency-Key 下文本相同但附件不同，视为不同请求。
- URL 在 `verifyAccess` 通过之后、`lifecycle.accept` 之前解析（在 boundedElastic 上执行，涉及本地文件查找）。`conversationId` 为空或无附件时不解析。
- `POST /api/chat`（旧接口）不支持附件。
- 前端 `App.tsx` 在请求体中加入 `attachments: uploadedRefs.map(r => r.name)`，消息文本中的 `[已上传文件]` 前缀保持不变（为历史和记忆保留文件名）。

### 4.6 消息模型与持久化

- `LlmClient.Message` 增加第 5 个字段 `List<String> imageUrls`，标注 `@JsonInclude(NON_EMPTY)`；保留 4 参数构造器和 `withImageUrls(urls)`。没有图片的消息序列化结果与改造前逐字节相同，已有存储数据无需迁移。
- `TurnCommand` 增加 `imageUrls`（不进入幂等指纹）。`TurnLifecycle` 把用户消息存为 `Message.user(text).withImageUrls(imageUrls)`。
- `ConversationHistory` 把存储的 payload 反序列化为 `Message`，因此历史轮的 `imageUrls` 随上下文组装进入请求，无需额外代码。

### 4.7 编排器

- `Orchestrator.chat(..., ResolvedModel, List<String> imageUrls)` 重载；原签名委托为空列表。
- 当前问题以字符串传入上下文组装，因此 `stream()` 在发送前把 URL 附加到“最后一条 role=user 且内容等于当前问题”的消息上。历史消息、系统提示和汇总调用的“各能力执行结果”后缀不受影响。
- 覆盖的调用：路由（ROUTING）、直接回答（DIRECT_ANSWER）、汇总（AGGREGATE）。内存命令分类不附加当前轮图片。
- 历史窗口内已存储 `imageUrls` 的用户消息原样发送，即历史图片在每次调用中重发，不设上限（用户决定）。
- 预期：能力分支的 DomainAgent 背景取自同一份组装上下文，因此背景中的历史图片也会发给能力模型；当前轮图片不附加给能力任务。

### 4.8 发送格式

`OpenAiCompatibleLlmClient.toWireMessage`：`imageUrls` 非空时

```json
{"role":"user","content":[
  {"type":"text","text":"..."},
  {"type":"image_url","image_url":{"url":"https://..."}}
]}
```

否则仍发送字符串 `content`。

## 5. 记忆与遗忘

| 数据 | 存储位置 | 是否进入长期记忆 |
| --- | --- | --- |
| 图片字节 | COS（永久）与本地磁盘（约 24h） | 否 |
| 图片 URL | 用户消息 payload 的 `imageUrls` | 只用于历史上下文重发；召回分块与记忆判断只读 `content` |
| 文件名 | 用户消息文本 `[已上传文件]` 前缀 | 是（文本） |
| 模型对图片的描述 | 助手回答文本 | 是（文本） |

- 遗忘或删除导致的排除（`RecallExclusions.excludedTurn`）会把整轮移出历史，该轮图片随之不再发送给模型；COS 对象保留（用户决定）。
- 历史窗口之外、只经召回命中的轮次，以文本参考消息注入，不附带图片。
- 本地附件仍由 `UploadCleanupTask` 按会话目录修改时间做 24h 清理；COS 是长期副本，`imageUrls` 不依赖本地文件。

## 6. 已知限制与风险

- **公开访问**：URL 不过期且无法撤销。保护仅来自对象键不可猜（会话 ID + 文件名哈希）。URL 会出现在数据库、备份和发给模型供应商的请求中。
- **Token 预算**：`TokenEstimator` 不计入图片 token。历史图片不设上限，图片多的长会话可能超出模型上下文或产生较高费用，且 413 预检不能提前发现。
- **模型能力**：租户配置的模型不支持图片输入时，带图片的轮次会收到供应商报错。
- **COS 依赖**：COS 不可用时图片上传失败（`STORAGE_ERROR`）。COS 未配置时自动退化为纯文本。
- **对象不删除**：会话删除、遗忘都不清理 COS，存储量只增不减。
- **PDF**：内容不进入模型。

## 7. 验证

- 单元测试：`CosClientTest`（固定时钟下的签名）、`ChatImageStoreTest`（键派生、URL 格式、跳过规则、未配置）、`OpenAiCompatibleLlmClientTest`（content 数组）、`OrchestratorTest`（仅当前问题与历史用户消息携带图片）、`UploadControllerTest`（图片镜像、PDF 不上传、COS 失败 500）、`ChatControllerTest`（附件进入 TurnCommand、访问校验后才解析 URL）、TurnLifecycle 持久化测试、前端 `App.test.tsx`（请求体含 `attachments`）。
- 后端全量：`mvn -pl agent-server -am test`。CI 必须使用 JDK 25（`OpenSourceBoundaryTest.buildAndRuntimeUseJdk25`）。
- 真实存储桶联调（2026-11-15，`fishpal-1304966108`，ap-hongkong）：签名 PUT 成功；签名 GET 200 且内容一致；不带签名 GET 200（确认公有读）；签名 DELETE 204 后 GET 404。一次早期失败的联调遗留了 `chat-uploads/_devin-selftest/1548569676384541.png`（71 字节），按用户决定保留。
- 待做：与真实 Grok 租户配置做一次端到端对话验证（当前环境无租户模型配置）。

## 8. 后置事项

- 上传时让模型生成图片描述并写入记忆，使召回能按图片内容检索。
- 召回命中窗口外的轮次时，按存储的 `imageUrls` 重新附加图片。
- 图片 token 估算并纳入上下文预算。
- PDF：用 Tika 抽取文本注入上下文。
