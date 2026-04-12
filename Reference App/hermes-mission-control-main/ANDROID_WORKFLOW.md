Android workflow for Hermes Mission Control

Big picture
- Android is a native app shell with bundled frontend assets.
- Android data must come from the live remote API at https://hermes.solobot.cloud.
- Do not rely on implicit localhost behavior on a physical phone.

Recommended commands

1. Fast local Android debug with Tauri dev
- Use when you want iterative debugging and can keep your host machine connected over adb.
- This command sets adb reverse tunnels first so localhost:1420 and localhost:1421 on the phone route back to the host.

Command:
  npm run android:debug

Notes:
- Requires a connected adb device.
- Uses adb reverse for:
  - tcp:1420 -> tcp:1420
  - tcp:1421 -> tcp:1421
- This replaces the old broken debug setup where the phone tried to hit its own localhost.

2. Reliable installable Android build for device testing
- Use when you want the app to behave like production but still install easily in local development.
- This builds the current frontend bundle, syncs it into Android assets, injects the explicit Android API base, builds a release APK, signs it with the local debug keystore, and installs it.

Command:
  npm run android:install

What it does:
- npm run build:web
- syncs dist/ into src-tauri/gen/android/app/src/main/assets
- injects:
  globalThis.__MISSION_CONTROL_API_BASE__ = 'https://hermes.solobot.cloud'
- builds Android release APK via Tauri
- signs with ~/.android/debug.keystore for local install
- installs to connected device

Why this is the preferred device-test flow
- It avoids the old localhost:1420 dev-url problem on phones.
- It keeps Android as a real bundled app shell.
- It makes Android data explicit and deterministic.

Rules going forward
- For physical phone testing, prefer:
  npm run android:install
- For live iterative debugging over adb, use:
  npm run android:debug
- Do not install old debug APKs manually if they were built without adb reverse or without the explicit Android API base.

Working APK output
- Release-style signed APK used for local installs:
  src-tauri/gen/android/app/build/outputs/apk/universal/release/app-universal-release-signed.apk

Current canonical remote endpoints
- App/domain: https://hermes.solobot.cloud
- API: https://hermes.solobot.cloud/api

If Android launches but shows no data
- Re-run:
  npm run android:install
- Then reopen the app.
- **Verify data loaded:** wait 3 seconds after launch — if still empty, proceed to logs.
- Capture logs immediately after opening:
  ```bash
  adb logcat -s CONSOLE:* | tail -50
  ```
- Confirm the device has network access (Wi-Fi or cellular). The app fetches live data from `https://hermes.solobot.cloud` — it will not show data in airplane mode.
