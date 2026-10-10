# OpenClaw Instance Provisioning via Portainer — Design

**Date:** 2026-07-01
**Project:** `mcp-tool` (Spring Boot, `com.jowo.agent`)
**Status:** Approved design, pending implementation plan

## Context

We want a self-service way to spin up isolated OpenClaw instances on demand. A caller
supplies only a **DeepSeek API key** and an **instance name**, and the system deploys a
running OpenClaw container onto the Portainer host at `http://redacted-host.invalid:19002`,
pulling the OpenClaw image from the private Harbor registry at `192.168.3.119`.

This builds on prior work: we already produced and pushed a full OpenClaw image to
Harbor (`192.168.3.119/jowosmart/openclaw:20260701`) via `~/openclaw-pkg/build-image.sh`.
That image bakes personal secrets (device identity, gateway token, DeepSeek key), so it
cannot be shared across instances. This feature therefore also introduces a
**secrets-free base image** that each instance is created from.

The `mcp-tool` project is Spring Boot MVC (Java 21), REST controllers under `/api`, with
constructor injection and secrets kept in `application-local.yml`. It has no external
HTTP client today. The `mes-service` Jenkinsfile is the reference for Harbor image
naming (`jowosmart/...`) and registry credentials — not a CI trigger for this feature.

## Goals / Non-goals

- **Goal:** `POST /api/instances {instanceName, deepseekApiKey}` → deploy a running,
  isolated OpenClaw instance and return its access URL.
- **Non-goal (v1):** instance lifecycle management (list/stop/delete), idempotent
  re-deploy, reverse-proxy routing, multi-tenant quotas.

## Architecture & data flow

```
POST /api/instances {instanceName, deepseekApiKey}
        │
   InstanceController (com.jowo.agent.controller)
        │
   InstanceService  ──►  PortainerClient (Spring RestClient, X-API-Key)
        │                     Portainer @ http://redacted-host.invalid:19002/api
        │                     ├─ POST /endpoints/{id}/docker/images/create      (pull harbor base image)
        │                     ├─ POST /endpoints/{id}/docker/containers/create   (name, env, expose, publish-all, volume, restart)
        │                     ├─ POST /endpoints/{id}/docker/containers/{cid}/start
        │                     └─ GET  /endpoints/{id}/docker/containers/{cid}/json (read assigned host port)
        │
   returns {instanceName, containerId, url: http://redacted-host.invalid:<port>, gatewayToken, status}
```

Inside the container the entrypoint bootstraps (once per fresh volume): sets the DeepSeek
key from `DEEPSEEK_API_KEY`, ensures a fresh device identity + gateway token, then runs
`openclaw gateway run --bind lan --port 18789 --token "$OPENCLAW_GATEWAY_TOKEN" --force`.

## Component A — `openclaw-base` image (secrets-free)

Implemented as a new `--strip-secrets` mode in `~/openclaw-pkg/build-image.sh`.

- **Staging additionally excludes**: `identity/`, `devices/`, `service-env/`.
- **Scrubs**: `openclaw.json` → `gateway.auth.token`; `agents/main/agent/plugins/deepseek/catalog.json` → `providers.deepseek.apiKey`.
- **Keeps**: skills (enterprise-api), persona (AGENTS.md/SOUL.md/etc.), models/plugins config.
- **Entrypoint bootstrap** (runs when identity is absent, i.e. fresh volume):
  1. If `DEEPSEEK_API_KEY` is set → configure the deepseek provider key + `deepseek:default`
     auth profile via `openclaw config set` (fallback: patch `catalog.json` directly).
  2. Ensure device identity + gateway token exist — generate if missing; use
     `OPENCLAW_GATEWAY_TOKEN` from env when provided.
  3. `exec openclaw gateway run --bind lan --port 18789 --token "$OPENCLAW_GATEWAY_TOKEN" --force`.
- **Publish**: `192.168.3.119/jowosmart/openclaw-base:<tag>`.

> Verification needed during implementation: confirm OpenClaw auto-generates device
> identity on first `gateway run`; if not, add a headless bootstrap (`openclaw configure`/
> `doctor`) before the gateway starts.

## Component B — mcp-tool code (`com.jowo.agent`)

- `controller/InstanceController` — `POST /api/instances`, `@CrossOrigin`, constructor injection (matches `ChatController`).
- `service/InstanceService` — orchestrates pull → create → start → inspect; generates a
  per-instance gateway token (secure random); sanitizes `instanceName` → container name
  `openclaw-<name>`.
- `service/PortainerClient` — thin wrapper over Spring **RestClient** (new dependency-free;
  part of spring-boot-starter-web), base URL + `X-API-Key` header, configurable `endpointId`.
- `config/PortainerProperties` — `@ConfigurationProperties("portainer")`.
- DTOs: `CreateInstanceRequest {instanceName, deepseekApiKey}`,
  `InstanceResponse {instanceName, containerId, url, gatewayToken, status}`.

**Config**
- `application.yml`: `portainer.base-url: http://redacted-host.invalid:19002`,
  `portainer.endpoint-id: 1`, `portainer.image: 192.168.3.119/jowosmart/openclaw-base:<tag>`.
- `application-local.yml`: `portainer.api-key: ptr_…` (kept out of the committed main file,
  like the existing deepseek/enterprise secrets).

**Container create spec**
- `Image`: the harbor base image.
- `Env`: `DEEPSEEK_API_KEY`, `OPENCLAW_GATEWAY_TOKEN`, `OPENCLAW_BIND=lan`.
- `ExposedPorts`: `18789/tcp`.
- `HostConfig.PublishAllPorts`: `true` (Docker assigns a free host port).
- `HostConfig.Binds`/volume: named volume `openclaw-<name>` → `/root/.openclaw`.
- `HostConfig.RestartPolicy`: `unless-stopped`.

**Harbor pull auth (prerequisite):** register `192.168.3.119` as a Registry in Portainer
(with the `sukimiya` credentials) so pulls succeed. Fallback: send an `X-Registry-Auth`
header on the image-create call.

## Error handling & edge cases

- Duplicate name → container-create `409` → HTTP `409 Conflict` (reject; no auto-replace in v1).
- Invalid `instanceName` → sanitize to `[a-z0-9-]`; empty after sanitize → `400`.
- Missing/blank `deepseekApiKey` → `400` before any Portainer call.
- Harbor pull fails → surface Portainer message → `502`, hint about registry registration.
- Portainer unreachable / bad API key → `502` / `401`.
- Container started but no port bound (inspect race) → short retry, then `500`.
- **Never log** `deepseekApiKey` or the gateway token; mask in error output.
- Idempotency not guaranteed in v1 (same name twice = 409).

## Testing & verification

- **Unit** (`InstanceService`, mocked `PortainerClient`): asserts container-create JSON
  (env, name, publish-all, volume), URL built from inspected port, secrets never in
  thrown messages.
- **PortainerClient** (`@RestClientTest` / MockWebServer): the 4 calls + error mappings.
- **Manual E2E**: build & push `openclaw-base`; `POST /api/instances
  {instanceName:"demo", deepseekApiKey:"sk-…"}`; confirm the container in Portainer; hit
  the returned `http://redacted-host.invalid:<port>` gateway health; verify the injected key
  works with one agent turn.

## Open items to resolve in the plan

1. Confirm device-identity auto-generation on fresh volume (else add headless bootstrap).
2. Confirm the exact `openclaw config set` invocation for the deepseek provider key.
3. Pick the concrete base-image tag naming (e.g. date-based like the full image).
