# 知识库文档上传审核链路 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现一条完整的知识库文档上传审核链路——业务人员上传文本/Markdown 文档、系统用 LLM 自动生成摘要、审核人员通过 list/approve/reject 接口审核、通过后落库到 `./lib` 目录，形成可独立验收的 REST API 模块。

**Architecture:** 新建独立包 `com.jowo.agent.knowledge`，按"数据层 → 文件层 → LLM 能力 → 业务编排 → HTTP 层 → 端到端验证"的顺序自底向上实现：先落 R2DBC 仓储和 Flyway 迁移（Task 1），再落文件系统读写（Task 2），再给 `LlmClient` 加一个摘要生成便捷方法（Task 3），然后是编排状态机的 Service（Task 4），最后是 WebFlux Controller（Task 5）和一次全链路集成测试（Task 6）。每个 Task 结束时全量测试通过、可独立 commit。

**Tech Stack:** Spring Boot 3.5 / WebFlux / R2DBC (DatabaseClient) / Flyway / Reactor (Mono/Flux) / Jackson / JUnit 5 + Mockito + WebTestClient + StepVerifier

## Global Constraints

- JDK 21，Spring Boot 3.5.0，`spring-cloud-dependencies` 2025.0.0（不升级，不改 `pom.xml` 依赖）。
- 设计规范：`docs/superpowers/specs/2026-07-27-knowledge-base-upload-review-design.md`——每个 Task 开始前重读一遍对应章节。
- 测试命令统一 `mvn -q test` / 单类 `mvn -q -Dtest=<ClassName> test`。
- 两类测试各自的数据库/Redis 依赖方式不同，不要混用：
  - `KnowledgeDocumentRepositoryTest`（纯仓储测试）：仿照现有 `SessionRepositoryTest` 的模式，每个测试方法在 `@BeforeEach` 里用唯一名字建一个全新的 H2 内存库，`Flyway.configure().dataSource(...).load().migrate()` 跑一遍 classpath 上的全部迁移脚本，再手工用 `ConnectionFactoryOptions.builder()` 建一个指向同一个库的 R2DBC `DatabaseClient`。不连任何外部数据库。
  - `KnowledgeControllerIntegrationTest`（`@SpringBootTest` 全量上下文）：复用应用默认的自动配置——R2DBC 连接工厂由 Spring Boot 在测试期自动装配一个内嵌 H2（因为 `spring-boot-starter-data-r2dbc` 在类路径且未显式配置 `spring.r2dbc.url` 时会这样做，已在 Phase 1 验证过），Redis 用默认的 `spring.data.redis.*`（本机 `localhost:6379`，无密码，database 0——已确认本环境有一个监听在该地址的 Redis 实例）。
- 每个 Task 结束跑一次 `mvn -q test` 确认全绿再 commit；commit message 格式 `feat|test: <描述>`，结尾加：
  ```
  Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
  ```
- 新建的类一律不加 `public`（除非确有跨包引用需要）——沿用本项目 `UploadController`/`ChatController`/`FileStorageService` 的包内可见性风格；`KnowledgeDocumentRepository`/`KnowledgeFileStorage`/`KnowledgeDocumentService` 需要跨 `controller`/`knowledge` 包被 Spring 扫描注入，保持 `public`（与 `SessionRepository`/`FileStorageService` 现有风格一致）。
- 不改动：`ApiCatalog`、`AgentService`、现有的 `UploadController`/`FileStorageService`（聊天附件上传路径完全不碰）、`docs/goal/2026-07-15-reactive-multi-agent-design.md` 里的域 agent/Orchestrator 设计（那是子项目 2，不在这次范围内）。

## 文件结构总览

| 文件 | 动作 | 职责 |
|---|---|---|
| `src/main/resources/db/migration/V2__create_knowledge_documents_table.sql` | Create | 新表 `knowledge_documents` |
| `src/main/java/com/jowo/agent/knowledge/KnowledgeDocumentRepository.java` | Create | R2DBC 仓储 + `KnowledgeDocumentRow` 记录 + `Status` 枚举 |
| `src/main/java/com/jowo/agent/config/KnowledgeProperties.java` | Create | `app.knowledge.*` 配置绑定 |
| `src/main/java/com/jowo/agent/knowledge/KnowledgeFileStorage.java` | Create | 临时目录写入 + `./lib` 落库 |
| `src/main/java/com/jowo/agent/service/LlmClient.java` | Modify | 新增 `summarize(String): Mono<String>` 默认方法 |
| `src/main/java/com/jowo/agent/knowledge/KnowledgeDocumentService.java` | Create | 状态机编排：upload/list/get/approve/reject |
| `src/main/java/com/jowo/agent/knowledge/KnowledgeController.java` | Create | 5 个 REST 端点 |
| `src/main/resources/application.yml` | Modify | 新增 `app.knowledge.*` 配置块 |
| `src/test/java/com/jowo/agent/knowledge/KnowledgeDocumentRepositoryTest.java` | Create | 仓储层测试 |
| `src/test/java/com/jowo/agent/knowledge/KnowledgeFileStorageTest.java` | Create | 文件层测试 |
| `src/test/java/com/jowo/agent/service/LlmClientSummarizeTest.java` | Create | `summarize()` 默认方法测试 |
| `src/test/java/com/jowo/agent/knowledge/KnowledgeDocumentServiceTest.java` | Create | 业务编排测试 |
| `src/test/java/com/jowo/agent/knowledge/KnowledgeControllerTest.java` | Create | `@WebFluxTest` 切片测试 |
| `src/test/java/com/jowo/agent/knowledge/KnowledgeControllerIntegrationTest.java` | Create | `@SpringBootTest` 全链路测试 |

---

### Task 1: Flyway 迁移 + KnowledgeDocumentRepository

**Files:**
- Create: `src/main/resources/db/migration/V2__create_knowledge_documents_table.sql`
- Create: `src/main/java/com/jowo/agent/knowledge/KnowledgeDocumentRepository.java`
- Test: `src/test/java/com/jowo/agent/knowledge/KnowledgeDocumentRepositoryTest.java`

**Interfaces:**
- Produces:
  - `KnowledgeDocumentRepository(DatabaseClient db)` 构造器。
  - `insert(KnowledgeDocumentRow row): Mono<Void>`
  - `findById(String id): Mono<KnowledgeDocumentRow>`（无记录 → empty）
  - `findByStatus(KnowledgeDocumentRow.Status status): Flux<KnowledgeDocumentRow>`（按 `uploaded_at DESC` 排序）
  - `approveIfPending(String id, String summary, String reviewedByUserId, LocalDateTime reviewedAt): Mono<Long>`（条件更新，只在 `status='PENDING'` 时生效，返回受影响行数）
  - `rejectIfPending(String id, String reviewedByUserId, LocalDateTime reviewedAt): Mono<Long>`（同上，不改 summary）
  - `record KnowledgeDocumentRow(String id, String title, String originalFilename, String tempPath, Status status, String summary, String uploaderUserId, LocalDateTime uploadedAt, String reviewedByUserId, LocalDateTime reviewedAt)`，嵌套 `enum Status { PENDING, APPROVED, REJECTED }`——后续所有 Task 引用这两个类型都写全限定名 `KnowledgeDocumentRepository.KnowledgeDocumentRow` / `KnowledgeDocumentRepository.KnowledgeDocumentRow.Status`。

- [ ] **Step 1: 写失败测试**

