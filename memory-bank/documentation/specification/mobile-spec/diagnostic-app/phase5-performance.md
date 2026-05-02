# Phase 5: Performance & Polish

**Duration:** 3 days  
**Dependencies:** Phase 4 complete  
**Status:** ⬜ Not Started

---

## Overview

Optimize performance, ensure accessibility, and apply final polish before release.

**Coverage Target:** 80%+ final (10 tests: 3 performance + 2 accessibility + 5 validation)

---

## Day 1: Performance Profiling & Optimization

### **Macrobenchmark Tests**

```kotlin
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()
    
    @Test
    fun coldStartup() = benchmarkRule.measureRepeated(
        packageName = "com.jabauth.diagnostic",
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD
    ) {
        pressHome()
        startActivityAndWait()
        
        // Target: ≤ 2000ms cold start
    }
    
    @Test
    fun warmStartup() = benchmarkRule.measureRepeated(
        packageName = "com.jabauth.diagnostic",
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM
    ) {
        pressHome()
        startActivityAndWait()
        
        // Target: ≤ 500ms warm start
    }
    
    @Test
    fun scannerFrameRate() = benchmarkRule.measureRepeated(
        packageName = "com.jabauth.diagnostic",
        metrics = listOf(FrameTimingMetric()),
        iterations = 5
    ) {
        startActivityAndWait()
        
        // Navigate to scanner
        device.findObject(By.text("Scan JABCode")).click()
        device.waitForIdle()
        
        // Measure frame rate during scanning
        Thread.sleep(5000)
        
        // Target: ≥ 30fps
    }
}
```

### **Memory Profiling**

```kotlin
@Test
fun memoryUsage_staysBelowLimit() {
    // 1. Launch app
    val scenario = launchActivity<MainActivity>()
    
    // 2. Navigate through all screens
    scenario.onActivity { activity ->
        // Trigger GC
        Runtime.getRuntime().gc()
        Thread.sleep(1000)
        
        // Measure memory
        val runtime = Runtime.getRuntime()
        val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
        
        // Target: ≤ 50MB
        assertTrue("Memory usage $usedMemory MB exceeds 50MB", usedMemory <= 50)
    }
}
```

---

## Day 2: Accessibility Audit

### **Accessibility Tests**

```kotlin
@Test
fun dashboard_hasAccessibleContentDescriptions() {
    composeTestRule.setContent {
        DashboardScreen()
    }
    
    // Verify all interactive elements have labels
    composeTestRule.onNode(hasClickAction()).assertAll(
        hasContentDescription()
    )
}

@Test
fun scanner_worksWith_TalkBack() {
    // Enable TalkBack simulation
    UiAutomatorUtils.enableTalkBack()
    
    composeTestRule.setContent {
        ScannerScreen()
    }
    
    // Verify all UI elements are announced
    composeTestRule.onNode(hasTestTag("scan-target"))
        .assertHasClickLabel("Scan JABCode")
        
    composeTestRule.onNode(hasTestTag("torch-toggle"))
        .assertHasClickLabel("Toggle flashlight")
}
```

### **Contrast Ratio Verification**

```kotlin
@Test
fun verifyContrastRatios() {
    val theme = JABAuthTheme.colors
    
    // Primary on Background
    val ratio1 = calculateContrastRatio(theme.primary, theme.background)
    assertTrue("Primary/Background contrast $ratio1 < 4.5", ratio1 >= 4.5)
    
    // OnSurface on Surface
    val ratio2 = calculateContrastRatio(theme.onSurface, theme.surface)
    assertTrue("OnSurface/Surface contrast $ratio2 < 4.5", ratio2 >= 4.5)
}

private fun calculateContrastRatio(fg: Color, bg: Color): Double {
    val l1 = relativeLuminance(fg)
    val l2 = relativeLuminance(bg)
    return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
}
```

---

## Day 3: Final Validation

### **UI Comparison Tests**

```kotlin
@Test
fun dashboard_matchesPrototype() {
    // Take screenshot
    composeTestRule.setContent {
        DashboardScreen()
    }
    
    val screenshot = composeTestRule.onRoot().captureToImage()
    
    // Compare to reference (diagnostic-dashboard.html screenshot)
    val reference = loadReferenceImage("dashboard_reference.png")
    
    val similarity = compareImages(screenshot, reference)
    assertTrue("UI similarity $similarity% < 95%", similarity >= 95.0)
}
```

### **Performance Validation**

```bash
# Run all benchmarks
./gradlew :diagnostic-app:connectedBenchmarkAndroidTest

# Expected Results:
# - Cold start: ≤ 2s ✓
# - Warm start: ≤ 500ms ✓
# - Scanner FPS: ≥ 30 ✓
# - Memory: ≤ 50MB ✓
```

### **Release Build**

```bash
# Generate signed release APK
./gradlew :diagnostic-app:assembleRelease

# Verify APK size
ls -lh diagnostic-app/build/outputs/apk/release/
# Expected: ≤ 15MB
```

---

## Final Checklist

- [ ] All 84 tests pass
- [ ] 80%+ coverage achieved
- [ ] No critical/high bugs
- [ ] Performance targets met
- [ ] Accessibility score ≥ 90%
- [ ] UI matches prototypes
- [ ] APK size ≤ 15MB
- [ ] Release notes written
- [ ] Tag: v1.0.0

---

**Last Updated:** 2026-05-02  
**Status:** Ready for Release 🚀
