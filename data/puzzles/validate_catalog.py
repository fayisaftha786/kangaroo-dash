#!/usr/bin/env python3
"""Validate puzzle catalog metadata only; this does not run a key search.

Usage: python3 data/puzzles/validate_catalog.py path/to/catalog.json
Input must be a JSON array. Passing checks does not prove authenticity or
cryptographic correspondence between a public key and an address.
"""
import json
import re
import sys
from pathlib import Path
from urllib.parse import urlparse

STATUSES = {"unverified", "format_checked", "key_address_checked", "source_cross_checked"}
HEX_RE = re.compile(r"^(?:0x)?[0-9a-fA-F]+$")
PUBKEY_RE = re.compile(r"^(?:02|03)[0-9a-fA-F]{64}$|^04[0-9a-fA-F]{128}$")


def validate(records):
    errors = []
    if not isinstance(records, list):
        return ["catalog root must be a JSON array"]
    seen = set()
    for i, rec in enumerate(records):
        where = f"record[{i}]"
        if not isinstance(rec, dict):
            errors.append(f"{where}: must be a JSON object")
            continue
        pid = rec.get("id")
        if not isinstance(pid, int) or isinstance(pid, bool) or pid < 1:
            errors.append(f"{where}: id must be a positive integer")
        elif pid in seen:
            errors.append(f"{where}: duplicate id {pid}")
        else:
            seen.add(pid)
        if rec.get("verification_status") not in STATUSES:
            errors.append(f"{where}: invalid or missing verification_status")
        source = rec.get("source_url")
        parsed = urlparse(source) if isinstance(source, str) else None
        if not parsed or parsed.scheme != "https" or not parsed.netloc:
            errors.append(f"{where}: source_url must be an https URL")
        for field in ("range_start", "range_end"):
            val = rec.get(field)
            if val is not None and (not isinstance(val, str) or not HEX_RE.fullmatch(val)):
                errors.append(f"{where}: {field} must be a hex string or null")
        start, end = rec.get("range_start"), rec.get("range_end")
        if isinstance(start, str) and isinstance(end, str) and HEX_RE.fullmatch(start) and HEX_RE.fullmatch(end):
            if int(start.removeprefix("0x"), 16) >= int(end.removeprefix("0x"), 16):
                errors.append(f"{where}: range_start must be less than range_end")
        bits = rec.get("bit_length")
        if bits is not None and (not isinstance(bits, int) or isinstance(bits, bool) or bits < 1):
            errors.append(f"{where}: bit_length must be a positive integer or null")
        key = rec.get("public_key")
        if key is not None and (not isinstance(key, str) or not PUBKEY_RE.fullmatch(key)):
            errors.append(f"{where}: public_key must be a valid-length SEC hex key or null")
        address = rec.get("address")
        if address is not None and (not isinstance(address, str) or not address.strip()):
            errors.append(f"{where}: address must be a non-empty string or null")
        inclusive = rec.get("range_end_inclusive")
        if inclusive is not None and not isinstance(inclusive, bool):
            errors.append(f"{where}: range_end_inclusive must be boolean or null")
    return errors


def main(argv):
    if len(argv) != 2:
        print(__doc__.strip(), file=sys.stderr)
        return 2
    try:
        records = json.loads(Path(argv[1]).read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        print(f"ERROR: cannot read valid JSON: {exc}", file=sys.stderr)
        return 2
    errors = validate(records)
    print(f"Records checked: {len(records) if isinstance(records, list) else 0}")
    if errors:
        print(f"Validation errors: {len(errors)}")
        for error in errors:
            print(f"- {error}")
        print("FAIL: metadata/schema checks did not pass")
        return 1
    print("PASS: formatting checks passed; authenticity is not established")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