```java
package com.jowo.agent.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.r2dbc.core.DatabaseClient;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.Option;

import com.jowo.agent.knowledge.KnowledgeDocumentRepository.KnowledgeDocumentRow;
import com.jowo.agent.knowledge.KnowledgeDocumentRepository.KnowledgeDocumentRow.Status;

class KnowledgeDocumentRepositoryTest {

    private KnowledgeDocumentRepository repository;

    @BeforeEach
    void setUp() {
        var dbName = "knowledge-repo-test-" + System.nanoTime();
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + dbName + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        dataSource.setDriverClassName("org.h2.Driver");
        Flyway.configure().dataSource(dataSource).load().migrate();
        var options = ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, "h2")
                .option(ConnectionFactoryOptions.PROTOCOL, "mem")
                .option(ConnectionFactoryOptions.DATABASE, dbName)
                .option(ConnectionFactoryOptions.USER, "sa")
                .option(ConnectionFactoryOptions.PASSWORD, "")
                .option(Option.valueOf("options"), "MODE=MySQL;DB_CLOSE_DELAY=-1")
                .build();
        var connectionFactory = ConnectionFactories.get(options);
        repository = new KnowledgeDocumentRepository(DatabaseClient.create(connectionFactory));
    }

    private static KnowledgeDocumentRow newRow(String id, Status status) {
        return new KnowledgeDocumentRow(id, "标题", "original.md", "/tmp/" + id + ".md",
                status, "摘要", "user-1", LocalDateTime.now(), null, null);
    }

    @Test
    void findByIdOnUnknownIdReturnsEmpty() {
        assertThat(repository.findById("no-such-id").blockOptional()).isEmpty();
    }

    @Test
    void insertThenFindByIdReturnsRow() {
        repository.insert(newRow("k1", Status.PENDING)).block();

        var row = repository.findById("k1").blockOptional();

        assertThat(row).isPresent();
        assertThat(row.get().id()).isEqualTo("k1");
        assertThat(row.get().title()).isEqualTo("标题");
        assertThat(row.get().originalFilename()).isEqualTo("original.md");
        assertThat(row.get().status()).isEqualTo(Status.PENDING);
        assertThat(row.get().summary()).isEqualTo("摘要");
        assertThat(row.get().uploaderUserId()).isEqualTo("user-1");
        assertThat(row.get().reviewedByUserId()).isNull();
        assertThat(row.get().reviewedAt()).isNull();
    }

    @Test
    void findByStatusReturnsOnlyMatchingRowsNewestFirst() throws InterruptedException {
        repository.insert(newRow("k1", Status.PENDING)).block();
        Thread.sleep(10);
        repository.insert(newRow("k2", Status.PENDING)).block();
        repository.insert(newRow("k3", Status.APPROVED)).block();

        var rows = repository.findByStatus(Status.PENDING).collectList().block();

        assertThat(rows).extracting(KnowledgeDocumentRow::id).containsExactly("k2", "k1");
    }

    @Test
    void approveIfPendingUpdatesRowAndReturnsOneWhenPending() {
        repository.insert(newRow("k1", Status.PENDING)).block();

        var updated = repository.approveIfPending("k1", "新摘要", "admin-1", LocalDateTime.now()).block();

        assertThat(updated).isEqualTo(1L);
        var row = repository.findById("k1").block();
        assertThat(row.status()).isEqualTo(Status.APPROVED);
        assertThat(row.summary()).isEqualTo("新摘要");
        assertThat(row.reviewedByUserId()).isEqualTo("admin-1");
        assertThat(row.reviewedAt()).isNotNull();
    }

    @Test
    void approveIfPendingReturnsZeroWhenAlreadyApproved() {
        repository.insert(newRow("k1", Status.APPROVED)).block();

        var updated = repository.approveIfPending("k1", "新摘要", "admin-1", LocalDateTime.now()).block();

        assertThat(updated).isZero();
    }

    @Test
    void rejectIfPendingUpdatesRowAndReturnsOneWhenPending() {
        repository.insert(newRow("k1", Status.PENDING)).block();

        var updated = repository.rejectIfPending("k1", "admin-1", LocalDateTime.now()).block();

        assertThat(updated).isEqualTo(1L);
        var row = repository.findById("k1").block();
        assertThat(row.status()).isEqualTo(Status.REJECTED);
        assertThat(row.reviewedByUserId()).isEqualTo("admin-1");
    }

    @Test
    void rejectIfPendingReturnsZeroWhenAlreadyRejected() {
        repository.insert(newRow("k1", Status.REJECTED)).block();

        var updated = repository.rejectIfPending("k1", "admin-1", LocalDateTime.now()).block();

        assertThat(updated).isZero();
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn -q -Dtest=KnowledgeDocumentRepositoryTest test`
Expected: COMPILATION ERROR（`KnowledgeDocumentRepository`、`V2` 迁移都还不存在）

- [ ] **Step 3: 实现**

`V2__create_knowledge_documents_table.sql`：

```sql
CREATE TABLE knowledge_documents (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    temp_path VARCHAR(500) NOT NULL,
    status VARCHAR(16) NOT NULL,
    summary TEXT,
    uploader_user_id VARCHAR(64) NOT NULL,
    uploaded_at DATETIME(6) NOT NULL,
    reviewed_by_user_id VARCHAR(64),
    reviewed_at DATETIME(6)
);

CREATE INDEX idx_knowledge_documents_status ON knowledge_documents (status, uploaded_at);
```

`KnowledgeDocumentRepository.java`：

```java
package com.jowo.agent.knowledge;

import java.time.LocalDateTime;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;

import io.r2dbc.spi.Readable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class KnowledgeDocumentRepository {

    private final DatabaseClient db;

    public KnowledgeDocumentRepository(DatabaseClient db) {
        this.db = db;
    }

    public Mono<Void> insert(KnowledgeDocumentRow row) {
        return db.sql("""
                INSERT INTO knowledge_documents
                    (id, title, original_filename, temp_path, status, summary, uploader_user_id, uploaded_at)
                VALUES
                    (:id, :title, :originalFilename, :tempPath, :status, :summary, :uploaderUserId, :uploadedAt)
                """)
                .bind("id", row.id())
                .bind("title", row.title())
                .bind("originalFilename", row.originalFilename())
                .bind("tempPath", row.tempPath())
                .bind("status", row.status().name())
                .bind("summary", row.summary() == null ? "" : row.summary())
                .bind("uploaderUserId", row.uploaderUserId())
                .bind("uploadedAt", row.uploadedAt())
                .fetch().rowsUpdated().then();
    }

    public Mono<KnowledgeDocumentRow> findById(String id) {
        return db.sql("""
                SELECT id, title, original_filename, temp_path, status, summary,
                       uploader_user_id, uploaded_at, reviewed_by_user_id, reviewed_at
                FROM knowledge_documents WHERE id = :id
                """)
                .bind("id", id)
                .map(KnowledgeDocumentRepository::mapRow)
                .one();
    }

    public Flux<KnowledgeDocumentRow> findByStatus(KnowledgeDocumentRow.Status status) {
        return db.sql("""
                SELECT id, title, original_filename, temp_path, status, summary,
                       uploader_user_id, uploaded_at, reviewed_by_user_id, reviewed_at
                FROM knowledge_documents WHERE status = :status ORDER BY uploaded_at DESC
                """)
                .bind("status", status.name())
                .map(KnowledgeDocumentRepository::mapRow)
                .all();
    }

    public Mono<Long> approveIfPending(String id, String summary, String reviewedByUserId, LocalDateTime reviewedAt) {
        return db.sql("""
                UPDATE knowledge_documents
                SET status = 'APPROVED', summary = :summary, reviewed_by_user_id = :reviewedBy, reviewed_at = :reviewedAt
                WHERE id = :id AND status = 'PENDING'
                """)
                .bind("summary", summary == null ? "" : summary)
                .bind("reviewedBy", reviewedByUserId)
                .bind("reviewedAt", reviewedAt)
                .bind("id", id)
                .fetch().rowsUpdated();
    }

    public Mono<Long> rejectIfPending(String id, String reviewedByUserId, LocalDateTime reviewedAt) {
        return db.sql("""
                UPDATE knowledge_documents
                SET status = 'REJECTED', reviewed_by_user_id = :reviewedBy, reviewed_at = :reviewedAt
                WHERE id = :id AND status = 'PENDING'
                """)
                .bind("reviewedBy", reviewedByUserId)
                .bind("reviewedAt", reviewedAt)
                .bind("id", id)
                .fetch().rowsUpdated();
    }

    private static KnowledgeDocumentRow mapRow(Readable row) {
        return new KnowledgeDocumentRow(
                row.get("id", String.class),
                row.get("title", String.class),
                row.get("original_filename", String.class),
                row.get("temp_path", String.class),
                KnowledgeDocumentRow.Status.valueOf(row.get("status", String.class)),
                row.get("summary", String.class),
                row.get("uploader_user_id", String.class),
                row.get("uploaded_at", LocalDateTime.class),
                row.get("reviewed_by_user_id", String.class),
                row.get("reviewed_at", LocalDateTime.class));
    }

    public record KnowledgeDocumentRow(
            String id, String title, String originalFilename, String tempPath,
            Status status, String summary, String uploaderUserId, LocalDateTime uploadedAt,
            String reviewedByUserId, LocalDateTime reviewedAt) {

        public enum Status { PENDING, APPROVED, REJECTED }
    }
}
```

