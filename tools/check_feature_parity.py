#!/usr/bin/env python3
"""Validate the source evidence that protects Arena feature parity."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path
from typing import Iterable


MANIFESTS = (
    "tools/ed_0_4_0_feature_parity.json",
    "tools/distribution_feature_parity.json",
    "tools/ed_mainline_feature_parity.json",
)
REQUIRED_ROLES = ("entry", "state", "execution")
FIXED_FEATURE_COUNTS = {
    "ed_0_4_0_feature_parity.json": 14,
    "ed_mainline_feature_parity.json": 24,
}
FIXED_AUDIT_COMMIT_COUNTS = {"ed_mainline_feature_parity.json": 52}
FIXED_AUDIT_COMMIT_DIGESTS = {
    "ed_mainline_feature_parity.json":
        "896b677b52c2122b5178ac948a6823b5f5d61c65b7c85d8248bd62213cd0726e",
}
AUDIT_STATUSES = {"integrated", "equivalent", "superseded", "excluded", "metadata"}


class FeatureParityError(RuntimeError):
    pass


def _source_file(root: Path, relative_path: str) -> Path:
    candidate = (root / relative_path).resolve()
    try:
        candidate.relative_to(root.resolve())
    except ValueError as exc:
        raise FeatureParityError(f"evidence path escapes repository: {relative_path}") from exc
    return candidate


def _audit_commit_digest(commits: Iterable[str]) -> str:
    payload = "\n".join(sorted(commits)) + "\n"
    return hashlib.sha256(payload.encode("ascii")).hexdigest()


def validate_manifest(root: Path, manifest_path: Path) -> list[str]:
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise FeatureParityError(f"cannot read parity manifest {manifest_path}: {exc}") from exc

    features = manifest.get("features")
    expected = manifest.get("expected_feature_count")
    schema_version = manifest.get("schema_version")
    if schema_version not in {1, 2} or not isinstance(features, list):
        raise FeatureParityError(f"invalid parity manifest schema: {manifest_path}")
    if expected != len(features):
        raise FeatureParityError(
            f"{manifest_path.name}: expected {expected} features, found {len(features)}"
        )
    fixed_count = FIXED_FEATURE_COUNTS.get(manifest_path.name)
    if fixed_count is not None and expected != fixed_count:
        raise FeatureParityError(
            f"{manifest_path.name}: expected_feature_count must remain {fixed_count}"
        )

    errors: list[str] = []
    seen: set[str] = set()
    for feature in features:
        feature_id = feature.get("id")
        if not isinstance(feature_id, str) or not feature_id or feature_id in seen:
            errors.append(f"invalid or duplicate feature id: {feature_id!r}")
            continue
        seen.add(feature_id)
        if feature.get("state_scope") not in {"persistent", "session"}:
            errors.append(f"{feature_id}: state_scope must be persistent or session")
        evidence = feature.get("evidence")
        if not isinstance(evidence, dict):
            errors.append(f"{feature_id}: evidence must be an object")
            continue
        for role in REQUIRED_ROLES:
            item = evidence.get(role)
            if not isinstance(item, dict):
                errors.append(f"{feature_id}: missing {role} evidence")
                continue
            relative_path = item.get("path")
            marker = item.get("contains")
            if not isinstance(relative_path, str) or not isinstance(marker, str) or not marker:
                errors.append(f"{feature_id}: invalid {role} evidence")
                continue
            try:
                source = _source_file(root, relative_path)
                contents = source.read_text(encoding="utf-8")
            except (OSError, UnicodeError, FeatureParityError) as exc:
                errors.append(f"{feature_id}: cannot inspect {role} evidence: {exc}")
                continue
            if marker not in contents:
                errors.append(
                    f"{feature_id}: {role} marker {marker!r} is missing from {relative_path}"
                )
        regressions = feature.get("regressions", [])
        if not isinstance(regressions, list):
            errors.append(f"{feature_id}: regressions must be a list")
            continue
        for regression in regressions:
            relative_path = regression.get("path") if isinstance(regression, dict) else None
            marker = regression.get("contains") if isinstance(regression, dict) else None
            if not isinstance(relative_path, str) or not isinstance(marker, str) or not marker:
                errors.append(f"{feature_id}: invalid regression evidence")
                continue
            try:
                contents = _source_file(root, relative_path).read_text(encoding="utf-8")
            except (OSError, UnicodeError, FeatureParityError) as exc:
                errors.append(f"{feature_id}: cannot inspect regression evidence: {exc}")
                continue
            if marker not in contents:
                errors.append(
                    f"{feature_id}: regression marker {marker!r} is missing from {relative_path}"
                )

    if schema_version == 2:
        commits = manifest.get("audit_commits")
        expected_commits = manifest.get("expected_audit_commit_count")
        if not isinstance(commits, list) or expected_commits != len(commits):
            errors.append(
                f"{manifest_path.name}: expected {expected_commits} audited commits, "
                f"found {len(commits) if isinstance(commits, list) else 'invalid'}"
            )
            commits = []
        fixed_commits = FIXED_AUDIT_COMMIT_COUNTS.get(manifest_path.name)
        if fixed_commits is not None and expected_commits != fixed_commits:
            errors.append(
                f"{manifest_path.name}: expected_audit_commit_count must remain {fixed_commits}"
            )
        seen_commits: set[str] = set()
        for item in commits:
            commit = item.get("commit") if isinstance(item, dict) else None
            status = item.get("status") if isinstance(item, dict) else None
            feature = item.get("feature") if isinstance(item, dict) else None
            if not isinstance(commit, str) or not re.fullmatch(r"[0-9a-f]{40}", commit):
                errors.append(f"invalid audited commit: {commit!r}")
                continue
            if commit in seen_commits:
                errors.append(f"duplicate audited commit: {commit}")
            seen_commits.add(commit)
            if status not in AUDIT_STATUSES:
                errors.append(f"{commit}: invalid audit status {status!r}")
            if feature is not None and feature not in seen:
                errors.append(f"{commit}: unknown feature id {feature!r}")
            if status in {"integrated", "equivalent"} and feature is None:
                errors.append(f"{commit}: {status} audit requires a feature id")
            if not isinstance(item.get("note"), str) or not item["note"].strip():
                errors.append(f"{commit}: audit note is required")
        fixed_digest = FIXED_AUDIT_COMMIT_DIGESTS.get(manifest_path.name)
        if fixed_digest is not None and _audit_commit_digest(seen_commits) != fixed_digest:
            errors.append(f"{manifest_path.name}: audited commit set does not match the fixed history")
    return errors


def validate_release_repository(root: Path, manifests: Iterable[str] = MANIFESTS) -> None:
    errors: list[str] = []
    for relative_path in manifests:
        manifest_path = _source_file(root, relative_path)
        if not manifest_path.is_file():
            errors.append(f"required parity manifest is missing: {relative_path}")
            continue
        errors.extend(validate_manifest(root, manifest_path))
    if errors:
        raise FeatureParityError("feature parity validation failed:\n- " + "\n- ".join(errors))


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args(argv)
    try:
        validate_release_repository(args.root.resolve())
    except FeatureParityError as exc:
        print(f"error: {exc}")
        return 1
    print("Arena feature parity evidence is complete.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
