import unittest
from validate_catalog import validate


def sample(pid=1):
    return {
        "id": pid, "bit_length": 3, "range_start": "08", "range_end": "10",
        "range_end_inclusive": False, "public_key": None, "address": None,
        "source_url": "https://example.org/source",
        "verification_status": "unverified",
        "notes": "Synthetic test fixture only."
    }


class CatalogValidatorTests(unittest.TestCase):
    def test_valid_synthetic_record(self):
        self.assertEqual(validate([sample()]), [])

    def test_duplicate_id(self):
        self.assertTrue(any("duplicate id" in e for e in validate([sample(), sample()])))

    def test_reversed_range(self):
        item = sample()
        item["range_start"], item["range_end"] = "20", "10"
        self.assertTrue(any("range_start must be less" in e for e in validate([item])))

    def test_bad_public_key(self):
        item = sample()
        item["public_key"] = "bad-key"
        self.assertTrue(any("public_key" in e for e in validate([item])))

    def test_http_source_rejected(self):
        item = sample()
        item["source_url"] = "http://example.org/source"
        self.assertTrue(any("source_url" in e for e in validate([item])))

    def test_root_must_be_array(self):
        self.assertEqual(validate({"id": 1}), ["catalog root must be a JSON array"])


if __name__ == "__main__":
    unittest.main()
