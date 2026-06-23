#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
UPSTREAM_TAG="2026-04-25"
WORK_DIR="${ROOT}/.upstream-build"
UPSTREAM_DIR="${WORK_DIR}/mpv-android-${UPSTREAM_TAG}"

case "$(uname -s)" in
  Linux|Darwin) ;;
  *)
    echo "mpv-android native rebuilds require Linux or macOS. Use WSL or CI on Windows." >&2
    exit 1
    ;;
esac

mkdir -p "${WORK_DIR}"
if [ ! -d "${UPSTREAM_DIR}" ]; then
  curl -L "https://github.com/mpv-android/mpv-android/archive/refs/tags/${UPSTREAM_TAG}.tar.gz" \
    | tar -xz -C "${WORK_DIR}"
fi

pushd "${UPSTREAM_DIR}/buildscripts" >/dev/null
./download.sh
./buildall.sh --arch armv7l mpv-android
./buildall.sh --arch arm64 mpv-android
./buildall.sh --arch x86 mpv-android
./buildall.sh --arch x86_64 mpv-android
popd >/dev/null

for abi in armeabi-v7a arm64-v8a x86 x86_64; do
  rm -rf "${ROOT}/app/src/main/jniLibs/${abi}"
  mkdir -p "${ROOT}/app/src/main/jniLibs/${abi}"
done

for abi in armeabi-v7a arm64-v8a x86 x86_64; do
  find "${UPSTREAM_DIR}/app/src/main/jniLibs/${abi}" -name '*.so' ! -name 'libc++_shared.so' \
    -exec cp {} "${ROOT}/app/src/main/jniLibs/${abi}/" \;
done

echo "Updated vendored mpv native libraries."
