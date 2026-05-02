#!/bin/bash

# JABAuth Diagnostic Framework - Module Setup Script
# Creates the multi-module Gradle project structure

set -e

echo "🚀 JABAuth Diagnostic Framework - Module Setup"
echo "=============================================="
echo ""

# Get the script directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

# Create module directories
echo "📁 Creating module directories..."
mkdir -p core/src/main/{java/com/jabauth/core,res}
mkdir -p core/src/test/java/com/jabauth/core

mkdir -p jabcode-sdk/src/main/{java/com/jabcode/sdk,res}
mkdir -p jabcode-sdk/src/test/java/com/jabcode/sdk
mkdir -p jabcode-sdk/src/androidTest/java/com/jabcode/sdk

mkdir -p jabauth-client/src/main/{java/com/jabauth/client,res}
mkdir -p jabauth-client/src/test/java/com/jabauth/client

mkdir -p diagnostic-engine/src/main/{java/com/jabauth/diagnostics,res}
mkdir -p diagnostic-engine/src/test/java/com/jabauth/diagnostics

mkdir -p ui-components/src/main/{java/com/jabauth/ui,res}
mkdir -p ui-components/src/test/java/com/jabauth/ui

mkdir -p diagnostic-app/src/main/{java/com/jabauth/app,res}
mkdir -p diagnostic-app/src/test/java/com/jabauth/app
mkdir -p diagnostic-app/src/androidTest/java/com/jabauth/app

echo "✅ Module directories created"
echo ""

# Create settings.gradle.kts
echo "📝 Creating settings.gradle.kts..."
cat > settings.gradle.kts << 'EOF'
rootProject.name = "jabauth-diagnostic-framework"

include(":library")           // Existing native JABCode wrapper
include(":testapp")           // Legacy scanner app (keep for now)

// New modular architecture
include(":core")
include(":jabcode-sdk")
include(":jabauth-client")
include(":diagnostic-engine")
include(":ui-components")
include(":diagnostic-app")
EOF

echo "✅ settings.gradle.kts created"
echo ""

# Create root build.gradle.kts
echo "📝 Creating root build.gradle.kts..."
cat > build.gradle.kts << 'EOF'
// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.7.3")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.20")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

// Shared versions
ext {
    set("minSdkVersion", 24)
    set("compileSdkVersion", 35)
    set("targetSdkVersion", 35)
    
    set("kotlinVersion", "1.9.20")
    set("coroutinesVersion", "1.7.3")
    
    set("androidxCoreVersion", "1.12.0")
    set("androidxAppCompatVersion", "1.7.0")
    set("materialVersion", "1.12.0")
    set("constraintLayoutVersion", "2.1.4")
    set("lifecycleVersion", "2.7.0")
    set("navigationVersion", "2.7.7")
    set("cameraXVersion", "1.4.0")
    
    set("retrofitVersion", "2.9.0")
    set("okhttpVersion", "4.12.0")
    set("gsonVersion", "2.10.1")
    
    set("junitVersion", "4.13.2")
    set("mockitoVersion", "5.8.0")
    set("robolectricVersion", "4.11.1")
    set("espressoVersion", "3.5.1")
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
EOF

echo "✅ Root build.gradle.kts created"
echo ""

# Create :core module build.gradle.kts
echo "📝 Creating :core/build.gradle.kts..."
cat > core/build.gradle.kts << 'EOF'
plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    namespace = "com.jabauth.core"
    compileSdk = rootProject.extra["compileSdkVersion"] as Int
    
    defaultConfig {
        minSdk = rootProject.extra["minSdkVersion"] as Int
        targetSdk = rootProject.extra["targetSdkVersion"] as Int
        
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // Kotlin
    api("org.jetbrains.kotlin:kotlin-stdlib:${rootProject.extra["kotlinVersion"]}")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:${rootProject.extra["coroutinesVersion"]}")
    
    // AndroidX Core
    api("androidx.core:core-ktx:${rootProject.extra["androidxCoreVersion"]}")
    
    // Network
    api("com.squareup.okhttp3:okhttp:${rootProject.extra["okhttpVersion"]}")
    api("com.google.code.gson:gson:${rootProject.extra["gsonVersion"]}")
    
    // Storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    
    // Testing
    testImplementation("junit:junit:${rootProject.extra["junitVersion"]}")
    testImplementation("org.mockito:mockito-core:${rootProject.extra["mockitoVersion"]}")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${rootProject.extra["coroutinesVersion"]}")
}
EOF

echo "✅ :core/build.gradle.kts created"
echo ""

# Create :jabcode-sdk module build.gradle.kts
echo "📝 Creating :jabcode-sdk/build.gradle.kts..."
cat > jabcode-sdk/build.gradle.kts << 'EOF'
plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    namespace = "com.jabcode.sdk"
    compileSdk = rootProject.extra["compileSdkVersion"] as Int
    
    defaultConfig {
        minSdk = rootProject.extra["minSdkVersion"] as Int
        targetSdk = rootProject.extra["targetSdkVersion"] as Int
        
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":library"))  // Native JABCode wrapper
    
    // CameraX
    api("androidx.camera:camera-core:${rootProject.extra["cameraXVersion"]}")
    api("androidx.camera:camera-camera2:${rootProject.extra["cameraXVersion"]}")
    api("androidx.camera:camera-lifecycle:${rootProject.extra["cameraXVersion"]}")
    api("androidx.camera:camera-view:${rootProject.extra["cameraXVersion"]}")
    
    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:${rootProject.extra["lifecycleVersion"]}")
    
    // Testing
    testImplementation("junit:junit:${rootProject.extra["junitVersion"]}")
    testImplementation("org.mockito:mockito-core:${rootProject.extra["mockitoVersion"]}")
    testImplementation("org.robolectric:robolectric:${rootProject.extra["robolectricVersion"]}")
    
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:${rootProject.extra["espressoVersion"]}")
}
EOF

