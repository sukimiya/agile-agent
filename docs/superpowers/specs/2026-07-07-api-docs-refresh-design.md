# API 文档刷新流程设计

## 背景

`ApiCatalog`（`src/main/java/com/jowo/agent/service/ApiCatalog.java`）在运行时从
`src/main/resources/api-docs/{crm,mes,wms,qms,srm}/*.md` 加载平铺格式的接口文档，
生成给 LLM 的精简索引和白名单。这些文档最初由 `scripts/convert-api-docs.py`
从静态导出的 swagger JSON（`api-docs/{service}-api-docs.json`）转换而来，但：

1. 那些 JSON 是某次手动导出的快照，企业服务的接口变化后不会自动更新。
2. `convert-api-docs.py` 的输出目标是仓库根目录的 `docs/{service}.md`，
   不是 `ApiCatalog` 真正读取的 `src/main/resources/api-docs/{service}/`——
   中间依赖一次手动拷贝，容易漏做导致两边不一致。

另有一个更完整的生成器 `/Users/breannalinlin/.openclaw/workspace/skills/enterprise-api/_generate.py`
（OpenClaw skill 使用，非本仓库文件），会直接从网关实时拉取 swagger 并生成 Tier-1/Tier-2
两层文档。本设计**不复用它的输出格式**（`ApiCatalog` 的平铺解析器不认识 Tier-1/Tier-2
结构），只借用它"实时拉取 swagger"这一步的做法。

## 目标

让"企业服务接口变化后，`mcp-tool` 的接口文档如何更新"这件事变成两条命令，
不再需要手动导出 JSON、手动拷贝文件。

## 非目标

- 不支持 EAM 服务（`ApiCatalog`、`ChatController.listServices()`、白名单均未覆盖 EAM，
  超出本次范围）。
- 不迁移 `patch-descriptions.py`（读取 CRM 兄弟仓库本地 Java 源码补充字段描述）到自动化
  流程中——它依赖本机特定路径下检出的另一个仓库，继续作为可选的手动步骤保留原样。
- 不生成"适用问题示例"之外的新内容，`convert-api-docs.py` 现有的生成逻辑（含
  `enrich_description` 的人工覆盖表和 `generate_examples` 的模式匹配）原样保留。
- 不清理 `docs/{service}.md` 下已存在的旧副本文件（它们会在本次改动后变为过期状态，
  留待用户之后自行决定是否删除）。

## 设计

### 新增：`scripts/fetch-swagger.py`

对以下 5 个服务依次发起 `GET {GATEWAY}/{service}-service/v3/api-docs`：

| 服务代码 | 网关路径前缀 | 输出 JSON 文件 |
|---|---|---|
| crm | `/crm-service` | `api-docs/crm-service-api-docs.json` |
| mes | `/mes-service` | `api-docs/mes-api-docs.json` |
| wms | `/wms-service` | `api-docs/wms-service-api-docs.json` |
| qms | `/qms-service` | `api-docs/qms-service-api-docs.json` |
| srm | `/srm-service` | `api-docs/srm-service-api-docs.json` |

- `GATEWAY = "http://192.168.3.234:8888"`，与 `_generate.py` 一致，脚本内硬编码为
  一个常量（单点维护，网关地址变化时只改一处）。
- 认证 token 从环境变量 `X_ACCESS_TOKEN` 读取（与 OpenClaw skill 文档要求用户
  export 的变量名保持一致，减少认知负担）；未设置时脚本立即报错退出，不发起任何请求。
- 请求成功即用返回的 JSON **覆盖写入**对应文件——不做本地缓存（`_generate.py` 的
  `/tmp/swagger-*.json` 缓存是为了避免重复拉取浪费流量，但本脚本的目的就是"每次都要
  拿到最新的"，缓存与目标矛盾）。
- 单个服务请求失败（网络错误、404、401 等）不中断整体流程：记录失败原因，继续处理
  剩余服务，最后打印一份"成功/失败"汇总到 stderr，并以非零退出码提示"至少一个服务
  刷新失败"。这样某个服务的网关路由临时出问题时，不会连累其余四个服务的刷新。

### 修改：`scripts/convert-api-docs.py`

- `main()` 的输出路径从 `f'{base_dir}/docs/{md_file}'` 改为
  `f'{base_dir}/src/main/resources/api-docs/{service_dir}/{md_file}'`，`service_dir`
  为 `crm/mes/wms/qms/srm`（与现有 `src/main/resources/api-docs/` 下的子目录名一致）。
  不再写入 `docs/` 目录。
- `base_dir` 从硬编码的绝对路径 `/Users/breannalinlin/Documents/code/jowo/mcp-tool`
  改为基于脚本自身文件位置推导（`Path(__file__).resolve().parent.parent`），不再绑定
  到某一台机器的具体路径。
- 转换逻辑本身（字段解析、`enrich_description` 覆盖表、`generate_examples` 生成
  "适用问题示例"）不改动。

### 使用流程

```bash
export X_ACCESS_TOKEN='真实的网关 token'
python3 scripts/fetch-swagger.py       # 刷新 api-docs/*.json（实时拉取 swagger）
python3 scripts/convert-api-docs.py    # 重新生成 src/main/resources/api-docs/*/*.md
mvn -Dtest=ApiCatalogTest test         # 确认新文档仍能被 ApiCatalog 正常解析
```

改完文档后需要重新 `mvn package`（或重启 `mvn spring-boot:run`）才会把新文档打进
运行时用的 classpath / jar——文档不是运行时热加载的配置。

写进 `scripts/README.md`，避免流程被遗忘。

## 测试

- `fetch-swagger.py`：不写自动化测试（纯 I/O 脚本，行为等价于 curl，用真实网关手动
  验证一次即可，与 `_generate.py` 现有的零测试覆盖precedent一致）。
- `convert-api-docs.py` 的输出路径改动：跑一次 `python3 scripts/convert-api-docs.py`
  后，用现有的 `ApiCatalogTest`（已经解析真实 bundled 文档，断言精确的接口计数）作为
  回归验证——如果转换逻辑或输出路径出错，这个测试会先失败。

## 文档

新增 `scripts/README.md`，内容为上面的"使用流程"一节，供以后忘记步骤时查阅。