- [ ] **Step 4: 运行确认通过 + 全量回归**

Run: `mvn -q -Dtest=KnowledgeDocumentRepositoryTest test`
Expected: PASS（8 个测试全绿）

Run: `mvn -q test`
Expected: 全部 PASS（新迁移不影响任何现有测试——`SessionRepositoryTest` 自己建独立的 H2 库，`V2` 只是多了一张现有测试都不查询的新表）

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/migration/V2__create_knowledge_documents_table.sql \
        src/main/java/com/jowo/agent/knowledge/KnowledgeDocumentRepository.java \
        src/test/java/com/jowo/agent/knowledge/KnowledgeDocumentRepositoryTest.java
git commit -m "$(cat <<'EOF'
feat: add knowledge_documents table and R2DBC repository

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: KnowledgeProperties + KnowledgeFileStorage

**Files:**
- Create: `src/main/java/com/jowo/agent/config/KnowledgeProperties.java`
- Create: `src/main/java/com/jowo/agent/knowledge/KnowledgeFileStorage.java`
- Modify: `src/main/resources/application.yml`
- Test: `src/test/java/com/jowo/agent/knowledge/KnowledgeFileStorageTest.java`

**Interfaces:**
- Consumes: nothing from Task 1（本 Task 独立于仓储）。
- Produces:
  - `KnowledgeProperties`：`getTempDir()/setTempDir(String)`、`getLibDir()/setLibDir(String)`、`getMaxSizeMb()/setMaxSizeMb(int)`、`getMaxSizeBytes(): long`。
  - `KnowledgeFileStorage(KnowledgeProperties props)` 构造器。
  - `saveTemp(String id, FilePart part): Mono<UploadedFile>`，`record UploadedFile(String tempPath, String content)`。
  - `promoteToLib(String id, String tempPath, String title, String summary, String approvedBy, LocalDateTime approvedAt): Mono<Void>`。
  - `static class RejectedException extends RuntimeException`，带 `getCode(): String`。

- [ ] **Step 1: 写失败测试**

```java
package com.jowo.agent.knowledge;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;

import com.jowo.agent.config.KnowledgeProperties;

import reactor.core.publisher.Mono;

class KnowledgeFileStorageTest {

    @TempDir Path tempDir;
    @TempDir Path libDir;
    KnowledgeProperties props;
    KnowledgeFileStorage storage;

    @BeforeEach
    void setUp() {
        props = new KnowledgeProperties();
        props.setTempDir(tempDir.toString());
        props.setLibDir(libDir.toString());
        props.setMaxSizeMb(1);
        storage = new KnowledgeFileStorage(props);
    }

    private static FilePart mockFilePart(String filename, String contentType, byte[] bytes) {
        var part = mock(FilePart.class);
        when(part.filename()).thenReturn(filename);
        var headers = new HttpHeaders();
        if (contentType != null) headers.setContentType(MediaType.parseMediaType(contentType));
        when(part.headers()).thenReturn(headers);
        when(part.transferTo(any(Path.class))).thenAnswer(invocation -> {
            Path target = invocation.getArgument(0);
            Files.write(target, bytes);
            return Mono.empty();
        });
        return part;
    }

    @Test
    void savesMarkdownFileAndReturnsContent() {
        var part = mockFilePart("doc.md", "text/markdown",
                "# 标题\n正文内容".getBytes(StandardCharsets.UTF_8));

        var result = storage.saveTemp("k1", part).block();

        assertThat(result.tempPath()).endsWith("k1.md");
        assertThat(Files.exists(Path.of(result.tempPath()))).isTrue();
        assertThat(result.content()).isEqualTo("# 标题\n正文内容");
    }

    @Test
    void acceptsPlainTextContentType() {
        var part = mockFilePart("doc.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));

        var result = storage.saveTemp("k1", part).block();

        assertThat(result.content()).isEqualTo("hello");
    }

    @Test
    void rejectsDisallowedContentType() {
        var part = mockFilePart("doc.pdf", "application/pdf", "fake".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> storage.saveTemp("k1", part).block())
                .isInstanceOf(KnowledgeFileStorage.RejectedException.class)
                .satisfies(e -> assertThat(((KnowledgeFileStorage.RejectedException) e).getCode())
                        .isEqualTo("FILE_REJECTED"));
    }

    @Test
    void rejectsFileExceedingSizeLimit() {
        var big = new byte[2 * 1024 * 1024];
        var part = mockFilePart("big.md", "text/markdown", big);

        assertThatThrownBy(() -> storage.saveTemp("k1", part).block())
                .isInstanceOf(KnowledgeFileStorage.RejectedException.class)
                .satisfies(e -> assertThat(((KnowledgeFileStorage.RejectedException) e).getCode())
                        .isEqualTo("FILE_REJECTED"));
    }

    @Test
    void promoteToLibWritesOriginalAndMetadataFiles() throws Exception {
        var tempFile = tempDir.resolve("k1.md");
        Files.writeString(tempFile, "原文内容");
        var approvedAt = LocalDateTime.of(2026, 7, 27, 10, 0);

        storage.promoteToLib("k1", tempFile.toString(), "标题", "摘要内容", "user-1", approvedAt).block();

        var libFile = libDir.resolve("k1.md");
        var metaFile = libDir.resolve("k1.meta.json");
        assertThat(Files.readString(libFile)).isEqualTo("原文内容");
        assertThat(Files.readString(metaFile))
                .contains("\"title\"", "标题", "\"summary\"", "摘要内容", "\"approvedBy\"", "user-1");
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn -q -Dtest=KnowledgeFileStorageTest test`
Expected: COMPILATION ERROR（`KnowledgeProperties`、`KnowledgeFileStorage` 都还不存在）

- [ ] **Step 3: 实现**

`KnowledgeProperties.java`：

```java
package com.jowo.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.knowledge")
public class KnowledgeProperties {
    private String tempDir;
    private String libDir;
    private int maxSizeMb = 5;

    public String getTempDir() { return tempDir; }
    public void setTempDir(String v) { this.tempDir = v; }
    public String getLibDir() { return libDir; }
    public void setLibDir(String v) { this.libDir = v; }
    public int getMaxSizeMb() { return maxSizeMb; }
    public void setMaxSizeMb(int v) { this.maxSizeMb = v; }

    public long getMaxSizeBytes() { return maxSizeMb * 1024L * 1024L; }
}
```

`KnowledgeFileStorage.java`：

