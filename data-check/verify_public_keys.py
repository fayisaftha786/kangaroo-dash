#!/usr/bin/env python3
"""Validate catalog addresses and any supplied SEC public keys.

This verifies address checksums and public-key/address correspondence only.
It does not recover private keys, prove source authenticity, or verify live-chain status.

Usage: python3 data-check/verify_public_keys.py [catalog.json]
"""
import hashlib
import json
import sys
from pathlib import Path

P = 0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F
ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
DEFAULT_CATALOG = Path("data-check/puzzles.json")


def sha256(data):
    return hashlib.sha256(data).digest()


def hash160(data):
    return hashlib.new("ripemd160", sha256(data)).digest()


def decode_base58check(address):
    n = 0
    for char in address:
        if char not in ALPHABET:
            raise ValueError("invalid Base58 character")
        n = n * 58 + ALPHABET.index(char)

    raw = n.to_bytes((n.bit_length() + 7) // 8, "big") if n else b""
    raw = b"\x00" * (len(address) - len(address.lstrip("1"))) + raw
    if len(raw) != 25:
        raise ValueError("unexpected Base58Check length")
    if sha256(sha256(raw[:-4]))[:4] != raw[-4:]:
        raise ValueError("bad Base58Check checksum")
    if raw[0] != 0:
        raise ValueError("not a mainnet P2PKH address")
    return raw[1:21]


def valid_public_point(key):
    if len(key) == 33 and key[0] in (2, 3):
        x = int.from_bytes(key[1:], "big")
        if x >= P:
            return False
        rhs = (pow(x, 3, P) + 7) % P
        y = pow(rhs, (P + 1) // 4, P)
        if pow(y, 2, P) != rhs:
            return False
        if (y & 1) != (key[0] & 1):
            y = P - y
        return (y & 1) == (key[0] & 1) and pow(y, 2, P) == rhs

    if len(key) == 65 and key[0] == 4:
        x = int.from_bytes(key[1:33], "big")
        y = int.from_bytes(key[33:], "big")
        return x < P and y < P and pow(y, 2, P) == (pow(x, 3, P) + 7) % P

    return False


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
    if not isinstance(data, list):
        print("FAIL: catalog root must be a JSON array", file=sys.stderr)
        return 1

    checked_addresses = 0
    checked_keys = 0
    errors = []

    for index, record in enumerate(data):
        label = f"record[{index}]"
        if not isinstance(record, dict):
            errors.append((label, "not a JSON object"))
            continue
        try:
            address_hash = decode_base58check(record.get("address", ""))
            checked_addresses += 1

            stored_hash = record.get("hash160")
            if stored_hash is not None:
                if not isinstance(stored_hash, str) or len(stored_hash) != 40:
                    raise ValueError("stored hash160 must be 40 hexadecimal characters")
                try:
                    stored_hash_bytes = bytes.fromhex(stored_hash)
                except ValueError as exc:
                    raise ValueError("stored hash160 is not hexadecimal") from exc
                if stored_hash_bytes != address_hash:
                    raise ValueError("stored hash160 does not match address")

            pub = record.get("public_key")
            if pub is None or pub == "":
                continue
            if not isinstance(pub, str):
                raise ValueError("public_key must be a hex string or null")
            try:
                key = bytes.fromhex(pub)
            except ValueError as exc:
                raise ValueError("public_key is not valid hex") from exc
            if not valid_public_point(key):
                raise ValueError("invalid SEC secp256k1 public point")
            if hash160(key) != address_hash:
                raise ValueError("public-key HASH160 does not match address")
            checked_keys += 1
        except Exception as exc:
            errors.append((f"#{record.get('id', index)}", str(exc)))

    print(f"Catalog: {path}")
    print(f"Records: {len(data)}")
    print(f"Addresses successfully checked: {checked_addresses}")
    print(f"Public keys successfully checked: {checked_keys}")
    print(f"Failures: {len(errors)}")
    for label, reason in errors[:100]:
        print(f"FAIL {label}: {reason}")
    if len(errors) > 100:
        print(f"... {len(errors) - 100} additional failures")

    if checked_keys == 0:
        print("FAIL: no public keys were available for verification")
        return 1
    if errors:
        print("FAIL: address/key integrity checks did not pass")
        return 1
    print("PASS: all addresses and supplied public-key/address pairs passed local integrity checks")
    print("LIMIT: source authenticity, live-chain status, and native-engine behavior are not verified")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
