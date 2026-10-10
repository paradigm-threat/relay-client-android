#!/usr/bin/env bash
# Cross-compile Kubo (cmd/ipfs) as libkubo_daemon.so for Flashback jniLibs.
# Requires: Go 1.23+, Android NDK. Output → prebuilt/{arm64-v8a,armeabi-v7a}/
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
OUT="$ROOT/prebuilt"
KUBO_SRC="${KUBO_SRC:-/tmp/kubo-src}"
KUBO_TAG="${KUBO_TAG:-v0.32.1}"
NDK="${ANDROID_NDK_HOME:-${ANDROID_HOME:-$HOME/Android/Sdk}/ndk/27.0.12077973}"
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64"
API="${ANDROID_API:-24}"

if [[ ! -x "$TOOLCHAIN/bin/aarch64-linux-android${API}-clang" ]]; then
  echo "NDK clang not found under $NDK (API $API)" >&2
  exit 1
fi

export PATH="${GOROOT:-$HOME/.local/go}/bin:${HOME}/go/bin:${PATH}"
command -v go >/dev/null || { echo "go not on PATH" >&2; exit 1; }

if [[ ! -d "$KUBO_SRC/.git" ]]; then
  git clone --depth 1 --branch "$KUBO_TAG" https://github.com/ipfs/kubo.git "$KUBO_SRC"
fi

mkdir -p "$OUT/arm64-v8a" "$OUT/armeabi-v7a" "$OUT/x86_64"
cd "$KUBO_SRC"

build_one() {
  local goarch=$1 abi=$2 cc=$3
  echo "=== $abi ($goarch) ==="
  CGO_ENABLED=1 GOOS=android GOARCH="$goarch" CC="$cc" \
    go build -trimpath -ldflags='-checklinkname=0 -s -w' \
    -o "$OUT/$abi/libkubo_daemon.so" ./cmd/ipfs
  ls -lh "$OUT/$abi/libkubo_daemon.so"
}

build_one arm64 arm64-v8a "$TOOLCHAIN/bin/aarch64-linux-android${API}-clang"
build_one arm armeabi-v7a "$TOOLCHAIN/bin/armv7a-linux-androideabi${API}-clang"
# Emulator spike (optional for publish; used by local connected tests)
build_one amd64 x86_64 "$TOOLCHAIN/bin/x86_64-linux-android${API}-clang"
echo "Done. Gradle :app:syncIpfsKubo copies these into jniLibs."
