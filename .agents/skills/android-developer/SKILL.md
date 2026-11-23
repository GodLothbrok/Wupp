---
name: android-developer
description: >-
  Expert Android developer guide for Wupp: building, debugging, Compose UI, Room DB,
  and native Rust JNI bridge integration. Use when compiling, developing, or troubleshooting Android components.
---

# Android Developer Skill (Wupp)

This skill provides step-by-step procedures and technical reference for building and developing the Wupp Android companion app.

## Project Structure & Architecture
- **Root**: Gradle multi-project containing `:app` and native `:rustbridge`.
- **Target Platform**: Android minSdk 26, targetSdk 34, compileSdk 37 (minorApi 2).
- **Core Stack**:
  - Kotlin + Jetpack Compose (Material3)
  - Room DB (`com.wa.beastclient.data.db.WaUiDatabase`) with KSP schema generation
  - Kotlinx Serialization & Coroutines/StateFlow
  - Rust companion core (`rustbridge/`) loaded via JNI as `libwhatsbridge_rs.so`

## Build Runbook

### 1. Build Rust Native Bridge
Before packaging the APK with native bridge functionality, compile the Rust bridge for Android targets:
- **Windows (PowerShell)**:
  ```powershell
  cd rustbridge
  .\build-android.ps1
  cd ..
  ```
- **Linux/macOS (Bash)**:
  ```bash
  cd rustbridge
  bash build-android.sh
  cd ..
  ```
This places `libwhatsbridge_rs.so` in `app/src/main/jniLibs/arm64-v8a/`.

### 2. Build Android App (Debug APK)
Use the Gradle Wrapper:
- **Windows**:
  ```powershell
  .\gradlew.bat assembleDebug
  ```
- **Linux / macOS**:
  ```bash
  ./gradlew assembleDebug
  ```
The generated APK is located at: `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk` and `app-universal-debug.apk`.

### 3. Clean and Cache Invalidation
If build errors or stale KSP cache occurs:
```powershell
.\gradlew.bat clean --no-daemon
```

## Key Development Guidelines
1. **State & Architecture**: Keep UI reactive using Compose state hoisters and ViewModels. State flows should use `SharingStarted.WhileSubscribed(5000)`.
2. **Room DB Migrations**: Whenever altering entity schemas, ensure schema generation is enabled in Room and update version numbers with automated or manual migration paths.
3. **Rust JNI Boundary**: Any new JNI method must be declared `external fun` in Kotlin and implemented with corresponding `#[no_mangle] pub extern "system" fn Java_com_...` in `rustbridge/src/jni_api.rs`.
