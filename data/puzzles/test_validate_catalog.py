import unittest
from validate_catalog import validate


def sample(pid=1):
    return {
        "id": pid,
        "bit_length": 4,
        "range_start": "08",
        "range_end": "10",
        "range_end_inclusive": False,
        "public_key": None,
        "address": None,
        "source_url": "https://example.org/source",
        "verification_status": "unverified",
        "notes": "Synthetic test fixture only.",
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

    def test_inclusive_single_value_range_is_valid(self):
        item = sample()
        item["range_start"] = "0x08"
        item["range_end"] = "0X08"
        item["range_end_inclusive"] = True
        self.assertEqual(validate([item]), [])

    def test_exclusive_equal_bounds_are_invalid(self):
        item = sample()
        item["range_start"] = "08"
        item["range_end"] = "08"
        item["range_end_inclusive"] = False
        self.assertTrue(any("exclusive range_start must be less" in e for e in validate([item])))

    def test_boundary_convention_is_required_when_range_is_present(self):
        item = sample()
        item.pop("range_end_inclusive")
        self.assertTrue(any("range_end_inclusive must be true or false" in e for e in validate([item])))

    def test_only_one_range_boundary_is_invalid(self):
        item = sample()
        item["range_end"] = None
        self.assertTrue(any("must both be supplied or both be null" in e for e in validate([item])))

    def test_upper_exclusive_bit_length_boundary_is_valid(self):
        item = sample()
        item["range_start"] = "0X08"
        item["range_end"] = "0X10"
        item["range_end_inclusive"] = False
        self.assertEqual(validate([item]), [])

    def test_inclusive_end_cannot_equal_upper_exclusive_boundary(self):
        item = sample()
        item["range_end"] = "10"
        item["range_end_inclusive"] = True
        self.assertTrue(any("range_end exceeds" in e for e in validate([item])))

    def test_start_must_fit_declared_bit_length(self):
        item = sample()
        item["range_start"] = "07"
        self.assertTrue(any("range_start is below" in e for e in validate([item])))

    def test_uppercase_hex_prefix_is_accepted(self):
        item = sample()
        item["range_start"] = "0X08"
        item["range_end"] = "0X10"
        self.assertEqual(validate([item]), [])

    def test_malformed_hex_is_rejected(self):
        item = sample()
        item["range_start"] = "0xGG"
        self.assertTrue(any("range_start must be valid hexadecimal" in e for e in validate([item])))

    def test_bit_length_boolean_is_rejected(self):
        item = sample()
        item["bit_length"] = True
        self.assertTrue(any("bit_length must be a positive integer" in e for e in validate([item])))

    def test_range_convention_must_be_null_when_bounds_missing(self):
        item = sample()
        item["range_start"] = None
        item["range_end"] = None
        item["range_end_inclusive"] = True
        self.assertTrue(any("must be null when range bounds are unavailable" in e for e in validate([item])))

    def test_bad_public_key(self):
        item = sample()
        item["public_key"] = "bad-key"
        self.assertTrue(any("public_key" in e for e in validate([item])))

    def test_http_source_rejected(self):
        item = sample()
        item["source_url"] = "http://example.org/source"
        self.assertTrue(any("source_url must be an https URL" in e for e in validate([item])))

    def test_uppercase_https_scheme_is_accepted(self):
        item = sample()
        item["source_url"] = "HTTPS://example.org/source"
        self.assertEqual(validate([item]), [])

    def test_root_must_be_array(self):
        self.assertEqual(validate({"id": 1}), ["catalog root must be a JSON array"])


if __name__ == "__main__":
    unittest.main()
