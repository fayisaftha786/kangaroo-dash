"""Static regression guards for the inspection-only release.

These tests check source-level guard placement; they do not replace Android device
testing and do not prove the native executable's runtime semantics.
"""
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read_source(relative_path):
    return (ROOT / relative_path).read_text(encoding="utf-8")


def source_region(source, start_marker, end_marker):
    start = source.find(start_marker)
    if start < 0:
        raise AssertionError(f"missing source marker: {start_marker}")
    end = source.find(end_marker, start + len(start_marker))
    if end < 0:
        raise AssertionError(f"missing end marker: {end_marker}")
    return source[start:end]


class InspectionOnlySourceGuardTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.policy = read_source(
            "app/src/main/java/app/kangaroodash/SolverServiceExecutionPolicy.java"
        )
        cls.activity = read_source(
            "app/src/main/java/app/kangaroodash/MainActivity.java"
        )
        cls.service = read_source(
            "app/src/main/java/app/kangaroodash/SolverService.java"
        )

    def test_build_policy_is_fail_closed(self):
        self.assertIn("private static final boolean INSPECTION_ONLY_BUILD = true;", self.policy)
        self.assertIn("return !INSPECTION_ONLY_BUILD;", self.policy)
        self.assertIn("nativeExecutionBlockReason()", self.policy)
        self.assertIn("native search is not authorized", self.policy)

    def test_activity_rejects_empty_eligible_catalog_before_starting_service(self):
        start = source_region(
            self.activity,
            "private void startSolver()",
            "private static final java.math.BigInteger SECP256K1_P",
        )
        no_catalog = start.find("if (!catalogDataParsed)")
        no_eligible_entries = start.find("if (catalogEligibleEntryCount == 0)")
        selected_entry_gate = start.find("if (selectedPuzzleBlocked")
        service_start = start.find("startForegroundService(i)")
        self.assertGreaterEqual(no_catalog, 0)
        self.assertGreaterEqual(no_eligible_entries, 0)
        self.assertGreaterEqual(selected_entry_gate, 0)
        self.assertGreaterEqual(service_start, 0)
        self.assertLess(no_catalog, service_start)
        self.assertLess(no_eligible_entries, service_start)
        self.assertLess(selected_entry_gate, service_start)

    def test_service_rejects_all_starts_before_foreground_promotion(self):
        on_start = source_region(
            self.service,
            "@Override public int onStartCommand",
            "private static String safeMessage",
        )
        self.assertNotIn("diagnostics_test", on_start)
        self.assertNotIn("diagnosticsTest", on_start)
        policy_check = on_start.find("nativeExecutionBlockReason()")
        stop_self = on_start.find("stopSelf();", policy_check)
        foreground_promotion = on_start.find("startForeground(1, n")
        self.assertGreaterEqual(policy_check, 0)
        self.assertGreaterEqual(stop_self, 0)
        self.assertGreaterEqual(foreground_promotion, 0)
        self.assertLess(policy_check, stop_self)
        self.assertLess(stop_self, foreground_promotion)

    def test_worker_rechecks_policy_before_native_process_creation(self):
        loop = source_region(
            self.service,
            "void loop(String[] a)",
            "private static String fileState",
        )
        self.assertNotIn("diagnosticsTest", loop)
        self.assertNotIn("runSafeSelfTest", self.service)
        policy_check = loop.find("nativeExecutionBlockReason()")
        process_creation = loop.find("new ProcessBuilder(")
        self.assertGreaterEqual(policy_check, 0)
        self.assertGreaterEqual(process_creation, 0)
        self.assertLess(policy_check, process_creation)

    def test_simulated_self_test_entrypoint_is_removed(self):
        self.assertNotIn("startSafeSelfTest", self.activity)
        self.assertNotIn('putExtra("diagnostics_test"', self.activity)
        self.assertNotIn("diagnostics_test", self.service)
        for path in (
            "app/src/main/res/values/strings.xml",
            "app/src/main/res/values-ml/strings.xml",
        ):
            strings = read_source(path)
            self.assertNotIn("safe_self_test_button", strings)
            self.assertNotIn("self_test_starting", strings)
            self.assertNotIn("self_test_unavailable", strings)
            self.assertNotIn("self_test_start_failed", strings)


if __name__ == "__main__":
    unittest.main()
