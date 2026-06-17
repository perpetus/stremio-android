# Stremio Mobile (Kotlin / Jetpack Compose)

An open-source Stremio application for Android built natively using Kotlin, Jetpack Compose, and Kotlin Multiplatform (KMP).

GitHub Repository: [stremio-android](https://github.com/perpetus/stremio-android)

It integrates a high-performance, local streaming server powered by the Rust-based [stream-server](https://github.com/perpetus/stream-server).

> [!WARNING]
> This application is currently in its **testing phase**. Bugs, performance anomalies, and unimplemented features are expected at this stage.

## Screenshot

<p align="center">
  <img src="screenshot.png" alt="Stremio Android App Screenshot" width="360"/>
</p>


## Project Structure

* **`app`**: The Android application module containing the Jetpack Compose UI, player components (Media3/ExoPlayer), ViewModels, and screens.
* **`streamio-core-kotlin`**: A composite Kotlin Multiplatform (KMP) library that acts as a JNI bridge to the Rust Stremio core, automatically compiling Rust libraries for target architectures (`arm64-v8a` and `x86_64`).

## Prerequisites

* **Android SDK**: Compile SDK `37` and NDK installed.
* **Rust Toolchain**: `cargo ndk` installed for Android cross-compilation.
* **Vcpkg**: Required for dependency management during native library builds (e.g. OpenSSL).

## Build & Run

To build the application and compile the native JNI libraries (including the `stream-server` and Stremio core):

```powershell
# Compile the debug APK
.\gradlew assembleDebug

# Install the app on a connected emulator or device
.\gradlew installDebug
```
