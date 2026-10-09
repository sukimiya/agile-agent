# Phase 4: Scoped Knowledge and Open-source Release Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add GLOBAL/TENANT/ORG knowledge isolation across persistence, management, and Agent consumption, then finish deployment and migration documentation and verify the open-source release boundary.

**Architecture:** Every approved knowledge artifact carries explicit scope metadata. One `KnowledgeAccessPolicy` supplies the same authorization predicates to controllers, services, catalog indexing, and full-content tool reads so no alternate path can bypass scope filtering. Existing documents migrate to GLOBAL.

**Tech Stack:** Spring WebFlux, R2DBC, Flyway, Reactor, filesystem storage, JUnit 5, H2/MySQL

**Spec:** `docs/spec/2026-10-09-open-source-multimodule-migration.md`

## Global Constraints

- Supported scope values are exactly `GLOBAL`, `TENANT`, and `ORG`.
- Authorization uses IDs; tenant/org names are display snapshots only.
- Existing V2 documents become GLOBAL during V6 migration.
- Ordinary users without both tenant and primary organization are rejected before file persistence.
- Index visibility and full document reads must use the same access predicate.
- Controllers never accept a caller-supplied tenant ID as authority.

## Review Focus

- Guessing a document ID must not bypass list/index scope checks during download or tool execution; covered in Tasks 3 and 4.
- A tenant administrator with a matching org ID from a different tenant must still be denied; covered in Task 2.
- A file-storage failure after metadata creation or metadata failure after temp save must not create an approved orphan; covered in Task 2.
- A V2-to-V6 migration with existing rows must preserve every row as GLOBAL and remain idempotent under Flyway; covered in Task 1.
- Identity refresh changing readable/manageable organizations must affect subsequent catalog and management access without restart; covered in Task 4.

---

### Task 1: Add V6 scope migration and repository model

**Files:**
- Create: `agent-server/src/main/resources/db/migration/V6__add_knowledge_scope.sql`
- Modify: `agent-server/src/main/java/io/github/sukimiya/agileagent/knowledge/KnowledgeDocumentRepository.java`
- Modify: repository tests
- Create: `agent-server/src/test/java/io/github/sukimiya/agileagent/knowledge/KnowledgeScopeMigrationTest.java`

**Interfaces:**
- Consumes: V2 `knowledge_documents` table and existing repository state machine.
- Produces: `ScopeType { GLOBAL, TENANT, ORG }`; row fields `tenantId`, `tenantName`, `orgId`, `orgName`; queries retain status filtering and return complete scope metadata.

- [ ] **Step 1: Write failing migration and repository tests**

Create a V2 fixture with pending/approved/rejected rows, migrate to V6, and assert every existing row is GLOBAL with null tenant/org IDs. Cover round-trip for each new scope and the `(tenant_id, org_id, status)` index path.

- [ ] **Step 2: Run tests and verify RED**

Run: `mvn -pl agent-server -Dtest='KnowledgeDocumentRepositoryTest,KnowledgeScopeMigrationTest' test`

Expected: FAIL because V6 and scope fields do not exist.

- [ ] **Step 3: Implement migration, enum, row mapping, inserts, and updates**

Use non-null `scope_type` with default GLOBAL. Keep existing Flyway history table configuration unchanged.

- [ ] **Step 4: Run persistence tests**

Run: `mvn -pl agent-server -Dtest='KnowledgeDocumentRepositoryTest,KnowledgeScopeMigrationTest' test`

Expected: PASS.

- [ ] **Step 5: Commit scope persistence**

```bash
git add agent-server
git commit -m "feat: persist global tenant and organization knowledge scopes"
```

### Task 2: Implement one knowledge access policy and scoped state machine

**Files:**
- Create: `agent-server/src/main/java/io/github/sukimiya/agileagent/knowledge/KnowledgeAccessPolicy.java`
- Create: `agent-server/src/test/java/io/github/sukimiya/agileagent/knowledge/KnowledgeAccessPolicyTest.java`
- Modify: `agent-server/src/main/java/io/github/sukimiya/agileagent/knowledge/KnowledgeDocumentService.java`
- Modify: `agent-server/src/main/java/io/github/sukimiya/agileagent/knowledge/KnowledgeFileStorage.java`
- Modify: corresponding service/storage tests

