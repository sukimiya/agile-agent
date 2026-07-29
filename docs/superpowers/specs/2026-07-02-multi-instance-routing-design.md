# Multi-Instance OpenClaw Routing — Design

**Date:** 2026-07-02
**Project:** `mcp-tool` (Spring Boot, `com.jowo.agent`) + `web` (Next.js/React frontend)
**Status:** Approved design, pending implementation plan

## Context

[2026-07-01-openclaw-instance-provisioning-design.md](2026-07-01-openclaw-instance-provisioning-design.md)
added `POST /api/instances`, which provisions an isolated OpenClaw container via Portainer
and returns its access URL + gateway token — but nothing remembers that URL afterward.
Meanwhile `ChatController` / `AgentService` still route every conversation to a **single
local embedded OpenClaw** via `ProcessBuilder` (`openclaw agent --local --session-key
agent:main:<conversationId> --message ...`).

We want `mcp-tool` to remember every OpenClaw instance it provisions and let different
`conversationId`s talk to different instances — so a user can spin up an isolated OpenClaw
per project/customer/experiment and keep chatting with the right one across page reloads.

This design **removes the local embedded path entirely**: every conversation must be bound
to a Portainer-provisioned remote instance before it can chat. This is a deliberate scope
decision (confirmed during brainstorming) — it simplifies `AgentService` to a single code
path and matches the actual use case (all real usage should be isolated instances, not the
shared local one).

Technical grounding: `openclaw gateway call agent --url ws://<host>:<port> --token <token>
--params '{"message":...,"sessionKey":...,"idempotencyKey":...}' --expect-final --json`
was verified live against a running gateway — it works without any local install on the
target, returning the answer in `finalAssistantVisibleText`. Without `--expect-final` the
call returns immediately with `{"status":"accepted"}` and no token-level streaming is
available through the CLI — confirmed by direct testing. So remote chat is necessarily
"thinking → one full answer", not character-by-character streaming (this is an accepted
UX tradeoff, not a bug to fix later).

## Goals / Non-goals

- **Goal:** persist every provisioned instance's `{url, gatewayToken}`; bind
  `conversationId → instanceName`; route chat calls through that binding to the right
  remote Gateway; let a `conversationId` reuse an already-provisioned instance.
- **Goal:** frontend picker for "new chat" — choose an existing instance or create one.
- **Non-goal:** real token-level streaming for remote instances (accepted: full-answer
  response after a "thinking" state).
- **Non-goal:** instance lifecycle management beyond what already exists (still no
  list/stop/delete against Portainer itself — `GET /api/instances` reads mcp-tool's own
  registry, not live Portainer state).
- **Non-goal:** keeping the local embedded `openclaw agent --local` path — it is removed.

## Architecture & data flow

```
Frontend "new chat"
  │
  ├─ GET /api/instances  → list of {instanceName, status, createdAt}
  │
  ├─ (pick existing) ──► POST /api/conversations/{conversationId}/instance {instanceName}
  │                        └─ 404 if instanceName unknown
  │
  └─ (create new) ─────► POST /api/instances {instanceName, deepseekApiKey, conversationId}
                           └─ existing Portainer provisioning flow, THEN registers +
                              binds conversationId in the same call
                           └─ 409 if instanceName already exists (unchanged)

Chat turn (existing endpoints, unchanged request/response shape)
  POST /api/chat/completions {conversationId, message}
        │
   ChatController → AgentService.chat(conversationId, message)
        │
   InstanceRegistry.resolve(conversationId)          ── not bound → HTTP 404, no SSE
        │  {url, gatewayToken}
        ▼
   ProcessBuilder: openclaw gateway call agent
     --url ws://<host-from-url>:<port-from-url>
     --token <gatewayToken>
     --params {message, sessionKey:"agent:main:"+conversationId, idempotencyKey:<random>}
     --expect-final --json --timeout 120000
        │
   parse stdout JSON → result.finalAssistantVisibleText
        │
   SSE stream: {type:"thinking"} → one content chunk → {type:"done"}
     (or {type:"error"} on failure/timeout — no auto-retry, no auto-instance-switch)
```

## Data model & persistence

Single JSON file, e.g. `~/.jowo-agent/instances.json` (path configurable via
`application.yml`, analogous to other local state). Loaded into memory at startup;
every mutation does copy-on-write + atomic replace (write to `*.tmp`, then `rename`),
guarded by an in-process lock (single Spring instance, no clustering — matches current
deployment).

```json
{
  "instances": {
    "demo": {
      "instanceName": "demo",
      "containerId": "...",
      "url": "http://192.168.3.232:32768",
      "gatewayToken": "...",
      "status": "running",
      "createdAt": "2026-07-02T10:00:00Z"
    }
  },
  "bindings": {
    "conv-abc123": "demo"
  }
}
```

`gatewayToken` is stored in plaintext, consistent with how `application-local.yml`
already stores the DeepSeek key. The default path lives under the user's home directory
(`~/.jowo-agent/`), outside the repo, so it is never committed — no `.gitignore` entry
needed unless the configured path is later pointed inside the project tree.

