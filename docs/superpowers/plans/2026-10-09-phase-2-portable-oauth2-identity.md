# Phase 2: Portable OAuth2 Identity and Mock Provider Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add configuration-driven tenant/organization identity mapping, centralized authorization, refreshable session identity, and a standalone local OAuth2 provider suitable for end-to-end development.

**Architecture:** `agent-server` maps arbitrary userinfo JSON into the `agent-spi` `IdentityContext` through configurable JSON Pointers and refreshes that identity with the session. `mock-oauth2` implements the protocol surface already consumed by the server and supplies deterministic multi-tenant seed users. Production replacement requires only endpoint, client, claim, and role configuration.

**Tech Stack:** Spring Boot WebFlux, Reactor, Redis, Jackson JsonPointer, HTML form rendering, JUnit 5, WebTestClient

**Spec:** `docs/spec/2026-10-09-open-source-multimodule-migration.md`

## Global Constraints

- Keep Authorization Code + PKCE S256, state binding, refresh-token rotation handling, and best-effort revoke.
- Identity cache age is five minutes; privileged and tenant operations fail closed when refresh fails.
- Missing tenant identity may log in but may not access tenant resources.
- Token values never enter `IdentityContext`, logs, error bodies, or mock UI URLs.
- `mock-oauth2` binds loopback by default and is never a runtime dependency of `agent-server`.
- Claim mappings and role names are externally configurable.

## Review Focus

- Missing or type-mismatched array claims must produce a controlled identity-mapping error, not silently grant empty admin scopes; covered in Task 1.
- Concurrent expired-token requests must refresh the token family once and converge on the refreshed identity; covered in Task 3.
- A user whose admin role is removed must lose privileges after the five-minute identity refresh window; covered in Task 3.
- Reusing an authorization code or a rotated refresh token must fail; covered in Task 5.
- Open redirects through OAuth callback or mock authorize redirect URI must be rejected; covered in Tasks 4 and 6.

---

### Task 1: Map configurable userinfo claims into `IdentityContext`

**Files:**
- Create: `agent-server/src/main/java/io/github/sukimiya/agileagent/config/OAuth2ClaimProperties.java`
- Modify: `agent-server/src/main/java/io/github/sukimiya/agileagent/config/OAuth2Properties.java`
- Create: `agent-server/src/main/java/io/github/sukimiya/agileagent/auth/OAuth2IdentityMapper.java`
- Create: `agent-server/src/main/java/io/github/sukimiya/agileagent/auth/JsonPointerOAuth2IdentityMapper.java`
- Create: `agent-server/src/main/java/io/github/sukimiya/agileagent/auth/IdentityMappingException.java`
- Create: `agent-server/src/test/java/io/github/sukimiya/agileagent/auth/JsonPointerOAuth2IdentityMapperTest.java`
- Modify: `agent-server/src/main/resources/application.yml`

**Interfaces:**
- Consumes: `JsonNode` userinfo and `IdentityContext` from `agent-spi`.
- Produces: `OAuth2IdentityMapper.map(JsonNode): IdentityContext`; configurable pointers for subject, display name, tenant, primary org, roles, manageable orgs, and readable orgs; configurable platform/tenant admin role names.

- [ ] **Step 1: Write failing mapper tests**

Cover the complete mock claim shape, custom nested pointers, missing required subject, missing optional tenant/org, non-array roles/scopes, immutable output sets, and derivation of both admin booleans from role names.

- [ ] **Step 2: Run mapper tests and verify RED**

Run: `mvn -pl agent-server -Dtest=JsonPointerOAuth2IdentityMapperTest test`

Expected: FAIL because the mapper and properties do not exist.

- [ ] **Step 3: Implement properties, mapper interface, default JSON Pointer mapper, and stable exceptions**

Required claim failures use `IDENTITY_CLAIMS_INVALID`; optional missing tenant/org fields map to null/empty sets without granting roles.

- [ ] **Step 4: Run mapper and property binding tests**

Run: `mvn -pl agent-server -Dtest='JsonPointerOAuth2IdentityMapperTest,OAuth2PropertiesTest' test`

Expected: PASS.

- [ ] **Step 5: Commit claim mapping**

```bash
git add agent-server
git commit -m "feat: map configurable OAuth2 identity claims"
```

### Task 2: Centralize authorization decisions

**Files:**
- Create: `agent-server/src/main/java/io/github/sukimiya/agileagent/auth/AuthorizationService.java`
- Create: `agent-server/src/main/java/io/github/sukimiya/agileagent/web/ForbiddenException.java`
- Create: `agent-server/src/main/java/io/github/sukimiya/agileagent/web/ApiError.java`
- Modify: `agent-server/src/main/java/io/github/sukimiya/agileagent/web/AuthExceptionHandler.java`
- Create: `agent-server/src/test/java/io/github/sukimiya/agileagent/auth/AuthorizationServiceTest.java`
- Modify: `agent-server/src/test/java/io/github/sukimiya/agileagent/web/AuthExceptionHandlerTest.java`

