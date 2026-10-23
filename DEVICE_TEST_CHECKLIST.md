# Android device QA checklist

Automated CI passing is not proof that the native engine works on a physical phone. Complete this checklist on each supported Android version before calling a release verified.

## Before testing
- Record device model, Android version, app commit SHA, APK SHA-256, and available storage.
- Use a synthetic/test-only fixture and a harmless bounded run. Do not use a real wallet secret or private key.
- Capture Android logcat and the app's `scan.log` before and after each case.
- Note the exact time for START and STOP so logs can be correlated.

### Capture logs with ADB (optional)
With USB debugging enabled and the phone connected to a trusted computer:

```sh
adb devices
adb logcat -c
# Reproduce one test case on the phone, then:
adb logcat -d -v threadtime > device-logcat.txt
```

If you have access to the app's private files through a debuggable build, capture `scan.log` using Android Studio's Device Explorer. Otherwise, use an in-app export if the app provides one. Do not assume private app files are accessible with `adb pull` on a non-rooted production device.

Before attaching logs to an issue or PR, inspect and redact any secrets, personal identifiers, tokens, local paths, and unrelated app output. Keep an unredacted copy only locally and share the minimum relevant time window. Never include a real wallet private key, seed phrase, or secret.

## App and service lifecycle
- [ ] App opens without a crash and all fields remain usable after rotating/reopening if rotation is supported.
- [ ] START with valid test inputs produces one foreground service and one engine process.
- [ ] Repeated START taps do not launch duplicate engine processes.
- [ ] STOP during startup, active output, a quiet/no-output period, and a restart delay stops the process promptly.
- [ ] After STOP, the foreground notification disappears, the wake lock is released, and the UI settles to STOPPED.
- [ ] Backgrounding the app and turning the screen off do not cause unexpected duplicate runs or stale RUNNING state.
- [ ] Force-stopping and relaunching the app does not report a stale run as active.

## Input and error handling
- [ ] Empty fields, non-hex public keys/start values, invalid prefixes/lengths, and out-of-range numeric fields are rejected before launch.
- [ ] A missing/non-executable native binary produces a readable error instead of a crash.
- [ ] A native process that exits nonzero is logged clearly and any restart behavior is bounded and understandable.
- [ ] Large or rapidly emitted engine output does not freeze the UI or prevent STOP from completing.

## Storage and result integrity
- [ ] Confirm the native engine actually creates/updates `dp.json`; record size and modification time before and after a controlled run.
- [ ] Stop and relaunch a controlled run; verify from native-engine evidence that saved state is loaded, not merely that the file exists.
- [ ] A stale `found.txt` is not shown as a new result.
- [ ] Any reported result is independently checked by a separate trusted verifier; the engine's own success text is not sufficient.
- [ ] Logs and dashboard labels do not imply verification when independent verification has not occurred.

## Thermal and long-run behavior
- [ ] Confirm temperature readings are available and in Celsius on the test device.
- [ ] Verify high-temperature handling actually terminates/pauses the native process as intended and can recover without duplicate processes.
- [ ] Observe memory, battery, temperature, and responsiveness during a controlled run.
- [ ] Confirm there is no ANR, crash, unbounded log growth, or lingering foreground notification after stopping.

## Release gate
- [ ] CI unit tests, lint, and debug build pass for the exact commit.
- [ ] Every applicable item above passes on a physical device.
- [ ] Attach sanitized logs and device details to the pull request.
- [ ] Any unchecked item is reported as unverified; do not claim 100% correctness.
