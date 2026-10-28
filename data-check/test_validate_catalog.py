import unittest

from validate_catalog import validate


def valid_catalog():
    records = []
    for pid in range(1, 161):
        lo = 1 << (pid - 1)
        hi = (1 << pid) - 1
        records.append({
            "id": pid,
            "bits": pid,
            "address": f"synthetic-address-{pid}",
            "status": "unsolved",
            "public_key": None,
            "pubkey_known": False,
            "hash160": None,
            "private_key_already_public": False,
            "unsafe_for_wallets": False,
            "source_url": f"https://example.org/catalog/{pid}",
            "range_start_hex": f"{lo:064x}",
            "range_end_hex": f"{hi:064x}",
            "range_size_decimal": str(hi - lo + 1),
            "search_bits": pid - 1,
        })
    return records


class BundledCatalogValidatorTests(unittest.TestCase):
    def test_valid_synthetic_catalog_has_no_errors_or_warnings(self):
        errors, warnings = validate(valid_catalog())
        self.assertEqual(errors, [])
        self.assertEqual(warnings, [])

    def test_rejects_wrong_inclusive_range_size(self):
        records = valid_catalog()
        records[1]["range_size_decimal"] = "3"
        errors, _ = validate(records)
        self.assertTrue(any("#2: incorrect inclusive range size" in e for e in errors))

    def test_rejects_wrong_search_bits(self):
        records = valid_catalog()
        records[1]["search_bits"] = 0
        errors, _ = validate(records)
        self.assertTrue(any("#2: search_bits must be integer id - 1" in e for e in errors))

    def test_rejects_boolean_search_bits_even_when_bool_equals_one(self):
        records = valid_catalog()
        records[1]["search_bits"] = True
        errors, _ = validate(records)
        self.assertTrue(any("#2: search_bits must be integer id - 1" in e for e in errors))

    def test_rejects_duplicate_ids(self):
        records = valid_catalog()
        records[1]["id"] = 1
        errors, _ = validate(records)
        self.assertTrue(any("duplicate IDs" in e for e in errors))

    def test_requires_explicit_boolean_safety_flags(self):
        records = valid_catalog()
        records[0].pop("unsafe_for_wallets")
        errors, _ = validate(records)
        self.assertTrue(any("#1: unsafe_for_wallets must be boolean" in e for e in errors))

    def test_solved_records_require_both_safety_flags(self):
        records = valid_catalog()
        records[0]["status"] = "solved"
        errors, _ = validate(records)
        self.assertTrue(any("#1: solved record must be flagged private_key_already_public" in e
                            for e in errors))
        self.assertTrue(any("#1: solved record must be flagged unsafe_for_wallets" in e
                            for e in errors))

        records[0]["private_key_already_public"] = True
        records[0]["unsafe_for_wallets"] = True
        errors, _ = validate(records)
        self.assertEqual(errors, [])

    def test_missing_source_is_reported_as_provenance_warning(self):
        records = valid_catalog()
        records[0]["source_url"] = None
        errors, warnings = validate(records)
        self.assertEqual(errors, [])
        self.assertTrue(any("#1: source_url missing or not HTTPS" in w for w in warnings))

    def test_non_array_root_is_rejected(self):
        errors, warnings = validate({"id": 1})
        self.assertEqual(errors, ["catalog root must be a JSON array"])
        self.assertEqual(warnings, [])


if __name__ == "__main__":
    unittest.main()
