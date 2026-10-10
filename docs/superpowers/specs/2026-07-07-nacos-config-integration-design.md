# Nacos 配置中心接入设计

## 背景

项目当前所有配置（DeepSeek key、企业 API token、Portainer key 等）分散在本地
`application.yml` / `application-dev.yml` / `application-local.yml` 里，其中
`application-dev.yml` 的敏感信息（openai/deepseek key、企业 token、portainer key）
以明文提交进了 git。公司其他微服务（如 mes-server）已经统一接入 Nacos 作为配置中心。
本项目即将新增 ChatMemory 的 Redis 持久化（另见后续设计），需要的 Redis 连接信息计划
直接放进 Nacos 管理，借此机会把整个项目的配置管理方式向 Nacos 迁移，与其他服务保持一致。

## 目标

- `dev`（以及未来的生产）profile 启动时从 Nacos 拉取配置，不再依赖本地明文 yml。
- `local` profile（开发者本机）保持现状，离线可跑，不依赖内网 Nacos。
- `spring.application.name` 对外显示为 `l7-skill-backend`（跟 Nacos/其他服务的命名习惯
  对齐），不改动 Maven `artifactId`/jar 命名。
- 顺带把以后要用的 MySQL 连接基础设施（驱动依赖 + 本地连接配置）提前搭好，本地开发
  不因此被破坏（见「非目标」——不写业务代码，也不能让没有本地 MySQL 的开发者启动失败）。

## 非目标

- 不改动 `pom.xml` 的 `artifactId`/jar 文件名（仍是 `agent-0.0.1-SNAPSHOT.jar`），
  Dockerfile 无需变动。
- 不实现 ChatMemory 的 Redis 持久化本身——这是独立的后续设计（子项目二），本设计只保证
  它需要的 Redis 连接信息将来能从 Nacos 读到。
- 不确定生产环境的 profile 名字与对应的 Nacos 命名空间/data-id——留空，等用户确定后按
  同样模式（`jowo-im-${profile}` 命名空间 + `l7-skill-backend-${profile}.yaml` data-id +
  `DEFAULT_GROUP`）在 Nacos 控制台补一条配置即可，无需改代码。
- 不清理 git 历史中已经提交过的旧明文密钥（openai key、企业 token、portainer key）——
  仅从当前工作区移除引用，历史提交里仍能翻到，是否需要轮换密钥由用户自行评估。
- 不实现真正的 MySQL 业务功能（entity/repository/service）——本设计只提前搭好本地能
  连通 MySQL 的基础设施（依赖 + `application-local.yml` 连接配置），供以后的功能开发
  直接使用，这次不写任何用到它的代码。
- 不引入 R2DBC——本项目是 `spring-boot-starter-web`（Servlet/Tomcat，阻塞式），只有两个
  聊天接口用 `Flux<String>` 做 SSE 流式响应，整体并非 WebFlux 应用；数据库访问按同类
  服务的惯例走传统 JDBC + HikariCP 连接池即可，R2DBC 在这里没有实际收益，反而引入不
  匹配的编程模型（响应式事务管理、与 Spring Data JPA 不兼容等）。

## 设计

### 依赖

新增 `com.alibaba.cloud:spring-cloud-starter-alibaba-nacos-config`——**只要 config，不要
`spring-cloud-starter-alibaba-nacos-discovery`**（用户明确不需要服务注册/发现，嫌它慢；
这个项目也不需要被别的服务通过 Nacos 发现）。版本号直接参照同样跑在 Spring Boot 3.5.x
上、已经在生产验证过的 `crm-service` 的 `pom.xml`（`/Users/breannalinlin/Documents/code/jowo/crm-service/pom.xml`），
而不是本设计早前查到的网上版本号（那个是错的，`spring-cloud-alibaba-dependencies` 实际
应该是 2025.0.0.0，不是 2025.1.0.0）：

- `spring-cloud-dependencies` 2025.0.0
- `spring-cloud-alibaba-dependencies` 2025.0.0.0

两者都以 `<dependencyManagement>` + `<scope>import</scope>` 的方式导入，与
`spring-boot-starter-parent` 3.5.0 一起用。

另外为以后的 MySQL 功能提前加两个依赖（这次不写任何使用它们的业务代码，只保证本地能
连通），同样对齐 `crm-service` 的写法：

- `spring-boot-starter-jdbc`（提供 `spring-jdbc` + HikariCP，`DataSourceAutoConfiguration`
  需要 `spring-jdbc` 里的 `EmbeddedDatabaseType` 才会激活，缺了它光加驱动是不会生效的）
- `com.mysql:mysql-connector-j`（注意 groupId 是 `com.mysql`，不是旧版的 `mysql`），
  `scope: runtime`

### 配置结构

