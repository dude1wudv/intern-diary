# Android latest-stable upgrade requirements

## Goal

Upgrade the Android app build stack and runtime dependencies to current stable versions before the next public release.

## Scope

- Android Gradle Plugin: 9.2.1.
- Gradle wrapper: 9.6.1.
- Kotlin Android plugin: removed; AGP 9 built-in Kotlin support is used.
- compileSdk / targetSdk: 37.
- Build Tools / installed SDK: use local Android SDK 37.
- AndroidX / Material / OkHttp / Coil dependencies: latest stable versions verified from Maven metadata.
- Keep current XML/ViewBinding architecture.
- Preserve image picker/upload performance fixes.

## Non-goals

- Do not migrate to Jetpack Compose.
- Do not change backend API.
- Do not publish a release until the branch builds and the user confirms.