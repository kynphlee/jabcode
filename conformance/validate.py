#!/usr/bin/env python3
"""Validate conformance/vectors.jsonl: every line parses as JSON, has the required
schema, and the canonical create-site vector emits modules 21 / px 252.

Usage: validate.py [path-to-vectors.jsonl]
"""
import json
import sys

REQUIRED_PARAMS = {"colorNumber", "eccLevel", "symbolNumber", "symbolVersions", "moduleSize"}
REQUIRED_TOP = {"id", "params", "payload_b64", "expect"}
REQUIRED_SYMBOL = {"modules_x", "modules_y", "px_x", "px_y"}


def main() -> int:
    path = sys.argv[1] if len(sys.argv) > 1 else "conformance/vectors.jsonl"
    count = 0
    create_site_ok = False
    with open(path, "r", encoding="utf-8") as fh:
        for lineno, line in enumerate(fh, 1):
            line = line.strip()
            if not line:
                continue
            try:
                rec = json.loads(line)
            except json.JSONDecodeError as exc:
                print(f"FAIL line {lineno}: invalid JSON: {exc}")
                return 1

            missing = REQUIRED_TOP - rec.keys()
            if missing:
                print(f"FAIL line {lineno} ({rec.get('id','?')}): missing keys {missing}")
                return 1
            pmiss = REQUIRED_PARAMS - rec["params"].keys()
            if pmiss:
                print(f"FAIL line {lineno} ({rec['id']}): missing params {pmiss}")
                return 1

            exp = rec["expect"]
            if "symbol_count" not in exp or "symbols" not in exp or "roundtrip" not in exp:
                print(f"FAIL line {lineno} ({rec['id']}): malformed expect block")
                return 1
            for sym in exp["symbols"]:
                if REQUIRED_SYMBOL - sym.keys():
                    print(f"FAIL line {lineno} ({rec['id']}): malformed symbol geometry")
                    return 1

            if rec["id"] == "create_site_8c_v1_ms12_ecc0":
                syms = exp["symbols"]
                if (exp["symbol_count"] == 1 and syms
                        and syms[0]["modules_x"] == 21 and syms[0]["modules_y"] == 21
                        and syms[0]["px_x"] == 252 and syms[0]["px_y"] == 252
                        and exp["roundtrip"] is True):
                    create_site_ok = True
                else:
                    print(f"FAIL line {lineno}: create-site geometry mismatch: {syms}")
                    return 1
            count += 1

    if not create_site_ok:
        print("FAIL: create-site vector (create_site_8c_v1_ms12_ecc0) not found")
        return 1

    print(f"OK: {count} vectors parsed; create-site = modules 21 / px 252, roundtrip true")
    return 0


if __name__ == "__main__":
    sys.exit(main())
