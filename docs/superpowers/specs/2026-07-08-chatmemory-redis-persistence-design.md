# ChatMemory Redis 持久化设计

## 背景

`ChatMemory`（`src/main/java/com/jowo/agent/service/ChatMemory.java`）目前是一个纯内存的
`ConcurrentHashMap<String, List<LlmClient.Message>>`，按 `ownerId`（对话/conversationId）
存对话历史，每个 owner 最多保留最近 20 条消息，且在按数量截断时会跳过开头孤立的
`"tool"` 角色消息（避免留下一条没有前置 `assistant` 工具调用消息的无效 `tool` 消息）。
应用重启或重新部署会导致所有对话历史丢失。

这是子项目二（子项目一是 Nacos 配置中心接入，已完成）：把这份历史持久化到 Redis，
不再受进程重启影响。子项目一已经把 `dev` profile 的 Redis 连接信息放进了 Nacos
管理的 `l7-skill-backend-dev.yaml`（`spring.data.redis.host/port/password/database/
lettuce.pool.*`，host `${TEST_REDIS_HOST}`，port `6379`），本设计直接复用这份基础设施。

## 目标

- 把 `ChatMemory` 的存储从内存换成 Redis，重启/重新部署后对话历史不丢失。
- 完全保留现有行为：每个 owner 最多 20 条消息、按数量截断时跳过开头孤立的 `tool`
  消息、owner 之间相互隔离、不设 TTL（一直保留，直到被 20 条上限自然截断）。
- `local` profile 也要求连接真实可达的 Redis（跟 `dev` 共用同一台 `${TEST_REDIS_HOST}:6379`），
  不引入内存/Redis 双实现分支。
- 测试连真实 Redis（同一台机器），但用独立的 database index（`1`）隔离，避免
  污染 `dev`/`local` 使用的真实对话数据（database `0`）。

## 非目标

- 不做内存/Redis 双实现——`ChatMemory` 只有一套基于 Redis 的实现，`local` 也依赖
  真实 Redis，没有"本地无 Redis 时退回内存"的分支逻辑。
- 不用 Redis 原生 List（`RPUSH`/`LTRIM`）存储——"按数量截断后跳过开头孤立 tool
  消息"这个不变量很难用纯 `LTRIM` 表达，需要额外写 Lua 脚本判断截断后的表头，
  复杂度和收益不成比例。改用单个 String key 存整段 JSON 序列化的历史。
- 不加 `WATCH`/`MULTI`/Lua 之类的原子性保证——单实例部署 + 每个对话在实践中是
  单用户顺序使用，并发写同一个 `ownerId` 的概率可以忽略；用普通的
  `GET` 再 `SET`（跟现在内存版 `compute()` 的行为等价，只是从进程内操作变成
  两次网络往返），足够用。
- `local` 和 `dev` 共用同一个 database index（`0`），不做环境间隔离——因为
  `ownerId` 在实际使用中是随机生成的 conversationId，两个环境撞 key 的概率
  可以忽略；只有测试用的固定 ID（如 `"conv-1"`）才需要专门隔离，所以只有
  测试单独换 database index。
- 不解决"多个开发者同时跑测试可能撞同一个固定 conversation ID"的边界情况——
  测试数据库是共享基础设施而非每次运行独占的实例，理论上存在这个碰撞窗口，
  但团队规模小、现有测试本来就用固定 ID，暂不为此增加随机前缀之类的复杂度，
  这是权衡后的有意选择而非疏漏。

## 设计

### 依赖

新增 `org.springframework.boot:spring-boot-starter-data-redis` +
`org.apache.commons:commons-pool2`（Lettuce 连接池用），与 `crm-service` 的
`pom.xml` 中对应依赖写法一致。

### 配置

- `dev`：已在 Nacos 的 `l7-skill-backend-dev.yaml` 中配置好，本设计不改动。
- `local`：在 `src/main/resources/application-local.yml` 新增一段，连到同一台
  共享 Redis（`${TEST_REDIS_HOST}:6379`，与 `dev` 相同的真实密码），database 用 `0`：
  ```yaml
  spring:
    data:
      redis:
        host: ${TEST_REDIS_HOST}
        port: 6379
        password: ${TEST_REDIS_PASSWORD}
        database: 0
        lettuce:
          pool:
            max-active: 8
            max-idle: 8
            min-idle: 0
            max-wait: -1ms
  ```

### `ChatMemory` 重写

保持现有公开接口不变（`get(String ownerId): List<Message>`、
`append(String ownerId, List<Message> newMessages): void`），内部存储从
`ConcurrentHashMap` 换成 `StringRedisTemplate` + Jackson `ObjectMapper`
（`LlmClient.Message`/`ToolCall` 都是简单 record，Jackson 2.12+ 原生支持
record 反序列化，不需要额外注解）。

- key 格式：`chatmemory:{ownerId}`。
- `get(ownerId)`：`GET` 该 key；不存在则返回空列表；存在则反序列化 JSON 为
  `List<Message>`。
- `append(ownerId, newMessages)`：先 `GET` 现有历史（不存在视为空列表），在
  Java 里按现有逻辑合并（`ArrayList` 拼接）+ 截断（保留最后 20 条，若截断点
  落在孤立 `tool` 消息前则继续前移，跳过开头孤立的 `tool` 消息——与现有
  `ChatMemory.append` 的截断算法完全一致，照搬即可），再把合并后的结果整体
  序列化为 JSON，`SET` 回同一个 key（不设置 `EXPIRE`）。
- 不加锁、不用事务：`GET` 和 `SET` 是两次独立的 Redis 往返，理论上存在竞态
  窗口（两个并发请求几乎同时 `append` 同一个 `ownerId` 时，后写入的会覆盖先
  写入的），但如「非目标」所述，这是有意接受的权衡。

### 测试

`ChatMemoryTest` 保持纯单元测试风格（不用 `@SpringBootTest`，不新建
`src/test/resources/application.yml` 之类的文件——子项目一里已经踩过这个坑：
测试专属的 `application.yml` 会在 classpath 上完全遮盖
`src/main/resources/application.yml`，而不是与之合并）。测试类自己在
`@BeforeEach`（或构造函数）里手动创建一个指向共享 Redis、database `1` 的
`LettuceConnectionFactory` + `StringRedisTemplate`，直接
`new ChatMemory(thatTemplate)`，不经过 Spring 容器。每个测试在 `@AfterEach`
里删除自己写入的 key（不做整库 `FLUSHDB`，避免误删同时在跑的同事的测试数据）。

## 文档

无新增文档；本设计文档本身即为该功能的记录。
