# API Docs Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn "the enterprise services' APIs changed, how does `mcp-tool` refresh its bundled interface docs" into two commands instead of a manual swagger export + manual file copy.

**Architecture:** A new `scripts/fetch-swagger.py` live-fetches each service's OpenAPI JSON from the shared gateway (reusing the fetch approach from the OpenClaw skill's `_generate.py`) and overwrites the existing local snapshots in `api-docs/*.json`. The existing `scripts/convert-api-docs.py` (whose field-parsing and example-question-generation logic is unchanged and already produces exactly today's bundled docs, verified byte-for-byte against the current JSON snapshots) gets its output path fixed to write directly into `src/main/resources/api-docs/{service}/` — the location `ApiCatalog` actually loads from — instead of the currently-unused `docs/` copy that needed a manual copy step.

**Tech Stack:** Python 3 (stdlib only: `json`, `os`, `sys`, `urllib.request`, `pathlib` — no new dependencies), matching the existing `scripts/*.py` style.

## Global Constraints

- No new Python dependencies — stdlib only, matching `scripts/convert-api-docs.py` and `scripts/patch-descriptions.py`'s existing style.
- Gateway is a single hardcoded constant: `GATEWAY = "http://192.168.3.234:8888"` (matches `_generate.py`'s value) — one place to change if it ever moves.
- Auth token read from environment variable `X_ACCESS_TOKEN` (same name the OpenClaw skill documentation already tells users to export) — required; the script must exit before making any network request if it's unset.
- Five services only: `crm`, `mes`, `wms`, `qms`, `srm`. EAM is explicitly out of scope (not covered by `ApiCatalog`, `ChatController.listServices()`, or the whitelist today).
- `scripts/fetch-swagger.py` does no local caching — every run must hit the live gateway (the entire point is "get the latest").
- A single service's fetch failure must not abort the others — collect results, print a success/failure summary, exit non-zero only if at least one failed.
- `scripts/convert-api-docs.py`'s conversion logic (`convert()`, `enrich_description()`, `generate_examples()`, `resolve_properties()`, etc.) must not change — only `main()`'s output path and the hardcoded `base_dir`.
- `scripts/patch-descriptions.py` is not touched and not wired into this flow — it remains an optional manual step.
- Do not delete the stale `docs/{service}-api-docs.md` copies — out of scope for this plan.

---

## File Map

| File | Change |
|---|---|
| `scripts/fetch-swagger.py` | **new** — live-fetches swagger JSON for all 5 services |
| `scripts/convert-api-docs.py` | modify `main()` — output path + `base_dir` |
| `scripts/README.md` | **new** — documents the two-command refresh workflow |

---

### Task 1: `scripts/fetch-swagger.py` — live swagger fetch

**Files:**
- Create: `scripts/fetch-swagger.py`

**Interfaces:**
- Produces: a standalone script, run as `python3 scripts/fetch-swagger.py`. Reads `X_ACCESS_TOKEN` from the environment. Writes/overwrites `api-docs/{crm-service,mes,wms-service,qms-service,srm-service}-api-docs.json` (exact filenames below). Exits 0 if all 5 services succeeded, exits 1 (with a summary printed to stderr) if any failed. Exits before any network call (with a message on stderr) if `X_ACCESS_TOKEN` is unset.

- [ ] **Step 1: Write the script**

Create `scripts/fetch-swagger.py`:

```python
#!/usr/bin/env python3
"""Fetch the latest swagger (OpenAPI) JSON for each enterprise service from the
shared gateway and overwrite the local snapshots that convert-api-docs.py reads.

Run this before convert-api-docs.py whenever the enterprise services' APIs change.

Usage:
    export X_ACCESS_TOKEN='...'
    python3 scripts/fetch-swagger.py
"""
import json
import os
import sys
import urllib.request
from pathlib import Path

GATEWAY = "http://192.168.3.234:8888"
OUT_DIR = Path(__file__).resolve().parent.parent / "api-docs"

# code -> (gateway path prefix, output filename)
SERVICES = {
    "crm": ("crm-service", "crm-service-api-docs.json"),
    "mes": ("mes-service", "mes-api-docs.json"),
    "wms": ("wms-service", "wms-service-api-docs.json"),
    "qms": ("qms-service", "qms-service-api-docs.json"),
    "srm": ("srm-service", "srm-service-api-docs.json"),
}


def fetch_swagger(svc_path):
    token = os.environ["X_ACCESS_TOKEN"]
    url = f"{GATEWAY}/{svc_path}/v3/api-docs"
    req = urllib.request.Request(url, headers={"x-access-token": token})
    with urllib.request.urlopen(req, timeout=15) as r:
        return r.read()


def main():
    if not os.environ.get("X_ACCESS_TOKEN"):
        sys.exit("X_ACCESS_TOKEN env var is required to fetch swagger")

    results = {}
    for code, (svc_path, filename) in SERVICES.items():
        try:
            data = fetch_swagger(svc_path)
            json.loads(data)  # validate it's real JSON before overwriting the snapshot
            out_path = OUT_DIR / filename
            out_path.write_bytes(data)
            results[code] = "OK"
            print(f"[{code}] wrote {out_path}", file=sys.stderr)
        except Exception as e:
            results[code] = f"FAILED: {e}"
            print(f"[{code}] FAILED: {e}", file=sys.stderr)

    print("\n=== Summary ===", file=sys.stderr)
    for code, status in results.items():
        print(f"{code}: {status}", file=sys.stderr)

    if any(status != "OK" for status in results.values()):
        sys.exit(1)


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: Verify the script's syntax is valid**

Run: `python3 -m py_compile scripts/fetch-swagger.py`
Expected: no output, exit code 0.

- [ ] **Step 3: Verify the missing-token fast-fail path**

This is the one behavior verifiable without live network access or real credentials — the token check happens before any request is attempted.

Run:
```bash
cd /Users/breannalinlin/Documents/code/jowo/mcp-tool
env -u X_ACCESS_TOKEN python3 scripts/fetch-swagger.py; echo "exit code: $?"
```
Expected output:
```
X_ACCESS_TOKEN env var is required to fetch swagger
exit code: 1
```
No `[crm]`/`[mes]`/etc. lines should appear — confirming no network request was attempted before the check.

- [ ] **Step 4: Confirm no files were modified by the failed run**

Run: `git status --short api-docs/`
Expected: no output (the `api-docs/*.json` files are untouched, since Step 3 exited before writing anything).

- [ ] **Step 5: Commit**

```bash
git add scripts/fetch-swagger.py
git commit -m "feat(scripts): add fetch-swagger.py to refresh api-docs JSON from the live gateway"
```

---

### Task 2: Fix `scripts/convert-api-docs.py`'s output path

**Files:**
- Modify: `scripts/convert-api-docs.py:1-4` (imports)
- Modify: `scripts/convert-api-docs.py:500-511` (`main()`)

**Interfaces:**
- Consumes: `api-docs/{crm-service,mes,wms-service,qms-service,srm-service}-api-docs.json` (same files Task 1 refreshes; already present in the repo today, so this task's own verification doesn't depend on Task 1 or on any live network access).
- Produces: running `python3 scripts/convert-api-docs.py` now writes into `src/main/resources/api-docs/{crm,mes,wms,qms,srm}/{same filenames as before}` instead of `docs/`. The `convert()` function's signature and behavior are unchanged.

- [ ] **Step 1: Add the `pathlib` import**

In `scripts/convert-api-docs.py`, the file currently starts:

```python
#!/usr/bin/env python3
"""Convert OpenAPI 3.1 JSON to markdown RAG-friendly API docs."""

import json
```

Change it to:

```python
#!/usr/bin/env python3
"""Convert OpenAPI 3.1 JSON to markdown RAG-friendly API docs."""

import json
from pathlib import Path
```

- [ ] **Step 2: Replace `main()`**

The current `main()` (lines 500-511) reads:

```python
def main():
    base_dir = '/Users/breannalinlin/Documents/code/jowo/mcp-tool'
    services = [
        ('crm-service-api-docs.json', 'crm-service-api-docs.md'),
        ('mes-api-docs.json', 'mes-api-docs.md'),
        ('wms-service-api-docs.json', 'wms-service-api-docs.md'),
        ('qms-service-api-docs.json', 'qms-service-api-docs.md'),
        ('srm-service-api-docs.json', 'srm-service-api-docs.md'),
    ]
    for json_file, md_file in services:
        convert(f'{base_dir}/api-docs/{json_file}', f'{base_dir}/docs/{md_file}')
```

Replace it with:

```python
def main():
    base_dir = Path(__file__).resolve().parent.parent
    services = [
        ('crm', 'crm-service-api-docs.json', 'crm-service-api-docs.md'),
        ('mes', 'mes-api-docs.json', 'mes-api-docs.md'),
        ('wms', 'wms-service-api-docs.json', 'wms-service-api-docs.md'),
        ('qms', 'qms-service-api-docs.json', 'qms-service-api-docs.md'),
        ('srm', 'srm-service-api-docs.json', 'srm-service-api-docs.md'),
    ]
    for service_dir, json_file, md_file in services:
        input_path = base_dir / 'api-docs' / json_file
        output_path = base_dir / 'src' / 'main' / 'resources' / 'api-docs' / service_dir / md_file
        convert(str(input_path), str(output_path))
```

- [ ] **Step 3: Verify the script's syntax is valid**

Run: `python3 -m py_compile scripts/convert-api-docs.py`
Expected: no output, exit code 0.

- [ ] **Step 4: Verify the regenerated docs are byte-identical to what's currently bundled**

The conversion logic is unchanged and the input JSON files are already present and unmodified — this run should reproduce today's bundled docs exactly (independently confirmed during planning: regenerating all 5 services from the current `api-docs/*.json` via the unmodified `convert()` function produces output that `diff` reports as identical to each corresponding file under `src/main/resources/api-docs/`).

Run:
```bash
cd /Users/breannalinlin/Documents/code/jowo/mcp-tool
git stash push -- src/main/resources/api-docs/
python3 scripts/convert-api-docs.py
git diff --stat src/main/resources/api-docs/
```
Expected: `git diff --stat` prints nothing (no changes) — the freshly regenerated files match what was already committed byte-for-byte. If it reports differences, `git diff src/main/resources/api-docs/` to see exactly what changed and STOP — do not proceed until you understand why (do not assume the diff is fine).

- [ ] **Step 5: Restore the stash and confirm no `docs/` files were touched**

```bash
git stash pop
git status --short docs/
```
Expected: `git status --short docs/` prints nothing — confirms `main()` no longer writes into `docs/` at all.

- [ ] **Step 6: Commit**

```bash
git add scripts/convert-api-docs.py
git commit -m "fix(scripts): write converted api-docs directly into src/main/resources/api-docs/"
```

---

### Task 3: `scripts/README.md` — document the refresh workflow

**Files:**
- Create: `scripts/README.md`

**Interfaces:**
- Consumes: nothing (documentation only, describes the scripts from Tasks 1 and 2).
- Produces: a markdown file a future reader opens to remember the refresh steps.

- [ ] **Step 1: Write the README**

Create `scripts/README.md`:

```markdown
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
```

- [ ] **Step 2: Commit**

```bash
git add scripts/README.md
git commit -m "docs(scripts): document the api-docs refresh workflow"
```

---

## Self-Review Notes

- **Spec coverage:** fetch script (Task 1) ✓, convert-api-docs.py output-path fix (Task 2) ✓, README (Task 3) ✓, EAM exclusion stated as a constraint ✓, `patch-descriptions.py` left untouched and documented as optional ✓, no deletion of stale `docs/` copies ✓, `mvn -Dtest=ApiCatalogTest test` called out as the regression check ✓.
- **No live-network/credential dependency in any task's own verification steps** — Task 1 verifies only the pre-network fast-fail path; Task 2 verifies against JSON files already committed in the repo. Actually exercising `fetch-swagger.py` against the real gateway requires the user's own `X_ACCESS_TOKEN` and is not part of either task's automated verification, matching this plan's Global Constraints (no new test infra for network I/O, matching `_generate.py`'s existing precedent).
