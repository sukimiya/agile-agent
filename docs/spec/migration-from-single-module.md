# Migrating from the single-module application

This guide covers moving an existing Agile Agent deployment to the open-source Maven reactor. The maintained product is a general-purpose agent server with the `agent-spi`, `agent-server`, and development-only `mock-oauth2` modules. The mock provider is not a production identity provider. Integrate external capabilities as ordinary dependencies on `agent-spi`; the server does not install adapter JARs at runtime.

## Build and run

Use JDK 21 for builds and runtime. Run reactor commands from the repository root so Maven can build module dependencies together:

```sh
mvn -pl agent-spi -am test
mvn -pl agent-server -am test
mvn -pl agent-server -am package
mvn -pl agent-server,mock-oauth2 -am -DskipTests package
```

From inside `agent-server/`, the equivalent server test command is:

```sh
mvn -f ../pom.xml -pl agent-server -am test
```

The last command packages the server and the local mock provider for the Compose `local-mock` profile. It is a development setup only. Set `LLM_CONFIG_SECRET` before starting the server; production secrets must be injected by the deployment environment or a secret manager.

```sh
export LLM_CONFIG_SECRET="$(openssl rand -base64 32)"
./start.sh
```

`start.sh` builds the server reactor modules, then starts the executable server JAR and, when `web/node_modules` exists, the Vite development server. For OAuth login against the mock identity service, first build the server and mock JARs, then run:

```sh
docker compose -f docker/docker-compose.yml --profile local-mock up --build agile-agent-server-local-mock
```

The profile runs the mock provider, MySQL, and Redis for local development. It publishes the server and mock-provider ports on loopback. Do not enable this profile in production or expose the mock provider to a network with real users or data.

## Database upgrade and session behavior

Flyway applies the server's migrations in order, V1 through V7. The configured history-table name is the exact legacy value `l7_skill_flyway_schema_history`; it is retained so existing databases keep their migration record. This table name is the sole legacy-brand compatibility exception.

| Version | Change |
| --- | --- |
| V1 (`V1__create_sessions_table.sql`) | Creates the session metadata table and user/update index. |
| V2 (`V2__create_knowledge_documents_table.sql`) | Creates the knowledge document table and status/upload index. |
| V3 (`V3__create_chat_hints_table.sql`) | Creates chat hints. |
| V4 (`V4__create_llm_provider_presets.sql`) | Creates LLM provider presets. |
| V5 (`V5__create_tenant_llm_config.sql`) | Creates tenant LLM configuration. |
| V6 (`V6__add_knowledge_scope.sql`) | Adds knowledge scope and tenant/organization metadata; existing knowledge rows are explicitly assigned `GLOBAL` scope with tenant and organization fields cleared. |
| V7 (`V7__add_knowledge_approval_claim_lease.sql`) | Adds `approval_owner`, `approval_claimed_at`, and monotonic `approval_version`; existing approved rows retain generation zero and their recorded reviewer as owner. |

Back up the database before deployment and let Flyway apply these migrations once against the shared database. Do not manually edit the Flyway history table or mark a migration complete without applying its SQL. After upgrading, existing sessions are invalidated and users must re-login; the new server cannot continue sessions from the previous application.

V7 claim timestamps implement a lease for an in-progress approval. `approval_owner` identifies the reviewer holding the claim, and `approval_version` is the fencing generation used to reject stale work after a lease is reclaimed. Keep database writes on the new server version during rollout. Mixed old and new application writers are unsupported: the old writer does not participate in the V6 scope rules or V7 lease/version fencing.

Knowledge files are stored below `APP_DATA_DIR/lib`. A multi-instance deployment must mount the same durable library storage at that path on every instance and use a shared database. Per-instance local disks are not suitable: database metadata and file content must remain available together after a request reaches any server instance.

Keep `LLM_CONFIG_SECRET` stable across restarts and identical across all server instances that read the same tenant LLM configuration. Generate a 32-byte key encoded as Base64 with `openssl rand -base64 32`, then provide it through the deployment's secret mechanism. Do not commit or put it in an image. If tenant API keys were encrypted by an earlier deployment, retain that deployment's key or re-encrypt those values before changing the key.

## Deployment boundaries

The default Compose service is the server image only. The `local-mock` profile is opt-in and contains the mock OAuth provider and its local database/cache services. Its database credentials are disposable local-development fixtures only. Production must supply its database, Redis, OAuth2 endpoints and client credentials, and all other secrets through the deployment environment or an external configuration service. The Compose file has no default for `LLM_CONFIG_SECRET`.

`agent-server` depends on the OAuth2 endpoint configuration, not on a provider implementation. A real identity integration may replace those endpoints or be packaged later as an additional Maven module without making the server depend on a vendor-specific provider.

Use HTTPS for every production OAuth2 endpoint, the registered server callback, the frontend redirect, and the public server URL. HTTP is intended only for loopback local development. When TLS terminates at a reverse proxy, set the OAuth redirect and frontend redirect values to their public HTTPS URLs and register the exact callback URL with the identity provider.

The open-source server no longer provides the former enterprise service-catalog endpoint. Clients must use the current public APIs and capability SPI; there is no compatibility endpoint in this distribution.
