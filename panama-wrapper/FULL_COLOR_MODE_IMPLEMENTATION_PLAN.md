# Full Color Mode Implementation Plan (Nc 0-7)

**Date:** 2026-01-08  
**Target:** Digital-only use cases  
**Status:** Ready to implement  
**Based on:** ISO/IEC 23634 specification + investigation findings

---

## Executive Summary

This plan operationalizes the full color mode support (Nc 0-7) for the Panama wrapper, focusing on **digital applications** where higher color modes work reliably without print/scan limitations.

**Key Findings from Investigation:**
- ✅ Encoder: Already complete (handles all modes 1-7)
- ❌ Decoder: Incomplete in native library
- 🎯 **Strategy:** Extend decoder to support modes 3-7 (16-256 colors)

---

## Current Status (From Investigation)

### What Works ✅
- **Encoder (`encoder.c`):** Full implementation
  - `genColorPalette()` - All modes 1-7
  - `setDefaultPalette()` - All modes
  - Metadata encoding - All modes
  - Module encoding - All modes

### What's Missing ❌
- **Decoder (`decoder.c`):** Partial implementation
  - `getPaletteThreshold()` - **ONLY modes 1-2** (lines 561-589)
  - Palette interpolation - **NOT IMPLEMENTED** for modes 6-7
  - Color discrimination - **ONLY 4 and 8 colors**

### Root Cause
```c
// decoder.c lines 561-589
void getPaletteThreshold(jab_byte* palette, jab_int32 color_number, jab_float* palette_ths)
{
	if(color_number == 4)    { /* implemented */ }
	else if(color_number == 8) { /* implemented */ }
	// ❌ NO CODE FOR 16, 32, 64, 128, 256
}
```

---

## Implementation Strategy

We have **two options**:

### Option A: Fix Native Decoder (Recommended for Performance) ⭐

**Approach:** Extend `decoder.c` to support all color modes

**Pros:**
- Leverages existing native performance
- Maintains architecture (Panama FFM → native)
- Smallest code change

**Cons:**
- Requires C programming
- Need to rebuild native library
- More complex testing

**Effort:** 40-60 hours

### Option B: Pure Java Decoder (Recommended for Portability)

**Approach:** Implement decoder entirely in Java, bypass native decoder

**Pros:**
- No native dependency
- Pure Java = easier debugging
- Perfect for digital use cases
- Easier to maintain

**Cons:**
- Slower than native (but acceptable for digital)
- More code to write
- Needs comprehensive testing

**Effort:** 60-80 hours

---

## Recommended Approach: **Hybrid Strategy**

1. **Short-term:** Extend native decoder for modes 3-5 (16-64 colors)
2. **Long-term:** Pure Java decoder for modes 6-7 (128-256 colors with interpolation)

**Rationale:**
- Modes 3-5: Simple threshold extension (~12 hours)
- Modes 6-7: Complex interpolation, better in Java (~40 hours)
- Get quick wins with modes 3-5, then tackle 6-7 properly

---

## Phase 1: Fix Native Decoder for Modes 3-5 (16-64 colors)

### Estimated Time: 12-16 hours

### Task 1.1: Implement getPaletteThreshold() for Mode 3 (16 colors)

**File:** `src/jabcode/decoder.c`

**Implementation:**
```c
else if(color_number == 16)
{
    // 16 colors = 4×2×2 (R×G×B variations)
    // Palette: Table 23 from ISO spec
    // RGB values: (0,85,170,255) × (0,255) × (0,255)
    
    // Calculate R channel thresholds (4 levels)
    jab_int32 r_ths[3];
    r_ths[0] = (0 + 85) / 2;    // 42
    r_ths[1] = (85 + 170) / 2;  // 127
    r_ths[2] = (170 + 255) / 2; // 212
    
    // Calculate G channel threshold (2 levels)
    jab_int32 g_ths = (0 + 255) / 2;  // 127
    
    // Calculate B channel threshold (2 levels)
    jab_int32 b_ths = (0 + 255) / 2;  // 127
    
    palette_ths[0] = r_ths[1];  // Primary R threshold
    palette_ths[1] = g_ths;     // G threshold
    palette_ths[2] = b_ths;     // B threshold
    
    // Store additional thresholds if needed
    // May need to extend palette_ths array or use multi-stage decision
}
```