```java
package com.jowo.agent.knowledge;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Set;

import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jowo.agent.config.KnowledgeProperties;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class KnowledgeFileStorage {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            MediaType.TEXT_PLAIN_VALUE, "text/markdown");

    private final KnowledgeProperties props;
    private final ObjectMapper json = new ObjectMapper();

    public KnowledgeFileStorage(KnowledgeProperties props) {
        this.props = props;
    }

    public static class RejectedException extends RuntimeException {
        private final String code;
        public RejectedException(String code, String message) {
            super(message);
            this.code = code;
        }
        public String getCode() { return code; }
    }

    public record UploadedFile(String tempPath, String content) {}

    public Mono<UploadedFile> saveTemp(String id, FilePart part) {
        return Mono.defer(() -> {
            var contentType = part.headers().getContentType();
            var declaredType = contentType == null ? null : contentType.toString();
            if (!ALLOWED_CONTENT_TYPES.contains(declaredType)) {
                return Mono.error(new RejectedException("FILE_REJECTED",
                        "content-type " + declaredType + " not allowed"));
            }
            var target = Path.of(props.getTempDir(), id + ".md");
            return Mono.fromCallable(() -> Files.createDirectories(target.getParent()))
                    .subscribeOn(Schedulers.boundedElastic())
                    .then(part.transferTo(target))
                    .then(Mono.fromCallable(() -> finalizeTemp(target))
                            .subscribeOn(Schedulers.boundedElastic()));
        });
    }

    private UploadedFile finalizeTemp(Path target) throws IOException {
        long size = Files.size(target);
        if (size > props.getMaxSizeBytes()) {
            Files.deleteIfExists(target);
            throw new RejectedException("FILE_REJECTED", "file exceeds " + props.getMaxSizeMb() + " MB");
        }
        String content = Files.readString(target, StandardCharsets.UTF_8);
        return new UploadedFile(target.toAbsolutePath().toString(), content);
    }

    public Mono<Void> promoteToLib(String id, String tempPath, String title, String summary,
                                    String approvedBy, LocalDateTime approvedAt) {
        return Mono.fromRunnable(() -> doPromote(id, tempPath, title, summary, approvedBy, approvedAt))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private void doPromote(String id, String tempPath, String title, String summary,
                            String approvedBy, LocalDateTime approvedAt) {
        try {
            var libDir = Path.of(props.getLibDir());
            Files.createDirectories(libDir);
            Files.copy(Path.of(tempPath), libDir.resolve(id + ".md"), StandardCopyOption.REPLACE_EXISTING);
            var meta = json.createObjectNode();
            meta.put("title", title);
            meta.put("summary", summary);
            meta.put("approvedBy", approvedBy);
            meta.put("approvedAt", approvedAt.toString());
            Files.writeString(libDir.resolve(id + ".meta.json"), json.writeValueAsString(meta));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
```

`application.yml` 在 `app:` 块下 `upload:` 之后新增：

```yaml
  knowledge:
    temp-dir: ${user.home}/.openclaw/workspace/knowledge-pending
    lib-dir: ./lib
    max-size-mb: 5
```

- [ ] **Step 4: 运行确认通过 + 全量回归**

Run: `mvn -q -Dtest=KnowledgeFileStorageTest test`
Expected: PASS（5 个测试全绿）

Run: `mvn -q test`
Expected: 全部 PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/jowo/agent/config/KnowledgeProperties.java \
        src/main/java/com/jowo/agent/knowledge/KnowledgeFileStorage.java \
        src/main/resources/application.yml \
        src/test/java/com/jowo/agent/knowledge/KnowledgeFileStorageTest.java
git commit -m "$(cat <<'EOF'
feat: add knowledge document temp storage and lib promotion

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: LlmClient.summarize()

**Files:**
- Modify: `src/main/java/com/jowo/agent/service/LlmClient.java`
- Test: `src/test/java/com/jowo/agent/service/LlmClientSummarizeTest.java`

**Interfaces:**
- Consumes: `LlmClient.chatStream(List<Message>, List<ToolDefinition>): Flux<LlmEvent>`（已存在）、`LlmEvent.Completed(String fullText)`（已存在）、`Message.system(String)`/`Message.user(String)`（已存在）。
- Produces: `LlmClient.summarize(String content): reactor.core.publisher.Mono<String>`（接口默认方法，任何实现类自动获得）。

- [ ] **Step 1: 写失败测试**

```java
package com.jowo.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class LlmClientSummarizeTest {

    @Test
    void summarizeReturnsCompletedFullText() {
        LlmClient client = (messages, tools) -> Flux.just(
                new LlmClient.LlmEvent.ContentDelta("这是"),
                new LlmClient.LlmEvent.ContentDelta("摘要"),
                new LlmClient.LlmEvent.Completed("这是摘要"));

        StepVerifier.create(client.summarize("一份很长的文档内容"))
                .expectNext("这是摘要")
                .verifyComplete();
    }

    @Test
    void summarizePassesContentAsUserMessageWithNoTools() {
        var captured = new AtomicReference<List<LlmClient.Message>>();
        LlmClient client = (messages, tools) -> {
            captured.set(messages);
            assertThat(tools).isEmpty();
            return Flux.just(new LlmClient.LlmEvent.Completed("摘要"));
        };

        client.summarize("文档内容").block();

        assertThat(captured.get()).extracting(LlmClient.Message::role).contains("system", "user");
        assertThat(captured.get()).extracting(LlmClient.Message::content).contains("文档内容");
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn -q -Dtest=LlmClientSummarizeTest test`
Expected: COMPILATION ERROR（`LlmClient` 上还没有 `summarize` 方法）

- [ ] **Step 3: 实现**

在 `src/main/java/com/jowo/agent/service/LlmClient.java` 里，紧跟在 `chatStream` 方法声明后面新增：

```java
    default reactor.core.publisher.Mono<String> summarize(String content) {
        var messages = List.of(
                Message.system("你是一个文档摘要助手。用一到两句话总结这份文档讲的是什么、"
                        + "适用于回答哪类问题，供审核人员快速判断这份知识库文档的用途。"
                        + "只输出摘要正文，不要额外解释。"),
                Message.user(content));
        return chatStream(messages, List.of())
                .ofType(LlmEvent.Completed.class)
                .map(LlmEvent.Completed::fullText)
                .next();
    }
```

- [ ] **Step 4: 运行确认通过 + 全量回归**

Run: `mvn -q -Dtest=LlmClientSummarizeTest test`
Expected: PASS（2 个测试全绿）

Run: `mvn -q test`
Expected: 全部 PASS（`DeepSeekLlmClient` 等既有实现类只需实现 `chatStream`，新的默认方法不影响它们的编译）

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/jowo/agent/service/LlmClient.java \
        src/test/java/com/jowo/agent/service/LlmClientSummarizeTest.java
git commit -m "$(cat <<'EOF'
feat: add LlmClient.summarize convenience method

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: KnowledgeDocumentService

**Files:**
- Create: `src/main/java/com/jowo/agent/knowledge/KnowledgeDocumentService.java`
- Test: `src/test/java/com/jowo/agent/knowledge/KnowledgeDocumentServiceTest.java`

**Interfaces:**
- Consumes:
  - `KnowledgeDocumentRepository`（Task 1）：`insert`、`findById`、`findByStatus`、`approveIfPending`、`rejectIfPending`。
  - `KnowledgeFileStorage`（Task 2）：`saveTemp(String, FilePart): Mono<UploadedFile>`、`promoteToLib(...): Mono<Void>`。
  - `LlmClient.summarize(String): Mono<String>`（Task 3）。
- Produces:
  - `KnowledgeDocumentService(KnowledgeDocumentRepository, KnowledgeFileStorage, LlmClient)` 构造器。
  - `upload(String uploaderUserId, String title, FilePart file): Mono<KnowledgeDocumentRow>`
  - `list(Status status): Flux<KnowledgeDocumentRow>`
  - `get(String id): Mono<KnowledgeDocumentRow>`
  - `approve(String id, String userId, String summaryOverride): Mono<ActionResult>`
  - `reject(String id, String userId): Mono<ActionResult>`
  - `enum ActionResult { OK, NOT_FOUND, CONFLICT }`

