#!/usr/bin/env python3
"""Audit puzzle-catalog structure and range arithmetic only.

This does not prove source authenticity, address/public-key correspondence,
live blockchain status, or native-engine argument semantics.
Usage: python3 data-check/validate_catalog.py [catalog.json]
"""
import json
import re
import sys
from pathlib import Path
from urllib.parse import urlparse

PUBKEY_RE = re.compile(r"^(?:02|03)[0-9a-fA-F]{64}$|^04[0-9a-fA-F]{128}$")
HASH160_RE = re.compile(r"^[0-9a-fA-F]{40}$")
DEFAULT_CATALOG = Path("data-check/puzzles.json")


def validate(data):
    errors = []
    warnings = []
    ids = []
    if not isinstance(data, list):
        return ["catalog root must be a JSON array"], warnings

    for i, record in enumerate(data):
        where = f"record[{i}]"
        if not isinstance(record, dict):
            errors.append(f"{where}: not an object")
            continue

        pid = record.get("id")
        if not isinstance(pid, int) or isinstance(pid, bool) or not 1 <= pid <= 160:
            errors.append(f"{where}: invalid id {pid!r}")
            continue
        ids.append(pid)

        bits = record.get("bits")
        if bits != pid or isinstance(bits, bool):
            errors.append(f"#{pid}: bits must equal id ({pid})")

        start = record.get("range_start_hex")
        end = record.get("range_end_hex")
        size = record.get("range_size_decimal")
        if not isinstance(start, str) or not isinstance(end, str):
            errors.append(f"#{pid}: missing range hex")
            continue
        if not re.fullmatch(r"(?:0x)?[0-9a-fA-F]+", start):
            errors.append(f"#{pid}: malformed range_start_hex")
            continue
        if not re.fullmatch(r"(?:0x)?[0-9a-fA-F]+", end):
            errors.append(f"#{pid}: malformed range_end_hex")
            continue

        try:
            lo, hi = int(start, 16), int(end, 16)
            expected_lo = 1 << (pid - 1)
            expected_hi = (1 << pid) - 1
            expected_size = expected_hi - expected_lo + 1
            if lo != expected_lo:
                errors.append(f"#{pid}: incorrect range start")
            if hi != expected_hi:
                errors.append(f"#{pid}: incorrect range end")
            if size != str(expected_size):
                errors.append(f"#{pid}: incorrect inclusive range size")
            search_bits = record.get("search_bits")
            if (not isinstance(search_bits, int) or isinstance(search_bits, bool)
                    or search_bits != pid - 1):
                errors.append(f"#{pid}: search_bits must be integer id - 1")
        except (ValueError, TypeError):
            errors.append(f"#{pid}: malformed range values")

        address = record.get("address")
        if not isinstance(address, str) or not address.strip():
            errors.append(f"#{pid}: missing address")

        pub = record.get("public_key")
        has_pub = isinstance(pub, str) and bool(pub)
        if pub is not None and (not isinstance(pub, str) or not PUBKEY_RE.fullmatch(pub)):
            errors.append(f"#{pid}: public_key is not valid-length SEC hex")
        if record.get("pubkey_known") != has_pub:
            errors.append(f"#{pid}: public_key/pubkey_known mismatch")

        hash160 = record.get("hash160")
        if hash160 is not None and (not isinstance(hash160, str) or not HASH160_RE.fullmatch(hash160)):
            errors.append(f"#{pid}: hash160 must be 40 hexadecimal characters or null")

        status = record.get("status")
        if status not in ("solved", "unsolved"):
            errors.append(f"#{pid}: status must be solved or unsolved")
        private_public = record.get("private_key_already_public")
        unsafe = record.get("unsafe_for_wallets")
        if not isinstance(private_public, bool):
            errors.append(f"#{pid}: private_key_already_public must be boolean")
        if not isinstance(unsafe, bool):
            errors.append(f"#{pid}: unsafe_for_wallets must be boolean")
        if status == "solved":
            if private_public is not True:
                errors.append(f"#{pid}: solved record must be flagged private_key_already_public")
            if unsafe is not True:
                errors.append(f"#{pid}: solved record must be flagged unsafe_for_wallets")
        elif status == "unsolved":
            if private_public is True:
                errors.append(f"#{pid}: unsolved record cannot be flagged private_key_already_public")
            if unsafe is True:
                errors.append(f"#{pid}: unsolved record cannot be flagged unsafe_for_wallets")

        source_url = record.get("source_url")
        parsed_source = urlparse(source_url) if isinstance(source_url, str) else None
        valid_record_source = bool(
            parsed_source and parsed_source.scheme.lower() == "https" and parsed_source.netloc
        )
        source_scope = record.get("source_scope")
        if source_scope not in {"record", "missing"}:
            errors.append(f"#{pid}: source_scope must be 'record' or 'missing'")
        elif valid_record_source and source_scope != "record":
            errors.append(f"#{pid}: source_url is present but source_scope is not 'record'")
        elif not valid_record_source and source_scope == "record":
            errors.append(f"#{pid}: source_scope='record' requires a valid HTTPS source_url")

        if not valid_record_source:
            warnings.append(f"#{pid}: source_url missing or not HTTPS; record-level provenance unverified")
        else:
            source_label = record.get("source_label")
            if not isinstance(source_label, str) or not source_label.strip():
                warnings.append(f"#{pid}: source_url has no evidence label; claim scope is unclear")

        dataset_url = record.get("dataset_source_url")
        parsed_dataset = urlparse(dataset_url) if isinstance(dataset_url, str) else None
        if not parsed_dataset or parsed_dataset.scheme.lower() != "https" or not parsed_dataset.netloc:
            warnings.append(f"#{pid}: dataset_source_url missing or not HTTPS; secondary cross-reference unavailable")
        snapshot = record.get("dataset_source_snapshot")
        if not isinstance(snapshot, str) or not re.fullmatch(r"\d{4}-\d{2}-\d{2}", snapshot):
            errors.append(f"#{pid}: dataset_source_snapshot must be an ISO date YYYY-MM-DD")

        txid = record.get("solve_txid")
        tx_url = record.get("chain_tx_url")
        tx_mirror = record.get("chain_tx_url_secondary")
        if txid is not None:
            if not isinstance(txid, str) or not re.fullmatch(r"[0-9a-fA-F]{64}", txid):
                errors.append(f"#{pid}: solve_txid must be 64 hexadecimal characters or null")
            else:
                expected_tx_url = f"https://mempool.space/tx/{txid}"
                expected_mirror_url = f"https://blockstream.info/tx/{txid}"
                if tx_url != expected_tx_url:
                    errors.append(f"#{pid}: chain_tx_url must reference its recorded mempool.space transaction")
                if tx_mirror != expected_mirror_url:
                    errors.append(f"#{pid}: chain_tx_url_secondary must reference its recorded blockstream.info transaction")
        elif tx_url is not None or tx_mirror is not None:
            errors.append(f"#{pid}: transaction explorer URLs require a solve_txid")

    duplicates = sorted({pid for pid in ids if ids.count(pid) > 1})
    if duplicates:
        errors.append(f"duplicate IDs: {duplicates}")

    expected_ids = set(range(1, 161))
    actual_ids = set(ids)
    missing, extra = sorted(expected_ids - actual_ids), sorted(actual_ids - expected_ids)
    if missing:
        errors.append(f"missing IDs: {missing}")
    if extra:
        errors.append(f"unexpected IDs: {extra}")
    if len(data) != 160:
        errors.append(f"expected 160 records, got {len(data)}")

    return errors, warnings


def main(argv):
    if len(argv) > 2:
        print(__doc__.strip(), file=sys.stderr)
        return 2
    path = Path(argv[1]) if len(argv) == 2 else DEFAULT_CATALOG
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        print(f"FAIL: cannot read valid JSON from {path}: {exc}", file=sys.stderr)
        return 2

    errors, warnings = validate(data)
    print(f"Catalog: {path}")
    print(f"Records: {len(data) if isinstance(data, list) else 0}")
    print(f"Errors: {len(errors)}")
    print(f"Provenance warnings: {len(warnings)}")
    for issue in errors[:100]:
        print("FAIL:", issue)
    for warning in warnings[:20]:
        print("WARN:", warning)
    if len(warnings) > 20:
        print(f"... {len(warnings) - 20} additional provenance warnings")

    if errors:
        print("FAIL: catalog structural/range checks failed")
        return 1
    print("PASS: structural and range arithmetic checks succeeded")
    print("LIMIT: source truth, key/address correspondence, live chain status, and native-engine semantics are not verified.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
