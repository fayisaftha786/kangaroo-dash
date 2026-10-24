# Kangaroo Dash engineering roadmap

This roadmap prioritizes correctness, recoverability, observability, and repeatable releases. A green CI run proves only the checks that CI actually performs; it does not prove the opaque native engine is mathematically correct or that every Android device behaves identically.

## Guardrails

- Keep all engine diagnostics local by default; review logs before sharing because they may contain puzzle inputs or configuration.
- Never delete or rewrite user data automatically during diagnostics or cleanup.
- Do not label a file as valid persisted solver state based only on its existence or size.
- Do not claim native-engine correctness, successful key recovery, or device-level behavior without a test that directly establishes it.
- Do not promise a speedup without repeatable, controlled measurements on the same device and workload.
- Keep work within authorized test puzzles and systems.

## Phase 1 — CI trustworthiness

- [x] Run unit tests, Android lint, and debug APK assembly on pull requests and main.
- [x] Verify the ARM64 native library is present in the APK and has ELF magic.
- [x] Generate and verify an APK SHA-256 checksum.
- [x] Upload test/lint reports and the APK as build artifacts.
- [ ] Fail verification if JUnit XML is missing, malformed, or reports zero tests.
- [ ] Review every failing job before merging; do not treat a green unrelated workflow as an Android build result.

## Phase 2 — Runtime lifecycle and diagnostics

- [x] Guard against duplicate workers while an earlier native process is shutting down.
- [x] Bound native-output buffering and avoid indefinite queue waits during shutdown.
- [x] Keep diagnostic logs bounded and rotate one backup.
- [x] Make storage inventory read-only, bounded, and explicit when incomplete.
- [ ] Add deterministic tests for start/stop races, reader interruption, output floods, and cleanup failure.
- [ ] On device, test repeated start/stop, app backgrounding, service recreation, low storage, and notification denial.
- [ ] Ensure watchdog warnings distinguish “no telemetry” from confirmed process failure; never silently claim the engine is dead.

## Phase 3 — Input and result integrity

- [x] Keep input validation isolated and unit-tested.
- [ ] Add test vectors for valid and invalid inputs from an authorized challenge specification.
- [ ] Display a result as verified only when an independent, documented verifier confirms it.
- [ ] Keep “engine reported a result” separate from “result independently verified.”
- [ ] Treat absent or malformed telemetry as unknown, not as zero progress or success.

## Phase 4 — Performance measurement (not speculative optimization)

- [ ] Add a repeatable benchmark harness using a fixed, non-sensitive test workload.
- [ ] Record device model, Android version, duration, CPU temperature if available, memory pressure, and telemetry freshness.
- [ ] Compare multiple runs and report variance; do not compare runs with different inputs or thermal conditions.
- [ ] Use measurements to diagnose UI stalls, excessive logging, memory growth, and service lifecycle overhead.
- [ ] Do not infer cryptographic search success from operation counts alone.

## Phase 5 — Release confidence

- [ ] Keep CI green on the exact commit used for the APK.
- [ ] Retain APK checksum and verification reports together.
- [ ] Test install, launch, start/stop, restart, and upgrade behavior on a physical ARM64 device.
- [ ] Document known limitations, native binary provenance, and the checks CI cannot perform.

## Current known limitation

The repository contains `libkangaroo.so` but not its native source. Android-side tests can validate process management, input handling, UI behavior, logging, and packaging; they cannot establish the native algorithm's correctness or the integrity/semantics of its internal persistence format.
