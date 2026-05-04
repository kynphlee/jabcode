// JABAuth Android - Multi-Module Monorepo
rootProject.name = "jabauth-android"

// Enable typesafe project accessors
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// Plugin management
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// Framework modules
include(":framework:core")
include(":framework:jabcode-sdk")
include(":framework:jabauth-client")
include(":framework:diagnostic-engine")
include(":framework:ui-components")

// Application modules
include(":diagnostic-app")

// Dependency resolution management
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        mavenLocal()  // For locally published framework artifacts
    }
}
