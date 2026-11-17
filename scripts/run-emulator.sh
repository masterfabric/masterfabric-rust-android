#!/usr/bin/env bash
# Boots the project AVD headless (only if no emulator is running: never two at once),
# installs the benchmark APK and launches the app.
#   scripts/run-emulator.sh [extra `am start` args, e.g. --ez autorun true --es scrollTo table]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$ANDROID_HOME/platform-tools/adb"
AVD="${AVD:-MF_Pixel_9_API_36}"
PKG=co.masterfabric.rust
if ! "$ADB" devices | grep -q '^emulator-'; then
  "$ANDROID_HOME/emulator/emulator" -avd "$AVD" -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect >/tmp/mf-emulator.log 2>&1 &
  "$ADB" wait-for-device
  until [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 1; done
fi
APK="$ROOT/platforms/android/app/build/outputs/apk/benchmark/app-benchmark.apk"
"$ADB" install -r "$APK" >/dev/null
"$ADB" shell am force-stop "$PKG"
"$ADB" shell am start -n "$PKG/.MainActivity" "$@" >/dev/null
echo "launched $PKG $*"
