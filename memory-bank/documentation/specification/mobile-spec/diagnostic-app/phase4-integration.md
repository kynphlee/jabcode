# Phase 4: Integration & End-to-End Testing

**Duration:** 4 days  
**Dependencies:** Phase 2 & 3 complete  
**Status:** ⬜ Not Started

---

## Overview

Integrate all screens, implement settings, set up dependency injection, and create comprehensive E2E test suite.

**Coverage Target:** 80%+ overall (25 tests: 5 unit + 5 DI + 15 E2E)

---

## Day 1: Settings Screen

### **Implementation**

```kotlin
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val selectedColorMode by viewModel.colorMode.collectAsState()
    val selectedEccLevel by viewModel.eccLevel.collectAsState()
    
    Column(modifier = Modifier.padding(16.dp)) {
        // Color Mode Preference
        PreferenceGroup(title = "Scan Settings") {
            DropdownPreference(
                title = "Color Mode",
                value = selectedColorMode,
                options = listOf(4, 8, 16, 32, 64, 128),
                onValueChange = { viewModel.setColorMode(it) }
            )
            
            SliderPreference(
                title = "ECC Level",
                value = selectedEccLevel,
                valueRange = 0..7,
                onValueChange = { viewModel.setEccLevel(it) }
            )
        }
        
        // Calibration Section
        PreferenceGroup(title = "Calibration") {
            SwitchPreference(
                title = "Use Calibration Profile",
                checked = viewModel.useCalibration.collectAsState().value,
                onCheckedChange = { viewModel.setUseCalibration(it) }
            )
        }
        
        // Diagnostics Section
        PreferenceGroup(title = "Diagnostics") {
            ActionPreference(
                title = "Run Benchmark Suite",
                onClick = { viewModel.runBenchmarks() }
            )
            
            ActionPreference(
                title = "Generate Bug Report",
                onClick = { viewModel.generateBugReport() }
            )
        }
    }
}
```

---

## Day 2: Dependency Injection

### **Hilt Modules**

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideSecureStorage(
        @ApplicationContext context: Context
    ): SecureStorage {
        return StorageFactory.create(context)
    }
    
    @Provides
    @Singleton
    fun provideLogger(): Logger {
        return AndroidLogger()
    }
    
    @Provides
    @Singleton
    fun provideJABCodeEncoder(
        logger: Logger
    ): JABCodeEncoder {
        return JABCodeEncoder(logger)
    }
    
    @Provides
    @Singleton
    fun provideJABCodeDecoder(
        logger: Logger
    ): JABCodeDecoder {
        return JABCodeDecoder(logger)
    }
    
    @Provides
    @Singleton
    fun provideCertificateValidator(): CertificateValidator {
        return CertificateValidatorImpl()
    }
    
    @Provides
    @Singleton
    fun provideJWTParser(): JWTParser {
        return JWTParserImpl()
    }
}
```

---

## Day 3-4: End-to-End Tests

### **User Flow Tests**

```kotlin
@Test
fun fullScanFlow_successfulAuthentication() {
    // 1. Launch app
    composeTestRule.setContent {
        JABAuthTheme {
            DiagnosticNavHost()
        }
    }
    
    // 2. Navigate to scanner
    composeTestRule.onNodeWithText("Scan JABCode").performClick()
    
    // 3. Wait for camera
    composeTestRule.waitUntil(5000) {
        composeTestRule.onNode(hasTestTag("camera-preview")).isDisplayed()
    }
    
    // 4. Simulate scan detection
    // (inject mock JABCode image into analyzer)
    
    // 5. Verify result panel appears
    composeTestRule.onNode(hasText("Authentication Valid")).assertIsDisplayed()
    
    // 6. Verify validation badges
    composeTestRule.onNode(hasText("PKI Valid")).assertIsDisplayed()
    composeTestRule.onNode(hasText("JWT Valid")).assertIsDisplayed()
    
    // 7. Accept result
    composeTestRule.onNode(hasText("Accept")).performClick()
    
    // 8. Verify return to dashboard
    composeTestRule.onNode(hasTestTag("dashboard")).assertIsDisplayed()
}

@Test
fun settingsFlow_persistsPreferences() {
    // 1. Navigate to settings
    composeTestRule.onNodeWithContentDescription("Settings").performClick()
    
    // 2. Change color mode
    composeTestRule.onNode(hasText("Color Mode")).performClick()
    composeTestRule.onNode(hasText("16")).performClick()
    
    // 3. Restart app (simulate)
    composeTestRule.activityRule.scenario.recreate()
    
    // 4. Verify preference persisted
    composeTestRule.onNode(hasText("16")).assertIsDisplayed()
}

@Test
fun errorHandling_retryFlow() {
    // 1. Scan invalid JABCode
    // 2. Verify error message
    // 3. Click "Scan Again"
    // 4. Verify returns to scanning state
}
```

---

## Test-Coverage-Update

```bash
# Run all tests
./gradlew :diagnostic-app:clean test connectedAndroidTest

# Generate coverage
./gradlew :diagnostic-app:jacocoTestReport

# Expected: 25 tests pass, 80%+ overall coverage
```

---

**Last Updated:** 2026-05-02
