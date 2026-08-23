from __future__ import annotations

import json
from pathlib import Path
import tempfile
import unittest

from check_feature_parity import (
    FeatureParityError,
    _audit_commit_digest,
    validate_manifest,
    validate_release_repository,
)


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

    def test_schema_two_requires_a_complete_unique_commit_ledger(self) -> None:
        manifest = self._manifest()
        data = json.loads(manifest.read_text(encoding="utf-8"))
        data.update({
            "schema_version": 2,
            "expected_audit_commit_count": 1,
            "audit_commits": [{
                "commit": "a" * 40,
                "status": "integrated",
                "feature": "feature",
                "note": "covered",
            }],
        })
        manifest.write_text(json.dumps(data), encoding="utf-8")

        self.assertEqual([], validate_manifest(self.root, manifest))

        data["audit_commits"].append(data["audit_commits"][0])
        data["expected_audit_commit_count"] = 2
        manifest.write_text(json.dumps(data), encoding="utf-8")
        errors = validate_manifest(self.root, manifest)
        self.assertTrue(any("duplicate audited commit" in error for error in errors))

    def test_audit_commit_digest_is_order_independent_but_content_sensitive(self) -> None:
        expected = _audit_commit_digest(["a" * 40, "b" * 40])
        self.assertEqual(expected, _audit_commit_digest(["b" * 40, "a" * 40]))
        self.assertNotEqual(expected, _audit_commit_digest(["a" * 40, "c" * 40]))


if __name__ == "__main__":
    unittest.main()
