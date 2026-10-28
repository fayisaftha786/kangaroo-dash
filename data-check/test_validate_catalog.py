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
            "native_search_approved": False,
            "source_url": f"https://example.org/catalog/{pid}",
            "source_scope": "record",
            "source_label": "Synthetic per-record source",
            "dataset_source_url": "https://example.org/secondary-catalog",
            "dataset_record_url": f"https://lotterypuzzle.org/puzzles/{pid}/",
            "dataset_source_snapshot": "2026-10-02",
            "chain_tx_url": None,
            "chain_tx_url_secondary": None,
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
        records[0]["source_scope"] = "missing"
        errors, warnings = validate(records)
        self.assertEqual(errors, [])
        self.assertTrue(any("#1: source_url missing or not HTTPS" in w for w in warnings))

    def test_source_url_and_scope_must_agree(self):
        records = valid_catalog()
        records[0]["source_scope"] = "missing"
        errors, _ = validate(records)
        self.assertTrue(any("#1: source_url is present but source_scope is not 'record'" in e
                            for e in errors))

    def test_record_source_without_evidence_label_is_warned(self):
        records = valid_catalog()
        records[0]["source_label"] = "   "
        errors, warnings = validate(records)
        self.assertEqual(errors, [])
        self.assertTrue(any("#1: source_url has no evidence label" in w for w in warnings))

    def test_dataset_reference_does_not_replace_missing_record_source(self):
        records = valid_catalog()
        records[0]["source_url"] = None
        records[0]["source_scope"] = "missing"
        errors, warnings = validate(records)
        self.assertEqual(errors, [])
        self.assertTrue(any("#1: source_url missing or not HTTPS" in w for w in warnings))
        self.assertFalse(any("#1: dataset_source_url missing" in w for w in warnings))

    def test_rejects_secondary_atlas_url_for_another_record(self):
        records = valid_catalog()
        records[0]["dataset_record_url"] = "https://lotterypuzzle.org/puzzles/2/"
        errors, warnings = validate(records)
        self.assertEqual(errors, [])
        self.assertTrue(any("#1: dataset_record_url missing or does not match" in w for w in warnings))

    def test_rejects_mismatched_transaction_explorer_link(self):
        records = valid_catalog()
        txid = "ab" * 32
        records[0]["status"] = "solved"
        records[0]["private_key_already_public"] = True
        records[0]["unsafe_for_wallets"] = True
        records[0]["solve_txid"] = txid
        records[0]["chain_tx_url"] = f"https://example.org/tx/{txid}"
        records[0]["chain_tx_url_secondary"] = f"https://blockstream.info/tx/{txid}"
        errors, _ = validate(records)
        self.assertTrue(any("#1: chain_tx_url must reference its recorded mempool.space transaction" in e
                            for e in errors))


    def test_real_catalog_public_key_reveals_have_record_level_on_chain_sources(self):
        from pathlib import Path
        import json

        catalog_path = Path(__file__).with_name("puzzles.json")
        bundled = json.loads(catalog_path.read_text(encoding="utf-8"))
        txid = "17e4e323cfbc68d7f0071cad09364e8193eedf8fefbcbd8a21b4b65717a4b3d3"
        for pid in (140, 145, 150, 155, 160):
            record = next(row for row in bundled if row.get("id") == pid)
            self.assertEqual(record.get("source_url"), f"https://mempool.space/tx/{txid}")
            self.assertEqual(record.get("source_scope"), "record")
            self.assertIn(f"puzzle #{pid}", record.get("source_label", "").lower())
            self.assertEqual(record.get("status"), "unsolved")
            self.assertTrue(record.get("public_key"))
            self.assertIs(record.get("native_search_approved"), False)

    def test_catalog_is_inspection_only_and_rejects_native_search_approval(self):
        records = valid_catalog()
        errors, _ = validate(records)
        self.assertEqual(errors, [])
        records[139]["native_search_approved"] = True
        errors, _ = validate(records)
        self.assertTrue(any(
            "#140: this catalog build is inspection-only; native_search_approved"
            in error for error in errors
        ))
        records = valid_catalog()
        records[0].pop("native_search_approved")
        errors, _ = validate(records)
        self.assertTrue(any(
            "#1: this catalog build is inspection-only; native_search_approved"
            in error for error in errors
        ))

    def test_non_array_root_is_rejected(self):
        errors, warnings = validate({"id": 1})
        self.assertEqual(errors, ["catalog root must be a JSON array"])
        self.assertEqual(warnings, [])


if __name__ == "__main__":
    unittest.main()