A new `InstanceRegistry` service owns this file (read/write/lookup) and replaces
`InstanceService`'s current pure in-memory, no-state behavior. `InstanceService` keeps
owning the Portainer orchestration (`deploy(...)`) but calls into `InstanceRegistry` to
persist the result and bind the `conversationId` passed alongside it.

## Component changes (`com.jowo.agent`)

- **`service/InstanceRegistry`** (new) — loads/saves `instances.json`; exposes
  `save(InstanceRecord)`, `bind(conversationId, instanceName)`,
  `resolve(conversationId) -> Optional<InstanceRecord>`, `list() -> List<InstanceSummary>`.
- **`service/InstanceService`** — `deploy(...)` gains a `conversationId` parameter;
  after a successful Portainer deploy, calls `registry.save(...)` then
  `registry.bind(conversationId, instanceName)` before returning.
- **`controller/InstanceController`**:
  - `POST /api/instances` — request DTO gains required `conversationId`.
  - `GET /api/instances` (new) — returns `List<InstanceSummary>` (no `gatewayToken`).
- **`controller/ConversationInstanceController`** (new, or a method added to
  `ChatController`) — `POST /api/conversations/{conversationId}/instance
  {instanceName}` → `registry.bind(...)`, 404 if `instanceName` not in registry.
- **`service/AgentService.chat(conversationId, message)`** — rewritten:
  1. `registry.resolve(conversationId)` → if empty, caller (`ChatController`) returns
     HTTP 404 before opening the SSE stream (not an SSE error event — this must fail
     the initial request so the frontend can branch without parsing SSE).
  2. Build the `openclaw gateway call agent` command from the resolved `{url,
     gatewayToken}` (rewrite `http://` → `ws://` for `--url`), run via `ProcessBuilder`
     same as today, but non-streaming: wait for exit, parse the JSON stdout blob.
  3. Emit `{"type":"thinking",...}` immediately (as today), then one content line with
     `finalAssistantVisibleText`, then `{"type":"done"}`. On process failure/timeout,
     emit `{"type":"error","content":"实例无法访问，请重试或换实例"}` then `done`.
  4. `--timeout 120000` on the `gateway call` invocation, matching the existing
     `process.waitFor(120, TimeUnit.SECONDS)` bound.
- **`controller/ChatController`** — `chatCompletions`/`chat` resolve the binding first
  (via `InstanceRegistry`, injected) and return 404 synchronously if unbound, instead of
  unconditionally opening the `Flux<String>` SSE stream.

## Frontend changes (`web/src`)

- **New-chat flow** (`App.tsx`): replace "generate UUID → straight into chat" with a
  modal/panel shown before a `convId` is created:
  - Tab 1 "选现有实例": dropdown populated from `GET /api/instances`; select → `POST
    /api/conversations/{newConvId}/instance {instanceName}` → enter chat.
  - Tab 2 "新建实例": `instanceName` + `deepseekApiKey` inputs → `POST /api/instances
    {instanceName, deepseekApiKey, conversationId: newConvId}` → enter chat.
  - Only after this succeeds is `convId` generated/set and the URL updated
    (`?id=...`), matching the existing `pushState`/`reload` pattern in `newChat()`.
- **Existing `?id=xxx` links**: unchanged — go straight to the chat view; the backend
  binding already exists from when that conversation was created.
- **Chat header**: show the bound `instanceName` next to the existing conversation id
  chip, so the user can confirm which OpenClaw they're talking to.
- **Send-message 404 handling**: if `POST /api/chat/completions` returns 404 (unbound —
  e.g., registry file lost after a restart, or a hand-edited `?id=`), show an inline
  error and route the user back to the instance picker. No automatic fallback to any
  default/local instance (none exists anymore).

## Error handling & edge cases

- Unresolved `conversationId` on a chat call → HTTP 404 before any SSE stream starts.
- `openclaw gateway call agent` failure/timeout (container down, network unreachable) →
  SSE `error` event with a user-facing message; no auto-retry, no auto-instance-switch.
- Duplicate `instanceName` on `POST /api/instances` → HTTP 409 (unchanged from the prior
  design); does not implicitly bind the conversation to the pre-existing instance.
- `POST /api/conversations/{id}/instance` with unknown `instanceName` → HTTP 404.
- `gatewayToken` is never logged, consistent with the existing "never log secrets" rule
  for `deepseekApiKey`; `instances.json` lives outside the repo by default (see Data
  model section), so it is never committed.

## Testing & verification

- **Unit** (`InstanceRegistry`): save/bind/resolve/list round-trip against a temp file;
  concurrent-write safety (parallel binds don't corrupt the file).
- **Unit** (`InstanceService`): `deploy(...)` calls `registry.save` + `registry.bind` in
  the right order; failure before container start does not create a stray binding.
- **Unit** (`AgentService`, mocked process execution): unresolved conversation short-
  circuits before spawning a process; successful `gateway call` output is parsed into
  the right SSE event sequence; timeout/failure maps to the `error` event.
- **Manual E2E**: provision an instance from the picker, send a message, confirm the
  reply comes from that specific container (e.g. by checking a distinguishing fact only
  set in that instance); reload the page via `?id=`, confirm it reconnects without
  re-prompting; restart `mcp-tool`, confirm the registry file survives and old
  conversations keep working.
