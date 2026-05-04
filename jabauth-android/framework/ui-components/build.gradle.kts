plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("jacoco")
}

android {
    namespace = "com.jabauth.ui"
    compileSdk = rootProject.property("COMPILE_SDK").toString().toInt()
    
    configurations.all {
        exclude(group = "com.google.guava", module = "listenablefuture")
    }
    
    defaultConfig {
        minSdk = rootProject.property("MIN_SDK").toString().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    
    buildFeatures {
        compose = true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
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
    
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib:${rootProject.property("KOTLIN_VERSION")}")
    
    // Compose
    implementation("androidx.compose.ui:ui:${rootProject.property("COMPOSE_VERSION")}")
    implementation("androidx.compose.material3:material3:${rootProject.property("COMPOSE_MATERIAL3_VERSION")}")
    implementation("androidx.compose.ui:ui-tooling-preview:${rootProject.property("COMPOSE_VERSION")}")
    implementation("androidx.activity:activity-compose:1.8.0")
    
    // CameraX
    implementation("androidx.camera:camera-core:${rootProject.property("CAMERAX_VERSION")}")
    implementation("androidx.camera:camera-camera2:${rootProject.property("CAMERAX_VERSION")}")
    implementation("androidx.camera:camera-lifecycle:${rootProject.property("CAMERAX_VERSION")}")
    implementation("androidx.camera:camera-view:${rootProject.property("CAMERAX_VERSION")}")
    
    // Testing
    testImplementation("junit:junit:${rootProject.property("JUNIT_VERSION")}")
    testImplementation("org.robolectric:robolectric:${rootProject.property("ROBOLECTRIC_VERSION")}")
    testImplementation("androidx.compose.ui:ui-test-junit4:${rootProject.property("COMPOSE_VERSION")}")
    testImplementation("com.google.truth:truth:1.1.5")
    debugImplementation("androidx.compose.ui:ui-tooling:${rootProject.property("COMPOSE_VERSION")}")
    debugImplementation("androidx.compose.ui:ui-test-manifest:${rootProject.property("COMPOSE_VERSION")}")
    
    // Instrumented Testing
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:${rootProject.property("COMPOSE_VERSION")}")
    androidTestImplementation("com.google.truth:truth:1.1.5")
}
