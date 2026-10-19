# Capability adapters

An adapter adds a capability by implementing the public contracts in `agent-spi` and registering an `AgentCapabilityProvider` Spring bean. The adapter depends on the SPI artifact, not `agile-agent-server` implementation packages.

## Maven dependency

Use the same published version of the SPI as the server:

```xml
<dependency>
    <groupId>io.github.sukimiya</groupId>
    <artifactId>agile-agent-spi</artifactId>
    <version>${agile-agent.version}</version>
</dependency>
```

The current reactor version is `0.0.1-SNAPSHOT`; consumers should use a released version. The SPI declares Reactor Core and Jackson Databind as dependencies because its method signatures use `Mono` and Jackson JSON nodes.

## Public contracts

All types below are in `io.github.sukimiya.agileagent.spi`:

- `identity.IdentityContext` is an immutable view of the authenticated subject, display name, optional tenant and primary organization, roles, readable and manageable organization IDs, and administrator flags. Its set fields are copied and immutable; `subject` is required. It contains no OAuth2 credential.
- `capability.AgentCapabilityProvider` identifies one capability with `code()`, `displayName()`, and `routingDescription()`. Implement `definition(AgentExecutionContext)` to resolve the prompt and tools for the current execution, and `execute(ToolInvocation, AgentExecutionContext)` to run one permitted tool. Both return Reactor `Mono` values.
- `capability.AgentExecutionContext` carries the `IdentityContext`, a required conversation ID, and a `Supplier<String>` access-token accessor. The accessor is lazy and controlled by the host; providers should obtain a token only when an operation requires it. The accessor is excluded from JSON and from `toString()`.
- `capability.CapabilityDefinition` contains a required system prompt and an immutable list of `ToolDefinition` values. Resolve definitions at execution time when available tools or schema depend on identity or other runtime state.
- `capability.ToolDefinition` contains the tool name, description, and JSON Schema parameter node. The name must be nonblank, description and schema must be non-null, and schema input is copied into a normalized snapshot.
- `capability.ToolInvocation` contains the selected tool name, call ID, and JSON arguments. The name and ID must be nonblank, and arguments are copied into a normalized snapshot.
- `capability.ToolResult` contains a success flag and non-null display text. The text is sent onward as model-visible tool output, so the adapter must make it safe to show to the user and must not include credentials or sensitive internal details.

The SPI does not grant authorization by itself. Use the supplied identity to make capability-specific access decisions, and enforce those decisions in the adapter's execution path.

## Register with Spring Boot

Package the adapter as an ordinary dependency and provide an auto-configuration class that declares the provider bean:

```java
package example.adapter;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import io.github.sukimiya.agileagent.spi.capability.AgentCapabilityProvider;

@AutoConfiguration
public class ExternalAdapterAutoConfiguration {
    @Bean
    AgentCapabilityProvider externalCapabilityProvider() {
        return new ExternalCapabilityProvider();
    }
}
```

Register that auto-configuration in the adapter JAR at
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`, with one fully qualified class name per line:

```text
example.adapter.ExternalAdapterAutoConfiguration
```

When the adapter dependency is on the application's classpath, Spring Boot discovers the configuration and the server collects its provider bean. Capability codes must be stable and unique within the application. Registration is a normal compile-time dependency; the server does not download, upload, or dynamically load adapter JARs.

## Dynamic definitions and tool execution

The server asks each selected provider for its definition for the current execution. The registry validates every returned definition before it is passed to the model: tool names must be nonblank and unique within the capability. `ToolDefinition` and `ToolInvocation` also validate their own required fields and take defensive JSON snapshots. The orchestrator only accepts routed capability codes present in the registry, and the domain agent only executes tool names declared by the selected definition.

Return a safe `ToolResult` for expected outcomes. If an implementation fails unexpectedly, let the error flow through the reactive result; the server converts provider failures to a stable, credential-safe failure. Do not put tokens, request authorization headers, or raw upstream error bodies in exception text or result text.

At application startup the registry rejects a blank capability code and duplicate codes. Providers are ordered by code before duplicates are checked, so the duplicate-code error is deterministic regardless of Spring bean ordering and names the lexically first duplicate code encountered. Treat code collisions as a configuration error and choose a distinct stable code.

## Credential handling

Never log, persist, serialize, or expose an OAuth2 access token. Do not copy it into identity objects, prompts, tool definitions, invocations, results, exception messages, metrics, or tracing attributes. The host keeps the token behind the lazy `AgentExecutionContext.accessTokenAccessor()`; call it only immediately before an authenticated external request that needs it, and pass it directly to that request. Do not retain the returned string beyond that operation. The safe-result and safe-error boundary remains the adapter's responsibility: redact credentials and sensitive response data before producing user-visible output or throwing an error.

## In-reactor example

`agent-medical` is the first adapter that lives inside this repository's Maven reactor instead of an external project. It follows the same rules: it depends on `agile-agent-spi` and Spring-managed libraries only, never on `agent-server` packages, and it registers its provider through `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. Its `MedicalAutoConfiguration` is guarded with `@ConditionalOnBean(ConnectionFactory.class)`, so applications that exclude R2DBC auto-configuration boot without any medical beans. Its package root `io.github.sukimiya.medical` is deliberately outside the server's component-scan base package: the provider is registered exactly once, by auto-configuration, and never also by component scan (which would trip the registry's duplicate-code guard).

## Package lifecycle and six-gate validation

`agent-medical` treats every hospital as knowledge data with a fenced lifecycle.
`HospitalPackageLifecycleService` (a plain bean, no REST surface) owns the only status
transitions:

