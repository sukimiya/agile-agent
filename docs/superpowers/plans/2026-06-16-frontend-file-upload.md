# 前端文件上传 + AI 识别 / 附件 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 给 mcp-tool 加文件上传：用户在聊天框 📎 多选图片/PDF，OpenClaw 识别；AI 在需要时自动落 MinIO 并写入业务记录；AI 输出附下载按钮。

**Architecture:** 前端把文件 POST 给后端，后端落到 `~/.openclaw/workspace/uploads/<sessionId>/`，把绝对路径以 `[已上传文件]` 块注入到下一条 message，OpenClaw 用 Read 工具读文件 + 按上下文用 bash+curl 完成 MinIO 上传。

**Tech Stack:** Spring Boot 3 + Apache Tika · React 19 + Vite + Vitest · Zustand · MinIO presigned URL（已有）

**Spec:** [docs/superpowers/specs/2026-06-16-frontend-file-upload-design.md](../specs/2026-06-16-frontend-file-upload-design.md)

---

## 跨仓边界

实施分布在两个 git 仓：

| 阶段 | 仓 | 远端 |
|---|---|---|
| Phase 1 (后端) + Phase 2 (前端) | `mcp-tool` | `git@codeup.aliyun.com:.../l7-skill-backend.git` |
| Phase 3 (enterprise-api skill 文档) | `l7-skill`（位于 `~/.openclaw/workspace/.openclaw/skills/enterprise-api/`） | `git@codeup.aliyun.com:.../l7-skill.git` |

每个 phase 最后一个 task 是 push 边界。

## File Structure

**新建：**
```
mcp-tool/
  src/main/java/com/jowo/agent/
    config/UploadProperties.java               # @ConfigurationProperties
    controller/UploadController.java            # POST /api/files/upload
    service/FileStorageService.java             # 落盘 / 校验 / 清理
    service/UploadCleanupTask.java              # @Scheduled
  src/test/java/com/jowo/agent/
    service/FileStorageServiceTest.java
    service/UploadCleanupTaskTest.java
    controller/UploadControllerTest.java
  web/src/
    api/upload.ts                               # uploadFiles()
    components/Attachment.tsx                   # 单文件 chip
    components/DownloadButton.tsx               # AI 消息下载按钮
  web/src/__tests__/
    api/upload.test.ts
    components/Attachment.test.tsx
    components/DownloadButton.test.tsx
  web/vitest.config.ts                          # 测试配置
  web/src/setupTests.ts                         # jest-dom + jsdom
```

**修改：**
```
mcp-tool/
  pom.xml                                       # 加 tika-core
  src/main/java/com/jowo/agent/AgentApplication.java   # @EnableScheduling
  src/main/resources/application.yml            # app.upload.* + multipart 上限
  web/package.json                              # 加 vitest / @testing-library
  web/vite.config.ts                            # 加 test 字段
  web/src/store.ts                              # 加 pendingFiles 切片
  web/src/App.tsx                               # 上传 + 注入 [已上传文件] 块
  web/src/Chat.tsx                              # 📎 按钮 + chip 区 + DownloadButton

~/.openclaw/workspace/.openclaw/skills/enterprise-api/
  crm.md                                        # 顶部加「文件上传通用流程」
```

---

# Phase 1: 后端（mcp-tool 仓）

## Task 1: 依赖、配置、Properties 类

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/java/com/jowo/agent/AgentApplication.java`
- Create: `src/main/java/com/jowo/agent/config/UploadProperties.java`

- [ ] **Step 1: 在 pom.xml 加 Tika 依赖**

在 `</dependencies>` 上方加：

```xml
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-core</artifactId>
    <version>3.2.0</version>
</dependency>
```

- [ ] **Step 2: 在 application.yml 加上传配置 + 放开 multipart**

⚠️ 现有 yaml 已有 `spring:` 顶级键（含 `application.name`）。**必须合并到同一个 `spring:` 块下**，不能再写一个 `spring:`（YAML 非法）。改完应该长这样：

```yaml
spring:
  application:
    name: agent
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 110MB

server:
  port: 8088

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
```

- [ ] **Step 3: 在 AgentApplication.java 启用定时任务**

在 `@SpringBootApplication` 下加 `@EnableScheduling`：

```java
package com.jowo.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }
}
```

- [ ] **Step 4: 创建 UploadProperties.java**

```java
package com.jowo.agent.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.upload")
public class UploadProperties {
    private String dir;
    private int ttlHours = 24;
    private int maxSizeMb = 10;
    private int maxFilesPerRequest = 10;
    private List<String> allowedContentTypes = List.of();

    public String getDir() { return dir; }
    public void setDir(String dir) { this.dir = dir; }
    public int getTtlHours() { return ttlHours; }
    public void setTtlHours(int v) { this.ttlHours = v; }
    public int getMaxSizeMb() { return maxSizeMb; }
    public void setMaxSizeMb(int v) { this.maxSizeMb = v; }
    public int getMaxFilesPerRequest() { return maxFilesPerRequest; }
    public void setMaxFilesPerRequest(int v) { this.maxFilesPerRequest = v; }
    public List<String> getAllowedContentTypes() { return allowedContentTypes; }
    public void setAllowedContentTypes(List<String> v) { this.allowedContentTypes = v; }

    public long getMaxSizeBytes() { return maxSizeMb * 1024L * 1024L; }
    public long getTtlMillis() { return ttlHours * 3600_000L; }
}
```

- [ ] **Step 5: 编译验证 + commit**

```bash
mvn -q compile
```

期望：编译通过无错误。

```bash
git add pom.xml src/main/resources/application.yml src/main/java/com/jowo/agent/AgentApplication.java src/main/java/com/jowo/agent/config/UploadProperties.java
git commit -m "feat(upload): add tika dep, upload config, enable scheduling"
```

---

## Task 2: FileStorageService — 落盘 + 冲突重命名（TDD）

**Files:**
- Create: `src/main/java/com/jowo/agent/service/FileStorageService.java`
- Create: `src/test/java/com/jowo/agent/service/FileStorageServiceTest.java`

- [ ] **Step 1: 写第一个失败测试 — 基础落盘**

```java
package com.jowo.agent.service;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import com.jowo.agent.config.UploadProperties;

class FileStorageServiceTest {

    @TempDir Path tempDir;
    UploadProperties props;
    FileStorageService service;

    @BeforeEach
    void setUp() {
        props = new UploadProperties();
        props.setDir(tempDir.toString());
        props.setMaxSizeMb(10);
        props.setMaxFilesPerRequest(10);
        props.setAllowedContentTypes(List.of("image/png", "application/pdf"));
        service = new FileStorageService(props);
    }

    @Test
    void savesFileToSessionDir() throws Exception {
        var file = new MockMultipartFile("files", "photo.png", "image/png", pngBytes());
        var ref = service.save("conv-123", file);
        assertEquals("photo.png", ref.name());
        assertTrue(ref.path().endsWith("/conv-123/photo.png"));
        assertTrue(Files.exists(Path.of(ref.path())));
    }

