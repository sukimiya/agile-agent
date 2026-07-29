# scripts/

## Refreshing the enterprise API docs

`src/main/resources/api-docs/{crm,mes,wms,qms,srm}/*.md` are the files
`ApiCatalog` loads at runtime to build the LLM's interface index and
call whitelist. When the enterprise services' APIs change, refresh them
with:

```bash
export X_ACCESS_TOKEN='real gateway token'
python3 scripts/fetch-swagger.py       # refreshes api-docs/*.json from the live gateway
python3 scripts/convert-api-docs.py    # regenerates src/main/resources/api-docs/*/*.md
mvn -Dtest=ApiCatalogTest test         # confirms the new docs still parse correctly
```

Then rebuild (`mvn package` or restart `mvn spring-boot:run`) — the docs are
bundled into the jar at build time, not loaded at runtime from disk, so a
doc-only change needs a rebuild to take effect.

Covers 5 services: `crm`, `mes`, `wms`, `qms`, `srm`. EAM is not supported
(not wired into `ApiCatalog` or the enterprise-api whitelist).

`patch-descriptions.py` is an optional, separate step — it patches CRM's
converted JSON with descriptions read from the `crm-service` Java source
checked out locally (hardcoded path inside the script). Not required for
the two-command refresh above.

## Files

- `fetch-swagger.py` — fetches `GET {gateway}/{service}/v3/api-docs` for
  each of the 5 services and overwrites `api-docs/{service}.json`.
- `convert-api-docs.py` — converts those JSON snapshots into the flat
  markdown format `ApiCatalog` parses, writing into
  `src/main/resources/api-docs/{service}/`.
- `patch-descriptions.py` — optional CRM-only enrichment step (see above).
