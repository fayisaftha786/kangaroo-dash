
[![Android CI](https://github.com/fayisaftha786/kangaroo-dash/actions/workflows/build.yml/badge.svg)](https://github.com/fayisaftha786/kangaroo-dash/actions/workflows/build.yml)



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