- [ ] **Step 1: 写失败测试**

```java
package com.jowo.agent.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.http.codec.multipart.FilePart;

import com.jowo.agent.knowledge.KnowledgeDocumentRepository.KnowledgeDocumentRow;
import com.jowo.agent.knowledge.KnowledgeDocumentRepository.KnowledgeDocumentRow.Status;
import com.jowo.agent.knowledge.KnowledgeFileStorage.UploadedFile;
import com.jowo.agent.service.LlmClient;

import reactor.core.publisher.Mono;

class KnowledgeDocumentServiceTest {

    @Test
    void uploadSavesFileGeneratesSummaryAndInsertsPendingRow() {
        var repository = mock(KnowledgeDocumentRepository.class);
        var storage = mock(KnowledgeFileStorage.class);
        var llm = mock(LlmClient.class);
        var file = mock(FilePart.class);
        when(file.filename()).thenReturn("doc.md");
        when(storage.saveTemp(any(), eq(file))).thenReturn(Mono.just(new UploadedFile("/tmp/x.md", "文档内容")));
        when(llm.summarize("文档内容")).thenReturn(Mono.just("这是摘要"));
        when(repository.insert(any())).thenReturn(Mono.empty());
        var service = new KnowledgeDocumentService(repository, storage, llm);

        var row = service.upload("user-1", "自定义标题", file).block();

        assertThat(row.title()).isEqualTo("自定义标题");
        assertThat(row.originalFilename()).isEqualTo("doc.md");
        assertThat(row.status()).isEqualTo(Status.PENDING);
        assertThat(row.summary()).isEqualTo("这是摘要");
        assertThat(row.uploaderUserId()).isEqualTo("user-1");
        verify(repository).insert(row);
    }

    @Test
    void uploadFallsBackToFilenameWhenTitleBlank() {
        var repository = mock(KnowledgeDocumentRepository.class);
        var storage = mock(KnowledgeFileStorage.class);
        var llm = mock(LlmClient.class);
        var file = mock(FilePart.class);
        when(file.filename()).thenReturn("doc.md");
        when(storage.saveTemp(any(), eq(file))).thenReturn(Mono.just(new UploadedFile("/tmp/x.md", "内容")));
        when(llm.summarize(any())).thenReturn(Mono.just("摘要"));
        when(repository.insert(any())).thenReturn(Mono.empty());
        var service = new KnowledgeDocumentService(repository, storage, llm);

        var row = service.upload("user-1", "  ", file).block();

        assertThat(row.title()).isEqualTo("doc.md");
    }

    @Test
    void uploadDegradesToEmptySummaryWhenLlmFails() {
        var repository = mock(KnowledgeDocumentRepository.class);
        var storage = mock(KnowledgeFileStorage.class);
        var llm = mock(LlmClient.class);
        var file = mock(FilePart.class);
        when(file.filename()).thenReturn("doc.md");
        when(storage.saveTemp(any(), eq(file))).thenReturn(Mono.just(new UploadedFile("/tmp/x.md", "内容")));
        when(llm.summarize(any())).thenReturn(Mono.error(new RuntimeException("LLM 超时")));
        when(repository.insert(any())).thenReturn(Mono.empty());
        var service = new KnowledgeDocumentService(repository, storage, llm);

        var row = service.upload("user-1", "标题", file).block();

        assertThat(row.summary()).isEmpty();
    }

    @Test
    void approveOnUnknownIdReturnsNotFound() {
        var repository = mock(KnowledgeDocumentRepository.class);
        var storage = mock(KnowledgeFileStorage.class);
        var llm = mock(LlmClient.class);
        when(repository.findById("missing")).thenReturn(Mono.empty());
        var service = new KnowledgeDocumentService(repository, storage, llm);

        var result = service.approve("missing", "user-1", null).block();

        assertThat(result).isEqualTo(KnowledgeDocumentService.ActionResult.NOT_FOUND);
    }

    @Test
    void approveOnPendingRowPromotesToLibAndReturnsOk() {
        var repository = mock(KnowledgeDocumentRepository.class);
        var storage = mock(KnowledgeFileStorage.class);
        var llm = mock(LlmClient.class);
        var existing = new KnowledgeDocumentRow("k1", "标题", "doc.md", "/tmp/k1.md",
                Status.PENDING, "原摘要", "user-1", LocalDateTime.now(), null, null);
        when(repository.findById("k1")).thenReturn(Mono.just(existing));
        when(repository.approveIfPending(eq("k1"), eq("原摘要"), eq("admin-1"), any())).thenReturn(Mono.just(1L));
        when(storage.promoteToLib(eq("k1"), eq("/tmp/k1.md"), eq("标题"), eq("原摘要"), eq("admin-1"), any()))
                .thenReturn(Mono.empty());
        var service = new KnowledgeDocumentService(repository, storage, llm);

        var result = service.approve("k1", "admin-1", null).block();

        assertThat(result).isEqualTo(KnowledgeDocumentService.ActionResult.OK);
        verify(storage).promoteToLib(eq("k1"), eq("/tmp/k1.md"), eq("标题"), eq("原摘要"), eq("admin-1"), any());
    }

    @Test
    void approveUsesOverrideSummaryWhenProvided() {
        var repository = mock(KnowledgeDocumentRepository.class);
        var storage = mock(KnowledgeFileStorage.class);
        var llm = mock(LlmClient.class);
        var existing = new KnowledgeDocumentRow("k1", "标题", "doc.md", "/tmp/k1.md",
                Status.PENDING, "原摘要", "user-1", LocalDateTime.now(), null, null);
        when(repository.findById("k1")).thenReturn(Mono.just(existing));
        when(repository.approveIfPending(eq("k1"), eq("修改后的摘要"), eq("admin-1"), any())).thenReturn(Mono.just(1L));
        when(storage.promoteToLib(any(), any(), any(), any(), any(), any())).thenReturn(Mono.empty());
        var service = new KnowledgeDocumentService(repository, storage, llm);

        service.approve("k1", "admin-1", "修改后的摘要").block();

        verify(repository).approveIfPending(eq("k1"), eq("修改后的摘要"), eq("admin-1"), any());
    }

    @Test
    void approveOnAlreadyProcessedRowReturnsConflict() {
        var repository = mock(KnowledgeDocumentRepository.class);
        var storage = mock(KnowledgeFileStorage.class);
        var llm = mock(LlmClient.class);
        var existing = new KnowledgeDocumentRow("k1", "标题", "doc.md", "/tmp/k1.md",
                Status.APPROVED, "摘要", "user-1", LocalDateTime.now(), "admin-0", LocalDateTime.now());
        when(repository.findById("k1")).thenReturn(Mono.just(existing));
        when(repository.approveIfPending(any(), any(), any(), any())).thenReturn(Mono.just(0L));
        var service = new KnowledgeDocumentService(repository, storage, llm);

        var result = service.approve("k1", "admin-1", null).block();

        assertThat(result).isEqualTo(KnowledgeDocumentService.ActionResult.CONFLICT);
        verifyNoInteractions(storage);
    }

    @Test
    void rejectOnUnknownIdReturnsNotFound() {
        var repository = mock(KnowledgeDocumentRepository.class);
        var storage = mock(KnowledgeFileStorage.class);
        var llm = mock(LlmClient.class);
        when(repository.findById("missing")).thenReturn(Mono.empty());
        var service = new KnowledgeDocumentService(repository, storage, llm);

        var result = service.reject("missing", "user-1").block();

        assertThat(result).isEqualTo(KnowledgeDocumentService.ActionResult.NOT_FOUND);
    }

    @Test
    void rejectOnPendingRowReturnsOk() {
        var repository = mock(KnowledgeDocumentRepository.class);
        var storage = mock(KnowledgeFileStorage.class);
        var llm = mock(LlmClient.class);
        var existing = new KnowledgeDocumentRow("k1", "标题", "doc.md", "/tmp/k1.md",
                Status.PENDING, "摘要", "user-1", LocalDateTime.now(), null, null);
        when(repository.findById("k1")).thenReturn(Mono.just(existing));
        when(repository.rejectIfPending(eq("k1"), eq("admin-1"), any())).thenReturn(Mono.just(1L));
        var service = new KnowledgeDocumentService(repository, storage, llm);

        var result = service.reject("k1", "admin-1").block();

        assertThat(result).isEqualTo(KnowledgeDocumentService.ActionResult.OK);
    }

    @Test
    void rejectOnAlreadyProcessedRowReturnsConflict() {
        var repository = mock(KnowledgeDocumentRepository.class);
        var storage = mock(KnowledgeFileStorage.class);
        var llm = mock(LlmClient.class);
        var existing = new KnowledgeDocumentRow("k1", "标题", "doc.md", "/tmp/k1.md",
                Status.REJECTED, "摘要", "user-1", LocalDateTime.now(), "admin-0", LocalDateTime.now());
        when(repository.findById("k1")).thenReturn(Mono.just(existing));
        when(repository.rejectIfPending(any(), any(), any())).thenReturn(Mono.just(0L));
        var service = new KnowledgeDocumentService(repository, storage, llm);

        var result = service.reject("k1", "admin-1").block();

        assertThat(result).isEqualTo(KnowledgeDocumentService.ActionResult.CONFLICT);
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn -q -Dtest=KnowledgeDocumentServiceTest test`
Expected: COMPILATION ERROR（`KnowledgeDocumentService` 还不存在）

