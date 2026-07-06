# Android latest-stable upgrade design

## Version targets

- AGP 9.2.1 + Gradle 9.6.1 + JDK 17.
- AGP 9.2.1 built-in Kotlin compiler; external Kotlin Android plugin removed.
- compileSdk/targetSdk 37.
- Coil 3.5.0 with `coil3.load` imports.
- OkHttp 5.4.0.

## Upgrade order

1. Update Gradle/AGP/Kotlin/SDK levels.
2. Update dependency coordinates.
3. Adjust source imports for Coil 3.
4. Build internal app and open-source app.
5. Run diff/static checks.
6. Push branch only after build passes.

## Compatibility note

AGP 9 removes the separate `org.jetbrains.kotlin.android` plugin. The attempted Kotlin 2.4.0/coroutines 1.11.0 path pulled Kotlin stdlib metadata 2.4.0, while AGP 9.2.1 built-in compiler expected <=2.3.0 metadata. Use coroutines 1.10.2 until AGP built-in Kotlin catches up.
