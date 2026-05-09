# Phase 3 Device Testing & Validation

**Date:** 2026-05-09  
**Status:** Ready for Testing  
**Build:** diagnostic-app:assembleDebug ✅

---

## Overview

Comprehensive validation of Phase 3 integration features on physical Android device. All features implemented and building successfully - ready for real-world testing.

**Integrated Features:**
1. Scanner camera integration with runtime permissions
2. Settings persistence with DataStore
3. Scanner-settings live integration (timeout, interval)
4. Debug logging toggle
5. Auto-focus camera control
6. Preferred color mode validation

---

## Pre-Testing Setup

### 1. Install Latest Build
```bash
cd /mnt/b34628fa-d41e-4c37-8caf-f06a6ecbb1ae/projects/practice/barcode/jabcode/jabauth-android
./gradlew :diagnostic-app:installDebug
```

### 2. Enable Debug Logging
- Open app → Settings screen
- Toggle "Debug Logging" ON
- Enables verbose diagnostic output

### 3. Connect ADB for Log Monitoring
```bash
# Monitor all diagnostic logs
adb logcat | grep -E "ScannerViewModel|DiagnosticLogger|Camera2"

# Filter for specific component
adb logcat | grep "ScannerViewModel"
```

### 4. Prepare Test JABCodes
Need various color modes for testing:
- 4-color JABCode
- 8-color JABCode
- 16-color JABCode
- 32-color JABCode
- 64-color JABCode
- 128-color JABCode

---

## Test Plan

### Test 1: Camera Permission Handling
**Objective:** Verify runtime permission request and camera activation

**Steps:**
1. Fresh install (clear app data if needed)
2. Navigate to Scanner screen
3. Observe permission dialog

**Expected Results:**
- ✅ Permission dialog appears on first access
- ✅ Camera preview activates after granting permission
- ✅ No crashes or errors
- ✅ App handles denied permission gracefully

**Log Validation:**
```
Camera2Controller: Camera2 preview started: AF=ON, AE=ON, AWB=AUTO
```

---

### Test 2: Settings Persistence (DataStore)
**Objective:** Verify settings persist across app restarts

**Steps:**
1. Open Settings screen
2. Change all settings:
   - Decode Timeout: 200ms → 500ms
   - Analyze Interval: 500ms → 1000ms
   - Auto-Focus: ON → OFF
   - Debug Logging: OFF → ON
   - Preferred Color Mode: Auto → 16-color
3. Force-close app (swipe away from recents)
4. Reopen app
5. Navigate to Settings screen

**Expected Results:**
- ✅ All settings retained at modified values
- ✅ No reset to defaults
- ✅ DataStore read successful

**Log Validation:**
```
SettingsRepository: Settings loaded from DataStore
ScannerViewModel: Settings updated: timeout=500ms, interval=1000ms, autoFocus=false, colorMode=16-color, debug=true
```

---

### Test 3: Scanner-Settings Live Integration
**Objective:** Verify scanner reacts to settings changes without restart

**Steps:**
1. Enable Debug Logging
2. Open Scanner screen
3. Start scanning (point at JABCode)
4. Switch to Settings screen (keep Scanner in back stack)
5. Change Decode Timeout: 200ms → 1000ms
6. Return to Scanner screen
7. Continue scanning

**Expected Results:**
- ✅ Settings flow emits update
- ✅ Analyzer recreated with new timeout
- ✅ Scanner continues working with new settings
- ✅ No camera restart required

**Log Validation:**
```
ScannerViewModel: Settings updated: timeout=1000ms, ...
ScannerViewModel: Creating analyzer: timeout=1000ms, interval=500ms
```

---

### Test 4: Debug Logging Toggle
**Objective:** Verify conditional logging based on user preference

**Test 4A: Debug Logging ON**
1. Settings → Debug Logging: ON
2. Navigate to Scanner
3. Scan a JABCode
4. Monitor logcat

**Expected Results:**
- ✅ Verbose logs visible:
  - "Settings updated: ..."
  - "Creating analyzer: ..."
  - "Decode SUCCESS: data='...', colorMode=..."
  - "Color mode validated: ..."

**Test 4B: Debug Logging OFF**
1. Settings → Debug Logging: OFF
2. Navigate to Scanner
3. Scan a JABCode
4. Monitor logcat

**Expected Results:**
- ✅ Diagnostic logs suppressed
- ✅ Only INFO-level logs visible
- ✅ No performance impact from disabled logging

---

### Test 5: Auto-Focus Camera Control
**Objective:** Verify auto-focus toggle affects camera behavior