**Testing:**
- Create 16-color test barcodes
- Verify threshold values match color palette
- Test all 16 color indices decode correctly

### Task 1.2: Implement getPaletteThreshold() for Mode 4 (32 colors)

**Implementation:**
```c
else if(color_number == 32)
{
    // 32 colors = 4×4×2 (R×G×B variations)
    // Palette from genColorPalette()
    // RGB values: (0,85,170,255) × (0,85,170,255) × (0,255)
    
    // R channel: 4 levels (same as 16-color)
    // G channel: 4 levels
    // B channel: 2 levels
    
    // Calculate thresholds similarly
    // ...implementation details...
}
```

### Task 1.3: Implement getPaletteThreshold() for Mode 5 (64 colors)

**Implementation:**
```c
else if(color_number == 64)
{
    // 64 colors = 4×4×4 (R×G×B variations)
    // All channels have 4 levels: (0,85,170,255)
    
    // Symmetric threshold calculation for all channels
    jab_int32 ths[3];  // Common for R, G, B
    ths[0] = 42;   // (0+85)/2
    ths[1] = 127;  // (85+170)/2
    ths[2] = 212;  // (170+255)/2
    
    palette_ths[0] = ths[1];
    palette_ths[1] = ths[1];
    palette_ths[2] = ths[1];
}
```

### Task 1.4: Rebuild and Test

**Commands:**
```bash
cd src/jabcode
mkdir -p build && cd build
cmake ..
make clean && make
sudo make install  # Or copy libraries manually
```

**Validation:**
```bash
cd panama-wrapper-itest
mvn test -Dtest=HigherColorModeTest
```

---

## Phase 2: Implement Palette Interpolation for Modes 6-7

### Estimated Time: 40-50 hours

### Option A: Native Implementation (C)

**Files to modify:**
- `src/jabcode/decoder.c`
- New function: `interpolateColorPalette()`

**Implementation:**
```c
/**
 * Interpolate full 128 or 256 color palette from embedded 64-color subset
 * @param embedded_palette The 64 colors extracted from symbol
 * @param full_palette Output buffer for full palette
 * @param color_number 128 or 256
 */
void interpolateColorPalette(jab_byte* embedded_palette, 
                              jab_byte* full_palette, 
                              jab_int32 color_number)
{
    if(color_number == 128) {
        // 128 = 8×4×4, but only R varies (8 levels)
        // Embedded: R ∈ {0,73,182,255}, G ∈ {0,85,170,255}, B ∈ {0,85,170,255}
        // Full: R ∈ {0,36,73,109,145,182,218,255}, same G,B
        
        // Interpolate R channel
        jab_byte r_embedded[4] = {0, 73, 182, 255};
        jab_byte r_full[8] = {0, 36, 73, 109, 145, 182, 218, 255};
        
        // For each G,B combination, expand R values
        for(int g_idx=0; g_idx<4; g_idx++) {
            for(int b_idx=0; b_idx<4; b_idx++) {
                jab_byte g = embedded_palette[(g_idx*4 + b_idx)*3 + 1];
                jab_byte b = embedded_palette[(g_idx*4 + b_idx)*3 + 2];
                
                for(int r_idx=0; r_idx<8; r_idx++) {
                    int full_idx = (r_idx*4*4 + g_idx*4 + b_idx) * 3;
                    full_palette[full_idx + 0] = r_full[r_idx];
                    full_palette[full_idx + 1] = g;
                    full_palette[full_idx + 2] = b;
                }
            }
        }
    }
    else if(color_number == 256) {
        // 256 = 8×8×4
        // Interpolate both R and G channels
        // Similar logic but for both R and G
    }
}
```

