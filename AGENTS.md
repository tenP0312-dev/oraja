# Arena oraja Agent Guide

This repository is the source of truth for the dedicated BMS-IR Arena oraja
client. Keep client work here instead of adding it to the BMS-IR server
repository.

## Repository Boundaries

- Client, launcher, input, rendering, local configuration, and Arena client
  protocol code belong here.
- The Arena server, public Web site, SQLite data, BMS-IR plugin, public update
  records, and production operations belong in `BMS-Mania/IR`.
- Protocol changes require paired Issues and compatible implementations in
  both repositories. Record the paired server commit or pull request in the
  client Issue.
- Preserve the LR2oraja Endless Dream and beatoraja upstream history. Use the
  upstream remotes for comparison; do not rewrite shared history.

## Workflow

- Read `README.md`, `doc/BMSIR_ARENA.md`,
  `docs/CODEX_PROGRESS_DISCORD.md`, and the code around the affected
  behavior before editing.
- Read-only investigation does not require an Issue. Implementation uses a
  scoped `codex/` branch, an Issue, local validation, a pull request, and merge
  only after required checks pass.
- Keep `main` protected. Do not push feature commits directly to `main`.
- Preserve unrelated local changes and avoid destructive Git commands.
- Use `apply_patch` for manual file edits.
- For substantial work expected to take more than about five minutes, define
  the task phases first. Send progress immediately at phase changes and errors,
  plus every 10 minutes while one phase continues, using the shared
  development route described in `docs/CODEX_PROGRESS_DISCORD.md`.

## Validation

- Run the smallest relevant Gradle tests after a stable change.
- For ordinary client changes, run `./gradlew core:test` and the applicable
  target build when the environment supports it.
- For launcher changes, run `cargo test --locked` in
  `arena-launcher/src-tauri` and the applicable unsigned bundle validation.
- For UI or rendering changes, verify the affected flow in a real client at
  desktop resolution and check that controls remain usable at smaller window
  sizes.
- For Arena protocol changes, verify both an ordinary non-Arena play path and
  a controlled Arena match against the paired server version.

## Release Boundaries

- Source merge does not authorize binary publication, production rollout, or
  Discord announcements.
- Official releases require an exact reviewed source commit, matching version
  fields, successful builds, signing/notarization where applicable, artifact
  verification, BMS-IR allowlist updates when required, and explicit release
  approval.
- Do not publish private server addresses, credentials, signing material,
  plugin fingerprints, allowlist values, or Discord route values.
- Development progress notes are approval-free, but cannot authorize or
  replace binary publication, deployment, or a public announcement.

## Version Locations

The Arena client version is defined in:

- `core/src/bms/player/beatoraja/Version.java`
- `core/build.gradle.kts`

Keep those values aligned for release commits.