**Interfaces:**
- Consumes: `IdentityContext`, scoped repository rows, LLM summarization, and file promotion.
- Produces: `canList`, `canDownload`, `canApprove`, and `canConsume` predicates; derived upload scope; platform/tenant admin auto-approval; ordinary-user ORG pending flow.

- [ ] **Step 1: Write failing policy matrix tests**

Cover platform admin, tenant admin, regular uploader, same/different tenant, manageable/readable orgs, same org ID across different tenants, missing tenant/org, and all three scopes.

- [ ] **Step 2: Run policy tests and verify RED**

Run: `mvn -pl agent-server -Dtest=KnowledgeAccessPolicyTest test`

Expected: FAIL because the policy does not exist.

- [ ] **Step 3: Implement the pure policy**

All methods require both tenant match and org membership for ORG decisions. GLOBAL consumption is readable by all authenticated identities; GLOBAL management remains platform-admin-only.

- [ ] **Step 4: Write failing scoped upload/approval tests**

Cover derived scopes/statuses, missing identity rejection before storage, tenant-admin cross-tenant denial, auto-approve return value, promotion-before-approved ordering, and cleanup/consistent status after storage or repository failure.

- [ ] **Step 5: Run service tests and verify RED**

Run: `mvn -pl agent-server -Dtest='KnowledgeDocumentServiceTest,KnowledgeFileStorageTest' test`

Expected: FAIL on scoped behavior.

- [ ] **Step 6: Implement the scoped state machine using only `KnowledgeAccessPolicy`**

Persist identity-derived IDs and display-name snapshots. Do not duplicate authorization expressions inside the service.

- [ ] **Step 7: Run policy/service/storage tests**

Run: `mvn -pl agent-server -Dtest='KnowledgeAccessPolicyTest,KnowledgeDocumentServiceTest,KnowledgeFileStorageTest' test`

Expected: PASS.

- [ ] **Step 8: Commit the scoped state machine**

```bash
git add agent-server
git commit -m "feat: authorize scoped knowledge workflows"
```

### Task 3: Enforce scope on knowledge management APIs

**Files:**
- Modify: `agent-server/src/main/java/io/github/sukimiya/agileagent/knowledge/KnowledgeController.java`
- Modify: controller and integration tests

**Interfaces:**
- Consumes: current `AuthUser.identity`, `KnowledgeDocumentService`, and `KnowledgeAccessPolicy` through the service.
- Produces: scoped upload/list/download/approve/reject; every summary the caller is authorized to see includes `scopeType`, `scopeName`, `tenantId`, and `orgId`.

- [ ] **Step 1: Write failing WebFlux permission tests**

Cover each role and scope for list, direct-ID download, approve, reject, wrong tenant, unmanageable org, missing org upload, missing document, and state conflict.

- [ ] **Step 2: Run controller tests and verify RED**

Run: `mvn -pl agent-server -Dtest='KnowledgeControllerTest,KnowledgeControllerIntegrationTest' test`

Expected: FAIL because controller methods still use uploader/global behavior.

- [ ] **Step 3: Implement scoped endpoints and stable error bodies**

Use 404 when direct-ID access should hide resource existence, 403 for an explicit forbidden management action, 409 for state conflict, and 400 for missing tenant/org upload identity.

- [ ] **Step 4: Run controller and integration tests**

Run: `mvn -pl agent-server -Dtest='KnowledgeControllerTest,KnowledgeControllerIntegrationTest' test`

Expected: PASS.

- [ ] **Step 5: Commit scoped management APIs**

```bash
git add agent-server
git commit -m "feat: enforce knowledge scope in management APIs"
```

### Task 4: Apply the same scope to Agent knowledge consumption

**Files:**
- Modify: `agent-server/src/main/java/io/github/sukimiya/agileagent/knowledge/KnowledgeCatalog.java`
- Modify: `agent-server/src/main/java/io/github/sukimiya/agileagent/knowledge/KnowledgeCapabilityProvider.java`
- Modify: catalog/provider/domain-agent tests

**Interfaces:**
- Consumes: approved metadata carrying scope IDs, current `IdentityContext`, and `KnowledgeAccessPolicy.canConsume`.
- Produces: identity-filtered compact index and full-content reads; provider definitions are regenerated per execution identity.

- [ ] **Step 1: Write failing index/read symmetry tests**

Create GLOBAL, two-tenant, readable-org, unreadable-org, and same-org-ID/different-tenant documents. Assert an item is present in the index if and only if direct tool content is non-empty for that identity.

