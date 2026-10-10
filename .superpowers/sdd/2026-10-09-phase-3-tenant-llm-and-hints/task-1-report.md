STATUS: complete

## Summary

- Added Flyway V3 `chat_hints`, V4 `llm_provider_preset`, and V5 `tenant_llm_config`; retained V1/V2 and configured history-table naming unchanged.
- Added reactive repositories for hint CRUD with `(created_at, id)` order, provider-preset CRUD with nullable `balance_path`, and tenant-keyed config find/upsert/delete. Tenant ownership is keyed only by `tenant_id`; preset references have no foreign key.
- Added isolated H2 MySQL-mode repository/migration tests. Migration coverage upgrades an explicit V2 target with seeded V2 records and migrates a fresh database through V5. Updated the end-to-end migration count for the three new migrations.

## RED

Command: `mvn -o -pl agent-server -am -Dtest='LlmProviderPresetRepositoryTest,TenantLlmConfigRepositoryTest,HintRepositoryTest,LlmMigrationTest' test`

Result: failed during test compilation because the three repository classes referenced by the tests did not exist. The initial exact command could not resolve the local `agile-agent-mock-oauth2` snapshot; offline reactor mode (`-am`) made the same focused test selection runnable without network access.

## GREEN

- Focused: `mvn -o -pl agent-server -am -Dtest='LlmProviderPresetRepositoryTest,TenantLlmConfigRepositoryTest,HintRepositoryTest,LlmMigrationTest' test` — 7 tests, 0 failures, 0 errors.
- Full server reactor: `mvn -o -pl agent-server -am test` — 244 tests, 0 failures, 0 errors.
- `git diff --check` — clean.

## Commit

`feat: add tenant LLM and hint persistence` (local commit; no push).

## Concerns

- Migration and repository behavior were verified on H2 2.3 in MySQL mode; a live MySQL 8.4 server was not available in this test run.

## Fix Round 1

- RED: `mvn -o -pl agent-server -am -Dtest=TenantLlmConfigRepositoryTest test` — 4 tests, 1 failure. Reusing tenant A's row ID for tenant B did not throw because the old `ON DUPLICATE KEY UPDATE` updated tenant A's row.
- GREEN: `mvn -o -pl agent-server -am -Dtest=TenantLlmConfigRepositoryTest test` — 4 tests, 0 failures, 0 errors. The collision now fails without changing tenant A or creating tenant B; concurrent same-tenant upserts leave one row with a coherent preset/key pair.
- Full Task 1 focus: `mvn -o -pl agent-server -am -Dtest='LlmProviderPresetRepositoryTest,TenantLlmConfigRepositoryTest,HintRepositoryTest,LlmMigrationTest' test` — 9 tests, 0 failures, 0 errors.
- Fix committed locally as `fix: scope tenant LLM config upserts by tenant`.