echo "✅ :jabcode-sdk/build.gradle.kts created"
echo ""

# Create :jabauth-client module build.gradle.kts
echo "📝 Creating :jabauth-client/build.gradle.kts..."
cat > jabauth-client/build.gradle.kts << 'EOF'
plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    namespace = "com.jabauth.client"
    compileSdk = rootProject.extra["compileSdkVersion"] as Int
    
    defaultConfig {
        minSdk = rootProject.extra["minSdkVersion"] as Int
        targetSdk = rootProject.extra["targetSdkVersion"] as Int
        
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(project(":core"))
    
    // Retrofit
    api("com.squareup.retrofit2:retrofit:${rootProject.extra["retrofitVersion"]}")
    api("com.squareup.retrofit2:converter-gson:${rootProject.extra["retrofitVersion"]}")
    implementation("com.squareup.okhttp3:logging-interceptor:${rootProject.extra["okhttpVersion"]}")
    
    // Testing
    testImplementation("junit:junit:${rootProject.extra["junitVersion"]}")
    testImplementation("org.mockito:mockito-core:${rootProject.extra["mockitoVersion"]}")
    testImplementation("com.squareup.okhttp3:mockwebserver:${rootProject.extra["okhttpVersion"]}")
}
EOF

echo "✅ :jabauth-client/build.gradle.kts created"
echo ""

# Create :diagnostic-engine module build.gradle.kts
echo "📝 Creating :diagnostic-engine/build.gradle.kts..."
cat > diagnostic-engine/build.gradle.kts << 'EOF'
plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    namespace = "com.jabauth.diagnostics"
    compileSdk = rootProject.extra["compileSdkVersion"] as Int
    
    defaultConfig {
        minSdk = rootProject.extra["minSdkVersion"] as Int
        targetSdk = rootProject.extra["targetSdkVersion"] as Int
        
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":jabcode-sdk"))
    implementation(project(":jabauth-client"))
    
    // JSON
    implementation("org.json:json:20231013")
    
    // Testing framework (JUnit for test structure)
    api("junit:junit:${rootProject.extra["junitVersion"]}")
    
    // Testing
    testImplementation("org.mockito:mockito-core:${rootProject.extra["mockitoVersion"]}")
}
EOF

echo "✅ :diagnostic-engine/build.gradle.kts created"
echo ""

