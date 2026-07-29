#!/usr/bin/env python3
"""Read Java @Schema annotations and patch missing descriptions into the
OpenAPI JSON so the converter picks them up."""

import json
import os
import re

BASE = "/Users/breannalinlin/Documents/code/jowo/crm-service/src/main/java/com/jowo/crmservice"
JSON_PATH = "/Users/breannalinlin/Documents/code/jowo/mcp-tool/api-docs/crm-service-api-docs.json"

def find_java_files(root):
    """Walk dirs and collect all .java files."""
    files = []
    for dirpath, _, filenames in os.walk(root):
        for f in filenames:
            if f.endswith(".java"):
                files.append(os.path.join(dirpath, f))
    return files

def extract_class_descriptions(path):
    """Parse a Java file and return (class_name, superclass_name, {field: desc})."""
    with open(path, "r") as fh:
        content = fh.read()

    # class name
    cm = re.search(r'(?:public\s+)?(?:class|interface|@interface)\s+(\w+)', content)
    if not cm:
        return None, None, {}
    cls = cm.group(1)

    # superclass
    sm = re.search(r'extends\s+(\w+)', content)
    supercls = sm.group(1) if sm else None

    # @Schema(description = "...") on class
    # class-level @Schema - used but we need field-level ones

    # field-level @Schema(description = "...")
    # Pattern: @Schema(description = "...") over a field declaration
    field_desc = {}

    # Remove block comments so we don't match stuff inside /* */
    stripped = re.sub(r'/\*.*?\*/', '', content, flags=re.DOTALL)

    # Find @Schema(...) followed optionally by other annotations and then a field
    # This is tricky because annotations can be multiline and there can be multiple annotations

    # Find individual @Schema blocks
    schema_blocks = list(re.finditer(
        r'@Schema\s*\(((?:[^()]|\([^()]*\))*)\)',
        stripped, re.DOTALL
    ))

    for m in schema_blocks:
        ann_body = m.group(1)
        # Check if this schema has a description attribute
        dm = re.search(r'description\s*=\s*"((?:\\.|[^"\\])*)"', ann_body)
        if not dm:
            continue
        desc = dm.group(1)

        # Now find the field name that follows this @Schema annotation
        # After the @Schema(...), there can be more annotations, then the field decl
        after_schema = stripped[m.end():]
        # Skip other @annotations and whitespace
        # Then find a line like "private Type fieldName;" or "private Type fieldName"
        fm = re.search(
            r'(?:@\w+(?:\([^)]*\))?\s*\n?\s*)*'
            r'(?:public|private|protected)\s+\S+\s+(\w+)'
            r'(?:\s*[=;])',
            after_schema[:500]  # look at most 500 chars after
        )
        if fm:
            field_desc[fm.group(1)] = desc

    return cls, supercls, field_desc

def resolve_inheritance(class_map):
    """Walk superclass chains so child schemas include parent descriptions."""
    # class_map: {name: (superclass, {field: desc})}
    resolved = {}
    def resolve(name, seen=None):
        if seen is None:
            seen = set()
        if name in seen:
            return {}
        seen.add(name)
        if name in resolved:
            return resolved[name]
        supercls, my_fields = class_map.get(name, (None, {}))
        parent_fields = resolve(supercls, seen) if supercls else {}
        merged = {**parent_fields, **my_fields}
        resolved[name] = merged
        return merged

    for name in class_map:
        resolve(name)
    return resolved

def main():
    files = find_java_files(BASE)
    print(f"Found {len(files)} Java files")

    class_map = {}
    for fpath in files:
        cls, supercls, fields = extract_class_descriptions(fpath)
        if cls:
            class_map[cls] = (supercls, fields)

    print(f"Extracted {len(class_map)} classes with @Schema descriptions")

    # Resolve inheritance
    merged = resolve_inheritance(class_map)

    # Load OpenAPI JSON
    with open(JSON_PATH, "r") as fh:
        api = json.load(fh)

    schemas = api.get("components", {}).get("schemas", {})
    patched_count = 0

    for schema_name, schema in schemas.items():
        props = schema.get("properties", {})
        if not props:
            continue

        java_descs = merged.get(schema_name, {})
        if not java_descs:
            # Try parent class names: the Java class might have a different name
            # The OpenAPI schema name usually matches the Java class name
            continue

        for prop_name, prop_schema in props.items():
            if prop_schema.get("description"):
                continue  # already has description
            if prop_name in java_descs:
                prop_schema["description"] = java_descs[prop_name]
                patched_count += 1

    # Save
    with open(JSON_PATH, "w") as fh:
        json.dump(api, fh, ensure_ascii=False)
    print(f"Patched {patched_count} descriptions into {JSON_PATH}")

if __name__ == "__main__":
    main()