- [ ] **Step 3: 实现**

```java
package com.jowo.agent.knowledge;

import java.time.LocalDateTime;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;

import com.jowo.agent.knowledge.KnowledgeDocumentRepository.KnowledgeDocumentRow;
import com.jowo.agent.knowledge.KnowledgeDocumentRepository.KnowledgeDocumentRow.Status;
import com.jowo.agent.service.LlmClient;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class KnowledgeDocumentService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeDocumentService.class);

    private final KnowledgeDocumentRepository repository;
    private final KnowledgeFileStorage storage;
    private final LlmClient llm;

    public KnowledgeDocumentService(KnowledgeDocumentRepository repository, KnowledgeFileStorage storage, LlmClient llm) {
        this.repository = repository;
        this.storage = storage;
        this.llm = llm;
    }

    public enum ActionResult { OK, NOT_FOUND, CONFLICT }

    public Mono<KnowledgeDocumentRow> upload(String uploaderUserId, String title, FilePart file) {
        String id = UUID.randomUUID().toString();
        return storage.saveTemp(id, file)
                .flatMap(saved -> llm.summarize(saved.content())
                        .onErrorResume(e -> {
                            log.warn("knowledge summary generation failed for {}: {}", id, e.getMessage());
                            return Mono.just("");
                        })
                        .defaultIfEmpty("")
                        .map(summary -> new KnowledgeDocumentRow(id,
                                title == null || title.isBlank() ? file.filename() : title,
                                file.filename(), saved.tempPath(), Status.PENDING, summary,
                                uploaderUserId, LocalDateTime.now(), null, null)))
                .flatMap(row -> repository.insert(row).thenReturn(row));
    }

    public Flux<KnowledgeDocumentRow> list(Status status) {
        return repository.findByStatus(status);
    }

    public Mono<KnowledgeDocumentRow> get(String id) {
        return repository.findById(id);
    }

    public Mono<ActionResult> approve(String id, String userId, String summaryOverride) {
        return repository.findById(id)
                .flatMap(existing -> {
                    String summary = summaryOverride != null ? summaryOverride : existing.summary();
                    LocalDateTime now = LocalDateTime.now();
                    return repository.approveIfPending(id, summary, userId, now)
                            .flatMap(updated -> updated == 0
                                    ? Mono.just(ActionResult.CONFLICT)
                                    : storage.promoteToLib(id, existing.tempPath(), existing.title(), summary, userId, now)
                                            .thenReturn(ActionResult.OK));
                })
                .defaultIfEmpty(ActionResult.NOT_FOUND);
    }

    public Mono<ActionResult> reject(String id, String userId) {
        return repository.findById(id)
                .flatMap(existing -> repository.rejectIfPending(id, userId, LocalDateTime.now())
                        .map(updated -> updated == 0 ? ActionResult.CONFLICT : ActionResult.OK))
                .defaultIfEmpty(ActionResult.NOT_FOUND);
    }
}
```

- [ ] **Step 4: 运行确认通过 + 全量回归**

Run: `mvn -q -Dtest=KnowledgeDocumentServiceTest test`
Expected: PASS（10 个测试全绿）

Run: `mvn -q test`
Expected: 全部 PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/jowo/agent/knowledge/KnowledgeDocumentService.java \
        src/test/java/com/jowo/agent/knowledge/KnowledgeDocumentServiceTest.java
git commit -m "$(cat <<'EOF'
feat: add KnowledgeDocumentService orchestrating the review state machine

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: KnowledgeController

**Files:**
- Create: `src/main/java/com/jowo/agent/knowledge/KnowledgeController.java`
- Test: `src/test/java/com/jowo/agent/knowledge/KnowledgeControllerTest.java`

**Interfaces:**
- Consumes:
  - `KnowledgeDocumentService`（Task 4）：`upload`、`list`、`get`、`approve`、`reject`、`ActionResult`。
  - `KnowledgeProperties`（Task 2）：`getLibDir()`。
  - 现有 `AuthSessionStore.AuthUser`（`userId()`）、`@CurrentUser` 注解、`WebConfig`/`AuthExceptionHandler`/`CurrentUserArgumentResolver`（都已存在，不改）。
- Produces：5 个端点，见下方实现；`record KnowledgeDocumentSummary(String id, String title, String uploaderUserId, String summary, LocalDateTime uploadedAt, String status, String downloadUrl)`（controller 内部 DTO，`list`/`upload` 的响应体）。`upload` 端点捕获 `KnowledgeFileStorage.RejectedException`（Task 2 产出）转换成 400，响应体 `{"code": ..., "reason": ...}`。

- [ ] **Step 1: 写失败测试**

