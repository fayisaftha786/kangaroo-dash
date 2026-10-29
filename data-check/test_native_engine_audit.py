import unittest

from audit_native_engine import (
    REQUIRED_CONTRACT_STRINGS,
    missing_contract_strings,
    validate_elf64_aarch64,
)


def synthetic_elf(payload: bytes = b"") -> bytes:
    header = bytearray(64)
    header[:4] = b"\x7fELF"
    header[4] = 2  # ELF64
    header[5] = 1  # little endian
    header[6] = 1  # ELF version
    header[16:18] = (3).to_bytes(2, "little")  # ET_DYN / PIE
    header[18:20] = (183).to_bytes(2, "little")  # AArch64
    return bytes(header) + payload


class NativeEngineStaticAuditTests(unittest.TestCase):
    def test_all_required_contract_strings_are_reported_present(self):
        payload = b"\0".join(REQUIRED_CONTRACT_STRINGS.values())
        self.assertEqual(missing_contract_strings(synthetic_elf(payload)), [])

    def test_missing_range_limit_is_reported(self):
        payload = b"\0".join(
            value for label, value in REQUIRED_CONTRACT_STRINGS.items()
            if label != "range-bit maximum (1..=255)"
        )
        self.assertEqual(
            missing_contract_strings(synthetic_elf(payload)),
            ["range-bit maximum (1..=255)"],
        )

    def test_rejects_non_elf_input(self):
        with self.assertRaisesRegex(ValueError, "missing ELF magic"):
            validate_elf64_aarch64(b"not-an-elf" + b"\0" * 60)

    def test_rejects_non_aarch64_elf(self):
        data = bytearray(synthetic_elf())
        data[18:20] = (62).to_bytes(2, "little")  # x86-64
        with self.assertRaisesRegex(ValueError, "AArch64"):
            validate_elf64_aarch64(bytes(data))


if __name__ == "__main__":
    unittest.main()
