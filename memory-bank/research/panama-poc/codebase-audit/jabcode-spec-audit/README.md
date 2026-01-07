# JABCode ISO/IEC 23634 Specification Audit

## Overview

This directory contains a comprehensive audit of the JABCode ISO/IEC 23634 specification with detailed implementation guidance for supporting all 8 color modes in the Panama FFM wrapper.

**Key Finding:** JABCode supports 8 color modes (Nc = 0-7) encoding from 4 to 256 colors. Current implementation supports only modes 1-2 (4 and 8 colors). This audit provides everything needed to implement modes 3-7.

---

## Critical Discovery

### Current Status
✅ **Mode 1** (4 colors) - Implemented  
✅ **Mode 2** (8 colors, default) - Implemented  
❌ **Modes 3-7** (16-256 colors) - **NOT IMPLEMENTED**

### Specification Requirement

**From ISO/IEC 23634, Section 4.4.1.2:**
> "It takes 3 bits and defines 8 colour modes. Nc shall be encoded in a three-colour mode..."

**All 8 modes defined:**

| Mode | Colors | Status | Complexity |
|------|--------|--------|------------|
| 0 | Reserved | Future/User | N/A |
| 1 | 4 | ✅ Standard | Low |
| 2 | 8 (Default) | ✅ Standard | Low |
| 3 | 16 | 🔧 User-defined (Annex G) | Medium |
| 4 | 32 | 🔧 User-defined (Annex G) | Medium |
| 5 | 64 | 🔧 User-defined (Annex G) | Medium |
| 6 | 128 | 🔧 User-defined (Annex G) | High |
| 7 | 256 | 🔧 User-defined (Annex G) | High |

---

## Document Structure

### Quick Reference Guide

**[00-index.md](./00-index.md)** ⭐ **START HERE**
- Executive summary
- Color modes overview table
- Implementation priorities
- Quick navigation guide

### Technical Deep Dives

**[01-color-modes-overview.md](./01-color-modes-overview.md)**
- Complete specifications for all 8 modes
- RGB values and bit encodings
- Use cases and trade-offs
- Implementation code examples

**[02-color-palette-construction.md](./02-color-palette-construction.md)**
- Palette generation algorithms
- Color distance calculations
- Embedding and extraction logic
- Java implementation examples

**[03-encoding-implementation.md](./03-encoding-implementation.md)**
- Complete encoding algorithm (Section 5)
- Variable bit-width module encoding
- Data masking for all color modes
- Nc metadata encoding (3-color mode)

**[04-decoding-implementation.md](./04-decoding-implementation.md)**
- Complete decoding algorithm (Section 6)
- Nc detection and color mode selection
- Palette extraction and interpolation
- Data unmasking for all color modes

**[05-annex-g-analysis.md](./05-annex-g-analysis.md)**
- Deep dive into Annex G
- RGB color space methodology
- Interpolation requirements (modes 6-7)
- Palette embedding rules

### Action Plan

**[06-implementation-checklist.md](./06-implementation-checklist.md)**
- 7-phase implementation plan
- Concrete task breakdown
- Effort estimates (36-54 hours)
- Success criteria

---

## Key Implementation Insights

### 1. Variable Bit Encoding

Different modes use different bit widths per module:

```
Mode 1: 2 bits → 4 colors   (log₂(4) = 2)
Mode 2: 3 bits → 8 colors   (log₂(8) = 3)
Mode 3: 4 bits → 16 colors  (log₂(16) = 4)
Mode 4: 5 bits → 32 colors  (log₂(32) = 5)
Mode 5: 6 bits → 64 colors  (log₂(64) = 6)
Mode 6: 7 bits → 128 colors (log₂(128) = 7)
Mode 7: 8 bits → 256 colors (log₂(256) = 8)
```

**Impact:** Encoder/decoder must handle variable bit-width packing.

### 2. Palette Embedding Limit

**Critical Constraint:** Maximum 64 colors can be embedded in symbol (128 modules reserved for 2 palettes).

**Solution for Modes 6-7:**
- Embed only subset of colors (64 out of 128/256)
- Decoder interpolates missing colors from embedded values
- Annex G.3 provides exact interpolation rules

### 3. Metadata Encoding Special Case

**Nc itself encoded in 3-color mode only:**
- Uses only Black, Cyan, Yellow
- Even if symbol uses 256 colors
- Table 7 defines the 3-color encoding

### 4. RGB Color Space Strategy

**Annex G.1 Principle:**
> "The used colours should keep a distance from each other in the RGB colour space cube."