### Option B: Java Implementation (Recommended) ⭐

**New File:** `ColorPaletteInterpolator.java`

```java
package com.jabcode.panama;

public class ColorPaletteInterpolator {
    
    /**
     * Interpolate full 128-color palette from embedded 64-color subset
     */
    public static byte[] interpolate128ColorPalette(byte[] embedded) {
        // 128 = 8×4×4 (R×G×B)
        // Embedded: R ∈ {0,73,182,255} (4 values)
        // Full:     R ∈ {0,36,73,109,145,182,218,255} (8 values)
        
        byte[] full = new byte[128 * 3];
        
        // R interpolation values
        int[] rEmbedded = {0, 73, 182, 255};
        int[] rFull = {0, 36, 73, 109, 145, 182, 218, 255};
        
        int fullIdx = 0;
        for (int r : rFull) {
            for (int g = 0; g < 256; g += 85) {  // 4 G values
                for (int b = 0; b < 256; b += 85) {  // 4 B values
                    full[fullIdx++] = (byte)r;
                    full[fullIdx++] = (byte)g;
                    full[fullIdx++] = (byte)b;
                }
            }
        }
        
        return full;
    }
    
    /**
     * Interpolate full 256-color palette from embedded 64-color subset
     */
    public static byte[] interpolate256ColorPalette(byte[] embedded) {
        // 256 = 8×8×4 (R×G×B)
        // Interpolate both R and G channels
        
        byte[] full = new byte[256 * 3];
        
        int[] rgFull = {0, 36, 73, 109, 145, 182, 218, 255};  // 8 values
        int[] bFull = {0, 85, 170, 255};  // 4 values
        
        int fullIdx = 0;
        for (int r : rgFull) {
            for (int g : rgFull) {
                for (int b : bFull) {
                    full[fullIdx++] = (byte)r;
                    full[fullIdx++] = (byte)g;
                    full[fullIdx++] = (byte)b;
                }
            }
        }
        
        return full;
    }
    
    /**
     * Find nearest color in palette for given RGB
     */
    public static int findNearestColor(byte[] palette, int r, int g, int b) {
        int minDistance = Integer.MAX_VALUE;
        int nearestIndex = 0;
        
        for (int i = 0; i < palette.length / 3; i++) {
            int pr = palette[i * 3] & 0xFF;
            int pg = palette[i * 3 + 1] & 0xFF;
            int pb = palette[i * 3 + 2] & 0xFF;
            
            int distance = (r - pr) * (r - pr) + 
                          (g - pg) * (g - pg) + 
                          (b - pb) * (b - pb);
            
            if (distance < minDistance) {
                minDistance = distance;
                nearestIndex = i;
            }
        }
        
        return nearestIndex;
    }
}
```

**Integration with Decoder:**
```java
// In JABCodeDecoder.decodeFromFileEx()
if (colorNumber == 128 || colorNumber == 256) {
    byte[] embeddedPalette = extractEmbeddedPalette(bitmap);
    byte[] fullPalette = (colorNumber == 128) 
        ? ColorPaletteInterpolator.interpolate128ColorPalette(embeddedPalette)
        : ColorPaletteInterpolator.interpolate256ColorPalette(embeddedPalette);
    
    // Use fullPalette for color discrimination
}
```

---

## Phase 3: Integration and Testing

### Task 3.1: Update JABCodeDecoder

**Modifications needed:**

1. **Detect color mode from metadata:**
```java
// Read Nc from metadata Part I
int nc = readNcMetadata(bitmap);  // Returns 0-7
int colorNumber = getColorNumberFromNc(nc);  // Returns 4,8,16,32,64,128,256
```

2. **Handle palette extraction:**
```java
if (colorNumber <= 64) {
    // Direct palette extraction (all colors embedded)
    palette = extractFullPalette(bitmap, colorNumber);
} else {
    // Extract subset and interpolate
    byte[] embedded = extractEmbeddedPalette(bitmap, 64);
    palette = ColorPaletteInterpolator.interpolate(embedded, colorNumber);
}
```

