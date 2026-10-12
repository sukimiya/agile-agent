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
