# Tier 2 & Tier 3 Implementation: Camera Configuration + Otsu Binarization

**Date:** 2026-05-10 20:30 UTC-04:00  
**Status:** ✅ COMPLETE - Ready for testing  
**Expected Detection Rate:** 5% → 85%+

---

## Implementation Summary

### **Tier 2: Camera Configuration (COMPLETE)**

**Goal:** Improve camera output quality for better binarization

**Changes Implemented:**

1. **Resolution Increase: 1280×720 → 1920×1080**
   - File: `@/framework/ui-components/src/main/java/com/jabauth/ui/scanner/Camera2Preview.kt:109-110`
   - Impact: 2.25× more pixels, less downscaling blur
   - Edge quality: 13× reduction → 5.8× reduction from sensor
   - Expected: 20-30% better edge classification

2. **Exposure Compensation: +1 EV**
   - File: `@/framework/ui-components/src/main/java/com/jabauth/ui/scanner/Camera2Preview.kt:276-285`
   - Impact: Biases exposure toward brighter whites (black→20, white→240+)
   - Prevents AE from compressing JABCode dynamic range
   - Expected: 15-25% improvement in contrast
   
3. **Diagnostic App Integration**
   - File: `@/diagnostic-app/src/main/java/com/jabauth/diagnostic/ui/scanner/ScannerScreen.kt:71`
   - Set to `exposureCompensation = +1` by default
   - Can be adjusted for testing (-2 to +2 range)

**Benefits:**
- ✅ Non-invasive (camera configuration only)
- ✅ Fast implementation (~30 minutes)
- ✅ Immediate testing (no native rebuild required for Tier 2 alone)
- ✅ Reversible via parameter

---

### **Tier 3: Otsu's Method Binarization (COMPLETE)**

**Goal:** Replace hardcoded threshold=128 with mathematically optimal adaptive thresholds

**Changes Implemented:**

1. **Added Otsu Threshold Calculation**
   - File: `@/src/jabcode/binarizer.c:607-653`
   - Function: `calculateOtsuThreshold(histogram, total_pixels)`
   - Algorithm: Maximizes inter-class variance (Otsu 1979)
   - Complexity: O(256) per block - fast!

2. **Modified binarizerLuminanceRGB()**
   - File: `@/src/jabcode/binarizer.c:666-777`
   - **Before:** Hardcoded threshold = 128 for all lighting conditions
   - **After:** Otsu threshold per block per RGB channel
   - Block structure: Same as original (width/2 × height/2 blocks)
   - Adaptation: Automatically handles compressed range (40-210) or full range (0-255)

3. **Per-Channel Histogram Analysis**
   - Builds histogram for R, G, B independently per block
   - Calculates optimal threshold for each channel
   - Spatially-varying thresholds adapt to local lighting

**Technical Details:**

**Memory Allocation:**
```c
jab_int32* threshold_r = malloc(block_num_x * block_num_y * sizeof(jab_int32));
jab_int32* threshold_g = malloc(block_num_x * block_num_y * sizeof(jab_int32));
jab_int32* threshold_b = malloc(block_num_x * block_num_y * sizeof(jab_int32));
```

**Histogram Building (per block):**
```c
jab_int32 hist_r[256] = {0}, hist_g[256] = {0}, hist_b[256] = {0};
for (each pixel in block) {
    hist_r[r_value]++;
    hist_g[g_value]++;
    hist_b[b_value]++;
}
```

**Otsu Calculation:**
```c
threshold_r[block_index] = calculateOtsuThreshold(hist_r, pixel_count);
threshold_g[block_index] = calculateOtsuThreshold(hist_g, pixel_count);
threshold_b[block_index] = calculateOtsuThreshold(hist_b, pixel_count);
```

**Binarization:**
```c
jab_int32 block_index = MIN(i/block_size_y, block_num_y-1) * block_num_x + MIN(j/block_size_x, block_num_x-1);
rgb[0]->pixel[i*bitmap->width + j] = (r >= threshold_r[block_index]) ? 255 : 0;
rgb[1]->pixel[i*bitmap->width + j] = (g >= threshold_g[block_index]) ? 255 : 0;
rgb[2]->pixel[i*bitmap->width + j] = (b >= threshold_b[block_index]) ? 255 : 0;
```

**Benefits:**
- ✅ Mathematically optimal (proven algorithm)
- ✅ No manual tuning required
- ✅ Adapts to camera ISP variations (tone mapping, gamma, etc.)
- ✅ Industry standard (used in QR codes, document scanning)
- ✅ Fast: O(256) per block, ~8ms overhead for 1920×1080

---

## Build Instructions

### 1. Rebuild Native Library (Tier 3 changes)

```bash
cd /mnt/b34628fa-d41e-4c37-8caf-f06a6ecbb1ae/projects/practice/barcode/jabcode/src/jabcode
make clean && make
```

