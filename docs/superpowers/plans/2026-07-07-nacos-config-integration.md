# Nacos 配置中心接入 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the `agent`/`l7-skill-backend` Spring Boot app pull its `dev` configuration from Nacos instead of a committed plaintext yml, while `local` stays fully offline, and pre-wire (but don't use) MySQL connectivity for future work.

**Architecture:** Add `spring-cloud-starter-alibaba-nacos-config` (config only, no service discovery) and use Spring Boot's `spring.config.import` mechanism inside a `dev`-profile-scoped YAML document in `application.yml`. `local` profile is untouched except for a new, currently-unused MySQL datasource block. `application-dev.yml`'s secrets are deleted from the repo since they now live in the already-created Nacos entry `l7-skill-backend-dev.yaml` (namespace `jowo-im-dev`, group `DEFAULT_GROUP`).

**Tech Stack:** Spring Boot 3.5.0, Java 21, Maven, `spring-cloud-alibaba` 2025.0.0.0 / `spring-cloud` 2025.0.0 (versions verified against the sibling `crm-service` project's `pom.xml`, which runs the same stack in production).

## Global Constraints

- Do **not** add `spring-cloud-starter-alibaba-nacos-discovery` — user explicitly rejected it ("慢得很"); only config import is in scope.
- Do **not** change the Maven `artifactId`/`<name>` (stays `agent`, jar stays `agent-0.0.1-SNAPSHOT.jar`) — only `spring.application.name` becomes `l7-skill-backend`.
- Do **not** write any MySQL business code (no entities, repositories, services) — this plan only adds the dependency + connection config so a later feature can use it immediately.
- Do **not** introduce R2DBC — this app is `spring-boot-starter-web` (Servlet/Tomcat), not WebFlux; use plain JDBC/HikariCP.
- `local` profile must keep working with **zero** network access and **zero** local MySQL running — no regressions to the offline dev loop.
- Dependency versions: `spring-cloud-dependencies` = `2025.0.0`, `spring-cloud-alibaba-dependencies` = `2025.0.0.0`, `mysql-connector-j` groupId is `com.mysql` (not `mysql`), scope `runtime`.
- Nacos `dev` config already exists in the console (namespace `jowo-im-dev`, group `DEFAULT_GROUP`, data-id `l7-skill-backend-dev.yaml`) — do not attempt to create it via code; it's already done.

---

## File Structure

- **Modify `pom.xml`** — add two BOM imports (`spring-cloud-dependencies`, `spring-cloud-alibaba-dependencies`) and four dependencies: `spring-cloud-starter-alibaba-nacos-config`, `spring-boot-starter-jdbc`, `mysql-connector-j` (runtime), `h2` (test).
- **Modify `src/main/resources/application.yml`** — rename `spring.application.name` to `l7-skill-backend`; append a `dev`-profile-scoped document with the Nacos config-import wiring.
- **Modify `src/main/resources/application-local.yml`** — add a `spring.datasource.*` block (MySQL, localhost defaults, Hikari pool) with `initialization-fail-timeout: -1` so local startup never blocks on an unreachable MySQL.
- **Delete `src/main/resources/application-dev.yml`** — every key it held now lives in the Nacos-managed `l7-skill-backend-dev.yaml`.

---

### Task 1: Add Nacos config + MySQL/JDBC Maven dependencies

**Files:**
- Modify: `pom.xml`

**Interfaces:**
- Produces: `spring-cloud-starter-alibaba-nacos-config` on the classpath (enables `nacos:` prefix in `spring.config.import`, consumed by Task 2). `spring-boot-starter-jdbc` + `com.mysql:mysql-connector-j` on the classpath (enables `DataSourceAutoConfiguration`, consumed by Task 3's local datasource config). `com.h2database:h2` on the test classpath (keeps existing `@SpringBootTest` classes working now that `DataSourceAutoConfiguration` is active with no datasource URL set during `mvn test`, which runs with no active profile).

- [ ] **Step 1: Confirm current test baseline passes before touching anything**

Run: `mvn -q test`
Expected: `BUILD SUCCESS` (all existing tests green, including the 3 `@SpringBootTest` classes: `UploadControllerTest`, `DeepSeekPropertiesTest`, `EnterpriseApiPropertiesTest`).

- [ ] **Step 2: Add BOM version properties**

In `pom.xml`, change:

```xml
	<properties>
		<java.version>21</java.version>
	</properties>
```

to:

```xml
	<properties>
		<java.version>21</java.version>
		<spring-cloud-dependencies.version>2025.0.0</spring-cloud-dependencies.version>
		<spring-cloud-alibaba-dependencies.version>2025.0.0.0</spring-cloud-alibaba-dependencies.version>
	</properties>
```

- [ ] **Step 3: Add `<dependencyManagement>` importing the two BOMs**

Insert this block right after `</properties>` and before `<dependencies>`:

```xml
	<dependencyManagement>
		<dependencies>
			<dependency>
				<groupId>org.springframework.cloud</groupId>
				<artifactId>spring-cloud-dependencies</artifactId>
				<version>${spring-cloud-dependencies.version}</version>
				<type>pom</type>
				<scope>import</scope>
			</dependency>
			<dependency>
				<groupId>com.alibaba.cloud</groupId>
				<artifactId>spring-cloud-alibaba-dependencies</artifactId>
				<version>${spring-cloud-alibaba-dependencies.version}</version>
				<type>pom</type>
				<scope>import</scope>
			</dependency>
		</dependencies>
	</dependencyManagement>
```

- [ ] **Step 4: Add the four new dependencies**

In `pom.xml`'s `<dependencies>` block, after the existing `tika-core` dependency (and before `</dependencies>`), add:

```xml
		<dependency>
			<groupId>com.alibaba.cloud</groupId>
			<artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
		</dependency>

		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-jdbc</artifactId>
		</dependency>
		<dependency>
			<groupId>com.mysql</groupId>
			<artifactId>mysql-connector-j</artifactId>
			<scope>runtime</scope>
		</dependency>
		<dependency>
			<groupId>com.h2database</groupId>
			<artifactId>h2</artifactId>
			<scope>test</scope>
		</dependency>
```

- [ ] **Step 5: Verify dependency resolution**

Run: `mvn -q dependency:tree -Dincludes=com.alibaba.cloud,org.springframework.cloud,com.mysql,com.h2database,org.springframework.boot:spring-boot-starter-jdbc`
Expected: prints a tree containing `com.alibaba.cloud:spring-cloud-starter-alibaba-nacos-config:2025.0.0.0`, `org.springframework.cloud:spring-cloud-*:2025.0.0` entries, `com.mysql:mysql-connector-j`, `com.h2database:h2`, `org.springframework.boot:spring-boot-starter-jdbc` — no errors, no `BUILD FAILURE`.

- [ ] **Step 6: Verify existing tests still pass with the new dependencies present**

Run: `mvn -q test`
Expected: `BUILD SUCCESS`. This specifically confirms the `h2` test dependency prevents `DataSourceAutoConfiguration` (now active because `spring-boot-starter-jdbc` is on the classpath) from failing the 3 `@SpringBootTest` classes with "Failed to configure a DataSource: 'url' attribute is not specified and no embedded datasource could be configured" — with no active profile during tests, there's no `spring.datasource.url` set anywhere yet, so Spring Boot falls back to an embedded H2 database instead of erroring.

- [ ] **Step 7: Commit**

```bash
git add pom.xml
git commit -m "$(cat <<'EOF'
build: add Nacos config-client and MySQL/JDBC dependencies

Prep for Nacos-backed dev config and future MySQL feature work.
No behavior change yet — nothing reads these until later tasks wire
the config in.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Rename `spring.application.name` and wire dev-profile Nacos config import

**Files:**
- Modify: `src/main/resources/application.yml`

**Interfaces:**
- Consumes: `spring-cloud-starter-alibaba-nacos-config` from Task 1 (provides the `nacos:` scheme for `spring.config.import`).
- Produces: when started with `spring.profiles.active=dev`, the app registers as `l7-skill-backend` with Nacos and imports `l7-skill-backend-dev.yaml` (namespace `jowo-im-dev`, group `DEFAULT_GROUP`) from `192.168.3.232:8848`. When no profile (or `local`) is active, this block is inert — behavior unchanged.

- [ ] **Step 1: Rename the application name**

In `src/main/resources/application.yml`, change:

```yaml
spring:
  application:
    name: agent
```

to:

```yaml
spring:
  application:
    name: l7-skill-backend
```

- [ ] **Step 2: Append the dev-profile Nacos document**

At the very end of `src/main/resources/application.yml` (after the existing `app.upload.*` block), append:

```yaml

---
spring:
  config:
    activate:
      on-profile: dev
    import:
      - nacos:${spring.application.name}-${spring.profiles.active}.yaml?group=${spring.cloud.nacos.config.group}&refreshEnabled=true
  cloud:
    nacos:
      config:
        server-addr: 192.168.3.232:8848
        namespace: jowo-im-${spring.profiles.active}
        group: DEFAULT_GROUP
```

- [ ] **Step 3: Verify the default profile still boots and existing tests pass**

Run: `mvn -q test`
Expected: `BUILD SUCCESS`. Tests run with no active profile, so this new document (scoped to `on-profile: dev`) never activates and requires no network access — this only proves the YAML is syntactically valid and the base app context still loads.

- [ ] **Step 4: Manually verify the `dev` profile actually reaches Nacos (requires internal network access to `192.168.3.232`)**

Run: `mvn spring-boot:run -Dspring-boot.run.profiles=dev`
Expected: log shows the app successfully pulling config from Nacos (look for `Nacos` client connection log lines and no `NacosException`/connection-refused errors), and the app reaches `Started AgentApplication`. Stop it with Ctrl-C once confirmed.

If this machine can't reach `192.168.3.232:8848`, skip this step here and run it once on a machine that can before merging — this is the one step in this plan that a sandboxed/offline worker cannot verify itself.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/application.yml
git commit -m "$(cat <<'EOF'
feat: rename app to l7-skill-backend, import dev config from Nacos

dev profile now pulls l7-skill-backend-dev.yaml from the already-
configured Nacos entry (namespace jowo-im-dev, group DEFAULT_GROUP)
instead of a local file. local profile is unaffected.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Add local MySQL datasource config with a startup safety net

**Files:**
- Modify: `src/main/resources/application-local.yml`

**Interfaces:**
- Consumes: `spring-boot-starter-jdbc` + `mysql-connector-j` from Task 1 (makes `spring.datasource.*` properties actually create a real `DataSource` bean instead of being inert).
- Produces: nothing consumed by later tasks — this is prep for a future, separate MySQL feature. No code in this repo reads this `DataSource` yet.

- [ ] **Step 1: Append the datasource block**

At the end of `src/main/resources/application-local.yml`, append:

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

- [ ] **Step 2: Verify local profile starts with no local MySQL running**

Confirm nothing is listening on `127.0.0.1:3306` (skip this check if you know there's genuinely no local MySQL): `lsof -i :3306 || echo "nothing on 3306, good"`

Run: `mvn spring-boot:run -Dspring-boot.run.profiles=local`
Expected: app reaches `Started AgentApplication` in the logs within the usual startup time, with **no** `PoolInitializationException` / `Failed to obtain JDBC Connection` error, despite there being no reachable MySQL. This is what `initialization-fail-timeout: -1` buys us — the pool defers connection attempts instead of failing fast at startup.

Then in another terminal: `curl -sf http://localhost:8088/api/services` — expect a JSON response (proves the app is actually serving requests, not just technically "started"). Stop the app with Ctrl-C.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/application-local.yml
git commit -m "$(cat <<'EOF'
feat: pre-wire local MySQL datasource config for future use

Not used by any code yet. initialization-fail-timeout: -1 keeps local
startup working for developers without a local MySQL running.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Remove migrated secrets from `application-dev.yml`

**Files:**
- Delete: `src/main/resources/application-dev.yml`

**Interfaces:**
- Consumes: nothing (this is a cleanup step, contingent on Task 2's Nacos import actually working — see the ordering note below).
- Produces: nothing consumed by later tasks.

- [ ] **Step 1: Confirm every key in this file already exists in the Nacos `l7-skill-backend-dev.yaml` entry**

Read the current contents:

```bash
cat src/main/resources/application-dev.yml
```

It currently holds exactly these three top-level keys: `spring.ai.openai.api-key`, `enterprise.api.token`, `portainer.api-key`. Cross-check in the Nacos console (namespace `jowo-im-dev`, group `DEFAULT_GROUP`, data-id `l7-skill-backend-dev.yaml`) that all three are present. Per the design doc, they already are — this step is a final confirmation before deleting, not a new migration.

**Do not proceed to Step 2 until Task 2's Step 4 (real Nacos connectivity check) has actually succeeded on some machine.** Deleting this file before confirming Nacos import works would leave `dev` profile with neither the local secrets nor a working Nacos fetch.

- [ ] **Step 2: Delete the file**

```bash
git rm src/main/resources/application-dev.yml
```

- [ ] **Step 3: Verify nothing else references it**

Run: `grep -rn "application-dev" --include=*.java --include=*.xml --include=*.sh . 2>/dev/null`
Expected: no output (nothing in code, build config, or scripts hardcodes this filename).

- [ ] **Step 4: Commit**

```bash
git commit -m "$(cat <<'EOF'
chore: remove application-dev.yml, secrets now live in Nacos

All three keys it held (openai key, enterprise token, portainer key)
are already present in the Nacos-managed l7-skill-backend-dev.yaml
(namespace jowo-im-dev, group DEFAULT_GROUP).

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: Final manual checklist (outside this repo)

This task has no files to change — it's operational cleanup in the Nacos console itself, called out separately so it isn't lost.

- [ ] **Step 1: Fix the logging key in the Nacos `l7-skill-backend-dev.yaml` entry**

In the Nacos console, under namespace `jowo-im-dev`, group `DEFAULT_GROUP`, data-id `l7-skill-backend-dev.yaml`, find:

```yaml
logging:
  level:
    "com.jowo.l7-skill-backend": DEBUG
```

and change the key to the app's actual Java package:

```yaml
logging:
  level:
    "com.jowo.agent": DEBUG
```

(`spring.application.name` was renamed to `l7-skill-backend` in Task 2, but the Java package itself is still `com.jowo.agent` — see Global Constraints. The old key matches no package and silently does nothing.)

- [ ] **Step 2: Confirm the fix took effect**

With the app running under `-Dspring.profiles.active=dev` (from Task 2 Step 4), trigger any request that logs at DEBUG level from a class under `com.jowo.agent` (e.g. `AgentService`), and confirm DEBUG-level lines now appear in the console/log file.

- [ ] **Step 3: Decide on the production profile name**

Per the design doc, the production `spring.profiles.active` value and its corresponding Nacos namespace/data-id (`jowo-im-<prod-profile>` / `l7-skill-backend-<prod-profile>.yaml`) are still undecided. No code change is needed once decided — just create the matching Nacos entry. Tracked here so it isn't forgotten, not blocking this plan's completion.
