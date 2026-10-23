# Agile Agent

Agile Agent is an open-source Maven multi-module project for a general-purpose agent server with a stable capability SPI. Build with JDK 21.

- `agent-spi` contains the public identity and capability contracts used by server-side adapters.
- `agent-server` contains HTTP APIs, authentication, orchestration, model integration, and the generic `KNOWLEDGE` capability.
- `mock-oauth2` is a standalone development identity service for local OAuth2 login flows.

External adapters are regular Spring Boot dependencies built against `agent-spi`; see the [capability adapter guide](docs/capability-adapters.md). For moving an existing deployment, see the [single-module migration guide](docs/spec/migration-from-single-module.md). Configure a production identity provider using the [OAuth2 provider guide](docs/oauth2-provider-integration.md).

## Build commands

Run Maven from the repository root so reactor dependencies are built together:

```sh
mvn -pl agent-spi -am test
mvn -pl agent-server -am test
mvn -pl agent-server -am package
mvn -pl agent-server,mock-oauth2 -am -DskipTests package
```

From `agent-server/`, the equivalent server test command is `mvn -f ../pom.xml -pl agent-server -am test`.

Memory persistence (M1) has opt-in checks beyond the default suite. Real-MySQL integration tests run against the loopback test database from `docker/docker-compose.memory-test.yml`: `mvn -Pmemory-mysql-it -pl agent-server -am test`. The failure-acceptance subset is `-Dtest=MemoryFailureIntegrationTest,TurnConcurrencyIntegrationTest`. H2 runs and successful builds do not count as MySQL concurrency acceptance. The real-model evaluation (`-Pmemory-eval-live`) runs only by hand in an isolated environment. Results, open items and the recovery drill record are in the [M1 acceptance record](docs/memory/phase-1-acceptance.md).

## Deployment limits

部署限制：单实例（replicas=1），多实例不受支持。

Run exactly one server instance per deployment (`app.memory.namespace`, default `default`). The Compose services pin `deploy.replicas: 1`. If a second live instance registers in the same namespace, both instances log `INSTANCE_CONFLICT` at ERROR, report `/actuator/health` as `OUT_OF_SERVICE` (HTTP 503), and refuse new turns with 503 `INSTANCE_CONFLICT` until only one remains; turns already running finish normally. Do not use the health status as a liveness probe that restarts the container. See the [memory operations notes](docs/memory/operations.md#单实例保护).

Other M1 limits: the production recovery objectives (RPO ≤ 15 min, RTO ≤ 4 h) have not been verified by a recovery drill. Attachment originals are not guaranteed to be recoverable long-term. `Idempotency-Key` is unique per tenant and user for 24 hours. A retried first request without a `conversationId` is replayed and not run or billed again; see the [memory API notes](docs/memory/api.md). The recovery runbook is in the [memory operations notes](docs/memory/operations.md#恢复手册仅限隔离环境演练).

## Local OAuth2 development

The server encrypts tenant LLM API keys with `LLM_CONFIG_SECRET`. Generate a Base64-encoded 32-byte key and provide it through the shell or your secret manager. Compose has no fallback value for this secret:

```sh
export LLM_CONFIG_SECRET="$(openssl rand -base64 32)"
```

Keep `LLM_CONFIG_SECRET` stable while encrypted tenant keys need to be read, including across restarts, redeployments, and restores of the same database; never commit it. The server's other production OAuth settings include `OAUTH2_AUTHORIZATION_URI`, `OAUTH2_TOKEN_URI`, `OAUTH2_USERINFO_URI`, optional `OAUTH2_REVOCATION_URI`, `OAUTH2_CLIENT_ID`, and `OAUTH2_CLIENT_SECRET`. Configure the claim mappings such as `OAUTH2_CLAIM_SUBJECT`, `OAUTH2_CLAIM_DISPLAY_NAME`, `OAUTH2_CLAIM_ROLES`, `OAUTH2_CLAIM_MANAGEABLE_ORG_IDS`, and `OAUTH2_CLAIM_READABLE_ORG_IDS` for your provider's user-info response. See the OAuth2 guide for the complete variable list and HTTPS requirements.

The default Compose service is the server image and does not enable or depend on the mock OAuth2 provider. The opt-in `local-mock` profile starts an isolated server, the mock identity provider, MySQL, and Redis with separate local data volumes. Package the server and mock applications first so Compose can mount the mock provider JAR:

```sh
mvn -pl agent-server,mock-oauth2 -am -DskipTests package
docker compose -f docker/docker-compose.yml --profile local-mock up --build agile-agent-server-local-mock
```

The local profile builds its own server image and does not depend on the default deployment image. Without `--profile local-mock`, Compose selects only the server service. To stop the local services, press Ctrl-C. Remove the local development databases and uploaded files with `docker compose -f docker/docker-compose.yml --profile local-mock down -v`.

The local-mock profile publishes the server and mock provider ports on `127.0.0.1` only. They are reachable from the same host and cannot be reached directly from other machines. The mock provider listens on all interfaces inside its container so the server can reach it over the Compose network.

Open `http://localhost:8088/api/auth/oauth2/authorize` to begin login. The server redirects to the mock identity chooser on port 8090; choosing a seed identity returns through the OAuth2 callback and redirects to `http://localhost:5173/login/callback#token=...`. The fragment contains the Agile Agent application token. A frontend development server on port 5173 is only needed to render that final route.

The mock provider seeds five neutral identities:

| Subject | Role | Tenant | Primary organization |
| --- | --- | --- | --- |
| `platform-admin` | `platform_admin` | Platform Tenant (`tenant-platform`) | Platform Root (`platform-root`) |
| `tenant-a-admin` | `tenant_admin` | Tenant A (`tenant-a`) | Tenant A Team 1 (`tenant-a-team-1`) |
| `tenant-a-user` | `user` | Tenant A (`tenant-a`) | Tenant A Team 1 (`tenant-a-team-1`) |
| `tenant-b-admin` | `tenant_admin` | Tenant B (`tenant-b`) | Tenant B Team 1 (`tenant-b-team-1`) |
| `tenant-b-user` | `user` | Tenant B (`tenant-b`) | Tenant B Team 1 (`tenant-b-team-1`) |

These identities and their organization scopes are local fixtures, not accounts for a business system. The mock is for development and isolated testing only. Do not expose it to the internet, connect it to real business data, or reuse the Compose development passwords outside this local profile.

The local profile defaults to client ID `agile-agent-local`, callback `http://localhost:8088/api/auth/oauth2/callback`, and an empty client secret. This mock configuration is only for loopback development. Production deployments must supply real provider endpoints and all required secrets externally, use HTTPS for provider endpoints and public redirect URLs, and register the exact callback URI with the provider. HTTPS is an operational deployment requirement; the application does not enforce URL schemes at startup. HTTP is suitable only for loopback development. No production secret is included in Compose or the image.

To run the server and optional frontend without Compose, set `LLM_CONFIG_SECRET` and run `./start.sh`. The script builds the reactor's server dependencies before starting the server JAR. Add `web/node_modules` if you also want it to start the Vite frontend.

Redis-backed tests use an isolated test-only Redis server on a random loopback port; the cross-module OAuth2 end-to-end test also starts a temporary H2 database. Run the tests on a machine with `redis-server` available on `PATH`:

```sh
mvn -pl agent-server -am -Dtest=MockOAuth2EndToEndTest test
```
