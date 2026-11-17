#!/usr/bin/env bash
# Builds the Rust core in RELEASE for Android (arm64-v8a + x86_64) with cargo-ndk,
# generates the Kotlin bridge with UniFFI (library mode) and copies everything into
# platforms/android/app/src/main/{jniLibs,java}.
#   scripts/build-android.sh            # arm64-v8a + x86_64
#   ABIS="arm64-v8a" scripts/build-android.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# rustup's toolchain (Android targets live there), never Homebrew's rustc.
export PATH="$HOME/.cargo/bin:$PATH"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
if [ -z "${ANDROID_NDK_HOME:-}" ]; then
  ANDROID_NDK_HOME="$(ls -d "$ANDROID_HOME"/ndk/* 2>/dev/null | sort -V | tail -1)"
  export ANDROID_NDK_HOME
fi
[ -d "$ANDROID_NDK_HOME" ] || { echo "Android NDK not found (set ANDROID_NDK_HOME)"; exit 1; }

CRATE=app-ffi
LIB=libapp_ffi
APP="$ROOT/platforms/android/app/src/main"
JNI="$APP/jniLibs"
GEN="$ROOT/build/generated-kotlin"
ABIS="${ABIS:-arm64-v8a x86_64}"
MIN_SDK=26

targets=()
for abi in $ABIS; do targets+=(-t "$abi"); done

echo "==> Rust: $ABIS (release, API $MIN_SDK, NDK $(basename "$ANDROID_NDK_HOME"))"
rm -rf "$JNI"
cargo ndk "${targets[@]}" --platform "$MIN_SDK" -o "$JNI" build -p "$CRATE" --release

echo "==> Rust: host dylib (for UniFFI metadata)"
cargo build -p "$CRATE" --release
HOST_LIB="target/release/$LIB.dylib"
[ -f "$HOST_LIB" ] || HOST_LIB="target/release/$LIB.so"

echo "==> UniFFI: Kotlin bindings"
rm -rf "$GEN" && mkdir -p "$GEN"
cargo run -q -p uniffi-bindgen -- generate \
  --library "$HOST_LIB" \
  --language kotlin \
  --no-format \
  --out-dir "$GEN"
rm -rf "$APP/java/co/masterfabric/rust/core"
mkdir -p "$APP/java/co/masterfabric/rust/core"
cp "$GEN"/co/masterfabric/rust/core/*.kt "$APP/java/co/masterfabric/rust/core/"

echo "Done:"
find "$JNI" -name '*.so' -exec ls -lh {} \; | awk '{print "  " $5 "  " $9}'
