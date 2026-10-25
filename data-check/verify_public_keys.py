import json
import hashlib

P = 0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F
ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

def sha256(b):
    return hashlib.sha256(b).digest()

def hash160(b):
    h = sha256(b)
    return hashlib.new("ripemd160", h).digest()

def decode_base58check(s):
    n = 0
    for c in s:
        if c not in ALPHABET:
            raise ValueError("Invalid Base58 character")
        n = n * 58 + ALPHABET.index(c)

    raw = n.to_bytes((n.bit_length() + 7) // 8, "big") if n else b""
    raw = b"\x00" * (len(s) - len(s.lstrip("1"))) + raw

    if len(raw) != 25:
        raise ValueError("Unexpected address length")
    if sha256(sha256(raw[:-4]))[:4] != raw[-4:]:
        raise ValueError("Bad Base58Check checksum")
    if raw[0] != 0:
        raise ValueError("Not a mainnet P2PKH address")
    return raw[1:21]

def valid_compressed_point(key):
    if len(key) != 33 or key[0] not in (2, 3):
        return False
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

data = json.load(open("data-check/puzzles.json"))
checked = 0
errors = []

for r in data:
    pub = r.get("public_key")
    if not pub:
        continue

    n = r["id"]
    try:
        key = bytes.fromhex(pub)
        if not valid_compressed_point(key):
            raise ValueError("Invalid compressed secp256k1 point")

        address_hash = decode_base58check(r["address"])
        if hash160(key) != address_hash:
            raise ValueError("Public key HASH160 does not match address")

        stored_hash = r.get("hash160")
        if stored_hash and hash160(key).hex() != stored_hash.lower():
            raise ValueError("Stored hash160 mismatch")

        checked += 1
    except Exception as e:
        errors.append((n, str(e)))

print("Records:", len(data))
print("Public keys successfully checked:", checked)
print("Failures:", len(errors))
for n, reason in errors:
    print(f"FAIL Puzzle #{n}: {reason}")

if not errors:
    print("PASS: all present public keys match their catalog addresses")
else:
    print("CHECK FAILED: do not treat the catalog as fully verified")
