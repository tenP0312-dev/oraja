from __future__ import annotations

import json
from pathlib import Path
import tempfile
import unittest

from check_feature_parity import FeatureParityError, validate_manifest, validate_release_repository


class FeatureParityTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        (self.root / "src").mkdir()
        (self.root / "src/feature.txt").write_text("entry state execution test", encoding="utf-8")

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def _manifest(self, marker: str = "execution") -> Path:
        manifest = self.root / "manifest.json"
        evidence = {
            role: {"path": "src/feature.txt", "contains": value}
            for role, value in (("entry", "entry"), ("state", "state"), ("execution", marker))
        }
        manifest.write_text(json.dumps({
            "schema_version": 1,
            "expected_feature_count": 1,
            "features": [{"id": "feature", "state_scope": "session", "evidence": evidence,
                          "regressions": [{"path": "src/feature.txt", "contains": "test"}]}],
        }), encoding="utf-8")
        return manifest

    def test_accepts_complete_entry_state_execution_evidence(self) -> None:
        self.assertEqual([], validate_manifest(self.root, self._manifest()))

    def test_reports_a_missing_execution_marker(self) -> None:
        errors = validate_manifest(self.root, self._manifest("missing"))
        self.assertIn("execution marker 'missing'", errors[0])

    def test_release_validation_requires_every_manifest(self) -> None:
        with self.assertRaisesRegex(FeatureParityError, "required parity manifest is missing"):
            validate_release_repository(self.root, ("missing.json",))


if __name__ == "__main__":
    unittest.main()