- [ ] **Step 2: Run tests and verify RED**

Run: `mvn -pl agent-server -Dtest='KnowledgeCatalogTest,KnowledgeCapabilityProviderTest,DomainAgentTest' test`

Expected: FAIL because catalog metadata and provider definitions are not fully scoped.

- [ ] **Step 3: Implement scoped catalog entries, index, and content reads**

Pass the current identity at execution time; do not cache an identity-filtered index globally. A refreshed identity must affect the next provider definition without catalog reload.

- [ ] **Step 4: Run knowledge and agent tests**

Run: `mvn -pl agent-server -Dtest='Knowledge*Test,DomainAgentTest,OrchestratorTest' test`

Expected: PASS.

- [ ] **Step 5: Commit scoped Agent consumption**

```bash
git add agent-server
git commit -m "feat: filter agent knowledge by tenant and organization"
```

### Task 5: Finish migration, deployment, and security documentation

**Files:**
- Modify: `README.md`
- Create: `docs/migration-from-single-module.md`
- Create: `docs/oauth2-provider-integration.md`
- Modify: `docs/capability-adapters.md`
- Modify: `docker/Dockerfile`
- Modify: `docker/docker-compose.yml`
- Modify: `Jenkinsfile`
- Modify: `start.sh`

**Interfaces:**
- Consumes: final module names, V1-V6 migrations, mock provider, required environment variables, and public APIs.
- Produces: reproducible local startup, production IdP replacement guide, database/session upgrade guide, neutral image/service names, and no embedded secret defaults.

- [ ] **Step 1: Add documentation assertions to the boundary test**

Assert README names all required variables and commands, Compose has no default `LLM_CONFIG_SECRET`, production Compose does not enable mock OAuth2, and migration docs state that existing sessions require re-login and V2 knowledge becomes GLOBAL.

- [ ] **Step 2: Run the boundary test and verify RED**

Run: `mvn -pl agent-server -Dtest=OpenSourceBoundaryTest test`

Expected: FAIL on missing release documentation or deployment constraints.

- [ ] **Step 3: Write release documentation and update deployment assets**

Document supported JDK, root/module Maven commands, local-mock startup, seed identities, claim mapping, HTTPS rules, required encryption key generation, Flyway upgrade, session invalidation, adapter packaging, and removal of enterprise endpoints.

- [ ] **Step 4: Run the boundary test**

Run: `mvn -pl agent-server -Dtest=OpenSourceBoundaryTest test`

Expected: PASS.

- [ ] **Step 5: Commit release documentation**

```bash
git add README.md docs docker Jenkinsfile start.sh agent-server
git commit -m "docs: publish migration and deployment guidance"
```

### Task 6: Perform final cross-module verification

**Files:**
- Modify only files required to fix failures proven by this task; each fix requires a focused regression test first.

**Interfaces:**
- Consumes: all prior phases.
- Produces: evidence for every completion criterion in the spec.

- [ ] **Step 1: Run formatting and diff integrity checks**

Run: `git diff --check`

Expected: no output, exit 0.

- [ ] **Step 2: Run the complete clean test suite**

Run: `mvn clean test`

Expected: zero failures and zero errors across all modules.

- [ ] **Step 3: Package both applications**

Run: `mvn -pl agent-server,mock-oauth2 -am package -DskipTests`

Expected: executable server and mock OAuth2 JARs are produced.

- [ ] **Step 4: Run end-to-end identity and multi-tenant security tests explicitly**

Run: `mvn -pl agent-server -am -Dtest='MockOAuth2EndToEndTest,TenantLlmConfigControllerTest,KnowledgeControllerIntegrationTest,KnowledgeCapabilityProviderTest' test`

Expected: PASS.

- [ ] **Step 5: Inspect the tracked boundary**

Run: `git ls-files pom.xml agent-spi agent-server mock-oauth2 docker Jenkinsfile start.sh README.md | rg '(^|/)(api-docs|crm|mes|qms|srm|wms)(/|$)|com/jowo|l7-skill'`

Expected: no matches.

- [ ] **Step 6: Review the complete branch diff against the spec checklist**

Confirm every item in spec section 18 has command or test evidence. Record any intentional deviation in `docs/migration-from-single-module.md` before completion.

- [ ] **Step 7: Commit verification-only fixes, if any**

```bash
git add -A
git commit -m "test: close open-source migration verification gaps"
```

Skip the commit when verification required no file changes.
