plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("jacoco")
}

// Staging target for the licence notices packaged into the AAR. This is the CONVENTIONAL
// Java-resources directory, not a build-dir source registered via sourceSets. Two other
// approaches were tried and both FAILED SILENTLY or obscurely:
//   1. srcDir(layout.buildDirectory.dir(...))  -> AGP accepted the Provider, ignored it,
//      and shipped an AAR with no notice on a green build.
//   2. srcDir(<eagerly resolved File in build/>) -> also not packaged.
// A probe file placed here, by contrast, provably reaches classes.jar/META-INF/.
// The directory is gitignored; jabauth-android/{LICENSE,NOTICE} remain the single source
// of truth and are copied in by stageLicensingNotices below.
val licensingStageDir: File = file("src/main/resources/META-INF")

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
                    "-DBUILD_SHARED_LIBS=ON",
                    // 16 KB page alignment, required by Android 15 (API 35) and enforced by Play
                    // for anything targeting it. NDK r27 SUPPORTS this but does not default to it
                    // — r28 does — so without the explicit flag the linker emits LOAD segments
                    // aligned to 4 KB and the library will not load on a 16 KB-page device.
                    //
                    // The failure mode is why it went unnoticed: nothing in the build objects.
                    // assembleRelease is green, the AAR is well-formed, and the app installs.
                    // Only Android complains, at install time, in a dialog the developer sees and
                    // CI never does.
                    "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384"
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

    // The AAR bundles libjabcode-mobile.so for three ABIs. That code is MIT-licensed
    // (Fraunhofer SIT + Kendall Fleming) and MIT requires its notice to accompany every
    // copy — an AAR IS a copy. AGP packages src/main/resources/** into the AAR's
    // classes.jar, so the notices staged there ride inside the shipped artifact rather
    // than sitting only in the repository, where they would satisfy nothing.
    //
    // No sourceSets override is needed: src/main/resources is already the convention.
    // See the licensingStageDir comment at the top of this file for the two approaches
    // that failed silently before this one.
}

val stageLicensingNotices by tasks.registering(Copy::class) {
    description = "Stages LICENSE and NOTICE into the AAR's classes.jar META-INF."
    from(rootProject.file("LICENSE")) { rename { "LICENSE-jabauth-sdk.txt" } }
    from(rootProject.file("NOTICE")) { rename { "NOTICE-jabauth-sdk.txt" } }
    into(licensingStageDir)
}

// Hook the task that actually CONSUMES Java resources. preBuild ordering does not
// guarantee the staged files exist before they are read.
tasks.matching { it.name.startsWith("process") && it.name.endsWith("JavaRes") }
    .configureEach { dependsOn(stageLicensingNotices) }

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
