plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("jacoco")
}

android {
    namespace = "com.jabauth.diagnostic"
    compileSdk = rootProject.property("COMPILE_SDK").toString().toInt()
    
    defaultConfig {
        minSdk = rootProject.property("MIN_SDK").toString().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Framework dependencies
    implementation(project(":framework:core"))
    implementation(project(":framework:jabcode-sdk"))
    implementation(project(":framework:jabauth-client"))
    
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib:${rootProject.property("KOTLIN_VERSION")}")
    implementation("androidx.core:core-ktx:1.12.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Testing (JVM / Robolectric)
    testImplementation("junit:junit:${rootProject.property("JUNIT_VERSION")}")
    testImplementation("org.mockito:mockito-core:${rootProject.property("MOCKITO_VERSION")}")
    testImplementation("org.robolectric:robolectric:${rootProject.property("ROBOLECTRIC_VERSION")}")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("com.google.truth:truth:1.1.5")

    // Android instrumentation tests (on-device) — required for the
    // BenchmarkSuite concrete implementations, since they exercise the
    // native JABCode decoder which only loads on real Android devices.
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("com.google.truth:truth:1.1.5")
}
