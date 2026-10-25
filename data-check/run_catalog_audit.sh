#!/usr/bin/env bash
set -u

cd "$(dirname "$0")/.."

echo "===== 1. STRUCTURAL AND RANGE VALIDATION ====="
python3 data-check/validate_catalog.py
range_result=$?

echo
echo "===== 2. PUBLIC KEY / ADDRESS VALIDATION ====="
python3 data-check/verify_public_keys.py
key_result=$?

echo
echo "===== AUDIT SUMMARY ====="
if [ "$range_result" -eq 0 ] && [ "$key_result" -eq 0 ]; then
    echo "PASS: both local checks passed"
    echo "Caveat: source authenticity and live blockchain status are not verified."
    exit 0
else
    echo "FAIL: one or more local checks failed"
    echo "Range validator exit code: $range_result"
    echo "Public-key validator exit code: $key_result"
    exit 1
fi
