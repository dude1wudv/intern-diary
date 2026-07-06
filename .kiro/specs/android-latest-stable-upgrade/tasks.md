# Android latest-stable upgrade tasks

- [x] Create branch `upgrade/android-latest-stable` in `diary-open-source`.
- [x] Update build stack versions.
- [x] Update Android dependencies.
- [x] Adjust Coil imports.
- [x] Build internal debug APK (`:app:assembleDebug`, 2026-07-06).
- [x] Build open-source debug APK (`:app:assembleDebug`, 2026-07-06).
- [x] Run diff/static checks (`git diff --check`, Maven metadata, `dependencyInsight`).
- [x] Push branch for review.

## Notes

- AGP 9.2.1 includes Android Kotlin support; the separate `org.jetbrains.kotlin.android` plugin and `kotlinOptions` block were removed.
- Coil 3.5.0 currently pulls Kotlin stdlib 2.4.0, which fails with AGP 9.2.1's stable compiler metadata support. Coil is pinned to 3.4.0 as the latest tested compatible Coil 3 line for this stack.
- Maven metadata check found kotlinx-coroutines-android 1.11.0 is stable and compatible; upgraded from 1.10.2.