# Create :ui-components module build.gradle.kts
echo "📝 Creating :ui-components/build.gradle.kts..."
cat > ui-components/build.gradle.kts << 'EOF'
plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    namespace = "com.jabauth.ui"
    compileSdk = rootProject.extra["compileSdkVersion"] as Int
    
    defaultConfig {
        minSdk = rootProject.extra["minSdkVersion"] as Int
        targetSdk = rootProject.extra["targetSdkVersion"] as Int
        
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    
    buildFeatures {
        viewBinding = true
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":jabcode-sdk"))
    
    // Material Design
    api("com.google.android.material:material:${rootProject.extra["materialVersion"]}")
    api("androidx.constraintlayout:constraintlayout:${rootProject.extra["constraintLayoutVersion"]}")
    api("androidx.appcompat:appcompat:${rootProject.extra["androidxAppCompatVersion"]}")
    
    // Charts (optional)
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    
    // Testing
    testImplementation("junit:junit:${rootProject.extra["junitVersion"]}")
    testImplementation("org.robolectric:robolectric:${rootProject.extra["robolectricVersion"]}")
    
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:${rootProject.extra["espressoVersion"]}")
}
EOF

echo "✅ :ui-components/build.gradle.kts created"
echo ""

# Create :diagnostic-app module build.gradle.kts
echo "📝 Creating :diagnostic-app/build.gradle.kts..."
cat > diagnostic-app/build.gradle.kts << 'EOF'
plugins {
    id("com.android.application")
    id("kotlin-android")
}

android {
    namespace = "com.jabauth.app"
    compileSdk = rootProject.extra["compileSdkVersion"] as Int
    
    defaultConfig {
        applicationId = "com.jabauth.diagnostic"
        minSdk = rootProject.extra["minSdkVersion"] as Int
        targetSdk = rootProject.extra["targetSdkVersion"] as Int
        versionCode = 1
        versionName = "1.0.0"
        
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    buildFeatures {
        viewBinding = true
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":jabcode-sdk"))
    implementation(project(":jabauth-client"))
    implementation(project(":diagnostic-engine"))
    implementation(project(":ui-components"))
    
    // Navigation
    implementation("androidx.navigation:navigation-fragment-ktx:${rootProject.extra["navigationVersion"]}")
    implementation("androidx.navigation:navigation-ui-ktx:${rootProject.extra["navigationVersion"]}")
    
    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:${rootProject.extra["lifecycleVersion"]}")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:${rootProject.extra["lifecycleVersion"]}")
    
    // Testing
    testImplementation("junit:junit:${rootProject.extra["junitVersion"]}")
    
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:${rootProject.extra["espressoVersion"]}")
    androidTestImplementation("androidx.test:rules:1.5.0")
}
EOF

echo "✅ :diagnostic-app/build.gradle.kts created"
echo ""

# Create placeholder AndroidManifest.xml files
echo "📝 Creating AndroidManifest.xml files..."

for module in core jabcode-sdk jabauth-client diagnostic-engine ui-components; do
    cat > $module/src/main/AndroidManifest.xml << EOF
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
</manifest>
EOF
done

cat > diagnostic-app/src/main/AndroidManifest.xml << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.INTERNET" />
    
    <application
        android:allowBackup="true"
        android:label="JABAuth Diagnostics"
        android:theme="@style/Theme.Material3.DayNight.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
EOF

echo "✅ AndroidManifest.xml files created"
echo ""

# Create .gitignore for modules
echo "📝 Creating .gitignore..."
cat > .gitignore << 'EOF'
# Gradle
.gradle/
build/
*/build/

# Android Studio
.idea/
*.iml
local.properties

# Generated
generated/
EOF

echo "✅ .gitignore created"
echo ""

# Verify structure
echo "🔍 Verifying module structure..."
echo ""
echo "Module directories:"
for module in core jabcode-sdk jabauth-client diagnostic-engine ui-components diagnostic-app; do
    if [ -d "$module" ]; then
        echo "  ✅ $module"
    else
        echo "  ❌ $module (missing)"
    fi
done
echo ""

echo "=============================================="
echo "✨ Module setup complete!"
echo ""
echo "Next steps:"
echo "1. Review generated build.gradle.kts files"
echo "2. Run: ./gradlew build"
echo "3. Begin Week 1 implementation"
echo ""
echo "Documentation:"
echo "  - DIAGNOSTIC_FRAMEWORK_ARCHITECTURE.md"
echo "  - REFACTORING_ROADMAP.md"
echo ""
echo "Happy coding! 🚀"
