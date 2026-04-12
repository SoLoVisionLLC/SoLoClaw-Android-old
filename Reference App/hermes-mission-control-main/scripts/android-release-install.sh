#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="/home/solo/repos/hermes-mission-control"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-/home/solo/Android/Sdk}"
ADB="${ANDROID_SDK_ROOT}/platform-tools/adb"
BUILD_TOOLS="$(find "$ANDROID_SDK_ROOT/build-tools" -maxdepth 1 -mindepth 1 -type d | sort | tail -1)"

cd "$PROJECT_DIR"

DEVICE_SERIAL="${1:-$($ADB devices | awk 'NR>1 && $2=="device" {print $1; exit}') }"
if [ -z "${DEVICE_SERIAL:-}" ]; then
  echo "No Android device detected"
  exit 1
fi

echo "Using device: $DEVICE_SERIAL"

echo "Building web bundle..."
npm run build:web

echo "Syncing current frontend bundle into Android assets..."
rm -rf src-tauri/gen/android/app/src/main/assets
mkdir -p src-tauri/gen/android/app/src/main/assets
cp dist/index.html src-tauri/gen/android/app/src/main/assets/index.html
cp -R dist/assets src-tauri/gen/android/app/src/main/assets/assets
python3 - <<'PY'
from pathlib import Path
p = Path('src-tauri/gen/android/app/src/main/assets/index.html')
text = p.read_text()
inject = "    <script>globalThis.__MISSION_CONTROL_API_BASE__ = 'https://hermes.solobot.cloud';</script>\n"
if '__MISSION_CONTROL_API_BASE__' not in text:
    text = text.replace('    <script type="module"', inject + '    <script type="module"')
p.write_text(text)
print('patched Android asset index.html with explicit API base')
PY

echo "Building Android release APK with Gradle (tauri CLI assembleUniversalRelease task unavailable)..."
cd src-tauri/gen/android
ANDROID_SDK_ROOT="$ANDROID_SDK_ROOT" ./gradlew --no-daemon --console=plain app:assembleRelease
cd "$PROJECT_DIR"

UNSIGNED="src-tauri/gen/android/app/build/outputs/apk/universal/release/app-universal-release-unsigned.apk"
ALIGNED="src-tauri/gen/android/app/build/outputs/apk/universal/release/app-universal-release-aligned.apk"
SIGNED="src-tauri/gen/android/app/build/outputs/apk/universal/release/app-universal-release-signed.apk"

echo "Signing APK with debug keystore for local install..."
"$BUILD_TOOLS/zipalign" -f 4 "$UNSIGNED" "$ALIGNED"
"$BUILD_TOOLS/apksigner" sign \
  --ks "$HOME/.android/debug.keystore" \
  --ks-key-alias androiddebugkey \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out "$SIGNED" "$ALIGNED"

echo "Installing on device..."
"$ADB" -s "$DEVICE_SERIAL" install -r "$SIGNED"

echo "Launching app..."
"$ADB" -s "$DEVICE_SERIAL" shell am start -W -n com.solovision.hermes.missioncontrol/.MainActivity

echo "Done. Installed APK: $SIGNED"
