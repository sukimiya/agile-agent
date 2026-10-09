# Phase 1: Open-source Core and Capability SPI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert the existing single-module service into a neutral multi-module open-source core, introduce the capability SPI, and remove all bundled enterprise-domain behavior while preserving generic chat and knowledge behavior.

**Architecture:** A root Maven aggregator builds `agent-spi`, `agent-server`, and an initially empty `mock-oauth2` module. The server discovers capability providers as Spring beans; the existing knowledge tool becomes the first built-in provider. A temporary global OpenAI-compatible client keeps chat functional until Phase 3 introduces tenant configuration.

**Tech Stack:** Java 21 source level, Maven, Spring Boot 3.5, WebFlux, Reactor, JUnit 5, Mockito, BlockHound

**Spec:** `docs/spec/2026-10-09-open-source-multimodule-migration.md`

## Global Constraints

- Use Java package root `io.github.sukimiya.agileagent` and Maven groupId `io.github.sukimiya`.
- Keep public auth, chat, session, file, and knowledge paths stable; remove `/api/services`.
- Do not add MES/CRM/WMS/QMS/SRM/MDM/EAM/EMS behavior, resources, or examples.
- `agent-spi` may depend on Reactor and Jackson annotations/types needed by its contract, but never on Spring Boot or `agent-server`.
- Never log or expose OAuth2 tokens.
- Every new behavior follows red-green-refactor; mechanical moves use the existing suite as their characterization gate.
- Do not skip or delete the BlockHound test to make JDK 25 green.

## Review Focus

- Duplicate capability codes must fail startup deterministically, and duplicate dynamic tool definitions must fail before an LLM call; covered in Task 4.
- A provider must not execute a tool name absent from its own definition; covered in Task 5.
- Empty tool lists must omit both `tools` and `tool_choice` from the LLM request; covered in Task 2.
- Package and resource scans must avoid false positives from the migration spec, the exact legacy Flyway history table compatibility value, and historical Git metadata; covered in Task 6.
- Moving Flyway resources must preserve the existing history table name and V1/V2 locations; covered in Task 1.

---

### Task 1: Create the Maven reactor without changing runtime behavior

**Files:**
- Modify: `pom.xml`
- Create: `agent-spi/pom.xml`
- Create: `agent-server/pom.xml`
- Create: `mock-oauth2/pom.xml`
- Move: `src/main/**` to `agent-server/src/main/**`
- Move: `src/test/**` to `agent-server/src/test/**`
- Test: `agent-server/src/test/java/com/jowo/agent/repository/SessionRepositoryTest.java`
- Test: `agent-server/src/test/java/com/jowo/agent/knowledge/KnowledgeControllerIntegrationTest.java`

**Interfaces:**
- Consumes: the current single-module Maven build and V1/V2 Flyway migrations.
- Produces: root reactor modules `agile-agent-spi`, `agile-agent-server`, and `agile-agent-mock-oauth2`; all existing application code remains in `agent-server` at this task boundary.

- [ ] **Step 1: Record the characterization result before moving files**

Run: `mvn test`

Expected: 186 tests execute; the only error is the documented JDK 25 `ReactiveChainBlockHoundTest.installBlockHound` instrumentation error.

- [ ] **Step 2: Create the root aggregator and three child POMs**

The root POM owns Java 21, Spring Boot 3.5.0, Spring Cloud versions, dependency management, and plugin management. Configure Surefire with `failIfNoSpecifiedTests=false` so focused multi-module test commands are stable. `agent-server` receives the current runtime/test dependencies; `agent-spi` starts with Reactor only; `mock-oauth2` starts as a compilable Spring Boot WebFlux application module with no server class yet.

- [ ] **Step 3: Move sources and resources into `agent-server` without changing package declarations**

Use filesystem moves so Git records renames. Keep V1/V2 migration bytes unchanged and retain `l7_skill_flyway_schema_history` temporarily until the explicit naming task.

- [ ] **Step 4: Add a BlockHound regression that performs a known blocking call on a Reactor non-blocking scheduler**

The assertion must observe `BlockingOperationError`; it must not merely assert that `BlockHound.install()` returns.

- [ ] **Step 5: Run the isolated test and confirm the compatibility failure**

Run: `mvn -pl agent-server -Dtest=ReactiveChainBlockHoundTest test`

Expected: FAIL during instrumentation on JDK 25 before the blocking assertion runs.

- [ ] **Step 6: Configure the supported BlockHound/Byte Buddy combination**