**Interfaces:**
- Consumes: `IdentityContext`.
- Produces: `requirePlatformAdmin`, `requireTenantAdmin`, `requireTenant`, `canManageOrg`, and `canReadOrg`; stable JSON error body `ApiError(String code, String message)`.

- [ ] **Step 1: Write the failing authorization matrix tests**

Exercise platform admin, tenant admin, regular user, missing tenant, null org, manageable org, readable org, and cross-tenant denial.

- [ ] **Step 2: Run tests and verify RED**

Run: `mvn -pl agent-server -Dtest='AuthorizationServiceTest,AuthExceptionHandlerTest' test`

Expected: FAIL because the service and JSON error contract do not exist.

- [ ] **Step 3: Implement authorization and error mapping**

Use 403 for existing identities without permission and 502 for identity-provider availability failures. Methods that require a tenant return the validated tenant ID.

- [ ] **Step 4: Run focused tests**

Run: `mvn -pl agent-server -Dtest='AuthorizationServiceTest,AuthExceptionHandlerTest' test`

Expected: PASS.

- [ ] **Step 5: Commit authorization**

```bash
git add agent-server
git commit -m "feat: centralize identity authorization"
```

### Task 3: Store and refresh identity with OAuth2 sessions

**Files:**
- Modify: `agent-server/src/main/java/io/github/sukimiya/agileagent/service/OAuth2Client.java`
- Modify: `agent-server/src/main/java/io/github/sukimiya/agileagent/service/AuthSessionStore.java`
- Modify: `agent-server/src/main/java/io/github/sukimiya/agileagent/web/CurrentUserArgumentResolver.java`
- Modify: `agent-server/src/main/java/io/github/sukimiya/agileagent/controller/AuthController.java`
- Modify: corresponding OAuth2, session, resolver, and controller tests

**Interfaces:**
- Consumes: `OAuth2IdentityMapper` and current token refresh/store APIs.
- Produces: `AuthUser` containing subject/name/tokens/token expiry/`IdentityContext`/`identityFetchedAt`; `OAuth2Client.fetchIdentity(accessToken): Mono<IdentityContext>`; five-minute identity freshness.

- [ ] **Step 1: Write failing session-shape and refresh tests**

Cover callback persistence, pre-migration payload rejection, identity refresh after five minutes, refresh after access-token rotation, removed-admin-role propagation, identity-provider failure, and concurrent access-token refresh single flight.

- [ ] **Step 2: Run the focused suite and verify RED**

Run: `mvn -pl agent-server -Dtest='AuthSessionStoreTest,OAuth2ClientTest,CurrentUserArgumentResolverTest,AuthControllerTest' test`

Expected: FAIL on the new identity fields and refresh behavior.

- [ ] **Step 3: Implement the new session payload and refresh pipeline**

After token refresh, fetch identity before persisting. When only identity is stale, fetch userinfo with the current token and atomically update the session. Reuse the existing Redis single-flight lock per app session.

- [ ] **Step 4: Run auth tests and the server suite**

Run: `mvn -pl agent-server -Dtest='AuthSessionStoreTest,OAuth2ClientTest,CurrentUserArgumentResolverTest,AuthControllerTest' test`

Expected: PASS.

Run: `mvn -pl agent-server test`

Expected: PASS.

- [ ] **Step 5: Commit refreshable identity**

```bash
git add agent-server
git commit -m "feat: refresh tenant identity in OAuth2 sessions"
```

### Task 4: Implement the mock provider token stores and PKCE rules

**Files:**
- Create: `mock-oauth2/src/main/java/io/github/sukimiya/agileagent/mockoauth/MockOAuth2Application.java`
- Create: `mock-oauth2/src/main/java/io/github/sukimiya/agileagent/mockoauth/MockIdentityCatalog.java`
- Create: `mock-oauth2/src/main/java/io/github/sukimiya/agileagent/mockoauth/AuthorizationCodeStore.java`
- Create: `mock-oauth2/src/main/java/io/github/sukimiya/agileagent/mockoauth/TokenStore.java`
- Create: `mock-oauth2/src/main/java/io/github/sukimiya/agileagent/mockoauth/PkceVerifier.java`
- Create: corresponding tests under `mock-oauth2/src/test/java/...`

**Interfaces:**
- Consumes: OAuth2 PKCE S256 inputs and deterministic seed identities.
- Produces: one-use authorization codes with 10-minute TTL, access tokens with one-hour TTL, rotating refresh tokens, revocation, and lookup of active identity by access token.

