#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="/home/solo/repos/hermes-mission-control"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-/home/solo/Android/Sdk}"
ADB="${ANDROID_SDK_ROOT}/platform-tools/adb"

cd "$PROJECT_DIR"

if ! command -v npm >/dev/null 2>&1; then
  echo "npm not found"
  exit 1
fi

if [ ! -x "$ADB" ]; then
  echo "adb not found at $ADB"
  exit 1
fi

DEVICE_SERIAL="${1:-$($ADB devices | awk 'NR>1 && $2=="device" {print $1; exit}') }"
if [ -z "${DEVICE_SERIAL:-}" ]; then
  echo "No Android device detected"
  exit 1
fi

echo "Using device: $DEVICE_SERIAL"

echo "Setting ADB reverse tunnels for Android debug..."
$ADB -s "$DEVICE_SERIAL" reverse tcp:1420 tcp:1420 || true
$ADB -s "$DEVICE_SERIAL" reverse tcp:1421 tcp:1421 || true

echo "Active reverse tunnels:"
$ADB -s "$DEVICE_SERIAL" reverse --list || true

echo "Starting Android debug flow..."
export ANDROID_SDK_ROOT
npx tauri android dev --target aarch64