3. **Color discrimination:**
```java
int decodeModuleColor(byte[] rgb, byte[] palette, int colorNumber) {
    if (colorNumber <= 8) {
        // Use existing native decoder
        return nativeDecodeModule(rgb);
    } else {
        // Use Java nearest-color matching
        return ColorPaletteInterpolator.findNearestColor(
            palette, 
            rgb[0] & 0xFF, 
            rgb[1] & 0xFF, 
            rgb[2] & 0xFF
        );
    }
}
```

### Task 3.2: Create Comprehensive Tests

**Test Matrix:**

| Mode | Colors | Palette | Interpolation | Test File |
|------|--------|---------|---------------|-----------|
| 1 | 4 | Direct | No | Mode1RoundTripTest.java |
| 2 | 8 | Direct | No | Mode2RoundTripTest.java |
| 3 | 16 | Direct | No | Mode3RoundTripTest.java |
| 4 | 32 | Direct | No | Mode4RoundTripTest.java |
| 5 | 64 | Direct | No | Mode5RoundTripTest.java |
| 6 | 128 | Subset+Interp | Yes (R) | Mode6RoundTripTest.java |
| 7 | 256 | Subset+Interp | Yes (R+G) | Mode7RoundTripTest.java |

**Test Template:**
```java
@Test
void testMode3_16Colors_RoundTrip(@TempDir Path tempDir) {
    String message = "Testing 16-color mode";
    Path barcodeFile = tempDir.resolve("mode3_test.png");
    
    // Encode with 16 colors
    var config = JABCodeEncoder.Config.builder()
        .colorNumber(16)
        .eccLevel(7)  // Higher ECC for safety
        .build();
    
    boolean encoded = encoder.encodeToPNG(message, barcodeFile.toString(), config);
    assertTrue(encoded, "16-color encoding should succeed");
    
    // Decode
    String decoded = decoder.decodeFromFile(barcodeFile);
    assertEquals(message, decoded, "16-color round-trip should work");
}
```

### Task 3.3: Performance Testing

**Benchmark all modes:**
```java
@Test
void benchmarkAllColorModes() {
    String message = "A".repeat(100);
    
    for (int colors : new int[]{4, 8, 16, 32, 64, 128, 256}) {
        long start = System.nanoTime();
        
        // Encode
        var config = JABCodeEncoder.Config.builder()
            .colorNumber(colors)
            .build();
        encoder.encodeToPNG(message, "test.png", config);
        
        // Decode
        decoder.decodeFromFile(Path.of("test.png"));
        
        long duration = System.nanoTime() - start;
        System.out.printf("Mode %d colors: %.2f ms%n", colors, duration / 1_000_000.0);
    }
}
```

---

## Phase 4: Documentation

### Task 4.1: User Guide

**Create:** `COLOR_MODES_GUIDE.md`

```markdown
# JABCode Color Modes Guide

## Mode Selection

| Mode | Colors | Bits/Module | Data Density | Use Case |
|------|--------|-------------|--------------|----------|
| 1 | 4 | 2 | Base | Maximum compatibility |
| 2 | 8 | 3 | 1.5x | Default, good balance |
| 3 | 16 | 4 | 2x | Higher density |
| 4 | 32 | 5 | 2.5x | Digital-only |
| 5 | 64 | 6 | 3x | Digital-only |
| 6 | 128 | 7 | 3.5x | Digital-only, high density |
| 7 | 256 | 8 | 4x | Maximum density |

## Digital vs Print

- **Modes 1-2:** Work for print and digital
- **Modes 3-7:** Recommended for digital-only
  - Perfect color reproduction on screens
  - No print quality concerns
  - Reliable screen-to-camera capture

## Example Usage

```java
// For digital applications
var config = JABCodeEncoder.Config.builder()
    .colorNumber(64)      // Mode 5
    .eccLevel(7)          // Higher ECC recommended
    .moduleSize(16)       // Larger modules for reliability
    .build();

