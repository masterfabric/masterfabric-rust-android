#!/usr/bin/env bash
# Measurement: launches the app N times on the running emulator/device (latency probe + all
# 36 benchmarks) and collects each run's perf.json as build/perf/<label>-<i>.json.
#   scripts/measure.sh <label> [runs=3]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LABEL="${1:?label required}"; RUNS="${2:-3}"
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
PKG=co.masterfabric.rust
REMOTE="/sdcard/Android/data/$PKG/files"
OUT="$ROOT/build/perf"; mkdir -p "$OUT"
for i in $(seq 1 "$RUNS"); do
  "$ADB" shell am force-stop "$PKG"
  "$ADB" shell rm -f "$REMOTE/perf.json"
  sleep 1
  "$ADB" shell am start -n "$PKG/.MainActivity" --es startScreen benchmark --ez probe true --ez autorun true >/dev/null
  for _ in $(seq 1 600); do "$ADB" shell test -f "$REMOTE/perf.json" && break; sleep 1; done
  sleep 0.5
  "$ADB" pull "$REMOTE/perf.json" "$OUT/$LABEL-$i.json" >/dev/null
  echo "run $i ok"
done
python3 "$ROOT/scripts/perf_summary.py" "$OUT"/"$LABEL"-*.json