Prefer a compatible dependency version. If no compatible release is present in the configured repositories, add the documented experimental class-reader system property to Surefire while retaining `-XX:+AllowRedefinitionToAddDeleteMethods`. Do not disable the test.

- [ ] **Step 7: Run module and reactor tests**

Run: `mvn -pl agent-server -Dtest=ReactiveChainBlockHoundTest test`

Expected: PASS and the blocking call is detected.

Run: `mvn clean test`

Expected: all modules pass with zero failures and zero errors; no missing resources or Flyway migration failures.

- [ ] **Step 8: Commit the reactor move and compatibility repair**

```bash
git add pom.xml agent-spi agent-server mock-oauth2
git commit -m "build: split project into Maven modules"
```

### Task 2: Replace the DeepSeek-specific default client with a neutral client

**Files:**
- Create: `agent-server/src/test/java/io/github/sukimiya/agileagent/service/OpenAiCompatibleLlmClientTest.java`
- Create: `agent-server/src/test/java/io/github/sukimiya/agileagent/service/OpenAiCompatibleLlmClientStreamTest.java`
- Create: `agent-server/src/main/java/io/github/sukimiya/agileagent/config/DefaultLlmProperties.java`
- Create: `agent-server/src/main/java/io/github/sukimiya/agileagent/service/OpenAiCompatibleLlmClient.java`
- Delete: `agent-server/src/main/java/com/jowo/agent/config/DeepSeekProperties.java`
- Delete: `agent-server/src/main/java/com/jowo/agent/service/DeepSeekLlmClient.java`
- Modify: `agent-server/src/main/resources/application.yml`

**Interfaces:**
- Consumes: existing `LlmClient` request/stream contract.
- Produces: `OpenAiCompatibleLlmClient(String baseUrl, String apiKey, String model, WebClient.Builder)` and temporary `DefaultLlmProperties`; Spring exposes one default `LlmClient` bean until Phase 3.

- [ ] **Step 1: Write failing request-shape tests**

Add tests proving that an empty tool list omits `tools` and `tool_choice`, a non-empty list includes both, and the configured model is sent.

- [ ] **Step 2: Run the request tests and verify RED**

Run: `mvn -pl agent-server -Dtest=OpenAiCompatibleLlmClientTest test`

Expected: FAIL because the neutral client and properties do not exist.

- [ ] **Step 3: Implement the neutral client and temporary `llm.default.*` configuration**

Keep the existing streaming and tool-call aggregation semantics. Use neutral `LLM error`/`LLM unreachable` messages.

- [ ] **Step 4: Run request, stream, and LLM consumer tests**

Run: `mvn -pl agent-server -Dtest='OpenAiCompatibleLlmClient*Test,OrchestratorTest,DomainAgentTest' test`

Expected: PASS.

- [ ] **Step 5: Commit the neutral LLM client**

```bash
git add agent-server
git commit -m "refactor: generalize the default LLM client"
```

### Task 3: Rename Java packages and application artifacts

**Files:**
- Move: all `agent-server/src/main/java/com/jowo/agent/**` to `agent-server/src/main/java/io/github/sukimiya/agileagent/**`
- Move: all `agent-server/src/test/java/com/jowo/agent/**` to `agent-server/src/test/java/io/github/sukimiya/agileagent/**`
- Move: shaded test fixture packages to the equivalent neutral namespace where they are project-owned
- Modify: `pom.xml`, `agent-spi/pom.xml`, `agent-server/pom.xml`, `mock-oauth2/pom.xml`
- Modify: `agent-server/src/main/resources/application.yml`
- Modify: `agent-server/src/main/resources/logback-spring.xml`
- Modify: `docker/**`, `Jenkinsfile`, `start.sh`

**Interfaces:**
- Consumes: the working multi-module server and neutral default LLM from Tasks 1-2.
- Produces: Java root package `io.github.sukimiya.agileagent`, Maven groupId `io.github.sukimiya`, neutral artifact/application/image/service names, and unchanged HTTP/database behavior.

- [ ] **Step 1: Record a passing full-suite checkpoint**

Run: `mvn clean test`

Expected: all modules pass before the mechanical rename.

- [ ] **Step 2: Move package directories and update declarations/imports/configuration**

Update `@SpringBootApplication`, test package declarations, logger abbreviations, Maven coordinates, Spring application name, JAR/image/service/container names, and scripts. Retain the exact legacy Flyway history table name `l7_skill_flyway_schema_history` for deployed-database compatibility and no other old branding.

- [ ] **Step 3: Run compile and full tests after the rename**

Run: `mvn clean test`

