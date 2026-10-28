#!/usr/bin/env python3
"""Validate puzzle catalog metadata only; this does not run a key search.

Usage: python3 data/puzzles/validate_catalog.py path/to/catalog.json
Passing checks does not prove source authenticity, public-key/address correspondence,
live-chain status, or native-engine correctness.
"""
import json
import re
import sys
from pathlib import Path
from urllib.parse import urlparse

STATUSES = {"unverified", "format_checked", "key_address_checked", "source_cross_checked"}
HEX_RE = re.compile(r"^(?:0[xX])?[0-9a-fA-F]+$")
PUBKEY_RE = re.compile(r"^(?:02|03)[0-9a-fA-F]{64}$|^04[0-9a-fA-F]{128}$")


def parse_hex(value):
    """Parse a hexadecimal string after strict syntax validation."""
    if not isinstance(value, str) or not HEX_RE.fullmatch(value):
        raise ValueError("not a valid hexadecimal string")
    digits = value[2:] if value[:2].lower() == "0x" else value
    return int(digits, 16)


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
        if not parsed or parsed.scheme.lower() != "https" or not parsed.netloc:
            errors.append(f"{where}: source_url must be an https URL")

        bits = rec.get("bit_length")
        if bits is not None and (
            not isinstance(bits, int) or isinstance(bits, bool) or bits < 1
        ):
            errors.append(f"{where}: bit_length must be a positive integer or null")
            bits = None

        parsed_bounds = {}
        for field in ("range_start", "range_end"):
            value = rec.get(field)
            if value is None:
                continue
            try:
                parsed_bounds[field] = parse_hex(value)
            except ValueError:
                errors.append(f"{where}: {field} must be valid hexadecimal or null")

        start_present = rec.get("range_start") is not None
        end_present = rec.get("range_end") is not None
        inclusive = rec.get("range_end_inclusive")

        if start_present != end_present:
            errors.append(
                f"{where}: range_start and range_end must both be supplied or both be null"
            )
        elif start_present and end_present:
            if not isinstance(inclusive, bool):
                errors.append(
                    f"{where}: range_end_inclusive must be true or false when bounds are present"
                )
            elif len(parsed_bounds) == 2:
                start = parsed_bounds["range_start"]
                end = parsed_bounds["range_end"]

                if inclusive:
                    if start > end:
                        errors.append(
                            f"{where}: inclusive range_start must be less than or equal to range_end"
                        )
                elif start >= end:
                    errors.append(
                        f"{where}: exclusive range_start must be less than range_end"
                    )

                # bit_length describes the scalar interval [2^(bits-1), 2^bits).
                # Inclusive ends must be below the upper bound; exclusive ends may equal it.
                if bits is not None:
                    lower = 1 << (bits - 1)
                    upper_exclusive = 1 << bits
                    if start < lower:
                        errors.append(
                            f"{where}: range_start is below the declared bit_length interval"
                        )
                    if (end >= upper_exclusive) if inclusive else (end > upper_exclusive):
                        errors.append(
                            f"{where}: range_end exceeds the declared bit_length interval"
                        )
        elif inclusive is not None:
            errors.append(
                f"{where}: range_end_inclusive must be null when range bounds are unavailable"
            )

        key = rec.get("public_key")
        if key is not None and (
            not isinstance(key, str) or not PUBKEY_RE.fullmatch(key)
        ):
            errors.append(
                f"{where}: public_key must be a valid-length SEC hex key or null"
            )

        address = rec.get("address")
        if address is not None and (
            not isinstance(address, str) or not address.strip()
        ):
            errors.append(f"{where}: address must be a non-empty string or null")

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

    print("PASS: metadata and declared-range checks passed; authenticity is not established")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
