plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("jacoco")
}

android {
    namespace = "com.jabauth.jabcode"
    compileSdk = rootProject.property("COMPILE_SDK").toString().toInt()

    // Pin the NDK so it does not float to AGP's default. A floating version is
    // re-resolved — and re-downloaded — on every clean CI run, and a corrupt
    // sdkmanager NDK download is exactly what flaked testDebugUnitTest. A fixed
    // version also gives the workflow a stable cache key (jabcode-sdk-unit-tests.yml).
    ndkVersion = "27.0.12077973"

    defaultConfig {
        minSdk = rootProject.property("MIN_SDK").toString().toInt()

        // Jetpack Microbenchmark requires its own runner — it manages
        // activity setup, GC suppression, and CPU-stability checks that
        // the default AndroidJUnitRunner does not. AndroidBenchmarkRunner
        // is a drop-in superset: non-benchmark androidTest classes
        // (CameraEnumeratorInstrumentedTest, JABCodeDecoderWithMetaInstrumentedTest,
        // etc.) still execute normally, they just go through a tiny
        // amount of additional setup overhead.
        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"

        // Allow Microbenchmark to run on debuggable builds for local
        // `connectedCheck` workflows. The default Gradle test task
        // (`connectedDebugAndroidTest`) targets the `debug` variant
        // which is necessarily debuggable; rather than force users to
        // switch to `connectedBenchmarkAndroidTest` (which doesn't auto-
        // generate for library modules), we accept the DEBUGGABLE flag
        // and the slight measurement noise it introduces in exchange
        // for benchmarks that actually run.
        //
        // For production-grade measurements (regression CI, release
        // tracking), use the `benchmark` build type defined below via
        // `gradle :framework:jabcode-sdk:connectedBenchmarkAndroidTest`
        // explicitly.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "DEBUGGABLE"
        consumerProguardFiles("consumer-rules.pro")
        
        // NDK configuration
        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a", "x86_64"))
        }
        
        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c11", "-O3", "-fPIC")
                arguments += listOf(
                    "-DMOBILE_BUILD=ON",
                    "-DBUILD_SHARED_LIBS=ON"
                )
            }
        }
    }
    
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // Microbenchmark refuses to run on debuggable builds (debug
        // instrumentation skews measurements unreliably). Provide a
        // dedicated non-debuggable variant so `connectedCheck` can
        // pick it up without the user needing to pass
        // `androidx.benchmark.suppressErrors=DEBUGGABLE`.
        //
        // Note: Android Library modules don't expose `isDebuggable`
        // (it's an Application-only property). Inheriting from
        // `release` is sufficient — release-flavored library
        // variants are non-debuggable by default.
        create("benchmark") {
            initWith(buildTypes.getByName("release"))
            matchingFallbacks += listOf("release")
        }
    }
    
    externalNativeBuild {
        cmake {
            path = file("../../../swift-java-wrapper/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
    
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    // Framework dependencies
    implementation(project(":framework:core"))
    
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib:${rootProject.property("KOTLIN_VERSION")}")
    
    // Android Core
    implementation("androidx.core:core-ktx:1.12.0")
    
    // CameraX (for camera utilities)
    implementation("androidx.camera:camera-core:1.3.0")
    
    // Testing
    testImplementation("junit:junit:${rootProject.property("JUNIT_VERSION")}")
    testImplementation("org.mockito:mockito-core:${rootProject.property("MOCKITO_VERSION")}")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    testImplementation("org.robolectric:robolectric:${rootProject.property("ROBOLECTRIC_VERSION")}")
    testImplementation("androidx.test:core:${rootProject.property("ANDROIDX_TEST_VERSION")}")
    testImplementation("com.google.truth:truth:1.1.5")
    
    androidTestImplementation("androidx.test.ext:junit:${rootProject.property("ANDROIDX_TEST_VERSION")}")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")

    // Jetpack Microbenchmark (component-level on-device benchmarks).
    // Used by CameraEnumerationBenchmark, StreamValidationBenchmark, and
    // ImageQualityAnalysisBenchmark per BENCHMARK_TESTING_GUIDE.md.
    // The library provides BenchmarkRule + measureRepeated() with proper
    // warmup, GC suppression, and CPU-stable measurement.
    androidTestImplementation("androidx.benchmark:benchmark-junit4:1.3.4")
}

// JaCoCo configuration
tasks.withType<Test> {
    configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")
    
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    
    val fileFilter = listOf(
        "**/R.class",
        "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "android/**/*.*"
    )
    
    val debugTree = fileTree("${project.layout.buildDirectory.get().asFile}/tmp/kotlin-classes/debug") {
        exclude(fileFilter)
    }
    
    val mainSrc = "${project.projectDir}/src/main/java"
    
    sourceDirectories.setFrom(files(mainSrc))
    classDirectories.setFrom(files(debugTree))
    executionData.setFrom(fileTree(project.layout.buildDirectory.get().asFile) {
        include("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")
    })
}
