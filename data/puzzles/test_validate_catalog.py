import unittest

from validate_catalog import validate


def record(puzzle_id=1):
    return {
        "id": puzzle_id,
        "bit_length": 3,
        "range_start": "08",
        "range_end": "10",
        "range_end_inclusive": False,
        "public_key": None,
        "address": None,
        "source_url": "https://example.org/source",
        "verification_status": "unverified",
        "notes": "Synthetic test fixture; not a real puzzle record.",
    }


class CatalogValidatorTests(unittest.TestCase):
    def test_accepts_well_formed_synthetic_record(self):
        self.assertEqual(validate([record()]), [])

    def test_rejects_duplicate_ids(self):
        errors = validate([record(), record()])
        self.assertTrue(any("duplicate id" in error for error in errors))

    def test_rejects_reversed_range(self):
        item = record()
        item["range_start"] = "20"
        item["range_end"] = "10"
        errors = validate([item])
        self.assertTrue(any("range_start must be less" in error for error in errors))

    def test_rejects_malformed_public_key(self):
        item = record()
        item["public_key"] = "not-a-public-key"
        errors = validate([item])
        self.assertTrue(any("public_key" in error for error in errors))

    def test_rejects_http_source(self):
        item = record()
        item["source_url"] = "http://example.org/source"
        errors = validate([item])
        self.assertTrue(any("source_url" in error for error in errors))

    def test_rejects_non_array_root(self):
        self.assertEqual(validate({"id": 1}), ["catalog root must be a JSON array"])


if __name__ == "__main__":
    unittest.main()