**Output:** `build/libjabcode.so` with Otsu implementation ✅ COMPLETE

### 2. Rebuild Android JNI Wrapper

```bash
cd /mnt/b34628fa-d41e-4c37-8caf-f06a6ecbb1ae/projects/practice/barcode/jabcode/jabauth-android
./gradlew :framework:jabcode-sdk:assembleDebug
```

### 3. Rebuild Diagnostic App

```bash
cd /mnt/b34628fa-d41e-4c37-8caf-f06a6ecbb1ae/projects/practice/barcode/jabcode/jabauth-android
./gradlew :diagnostic-app:assembleDebug
```

### 4. Install on Device

```bash
adb install -r diagnostic-app/build/outputs/apk/debug/diagnostic-app-debug.apk
```

---

## Testing Plan

### Phase 1: Tier 2 Validation (Camera Configuration Only)

**Purpose:** Isolate camera improvements before native decoder changes

**Steps:**
1. Comment out Tier 3 changes temporarily (revert binarizer.c)
2. Build with only Tier 2 (resolution + exposure)
3. Test detection rate
4. Establish baseline for Tier 2 contribution

**Expected Result:** 5% → 20-30% detection rate

### Phase 2: Full Implementation (Tier 2 + Tier 3)

**Purpose:** Validate complete solution

**Steps:**
1. Build with both Tier 2 and Tier 3
2. Test detection rate across lighting conditions
3. Compare against Tier 2-only baseline

**Expected Result:** 5% → 85%+ detection rate

### Test Matrix

| Condition | Current (5%) | Tier 2 Only | Tier 2+3 (Target) |
|-----------|-------------|-------------|-------------------|
| **Indoor LED (bright)** | <10% | 25-35% | 85-90% |
| **Indoor LED (dim)** | <5% | 15-25% | 75-85% |
| **Outdoor daylight** | 10-15% | 40-50% | 95%+ |
| **Outdoor cloudy** | 5-10% | 30-40% | 90-95% |
| **Mixed lighting** | <5% | 20-30% | 80-90% |

### Test Cases

**Minimal Test:**
- [ ] 4-color JABCode, ECC=3, 21×21 modules
- [ ] Indoor LED lighting
- [ ] Distance: 15cm
- [ ] Framing: JABCode fills 60% of viewfinder

**Comprehensive Test:**
- [ ] All 6 color modes (4, 8, 16, 32, 64, 128)
- [ ] Various lighting conditions (indoor, outdoor, mixed)
- [ ] Multiple distances (5cm, 10cm, 20cm, 30cm)
- [ ] Different framing (40%, 60%, 80% fill)
- [ ] With/without white border around JABCode

---

## Diagnostic Logging

### Camera Configuration

Check logcat for exposure compensation:
```bash
adb logcat -s Camera2Controller:D | grep "EV="
```

Expected output:
```
Camera2 preview started: AF=ON, AE=ON (EV=+1), AWB=AUTO
```

### Binarization Thresholds (Optional)

To debug Otsu thresholds, add logging to `calculateOtsuThreshold()`:

```c
// In binarizer.c:652 (before return)
JAB_REPORT_INFO(("Otsu threshold: %d (variance: %.2f)", optimal_threshold, max_variance))
```

Check native logs:
```bash
adb logcat -s jabcode:I | grep "Otsu"
```

---

## Performance Impact

### Tier 2: Resolution Increase

**Before:** 1280×720 = 921,600 pixels  
**After:** 1920×1080 = 2,073,600 pixels  
**Increase:** 2.25×

**Frame Processing:**
- YUV→RGB conversion: +2.25× time
- Bitmap creation: +2.25× memory
- Decode attempt: +2.25× data to process

**Throttling:** Already limited to 3 fps (333ms budget)  
**Expected overhead:** +20-30ms per frame (still within budget)

### Tier 3: Otsu Calculation

**Per-block overhead:**
- Histogram build: O(pixels_in_block) = ~10,000 pixels
- Otsu calculation: O(256) = constant
- Total blocks: ~4 (1920×1080 with block_size ≈ 960×540)

**Total overhead:** ~8ms per frame (1920×1080)

**Combined Tier 2+3:** ~30-40ms per frame (well within 333ms budget)

---

## Rollback Plan

### If Detection Rate Doesn't Improve

**Rollback Tier 3 Only:**
```bash
cd /mnt/b34628fa-d41e-4c37-8caf-f06a6ecbb1ae/projects/practice/barcode/jabcode/src/jabcode
git checkout HEAD -- binarizer.c
make clean && make
# Rebuild Android app
```

**Rollback Tier 2 Only:**
```kotlin
// In Camera2Preview.kt
private const val IMAGE_WIDTH = 1280   // Revert to original
private const val IMAGE_HEIGHT = 720

// In ScannerScreen.kt
exposureCompensation = 0,  // Revert to default
```

**Rollback Both:**
Revert all changes and rebuild.

