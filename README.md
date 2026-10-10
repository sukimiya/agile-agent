# Agile Agent

Agile Agent is a Maven multi-module project for a general-purpose agent server with a stable capability SPI.

- `agent-spi` contains the public identity and capability contracts used by server-side adapters.
- `agent-server` contains HTTP APIs, authentication, orchestration, model integration, and the generic `KNOWLEDGE` capability.
- `mock-oauth2` is a standalone development identity service and is packaged as a plain JAR in Phase 1.

External adapters are regular Spring Boot dependencies built against `agent-spi`; see the [capability adapter guide](docs/capability-adapters.md). The project boundaries and migration decisions are recorded in the [open-source migration specification](docs/spec/2026-10-09-open-source-multimodule-migration.md).
