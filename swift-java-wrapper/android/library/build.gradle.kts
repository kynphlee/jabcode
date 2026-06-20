plugins {
    id("com.android.library") version "8.7.3"
}

android {
    namespace = "com.jabcode.mobile"
    compileSdk = 35

    // Pin the NDK so it does not float to AGP's default (android-native-build.yml
    // caches the NDK keyed on this version). A floating version re-downloads each
    // clean run, and a corrupt sdkmanager download is a CI flake surface.
    ndkVersion = "27.0.12077973"

    defaultConfig {
        minSdk = 24
        
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
        
        externalNativeBuild {
            cmake {
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
        }
    }
    
    externalNativeBuild {
        cmake {
            path = file("../../CMakeLists.txt")
            version = "3.22.1"
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.annotation:annotation:1.7.1")
}
