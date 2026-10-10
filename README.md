# Agile Agent

Agile Agent is a Maven multi-module project for a general-purpose agent server with a stable capability SPI.

- `agent-spi` contains the public identity and capability contracts used by server-side adapters.
- `agent-server` contains HTTP APIs, authentication, orchestration, model integration, and the generic `KNOWLEDGE` capability.
- `mock-oauth2` is a standalone development identity service for local OAuth2 login flows.

External adapters are regular Spring Boot dependencies built against `agent-spi`; see the [capability adapter guide](docs/capability-adapters.md). The project boundaries and migration decisions are recorded in the [open-source migration specification](docs/spec/2026-10-09-open-source-multimodule-migration.md).

## Local OAuth2 development

The server encrypts tenant LLM API keys with `LLM_CONFIG_SECRET`. Generate a standard Base64 secret containing 32 random bytes, then export it in the shell used to run Compose:

```sh
export LLM_CONFIG_SECRET="$(openssl rand -base64 32)"
```

Both the default server and the `local-mock` server require this host variable. Keep the value in a secret manager or local shell configuration; never commit it. Retain the same value while encrypted tenant keys need to be read.

The `local-mock` Compose profile starts an isolated agent server, the mock identity provider, MySQL, and Redis. It uses its own local data volumes and development-only database credentials. Package the two applications first so Compose can mount the mock provider JAR:

```sh
mvn -pl agent-server,mock-oauth2 -am -DskipTests package
docker compose -f docker/docker-compose.yml --profile local-mock up --build agile-agent-server-local-mock
```

The local profile builds its own server image and does not depend on the default deployment image. Without `--profile local-mock`, Compose still selects only the existing server service. To stop the local services, press Ctrl-C. Remove the local development databases and uploaded files with `docker compose -f docker/docker-compose.yml --profile local-mock down -v`.

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

For an external identity provider, activate a deployment-specific profile or provide configuration for the authorization, token, user-info, and revocation endpoints; client ID and secret; callback and frontend redirect URIs; scopes; JSON Pointer claim mappings; and role names. Register the exact callback URI with that provider. The local profile defaults to client ID `agile-agent-local`, callback `http://localhost:8088/api/auth/oauth2/callback`, and empty client secret. No production secret is included.

Redis-backed tests use an isolated test-only Redis server on a random loopback port; the cross-module OAuth2 end-to-end test also starts a temporary H2 database. Run the tests on a machine with `redis-server` available on `PATH`:

```sh
mvn -pl agent-server -am -Dtest=MockOAuth2EndToEndTest test
```
