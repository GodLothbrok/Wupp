---
name: android-tester
description: >-
  Testing guide for Wupp: unit testing, Room DB DAO tests, Compose UI tests,
  Rust bridge tests, and CI test runner execution. Use when writing, running, or triaging tests.
---

# Android Tester Skill (Wupp)

This skill provides procedures for running and authoring tests across the Wupp application stack.

## Testing Levels & Tools
- **Unit Tests (JVM)**: JUnit 4/5, Kotlin Test, MockK, Coroutines Test (`kotlinx-coroutines-test`).
- **Room DB Tests**: In-memory database testing (`Room.inMemoryDatabaseBuilder`).
- **Rust Native Core Tests**: `cargo test` inside `rustbridge/`.
- **Instrumentation Tests**: AndroidX Test Runner for on-device testing.

## Execution Runbook

### 1. Run Kotlin / Android Unit Tests
To execute all local unit tests:
```powershell
.\gradlew.bat testDebugUnitTest
```
To run tests for a specific test class:
```powershell
.\gradlew.bat testDebugUnitTest --tests "com.wa.beastclient.YourTestClass"
```
HTML test reports are generated in `app/build/reports/tests/testDebugUnitTest/index.html`.

### 2. Run Rust Core Unit Tests
Test the underlying WhatsApp protocol and encryption bridge:
```powershell
cd rustbridge
cargo test --lib
cd ..
```

### 3. Triage & Debugging Common Failures
- **Unmocked Android Methods**: Ensure `isReturnDefaultValues = true` in `app/build.gradle.kts` `testOptions.unitTests` or provide proper fakes/mocks.
- **Coroutine Dispatchers**: Always use `StandardTestDispatcher()` or `UnconfinedTestDispatcher()` and `Dispatchers.setMain` when testing ViewModels.
- **Room Schemas**: Verify that schema validation passes and test in-memory DAOs using `runTest`.