**Mode Evolution:**
```
Mode 1: CMYK primaries (max distance)
Mode 2: RGB cube vertices (8 colors)
Mode 3: Add R subdivisions (16 colors)
Mode 4: Add R+G subdivisions (32 colors)
Mode 5: Add R+G+B subdivisions (64 colors - symmetric)
Mode 6: Fine R subdivisions (128 colors - interpolation needed)
Mode 7: Fine R+G subdivisions (256 colors - dual interpolation)
```

---

## Implementation Priorities

### Phase 1: Verify Standard Modes (Required)

**Effort:** 4-6 hours

```java
// Verify current implementation
✅ Mode 1 (4 colors) - Black, Cyan, Magenta, Yellow
✅ Mode 2 (8 colors) - RGB cube vertices

// Test against specification
✅ RGB values match Tables 3 & 4
✅ Bit encoding correct
✅ Round-trip works
```

### Phase 2: Add Extended Modes (High Priority)

**Effort:** 12-16 hours

```java
// No interpolation needed
🔧 Mode 3 (16 colors) - Table G.1
🔧 Mode 4 (32 colors) - Annex G.3(b)
🔧 Mode 5 (64 colors) - Annex G.3(c)

// All colors fit in embedded palette
// Straightforward implementation
```

### Phase 3: Add High-Color Modes (Advanced)

**Effort:** 16-24 hours

```java
// Interpolation required
🔧 Mode 6 (128 colors) - Annex G.3(d)
   - Embed 64-color subset
   - Interpolate R channel
   
🔧 Mode 7 (256 colors) - Annex G.3(e)
   - Embed 64-color subset
   - Interpolate R and G channels
```

### Phase 4: Testing & Integration

**Effort:** 8-12 hours

```java
✅ Unit tests for all modes
✅ Quality tests (Section 8.3)
✅ Performance benchmarks
✅ Panama wrapper integration
```

**Total Effort:** 40-58 hours (~1-2 weeks)

---

## Quick Start Guides

### For Reviewers

1. Read **00-index.md** for overview
2. Check **01-color-modes-overview.md** for mode details
3. Review **06-implementation-checklist.md** for effort estimate

### For Implementers

1. Start with **00-index.md**
2. Study **02-color-palette-construction.md** for algorithms
3. Review **05-annex-g-analysis.md** for Annex G details
4. Follow **06-implementation-checklist.md** phase by phase

### For Testers

1. Review **01-color-modes-overview.md** for specifications
2. Check **06-implementation-checklist.md** Phase 5 for test plan
3. Verify against ISO examples in specification

---

## Critical Code Examples

### Color Mode Enum

```java
public enum ColorMode {
    RESERVED_0(0, 0, "Reserved"),
    MODE_4(1, 4, "4 colors (CMYK)"),
    MODE_8(2, 8, "8 colors (RGB cube)"),
    MODE_16(3, 16, "16 colors (user-defined)"),
    MODE_32(4, 32, "32 colors (user-defined)"),
    MODE_64(5, 64, "64 colors (user-defined)"),
    MODE_128(6, 128, "128 colors (interpolation)"),
    MODE_256(7, 256, "256 colors (interpolation)");
    
    private final int ncValue;      // Nc metadata value
    private final int colorCount;    // Number of colors
    private final String description;
    
    public int getBitsPerModule() {
        if (colorCount == 0) return 0;
        return (int)Math.ceil(Math.log(colorCount) / Math.log(2));
    }
    
    public boolean requiresInterpolation() {
        return colorCount > 64;
    }
}
```

### Palette Generation (Mode 5 Example)

```java
public class Mode5Palette implements ColorPalette {
    private static final int[] RGB_VALUES = {0, 85, 170, 255};
    
    @Override
    public int[][] generateFullPalette() {
        int[][] palette = new int[64][3];
        int index = 0;
        
        for (int r : RGB_VALUES) {
            for (int g : RGB_VALUES) {
                for (int b : RGB_VALUES) {
                    palette[index][0] = r;
                    palette[index][1] = g;
                    palette[index][2] = b;
                    index++;
                }
            }
        }
        
        return palette;
    }
    
    @Override
    public int getBitsPerModule() {
        return 6; // log₂(64)
    }
}
```

### Interpolation (Mode 6 Example)

```java
public class Mode6Interpolation {
    // Embedded: 4 R values {0, 73, 182, 255}
    // Full: 8 R values {0, 36, 73, 109, 146, 182, 219, 255}
    
    public static int[] interpolateRChannel(int rEmbedded) {
        // Return possible full R values for this embedded value
        switch (rEmbedded) {
            case 0:   return new int[]{0, 36};
            case 73:  return new int[]{36, 73, 109, 146};
            case 182: return new int[]{146, 182, 219};
            case 255: return new int[]{219, 255};
            default:  throw new IllegalArgumentException();
        }
    }
}
```

---

## Test Strategy

### Unit Tests (Per Mode)

