# Build-Config Patterns

The framework uses `buildConfigField` to vary defaults between
production and diagnostic build variants without runtime cost.
Consumer apps adopting the framework's diagnostic-controls pattern
should follow this convention.

## The pattern

```kotlin
// app-or-module-level build.gradle.kts
android {
    buildTypes {
        debug {
            buildConfigField("boolean", "DEFAULT_DEBUG_LOGGING_ENABLED", "false")
        }
        release {
            buildConfigField("boolean", "DEFAULT_DEBUG_LOGGING_ENABLED", "false")
        }
        create("benchmark") {
            initWith(buildTypes.getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            buildConfigField("boolean", "DEFAULT_DEBUG_LOGGING_ENABLED", "true")
        }
    }

    buildFeatures {
        buildConfig = true  // required for the field to be visible
    }
}
```

Then in code:

```kotlin
import com.jabauth.diagnostic.BuildConfig  // adjust package per your app

companion object {
    val DEFAULT_DEBUG_LOGGING: Boolean = BuildConfig.DEFAULT_DEBUG_LOGGING_ENABLED
}
```

Note: drop `const` from the property because `BuildConfig` fields are
generated at AGP build time, not Kotlin compile time — using `const`
fails at compile time with "Const 'val' has type 'Boolean'. Only
primitives and String are allowed."

## When to use this vs Settings vs constants

| Mechanism | When to use |
|---|---|
| `const val` at the source level | Compile-time constants that never vary; e.g., timeout milliseconds |
| `buildConfigField` per variant | Defaults that differ between dev / test / production builds |
| Settings repository (DataStore) | User-modifiable preferences that should persist across launches |
| Runtime flag (constructor parameter) | Per-decoder-instance config that may differ between concurrent decoders |

The `DEFAULT_DEBUG_LOGGING_ENABLED` flag is a `buildConfigField`
specifically because: it's an installation-time default (not a
compile-time invariant), it should NOT persist across app reinstalls
in the same way per-user preferences should (a fresh install should
get the build-variant default), and it should not be user-modifiable
at runtime in non-diagnostic builds.

## Install-wipe behavior

`./gradlew :app:installBenchmark` (and `installDebug` /
`installRelease`) call `pm install -r` (replace), which **preserves**
app data across installs when the APK signature matches. This means:

- A fresh first-install gets the build-variant default (e.g., `true`
  for benchmark)
- A subsequent install (signature-matching) preserves any prior
  DataStore-persisted user toggle
- An install with a DIFFERENT signature forces uninstall+install,
  wiping DataStore

For diagnostic / benchmark builds where you want the build-variant
default to take effect, document that the consumer should run
`adb shell pm clear <package>` once to reset DataStore after the build
variant changes. Or design the Settings UI to surface the
build-variant default visibly.

## What NOT to do

- Do NOT vary `applicationId` per build type to force data wipes —
  that fragments the user's launcher icons and breaks signature-based
  install upgrades.
- Do NOT rely on `BuildConfig.DEBUG` for diagnostic-vs-production
  branching when a custom variant exists. `BuildConfig.DEBUG` is `true`
  only for the `debug` build type; custom variants like `benchmark`
  set it to `false` even when they're diagnostic.
- Do NOT use Java-style `BuildConfig` import paths. The Kotlin import
  is `import com.jabauth.diagnostic.BuildConfig` (replace package per
  your app's `applicationId`).
