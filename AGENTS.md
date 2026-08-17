# Arena oraja Agent Guide

This repository is the source of truth for the dedicated BMS-IR Arena oraja
client. Keep client work here instead of adding it to the BMS-IR server
repository.

## Repository Boundaries

- Client, launcher, input, rendering, local configuration, and Arena client
  protocol code belong here.
- In BMS-IR conversations, `秘伝のタレ` refers to the bridged requirement
  thread at `BMS-Mania/IR#478`. Read the exact linked attachment for the
  requested item instead of treating the whole mixed thread as one Issue.
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
- Use the configured `gh` CLI from the first request for GitHub write actions,
  including Issues and pull requests. Do not probe the connected GitHub app
  first; its write path for this repository is already known to return `403`.
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
- The desktop launcher now lives in its own repository,
  `tenP0312-dev/oraja-Rancher`. For launcher changes, clone that repository
  and run `cargo test --locked` in its `src-tauri` directory, plus the
  applicable unsigned bundle validation.
- On the normal macOS development host, use macOS-only builds and an isolated
  macOS install for implementation iterations. Do not dispatch Windows and
  macOS release CI after every edit. Once the change is stable, run the
  cross-platform CI build once as the final pre-distribution check.
- Test a sparse update during ordinary launcher iterations. Repeat the full
  empty-directory bootstrap download only when bootstrap extraction,
  inventory verification, self-update, cleanup, or storage behavior changed.
  A bug found during final validation must still be fixed and retested; these
  limits prevent redundant passes, not necessary regression work.
- For UI or rendering changes, use automated/static checks and leave physical
  client acceptance to the operator. Codex must not use Computer Use or launch,
  activate, focus, or control the launcher, updater, or game body. Record only
  the manual evidence returned by the operator.
- For Arena protocol changes, verify both an ordinary non-Arena play path and
  a controlled Arena match against the paired server version.

## Release Boundaries

- Source merge does not authorize binary publication, production rollout, or
  Discord announcements.
- Any BMS-IR-built body or plugin made downloadable through the BMS-IR
  launcher is gate-bound, including internal test builds, prereleases, sparse
  updates, and stable releases. Launcher availability, not a formal-release
  label, is the trigger.
- Before promoting the signed launcher channel, complete every applicable
  ordinary-score body/plugin allowlist and Arena client-version/build gate,
  the required guarded service reloads, and effective verification through
  `BMS-Mania/IR`'s `docs/PRODUCTION_VPS_OPERATIONS.md`. Once distribution is
  authorized, these additive gate steps need no separate per-artifact prompt.
- Use only the exact reviewed artifacts named by the signed manifest. Local
  previews and third-party or unreviewed builds are excluded. A launcher-only
  update has no score or Arena body/plugin gate to add.
- Do not report a release complete while its body or plugin is downloadable
  but rejected by ordinary score submission or the Arena connection gate.
- Do not publish private server addresses, credentials, signing material,
  plugin fingerprints, allowlist values, or Discord route values.
- Development progress notes are approval-free, but cannot authorize or
  replace binary publication, deployment, or a public announcement.

## Version Locations

The Arena client version is defined in:

- `core/src/bms/player/beatoraja/Version.java`
- `core/build.gradle.kts`

Keep those values aligned for release commits.