Expected: all modules pass with no classpath, component-scan, test-discovery, or migration regressions.

- [ ] **Step 4: Commit the mechanical rename**

```bash
git add -A
git commit -m "refactor: rename packages and artifacts for agile-agent"
```

### Task 4: Define the capability SPI and registry

**Files:**
- Create: `agent-spi/src/main/java/io/github/sukimiya/agileagent/spi/identity/IdentityContext.java`
- Create: `agent-spi/src/main/java/io/github/sukimiya/agileagent/spi/capability/AgentCapabilityProvider.java`
- Create: `agent-spi/src/main/java/io/github/sukimiya/agileagent/spi/capability/CapabilityDefinition.java`
- Create: `agent-spi/src/main/java/io/github/sukimiya/agileagent/spi/capability/ToolDefinition.java`
- Create: `agent-spi/src/main/java/io/github/sukimiya/agileagent/spi/capability/ToolInvocation.java`
- Create: `agent-spi/src/main/java/io/github/sukimiya/agileagent/spi/capability/ToolResult.java`
- Create: `agent-spi/src/main/java/io/github/sukimiya/agileagent/spi/capability/AgentExecutionContext.java`
- Create: `agent-spi/src/test/java/io/github/sukimiya/agileagent/spi/identity/IdentityContextTest.java`
- Create: `agent-server/src/main/java/io/github/sukimiya/agileagent/capability/CapabilityRegistry.java`
- Create: `agent-server/src/test/java/io/github/sukimiya/agileagent/capability/CapabilityRegistryTest.java`

**Interfaces:**
- Consumes: Reactor `Mono` and Jackson `JsonNode` as SPI-level types.
- Produces: the exact SPI records and `AgentCapabilityProvider` methods in the spec; `CapabilityRegistry(Collection<AgentCapabilityProvider>)`, `Optional<AgentCapabilityProvider> byCode(String)`, `List<AgentCapabilityProvider> all()`, and definition validation before use.

- [ ] **Step 1: Write failing SPI immutability and registry validation tests**

Assert defensive copies for all `IdentityContext` sets, rejection of blank/duplicate capability codes at registry creation, and rejection of duplicate tool names when a dynamic provider definition resolves.

- [ ] **Step 2: Run SPI and registry tests and verify RED**

Run: `mvn -pl agent-spi,agent-server -am -Dtest='IdentityContextTest,CapabilityRegistryTest' test`

Expected: FAIL because the SPI and registry do not exist.

- [ ] **Step 3: Implement the minimal SPI records and registry**

Use constructor validation in records. Registry ordering must be deterministic by capability code.

- [ ] **Step 4: Run the focused tests**

Run: `mvn -pl agent-spi,agent-server -am -Dtest='IdentityContextTest,CapabilityRegistryTest' test`

Expected: PASS.

- [ ] **Step 5: Commit the SPI**

```bash
git add agent-spi agent-server/src/main/java/io/github/sukimiya/agileagent/capability agent-server/src/test/java/io/github/sukimiya/agileagent/capability
git commit -m "feat: add the agent capability SPI"
```

### Task 5: Convert the knowledge role into a capability provider

**Files:**
- Create: `agent-server/src/main/java/io/github/sukimiya/agileagent/knowledge/KnowledgeCapabilityProvider.java`
- Create: `agent-server/src/test/java/io/github/sukimiya/agileagent/knowledge/KnowledgeCapabilityProviderTest.java`
- Modify: `agent-server/src/main/java/io/github/sukimiya/agileagent/agent/DomainAgent.java`
- Modify: `agent-server/src/main/java/io/github/sukimiya/agileagent/agent/Orchestrator.java`
- Modify: `agent-server/src/main/java/io/github/sukimiya/agileagent/agent/AgentRoleRegistry.java`
- Modify: corresponding agent tests

**Interfaces:**
- Consumes: `AgentCapabilityProvider`, `CapabilityRegistry` from Task 4, current `KnowledgeCatalog`, and current authentication token.
- Produces: built-in capability code `KNOWLEDGE`; Domain Agent delegates every tool call to the selected provider and rejects undefined tool names without invoking `execute`.

- [ ] **Step 1: Write failing provider and delegation tests**

Cover dynamic knowledge prompt/index creation, `readKnowledgeDoc`, unknown-tool rejection, and propagation of conversation/identity/token context without logging it.

- [ ] **Step 2: Run focused tests and verify RED**

Run: `mvn -pl agent-server -Dtest='KnowledgeCapabilityProviderTest,DomainAgentTest,OrchestratorTest' test`