    /** Minimal valid PNG (1x1 transparent). */
    static byte[] pngBytes() {
        return new byte[] {
            (byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte)0xC4,
            (byte)0x89, 0x00, 0x00, 0x00, 0x0D, 0x49, 0x44, 0x41,
            0x54, 0x78, (byte)0x9C, 0x62, 0x00, 0x01, 0x00, 0x00,
            0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, (byte)0xB4, 0x00,
            0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, (byte)0xAE,
            0x42, 0x60, (byte)0x82
        };
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
mvn -q test -Dtest=FileStorageServiceTest#savesFileToSessionDir
```

期望：编译失败，因为 `FileStorageService` 和 `Ref` 不存在。

- [ ] **Step 3: 写最小实现**

```java
package com.jowo.agent.service;

import java.io.IOException;
import java.nio.file.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.jowo.agent.config.UploadProperties;

@Service
public class FileStorageService {

    public record Ref(String name, String path, long size) {}

    private final UploadProperties props;

    public FileStorageService(UploadProperties props) {
        this.props = props;
    }

    public Ref save(String sessionId, MultipartFile file) throws IOException {
        var dir = Path.of(props.getDir(), sanitize(sessionId));
        Files.createDirectories(dir);
        var name = sanitize(file.getOriginalFilename());
        var target = resolveUnique(dir, name);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        return new Ref(target.getFileName().toString(), target.toAbsolutePath().toString(), file.getSize());
    }

    private static String sanitize(String name) {
        if (name == null || name.isBlank()) return "file";
        var basename = name.replaceAll("[\\\\/]", "_").replaceAll("\\.\\.+", "_");
        return basename.isBlank() ? "file" : basename;
    }

    private static Path resolveUnique(Path dir, String name) {
        var target = dir.resolve(name);
        if (!Files.exists(target)) return target;
        var dotIdx = name.lastIndexOf('.');
        var stem = dotIdx > 0 ? name.substring(0, dotIdx) : name;
        var ext = dotIdx > 0 ? name.substring(dotIdx) : "";
        for (int i = 1; i < 1000; i++) {
            var candidate = dir.resolve(stem + "-" + i + ext);
            if (!Files.exists(candidate)) return candidate;
        }
        throw new IllegalStateException("Too many collisions for " + name);
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

```bash
mvn -q test -Dtest=FileStorageServiceTest#savesFileToSessionDir
```

期望：PASS。

- [ ] **Step 5: 加冲突重命名测试**

加到 `FileStorageServiceTest`：

```java
@Test
void renamesOnCollision() throws Exception {
    var f1 = new MockMultipartFile("files", "photo.png", "image/png", pngBytes());
    var f2 = new MockMultipartFile("files", "photo.png", "image/png", pngBytes());
    var ref1 = service.save("conv-x", f1);
    var ref2 = service.save("conv-x", f2);
    assertNotEquals(ref1.path(), ref2.path());
    assertTrue(ref2.path().endsWith("/photo-1.png"), "got: " + ref2.path());
}
```

- [ ] **Step 6: 运行所有 FileStorageServiceTest**

```bash
mvn -q test -Dtest=FileStorageServiceTest
```

期望：2 PASS。

- [ ] **Step 7: 加 path traversal 防护测试**

```java
@Test
void rejectsPathTraversal() throws Exception {
    var bad = new MockMultipartFile("files", "../etc/passwd", "image/png", pngBytes());
    var ref = service.save("conv-x", bad);
    assertFalse(ref.path().contains(".."));
    assertTrue(ref.path().endsWith("/.._etc_passwd") || ref.path().endsWith("/_etc_passwd"));
}
```

- [ ] **Step 8: 运行测试通过**

```bash
mvn -q test -Dtest=FileStorageServiceTest
```

期望：3 PASS。

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/jowo/agent/service/FileStorageService.java src/test/java/com/jowo/agent/service/FileStorageServiceTest.java
git commit -m "feat(upload): FileStorageService save + collision rename + path traversal"
```

---

## Task 3: FileStorageService — Magic byte 校验 + 类型/大小校验

**Files:**
- Modify: `src/main/java/com/jowo/agent/service/FileStorageService.java`
- Modify: `src/test/java/com/jowo/agent/service/FileStorageServiceTest.java`

- [ ] **Step 1: 写 magic byte 不匹配的失败测试**

加到 `FileStorageServiceTest`：

```java
@Test
void rejectsMimeMismatch() {
    // 把 plain text 假装成 PNG 上传
    var fake = new MockMultipartFile("files", "fake.png", "image/png", "hello".getBytes());
    var ex = assertThrows(FileStorageService.RejectedException.class, () -> service.save("conv-x", fake));
    assertEquals("MIME_MISMATCH", ex.getCode());
}

@Test
void rejectsTooLarge() {
    var big = new byte[11 * 1024 * 1024];
    var f = new MockMultipartFile("files", "big.pdf", "application/pdf", big);
    var ex = assertThrows(FileStorageService.RejectedException.class, () -> service.save("conv-x", f));
    assertEquals("FILE_REJECTED", ex.getCode());
}

@Test
void rejectsForbiddenType() {
    var doc = new MockMultipartFile("files", "x.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", pngBytes());
    var ex = assertThrows(FileStorageService.RejectedException.class, () -> service.save("conv-x", doc));
    assertEquals("FILE_REJECTED", ex.getCode());
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
mvn -q test -Dtest=FileStorageServiceTest
```

期望：3 个新测试编译/运行失败。

- [ ] **Step 3: 加 RejectedException 内部类 + 校验逻辑**

替换 `FileStorageService.java` 的 `save` 方法 + 加内部类：

```java
public static class RejectedException extends RuntimeException {
    private final String code;
    public RejectedException(String code, String message) {
        super(message);
        this.code = code;
    }
    public String getCode() { return code; }
}

public Ref save(String sessionId, MultipartFile file) throws IOException {
    if (file.getSize() > props.getMaxSizeBytes()) {
        throw new RejectedException("FILE_REJECTED", "file exceeds " + props.getMaxSizeMb() + " MB");
    }
    var declaredType = file.getContentType();
    if (!props.getAllowedContentTypes().contains(declaredType)) {
        throw new RejectedException("FILE_REJECTED", "content-type " + declaredType + " not allowed");
    }
    var detector = new org.apache.tika.detect.DefaultDetector();
    var metadata = new org.apache.tika.metadata.Metadata();
    String detected;
    try (var is = new java.io.BufferedInputStream(file.getInputStream())) {
        detected = detector.detect(is, metadata).toString();
    }
    if (!props.getAllowedContentTypes().contains(detected)) {
        throw new RejectedException("MIME_MISMATCH", "real type " + detected + " differs from declared " + declaredType);
    }

    var dir = Path.of(props.getDir(), sanitize(sessionId));
    Files.createDirectories(dir);
    var name = sanitize(file.getOriginalFilename());
    var target = resolveUnique(dir, name);
    Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
    return new Ref(target.getFileName().toString(), target.toAbsolutePath().toString(), file.getSize());
}
```

- [ ] **Step 4: 运行所有测试**

```bash
mvn -q test -Dtest=FileStorageServiceTest
```

期望：6 PASS（3 原有 + 3 新加）。

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/jowo/agent/service/FileStorageService.java src/test/java/com/jowo/agent/service/FileStorageServiceTest.java
git commit -m "feat(upload): magic byte + size + content-type validation"
```

---

## Task 4: UploadCleanupTask（TDD）

**Files:**
- Create: `src/main/java/com/jowo/agent/service/UploadCleanupTask.java`
- Create: `src/test/java/com/jowo/agent/service/UploadCleanupTaskTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.jowo.agent.service;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import com.jowo.agent.config.UploadProperties;

class UploadCleanupTaskTest {

    @TempDir Path tempDir;

    @Test
    void deletesDirectoriesOlderThanTtl() throws Exception {
        var oldDir = tempDir.resolve("conv-old");
        var newDir = tempDir.resolve("conv-new");
        Files.createDirectories(oldDir);
        Files.createDirectories(newDir);
        Files.writeString(oldDir.resolve("x.txt"), "x");
        Files.writeString(newDir.resolve("y.txt"), "y");
        Files.setLastModifiedTime(oldDir, FileTime.from(Instant.now().minusSeconds(48 * 3600)));

        var props = new UploadProperties();
        props.setDir(tempDir.toString());
        props.setTtlHours(24);

        new UploadCleanupTask(props).cleanup();

        assertFalse(Files.exists(oldDir), "old dir should be deleted");
        assertTrue(Files.exists(newDir), "new dir should remain");
    }

    @Test
    void noOpWhenDirMissing() {
        var props = new UploadProperties();
        props.setDir(tempDir.resolve("does-not-exist").toString());
        props.setTtlHours(24);
        assertDoesNotThrow(() -> new UploadCleanupTask(props).cleanup());
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
mvn -q test -Dtest=UploadCleanupTaskTest
```

期望：编译失败。

- [ ] **Step 3: 写实现**

```java
package com.jowo.agent.service;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import org.slf4j.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.jowo.agent.config.UploadProperties;

@Component
public class UploadCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(UploadCleanupTask.class);
    private final UploadProperties props;

    public UploadCleanupTask(UploadProperties props) {
        this.props = props;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        cleanup();
    }

    @Scheduled(fixedRate = 3_600_000L)
    public void scheduledCleanup() {
        cleanup();
    }

    public void cleanup() {
        var root = Path.of(props.getDir());
        if (!Files.isDirectory(root)) return;
        var cutoff = Instant.now().minusMillis(props.getTtlMillis());
        try (var stream = Files.list(root)) {
            stream.filter(Files::isDirectory).forEach(p -> {
                try {
                    if (Files.getLastModifiedTime(p).toInstant().isBefore(cutoff)) {
                        deleteRecursively(p);
                    }
                } catch (IOException e) {
                    log.warn("cleanup error for {}: {}", p, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.warn("cleanup list error: {}", e.getMessage());
        }
    }

    private static void deleteRecursively(Path p) throws IOException {
        try (var walk = Files.walk(p)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(child -> {
                try { Files.deleteIfExists(child); } catch (IOException ignored) {}
            });
        }
    }
}
```

- [ ] **Step 4: 运行测试通过**

```bash
mvn -q test -Dtest=UploadCleanupTaskTest
```

期望：2 PASS。

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/jowo/agent/service/UploadCleanupTask.java src/test/java/com/jowo/agent/service/UploadCleanupTaskTest.java
git commit -m "feat(upload): scheduled + startup cleanup of expired session dirs"
```

---

## Task 5: UploadController（TDD）

**Files:**
- Create: `src/main/java/com/jowo/agent/controller/UploadController.java`
- Create: `src/test/java/com/jowo/agent/controller/UploadControllerTest.java`

- [ ] **Step 1: 写失败测试 — 成功多文件上传**

```java
package com.jowo.agent.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.junit.jupiter.api.BeforeEach;

@SpringBootTest
@TestPropertySource(properties = {
    "app.upload.dir=${java.io.tmpdir}/upload-test",
    "app.upload.allowed-content-types=image/png,application/pdf"
})
class UploadControllerTest {

    @Autowired WebApplicationContext ctx;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(ctx).build();
    }

    @Test
    void uploadsMultipleFiles() throws Exception {
        var f1 = new MockMultipartFile("files", "a.png", "image/png", FileStorageServiceTestData.PNG);
        var f2 = new MockMultipartFile("files", "b.png", "image/png", FileStorageServiceTestData.PNG);
        mvc.perform(multipart("/api/files/upload")
                .file(f1).file(f2)
                .param("sessionId", "conv-it"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].name").value("a.png"))
            .andExpect(jsonPath("$[0].path").exists());
    }

    @Test
    void rejectsMissingSessionId() throws Exception {
        var f = new MockMultipartFile("files", "a.png", "image/png", FileStorageServiceTestData.PNG);
        mvc.perform(multipart("/api/files/upload").file(f))
            .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsTooManyFiles() throws Exception {
        var req = multipart("/api/files/upload").param("sessionId", "conv-many");
        for (int i = 0; i < 11; i++) {
            req = req.file(new MockMultipartFile("files", "a" + i + ".png", "image/png", FileStorageServiceTestData.PNG));
        }
        mvc.perform(req)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("FILE_REJECTED"));
    }
}
```

加一个数据辅助类 `src/test/java/com/jowo/agent/controller/FileStorageServiceTestData.java`：

```java
package com.jowo.agent.controller;

class FileStorageServiceTestData {
    static final byte[] PNG = new byte[] {
        (byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
        0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte)0xC4,
        (byte)0x89, 0x00, 0x00, 0x00, 0x0D, 0x49, 0x44, 0x41,
        0x54, 0x78, (byte)0x9C, 0x62, 0x00, 0x01, 0x00, 0x00,
        0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, (byte)0xB4, 0x00,
        0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, (byte)0xAE,
        0x42, 0x60, (byte)0x82
    };
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
mvn -q test -Dtest=UploadControllerTest
```

期望：编译失败（UploadController 不存在）。

- [ ] **Step 3: 写 UploadController 实现**

```java
package com.jowo.agent.controller;

import java.io.IOException;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.jowo.agent.config.UploadProperties;
import com.jowo.agent.service.FileStorageService;
import com.jowo.agent.service.FileStorageService.RejectedException;

@RestController
@RequestMapping("/api/files")
@CrossOrigin(origins = "*")
class UploadController {

    private final FileStorageService storage;
    private final UploadProperties props;

    UploadController(FileStorageService storage, UploadProperties props) {
        this.storage = storage;
        this.props = props;
    }

    @PostMapping("/upload")
    ResponseEntity<?> upload(
            @RequestParam("sessionId") String sessionId,
            @RequestParam("files") List<MultipartFile> files) {
        if (files.size() > props.getMaxFilesPerRequest()) {
            return ResponseEntity.badRequest().body(Map.of(
                "code", "FILE_REJECTED",
                "reason", "exceeds max files per request: " + props.getMaxFilesPerRequest()
            ));
        }
        var refs = new ArrayList<FileStorageService.Ref>();
        for (var f : files) {
            try {
                refs.add(storage.save(sessionId, f));
            } catch (RejectedException e) {
                return ResponseEntity.badRequest().body(Map.of(
                    "code", e.getCode(),
                    "reason", e.getMessage(),
                    "file", f.getOriginalFilename()
                ));
            } catch (IOException e) {
                return ResponseEntity.internalServerError().body(Map.of(
                    "code", "STORAGE_ERROR",
                    "reason", e.getMessage()
                ));
            }
        }
        return ResponseEntity.ok(refs);
    }
}
```

- [ ] **Step 4: 运行测试通过**

```bash
mvn -q test -Dtest=UploadControllerTest
```

期望：3 PASS。

- [ ] **Step 5: 跑全部 backend 测试做回归**

```bash
mvn -q test
```

期望：所有测试通过。

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/jowo/agent/controller/UploadController.java src/test/java/com/jowo/agent/controller/
git commit -m "feat(upload): POST /api/files/upload multipart endpoint"
```

---

## Task 6: 后端冒烟测试 + Push

- [ ] **Step 1: 启动后端**

```bash
./start.sh
sleep 5
tail -20 logs/backend.log
```

期望日志：看到 `Started AgentApplication` 和监听 :8088。

- [ ] **Step 2: curl 测试 upload endpoint**

```bash
echo -n -e '\x89PNG\r\n\x1a\n' > /tmp/test.png  # 极简 PNG signature（够 magic byte 识别）
# 用真实的 PNG 更可靠 — 从 web/src/assets 拷贝一张：
cp web/src/assets/hero.png /tmp/test.png 2>/dev/null || echo "(用上面的 dummy)"

curl -s -X POST http://localhost:8088/api/files/upload \
  -F "sessionId=conv-smoke" \
  -F "files=@/tmp/test.png" | python3 -m json.tool
```

期望：返回 JSON 数组，含 `name`、`path`、`size`。

```bash
ls ~/.openclaw/workspace/uploads/conv-smoke/
```

期望：看到 `test.png`（或冲突重命名）。

- [ ] **Step 3: 清理冒烟测试数据 + 停后端**

```bash
rm -rf ~/.openclaw/workspace/uploads/conv-smoke
kill $(cat logs/backend.pid) 2>/dev/null
```

- [ ] **Step 4: Push 后端 phase**

```bash
git log --oneline -6
git push
```

期望：5 个 commit（Task 1-5）push 成功。

---

# Phase 2: 前端（mcp-tool 仓）

## Task 7: Vitest + Testing Library 安装与配置

**Files:**
- Modify: `web/package.json`
- Modify: `web/vite.config.ts`
- Create: `web/src/setupTests.ts`
- Create: `web/vitest.config.ts`（或合并进 vite.config）

- [ ] **Step 1: 装依赖**

```bash
cd web
npm install -D vitest @testing-library/react @testing-library/jest-dom @testing-library/user-event jsdom
```

- [ ] **Step 2: 改 web/vite.config.ts —— 只追加 `test:` 字段，不要替换整个文件**

在第一行 `import { defineConfig } from 'vite'` 之前加：

```ts
/// <reference types="vitest" />
```

在 `defineConfig({...})` 的对象内（`server: {...}` 之后、最后的 `})` 之前）插入：

```ts
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/setupTests.ts'],
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
  },
```

保留原有的 `plugins`、`server`（含 `host`、`port`、`proxy` 等所有子字段）。不要破坏现有代理路由。

- [ ] **Step 3: 创建 setupTests.ts**

```ts
import '@testing-library/jest-dom';
```

- [ ] **Step 4: 加 test script 到 package.json**

在 `"scripts"` 段加：

```json
"test": "vitest run",
"test:watch": "vitest"
```

- [ ] **Step 5: 跑一遍空测试集合验证配置**

```bash
cd web && npm test 2>&1 | tail -10
```

期望：`No test files found` 或类似 — 配置 OK，只是还没有测试。

- [ ] **Step 6: Commit**

```bash
git add web/package.json web/package-lock.json web/vite.config.ts web/src/setupTests.ts
git commit -m "test: setup vitest + testing-library for frontend"
```

---

## Task 8: store.ts — 加 pendingFiles 切片

**Files:**
- Modify: `web/src/store.ts`
- Create: `web/src/__tests__/store.test.ts`

- [ ] **Step 1: 写失败测试**

```ts
// web/src/__tests__/store.test.ts
import { describe, it, expect, beforeEach } from 'vitest';
import { useStore } from '../store';

describe('store.pendingFiles', () => {
  beforeEach(() => {
    useStore.setState({ pendingFiles: [] });
  });

  it('starts empty', () => {
    expect(useStore.getState().pendingFiles).toEqual([]);
  });

  it('addFiles appends', () => {
    const f = new File(['x'], 'a.png', { type: 'image/png' });
    useStore.getState().addFiles([f]);
    expect(useStore.getState().pendingFiles).toHaveLength(1);
    expect(useStore.getState().pendingFiles[0].name).toBe('a.png');
  });

  it('removeFile removes by index', () => {
    const f1 = new File(['x'], 'a.png', { type: 'image/png' });
    const f2 = new File(['y'], 'b.png', { type: 'image/png' });
    useStore.getState().addFiles([f1, f2]);
    useStore.getState().removeFile(0);
    expect(useStore.getState().pendingFiles).toHaveLength(1);
    expect(useStore.getState().pendingFiles[0].name).toBe('b.png');
  });

  it('clearFiles empties', () => {
    const f = new File(['x'], 'a.png', { type: 'image/png' });
    useStore.getState().addFiles([f]);
    useStore.getState().clearFiles();
    expect(useStore.getState().pendingFiles).toEqual([]);
  });
});
```

- [ ] **Step 2: 跑测试看失败**

```bash
cd web && npm test 2>&1 | tail -15
```

期望：FAIL（`addFiles`、`pendingFiles` 不存在）。

- [ ] **Step 3: 改 store.ts 加切片**

替换为：

```ts
import { create } from "zustand";

export interface Conversation {
  id: string;
  title: string;
  createdAt: Date;
}

interface ChatState {
  conversations: Conversation[];
  activeId: string | null;
  setActiveId: (id: string) => void;
  addConversation: (conv: Conversation) => void;

  pendingFiles: File[];
  addFiles: (files: File[]) => void;
  removeFile: (index: number) => void;
  clearFiles: () => void;
}

export const useStore = create<ChatState>((set) => ({
  conversations: [],
  activeId: null,
  setActiveId: (id) => set({ activeId: id }),
  addConversation: (conv) =>
    set((s) => ({ conversations: [conv, ...s.conversations] })),

  pendingFiles: [],
  addFiles: (files) => set((s) => ({ pendingFiles: [...s.pendingFiles, ...files] })),
  removeFile: (index) =>
    set((s) => ({ pendingFiles: s.pendingFiles.filter((_, i) => i !== index) })),
  clearFiles: () => set({ pendingFiles: [] }),
}));
```

- [ ] **Step 4: 跑测试通过**

```bash
cd web && npm test 2>&1 | tail -10
```

期望：4 PASS。

- [ ] **Step 5: Commit**

```bash
git add web/src/store.ts web/src/__tests__/store.test.ts
git commit -m "feat(upload): zustand pendingFiles slice (add/remove/clear)"
```

---

## Task 9: api/upload.ts — uploadFiles 函数（TDD）

**Files:**
- Create: `web/src/api/upload.ts`
- Create: `web/src/__tests__/api/upload.test.ts`

- [ ] **Step 1: 写失败测试**

```ts
// web/src/__tests__/api/upload.test.ts
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { uploadFiles, UploadError } from '../../api/upload';

describe('uploadFiles', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('posts multipart with sessionId and returns refs', async () => {
    const fakeRefs = [{ name: 'a.png', path: '/tmp/conv/a.png', size: 100 }];
    vi.stubGlobal('fetch', vi.fn(async () => ({
      ok: true,
      status: 200,
      json: async () => fakeRefs,
    })));

    const file = new File(['x'], 'a.png', { type: 'image/png' });
    const refs = await uploadFiles('conv-1', [file]);
    expect(refs).toEqual(fakeRefs);

    const call = (globalThis.fetch as any).mock.calls[0];
    expect(call[0]).toBe('/api/files/upload');
    expect(call[1].method).toBe('POST');
    const body = call[1].body as FormData;
    expect(body.get('sessionId')).toBe('conv-1');
    expect(body.getAll('files')).toHaveLength(1);
  });

  it('throws UploadError with code on 4xx response', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({
      ok: false,
      status: 400,
      json: async () => ({ code: 'FILE_REJECTED', reason: 'too big' }),
    })));
    const file = new File(['x'], 'a.png', { type: 'image/png' });
    await expect(uploadFiles('conv-1', [file])).rejects.toMatchObject({
      code: 'FILE_REJECTED',
    });
  });
});
```

- [ ] **Step 2: 跑测试看失败**

```bash
cd web && npm test 2>&1 | tail -10
```

期望：FAIL（模块不存在）。

- [ ] **Step 3: 写实现**

```ts
// web/src/api/upload.ts
export interface UploadedRef {
  name: string;
  path: string;
  size: number;
}

export class UploadError extends Error {
  constructor(public code: string, public reason: string) {
    super(`${code}: ${reason}`);
  }
}

export async function uploadFiles(
  sessionId: string,
  files: File[],
): Promise<UploadedRef[]> {
  const form = new FormData();
  form.set('sessionId', sessionId);
  for (const f of files) form.append('files', f);

  const res = await fetch('/api/files/upload', {
    method: 'POST',
    body: form,
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ code: 'UNKNOWN', reason: res.statusText }));
    throw new UploadError(err.code ?? 'UNKNOWN', err.reason ?? '');
  }
  return res.json();
}
```

- [ ] **Step 4: 跑测试通过**

```bash
cd web && npm test 2>&1 | tail -10
```

期望：2 PASS。

- [ ] **Step 5: Commit**

```bash
git add web/src/api/upload.ts web/src/__tests__/api/upload.test.ts
git commit -m "feat(upload): uploadFiles API client with UploadError"
```

---

## Task 10: Attachment.tsx 单文件 chip（TDD）

**Files:**
- Create: `web/src/components/Attachment.tsx`
- Create: `web/src/__tests__/components/Attachment.test.tsx`

- [ ] **Step 1: 写失败测试**

```tsx
// web/src/__tests__/components/Attachment.test.tsx
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { Attachment } from '../../components/Attachment';

describe('Attachment', () => {
  it('renders name and size', () => {
    render(<Attachment name="report.pdf" size={1024 * 1024 * 2} onRemove={() => {}} />);
    expect(screen.getByText('report.pdf')).toBeInTheDocument();
    expect(screen.getByText(/2.0 MB/)).toBeInTheDocument();
  });

  it('shows PDF icon for PDF', () => {
    render(<Attachment name="x.pdf" size={100} onRemove={() => {}} />);
    expect(screen.getByText('📄')).toBeInTheDocument();
  });

  it('shows image icon for image', () => {
    render(<Attachment name="x.png" size={100} onRemove={() => {}} />);
    expect(screen.getByText('🖼️')).toBeInTheDocument();
  });

  it('error state has red ring', () => {
    const { container } = render(<Attachment name="x.png" size={100} onRemove={() => {}} error="too big" />);
    expect(container.querySelector('[class*="border-red"]')).toBeTruthy();
    expect(screen.getByTitle('too big')).toBeInTheDocument();
  });

  it('calls onRemove when ❌ clicked', () => {
    const onRemove = vi.fn();
    render(<Attachment name="x.png" size={100} onRemove={onRemove} />);
    fireEvent.click(screen.getByLabelText('移除附件'));
    expect(onRemove).toHaveBeenCalled();
  });
});
```

- [ ] **Step 2: 跑测试看失败**

```bash
cd web && npm test -- Attachment 2>&1 | tail -10
```

- [ ] **Step 3: 写组件**

```tsx
// web/src/components/Attachment.tsx
function formatSize(n: number): string {
  if (n >= 1024 * 1024) return (n / 1024 / 1024).toFixed(1) + ' MB';
  if (n >= 1024) return (n / 1024).toFixed(0) + ' KB';
  return n + ' B';
}

function iconFor(name: string): string {
  const ext = name.split('.').pop()?.toLowerCase() ?? '';
  if (['png', 'jpg', 'jpeg', 'gif', 'webp'].includes(ext)) return '🖼️';
  if (ext === 'pdf') return '📄';
  return '📎';
}

interface Props {
  name: string;
  size: number;
  onRemove: () => void;
  error?: string;
}

export function Attachment({ name, size, onRemove, error }: Props) {
  const borderClass = error
    ? 'border-red-300 bg-red-50'
    : 'border-slate-200 bg-slate-50';
  return (
    <div
      className={`inline-flex items-center gap-2 px-2 py-1 rounded-md border ${borderClass} text-xs`}
      title={error}
    >
      <span>{iconFor(name)}</span>
      <span className="font-medium truncate max-w-[160px]">{name}</span>
      <span className="text-slate-400">{formatSize(size)}</span>
      <button
        type="button"
        aria-label="移除附件"
        onClick={onRemove}
        className="text-slate-400 hover:text-slate-600 cursor-pointer"
      >
        ❌
      </button>
    </div>
  );
}
```

- [ ] **Step 4: 跑测试通过**

```bash
cd web && npm test -- Attachment 2>&1 | tail -10
```

期望：5 PASS。

- [ ] **Step 5: Commit**

```bash
git add web/src/components/Attachment.tsx web/src/__tests__/components/Attachment.test.tsx
git commit -m "feat(upload): Attachment chip component"
```

---

## Task 11: DownloadButton.tsx（TDD）

**Files:**
- Create: `web/src/components/DownloadButton.tsx`
- Create: `web/src/__tests__/components/DownloadButton.test.tsx`

- [ ] **Step 1: 写失败测试**

```tsx
// web/src/__tests__/components/DownloadButton.test.tsx
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { DownloadButton, extractDownloadLinks } from '../../components/DownloadButton';

describe('extractDownloadLinks', () => {
  it('finds .xlsx and .pdf links', () => {
    const text = '看这个 [报表.xlsx](http://x.com/r.xlsx) 和 [合同.pdf](http://y.com/c.pdf)';
    const links = extractDownloadLinks(text);
    expect(links).toEqual([
      { label: '报表.xlsx', url: 'http://x.com/r.xlsx' },
      { label: '合同.pdf', url: 'http://y.com/c.pdf' },
    ]);
  });

  it('ignores links without download extensions', () => {
    const text = 'see [page](http://x.com/page) and [stuff](http://x.com/path)';
    expect(extractDownloadLinks(text)).toEqual([]);
  });

  it('matches .md .csv .zip .docx .doc .xls .txt', () => {
    const text = '[a.md](http://x/a.md) [b.csv](http://x/b.csv) [c.zip](http://x/c.zip) [d.docx](http://x/d.docx) [e.doc](http://x/e.doc) [f.xls](http://x/f.xls) [g.txt](http://x/g.txt)';
    expect(extractDownloadLinks(text)).toHaveLength(7);
  });
});

describe('DownloadButton', () => {
  it('saves text as .md when no download links found', () => {
    const createUrl = vi.fn(() => 'blob:fake');
    const revokeUrl = vi.fn();
    Object.defineProperty(URL, 'createObjectURL', { value: createUrl });
    Object.defineProperty(URL, 'revokeObjectURL', { value: revokeUrl });

    render(<DownloadButton content="纯文本内容" />);
    fireEvent.click(screen.getByText(/下载/));
    expect(createUrl).toHaveBeenCalled();
  });

  it('opens link directly for single download link', () => {
    const open = vi.spyOn(window, 'open').mockImplementation(() => null);
    render(<DownloadButton content="见 [r.xlsx](http://x.com/r.xlsx)" />);
    fireEvent.click(screen.getByText(/下载/));
    expect(open).toHaveBeenCalledWith('http://x.com/r.xlsx', '_blank');
  });

  it('shows dropdown menu for multiple links', () => {
    render(<DownloadButton content="[a.xlsx](http://x/a.xlsx) [b.pdf](http://x/b.pdf)" />);
    fireEvent.click(screen.getByText(/下载/));
    expect(screen.getByText('a.xlsx')).toBeInTheDocument();
    expect(screen.getByText('b.pdf')).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: 跑测试看失败**

```bash
cd web && npm test -- DownloadButton 2>&1 | tail -15
```

- [ ] **Step 3: 写实现**

```tsx
// web/src/components/DownloadButton.tsx
import { useState } from 'react';

const DOWNLOAD_EXT_RE = /\.(xlsx|xls|pdf|csv|zip|docx|doc|md|txt)$/i;

export interface DownloadLink {
  label: string;
  url: string;
}

export function extractDownloadLinks(content: string): DownloadLink[] {
  const re = /\[([^\]]+)\]\(([^)]+)\)/g;
  const links: DownloadLink[] = [];
  let m;
  while ((m = re.exec(content)) !== null) {
    if (DOWNLOAD_EXT_RE.test(m[1]) || DOWNLOAD_EXT_RE.test(m[2])) {
      links.push({ label: m[1], url: m[2] });
    }
  }
  return links;
}

function saveAsMarkdown(content: string) {
  const blob = new Blob([content], { type: 'text/markdown;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `chat-${Date.now()}.md`;
  a.click();
  URL.revokeObjectURL(url);
}

interface Props { content: string }

export function DownloadButton({ content }: Props) {
  const [open, setOpen] = useState(false);
  const links = extractDownloadLinks(content);

  const handleClick = () => {
    if (links.length === 0) {
      saveAsMarkdown(content);
    } else if (links.length === 1) {
      window.open(links[0].url, '_blank');
    } else {
      setOpen((v) => !v);
    }
  };

  return (
    <div className="relative inline-block">
      <button
        type="button"
        onClick={handleClick}
        className="text-xs text-slate-400 hover:text-slate-700 cursor-pointer px-2 py-0.5 rounded hover:bg-slate-100 transition-colors"
      >
        💾 下载{links.length > 1 ? ` ${links.length}` : ''}
      </button>
      {open && links.length > 1 && (
        <div className="absolute right-0 mt-1 bg-white border border-slate-200 rounded shadow-md py-1 min-w-[160px] z-10">
          {links.map((l, i) => (
            <a
              key={i}
              href={l.url}
              target="_blank"
              rel="noopener noreferrer"
              onClick={() => setOpen(false)}
              className="block px-3 py-1 text-xs hover:bg-slate-50"
            >
              {l.label}
            </a>
          ))}
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 4: 跑测试通过**

```bash
cd web && npm test -- DownloadButton 2>&1 | tail -15
```

期望：6 PASS。

- [ ] **Step 5: Commit**

```bash
git add web/src/components/DownloadButton.tsx web/src/__tests__/components/DownloadButton.test.tsx
git commit -m "feat(upload): DownloadButton with markdown link extraction"
```

---

## Task 12: App.tsx — 集成上传 + 包装 message

**Files:**
- Modify: `web/src/App.tsx`

- [ ] **Step 1: 改 App.tsx — 加 imports + 替换 send 函数**

**先在文件顶部 import 段加：**

```tsx
import { uploadFiles, UploadError } from "./api/upload";
import { useStore } from "./store";
```

**然后在组件函数内部、`const [convId] = useState(getConvId);` 这行之后加：**

```tsx
const pendingFiles = useStore((s) => s.pendingFiles);
const clearFiles = useStore((s) => s.clearFiles);
```

**最后替换 `send` 函数为下面的实现（注意已经包含上面新加的依赖）：**

```tsx
const send = useCallback(async (text?: string) => {
  const msg = (text ?? input).trim();
  if ((!msg && pendingFiles.length === 0) || isLoading) return;

  setInput("");
  setIsLoading(true);

  let uploadedRefs: { name: string; path: string }[] = [];
  if (pendingFiles.length > 0) {
    try {
      uploadedRefs = await uploadFiles(convId, pendingFiles);
    } catch (e) {
      const reason = e instanceof UploadError ? e.message : "上传失败";
      setMessages((prev) => [...prev, {
        id: genId(),
        role: "assistant",
        content: `文件上传失败：${reason}`,
      }]);
      setIsLoading(false);
      return;
    }
    clearFiles();
  }

  // 用户看到的纯文本（不含路径），API 发送的含 [已上传文件] 块
  const displayContent = msg || "（已上传文件）";
  const apiContent = uploadedRefs.length > 0
    ? `[已上传文件]\n${uploadedRefs.map(r => `- ${r.path}`).join("\n")}\n\n${msg}`
    : msg;

  const uid = genId();
  const aid = genId();
  const newMsgs: UIMessage[] = [
    ...messagesRef.current,
    { id: uid, role: "user", content: displayContent, attachments: uploadedRefs },
    { id: aid, role: "assistant", content: "" },
  ];
  setMessages(newMsgs);

  try {
    const res = await fetch("/api/chat/completions", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        model: "jowo-agent",
        messages: [{ role: "user", content: apiContent }],
        conversationId: convId,
        stream: true,
      }),
    });
    // ... rest of streaming logic unchanged
    if (!res.body) throw new Error("No stream body");
    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split("\n");
      buffer = lines.pop() || "";
      for (const line of lines) {
        const trimmed = line.trim();
        if (!trimmed.startsWith("data:")) continue;
        const jsonStr = trimmed.slice(5).trim();
        if (!jsonStr || jsonStr === "[DONE]") continue;
        try {
          const c = JSON.parse(jsonStr);
          const content = c?.choices?.[0]?.delta?.content || "";
          if (content) {
            setMessages((prev) => {
              const updated = [...prev];
              const last = updated[updated.length - 1];
              const sep = last.content ? "\n" : "";
              updated[updated.length - 1] = { ...last, content: last.content + sep + content };
              return updated;
            });
          }
        } catch { /* skip */ }
      }
    }
  } catch (err) {
    setMessages((prev) => {
      const updated = [...prev];
      updated[updated.length - 1] = {
        ...updated[updated.length - 1],
        content: "请求失败: " + (err instanceof Error ? err.message : "未知错误"),
      };
      return updated;
    });
  } finally {
    setIsLoading(false);
  }
}, [input, isLoading, pendingFiles, clearFiles, convId]);
```

- [ ] **Step 2: 更新 UIMessage 接口加 attachments**

```tsx
interface UIMessage {
  id: string;
  role: "user" | "assistant";
  content: string;
  attachments?: { name: string; path: string }[];
}
```

- [ ] **Step 3: 编译验证**

```bash
cd web && npx tsc -b 2>&1 | tail -10
```

期望：无错误。

- [ ] **Step 4: Commit**

```bash
git add web/src/App.tsx
git commit -m "feat(upload): integrate upload + [已上传文件] message wrapping in send"
```

---

## Task 13: Chat.tsx — 📎 按钮、chip 区、DownloadButton

**Files:**
- Modify: `web/src/Chat.tsx`

- [ ] **Step 1: 改 ChatProps 接口加 pendingFiles 相关**

在 `Chat.tsx` 顶部：

```tsx
import { useRef, useEffect, useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { Prism as SyntaxHighlighter } from "react-syntax-highlighter";
import { oneDark } from "react-syntax-highlighter/dist/esm/styles/prism";
import { Attachment } from "./components/Attachment";
import { DownloadButton } from "./components/DownloadButton";
import { useStore } from "./store";

interface Message {
  id: string;
  role: "user" | "assistant";
  content: string;
  attachments?: { name: string; path: string }[];
}

const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp', 'application/pdf'];
const MAX_SIZE = 10 * 1024 * 1024;
const MAX_FILES = 10;
```

- [ ] **Step 2: 在 Chat 组件加 file picker + 校验**

在 `bottomRef` 后加：

```tsx
const fileInputRef = useRef<HTMLInputElement>(null);
const pendingFiles = useStore((s) => s.pendingFiles);
const addFiles = useStore((s) => s.addFiles);
const removeFile = useStore((s) => s.removeFile);
const [fileError, setFileError] = useState<string>("");

const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
  const files = Array.from(e.target.files ?? []);
  if (files.length === 0) return;

  if (pendingFiles.length + files.length > MAX_FILES) {
    setFileError(`一次最多 ${MAX_FILES} 个文件`);
    e.target.value = "";
    return;
  }
  for (const f of files) {
    if (!ALLOWED_TYPES.includes(f.type)) {
      setFileError(`${f.name}：仅支持图片和 PDF（Word/Excel 请先导出为 PDF）`);
      e.target.value = "";
      return;
    }
    if (f.size > MAX_SIZE) {
      setFileError(`${f.name}：超出 10 MB 上限`);
      e.target.value = "";
      return;
    }
  }
  addFiles(files);
  setFileError("");
  e.target.value = "";
};
```

- [ ] **Step 3: 在 form 上方插入 chip 区**

在 `<form>` 之前：

```tsx
{pendingFiles.length > 0 && (
  <div className="px-4 pb-2 flex flex-wrap gap-2">
    {pendingFiles.map((f, i) => (
      <Attachment
        key={i}
        name={f.name}
        size={f.size}
        onRemove={() => removeFile(i)}
      />
    ))}
  </div>
)}
{fileError && (
  <div className="px-4 pb-1 text-xs text-red-500">{fileError}</div>
)}
```

- [ ] **Step 4: 在 input 旁加 📎 按钮**

替换原 `<div className="flex gap-3">` 块为：

```tsx
<div className="flex gap-2 items-center">
  <input
    type="file"
    multiple
    accept="image/jpeg,image/png,image/gif,image/webp,application/pdf"
    ref={fileInputRef}
    onChange={handleFileSelect}
    className="hidden"
  />
  <button
    type="button"
    onClick={() => fileInputRef.current?.click()}
    disabled={isLoading || pendingFiles.length >= MAX_FILES}
    className="px-3 py-2.5 text-slate-400 hover:text-primary disabled:opacity-30 disabled:cursor-not-allowed cursor-pointer"
    aria-label="上传文件"
  >
    📎
  </button>
  <input
    value={input || ""}
    onChange={(e) => onInputChange(e.target.value)}
    placeholder="输入您的问题..."
    className="flex-1 px-4 py-2.5 border border-slate-200 rounded-lg text-sm
      focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary/20
      bg-slate-50 transition-colors"
    disabled={isLoading}
  />
  <button
    type="submit"
    disabled={isLoading || (!(input || "").trim() && pendingFiles.length === 0)}
    className="px-5 py-2.5 bg-primary text-white text-sm font-medium rounded-lg
      hover:bg-primary-light disabled:opacity-40 disabled:cursor-not-allowed
      transition-colors cursor-pointer"
  >
    发送
  </button>
</div>
```

- [ ] **Step 5: 在用户消息渲染里显示 attachments**

在 user 消息体内 `<p className="whitespace-pre-wrap">{m.content}</p>` 之前加：

```tsx
{m.attachments && m.attachments.length > 0 && (
  <div className="flex flex-wrap gap-1.5 mb-2">
    {m.attachments.map((a, i) => (
      <span key={i} className="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-white/20 text-xs">
        📎 {a.name}
      </span>
    ))}
  </div>
)}
```

- [ ] **Step 6: 在 assistant 消息底部加 DownloadButton**

替换 assistant 消息渲染里 `<MarkdownContent content={m.content} />` 之后的 `<div className="text-xs text-slate-400 mt-1 text-right">` 块为：

```tsx
<div className="flex items-center justify-end gap-2 mt-1">
  <DownloadButton content={m.content} />
  <span className="text-xs text-slate-400">
    ~{formatTokens(estimateTokens(m.content))} tokens
  </span>
</div>
```

- [ ] **Step 7: 编译验证**

```bash
cd web && npx tsc -b 2>&1 | tail -10
```

期望：无错误。

- [ ] **Step 8: Commit**

```bash
git add web/src/Chat.tsx
git commit -m "feat(upload): 📎 picker + chip area + download button per AI msg"
```

---

## Task 14: 端到端冒烟测试

- [ ] **Step 1: 重启服务**

```bash
cd /Users/breannalinlin/Documents/code/jowo/mcp-tool
kill $(cat logs/backend.pid logs/frontend.pid 2>/dev/null) 2>/dev/null
./start.sh
sleep 8
tail -10 logs/backend.log
```

期望：两服务正常启动。

- [ ] **Step 2: 浏览器打开 http://localhost:5173**

手动测试清单：

1. **场景 A（识别）**：点 📎 选一张图片 → 看到 chip → 输入"这张图是什么？"→ 发送 → 看到 AI 描述图片
2. **chip 操作**：上传 2 张图，点其中一张的 ❌ → 只剩 1 张
3. **超额校验**：尝试上传 .docx → 看到红色错误「仅支持图片和 PDF」
4. **大小校验**：尝试上传 > 10MB 文件 → 看到「超出 10 MB 上限」
5. **场景 B（附件，需要 enterprise-api skill 已包含上传文档 — 见 Phase 3）**：上传图 + 输入"帮多明客户提个反馈：设备报警有延迟，附上现场照"→ AI 应该执行多步 tool call，最后告诉你反馈已创建
6. **下载按钮（纯文本）**：找一条 AI 消息，点右下 💾 → 浏览器下载 chat-xxx.md
7. **下载按钮（文件链接）**：让 AI 输出含 markdown 文件链接的消息（暂时手动构造，等真实导出接口）→ 点 💾 → 浏览器跳转下载

- [ ] **Step 3: 验证上传后服务器有文件**

```bash
ls -la ~/.openclaw/workspace/uploads/
```

期望：看到 `<convId>/<filename>` 目录结构。

- [ ] **Step 4: 跑全部前后端测试做最终回归**

```bash
cd /Users/breannalinlin/Documents/code/jowo/mcp-tool
mvn -q test
cd web && npm test 2>&1 | tail -10
```

期望：全部通过。

- [ ] **Step 5: 停服务 + Push 前端 phase**

```bash
cd /Users/breannalinlin/Documents/code/jowo/mcp-tool
kill $(cat logs/backend.pid logs/frontend.pid 2>/dev/null) 2>/dev/null
git log --oneline -10
git push
```

期望：Phase 2 所有 commit（Task 7-13）push 成功。

---

# Phase 3: enterprise-api skill（l7-skill 仓）

## Task 15: crm.md 加文件上传通用流程

**Files:**
- Modify: `~/.openclaw/workspace/.openclaw/skills/enterprise-api/crm.md`

- [ ] **Step 1: 编辑 crm.md 在顶部插入新段落**

在文件最顶部（# CRM 标题之后、## 反馈单管理 之前）插入：

```markdown
---

## 文件上传通用流程（跨服务复用）

当用户已上传文件（出现在 `[已上传文件]` 块里）且任务需要把文件登记到业务记录里（创建反馈带附件、上传工艺文件、保存检验报告等），按下面两步流程上传到 MinIO，再用返回的 key 填进业务接口的 `attachments` 字段。

### 何时上传

- ✓ 用户说"创建/提交/记录/反馈/工单/工艺文件"+ 暗示或明示要附件
- ✓ 用户说"把这个文件存下"——反问要存到哪个业务域
- ✗ 用户只是问"这是什么/识别一下/什么意思"——不要上传

### 两步 bash 流程

```bash
# 1) 申请上传 URL：fileNum 通常是 1（每次循环 1 个文件），parentDir 由业务域决定（feedback / process / inspection 等）
RESP=$(curl -s "http://192.168.3.234:8888/crm-service/api/file/request-upload?fileNum=1&parentDir=feedback&fileNames=<原文件名>" \
  -H "x-access-token: $X_ACCESS_TOKEN")

# 解析返回的 presigned URL 和 minio key（key 才是要填进业务接口的）
URL=$(echo "$RESP" | python3 -c "import json,sys;print(json.load(sys.stdin)['data'][0]['url'])")
KEY=$(echo "$RESP" | python3 -c "import json,sys;print(json.load(sys.stdin)['data'][0]['key'])")

# 2) PUT 文件内容到 presigned URL（Content-Type 按实际 mime 给）
curl -s -X PUT --data-binary @<message 里 [已上传文件] 块中的绝对路径> \
  -H 'Content-Type: image/jpeg' \
  "$URL"

# 3) 用 $KEY 填业务接口的 attachments 字段，例如：
curl -s -X POST 'http://192.168.3.234:8888/crm-service/api/feedback/create' \
  -H "x-access-token: $X_ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"customerId\":\"...\",\"problemType\":\"3\",\"problemDescription\":\"...\",\"createrId\":\"...\",\"attachments\":[\"$KEY\"]}"
```

多文件就循环这两步，最后把所有 key 放进 attachments 数组。

### 容错

- `request-upload` 返回 5xx：重试 1 次；仍失败告诉用户「文件上传到 MinIO 失败，业务记录已创建但附件未关联，错误码 XXX」
- PUT 超时 / 网络错：同上
- 业务接口创建失败但 MinIO 已成功：告诉用户「文件已存档但业务记录创建失败，请稍后重试 — 不需要重新上传」
- 不要盲猜重试 ≥ 2 次（参见规则 R1）

### 隐私

不向用户透露 MinIO key、presigned URL、文件绝对路径等内部字段（参见 SOUL.md 规则 R3）。

---
```

- [ ] **Step 2: 验证 markdown 渲染正常（grep 检查）**

```bash
grep -A2 "文件上传通用流程" ~/.openclaw/workspace/.openclaw/skills/enterprise-api/crm.md
```

期望：能看到段标题。

- [ ] **Step 3: Commit + Push**

```bash
cd ~/.openclaw/workspace/.openclaw/skills/enterprise-api
git add crm.md
git commit -m "feat(crm): document file upload (request-upload + presigned PUT) flow"
git push
```

期望：push 到 `l7-skill.git`。

- [ ] **Step 4: 验证 AI 能用到这段文档**

回到 mcp-tool 启动服务：

```bash
cd /Users/breannalinlin/Documents/code/jowo/mcp-tool
./start.sh
sleep 8
```

浏览器测试场景 B：上传一张图 + "帮多明客户提个反馈：设备报警有延迟，附上现场照"

期望：AI 完整执行多步 tool call（查客户、读文档、申请 URL、PUT 文件、调 feedback/create），最后输出"已为多明提交反馈，附件已上传"。

- [ ] **Step 5: 清理服务**

```bash
kill $(cat logs/backend.pid logs/frontend.pid 2>/dev/null) 2>/dev/null
```

---

# 完成检查

实施完成后核对：

- [ ] mcp-tool 仓有 14 个新 commit（Phase 1 + 2），全部 push 到 `l7-skill-backend.git`
- [ ] l7-skill 仓有 1 个新 commit（Phase 3），push 到 `l7-skill.git`
- [ ] 后端 `mvn test` 全绿（FileStorageServiceTest 6+ / UploadCleanupTaskTest 2 / UploadControllerTest 3）
- [ ] 前端 `npm test` 全绿（store 4 / upload 2 / Attachment 5 / DownloadButton 6）
- [ ] 端到端手测场景 A、B、下载都跑通
- [ ] `~/.openclaw/workspace/uploads/<sessionId>/` 下能看到实际落盘文件
- [ ] 隔天/重启后能看到 cleanup 任务把 > 24h 的目录删了（可以手动改目录 mtime 测）
