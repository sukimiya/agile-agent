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
    "mdm": ("mdm-service", "mdm-service-api-docs.json"),
    "eam": ("eam-service", "eam-service-api-docs.json"),
    "ems": ("ems-service", "ems-service-api-docs.json"),
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
