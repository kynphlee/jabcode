/**
 * Macrobenchmark module — end-to-end performance measurements against the
 * `:diagnostic-app` target. Implements the three benchmarks described in
 * `BENCHMARK_TESTING_GUIDE.md` § Macrobenchmark Tests:
 *
 *   1. CameraStartupBenchmark   — cold / warm start latency
 *   2. FrameProcessingBenchmark — 60 FPS / 16ms per frame target
 *   3. JABCodeDecodeBenchmark   — end-to-end decode latency
 *
 * **Run:**
 * ```
 * ./gradlew :benchmark-macro:connectedBenchmarkAndroidTest
 * ```
 *
 * Requires a release-profileable target build of :diagnostic-app and a
 * connected device with USB debugging + benchmark mode enabled.
 */
plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.jabauth.benchmark.macro"
    compileSdk = rootProject.property("COMPILE_SDK").toString().toInt()

    defaultConfig {
        // Macrobenchmarks require API 23+; the broader project's MIN_SDK
        // may be lower, but macrobenchmark itself enforces this floor.
        minSdk = 23
        targetSdk = rootProject.property("COMPILE_SDK").toString().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        // Macrobenchmark needs a non-debuggable target build. The default
        // `release` here matches the diagnostic-app's release+profileable
        // build, since macrobenchmark measures release-like performance.
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // The target project that the macrobenchmarks measure against.
    targetProjectPath = ":diagnostic-app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation("androidx.test.ext:junit:1.1.5")
    implementation("androidx.test.espresso:espresso-core:3.5.1")
    implementation("androidx.test.uiautomator:uiautomator:2.2.0")

    // Jetpack Macrobenchmark library.
    implementation("androidx.benchmark:benchmark-macro-junit4:1.2.4")
}

androidComponents {
    beforeVariants(selector().all()) {
        // Only build the `benchmark` variant; debug isn't useful for
        // macrobenchmark and release isn't signable without the keystore.
        it.enable = it.buildType == "benchmark"
    }
}
