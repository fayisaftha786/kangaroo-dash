# CI debug APK signing and update compatibility

Kangaroo CI produces a **debug APK**, not a production release. Android only installs an APK as an update to an existing app when the package identity and signing certificate are compatible. The APK SHA-256 checksum checks file integrity; it does not prove that the signer matches an installed copy.

By default, a GitHub-hosted runner may create its own Android debug keystore. A different runner can therefore sign the same app code with a different certificate. The CI artifact now includes `app-debug.apk.certs.txt` with the signer certificate fingerprint and warns when a stable key has not been configured. The signing secret is used only when the workflow ref is `refs/heads/main` and the event is either a `push` or `workflow_dispatch`. Pull-request builds and manual workflow runs targeting any other branch do not receive it.

## Configure a stable CI debug key for future builds

Do this only on a trusted device or computer. The private keystore must stay private.

1. Generate one debug keystore once. On a system with Java's `keytool`:

   ```sh
   keytool -genkeypair -v \
     -keystore "$HOME/kangaroo-debug.keystore" \
     -storepass android \
     -alias androiddebugkey \
     -keypass android \
     -keyalg RSA -keysize 2048 -validity 10000 \
     -dname "CN=Kangaroo Debug, O=Kangaroo, C=IN"
   ```

2. Encode the file as one line (the output contains private signing-key material):

   ```sh
   base64 "$HOME/kangaroo-debug.keystore" | tr -d '\r\n'
   ```

3. In GitHub, open **Settings → Secrets and variables → Actions → New repository secret**. Set the name to `KANGAROO_DEBUG_KEYSTORE_BASE64` and paste the one-line output as the secret value. Do **not** paste it into a chat, commit it to the repository, or upload the keystore as a build artifact.

4. Run CI again. The log and APK artifact should report that a stable debug key was loaded. Keep this key for future CI APKs; losing it means future APKs cannot update copies signed with it.

The keystore password and alias in the example are Android debug-key defaults. This mechanism is for development/testing only; do not use this debug key as a production signing key.

## Existing installations

The keystore currently used by an already-installed APK cannot be recovered from its public certificate. Switching from a runner-generated certificate to a stable key will not magically make the signatures match. Before any one-time reinstall, export diagnostic logs or any app data that needs to be kept. Do not uninstall an existing installation just to test a replacement APK.

No part of this signing setup enables native puzzle search; the current build remains inspection-only.
