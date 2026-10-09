# Phase 3: Tenant LLM and Chat Hints Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the temporary global LLM with encrypted tenant-shared configuration, provider presets, usage reporting, optional balance lookup, and platform-managed chat hints.

**Architecture:** Global provider presets define trusted OpenAI-compatible endpoints and models. Each tenant owns one encrypted API Key and selected preset, resolved exclusively from `IdentityContext.tenantId`. A short-lived local cache limits database work without causing permanent multi-instance staleness.

**Tech Stack:** Spring WebFlux, R2DBC, Flyway, AES-256-GCM, WebClient SSE, Reactor, H2/MySQL, JUnit 5, MockWebServer

**Spec:** `docs/spec/2026-10-09-open-source-multimodule-migration.md`

## Global Constraints

- Configuration ownership key is `tenant_id`, never user ID and never a request parameter.
- Platform admins manage presets; tenant admins manage current-tenant configuration; regular users have read/use access only.
- API Keys are AES-256-GCM encrypted and never returned or logged.
- No fixed default encryption key is allowed in source or Compose.
- Provider base URLs require HTTPS outside mock/test profiles.
- Missing configuration returns HTTP 428 with code `LLM_CONFIG_REQUIRED`.
- Cache entries have a finite TTL and local writes evict immediately.

## Review Focus

- A tenant administrator must not select or mutate another tenant by supplying JSON/query/path data; covered in Task 5.
- Deleting or changing a referenced preset must not keep a permanently cached client alive; covered in Tasks 3 and 4.
- A malformed encrypted key must fail closed without exposing ciphertext or provider credentials; covered in Task 2.
- Provider validation errors must not echo response bodies that may contain secrets; covered in Task 5.
- Usage chunks with empty `choices` must still be emitted exactly once and accumulated across tool rounds; covered in Task 6.

---

### Task 1: Add LLM and hint Flyway migrations and repositories

**Files:**
- Create: `agent-server/src/main/resources/db/migration/V3__create_chat_hints_table.sql`
- Create: `agent-server/src/main/resources/db/migration/V4__create_llm_provider_presets.sql`
- Create: `agent-server/src/main/resources/db/migration/V5__create_tenant_llm_config.sql`
- Create: `agent-server/src/main/java/io/github/sukimiya/agileagent/llmconfig/LlmProviderPresetRepository.java`
- Create: `agent-server/src/main/java/io/github/sukimiya/agileagent/llmconfig/TenantLlmConfigRepository.java`
- Create: `agent-server/src/main/java/io/github/sukimiya/agileagent/hint/HintRepository.java`
- Create: matching repository tests

**Interfaces:**
- Consumes: R2DBC `DatabaseClient`, V1/V2 Flyway history.
- Produces: preset CRUD, tenant config find/upsert/delete by tenant ID, and hint ordered CRUD; `tenant_llm_config.tenant_id` is unique.

- [ ] **Step 1: Write failing repository and migration tests**

Cover empty lookup, insert/update/delete, unique tenant enforcement, nullable balance path, hint ordering, and migration from an explicit V2 schema fixture.

- [ ] **Step 2: Run repository tests and verify RED**

Run: `mvn -pl agent-server -Dtest='LlmProviderPresetRepositoryTest,TenantLlmConfigRepositoryTest,HintRepositoryTest,LlmMigrationTest' test`

Expected: FAIL because V3-V5 and repositories do not exist.

- [ ] **Step 3: Implement migrations and repositories**

Do not add a foreign key that prevents the documented behavior for deleted presets; resolver treats a missing preset as unconfigured.

- [ ] **Step 4: Run repository tests**

Run: `mvn -pl agent-server -Dtest='LlmProviderPresetRepositoryTest,TenantLlmConfigRepositoryTest,HintRepositoryTest,LlmMigrationTest' test`

Expected: PASS.

- [ ] **Step 5: Commit persistence**

```bash
git add agent-server
git commit -m "feat: add tenant LLM and hint persistence"
```

### Task 2: Encrypt tenant API Keys

**Files:**
- Create: `agent-server/src/main/java/io/github/sukimiya/agileagent/config/LlmSecretProperties.java`
- Create: `agent-server/src/main/java/io/github/sukimiya/agileagent/llmconfig/ApiKeyCrypto.java`
- Create: `agent-server/src/test/java/io/github/sukimiya/agileagent/llmconfig/ApiKeyCryptoTest.java`
- Modify: `agent-server/src/main/resources/application.yml`

**Interfaces:**
- Consumes: base64 32-byte `LLM_CONFIG_SECRET`.
- Produces: `encrypt(String): String` and `decrypt(String): String` using random 12-byte IV plus AES/GCM/NoPadding ciphertext/tag.

- [ ] **Step 1: Write failing crypto tests**

Cover round-trip, different ciphertext for the same plaintext, missing/invalid/wrong-length master key, truncated payload, wrong key, and error messages without plaintext/ciphertext.

- [ ] **Step 2: Run crypto tests and verify RED**

Run: `mvn -pl agent-server -Dtest=ApiKeyCryptoTest test`

Expected: FAIL because crypto does not exist.

