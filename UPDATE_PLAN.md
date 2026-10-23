# Full App Audit and Update Plan

**Repository:** `fayisaftha786/kangaroo-dash`  
**Audit baseline:** `92c078d52b87d0f88d5623dc41bdfff8c25e67cd`  
**Status:** Source/CI review completed; physical-device and native-engine behavior remain unverified.

## What was reviewed

- Android manifest and foreground-service declarations
- `MainActivity.java` input validation, controls, dashboard, and result presentation
- `SolverService.java` process launch/termination, output parsing, stale telemetry warning, restart loop, thermal pause, DP-file metadata, and result flags
- `DiagnosticLog.java` bounded UTF-8 logging and log rotation
- Unit tests for input validation, diagnostic logging, and telemetry parsing
- CI workflow, APK artifact, and SHA-256 checksum verification
- Repository contents and the compiled native library packaging

## Existing protections confirmed in source

- Public-key input checks hex characters, length, prefix, and secp256k1 curve-point validity for compressed and uncompressed encodings.
- The default sample public key is a valid compressed secp256k1 generator point; it is a sample, not a target key.
- Numeric fields have configured bounds, but start/range consistency still needs the native CLI contract.
- Stale `found.txt` is cleared before launching a new scan.
- Missing/non-executable native library is reported rather than retried forever.
- Native output is read off the worker thread through a bounded queue.
- Process shutdown has a graceful wait and force-kill fallback.
- Diagnostic logs are size-bounded and UTF-8-safe.
- A stalled telemetry warning is not automatically treated as proof the engine is dead.
- A result is explicitly labelled unverified unless the engine emits its own success-verification marker; independent verification is still unavailable.
- DP-file presence/size is not described as proof of successful native save/load.

## Findings and limitations

### P1 — Native-engine correctness and telemetry format are not independently inspectable

Only `libkangaroo.so` is present; the native implementation/source is not. The UI's Ops/DP parser expects output matching `Ops: <integer>M | DPs: <integer>`. Unit tests cover this expected format and malformed/unrelated lines, but the real binary's output still needs to be checked against a sanitized device `scan.log`. If the binary uses another format, the app can remain RUNNING while speed is shown as `—`. Do not invent telemetry or infer a successful search from elapsed time or a DP file.

### P1 — DP persistence is not proven

The app passes `KANGAROO_DP_FILE` and records file metadata, but metadata alone cannot prove the native engine reads, validates, or restores the file. A controlled stop/relaunch test with native-side evidence is required.

### P1 — Physical-device lifecycle still needs verification

CI covers unit tests, lint, and APK assembly, not Android service/process behavior on a real device. Test STOP during startup/output/restart wait, repeated START, backgrounding, thermal pause, process exit, wake-lock release, and notification cleanup before release.

### P2 — Start/range semantics are not fully validated

The public key is checked as a valid secp256k1 point. The start value is checked as hex, but its exact accepted format and consistency with range bits depend on the unavailable native CLI contract. Do not tighten validation in a way that might reject legitimate engine inputs until that contract is established.

### P2 — Result trust boundary

The app's own `Verification: SUCCESS` line is still only a native-engine claim. `resultVerified` remains false, and the UI labels the result as not independently verified. Preserve that distinction and add an independent verifier only when its input/output format and expected public-key/range semantics are documented.

### P2 — Restart observability

The restart counter is reset at each new service start and increments on each in-session restart. Confirm this meaning is clear in the UI and test whether repeated nonzero exits produce a useful, bounded diagnostic instead of an endless loop. Do not silently change retry policy without a defined product requirement.

## Ordered update plan

### Phase 1 — Reproducible baseline
- [x] Run CI unit tests, lint, debug APK build, and APK checksum verification for the audited commit.
- [x] Add a physical-device test checklist and document APK checksum limitations.
- [ ] Record APK SHA-256, device model, Android version, and exact tested commit for every device run.
- [ ] Capture sanitized `scan.log` and logcat from one bounded, test-only run.

### Phase 2 — Input and state safety
- [ ] Document the native CLI contract for public key, start value, range bits, DP bits, and kangaroo count.
- [ ] Add tests for numeric boundaries, malformed lengths, prefix cases, overflow, and empty values.
- [x] Validate compressed/uncompressed secp256k1 public keys against the curve equation and replace the invalid hard-coded sample key with the valid generator point. Start/range consistency still requires the native CLI contract.
- [ ] Add lifecycle regression tests or a small state-machine abstraction for START/STOP/restart races before changing service control further.

### Phase 3 — Telemetry reliability
- [ ] Capture real native output from the target phone and compare it with the current Ops/DP parser.
- [x] Add parser tests for valid lines, unrelated output, malformed counters, and prefix/suffix output.
- [ ] Extend parser/runtime tests for ANSI escapes, counter reset, overflow, unchanged counters, and missing telemetry.
- [ ] Separate last-output time from last-progress time in the UI/logs, so output activity and computational progress are not conflated.
- [x] Confirm stale warnings recover only when a meaningful Ops/DP counter changes in the current source review; device behavior still needs confirmation.

### Phase 4 — Process and thermal lifecycle
- [ ] On-device verify exactly one engine process after START and no lingering process after STOP.
- [ ] Verify STOP while waiting to restart and while paused for temperature.
- [ ] Confirm high-temperature behavior, recovery, foreground notification removal, and wake-lock release.
- [ ] Decide and test a bounded restart/backoff policy for repeated crashes.

### Phase 5 — Persistence and result integrity
- [ ] Verify DP load/save using native-side evidence across a controlled restart; file metadata alone is insufficient.
- [ ] Test stale result cleanup and malformed/empty result files.
- [ ] Define an independent result-verification procedure and test known synthetic fixtures.
- [x] Keep all results visibly labelled as unverified until independent verification succeeds.

### Phase 6 — Release gate
- [ ] CI green on the exact release commit.
- [ ] Applicable device checklist complete on supported Android versions.
- [ ] No unresolved P1 findings; remaining P2 findings documented.
- [ ] Sanitized logs and test evidence attached to the PR.
- [ ] Review and merge PR only after evidence is available; no merge is performed by this audit.

## Current CI evidence

The workflow for commit `98deca879e880617dff2e2ddd10a889f967e2d15` completed successfully, including `testDebugUnitTest`, `lintDebug`, `assembleDebug`, SHA-256 generation/check, and APK artifact upload:
https://github.com/fayisaftha786/kangaroo-dash/actions/runs/39904019834

The telemetry test PR CI also passed on commit `9276ade684a46e33d8caeb454a6bac75dc91f8d4`:
https://github.com/fayisaftha786/kangaroo-dash/actions/runs/39908881957

A successful CI run is not proof of native-engine correctness, DP restore behavior, or physical-device lifecycle behavior.