**Test 5A: Auto-Focus ON (Default)**
1. Settings → Auto-Focus: ON
2. Navigate to Scanner
3. Point camera at JABCode at varying distances
4. Observe focus behavior

**Expected Results:**
- ✅ Camera continuously adjusts focus
- ✅ Sharp focus achieved automatically
- ✅ JABCode readable at multiple distances

**Log Validation:**
```
Camera2Controller: Auto-focus enabled
Camera2Preview: AF=ON, AE=ON, AWB=AUTO
```

**Test 5B: Auto-Focus OFF**
1. Settings → Auto-Focus: OFF
2. Navigate to Scanner (or observe live update)
3. Point camera at JABCode

**Expected Results:**
- ✅ Camera uses fixed focus
- ✅ No continuous focus hunting
- ✅ May require specific distance for sharp image

**Log Validation:**
```
Camera2Controller: Auto-focus disabled
Camera2Preview: AF=OFF, AE=ON, AWB=AUTO
```

**Test 5C: Live Toggle (No Restart)**
1. Keep Scanner screen open
2. Switch to Settings
3. Toggle Auto-Focus: ON → OFF
4. Return to Scanner

**Expected Results:**
- ✅ Focus mode changes immediately
- ✅ No camera restart
- ✅ Capture request updated via `setRepeatingRequest`

---

### Test 6: Preferred Color Mode Validation
**Objective:** Verify color mode preference logging and validation

**Test 6A: Auto-Detect Mode (null)**
1. Settings → Preferred Color Mode: Auto-detect
2. Navigate to Scanner
3. Scan JABCodes of various color modes (4, 8, 16, 32, 64, 128)

**Expected Results:**
- ✅ All color modes decode successfully
- ✅ No validation performed
- ✅ Logs show: "colorMode=auto-detect"

**Test 6B: Specific Mode Match**
1. Settings → Preferred Color Mode: 16-color
2. Scan an actual 16-color JABCode
3. Monitor logcat

**Expected Results:**
- ✅ Decode succeeds
- ✅ Log: "Color mode validated: 16-color matches preference"

**Test 6C: Mode Mismatch**
1. Settings → Preferred Color Mode: 64-color
2. Scan an 8-color JABCode
3. Monitor logcat

**Expected Results:**
- ✅ Decode succeeds (auto-detect works)
- ✅ Log: "Color mode mismatch: expected 64-color, decoded 8-color (auto-detect found different mode)"
- ✅ Result displayed correctly despite mismatch

---

### Test 7: Performance Validation
**Objective:** Measure decode performance across settings

**Test 7A: Timeout Impact**
Test configurations:
- 200ms timeout (default)
- 500ms timeout
- 1000ms timeout

**Methodology:**
1. Set timeout value
2. Scan same JABCode 10 times
3. Record `decodeTimeMs` from logs
4. Calculate average

**Expected Results:**
- ✅ Timeout provides upper bound
- ✅ Successful decodes complete faster than timeout
- ✅ Failed decodes respect timeout limit

**Test 7B: Analyze Interval Impact**
Test configurations:
- 100ms interval (fast)
- 500ms interval (default)
- 1000ms interval (slow)

**Methodology:**
1. Enable Debug Logging
2. Set interval value
3. Observe scan attempt frequency in logs
4. Measure battery/CPU impact (Settings → Battery)

**Expected Results:**
- ✅ 100ms: High frame rate, more CPU usage
- ✅ 500ms: Balanced performance
- ✅ 1000ms: Lower CPU, slower detection

**Test 7C: Color Mode Performance**
Scan each color mode and record decode times:

| Color Mode | Decode Time (avg) | Notes |
|------------|-------------------|-------|
| 4-color    | ___ ms            |       |
| 8-color    | ___ ms            |       |
| 16-color   | ___ ms            |       |
| 32-color   | ___ ms            |       |
| 64-color   | ___ ms            |       |
| 128-color  | ___ ms            |       |

**Expected Pattern:**
- Higher color modes may decode slower (more palette complexity)
- All modes should complete within timeout

---

### Test 8: Settings Persistence Edge Cases
**Objective:** Verify DataStore handles edge cases

**Test 8A: App Crash Recovery**
1. Set custom values in Settings
2. Force crash app: `adb shell am crash com.jabauth.diagnostic`
3. Relaunch app
4. Check Settings

**Expected Results:**
- ✅ Settings retained
- ✅ No corruption

**Test 8B: Clear Data**
1. Set custom values
2. Clear app data: Settings → Apps → Diagnostic App → Clear Data
3. Relaunch app
4. Check Settings

