# Phase 1: Setup & Design System

**Duration:** 3 days  
**Dependencies:** Framework modules complete  
**Status:** ⬜ Not Started

---

## Overview

Set up the diagnostic app module, navigation structure, and implement the JABAuth theme from design system prototypes.

**Coverage Target:** 80%+ (5 tests)

---

## Day 1: Project Setup

### **Module Configuration**

```kotlin
// :diagnostic-app/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("kotlin-kapt")
}

android {
    namespace = "com.jabauth.diagnostic"
    compileSdk = 35
    
    defaultConfig {
        applicationId = "com.jabauth.diagnostic"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
    
    buildFeatures {
        compose = true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.3"
    }
}

dependencies {
    // Framework modules
    implementation(project(":core"))
    implementation(project(":jabcode-sdk"))
    implementation(project(":jabauth-client"))
    implementation(project(":diagnostic-engine"))
    implementation(project(":ui-components"))
    
    // Compose
    implementation("androidx.compose.ui:ui:1.5.4")
    implementation("androidx.compose.material3:material3:1.1.2")
    implementation("androidx.compose.ui:ui-tooling-preview:1.5.4")
    implementation("androidx.activity:activity-compose:1.8.0")
    implementation("androidx.navigation:navigation-compose:2.7.5")
    
    // Hilt
    implementation("com.google.dagger:hilt-android:2.48")
    kapt("com.google.dagger:hilt-compiler:2.48")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.5.4")
    debugImplementation("androidx.compose.ui:ui-tooling:1.5.4")
}
```

---

## Day 2: Navigation & Theme

### **Navigation Graph**

```kotlin
@Composable
fun DiagnosticNavHost(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = "dashboard"
    ) {
        composable("dashboard") {
            DashboardScreen(
                onNavigateToScanner = { navController.navigate("scanner") },
                onNavigateToSettings = { navController.navigate("settings") }
            )
        }
        
        composable("scanner") {
            ScannerScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDashboard = { navController.navigate("dashboard") }
            )
        }
        
        composable("settings") {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
```

### **Theme Implementation**

```kotlin
@Composable
fun JABAuthTheme(
    context: AppContext = AppContext.Healthcare,
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = context.primaryColor(),
            primaryContainer = context.primaryDim(),
            background = Color(0xFF0B1120),
            surface = Color(0xFF1A2438),
            onPrimary = Color(0xFF0B1120),
            onBackground = Color(0xFFE8F4F8),
            onSurface = Color(0xFFE8F4F8)
        )
    } else {
        lightColorScheme(/* light theme colors */)
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = JABAuthTypography,
        content = content
    )
}
```

---

## Day 3: Testing

### **Navigation Tests**

```kotlin
@Test
fun navigateFromDashboardToScanner() {
    val navController = TestNavHostController(ApplicationProvider.getApplicationContext())
    
    composeTestRule.setContent {
        navController.navigatorProvider.addNavigator(ComposeNavigator())
        DiagnosticNavHost(navController)
    }
    
    composeTestRule.onNodeWithText("Scan JABCode").performClick()
    assertEquals("scanner", navController.currentBackStackEntry?.destination?.route)
}
```

---

**Test-Coverage-Update:**
```bash
./gradlew :diagnostic-app:test jacocoTestReport
# Expected: 5 tests pass, 80%+ coverage
```

---

**Last Updated:** 2026-05-02
