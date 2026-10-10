# Phase 2 Task 6 报告

**STATUS: PASS（Compose 配置通过解析；因本机 Docker daemon 未运行，未实际启动容器）**

## 摘要

- 新增 `application-local-mock.yml`：默认 endpoint/client/callback/claims/roles 对齐本地 mock，支持通过环境变量替换，不含生产 secret。
- 新增跨模块真实 HTTP E2E：随机 loopback ports 上启动两个 Spring Boot context；覆盖 authorize/state-cookie、tenant user chooser、callback/token/userinfo/session、fragment app token、`/api/auth/me`、过期 identity refresh、logout 与真实 refresh-token revoke。
- E2E 使用独立随机端口的临时 Redis（DB 15）和随机 H2 内存库，测试前后 flush Redis、关闭两个 context、停止 Redis 子进程并清理临时目录。
- mock 模块通过 `mock.oauth2.enabled=true` 显式激活；可执行 JAR main 默认开启，mock 单测及 E2E 显式开启。server 对 mock 的依赖仅为 test scope，server 生产代码无 mock 引用。
- Compose `local-mock` profile opt-in 启动 mock、server、MySQL、Redis；默认 Compose 仍只选择 server。README 描述了打包、启动、五个中性 seed users、登录流程、IdP 替换点和仅供开发/隔离测试的边界。

## RED

先新增 E2E，再运行简报指定命令：

```sh
mvn -pl agent-server -am -Dtest=MockOAuth2EndToEndTest test
```

初始结果：失败在 mock chooser，预期 HTTP 200、实际 400。尚未添加 local mock profile 时，server authorize URL 没有默认 client ID，mock 按预期拒绝请求；这确认了测试覆盖 profile wiring 的缺失行为。

## GREEN

- `mvn -pl agent-server -am -Dtest=MockOAuth2EndToEndTest test`：1 个 E2E 测试通过。
- `mvn -pl agent-server -am -Dtest=MockOAuth2EndToEndTest,OpenSourceBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false test`：4 个目标测试通过。
- 最终 `mvn clean test`：234 tests，0 failures、0 errors、0 skipped，BUILD SUCCESS。
- 实现过程中一次全量运行发现两处测试问题并已修复：Compose 边界断言仍要求旧的列表式环境变量格式；直接测试 revoke 的 refresh 请求漏带测试 client secret，得到 `invalid_client` 而非目标 `invalid_grant`。

## Package / Compose 验证

```sh
mvn -pl agent-server,mock-oauth2 -am -DskipTests package
```

BUILD SUCCESS，生成：

- `agent-server/target/agile-agent-server-0.0.1-SNAPSHOT.jar`
- `mock-oauth2/target/agile-agent-mock-oauth2-0.0.1-SNAPSHOT-exec.jar`

两个 JAR manifest 均使用 Spring Boot `JarLauncher`；server Start-Class 为 `AgentApplication`，mock Start-Class 为 `MockOAuth2Application`。两个最新 package JAR 均以 `java -jar` 在 loopback 上启动成功并 graceful shutdown。server smoke 使用不可达的 dummy DB/Redis 地址并关闭 Flyway，没有连接外部系统。server fat JAR 不包含 mock 类或 mock runtime library。

- `docker compose -f docker/docker-compose.yml config --services`：仅 `agile-agent-server`。
- `docker compose -f docker/docker-compose.yml --profile local-mock config --services`：包含 mock、local-mock server、MySQL、Redis。
- `docker compose -f docker/docker-compose.yml --profile local-mock config --format json | jq -r '.services["agile-agent-mock-oauth2"].environment.SERVER_ADDRESS'`：`0.0.0.0`，仅在 opt-in profile。
- `docker info --format '{{.ServerVersion}}'` 失败：当前环境没有 `~/.docker/run/docker.sock`，所以未执行实际容器 `up`；Compose 文件解析与 profile 展开已验证。

## 自检 / concerns

- `git diff --check` 通过；`AgentApplication.java` 无差异，server production source 不引用 mock；依赖为 test scope。
- E2E 断言 subject、display name、tenant ID/name、primary org ID/name、roles、readable/manageable scopes；过期缓存被真实 userinfo 覆盖，且伪造 platform/tenant admin privilege 未保留。回调攻击者 `redirect_uri`/`next` 参数不能改变 frontend redirect；错误 state cookie 不创建 session 且不消费 PKCE verifier。
- logout 后 app token 返回 401；同一 provider refresh token 直接提交到 mock token endpoint 返回 `invalid_grant`。测试从 Redis 仅内存读取 refresh token，不输出 token 内容。
- 本机完整套件所需 `redis-server` 可用；新增 E2E 要求运行测试的开发机安装 `redis-server`，README 已说明。
- Compose 实际容器启动尚未验证，原因仅为 Docker daemon 不可用；可在 Docker daemon 可用的机器按 README 命令启动验证。

## Commit

`feat: integrate the configurable mock OAuth2 identity flow`（单一 Task 6 commit；完整 SHA 在最终交接中给出。）