- [ ] **Step 3: Implement fail-fast AES-256-GCM crypto**

Constructor validates the secret. Decrypt rejects payloads shorter than IV plus tag before slicing.

- [ ] **Step 4: Run crypto tests**

Run: `mvn -pl agent-server -Dtest=ApiKeyCryptoTest test`

Expected: PASS.

- [ ] **Step 5: Commit encryption**

```bash
git add agent-server
git commit -m "feat: encrypt tenant LLM API keys"
```

### Task 3: Resolve tenant clients with bounded caching

**Files:**
- Create: `agent-server/src/main/java/io/github/sukimiya/agileagent/service/TenantLlmClientResolver.java`
- Create: `agent-server/src/main/java/io/github/sukimiya/agileagent/service/TenantLlmConfigRequiredException.java`
- Create: `agent-server/src/main/java/io/github/sukimiya/agileagent/service/TtlCache.java`
- Create: matching resolver/cache tests
- Modify: `agent-server/src/main/resources/application.yml`

**Interfaces:**
- Consumes: preset/config repositories, `ApiKeyCrypto`, `OpenAiCompatibleLlmClient`, and tenant ID.
- Produces: `resolve(String tenantId): Mono<LlmClient>`, `resolvePresetAndKey(String tenantId)`, `evict(String tenantId)`, and `evictAll()`; default TTL five minutes and injectable `Clock`.

- [ ] **Step 1: Write failing resolver and cache tests**

Cover same-tenant hit, cross-tenant separation, expiry reload, local eviction, preset deletion, missing/blank tenant, missing config, malformed key, and concurrent misses.

- [ ] **Step 2: Run tests and verify RED**

Run: `mvn -pl agent-server -Dtest='TenantLlmClientResolverTest,TtlCacheTest' test`

Expected: FAIL because resolver/cache do not exist.

- [ ] **Step 3: Implement the bounded cache and resolver**

Concurrent misses may create at most one cached final value; duplicate transient construction is acceptable only if no secret escapes and the final entry is consistent.

- [ ] **Step 4: Run resolver tests**

Run: `mvn -pl agent-server -Dtest='TenantLlmClientResolverTest,TtlCacheTest' test`

Expected: PASS.

- [ ] **Step 5: Commit resolver**

```bash
git add agent-server
git commit -m "feat: resolve cached tenant LLM clients"
```

### Task 4: Add platform-admin provider preset management

**Files:**
- Create: `agent-server/src/main/java/io/github/sukimiya/agileagent/llmconfig/LlmProviderPresetService.java`
- Create: `agent-server/src/main/java/io/github/sukimiya/agileagent/llmconfig/LlmProviderPresetController.java`
- Create: matching service/controller tests
- Modify: `agent-server/src/main/java/io/github/sukimiya/agileagent/web/AuthExceptionHandler.java`

**Interfaces:**
- Consumes: `AuthorizationService`, current `IdentityContext`, preset repository, resolver eviction.
- Produces: authenticated `GET /api/llm-providers`; platform-admin-only POST/PUT/DELETE; DTO excludes secrets and validates HTTPS unless HTTP is allowed by profile.

- [ ] **Step 1: Write failing permission and validation tests**

Cover list access, platform-admin writes, tenant-admin/regular denial, blank fields, malformed URL, HTTP rejection/default, test-profile HTTP allowance, not found, and evict-all on edit/delete.

- [ ] **Step 2: Run tests and verify RED**

Run: `mvn -pl agent-server -Dtest='LlmProviderPresetServiceTest,LlmProviderPresetControllerTest' test`

Expected: FAIL because service/controller do not exist.

- [ ] **Step 3: Implement preset service and controller**

Validate scheme and host before persistence. Never perform connectivity checks against arbitrary request-provided URLs.

- [ ] **Step 4: Run focused tests**

Run: `mvn -pl agent-server -Dtest='LlmProviderPresetServiceTest,LlmProviderPresetControllerTest' test`

Expected: PASS.

- [ ] **Step 5: Commit preset management**

```bash
git add agent-server
git commit -m "feat: manage OpenAI-compatible provider presets"
```

### Task 5: Add current-tenant LLM configuration

**Files:**
- Create: `agent-server/src/main/java/io/github/sukimiya/agileagent/llmconfig/TenantLlmConfigService.java`
- Create: `agent-server/src/main/java/io/github/sukimiya/agileagent/llmconfig/TenantLlmConfigController.java`
- Create: `agent-server/src/main/java/io/github/sukimiya/agileagent/llmconfig/LlmConfigValidationException.java`
- Create: matching service/controller tests

**Interfaces:**
- Consumes: current `IdentityContext`, preset/config repositories, crypto, resolver, and WebClient.
- Produces: GET/PUT/DELETE `/api/llm-config`; tenant ID is derived only from identity; GET returns `{presetId,presetName,model,apiKeyMasked}`.

- [ ] **Step 1: Write failing tenant-isolation and connectivity tests**

Cover tenant-admin save/delete, regular-user read/write denial, platform admin behavior within its own tenant, missing tenant, ignored/rejected client-supplied tenant fields, nonexistent preset, connection success/failure, masked key, local eviction, and remote error sanitization.