encoder.encodeToPNG(data, outputFile, config);
String decoded = decoder.decodeFromFile(outputFile);
```
```

### Task 4.2: API Documentation

Update Javadoc for all new classes and methods.

---

## Success Criteria

### Functional Requirements ✅
- [ ] All modes 1-7 encode successfully
- [ ] All modes 1-7 decode successfully
- [ ] Round-trip tests pass for all modes
- [ ] Palette interpolation works correctly (modes 6-7)
- [ ] Color discrimination accurate (>95% for digital)

### Performance Requirements ✅
- [ ] Modes 1-2: Within 10% of current performance
- [ ] Modes 3-5: Within 50% of mode 2 (acceptable)
- [ ] Modes 6-7: Within 100% of mode 2 (acceptable for digital)

### Quality Requirements ✅
- [ ] Test coverage: >90% for all color mode code
- [ ] No regressions in existing tests
- [ ] All edge cases covered
- [ ] Documentation complete

---

## Timeline Estimate

### Conservative Estimate (Solo Developer)

| Phase | Tasks | Hours | Calendar |
|-------|-------|-------|----------|
| Phase 1 | Native decoder modes 3-5 | 12-16h | Week 1 |
| Phase 2 | Java interpolator modes 6-7 | 40-50h | Weeks 2-3 |
| Phase 3 | Integration & testing | 20-24h | Week 4 |
| Phase 4 | Documentation | 8-10h | Week 4 |
| **Total** | | **80-100h** | **4 weeks** |

### Aggressive Estimate (With Assistance)

| Phase | Tasks | Hours | Calendar |
|-------|-------|-------|----------|
| Phase 1 | Native decoder modes 3-5 | 8-10h | Days 1-2 |
| Phase 2 | Java interpolator modes 6-7 | 24-30h | Days 3-6 |
| Phase 3 | Integration & testing | 12-16h | Days 7-8 |
| Phase 4 | Documentation | 4-6h | Day 9 |
| **Total** | | **48-62h** | **9-10 days** |

---

## Risks and Mitigations

### Risk 1: Color Discrimination Accuracy
**Issue:** Higher color modes may have color confusion  
**Mitigation:**
- Use higher ECC levels (7-9) for modes 3-7
- Implement robust nearest-color algorithm
- Add color distance validation in tests

### Risk 2: Performance Degradation
**Issue:** Java-based color matching slower than native  
**Mitigation:**
- Optimize hot paths
- Consider native implementation if critical
- Acceptable for digital use cases (not real-time)

### Risk 3: Interpolation Errors
**Issue:** Palette reconstruction may be incorrect  
**Mitigation:**
- Extensive unit tests for interpolation
- Validate against ISO specification values
- Test with reference implementation if available

---

## Next Steps

1. ✅ **Review this plan** - Confirm approach and timeline
2. 🔧 **Set up development branch** - `feature/full-color-modes`
3. 🚀 **Start Phase 1** - Extend native decoder for modes 3-5
4. 📊 **Track progress** - Update plan with actual hours/blockers
5. ✅ **Test iteratively** - Run `/test-coverage-update` after each phase

---

## References

- **Roadmap:** `/memory-bank/research/panama-poc/codebase-audit/03-panama-implementation-roadmap.md`
- **Spec Audit:** `/memory-bank/research/panama-poc/codebase-audit/jabcode-spec-audit/`
- **Investigation:** `INVESTIGATION_FINDINGS.md`, `DECODER_ANALYSIS.md`
- **ISO Spec:** ISO/IEC 23634 (reference for all implementation details)

---

**Status:** Ready to implement  
**Approved by:** User (digital-only use cases confirmed)  
**Start Date:** TBD  
**Target Completion:** 4 weeks (conservative) / 10 days (aggressive)
