import json
from pathlib import Path

p = Path("data-check/puzzles.json")
data = json.loads(p.read_text())

errors = []
ids = []

if not isinstance(data, list):
    raise SystemExit("FAIL: root is not a list")

for i, r in enumerate(data):
    if not isinstance(r, dict):
        errors.append(f"record[{i}]: not an object")
        continue

    n = r.get("id")
    b = r.get("bits")
    ids.append(n)

    if not isinstance(n, int) or isinstance(n, bool) or not 1 <= n <= 160:
        errors.append(f"record[{i}]: invalid id {n!r}")
        continue

    if b != n:
        errors.append(f"#{n}: bits={b!r}, expected {n}")

    start = r.get("range_start_hex")
    end = r.get("range_end_hex")
    size = r.get("range_size_decimal")

    if not isinstance(start, str) or not isinstance(end, str):
        errors.append(f"#{n}: missing range hex")
        continue

    try:
        lo = int(start, 16)
        hi = int(end, 16)
        expected_lo = 1 << (n - 1)
        expected_hi = (1 << n) - 1

        if lo != expected_lo:
            errors.append(f"#{n}: incorrect range start")
        if hi != expected_hi:
            errors.append(f"#{n}: incorrect range end")
        if size != str(expected_hi - expected_lo + 1):
            errors.append(f"#{n}: incorrect range size")
        if r.get("search_bits") != n - 1:
            errors.append(f"#{n}: incorrect search_bits")
    except (ValueError, TypeError):
        errors.append(f"#{n}: malformed range values")

    pub = r.get("public_key")
    known = r.get("pubkey_known")
    if known != (isinstance(pub, str) and len(pub) > 0):
        errors.append(f"#{n}: public_key/pubkey_known mismatch")

    if not isinstance(r.get("address"), str) or not r["address"]:
        errors.append(f"#{n}: missing address")

duplicates = sorted({x for x in ids if ids.count(x) > 1})
if duplicates:
    errors.append(f"Duplicate IDs: {duplicates}")

expected_ids = set(range(1, 161))
actual_ids = {x for x in ids if isinstance(x, int)}
missing = sorted(expected_ids - actual_ids)
extra = sorted(actual_ids - expected_ids)

if missing:
    errors.append(f"Missing IDs: {missing}")
if extra:
    errors.append(f"Unexpected IDs: {extra}")

print("Records:", len(data))
print("Unique valid IDs:", len(actual_ids))
print("Errors:", len(errors))

for e in errors[:100]:
    print("FAIL:", e)

if errors:
    raise SystemExit(1)

print("PASS: structural and range checks succeeded")
print("Note: source truth and live blockchain status not verified.")
