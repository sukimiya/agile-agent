## Final Review Fix Wave

**STATUS: PASS**

Closed the three final-review findings in one wave:

- `local-mock` host publishes now bind server `8088` and mock `8090` to `127.0.0.1`; the always-on server service keeps its existing `8088:8088` publish. The mock still listens on `0.0.0.0` inside the Compose network. README and boundary assertions describe and enforce the host loopback restriction.
- Redis-backed tests now share one test-only `redis-server` started on a random loopback port, with no password, Docker, or external service. The fixture redirects process output, stops Redis at test-JVM shutdown, and removes its temporary directory. AuthController, session-store, chat-memory, OAuth2-state, current-user resolver, and E2E tests use it. Legacy documentation uses `${TEST_REDIS_HOST}` / `${TEST_REDIS_PASSWORD}` or `redacted-host.invalid` placeholders.
- Organization reads and management now require a nonblank tenant before the platform-admin scope bypass. Tenantless and blank-tenant platform admins are denied; tenant-bearing platform admins retain the configured bypass.

### Verification

- `mvn -pl agent-server -am -Dtest='AuthorizationServiceTest,OpenSourceBoundaryTest,AuthControllerTest,AuthSessionStoreTest,ChatMemoryTest,OAuth2StateStoreTest,CurrentUserArgumentResolverTest,MockOAuth2EndToEndTest' -Dsurefire.failIfNoSpecifiedTests=false test` — **71 tests, 0 failures, 0 errors, 0 skipped**.
- `mvn clean test` — **237 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**.
- `mvn -pl agent-server,mock-oauth2 -am -DskipTests package` — **BUILD SUCCESS**.
- `DOCKER_REGISTRY_URL=registry.invalid IMAGE_TAG=review-round-2 docker compose -f docker/docker-compose.yml config --services` — only `agile-agent-server`.
- `DOCKER_REGISTRY_URL=registry.invalid IMAGE_TAG=review-round-2 docker compose -f docker/docker-compose.yml --profile local-mock config --services` — `agile-agent-mock-oauth2`, `mysql`, `redis`, `agile-agent-server-local-mock`, and `agile-agent-server`.
- `DOCKER_REGISTRY_URL=registry.invalid IMAGE_TAG=review-round-2 docker compose -f docker/docker-compose.yml --profile local-mock config --format json | jq -r '.services | {default: .["agile-agent-server"].ports, mock: .["agile-agent-mock-oauth2"].ports, local_server: .["agile-agent-server-local-mock"].ports, mock_address: .["agile-agent-mock-oauth2"].environment.SERVER_ADDRESS}'` — default server remains published on `8088` without an explicit host IP; profile ports render with host IP `127.0.0.1`; mock container address remains `0.0.0.0`.
- `git diff --check` — pass.
- Non-disclosing exact scan using `rg -n -F --file=-` with candidates read from the pre-fix tracked test and supplied only through stdin, over `git ls-files --cached --others --exclude-standard -z` — **0 endpoint matches, 0 credential matches**. Candidate values are omitted.

### Self-review and concerns

- `requireTenant` behavior is unchanged. `canManageOrg` and `canReadOrg` have no current production call sites under `agent-server/src`; both still fail closed for null identity or blank organization IDs.
- The boundary test scans `agent-server` and `mock-oauth2` production/test sources and resources, deployment files, and README for generic non-loopback Redis endpoints and inline Redis credentials. It uses sanitized fixtures and contains neither pre-fix value.
- Full tests require `redis-server` on `PATH`, now documented. This environment had it available. Compose parsing and rendered ports were checked; containers were not started.
- The full suite also emitted non-failing Reactor/BlockHound scheduler error logs and existing deprecation warnings; Maven reported all tests passing.
- No external credential rotation or Git history rewrite was performed. If the removed credential was active, rotate it out of band; prior commits still retain the pre-fix tree, so release history handling remains an owner follow-up.

Commit message: `fix: close portable identity security gaps`
