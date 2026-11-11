# Local development

This page collects local build, Compose, and `start.sh` details that used to live on the repository homepage. For the shortest path, see the [README Quick start](../README.md#quick-start). Production OAuth2 variables and claim mappings are in the [OAuth2 provider guide](oauth2-provider-integration.md).

## Maven build commands

Run Maven from the repository root so reactor dependencies are built together:

```sh
mvn -pl agent-spi -am test
mvn -pl agent-server -am test
mvn -pl agent-server -am package
mvn -pl agent-server,mock-oauth2 -am -DskipTests package
```

From `agent-server/`, the equivalent server test command is `mvn -f ../pom.xml -pl agent-server -am test`.

## `LLM_CONFIG_SECRET`

The server encrypts tenant LLM API keys with `LLM_CONFIG_SECRET`. Generate a Base64-encoded 32-byte key and provide it through the shell or your secret manager. Compose has no fallback value for this secret:

```sh
export LLM_CONFIG_SECRET="$(openssl rand -base64 32)"
```

Keep `LLM_CONFIG_SECRET` stable while encrypted tenant keys need to be read, including across restarts, redeployments, and restores of the same database; never commit it.

## Local-mock Compose behavior

The default Compose service is the server image and does not enable or depend on the mock OAuth2 provider. The opt-in `local-mock` profile starts an isolated server, the mock identity provider, MySQL 8.4, and Redis with separate local data volumes.

The local profile builds its own server image (`agile-agent-server-local-mock:local`) and does not depend on the default deployment image. Without `--profile local-mock`, Compose selects only the server service.

The local-mock profile publishes the server (`8088`) and mock provider (`8090`) ports on `127.0.0.1` only. They are reachable from the same host and cannot be reached directly from other machines. The mock provider listens on all interfaces inside its container (`SERVER_ADDRESS: 0.0.0.0`) so the server can reach it over the Compose network.

The local profile defaults to client ID `agile-agent-local`, callback `http://localhost:8088/api/auth/oauth2/callback`, frontend redirect `http://localhost:5173/login/callback`, and an empty client secret. This mock configuration is only for loopback development. Production deployments must supply real provider endpoints and all required secrets externally, use HTTPS for provider endpoints and public redirect URLs, and register the exact callback URI with the provider. HTTPS is an operational deployment requirement; the application does not enforce URL schemes at startup. HTTP is suitable only for loopback development. No production secret is included in Compose or the image.

These identities and their organization scopes are local fixtures, not accounts for a business system. The mock is for development and isolated testing only. Do not expose it to the internet, connect it to real business data, or reuse the Compose development passwords outside this local profile. Seeded subjects and the password warning are listed in the [OAuth2 provider guide](oauth2-provider-integration.md#local-mock-provider).

## Running without Compose (`start.sh`)

To run the server and optional frontend without Compose, set `LLM_CONFIG_SECRET` and run `./start.sh`. The script:

- refuses to start if `LLM_CONFIG_SECRET` is unset
- runs `mvn -pl agent-server -am -DskipTests package`
- starts `agent-server/target/agile-agent-server-0.0.1-SNAPSHOT.jar` in the background (logs under `logs/backend.log`)
- starts the Vite frontend only if `web/node_modules` exists; otherwise it prints that `web/` should be installed first with `cd web && npm install`

## Frontend

The SPA lives in `web/` (React, TypeScript, Vite). Install and run it from that directory:

```sh
cd web
npm ci
npm test
npm run build
npm run dev
```

`npm run dev` serves http://localhost:5173 and proxies `/api` to `VITE_API_PROXY_TARGET` (default `http://127.0.0.1:8088`). Leave `VITE_API_BASE_URL` empty so requests stay same-origin; see `web/.env.example`. `./start.sh` starts the Vite server only when `web/node_modules` already exists.

`npm run build` writes static files to `web/dist`. Serve that directory behind nginx (or any static host) on the same origin as the API. The site root must fall back to `index.html` so `/login/callback` works on refresh, and `/api/` must be proxied to the server with response buffering disabled so chat SSE is not held until the turn ends:

```nginx
location /api/ {
    proxy_pass http://127.0.0.1:8088;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_buffering off;
    proxy_read_timeout 3600s;
}

location / {
    root /var/www/agile-agent;   # contents of web/dist
    try_files $uri $uri/ /index.html;
}
```

For that deployment, set `OAUTH2_FRONTEND_REDIRECT_URI` to `https://<your-host>/login/callback` (production host `https://ai.avacha.cloud/login/callback`). More detail is in [web/README.md](../web/README.md).

## Mock OAuth2 end-to-end tests

Redis-backed tests use an isolated test-only Redis server on a random loopback port; the cross-module OAuth2 end-to-end test also starts a temporary H2 database. Run the tests on a machine with `redis-server` available on `PATH`:

```sh
mvn -pl agent-server -am -Dtest=MockOAuth2EndToEndTest test
```

## Extra memory-test commands

Opt-in checks beyond the default suite (also described in `docs/memory/eval-baseline.md` and `agent-server/pom.xml`):

- Real-MySQL integration tests against the loopback test database from `docker/docker-compose.memory-test.yml`: `mvn -Pmemory-mysql-it -pl agent-server -am test`
- Failure-acceptance subset: `-Dtest=MemoryFailureIntegrationTest,TurnConcurrencyIntegrationTest`
- H2 runs and successful builds do not count as MySQL concurrency acceptance.
- Live-model evaluation is a separate opt-in profile: `mvn -Pmemory-eval-live -pl agent-server -am test`

## Server image JVM flags

The image `docker/Dockerfile` starts the JVM as:

```
java -server -XX:+UseCompactObjectHeaders --enable-native-access=ALL-UNNAMED -Xms1024m -Xmx1024m -Djava.security.egd=file:/dev/urandom -Duser.timezone=Asia/Shanghai -jar /app/agile-agent-server.jar
```

`--enable-native-access=ALL-UNNAMED` is required for the zstd native library on JDK 25. `-XX:+UseCompactObjectHeaders` is a JDK 25 option (off by default in the JDK). The 1 GB heap (`-Xms1024m`/`-Xmx1024m`) is the image default, not an independently measured minimum host requirement. Related compression notes live in [memory operations](memory/operations.md#原始消息分层压缩m2b-task-9默认关闭).
