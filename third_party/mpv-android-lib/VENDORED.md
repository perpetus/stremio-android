# Vendored mpv-android

Source: https://github.com/mpv-android/mpv-android

Pinned release tag: `2026-04-25`

Pinned commit: `3018d47277d5b3ca02acdd96466f261c1d23ee08`

This directory vendors the minimal embeddable pieces needed by the Android app:

- `MPVLib.kt`
- `BaseMPVView.kt`
- JNI bridge sources under `app/src/main/jni`
- `cacert.pem`
- prebuilt release native libraries for `armeabi-v7a`, `arm64-v8a`, `x86`, and `x86_64`

Upstream `mpv-android/mpv-android` tag `2026-04-25` does not include `subfont.ttf` in `app/src/main/assets`; only `cacert.pem` is vendored from that assets directory.

The app intentionally excludes upstream `libc++_shared.so` from this module and uses the C++ runtime already packaged by the main app native libraries.

## Local Patch

`BaseMPVView` changes its `AttributeSet` constructor parameter from non-null to nullable so the app can instantiate it programmatically from Compose interop.

## Rebuilding Native Libraries

Native rebuilds are supported by upstream only on Linux/macOS. Windows should use WSL or CI.

Run from this directory:

```sh
./rebuild-native.sh
```

The script builds upstream for `armv7l`, `arm64`, `x86`, and `x86_64`, then copies the generated `.so` files into this module, omitting `libc++_shared.so`.

## Release Asset Checksums

Native libraries currently come from upstream release APKs:

- `app-default-arm64-v8a-release.apk`
  - SHA-256: `4400bcba6be9cec1128e24d1eba153d8727384926b0639fa7fe44d4e36b04f81`
- `app-default-armeabi-v7a-release.apk`
  - SHA-256: `95a863eddc407f95bb1e5917957d7a9b0e6e290109ee5709b7eb3bc90458a024`
- `app-default-x86-release.apk`
  - SHA-256: `2cf77b8cc654b9a257602aff0ad7f8c7e933dd91b99a29f9ca02f3c397e6b1dc`
- `app-default-x86_64-release.apk`
  - SHA-256: `af240afd26d7110f83ee14a6dbb6c2b451eb1849ea99bb8a27e1928a7df7d873`

## License Notes

The upstream Android wrapper is MIT licensed. The bundled native libraries include mpv/FFmpeg-related components and may carry additional redistribution obligations. Review upstream release notes and dependency licenses before public distribution.