- [ ] **Step 1: Write failing store and PKCE tests**

Cover correct verifier, wrong verifier, plain challenge rejection, code expiry, code replay, refresh rotation/replay, access expiry, and revocation.

- [ ] **Step 2: Run mock core tests and verify RED**

Run: `mvn -pl mock-oauth2 -Dtest='PkceVerifierTest,AuthorizationCodeStoreTest,TokenStoreTest' test`

Expected: FAIL because mock protocol classes do not exist.

- [ ] **Step 3: Implement in-memory stores with injectable `Clock` and `SecureRandom`**

Never place tokens in exception text or `toString`. Seed identities must include two tenants and different readable/manageable organization sets.

- [ ] **Step 4: Run mock core tests**

Run: `mvn -pl mock-oauth2 -Dtest='PkceVerifierTest,AuthorizationCodeStoreTest,TokenStoreTest' test`

Expected: PASS.

- [ ] **Step 5: Commit mock protocol core**

```bash
git add mock-oauth2
git commit -m "feat: add mock OAuth2 token and PKCE core"
```

### Task 5: Expose mock OAuth2 endpoints and local user selection

**Files:**
- Create: `mock-oauth2/src/main/java/io/github/sukimiya/agileagent/mockoauth/OAuth2Controller.java`
- Create: `mock-oauth2/src/main/java/io/github/sukimiya/agileagent/mockoauth/LoginPageController.java`
- Create: `mock-oauth2/src/main/resources/templates/login.html`
- Create: `mock-oauth2/src/main/resources/application.yml`
- Create: `mock-oauth2/src/test/java/io/github/sukimiya/agileagent/mockoauth/OAuth2ControllerTest.java`

**Interfaces:**
- Consumes: stores from Task 4.
- Produces: `/oauth2/authorize`, `/oauth2/token`, `/oauth2/userinfo`, `/oauth2/revoke`; loopback binding; seeded user chooser; JSON claims matching the spec defaults.

- [ ] **Step 1: Write failing WebTestClient endpoint tests**

Cover redirect URI allowlisting, authorize form, code exchange, userinfo Bearer auth, refresh, revoke, invalid client, missing state, and token non-disclosure in rendered HTML.

- [ ] **Step 2: Run controller tests and verify RED**

Run: `mvn -pl mock-oauth2 -Dtest=OAuth2ControllerTest test`

Expected: FAIL because endpoints do not exist.

- [ ] **Step 3: Implement form and protocol endpoints**

The configured client has an exact redirect URI allowlist. Token endpoint accepts form encoding. Bind `server.address` to `127.0.0.1` by default.

- [ ] **Step 4: Run the mock module suite**

Run: `mvn -pl mock-oauth2 test`

Expected: PASS.

- [ ] **Step 5: Commit mock endpoints**

```bash
git add mock-oauth2
git commit -m "feat: expose the local mock OAuth2 provider"
```

### Task 6: Add local profile and end-to-end OAuth2 integration

**Files:**
- Create: `agent-server/src/main/resources/application-local-mock.yml`
- Create: `agent-server/src/test/java/io/github/sukimiya/agileagent/auth/MockOAuth2EndToEndTest.java`
- Modify: `docker/docker-compose.yml`
- Modify: `README.md`

**Interfaces:**
- Consumes: runnable `agent-server` and `mock-oauth2` modules.
- Produces: a local profile with mock endpoints/claims and an integration test covering browser redirect through callback, session creation, `/api/auth/me`, identity refresh, and logout/revoke.

- [ ] **Step 1: Write the failing cross-module integration test**

Start both applications on random ports. Drive authorize with state-binding cookie, select a tenant user, exchange callback, extract the app token fragment, and assert `/api/auth/me` returns subject, display name, tenant, organization, and roles without any provider token.

- [ ] **Step 2: Run the integration test and verify RED**

Run: `mvn -pl agent-server -am -Dtest=MockOAuth2EndToEndTest test`

Expected: FAIL until the profile and test wiring can start both applications.

- [ ] **Step 3: Add local profile, Compose development service, and startup documentation**

Keep mock startup opt-in through a Compose profile. Do not provide production secrets.

- [ ] **Step 4: Verify Phase 2**

Run: `mvn -pl agent-server -am -Dtest=MockOAuth2EndToEndTest test`

Expected: PASS.

Run: `mvn clean test`

Expected: all modules pass.

- [ ] **Step 5: Commit portable identity integration**

```bash
git add agent-server mock-oauth2 docker README.md
git commit -m "feat: integrate the configurable mock OAuth2 identity flow"
```
