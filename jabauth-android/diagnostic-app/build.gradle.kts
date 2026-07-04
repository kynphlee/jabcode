plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("kotlin-kapt")
    id("jacoco")
}

android {
    namespace = "com.jabauth.diagnostic"
    compileSdk = rootProject.property("COMPILE_SDK").toString().toInt()
    
    defaultConfig {
        applicationId = "com.jabauth.diagnostic"
        minSdk = rootProject.property("MIN_SDK").toString().toInt()
        targetSdk = rootProject.property("TARGET_SDK").toString().toInt()
        versionCode = 1
        versionName = "1.0.0"
        
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        vectorDrawables {
            useSupportLibrary = true
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
        debug {
            enableAndroidTestCoverage = true
            enableUnitTestCoverage = true
            // Production/debug builds: verbose logging defaults OFF (user
            // must explicitly opt in via Settings).
            buildConfigField("boolean", "DEFAULT_DEBUG_LOGGING_ENABLED", "false")
            // Haptic-on-decode-success defaults ON — standard scan UX.
            buildConfigField("boolean", "DEFAULT_HAPTIC_ENABLED", "true")
            // Motion telemetry + throttling default ON in debug — developer
            // builds want full instrumentation visible.
            buildConfigField("boolean", "DEFAULT_MOTION_TELEMETRY_ENABLED", "true")
            buildConfigField("boolean", "DEFAULT_MOTION_THROTTLING_ENABLED", "true")
        }
        release {
            // Production builds: verbose logging defaults OFF.
            buildConfigField("boolean", "DEFAULT_DEBUG_LOGGING_ENABLED", "false")
            // Haptic-on-decode-success defaults ON — standard scan UX.
            buildConfigField("boolean", "DEFAULT_HAPTIC_ENABLED", "true")
            // Motion telemetry default OFF in release (privacy / battery);
            // motion throttling ON because it improves scan-success rate.
            buildConfigField("boolean", "DEFAULT_MOTION_TELEMETRY_ENABLED", "false")
            buildConfigField("boolean", "DEFAULT_MOTION_THROTTLING_ENABLED", "true")
        }
        // Build variant consumed by the `:benchmark-macro` module. Acts as
        // a release-like build (no debug coverage instrumentation) but
        // signed with the debug key so it can be installed by macro tests
        // without keystore configuration.
        create("benchmark") {
            initWith(buildTypes.getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
            // Macrobenchmark explicitly requires the target app to be
            // profileable. Granting profileable here lets macrobenchmark
            // capture trace sections and frame timing without needing a
            // signed release build.
            isProfileable = true
            // Diagnostic builds (installBenchmark wipes DataStore on every
            // reinstall — see SettingsRepository comment). Default verbose
            // logging ON so the first trace capture after a build doesn't
            // need a manual Settings toggle flip to surface the [PartI_DIAG]
            // markers.
            buildConfigField("boolean", "DEFAULT_DEBUG_LOGGING_ENABLED", "true")
            // Haptic-on-decode-success defaults ON — confirms haptic
            // wiring fires during validation scans without manual toggle.
            buildConfigField("boolean", "DEFAULT_HAPTIC_ENABLED", "true")
            // Motion telemetry + throttling default ON in benchmark variant
            // so SENSOR_SNAPSHOT markers fire and throttling improves
            // validation-scan success rate from frame 1.
            buildConfigField("boolean", "DEFAULT_MOTION_TELEMETRY_ENABLED", "true")
            buildConfigField("boolean", "DEFAULT_MOTION_THROTTLING_ENABLED", "true")
        }
    }

    buildFeatures {
        compose = true
        // Required for the per-variant DEFAULT_DEBUG_LOGGING_ENABLED constant
        // generated above to be visible to SettingsRepository.
        buildConfig = true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
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
            // Robolectric needs the merged manifest + resources (incl. the
            // ui-test-manifest stub ComponentActivity) so createComposeRule()
            // can host and render the Dashboard / Error Log / Settings screens
            // under the JABAuth theme on the JVM (the re-skin regression smoke).
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // BouncyCastle 1.84 ships per-version OSGi metadata at
            // META-INF/versions/<N>/OSGI-INF/MANIFEST.MF in each of bcprov,
            // bcpkix and bcutil, so the three collide in
            // :diagnostic-app:mergeDebugJavaResource. Android has no OSGi
            // runtime, so this packaging-only metadata is safe to drop. This
            // excludes only the OSGI-INF metadata — the multi-release BC
            // .class files under META-INF/versions/<N>/org/... are untouched.
            excludes += "/META-INF/versions/**/OSGI-INF/**"
        }
    }

    // Pin the JaCoCo version used by AGP's debug coverage instrumentation
    // (`enableAndroidTestCoverage` / `enableUnitTestCoverage` above). AGP's
    // bundled JaCoCo is too old to parse the Java 25 classes in BouncyCastle
    // 1.84's multi-release jar (META-INF/versions/25), which made
    // :diagnostic-app:mergeExtDexDebug fail in JacocoTransform. 0.8.15 adds
    // official Java 26 support, so it parses BC 1.84's bytecode cleanly.
    testCoverage {
        jacocoVersion = "0.8.15"
    }
}

// Align the JacocoReport tasks below with the instrumentation version pinned
// in android { testCoverage { ... } } so report tooling reads the same
// coverage data format.
jacoco {
    toolVersion = "0.8.15"
}

dependencies {
    val usePublishedFramework = rootProject.property("USE_PUBLISHED_FRAMEWORK").toString().toBoolean()
    val frameworkVersion = rootProject.property("FRAMEWORK_VERSION").toString()
    
    // Framework dependencies - switchable between project and Maven
    if (usePublishedFramework) {
        // Use published Maven artifacts
        implementation("com.jabauth.framework:core:$frameworkVersion")
        implementation("com.jabauth.framework:jabcode-sdk:$frameworkVersion")
        implementation("com.jabauth.framework:jabauth-client:$frameworkVersion")
        implementation("com.jabauth.framework:diagnostic-engine:$frameworkVersion")
        implementation("com.jabauth.framework:ui-components:$frameworkVersion")
    } else {
        // Use project dependencies (for active development)
        implementation(project(":framework:core"))
        implementation(project(":framework:jabcode-sdk"))
        implementation(project(":framework:jabauth-client"))
        implementation(project(":framework:diagnostic-engine"))
        implementation(project(":framework:ui-components"))
    }
    
    // COA credential inspection — the diagnostic reads the JWS header algorithm and the SD-JWT
    // disclosure digests directly for the Verification HUD / Credential drill-down. jabauth-client keeps
    // these as `implementation` (encapsulated), so the app declares them at the same pinned versions.
    implementation("com.auth0:java-jwt:4.5.2")
    implementation("com.authlete:sd-jwt:1.9")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib:${rootProject.property("KOTLIN_VERSION")}")
    
    // Android Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:${rootProject.property("LIFECYCLE_VERSION")}")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:${rootProject.property("LIFECYCLE_VERSION")}")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:${rootProject.property("LIFECYCLE_VERSION")}")
    implementation("androidx.activity:activity-compose:1.8.0")
    
    // Compose
    implementation("androidx.compose.ui:ui:${rootProject.property("COMPOSE_VERSION")}")
    implementation("androidx.compose.ui:ui-graphics:${rootProject.property("COMPOSE_VERSION")}")
    implementation("androidx.compose.ui:ui-tooling-preview:${rootProject.property("COMPOSE_VERSION")}")
    implementation("androidx.compose.material3:material3:${rootProject.property("COMPOSE_MATERIAL3_VERSION")}")
    implementation("androidx.compose.material:material-icons-extended:${rootProject.property("COMPOSE_VERSION")}")
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:${rootProject.property("NAVIGATION_VERSION")}")
    
    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    
    // Hilt
    implementation("com.google.dagger:hilt-android:${rootProject.property("HILT_VERSION")}")
    kapt("com.google.dagger:hilt-compiler:${rootProject.property("HILT_VERSION")}")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    
    // CameraX
    implementation("androidx.camera:camera-core:${rootProject.property("CAMERAX_VERSION")}")
    implementation("androidx.camera:camera-camera2:${rootProject.property("CAMERAX_VERSION")}")
    implementation("androidx.camera:camera-lifecycle:${rootProject.property("CAMERAX_VERSION")}")
    implementation("androidx.camera:camera-view:${rootProject.property("CAMERAX_VERSION")}")
    
    // Testing
    testImplementation("junit:junit:${rootProject.property("JUNIT_VERSION")}")
    testImplementation("org.mockito:mockito-core:${rootProject.property("MOCKITO_VERSION")}")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    testImplementation("org.robolectric:robolectric:${rootProject.property("ROBOLECTRIC_VERSION")}")
    testImplementation("com.google.truth:truth:1.1.5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    // Generate real X.509 chains to test the PKI stage's real-validator path (Phase 6).
    testImplementation("org.bouncycastle:bcprov-jdk18on:1.84")
    testImplementation("org.bouncycastle:bcpkix-jdk18on:1.84")
    testImplementation("com.google.dagger:hilt-android-testing:${rootProject.property("HILT_VERSION")}")
    // Robolectric-hosted Compose render of the themed screens (re-skin smoke).
    // ui-test-junit4 provides createComposeRule(); ui-test-manifest registers
    // the stub ComponentActivity in the merged unit-test manifest.
    testImplementation("androidx.compose.ui:ui-test-junit4:${rootProject.property("COMPOSE_VERSION")}")
    testImplementation("androidx.compose.ui:ui-test-manifest:${rootProject.property("COMPOSE_VERSION")}")
    kaptTest("com.google.dagger:hilt-android-compiler:${rootProject.property("HILT_VERSION")}")
    
    // Android Testing
    androidTestImplementation("androidx.test.ext:junit:${rootProject.property("ANDROIDX_TEST_VERSION")}")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:${rootProject.property("COMPOSE_VERSION")}")
    androidTestImplementation("com.google.dagger:hilt-android-testing:${rootProject.property("HILT_VERSION")}")
    kaptAndroidTest("com.google.dagger:hilt-android-compiler:${rootProject.property("HILT_VERSION")}")
    
    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling:${rootProject.property("COMPOSE_VERSION")}")
    debugImplementation("androidx.compose.ui:ui-test-manifest:${rootProject.property("COMPOSE_VERSION")}")
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
        "android/**/*.*",
        "**/di/**",
        "**/*_HiltModules*",
        "**/*_Factory*",
        "**/*_MembersInjector*"
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
