#!/usr/bin/env python3
"""Validate the source evidence that protects Arena feature parity."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Iterable


MANIFESTS = (
    "tools/ed_0_4_0_feature_parity.json",
    "tools/distribution_feature_parity.json",
)
REQUIRED_ROLES = ("entry", "state", "execution")
FIXED_FEATURE_COUNTS = {"ed_0_4_0_feature_parity.json": 14}


class FeatureParityError(RuntimeError):
    pass


def _source_file(root: Path, relative_path: str) -> Path:
    candidate = (root / relative_path).resolve()
    try:
        candidate.relative_to(root.resolve())
    except ValueError as exc:
        raise FeatureParityError(f"evidence path escapes repository: {relative_path}") from exc
    return candidate


def validate_manifest(root: Path, manifest_path: Path) -> list[str]:
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise FeatureParityError(f"cannot read parity manifest {manifest_path}: {exc}") from exc

    features = manifest.get("features")
    expected = manifest.get("expected_feature_count")
    if manifest.get("schema_version") != 1 or not isinstance(features, list):
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