- [ ] **Step 2: Run tests and verify RED**

Run: `mvn -pl agent-server -Dtest='TenantLlmConfigServiceTest,TenantLlmConfigControllerTest' test`

Expected: FAIL because service/controller do not exist.

- [ ] **Step 3: Implement current-tenant configuration and a one-request connectivity probe**

Persist only after a successful minimal streaming request. Limit public validation messages to stable categories and status; do not echo response bodies or request headers.

- [ ] **Step 4: Run focused tests**

Run: `mvn -pl agent-server -Dtest='TenantLlmConfigServiceTest,TenantLlmConfigControllerTest' test`

Expected: PASS.

- [ ] **Step 5: Commit tenant configuration**

```bash
git add agent-server
git commit -m "feat: configure one shared LLM per tenant"
```

### Task 6: Route chat through tenant clients and report usage/balance

**Files:**
- Modify: `agent-server/src/main/java/io/github/sukimiya/agileagent/service/LlmClient.java`
- Modify: `agent-server/src/main/java/io/github/sukimiya/agileagent/service/OpenAiCompatibleLlmClient.java`
- Modify: `agent-server/src/main/java/io/github/sukimiya/agileagent/agent/AgentEvent.java`
- Modify: `agent-server/src/main/java/io/github/sukimiya/agileagent/agent/DomainAgent.java`
- Modify: `agent-server/src/main/java/io/github/sukimiya/agileagent/agent/Orchestrator.java`
- Modify: `agent-server/src/main/java/io/github/sukimiya/agileagent/controller/ChatController.java`
- Create: `agent-server/src/main/java/io/github/sukimiya/agileagent/llmconfig/LlmBalanceService.java`
- Create: `agent-server/src/main/java/io/github/sukimiya/agileagent/llmconfig/LlmBalanceController.java`
- Modify/Create: corresponding stream, agent, chat, exception, and balance tests

**Interfaces:**
- Consumes: `TenantLlmClientResolver` and `IdentityContext.tenantId`.
- Produces: pre-SSE 428 validation, per-tenant LLM throughout orchestrator/domain rounds, `LlmEvent.Usage`, `AgentEvent.Usage`, OpenAI-compatible usage chunk, and GET `/api/llm-config/balance`.

- [ ] **Step 1: Write failing chat, usage, and balance tests**

Cover missing config before response commitment, two tenants selecting different upstreams, usage chunk with empty choices, multi-round token totals, unsupported balance 404, provider balance failure 502, and user Key sent only as upstream Bearer auth.

- [ ] **Step 2: Run tests and verify RED**

Run: `mvn -pl agent-server -Dtest='OpenAiCompatibleLlmClient*Test,DomainAgentTest,OrchestratorTest,ChatControllerTest,LlmBalance*Test' test`

Expected: FAIL on resolver wiring and usage/balance behavior.

- [ ] **Step 3: Implement tenant routing, usage propagation, 428 handling, and optional balance**

Remove the temporary global `LlmClient` bean and `llm.default.*` configuration after every consumer uses the resolver.

- [ ] **Step 4: Run focused and full server tests**

Run: `mvn -pl agent-server -Dtest='OpenAiCompatibleLlmClient*Test,DomainAgentTest,OrchestratorTest,ChatControllerTest,LlmBalance*Test' test`

Expected: PASS.

Run: `mvn -pl agent-server test`

Expected: PASS.

- [ ] **Step 5: Commit tenant chat routing**

```bash
git add agent-server
git commit -m "feat: route chat and usage through tenant LLMs"
```

### Task 7: Add platform-managed chat hints

**Files:**
- Create: `agent-server/src/main/java/io/github/sukimiya/agileagent/hint/HintService.java`
- Create: `agent-server/src/main/java/io/github/sukimiya/agileagent/hint/HintController.java`
- Create: `agent-server/src/main/java/io/github/sukimiya/agileagent/hint/HintValidationException.java`
- Create: matching service/controller tests

**Interfaces:**
- Consumes: Hint repository, current identity, and `AuthorizationService`.
- Produces: authenticated GET and platform-admin-only POST/PUT/DELETE `/api/chat/hints`; ordered summaries `{id,content,createdBySubject,createdAt}`.

- [ ] **Step 1: Write failing CRUD and permission tests**

Cover authenticated list, blank content, platform-admin writes, tenant-admin/regular denial, update/delete missing ID, ordering, and stable error bodies.

- [ ] **Step 2: Run tests and verify RED**

Run: `mvn -pl agent-server -Dtest='HintServiceTest,HintControllerTest' test`

Expected: FAIL because service/controller do not exist.

- [ ] **Step 3: Implement hint service/controller**

Keep physical delete and creation-time order; do not add pagination, sort editing, or audit history.

- [ ] **Step 4: Verify Phase 3**

Run: `mvn -pl agent-server -Dtest='Hint*Test,*Llm*Test,ChatControllerTest' test`

Expected: PASS.

Run: `mvn clean test`

Expected: all modules pass.

- [ ] **Step 5: Commit hints**

```bash
git add agent-server
git commit -m "feat: manage global chat hints"
```
