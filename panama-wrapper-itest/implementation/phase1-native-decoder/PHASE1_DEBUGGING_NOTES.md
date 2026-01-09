# Phase 1 Implementation - Debugging Notes

**Date:** 2026-01-08 21:33 EST  
**Status:** 🔧 Debugging in progress  

---

## What Was Done

### ✅ Changes Applied
1. **Extended `getPaletteThreshold()`** in `/mnt/b34628fa-d41e-4c37-8caf-f06a6ecbb1ae/projects/practice/barcode/jabcode/src/jabcode/decoder.c`
   - Added cases for `color_number == 16, 32, 64`
   - Set threshold values to 42.5f for black detection
   - Native library rebuilt successfully

### ❌ Test Results
- **All Mode 3 tests still failing** (10/14 failures)
- Same error pattern as baseline (RED state)
- No improvement observed

---

## Error Analysis

### Primary Errors
```
JABCode Error: No alignment pattern is available
JABCode Error: Too many errors in message. LDPC decoding failed.
```

### Root Cause Investigation

#### 1. **Alignment Pattern Error**
**Source:** `detector.c:2949`
```c
if(symbol->metadata.side_version.x < 6 && symbol->metadata.side_version.y < 6)
{
    reportError("No alignment pattern is available");
    return NULL;
}
```

**Analysis:**
- This error occurs when trying to sample a symbol with no alignment patterns
- Suggests the **metadata is not being read correctly**
- The decoder can't determine the symbol version/size

#### 2. **LDPC Decoding Failure**
- Indicates corrupted data after attempting to decode
- Could be caused by incorrect module color discrimination
- Happens **after** metadata reading fails

---

## Decoder Architecture Understanding

### How Color Discrimination Works

#### `getPaletteThreshold()` Function
- **Purpose:** Only used to quickly detect BLACK modules (color index 0)
- **Usage:** Line 426 in `decodeModuleHD()`
  ```c
  if(rgb[0] < pal_ths[p_index*3 + 0] && 
     rgb[1] < pal_ths[p_index*3 + 1] && 
     rgb[2] < pal_ths[p_index*3 + 2])
  {
      index1 = 0;  // It's black
      return index1;
  }
  ```
- **Array size:** Only 3 values per palette (R, G, B)
- **Not used for full color discrimination**

#### Actual Color Matching (Lines 440-466)
```c
for(jab_int32 i=0; i<color_number; i++)
{
    // Normalize and compare using Euclidean distance
    jab_float diff = (pr - r)*(pr - r) + (pg - g)*(pg - g) + (pb - b)*(pb - b);
    // Find closest match
}
```

**This should work for any color count** - it's distance-based!

---

## Hypothesis: Why It's Still Failing

### Theory 1: Metadata Reading Issue ⚠️
The decoder reads metadata modules first to determine:
- Color mode (Nc value)
- Symbol size/version
- Error correction level

**If metadata reading fails:**
1. Can't determine correct color_number
2. Can't allocate proper palette
3. Can't locate alignment patterns
4. Everything downstream fails

### Theory 2: Barcode Size Issue 📏
Default test parameters might create symbols that are:
- Too small (< version 6)
- Don't have alignment patterns
- Fail the check in `sampleSymbolByAlignmentPattern()`

### Theory 3: Missing Encoder/Decoder Coordination ⚙️
- Encoder generates 16-color barcodes correctly
- But decoder might not recognize them during **finder pattern detection**
- Issue could be in `detector.c` not `decoder.c`

---

## Next Steps to Try

### Option A: Add Debug Logging 🔍
Add printf statements to trace:
1. What Nc value is being read from metadata
2. What color_number is calculated
3. Whether `getPaletteThreshold()` is being called with 16/32/64
4. What happens during metadata module decoding

```c
// In decoder.c
printf("DEBUG: Nc=%d, color_number=%d\n", symbol->metadata.Nc, color_number);
```

### Option B: Test with Larger Symbols 📐
Modify test configuration:
```java
JABCodeEncoder.Config.builder()
    .colorNumber(16)
    .moduleSize(20)  // Larger
    .eccLevel(5)     // Lower ECC for bigger symbol
    .version(10)     // Force larger version
    .build();
```

### Option C: Check Detector Logic 🔎
Search `detector.c` for any hard-coded limits:
```bash
grep -n "color.*8\|Nc.*1" detector.c
```

### Option D: Verify Palette Generation 🎨
Check if encoder properly generates 16-color palettes:
```bash
# Run encoder standalone and inspect output
./jabcodeWriter -i "test" -o test16.png --color-number 16
```

---

## Files Modified

### `/mnt/b34628fa-d41e-4c37-8caf-f06a6ecbb1ae/projects/practice/barcode/jabcode/src/jabcode/decoder.c`
**Lines 589-613:** Added color_number cases for 16, 32, 64

**Before:**
```c
else if(color_number == 8)
{
    // ... 8-color logic
}
}  // Function ended here
```

**After:**
```c
else if(color_number == 8)
{
    // ... 8-color logic  
}
else if(color_number == 16)
{
    palette_ths[0] = 42.5f;
    palette_ths[1] = 42.5f;
    palette_ths[2] = 42.5f;
}
else if(color_number == 32)
{
    palette_ths[0] = 42.5f;
    palette_ths[1] = 42.5f;
    palette_ths[2] = 42.5f;
}
else if(color_number == 64)
{
    palette_ths[0] = 42.5f;
    palette_ths[1] = 42.5f;
    palette_ths[2] = 42.5f;
}
}
```

---

## Library Rebuild Status

```bash
$ stat ../src/jabcode/build/libjabcode.so
Modified: 2026-01-08 21:32:23 (most recent)
```

✅ Library is up to date  
✅ Compilation successful  
✅ Library being loaded by tests (verified via -X output)

---

## Critical Questions

1. **Is the encoder actually generating valid 16-color barcodes?**
   - Need to test with official jabcodeWriter tool
   - Compare with our Java encoder output

2. **Are there other decoder functions limiting color modes?**
   - Need to audit entire decoder.c and detector.c
   - Look for hard-coded assumptions about 4/8 colors

3. **Is the metadata format different for 16+ colors?**
   - Check ISO spec for metadata encoding differences
   - Verify our understanding of Nc field encoding

4. **Are alignment patterns required for small barcodes?**
   - Test with explicit larger versions
   - Check if default barcode size is too small

---

## Recommended Immediate Action

**Create a minimal test case:**
1. Encode a simple message with 16 colors using official C tool
2. Try to decode it with official C tool  
3. If that works, try with our Java wrapper
4. If that fails, compare binary execution paths

```bash
cd /mnt/b34628fa-d41e-4c37-8caf-f06a6ecbb1ae/projects/practice/barcode/jabcode/src/jabcode
./jabcodeWriter -i "test" --color-number 16 -o /tmp/test16_official.png
./jabcodeReader -i /tmp/test16_official.png
```

If official tools work, the issue is in our Java integration.  
If official tools fail, the issue is in the C library implementation.

---

## Time Investment

- **Time spent:** ~2 hours
- **Expected for Phase 1:** 12-16 hours
- **Status:** On track, but need different debugging approach

---

**Next:** Need user input on debugging strategy