在 `application.yml` 中新增一段仅 `dev` profile 生效的配置文档（用 `---` 分隔 +
`spring.config.activate.on-profile: dev`），`local` profile 不受影响，继续吃
`application-local.yml`：

```yaml
spring:
  application:
    name: l7-skill-backend
---
spring:
  config:
    activate:
      on-profile: dev
  cloud:
    nacos:
      config:
        server-addr: redacted-host.invalid:8848
        namespace: jowo-im-${spring.profiles.active}
        group: DEFAULT_GROUP
  config:
    import:
      - nacos:${spring.application.name}-${spring.profiles.active}.yaml?group=${spring.cloud.nacos.config.group}&refreshEnabled=true
```

`dev` profile 下解析结果：命名空间 `jowo-im-dev`，group `DEFAULT_GROUP`，
data-id `l7-skill-backend-dev.yaml`——与用户在 Nacos 控制台核实到的一致。

### Nacos 数据准备（已完成）

用户已在 Nacos 控制台（`redacted-host.invalid:8848`）的命名空间 `jowo-im-dev`、分组
`DEFAULT_GROUP` 下创建好 data-id 为 `l7-skill-backend-dev.yaml` 的配置，内容覆盖了
`application-dev.yml` 原有的全部键，外加 MySQL（`spring.datasource.*`）、RabbitMQ、
Redis（`REDIS_PASSWORD` 走环境变量、带默认值）、MinIO、`smartlink`、`jowo.*`、
`enterprise-api.*` 等完整配置。不再是本设计的待办项。

需要用户在实现阶段回 Nacos 控制台修正一处：`logging.level` 下的 key 当前写的是
`"com.jowo.l7-skill-backend"`，但本项目实际 Java 包名是 `com.jowo.agent`（这次只改
`spring.application.name`，不改包名，见「非目标」），现在这个 key 匹配不到任何包，
日志级别设置不会生效。应改成 `"com.jowo.agent": DEBUG`。

### 现有文件的处理

- `application-dev.yml`：迁移完成并验证 Nacos 拉取成功后，从仓库中移除这个文件里
  已经搬到 Nacos 的敏感键（如果其中还有专属本机调试、不适合放 Nacos 的键，实现阶段
  酌情保留）。
- `application-local.yml`：不接 Nacos，继续离线。新增 MySQL 连接配置（本地默认值，
  不含 RabbitMQ——项目暂不用它）。额外加一个用户给的原始模板里没有的
  `hikari.initialization-fail-timeout: -1`：因为 `spring-boot-starter-jdbc` +
  `mysql-connector-j` 一旦真的在 classpath 上，`DataSourceAutoConfiguration` 会
  激活，HikariCP 默认在启动时就尝试建立真实连接（受 `minimum-idle` 影响）失败即
  fail-fast——但目前没有任何代码用到这个 DataSource，也没人本地起 MySQL，不该让
  没做 MySQL 功能的开发者的 `mvn spring-boot:run` 因此启动失败。这个值只加在
  `local`，不动 `dev`（Nacos 上那份 dev 配置对应真实可达的 MySQL，不需要这个例外）：
  ```yaml
  spring:
    datasource:
      url: jdbc:mysql://${JDBC_HOST:127.0.0.1}:${JDBC_PORT:3306}/db_jowo_im?allowPublicKeyRetrieval=true&useSSL=false&useLegacyDatetimeCode=false&serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=UTF-8
      username: ${JDBC_USER:root}
      password: ${JDBC_PASS:123456}
      driver-class-name: com.mysql.cj.jdbc.Driver
      hikari:
        connection-timeout: 20000
        minimum-idle: 5
        maximum-pool-size: 10
        idle-timeout: 10000
        initialization-fail-timeout: -1
  ```
- `application.yml`：新增上面的 `spring.application.name` 顶层配置和 dev-profile
  专属文档块。

## 测试

- 在有内网访问权限的机器上，用 `-Dspring.profiles.active=dev` 跑
  `mvn spring-boot:run`（或打包后 `java -jar ... --spring.profiles.active=dev`），
  确认：
  - 应用能正常启动（Nacos 配置拉取失败时 Spring Boot 默认 fail-fast，启动失败即说明
    连接地址、命名空间或 data-id 有问题）。
  - 临时改一下 Nacos 上某个已知配置项的值，观察应用行为随之变化，确认真的读到了
    Nacos 而不是本地文件的旧值。
- `local` profile 回归：在**不运行本地 MySQL** 的机器上确认应用仍能正常启动（验证
  `initialization-fail-timeout: -1` 生效，没有因为新加的 datasource 配置导致启动失败），
  且不带任何 Nacos 相关配置也能照常启动，验证 profile 隔离没有破坏离线开发路径。
- 现有单测（如 `ChatMemoryTest` 等）不受影响，这次改动只涉及配置装配，不涉及业务代码。

## 文档

无新增文档；本设计文档本身即为该配置迁移的记录。
