#!/usr/bin/env python3
"""Static contract check for the bundled, precompiled native engine.

This only checks ELF identity and embedded contract strings. It does not execute
the binary and cannot prove CLI parsing, range behavior, or DP save/load at runtime.
"""
from pathlib import Path
import struct
import sys

REQUIRED_CONTRACT_STRINGS = {
    "range-bit maximum (1..=255)": b"range_bits must be 1..=255",
    "public-key input help": b"Public key to solve",
    "start-value input help": b"Start of search range",
    "range-width input help": b"Bit range to search",
    "DP-bits input help": b"Distinguished point bits",
    "kangaroo-count input help": b"Number of kangaroos",
    "output-file input help": b"Output file for result",
    "documented inclusive range formula": b"start + 2^range_bits - 1",
    "DP-path override variable": b"KANGAROO_DP_FILE",
    "fallback DP filename": b"/kangaroo_dp.json",
}


def validate_elf64_aarch64(data: bytes) -> None:
    """Raise ValueError unless data starts with an ELF64 little-endian AArch64 image."""
    if len(data) < 64:
        raise ValueError("ELF header is truncated")
    if data[:4] != b"\x7fELF":
        raise ValueError("missing ELF magic")
    if data[4] != 2:
        raise ValueError("expected ELF64 class")
    if data[5] != 1:
        raise ValueError("expected little-endian ELF")
    machine = struct.unpack_from("<H", data, 18)[0]
    if machine != 183:
        raise ValueError(f"expected AArch64 e_machine=183, got {machine}")


def missing_contract_strings(data: bytes) -> list[str]:
    """Return required static contract labels not found in the binary bytes."""
    return [
        label for label, expected in REQUIRED_CONTRACT_STRINGS.items()
        if expected not in data
    ]


def audit_file(path: Path) -> None:
    data = path.read_bytes()
    validate_elf64_aarch64(data)
    missing = missing_contract_strings(data)
    if missing:
        details = "\n".join(f"  - {label}" for label in missing)
        raise ValueError("missing native contract evidence:\n" + details)

    print(f"PASS: {path} is an ELF64 little-endian AArch64 image ({len(data)} bytes)")
    print(f"PASS: {len(REQUIRED_CONTRACT_STRINGS)} native contract strings are present")
    print("LIMIT: static string evidence only; no native code was executed.")
    print("LIMIT: CLI behavior, range arithmetic at runtime, and DP save/load remain unverified.")


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print(f"Usage: {Path(argv[0]).name} PATH_TO_LIBKANGAROO_SO", file=sys.stderr)
        return 2
    try:
        audit_file(Path(argv[1]))
    except (OSError, ValueError) as exc:
        print(f"FAIL: native engine static audit: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
