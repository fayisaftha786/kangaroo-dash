"""Regression checks for CI debug signing compatibility and artifact evidence."""
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


class CiDebugSigningContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = (ROOT / ".github/workflows/build.yml").read_text(encoding="utf-8")
        cls.docs = (ROOT / "docs/CI_DEBUG_SIGNING.md").read_text(encoding="utf-8")

    def test_workflow_accepts_keystore_from_repository_secret_before_gradle(self):
        secret = self.workflow.find("KANGAROO_DEBUG_KEYSTORE_BASE64")
        setup_gradle = self.workflow.find("- name: Setup Gradle")
        self.assertGreaterEqual(secret, 0)
        self.assertGreaterEqual(setup_gradle, 0)
        self.assertLess(secret, setup_gradle)
        self.assertIn('"$HOME/.android/debug.keystore"', self.workflow)
        self.assertIn("keytool -list", self.workflow)
        self.assertIn("KANGAROO_STABLE_DEBUG_SIGNING=true", self.workflow)

    def test_workflow_warns_when_signing_key_is_not_stable(self):
        self.assertIn("::warning::KANGAROO_DEBUG_KEYSTORE_BASE64 is not configured", self.workflow)
        self.assertIn("may not update an APK signed by a different run", self.workflow)

    def test_workflow_records_and_uploads_apk_signer_fingerprint(self):
        self.assertIn('APKSIGNER="$SDK_ROOT/build-tools/36.0.0/apksigner"', self.workflow)
        self.assertIn('verify --verbose --print-certs "$APK"', self.workflow)
        self.assertIn("Signer #1 certificate SHA-256 digest:", self.workflow)
        self.assertIn("app/build/outputs/apk/debug/app-debug.apk.certs.txt", self.workflow)
        self.assertIn("APK certificate fingerprint identifies the signer", self.workflow)

    def test_documentation_protects_private_key_and_existing_app_data(self):
        self.assertIn("paste it into a chat", self.docs)
        self.assertIn("Do not uninstall an existing installation", self.docs)
        self.assertIn("inspection-only", self.docs)


if __name__ == "__main__":
    unittest.main()