```java
package com.jowo.agent.knowledge;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

import com.jowo.agent.config.KnowledgeProperties;
import com.jowo.agent.knowledge.KnowledgeDocumentRepository.KnowledgeDocumentRow;
import com.jowo.agent.knowledge.KnowledgeDocumentRepository.KnowledgeDocumentRow.Status;
import com.jowo.agent.service.AuthSessionStore;
import com.jowo.agent.web.AuthExceptionHandler;
import com.jowo.agent.web.CurrentUserArgumentResolver;
import com.jowo.agent.web.WebConfig;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@WebFluxTest(KnowledgeController.class)
@Import({WebConfig.class, AuthExceptionHandler.class, CurrentUserArgumentResolver.class})
class KnowledgeControllerTest {

    @Autowired WebTestClient client;
    @MockBean KnowledgeDocumentService service;
    @MockBean KnowledgeProperties props;
    @MockBean AuthSessionStore sessions;

    private void asUser1() {
        when(sessions.resolve("test-token")).thenReturn(
                Mono.just(new AuthSessionStore.AuthUser("user-1", "Test User", "sl-tok")));
    }

    private static ByteArrayResource textPart(String filename, String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override public String getFilename() { return filename; }
        };
    }

    @Test
    void uploadRequiresAuthentication() {
        var builder = new MultipartBodyBuilder();
        builder.part("file", textPart("doc.md", "内容")).contentType(MediaType.TEXT_MARKDOWN);

        client.post().uri("/api/knowledge/upload")
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void uploadReturnsCreatedSummary() {
        asUser1();
        var now = LocalDateTime.now();
        var row = new KnowledgeDocumentRow("k1", "标题", "doc.md", "/tmp/k1.md",
                Status.PENDING, "摘要", "user-1", now, null, null);
        when(service.upload(eq("user-1"), any(), any())).thenReturn(Mono.just(row));
        var builder = new MultipartBodyBuilder();
        builder.part("file", textPart("doc.md", "内容")).contentType(MediaType.TEXT_MARKDOWN);

        client.post().uri("/api/knowledge/upload")
                .header("Authorization", "Bearer test-token")
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("k1")
                .jsonPath("$.title").isEqualTo("标题")
                .jsonPath("$.summary").isEqualTo("摘要")
                .jsonPath("$.downloadUrl").isEqualTo("/api/knowledge/k1/download");
    }

    @Test
    void uploadReturns400WhenFileRejected() {
        asUser1();
        when(service.upload(eq("user-1"), any(), any())).thenReturn(
                Mono.error(new KnowledgeFileStorage.RejectedException("FILE_REJECTED", "content-type application/pdf not allowed")));
        var builder = new MultipartBodyBuilder();
        builder.part("file", textPart("doc.pdf", "内容")).contentType(MediaType.APPLICATION_PDF);

        client.post().uri("/api/knowledge/upload")
                .header("Authorization", "Bearer test-token")
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FILE_REJECTED");
    }

    @Test
    void listDefaultsToPendingStatus() {
        asUser1();
        var now = LocalDateTime.now();
        when(service.list(Status.PENDING)).thenReturn(Flux.just(
                new KnowledgeDocumentRow("k1", "标题", "doc.md", "/tmp/k1.md",
                        Status.PENDING, "摘要", "user-1", now, null, null)));

        client.get().uri("/api/knowledge").header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].id").isEqualTo("k1")
                .jsonPath("$[0].status").isEqualTo("PENDING");
    }

    @Test
    void listWithStatusQueryParamFiltersApproved() {
        asUser1();
        when(service.list(Status.APPROVED)).thenReturn(Flux.empty());

        client.get().uri("/api/knowledge?status=APPROVED").header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(0);

        verify(service).list(Status.APPROVED);
    }

    @Test
    void downloadReturns404WhenFileMissing() {
        asUser1();
        var now = LocalDateTime.now();
        when(service.get("k1")).thenReturn(Mono.just(new KnowledgeDocumentRow("k1", "标题", "doc.md",
                "/no/such/path/k1.md", Status.PENDING, "摘要", "user-1", now, null, null)));

        client.get().uri("/api/knowledge/k1/download").header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void downloadReturns404WhenIdUnknown() {
        asUser1();
        when(service.get("missing")).thenReturn(Mono.empty());

        client.get().uri("/api/knowledge/missing/download").header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void approveReturns404ForUnknownId() {
        asUser1();
        when(service.approve(eq("missing"), eq("user-1"), any())).thenReturn(
                Mono.just(KnowledgeDocumentService.ActionResult.NOT_FOUND));

        client.post().uri("/api/knowledge/missing/approve").header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void approveReturns409ForAlreadyProcessedDocument() {
        asUser1();
        when(service.approve(eq("k1"), eq("user-1"), any())).thenReturn(
                Mono.just(KnowledgeDocumentService.ActionResult.CONFLICT));

        client.post().uri("/api/knowledge/k1/approve").header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void approveWithBodyPassesSummaryOverride() {
        asUser1();
        when(service.approve("k1", "user-1", "改写后的摘要")).thenReturn(
                Mono.just(KnowledgeDocumentService.ActionResult.OK));

        client.post().uri("/api/knowledge/k1/approve")
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"summary\":\"改写后的摘要\"}")
                .exchange()
                .expectStatus().isOk();

        verify(service).approve("k1", "user-1", "改写后的摘要");
    }

    @Test
    void rejectReturnsOkForPendingDocument() {
        asUser1();
        when(service.reject("k1", "user-1")).thenReturn(Mono.just(KnowledgeDocumentService.ActionResult.OK));

        client.post().uri("/api/knowledge/k1/reject").header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isOk();
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn -q -Dtest=KnowledgeControllerTest test`
Expected: COMPILATION ERROR（`KnowledgeController` 还不存在）

- [ ] **Step 3: 实现**

```java
package com.jowo.agent.knowledge;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;

import com.jowo.agent.config.KnowledgeProperties;
import com.jowo.agent.knowledge.KnowledgeDocumentRepository.KnowledgeDocumentRow;
import com.jowo.agent.knowledge.KnowledgeDocumentRepository.KnowledgeDocumentRow.Status;
import com.jowo.agent.service.AuthSessionStore;
import com.jowo.agent.web.CurrentUser;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/knowledge")
@CrossOrigin(origins = "*")
class KnowledgeController {

    private final KnowledgeDocumentService service;
    private final KnowledgeProperties props;

    KnowledgeController(KnowledgeDocumentService service, KnowledgeProperties props) {
        this.service = service;
        this.props = props;
    }

    @PostMapping("/upload")
    Mono<ResponseEntity<Object>> upload(
            @RequestPart("file") FilePart file,
            @RequestPart(value = "title", required = false) String title,
            @CurrentUser AuthSessionStore.AuthUser user) {
        return service.upload(user.userId(), title, file)
                .map(row -> ResponseEntity.ok((Object) toSummary(row)))
                .onErrorResume(KnowledgeFileStorage.RejectedException.class, e -> Mono.just(
                        ResponseEntity.badRequest().body((Object) Map.of(
                                "code", e.getCode(), "reason", e.getMessage()))));
    }

    @GetMapping
    Flux<KnowledgeDocumentSummary> list(
            @RequestParam(value = "status", defaultValue = "PENDING") Status status,
            @CurrentUser AuthSessionStore.AuthUser user) {
        return service.list(status).map(KnowledgeController::toSummary);
    }

    @GetMapping("/{id}/download")
    Mono<ResponseEntity<?>> download(@PathVariable String id, @CurrentUser AuthSessionStore.AuthUser user) {
        return service.get(id)
                .map(row -> {
                    var path = row.status() == Status.APPROVED
                            ? Path.of(props.getLibDir(), row.id() + ".md")
                            : Path.of(row.tempPath());
                    if (!Files.isRegularFile(path)) {
                        return ResponseEntity.notFound().build();
                    }
                    return ResponseEntity.ok()
                            .contentType(MediaType.TEXT_PLAIN)
                            .body((Object) new FileSystemResource(path));
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/approve")
    Mono<ResponseEntity<Void>> approve(@PathVariable String id,
            @RequestBody(required = false) ApproveRequest body,
            @CurrentUser AuthSessionStore.AuthUser user) {
        String override = body == null ? null : body.summary();
        return service.approve(id, user.userId(), override).map(KnowledgeController::toStatus);
    }

    @PostMapping("/{id}/reject")
    Mono<ResponseEntity<Void>> reject(@PathVariable String id, @CurrentUser AuthSessionStore.AuthUser user) {
        return service.reject(id, user.userId()).map(KnowledgeController::toStatus);
    }

    private static ResponseEntity<Void> toStatus(KnowledgeDocumentService.ActionResult result) {
        return switch (result) {
            case OK -> ResponseEntity.ok().build();
            case NOT_FOUND -> ResponseEntity.notFound().build();
            case CONFLICT -> ResponseEntity.status(HttpStatus.CONFLICT).build();
        };
    }

    private static KnowledgeDocumentSummary toSummary(KnowledgeDocumentRow row) {
        return new KnowledgeDocumentSummary(row.id(), row.title(), row.uploaderUserId(), row.summary(),
                row.uploadedAt(), row.status().name(), "/api/knowledge/" + row.id() + "/download");
    }

    record ApproveRequest(String summary) {}

    record KnowledgeDocumentSummary(String id, String title, String uploaderUserId, String summary,
            LocalDateTime uploadedAt, String status, String downloadUrl) {}
}
```

