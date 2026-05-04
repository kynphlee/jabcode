plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Hilt temporarily disabled due to Gradle 9.0 + kapt compatibility issue
    // id("com.google.dagger.hilt.android")
    // kotlin("kapt")
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
        versionName = "1.0.0-alpha"
        
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
    }
    
    buildFeatures {
        compose = true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
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
    // Framework modules
    implementation(project(":framework:core"))
    implementation(project(":framework:jabcode-sdk"))
    implementation(project(":framework:jabauth-client"))
    implementation(project(":framework:diagnostic-engine"))
    implementation(project(":framework:ui-components"))
    
    // Compose
    implementation("androidx.compose.ui:ui:${rootProject.property("COMPOSE_VERSION")}")
    implementation("androidx.compose.material3:material3:${rootProject.property("COMPOSE_MATERIAL3_VERSION")}")
    implementation("androidx.compose.ui:ui-tooling-preview:${rootProject.property("COMPOSE_VERSION")}")
    implementation("androidx.activity:activity-compose:1.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.5")
    
    // Hilt (temporarily disabled due to Gradle 9.0 + kapt compatibility)
    // implementation("com.google.dagger:hilt-android:${rootProject.property("HILT_VERSION")}")
    // kapt("com.google.dagger:hilt-compiler:${rootProject.property("HILT_VERSION")}")
    // implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Testing
    testImplementation("junit:junit:${rootProject.property("JUNIT_VERSION")}")
    testImplementation("org.mockito:mockito-core:${rootProject.property("MOCKITO_VERSION")}")
    testImplementation("com.google.truth:truth:1.1.5")
    
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:${rootProject.property("COMPOSE_VERSION")}")
    androidTestImplementation("androidx.navigation:navigation-testing:2.7.5")
    // androidTestImplementation("com.google.dagger:hilt-android-testing:${rootProject.property("HILT_VERSION")}")
    // kaptAndroidTest("com.google.dagger:hilt-compiler:${rootProject.property("HILT_VERSION")}")
    
    debugImplementation("androidx.compose.ui:ui-tooling:${rootProject.property("COMPOSE_VERSION")}")
    debugImplementation("androidx.compose.ui:ui-test-manifest:${rootProject.property("COMPOSE_VERSION")}")
}
