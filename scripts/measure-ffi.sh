#!/usr/bin/env bash
# Test #37 (FFI boundary): launches the app N times on the running emulator/device, runs only
# the FFI section, collects ffi.json as build/perf/ffi-<label>-<i>.json and prints the
# per-row median.   scripts/measure-ffi.sh <label> [runs=3]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LABEL="${1:?label required}"; RUNS="${2:-3}"
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
PKG=co.masterfabric.rust
REMOTE="/sdcard/Android/data/$PKG/files"
OUT="$ROOT/build/perf"; mkdir -p "$OUT"
for i in $(seq 1 "$RUNS"); do
  "$ADB" shell am force-stop "$PKG"
  "$ADB" shell rm -f "$REMOTE/ffi.json"
  sleep 1
  "$ADB" shell am start -n "$PKG/.MainActivity" --es startScreen benchmark --ez ffiAutorun true --es scrollTo ffi >/dev/null
  for _ in $(seq 1 600); do "$ADB" shell test -f "$REMOTE/ffi.json" && break; sleep 1; done
  sleep 0.5
  "$ADB" pull "$REMOTE/ffi.json" "$OUT/ffi-$LABEL-$i.json" >/dev/null
  echo "run $i ok"
done
python3 "$ROOT/scripts/ffi_summary.py" "$OUT"/ffi-"$LABEL"-*.json