- [ ] **Step 4: 运行确认通过 + 全量回归**

Run: `mvn -q -Dtest=KnowledgeControllerTest test`
Expected: PASS（11 个测试全绿）

Run: `mvn -q test`
Expected: 全部 PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/jowo/agent/knowledge/KnowledgeController.java \
        src/test/java/com/jowo/agent/knowledge/KnowledgeControllerTest.java
git commit -m "$(cat <<'EOF'
feat: add KnowledgeController REST endpoints

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: 全链路集成测试

**Files:**
- Test: `src/test/java/com/jowo/agent/knowledge/KnowledgeControllerIntegrationTest.java`

**Interfaces:**
- Consumes: 全部前 5 个 Task 产出的真实 Bean（`KnowledgeController`/`KnowledgeDocumentService`/`KnowledgeDocumentRepository`/`KnowledgeFileStorage`/`KnowledgeProperties`），以及现有的 `AuthSessionStore`（真实 Redis）。`LlmClient` 用 `@MockBean` 替换，避免测试期间真的调用 DeepSeek。
- Produces: 无新生产代码，只验证 Task 1-5 拼在一起的端到端行为。

- [ ] **Step 1: 写测试**

（这个 Task 没有"先失败后实现"的顺序——所有生产代码已在前面 Task 完成，这里是纯粹的集成验证。先写测试，直接运行，预期直接 PASS；如果 FAIL 说明前面某个 Task 的接口对不上，需要回头修。）

```java
package com.jowo.agent.knowledge;

import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jowo.agent.config.KnowledgeProperties;
import com.jowo.agent.service.AuthSessionStore;
import com.jowo.agent.service.LlmClient;

import reactor.core.publisher.Mono;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestPropertySource(properties = {
    "app.knowledge.temp-dir=${java.io.tmpdir}/knowledge-test/pending",
    "app.knowledge.lib-dir=${java.io.tmpdir}/knowledge-test/lib"
})
class KnowledgeControllerIntegrationTest {

    @Autowired WebTestClient client;
    @Autowired KnowledgeProperties props;
    @Autowired AuthSessionStore sessions;
    @MockBean LlmClient llm;
    String token;

    @BeforeEach
    void setUp() {
        when(llm.summarize(org.mockito.ArgumentMatchers.anyString())).thenReturn(Mono.just("自动生成的摘要"));
        token = sessions.create("knowledge-test-user", "Knowledge Test", "sl-tok").block();
        deleteRecursively(Path.of(props.getTempDir()));
        deleteRecursively(Path.of(props.getLibDir()));
    }

    @AfterEach
    void tearDown() {
        sessions.delete(token).block();
        deleteRecursively(Path.of(props.getTempDir()));
        deleteRecursively(Path.of(props.getLibDir()));
    }

    private static void deleteRecursively(Path dir) {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
    }

    private static ByteArrayResource textPart(String filename, String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override public String getFilename() { return filename; }
        };
    }

    private static String extractId(byte[] body) {
        try {
            return new ObjectMapper().readTree(body).path("id").asText();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void fullLifecycle_uploadListApproveDownload() {
        var builder = new MultipartBodyBuilder();
        builder.part("file", textPart("退款流程.md", "# 退款流程\n\n用户申请退款后...")).contentType(MediaType.TEXT_MARKDOWN);

        var uploadBody = client.post().uri("/api/knowledge/upload")
                .header("Authorization", "Bearer " + token)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("PENDING")
                .jsonPath("$.summary").isEqualTo("自动生成的摘要")
                .returnResult().getResponseBody();
        String id = extractId(uploadBody);

        client.get().uri("/api/knowledge?status=PENDING")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[?(@.id=='" + id + "')]").exists();

        client.post().uri("/api/knowledge/" + id + "/approve")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk();

        client.get().uri("/api/knowledge?status=PENDING")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[?(@.id=='" + id + "')]").doesNotExist();

        client.get().uri("/api/knowledge/" + id + "/download")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("# 退款流程\n\n用户申请退款后...");

        var libFile = Path.of(props.getLibDir(), id + ".md");
        var metaFile = Path.of(props.getLibDir(), id + ".meta.json");
        org.assertj.core.api.Assertions.assertThat(Files.exists(libFile)).isTrue();
        org.assertj.core.api.Assertions.assertThat(Files.exists(metaFile)).isTrue();
    }

    @Test
    void rejectRemovesDocumentFromPendingListButKeepsItInRejectedHistory() {
        var builder = new MultipartBodyBuilder();
        builder.part("file", textPart("faq.md", "常见问题解答")).contentType(MediaType.TEXT_MARKDOWN);

        var uploadBody = client.post().uri("/api/knowledge/upload")
                .header("Authorization", "Bearer " + token)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isOk()
                .expectBody().returnResult().getResponseBody();
        String id = extractId(uploadBody);

        client.post().uri("/api/knowledge/" + id + "/reject")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk();

        client.get().uri("/api/knowledge?status=PENDING")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectBody()
                .jsonPath("$[?(@.id=='" + id + "')]").doesNotExist();

        client.get().uri("/api/knowledge?status=REJECTED")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectBody()
                .jsonPath("$[?(@.id=='" + id + "')]").exists();
    }

    @Test
    void approvingSameDocumentTwiceReturnsConflictOnSecondCall() {
        var builder = new MultipartBodyBuilder();
        builder.part("file", textPart("doc.md", "内容")).contentType(MediaType.TEXT_MARKDOWN);
        var uploadBody = client.post().uri("/api/knowledge/upload")
                .header("Authorization", "Bearer " + token)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectBody().returnResult().getResponseBody();
        String id = extractId(uploadBody);

        client.post().uri("/api/knowledge/" + id + "/approve")
                .header("Authorization", "Bearer " + token).exchange().expectStatus().isOk();

        client.post().uri("/api/knowledge/" + id + "/approve")
                .header("Authorization", "Bearer " + token).exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void uploadRejectsNonTextContentType() {
        var builder = new MultipartBodyBuilder();
        builder.part("file", textPart("doc.bin", "binary-ish")).contentType(MediaType.APPLICATION_OCTET_STREAM);

        client.post().uri("/api/knowledge/upload")
                .header("Authorization", "Bearer " + token)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FILE_REJECTED");
    }
}
```

- [ ] **Step 2: 运行确认**

Run: `mvn -q -Dtest=KnowledgeControllerIntegrationTest test`
Expected: PASS（4 个测试全绿）。如果 FAIL，先看是不是 Redis（`localhost:6379`）没起来，或者前面某个 Task 的方法签名跟这里的调用对不上。

- [ ] **Step 3: 全量回归**

Run: `mvn -q test`
Expected: 全部 PASS（这次运行会覆盖 Task 1-6 新增的全部 30+ 个测试，加上项目原有的全部测试）

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/jowo/agent/knowledge/KnowledgeControllerIntegrationTest.java
git commit -m "$(cat <<'EOF'
test: add end-to-end integration test for knowledge review pipeline

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
```

---

## 验收标准

1. `mvn -q test` 全绿，包含本次新增的约 35 个测试（Repository 8 + FileStorage 5 + Summarize 2 + Service 10 + Controller 11 + Integration 4）。
2. `git log` 呈现本计划 6 个独立提交。
3. 手动冒烟（可选，仿照 Phase 1 的验收方式）：`mvn spring-boot:run` 起服务后，用 curl 走一遍 upload → list → approve → download，确认 `./lib/{id}.md` 和 `./lib/{id}.meta.json` 真的落到项目根目录的 `lib/` 文件夹里。