**Expected Results:**
- ✅ Settings reset to defaults
- ✅ No crashes on first read

**Test 8C: Rapid Changes**
1. Quickly toggle settings on/off multiple times
2. Switch between screens rapidly
3. Monitor for crashes

**Expected Results:**
- ✅ No race conditions
- ✅ Latest value persists
- ✅ Flow updates correctly

---

### Test 9: Integration Stability
**Objective:** Verify all features work together without conflicts

**Stress Test Scenario:**
1. Enable Debug Logging
2. Set Preferred Color Mode: 32-color
3. Set Auto-Focus: OFF
4. Set Decode Timeout: 1000ms
5. Set Analyze Interval: 200ms
6. Scan various JABCodes for 5 minutes
7. Toggle settings multiple times during scanning
8. Switch between screens frequently

**Expected Results:**
- ✅ No crashes
- ✅ No memory leaks
- ✅ Settings changes apply correctly
- ✅ Camera remains stable
- ✅ Decode success rate consistent

---

### Test 10: Debug Logging Output Verification
**Objective:** Verify all logging statements work correctly

**Complete Log Sequence (Debug ON):**
```
1. App Launch:
   SettingsRepository: Settings loaded from DataStore

2. Settings Change:
   ScannerViewModel: Settings updated: timeout=500ms, interval=1000ms, autoFocus=false, colorMode=16-color, debug=true
   ScannerViewModel: Creating analyzer: timeout=500ms, interval=1000ms

3. Auto-Focus Toggle:
   Camera2Controller: Auto-focus disabled
   Camera2Preview: AF=OFF, AE=ON, AWB=AUTO

4. Scan Success:
   ScannerViewModel: Decode SUCCESS: data='Hello World', colorMode=COLOR_8, decodeTime=45ms
   ScannerViewModel: Color mode mismatch: expected 16-color, decoded 8-color (auto-detect found different mode)

5. Scan Failure:
   ScannerViewModel: Decode FAILURE: Timeout exceeded
```

**Verify:**
- ✅ All log statements present
- ✅ Correct log levels (DEBUG, INFO)
- ✅ Formatting readable
- ✅ Timestamps accurate

---

## Success Criteria

### Critical (Must Pass)
- [ ] Camera permission handling works
- [ ] Settings persist across app restarts
- [ ] Scanner uses updated settings without restart
- [ ] Auto-focus toggle affects camera behavior
- [ ] Debug logging can be disabled
- [ ] All 6 color modes decode successfully

### Important (Should Pass)
- [ ] Color mode validation logs correctly
- [ ] Performance within acceptable range (<200ms typical)
- [ ] No crashes during stress testing
- [ ] Battery usage reasonable
- [ ] Settings flow reactive to changes

### Nice-to-Have (May Pass)
- [ ] Auto-focus improves scan success rate
- [ ] Preferred color mode helps QA workflow
- [ ] Debug logging aids troubleshooting
- [ ] Analyze interval optimizes battery vs performance

---

## Issue Reporting Template

If issues found, document using this template:

```
## Issue: [Brief Description]

**Test:** [Test number and name]
**Device:** [Model, Android version]
**Build:** [Git commit hash]

**Steps to Reproduce:**
1. 
2. 
3. 

**Expected Result:**
...

**Actual Result:**
...

**Logs:**
```
[Paste relevant logcat output]
```

**Screenshots:**
[Attach if applicable]

**Severity:** Critical / High / Medium / Low
```

---

## Device Information Template

Record device specs for performance reference:

```
**Device:** [e.g., Samsung Galaxy S23 Ultra]
**Model:** [e.g., SM-S918U]
**Android Version:** [e.g., Android 14]
**Camera:** [e.g., 200MP main, OIS, PDAF]
**Screen:** [e.g., 6.8" AMOLED, 120Hz]
**Tested Date:** 2026-05-09
```

---

## Post-Testing Actions

After completing all tests:

1. **Update Progress Narrative:**
   - Mark testing tasks complete
   - Document any issues found
   - Record performance metrics

2. **Create Test Report:**
   - Summary of pass/fail rates
   - Performance benchmarks
   - Device-specific notes

3. **Address Issues:**
   - File bugs for failures
   - Prioritize fixes
   - Re-test after fixes

4. **Update Documentation:**
   - Add known device limitations
   - Document optimal settings
   - Update troubleshooting guide

---

**JARVIS**  
*Testing Coordinator*  
*Created: 2026-05-09 14:25 EDT*
