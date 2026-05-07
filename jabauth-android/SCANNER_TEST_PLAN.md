# Scanner Testing Plan - Phase 3 Day 5
**Date:** 2026-05-07  
**Build:** diagnostic-app-debug.apk  
**Device:** SM-S938U (Samsung Galaxy)

---

## Test Status Tracker

### Critical Path Tests
- [ ] **T1:** App launches without crash
- [ ] **T2:** Navigate to Scanner tab
- [ ] **T3:** Camera permission granted
- [ ] **T4:** Camera preview displays
- [ ] **T5:** Quality metrics update
- [ ] **T6:** Torch toggle works
- [ ] **T7:** Mock success button works
- [ ] **T8:** Mock failure button works
- [ ] **T9:** Result panel interactions
- [ ] **T10:** No memory leaks (extended use)

---

## Test Procedures

### T1: App Launch ✓
**Steps:**
1. Force stop app: `adb shell am force-stop com.jabauth.diagnostic`
2. Launch app: `adb shell am start -n com.jabauth.diagnostic/.MainActivity`
3. Verify dashboard appears

**Expected:** App launches to dashboard without crash

---

### T2: Navigate to Scanner Tab
**Steps:**
1. Tap "Scanner" icon in bottom navigation bar
2. Observe screen transition

**Expected:** 
- Smooth transition animation
- No freeze or lag
- Camera permission dialog appears (first time only)

**Known Issue Fixed:** Camera buffer overflow (now throttled to 2 FPS with 200ms timeout)

---

### T3: Camera Permission
**Steps:**
1. When permission dialog appears, tap "Allow"
2. Observe transition

**Expected:**
- Permission granted immediately
- Camera preview starts
- No app crash

---

### T4: Camera Preview
**Steps:**
1. After permission granted, observe camera view
2. Move phone around
3. Point at different objects/lighting

**Expected:**
- Live camera feed displays smoothly
- No lag or stutter
- Frame rate stable (~30 FPS)
- Green scan target overlay animates

---

### T5: Quality Metrics
**Steps:**
1. Watch bottom overlay while scanning
2. Move to bright area → observe "Brightness" value
3. Move close/far from object → observe "Focus" value
4. Point at high-contrast scene → observe "Contrast" value

**Expected:**
- Metrics update in real-time
- Values change appropriately with scene
- No crashes during metric calculation

**Current Implementation:**
- Brightness: 0.0-1.0 (normalized grayscale mean)
- Focus: Laplacian variance (higher = sharper)
- Contrast: Grayscale std dev

---

### T6: Torch Toggle
**Steps:**
1. Tap "Torch Off" button (top right)
2. Verify flashlight turns ON
3. Button changes to "Torch On"
4. Tap "Torch On" button
5. Verify flashlight turns OFF

**Expected:**
- Instant response
- Flashlight works correctly
- Button state updates
- Works during active scanning

---

### T7: Mock Success Button
**Steps:**
1. Tap "Test Success" button (temporary debug control)
2. Observe result panel slide up

**Expected:**
- Result panel appears with animation
- Shows "Authentication Valid"
- Green validation badges
- Certificate details visible
- JWT claims displayed
- "Accept" and "Scan Again" buttons present

**Note:** This simulates successful JABCode decode + JWT validation

---

### T8: Mock Failure Button
**Steps:**
1. Dismiss previous result if shown
2. Tap "Test Failure" button
3. Observe result panel

**Expected:**
- Result panel appears
- Shows "Authentication Failed"
- Red validation badges
- Error messages displayed
- "Retry" button (not "Accept")

---

### T9: Result Panel Interactions
**Steps:**
1. Trigger success result
2. Test each interaction:
   - Tap "Accept" → Panel dismisses
   - Trigger again, tap "Scan Again" → Panel dismisses
   - Trigger again, tap "✕" close button → Panel dismisses
   - Trigger again, swipe down → Panel dismisses
3. Verify scanner remains functional after each dismiss

**Expected:**
- All dismiss methods work
- Scanner continues operating
- No state corruption

---

### T10: Extended Use (Memory Leak Check)
**Steps:**
1. Leave scanner running for 2 minutes
2. Monitor device temperature
3. Navigate away and back multiple times
4. Check for performance degradation

**Expected:**
- No increasing lag
- Device doesn't overheat
- Camera buffers released properly
- App memory stable

---

## Known Issues & Fixes Applied

### Issue 1: Native Library Loading ✅ FIXED
**Problem:** `UnsatisfiedLinkError` - JNI function signatures didn't match  
**Fix:** Created `JABCodeMobile.kt` bridge class  
**Files:** `com/jabcode/JABCodeMobile.kt`, `JABCodeDecoderImpl.kt`

### Issue 2: Camera Buffer Overflow ✅ FIXED
**Problem:** `BufferQueueProducer timeout` - frames not released fast enough  
**Fix:** Reduced decode frequency to 2 FPS, added 200ms timeout  
**Files:** `JABCodeAnalyzer.kt`

### Issue 3: ColorMode Enum ✅ FIXED
**Problem:** Referenced non-existent `COLOR_128`  
**Fix:** Changed to `COLOR_8`  
**Files:** `JABCodeDecoderImpl.kt`

---

## Performance Targets

| Metric | Target | Current |
|--------|--------|---------|
| Camera FPS | 30 FPS | ✓ Stable |
| Decode attempts | 2 per second | ✓ Throttled |
| Decode timeout | <200ms | ✓ Configured |
| Buffer queue depth | <2 frames | ✓ Fixed |
| Memory usage | <100MB | TBD (test T10) |

---

## Test Results Summary

**Pass Rate:** ___/10 tests  
**Critical Failures:** _______  
**Performance Issues:** _______  
**Memory Leaks:** _______

---

## Next Steps After Testing

1. **If all tests pass:**
   - Tag build: `diagnostic-app-phase3`
   - Update checklist: Phase 3 complete
   - Begin Phase 4: Settings & DI

2. **If issues found:**
   - Document failures in this file
   - Create diagnostic memory in memory-bank
   - Fix and re-test

---

## Real JABCode Testing (Future)

When real JABCode images are available:

**Steps:**
1. Display JABCode containing JWT on another device
2. Point scanner at code
3. Verify automatic detection within 50-200ms
4. Verify JWT parsing and validation
5. Verify authentication result display

**Note:** Mock buttons simulate this flow for now