Expected: FAIL because Domain Agent still uses hardcoded roles and switch-based tools.

- [ ] **Step 3: Implement `KnowledgeCapabilityProvider` and registry-based execution**

Remove the hardcoded tool switch from Domain Agent. Keep LLM round limits, event ordering, chat memory, and structured logging behavior unchanged.

- [ ] **Step 4: Run agent and knowledge suites**

Run: `mvn -pl agent-server -Dtest='*AgentTest,*RegistryTest,Knowledge*Test' test`

Expected: PASS.

- [ ] **Step 5: Commit capability execution**

```bash
git add agent-server
git commit -m "refactor: execute agent tools through capability providers"
```

### Task 6: Remove enterprise-domain code and enforce the boundary

**Files:**
- Delete: `agent-server/src/main/java/**/service/ApiCatalog.java`
- Delete: `agent-server/src/main/java/**/service/EnterpriseApiClient.java`
- Delete: `agent-server/src/main/java/**/service/HttpEnterpriseApiClient.java`
- Delete: `agent-server/src/main/java/**/config/EnterpriseApiProperties.java`
- Delete: matching tests
- Delete: `api-docs/**`, `agent-server/src/main/resources/api-docs/**`, business-domain docs, and API conversion scripts
- Modify: `agent-server/src/main/resources/application.yml`
- Create: `agent-server/src/test/java/io/github/sukimiya/agileagent/OpenSourceBoundaryTest.java`

**Interfaces:**
- Consumes: capability-based execution from Task 5 and the neutral package/artifact names from Task 3.
- Produces: no `com.jowo`, enterprise service class, `/api/services`, or bundled business API resource in product code/configuration; `l7-skill` is absent except for the exact legacy Flyway history table compatibility value; application name and image are `agile-agent-server`.

- [ ] **Step 1: Write the failing open-source boundary test**

Scan tracked product roots only: module POMs, `agent-*/src`, Docker/Jenkins/start scripts, and current README. Exclude `docs/spec`, `docs/superpowers`, `.git`, `target`, historical review artifacts, and the exact configured value `l7_skill_flyway_schema_history`. Assert all other forbidden package/branding/domain identifiers are absent.

- [ ] **Step 2: Run the boundary test and verify RED**

Run: `mvn -pl agent-server -Dtest=OpenSourceBoundaryTest test`

Expected: FAIL and list current package, service, and resource violations.

- [ ] **Step 3: Delete business components and remove their wiring**

Delete `/api/services` from `ChatController`, remove business configuration properties and beans, and remove business docs/resources rather than renaming them.

- [ ] **Step 4: Run boundary and full reactor tests**

Run: `mvn -pl agent-server -Dtest=OpenSourceBoundaryTest test`

Expected: PASS.

Run: `mvn clean test`

Expected: all modules pass with zero failures and zero errors.

- [ ] **Step 5: Commit the open-source boundary**

```bash
git add -A
git commit -m "refactor: establish the open-source core boundary"
```

### Task 7: Document the capability adapter contract

**Files:**
- Create: `docs/capability-adapters.md`
- Modify: `README.md` or create it if absent
- Test: `agent-server/src/test/java/io/github/sukimiya/agileagent/capability/ExternalCapabilityIntegrationTest.java`

**Interfaces:**
- Consumes: published `agent-spi` API and Spring bean discovery.
- Produces: a test-only provider that depends only on `agent-spi` and completes one routed tool call; user documentation for packaging a Spring Boot auto-configuration adapter.

- [ ] **Step 1: Write a failing external-provider integration test**

Create a test fixture provider without importing any `agent-server` implementation package. Assert discovery, routing metadata, definition, and one successful tool result.

- [ ] **Step 2: Run the integration test and verify RED**

Run: `mvn -pl agent-server -Dtest=ExternalCapabilityIntegrationTest test`

Expected: FAIL if server internals are still required or discovery is incomplete.

- [ ] **Step 3: Make only the boundary adjustments required by the test and write adapter documentation**

Document Maven coordinates, SPI contracts, auto-configuration registration, token-handling rules, and duplicate-code behavior. Do not add a business sample.

- [ ] **Step 4: Verify the phase**

Run: `mvn clean test`

Expected: all tests pass.

Run: `mvn -pl agent-server,mock-oauth2 -am package -DskipTests`

Expected: both application modules package successfully.

- [ ] **Step 5: Commit the adapter contract**

```bash
git add README.md docs/capability-adapters.md agent-server
git commit -m "docs: define the capability adapter contract"
```