- `submit` upserts the draft by `hospital_code` with replacement semantics: the row goes
  back to `PENDING`, `approval_version` increments, and all review columns are cleared, so
  a package replaced mid-review can never be approved on a stale report.
- `review` runs the fixed six-gate pipeline — SCHEMA, SEMANTIC, PROVENANCE, CAPABILITY,
  SAFETY, SIMULATION — collects every violation into a `GateReport`, and stores it without
  bumping the version. Unparsable content skips everything after SCHEMA; a red CAPABILITY
  gate skips SIMULATION so un-screened endpoints are never probed. SIMULATION is a
  deterministic sandbox dry-run (definition, read-only execution, anomalous arguments,
  cross-tenant isolation, human-task idempotency) — it never calls an LLM.
- `approve`, `reject`, and `revoke` require a platform admin and a matching
  `approval_version` (optimistic CAS). `approve` additionally requires a green gate report
  stored at the current version; `reject` and `revoke` require a reason.

The CAPABILITY gate and the runtime `HttpApiTransportExecutor` share one
`EndpointScreeningPolicy`: absolute `http(s)` endpoints resolving to public addresses only,
with the operator allowlist `medical.hospital.endpoint-allowlist` (comma-separated hosts)
as the escape hatch for local development such as `127.0.0.1,localhost`. Human-channel
tools are idempotent per submission: the executor derives a `submission_key` from the
conversation, hospital, tool, and arguments, so a retry reports the original task instead
of creating a duplicate.

## Rule engine, E-call escalation, and takeover state machine

`agent-medical` also carries a deterministic medical rule layer. Rules are knowledge data
too: rows in `medical_rules` (kinds `EMERGENCY` and `SAFETY_PATTERN`; scopes GLOBAL,
TENANT, HOSPITAL) move through their own fenced lifecycle in `MedicalRuleLifecycleService`
— `submit` (replacement by `rule_code`), `review` (persists a `RuleValidationReport`),
`approve` / `reject` / `revoke` under an `approval_version` CAS — mirroring the package
lifecycle. The V11 migration seeds one approved GLOBAL emergency baseline rule, so an
empty database already carries the curated emergency disease list, the fixed takeover
scripts, and the SLA ladder.

**Triage signal.** `UnifiedToolCatalog.triageToolDefinition()` advertises `submitTriage`
as a capability-level built-in tool: it belongs to no hospital, has no transport binding,
and is always offered in the normal state alongside the data-driven hospital tools. The
rule engine trusts only this structured signal (a curated `triageCategory` enum plus
symptom→disease pairs); free-text arguments of other tools are never keyword-scanned.

**Deterministic verdicts.** `RuleEngine.triage` loads the visible approved, unexpired
EMERGENCY rules (GLOBAL is always visible; TENANT/HOSPITAL by the caller's tenant),
merges rows sharing a `rule_code` strictest-first (category and disease-name unions,
highest priority, shortest SLA — a lower scope can only tighten the GLOBAL baseline,
never relax or delete it), and decides by exact match against the curated lists
(normalization is trim + lowercase only). A hit creates an E-call human task via
`EscalationService.createEcall` (idempotent per conversation through the partial unique
index `ux_medical_human_tasks_open_ecall`) and returns the rule's fixed
awaiting-takeover script; a miss returns a safe "continue the normal flow" result. Any
engine failure returns a safe fallback script — the engine never fabricates a triage
conclusion.

**Five-state enforcement.** The conversation state is derived from open E-call rows; no
state table exists. NORMAL (no open E-call) exposes the data-driven tools plus
`submitTriage`. While an E-call is PENDING (awaiting takeover) or CLAIMED (taken over),
`HospitalCapabilityProvider.definition()` returns a fixed-script system prompt and ZERO
tools — the hard gate on autonomous action — and `execute()` refuses every invocation as
defense in depth. A guard query or payload failure is treated as restricted
(fail-closed). Completing the task returns the conversation to NORMAL. Claiming is an
atomic CAS restricted to the `MEDICAL_OPS` role or a platform admin; completing an E-call
requires the claim owner (or a platform admin) and a resolution note.

**SLA escalation.** The auto-configured `EscalationPoller` (`Flux.interval`, default 30
s, switchable) calls `EscalationService.tick()`, which escalates overdue PENDING E-calls:
CAS `escalation_level + 1`, recompute the deadline from the rule's `escalationLevels`
ladder, and write an ESCALATED audit event. The level freezes at the top of the ladder
with a single terminal audit entry. `ConversationGuard.state` also runs the same
idempotent routine lazily, so correctness never depends on the poller being enabled or
punctual. A claim stuck in CLAIMED beyond `stale-claim-seconds` is audited and released
back to PENDING (configurable; not a full lease).

**Data-driven safety patterns.** `TextSafetyScreen` is a composite: the code-level
curated baseline (a frozen constant — never removable or relaxable by data) plus the
approved GLOBAL `SAFETY_PATTERN` rules loaded by `SafetyPatternStore`. The six-gate
`SafetyGate` and runtime screening share one instance. A data-layer load failure degrades
screening to baseline-only (fail-safe).

Configuration (`MedicalRulesProperties`, prefix `medical.rules`):

| Property | Default | Purpose |
| --- | --- | --- |
| `escalation-poll-interval` | `30s` | SLA poller period |
| `escalation-poller-enabled` | `true` | Registers and starts the poller bean |
| `stale-claim-seconds` | `1800` | Age of a CLAIMED task before it counts as stuck |
| `stale-claim-release-enabled` | `true` | Releases stuck claims back to PENDING |
