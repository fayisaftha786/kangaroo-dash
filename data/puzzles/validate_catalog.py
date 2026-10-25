#!/usr/bin/env python3
"""Validate puzzle catalog metadata without running any key-search operation.

Usage:
  python3 data/puzzles/validate_catalog.py path/to/catalog.json

The input must be a JSON array of records. This checks schema and formatting only;
it does not prove source authenticity or cryptographic key/address correspondence.
"""
import json
import re
import sys
from pathlib import Path
from urllib.parse import urlparse

STATUSES = {
    "unverified",
    "format_checked",
    "key_address_checked",
    "source_cross_checked",
}
HEX_RE = re.compile(r"^(?:0x)?[0-9a-fA-F]+$")
COMPRESSED_PUBKEY_RE = re.compile(r"^(?:02|03)[0-9a-fA-F]{64}$")
UNCOMPRESSED_PUBKEY_RE = re.compile(r"^04[0-9a-fA-F]{128}$")


def validate(records):
    errors = []
    if not isinstance(records, list):
        return ["catalog root must be a JSON array"]

    seen_ids = set()
    for index, record in enumerate(records):
        where = f"record[{index}]"
        if not isinstance(record, dict):
            errors.append(f"{where}: must be a JSON object")
            continue

        puzzle_id = record.get("id")
        if not isinstance(puzzle_id, int) or isinstance(puzzle_id, bool) or puzzle_id < 1:
            errors.append(f"{where}: id must be a positive integer")
        elif puzzle_id in seen_ids:
            errors.append(f"{where}: duplicate id {puzzle_id}")
        else:
            seen_ids.add(puzzle_id)

        status = record.get("verification_status")
        if status not in STATUSES:
            errors.append(f"{where}: invalid or missing verification_status")

        source = record.get("source_url")
        parsed = urlparse(source) if isinstance(source, str) else None
        if not parsed or parsed.scheme != "https" or not parsed.netloc:
            errors.append(f"{where}: source_url must be an https URL")

        for field in ("range_start", "range_end"):
            value = record.get(field)
            if value is not None and (
                not isinstance(value, str) or not HEX_RE.fullmatch(value)
            ):
                errors.append(f"{where}: {field} must be a hex string or null")

        start, end = record.get("range_start"), record.get("range_end")
        if start is not None and end is not None:
            try:
                if int(start.removeprefix("0x"), 16) >= int(end.removeprefix("0x"), 16):
                    errors.append(f"{where}: range_start must be less than range_end")
            except (AttributeError, ValueError):
                pass  # Individual field errors above explain malformed values.

        bits = record.get("bit_length")
        if bits is not None and (
            not isinstance(bits, int) or isinstance(bits, bool) or bits < 1
        ):
            errors.append(f"{where}: bit_length must be a positive integer or null")

        pubkey = record.get("public_key")
        if pubkey is not None and (
            not isinstance(pubkey, str)
            or not (COMPRESSED_PUBKEY_RE.fullmatch(pubkey)
                    or UNCOMPRESSED_PUBKEY_RE.fullmatch(pubkey))
        ):
            errors.append(f"{where}: public_key must be a valid-length SEC hex key or null")

        address = record.get("address")
        if address is not None and (
            not isinstance(address, str) or not address.strip()
        ):
            errors.append(f"{where}: address must be a non-empty string or null")

        inclusive = record.get("range_end_inclusive")
        if inclusive is not None and not isinstance(inclusive, bool):
            errors.append(f"{where}: range_end_inclusive must be boolean or null")

    return errors


def main(argv):
    if len(argv) != 2:
        print(__doc__.strip(), file=sys.stderr)
        return 2
    path = Path(argv[1])
    try:
        records = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        print(f"ERROR: cannot read valid JSON from {path}: {exc}", file=sys.stderr)
        return 2

    errors = validate(records)
    print(f"Records checked: {len(records) if isinstance(records, list) else 0}")
    if errors:
        print(f"Validation errors: {len(errors)}")
        for error in errors:
            print(f"- {error}")
        print("FAIL: metadata/schema checks did not pass")
        return 1
    print("PASS: schema and formatting checks passed (not proof of authenticity)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