```java
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Mode3PaletteTest {
    @Test
    void testPaletteGeneration() {
        int[][] palette = Mode3Palette.generatePalette();
        assertEquals(16, palette.length);
        // Verify against Table G.1
    }
    
    @Test
    void testColorDistances() {
        int[][] palette = Mode3Palette.generatePalette();
        double minDistance = calculateMinDistance(palette);
        assertEquals(85.0, minDistance, 0.1);
    }
    
    @Test
    void testRoundTrip() {
        String data = "Test Data";
        byte[] encoded = encoder.encode(data, ColorMode.MODE_16);
        String decoded = decoder.decode(encoded);
        assertEquals(data, decoded);
    }
}
```

### Integration Tests

```java
@Test
void testAllColorModes() {
    String data = "Hello JABCode";
    
    for (ColorMode mode : ColorMode.values()) {
        if (mode == ColorMode.RESERVED_0) continue;
        
        // Encode
        BufferedImage image = encoder.encode(data, mode);
        assertNotNull(image);
        
        // Decode
        String result = decoder.decode(image);
        assertEquals(data, result, "Failed for mode: " + mode);
    }
}
```

---

## Known Limitations

### Modes 3-7 Not Standardized

**From Specification:**
> "Colour modes 0, 3, 4, 5, 6 and 7 are reserved for future extensions. These colour modes can also be used for user-defined colour modes." (Section 4.4.1.2)

**Impact:**
- Modes 1-2 are ISO standardized
- Modes 3-7 are "reserved" but fully specified in Annex G
- Implementation valid but considered "user-defined"
- May not be supported by all JABCode readers

### High-Color Mode Challenges

**Mode 6 (128 colors):**
- Requires precise printing (R channel step = 37 units)
- Interpolation may introduce ambiguity
- Best for controlled environments

**Mode 7 (256 colors):**
- Extremely challenging to print/scan accurately
- Dual-channel interpolation complex
- Recommended only for research/specialized applications

---

## Success Criteria

### Functional
- [ ] All 8 modes implemented
- [ ] Palette generation matches specification
- [ ] Encoding/decoding works for all modes
- [ ] Interpolation accurate (modes 6-7)
- [ ] Nc metadata encoding correct

### Quality
- [ ] Color distances meet specification
- [ ] Section 8.3.1 (Palette Accuracy) tests pass
- [ ] Section 8.3.2 (Color Variation) tests pass
- [ ] No regression in modes 1-2

### Performance
- [ ] Modes 1-2 within 10% of current implementation
- [ ] Modes 3-7 reasonable performance
- [ ] Memory usage acceptable

---

## Next Steps

1. **Review audit documents** (this directory)
2. **Prioritize implementation phases** based on requirements
3. **Start with Phase 1** (verify existing implementation)
4. **Implement extended modes** (Phases 2-3)
5. **Test thoroughly** (Phase 5)
6. **Integrate with Panama wrapper** (Phase 7)

---

## References

### ISO/IEC 23634 Sections

- **Section 3.3** - Nc mathematical symbol
- **Section 4.3.9** - Colour palette structure
- **Section 4.4.1.2** - Module colour mode (p. 14-15)
- **Section 5.7** - Data module encoding
- **Section 6.6** - Decoding metadata and colour palettes
- **Section 8.3** - JAB-Code colour verification
- **Annex G** - Guidelines for module colour selection (p. 67-70)

### Tables

- **Table 3** - 8-color palette
- **Table 4** - 4-color palette
- **Table 6** - Part I module colour modes
- **Table 7** - Nc encoding (3-color mode)
- **Table 21** - Bit encoding using module colours
- **Table G.1** - 16-colour mode RGB values
- **Table G.2** - User-defined colour modes

### Related Documents

- **`../01-jabcode-c-library-structure.md`** - C API reference
- **`../02-jni-wrapper-implementation.md`** - JNI patterns
- **`../03-panama-implementation-roadmap.md`** - Panama roadmap

---

## Metadata

| Field | Value |
|-------|-------|
| **Audit Date** | 2026-01-07 |
| **Specification** | ISO/IEC 23634:2022-04 |
| **Focus** | Color modes implementation |
| **Target** | Panama FFM wrapper |
| **Status** | ✅ Complete |
| **Effort Estimate** | 36-54 hours (1-2 weeks) |

---

## Summary

This audit provides everything needed to implement all 8 JABCode color modes:

✅ **Complete specifications** for modes 0-7  
✅ **Detailed RGB values** and generation algorithms  
✅ **Interpolation strategies** for high-color modes  
✅ **Concrete code examples** in Java  
✅ **Test strategies** for validation  
✅ **Phase-by-phase implementation plan** with effort estimates  

**Ready for implementation.** 🚀
