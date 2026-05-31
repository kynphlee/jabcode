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
        }
        release {
            // Production builds: verbose logging defaults OFF.
            buildConfigField("boolean", "DEFAULT_DEBUG_LOGGING_ENABLED", "false")
            // Haptic-on-decode-success defaults ON — standard scan UX.
            buildConfigField("boolean", "DEFAULT_HAPTIC_ENABLED", "true")
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
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
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
    testImplementation("com.google.dagger:hilt-android-testing:${rootProject.property("HILT_VERSION")}")
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
