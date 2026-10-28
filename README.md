[![Android CI](https://github.com/fayisaftha786/kangaroo-dash/actions/workflows/build.yml/badge.svg)](https://github.com/fayisaftha786/kangaroo-dash/actions/workflows/build.yml)

**Current build target:** Kangaroo v2.4 (`versionCode 6`) · Android API 36. Local diagnostics report JSON parse/load state, usable catalog-entry count, execution-eligible count, and Activity-level START rejection reason. Valid puzzle metadata can be shown for inspection while solved/unsafe records and records missing required per-record source references remain blocked from native START. Puzzles #140, #145, #150, #155 and #160 now cite the 2019-06-01 on-chain public-key reveal transaction at record scope; that source supports public-key exposure, not a solved scalar or live-balance claim. The native engine's internal search semantics and DP persistence are not proven by CI.

## Downloading and checking a CI APK

1. Open the [Actions runs](https://github.com/fayisaftha786/kangaroo-dash/actions/workflows/build.yml) and select a successful run for the branch/commit you intend to test.
2. In **Artifacts**, download `kangaroo-apk` and extract the ZIP.
3. Keep `app-debug.apk` and `app-debug.apk.sha256` in the same directory.
4. Verify the checksum before installing:
   - Linux: `sha256sum --check app-debug.apk.sha256`
   - macOS: `shasum --check app-debug.apk.sha256`
   - Windows Command Prompt: run `certutil -hashfile app-debug.apk SHA256` and compare the displayed hash with the hash in `app-debug.apk.sha256`.

A matching SHA-256 checksum detects accidental changes or corruption relative to the checksum file. It does **not** independently prove who produced the APK or that the solver's results are correct. Only install builds you trust, and test debug APKs on a device you control.

## Distinguished-point (DP) persistence status

The Android service passes the path of `dp.json` to the native engine through the
`KANGAROO_DP_FILE` environment variable. The dashboard can report the file's size,
but **file existence or size does not prove that the native engine successfully saves,
validates, or reloads its contents**. The native implementation of this environment
variable must be inspected and tested before persistence across process restarts can
be described as verified.

Current status: **save/load behavior is unverified from the Android Java code alone**.
Do not treat the DP storage size metric as proof that previously processed work is
restored after restarting the app or engine.


## Safe device self-test (no native search)

The dashboard includes a **RUN SAFE SELF-TEST (NO NATIVE SEARCH)** button. It exercises the Android foreground-service lifecycle for about eight seconds using explicitly simulated counters. It does not invoke `libkangaroo.so`, search a puzzle, or claim a result.

Use it to check the visible service status, notification lifecycle, STOP handling, wake-lock cleanup, and diagnostic log path before any native-engine test. The simulated test must not create, delete, or change `dp.json` or `found.txt`.

A successful self-test proves only that this simulated service path works. It does not verify the native binary, puzzle range semantics, real telemetry, or DP save/load.

## Reliability update and validation boundaries

The Android UI validates the public key as a secp256k1 point and accepts the solver start value only as 1–64 hexadecimal digits (up to 256 bits). The Service now independently validates all five arguments again before deleting a prior `found.txt` result or launching the native binary; malformed or missing extras fail closed and are logged without recording their values. This is input validation only; it does not prove that the native engine interprets every argument as intended.

### Verification matrix

| Area | Automated check | Device/native check still needed |
| --- | --- | --- |
| Public-key input | Compressed and uncompressed secp256k1 points; malformed/off-curve values | Confirm native CLI accepts the same encodings |
| Start value | Empty, non-hex, 64-digit boundary, and over-256-bit input | Confirm the native engine's exact start-value semantics |
| Telemetry | Valid progress records, malformed tokens, and integer overflow | Compare dashboard values with real engine output |
| Diagnostic logs | UTF-8, bounded size, rotation, and oversized legacy files | Confirm useful logs are retained during real device failures |
| Process cleanup | Graceful/forced termination, exceptions, repeated interrupts, and surviving process reporting | Stop/start, service recreation, and OS process cleanup on a physical Android device |
| DP persistence | UI reports file presence/size without claiming verification | Inspect native implementation and prove save/reload behavior across restarts |

Run the automated checks with `./gradlew testDebugUnitTest lintDebug assembleDebug --no-daemon`. A green CI run confirms these repository checks passed; it does not independently verify the native algorithm, DP persistence, or real-device lifecycle behavior.


## CPU, I/O and battery efficiency

The Android service now checks battery temperature on a 15-second cadence instead of requesting a battery-temperature snapshot for every native output line. A hot reading still triggers the existing stop/pause behavior; the cadence change does not disable thermal protection.

Recognized high-frequency `Ops/DPs` telemetry is written to `scan.log` immediately and then at most once per 5 seconds per native-process run. Dashboard counters and live speed continue to update for every recognized telemetry line. Non-telemetry engine output and important service/process events continue to be logged.

This reduces repeated framework queries and log-file open/write/close operations during chatty solver output. It is a conservative overhead reduction, not a promise of higher native OPS: the native binary and actual device workload must be profiled to quantify CPU, memory, battery, and throughput changes.

### Device benchmark protocol

1. Use the same device, APK, target/configuration, charging state, screen state, and ambient temperature for each run.
2. Record a 10-minute baseline and a 10-minute optimized run; repeat each at least three times after the device cools.
3. Record solver-reported OPS/DPs, app CPU, process PSS/RSS, battery temperature, battery-level change, and log file growth.
4. Compare medians, not the single best run. Stop if the device becomes uncomfortably hot or Android reports thermal warnings.
5. Do not claim a performance improvement until device measurements confirm it. Automated unit tests verify timing policy, not real hardware performance.