---

## Next Steps After Testing

### If Successful (85%+ detection rate):

1. **Production Integration**
   - Merge changes to main codebase
   - Update documentation
   - Add exposure compensation as user setting (optional)

2. **Optimization**
   - Profile Otsu overhead on lower-end devices
   - Consider caching histograms if thermal becomes issue
   - Test sustained scanning (thermal impact)

3. **User Experience**
   - Add visual feedback (green overlay when JABCode detected)
   - Optimize preview size for different screen sizes
   - Add distance guidance (too close/too far indicators)

### If Partially Successful (40-70% detection rate):

1. **Tier 4: CLAHE Preprocessing**
   - Add histogram equalization before binarization
   - Expected: +10-15% detection rate
   - Cost: +12ms overhead

2. **Tier 5: Multi-frame Fusion**
   - Combine multiple frames before decode attempt
   - Reduces motion blur and noise
   - Expected: +5-10% detection rate

### If Unsuccessful (<40% detection rate):

1. **Root Cause Investigation**
   - Add detailed Otsu threshold logging
   - Capture and analyze failed frames
   - Check if issue is upstream (finder pattern detection)
   - Validate Otsu is actually being called (not falling back to old path)

2. **Alternative Approaches**
   - Triangle method (skewed distributions)
   - Sauvola thresholding (local adaptation)
   - Machine learning-based binarization

---

## Files Modified

### Android Kotlin (Tier 2)

1. **Camera2Preview.kt** (framework/ui-components)
   - Lines 50, 57, 105, 109-110: Added exposureCompensation parameter
   - Lines 69-72: LaunchedEffect for exposure updates
   - Lines 125, 148-157: updateExposureCompensation() function
   - Lines 276-285: Apply exposure compensation to capture request

2. **ScannerScreen.kt** (diagnostic-app)
   - Line 71: Set exposureCompensation = +1

### Native C (Tier 3)

3. **binarizer.c** (src/jabcode)
   - Lines 607-653: NEW - calculateOtsuThreshold() function
   - Lines 656-664: Updated function documentation
   - Lines 688-707: Otsu threshold array allocation
   - Lines 709-745: Per-block histogram building and Otsu calculation
   - Lines 747-776: Binarization with Otsu thresholds (replaced hardcoded 128)

---

## Success Metrics

### Detection Rate
- **Baseline:** <5%
- **Tier 2 Target:** 20-30%
- **Tier 2+3 Target:** 85%+

### False Positive Rate
- **Target:** <0.1%
- **Method:** Test with non-JABCode images (QR codes, text, patterns)

### Processing Time
- **Baseline:** ~26ms per frame
- **Tier 2+3 Target:** <60ms per frame (still within 3 fps budget)

### User Experience
- **Target:** Detection within 2 seconds of pointing at JABCode
- **Method:** Measure time from app open to first successful decode

---

## Known Limitations

### Otsu Assumptions

1. **Bimodal Distribution Required**
   - Otsu assumes two clear peaks (black and white)
   - May fail on uniform gray images
   - Mitigation: Fallback to threshold=128 if max_variance is low

2. **Block Size Trade-off**
   - Large blocks: Better statistics, less spatial adaptation
   - Small blocks: Better adaptation, noisier histograms
   - Current: ~960×540 blocks (4 total for 1920×1080)

3. **Multimodal Distributions**
   - JABCode with >2 colors may have 3+ peaks
   - Otsu finds one threshold, may split wrong colors
   - Mitigation: Per-channel Otsu handles color separation

### Camera Limitations

1. **Exposure Compensation Range**
   - Samsung S23: Typically -2 to +2 EV in 1/3 steps
   - Other devices may have different ranges
   - Code checks range before applying (line 278)

2. **Auto-Exposure Lock**
   - Not implemented in Tier 2
   - AE continues to adjust even with +1 EV compensation
   - Future: Add AE lock after first detection

---

## Validation Checklist

Before deployment:

- [ ] Native library builds without errors
- [ ] Android app builds without errors
- [ ] App installs on device
- [ ] Camera preview shows correctly at 1920×1080
- [ ] Logcat confirms EV=+1 in camera logs
- [ ] JABCode detection succeeds in good lighting
- [ ] No crashes during sustained scanning (5+ minutes)
- [ ] Memory usage stable (no leaks from threshold arrays)
- [ ] Battery drain acceptable (<5% per minute of scanning)

---

## References

- **Tier 2 Analysis:** `@/research-docs/diagnostics/binarization-threshold-deep-dive.md`
- **Otsu Explanation:** `@/research-docs/diagnostics/otsu-method-explained.md`
- **Camera2 API:** Android Developer Documentation
- **Otsu Paper:** Nobuyuki Otsu (1979), IEEE Transactions on SMC

---

**Status:** ✅ Implementation complete, ready for build and testing.

**Next Action:** Build Android app and install on device for validation testing.
