[![Android CI](https://github.com/fayisaftha786/kangaroo-dash/actions/workflows/build.yml/badge.svg)](https://github.com/fayisaftha786/kangaroo-dash/actions/workflows/build.yml)

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


## Reliability update and validation boundaries

The Android UI validates the public key as a secp256k1 point and accepts the solver start value only as 1–64 hexadecimal digits (up to 256 bits). This is input-shape validation; it does not prove that the native engine interprets every argument as intended.

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
