# JABCode Color Modes: Implementation Checklist

## Overview

This document provides a concrete, actionable checklist for implementing all 8 JABCode color modes in the Panama FFM wrapper.

**Goal:** Support all color modes (Nc = 0-7) as defined in ISO/IEC 23634

---

## Phase 1: Foundation (Required)

### Task 1.1: Color Palette Infrastructure

- [ ] Create `ColorPalette` interface
  ```java
  public interface ColorPalette {
      int[][] generateFullPalette();
      int[][] generateEmbeddedPalette();
      int[] getRGB(int colorIndex);
      int getColorIndex(int r, int g, int b);
      int getBitsPerModule();
      int getColorCount();
  }
  ```

- [ ] Create `ColorMode` enum
  ```java
  public enum ColorMode {
      RESERVED_0(0, 0),
      MODE_4(1, 4),
      MODE_8(2, 8),
      MODE_16(3, 16),
      MODE_32(4, 32),
      MODE_64(5, 64),
      MODE_128(6, 128),
      MODE_256(7, 256);
      
      private final int ncValue;
      private final int colorCount;
      
      public int getBitsPerModule() {
          return (int)Math.ceil(Math.log(colorCount) / Math.log(2));
      }
  }
  ```

- [ ] Create `ColorUtils` utility class
  - [ ] Euclidean color distance calculation
  - [ ] Weighted color distance (perceptual)
  - [ ] Find nearest color in palette
  - [ ] RGB to index conversion
  - [ ] Index to RGB conversion

### Task 1.2: Verify Existing Implementation

- [ ] Audit current code for Mode 1 (4 colors)
  - [ ] Verify RGB values match Table 4
  - [ ] Test encoding/decoding
  - [ ] Measure color distances

- [ ] Audit current code for Mode 2 (8 colors)
  - [ ] Verify RGB values match Table 3
  - [ ] Test encoding/decoding
  - [ ] Verify as default mode
  - [ ] Measure color distances

- [ ] Document any deviations from specification

### Task 1.3: Test Infrastructure

- [ ] Create `ColorModeTest` base class
- [ ] Add test for palette generation
- [ ] Add test for color distance calculations
- [ ] Add test for round-trip encoding/decoding
- [ ] Add test for Nc metadata encoding (3-color mode)

---

## Phase 2: Extended Modes (No Interpolation)

### Task 2.1: Implement Mode 3 (16 Colors)

- [ ] Create `Mode3Palette` class
  ```java
  public class Mode3Palette implements ColorPalette {
      private static final int[] R_VALUES = {0, 85, 170, 255};
      private static final int[] G_VALUES = {0, 255};
      private static final int[] B_VALUES = {0, 255};
      // ... implementation
  }
  ```

- [ ] Generate palette matching Table G.1
- [ ] Verify all 16 colors generated correctly
- [ ] Test 4-bit encoding/decoding
- [ ] Calculate minimum color distance (should be 85)
- [ ] Add unit tests

### Task 2.2: Implement Mode 4 (32 Colors)

- [ ] Create `Mode4Palette` class
  ```java
  public class Mode4Palette implements ColorPalette {
      private static final int[] R_VALUES = {0, 85, 170, 255};
      private static final int[] G_VALUES = {0, 85, 170, 255};
      private static final int[] B_VALUES = {0, 255};
      // ... implementation
  }
  ```

- [ ] Generate 32-color palette per Annex G.3(b)
- [ ] Test 5-bit encoding/decoding
- [ ] Calculate minimum color distance (should be 85)
- [ ] Add unit tests

### Task 2.3: Implement Mode 5 (64 Colors)

- [ ] Create `Mode5Palette` class
  ```java
  public class Mode5Palette implements ColorPalette {
      private static final int[] RGB_VALUES = {0, 85, 170, 255};
      // 4 × 4 × 4 = 64 colors
  }
  ```

- [ ] Generate 64-color palette per Annex G.3(c)
- [ ] Test 6-bit encoding/decoding
- [ ] Verify symmetric color space (4×4×4)
- [ ] Calculate minimum color distance (should be 85)
- [ ] **Verify exactly fills 64-color embedded palette limit**
- [ ] Add unit tests

---

## Phase 3: High-Color Modes (With Interpolation)

### Task 3.1: Implement Interpolation Framework

- [ ] Create `ColorInterpolator` interface
  ```java
  public interface ColorInterpolator {
      int[] interpolateChannel(int embeddedValue, int[] embeddedValues,
                               int[] fullValues);
      int[][] reconstructFullPalette(int[][] embeddedPalette);
  }
  ```

- [ ] Create `LinearInterpolator` implementation
  ```java
  public class LinearInterpolator implements ColorInterpolator {
      // Interpolate between nearest embedded values
  }
  ```

- [ ] Add tests for interpolation accuracy

### Task 3.2: Implement Mode 6 (128 Colors)

- [ ] Create `Mode6Palette` class with interpolation
  ```java
  public class Mode6Palette implements ColorPalette {
      // Full: 8 R × 4 G × 4 B
      private static final int[] R_FULL = 
          {0, 36, 73, 109, 146, 182, 219, 255};
      
      // Embedded: 4 R × 4 G × 4 B = 64
      private static final int[] R_EMBEDDED = {0, 73, 182, 255};
  }
  ```

- [ ] Generate full 128-color palette per Annex G.3(d)
- [ ] Generate embedded 64-color subset
- [ ] Implement R-channel interpolation
  - [ ] Map {0, 36} ← 0
  - [ ] Map {36, 73, 109, 146} ← 73
  - [ ] Map {146, 182, 219} ← 182
  - [ ] Map {219, 255} ← 255

- [ ] Test palette reconstruction from embedded subset
- [ ] Test 7-bit encoding/decoding
- [ ] Verify reconstructed palette matches full palette
- [ ] Add unit tests

### Task 3.3: Implement Mode 7 (256 Colors)

- [ ] Create `Mode7Palette` class with dual interpolation
  ```java
  public class Mode7Palette implements ColorPalette {
      // Full: 8 R × 8 G × 4 B = 256
      private static final int[] RG_FULL = 
          {0, 36, 73, 109, 146, 182, 219, 255};
      
      // Embedded: 4 R × 4 G × 4 B = 64
      private static final int[] RG_EMBEDDED = {0, 73, 182, 255};
  }
  ```

- [ ] Generate full 256-color palette per Annex G.3(e)
- [ ] Generate embedded 64-color subset
- [ ] Implement R-channel interpolation
- [ ] Implement G-channel interpolation (same as R)
- [ ] Test dual-channel reconstruction
- [ ] Test 8-bit encoding/decoding (1 byte per module)
- [ ] Verify reconstructed palette
- [ ] Add unit tests

---

## Phase 4: Encoding Integration

### Task 4.1: Update Encoder

- [ ] Add color mode parameter to encoder configuration
  ```java
  public class EncoderConfig {
      private ColorMode colorMode = ColorMode.MODE_8; // Default
      // ...
  }
  ```

- [ ] Implement mode selection logic
- [ ] Update bit packing to support variable bit widths
  - [ ] 2 bits (Mode 1)
  - [ ] 3 bits (Mode 2)
  - [ ] 4 bits (Mode 3)
  - [ ] 5 bits (Mode 4)
  - [ ] 6 bits (Mode 5)
  - [ ] 7 bits (Mode 6)
  - [ ] 8 bits (Mode 7)

- [ ] Update Nc metadata encoding (3-color mode for Nc itself)
  ```java
  // Nc encoded using only black, cyan, yellow
  // Per Table 7
  ```

- [ ] Implement palette embedding logic
  - [ ] Full embedding for modes 1-5
  - [ ] Subset embedding for modes 6-7

### Task 4.2: Update Decoder

- [ ] Decode Nc from metadata (Part I)
- [ ] Extract and parse color mode
- [ ] Extract embedded color palette from symbol
- [ ] Reconstruct full palette if needed (modes 6-7)
- [ ] Implement variable bit-width unpacking
- [ ] Map color indices back to RGB values

### Task 4.3: Bit Packing/Unpacking

- [ ] Create `BitStreamEncoder`
  ```java
  public class BitStreamEncoder {
      public void writeBits(int value, int bitCount);
      public void alignToByte();
      public byte[] toByteArray();
  }
  ```

- [ ] Create `BitStreamDecoder`
  ```java
  public class BitStreamDecoder {
      public int readBits(int bitCount);
      public boolean hasMore();
  }
  ```

- [ ] Test with all bit widths (2-8 bits)

---

## Phase 5: Testing and Validation

### Task 5.1: Unit Tests

For each color mode:

- [ ] Test palette generation
  - [ ] Verify color count
  - [ ] Verify RGB values match specification
  - [ ] Verify color indices

- [ ] Test color distance calculations
  - [ ] Calculate minimum pairwise distance
  - [ ] Verify meets specification requirements
  - [ ] Calculate dR, dG, dB for quality checks

- [ ] Test encoding
  - [ ] Encode sample data
  - [ ] Verify bit widths
  - [ ] Verify color mapping

- [ ] Test decoding
  - [ ] Decode encoded data
  - [ ] Verify data integrity
  - [ ] Test round-trip

- [ ] Test interpolation (modes 6-7 only)
  - [ ] Verify embedded subset generation
  - [ ] Test palette reconstruction
  - [ ] Verify accuracy

### Task 5.2: Integration Tests

- [ ] Test mode switching
  - [ ] Encode same data in all modes
  - [ ] Verify symbols generated correctly
  - [ ] Compare symbol sizes

- [ ] Test Nc metadata encoding
  - [ ] Encode Nc in 3-color mode
  - [ ] Decode and verify mode detected correctly

- [ ] Test palette embedding/extraction
  - [ ] Embed palette in symbol
  - [ ] Extract palette
  - [ ] Verify matches original

### Task 5.3: Performance Tests

- [ ] Benchmark encoding speed for each mode
- [ ] Benchmark decoding speed for each mode
- [ ] Measure memory usage
- [ ] Compare with JNI implementation

### Task 5.4: Quality Tests (ISO Section 8.3)

- [ ] Implement Colour Palette Accuracy test (Section 8.3.1)
  ```java
  // Calculate CAP (Colour Accuracy Palette)
  // CAP = |RCol - RMod| / dR + |GCol - GMod| / dG + |BCol - BMod| / dB
  // Grade: CAP < 0.2 → 4.0, CAP < 0.4 → 3.0, etc.
  ```

- [ ] Implement Colour Variation test (Section 8.3.2)
  ```java
  // Calculate CVDM (Colour Variation in Data Modules)
  ```

- [ ] Test against quality thresholds for each mode

---

## Phase 6: Documentation

### Task 6.1: API Documentation

- [ ] Document `ColorMode` enum
- [ ] Document each palette class
- [ ] Document encoder color mode parameter
- [ ] Document decoder color mode detection
- [ ] Add Javadoc examples

### Task 6.2: User Guide

- [ ] Create color mode selection guide
  - [ ] When to use each mode
  - [ ] Trade-offs (capacity vs reliability)
  - [ ] Printing requirements

- [ ] Add code examples for each mode

- [ ] Document limitations
  - [ ] Modes 0, 3-7 are user-defined (not fully standardized)
  - [ ] Higher modes require better printing/scanning

### Task 6.3: Implementation Notes

- [ ] Document deviations from spec (if any)
- [ ] Document assumptions made
- [ ] Document interpolation algorithm details
- [ ] Performance characteristics of each mode

---

## Phase 7: Integration with Panama Wrapper

### Task 7.1: Update JABCodeEncoder

- [ ] Add color mode to encoder configuration
  ```java
  public class JABCodeEncoder {
      public BufferedImage encode(String data, ColorMode mode) {
          // ...
      }
  }
  ```

- [ ] Map Java ColorMode to C Nc value
- [ ] Pass to native createEncode function

### Task 7.2: Update JABCodeDecoder

- [ ] Read Nc from decoded metadata
- [ ] Map to ColorMode enum
- [ ] Use correct palette for color interpretation

### Task 7.3: Update Config Classes

- [ ] Add colorMode field to encoding config
- [ ] Add validation (modes 3-7 require acknowledgment)
- [ ] Set default to MODE_8 (Nc=2)

---

## Success Criteria

### Functional Requirements

- [ ] All 8 color modes implemented
- [ ] Modes 1-2 match current implementation
- [ ] Modes 3-5 generate correct palettes
- [ ] Modes 6-7 interpolation works correctly
- [ ] Round-trip encoding/decoding works for all modes
- [ ] Nc metadata encoding works (3-color mode)

### Quality Requirements

- [ ] Minimum color distances meet specification
- [ ] Palette accuracy tests pass
- [ ] Color variation tests pass
- [ ] No regression in modes 1-2

### Performance Requirements

- [ ] Encoding speed within 10% of JNI for modes 1-2
- [ ] Modes 3-7 encoding reasonable (within 20% of mode 2)
- [ ] Decoding speed within 15% of JNI
- [ ] Memory usage acceptable

### Documentation Requirements

- [ ] All public APIs documented
- [ ] User guide complete
- [ ] Examples for all modes
- [ ] Compliance with ISO noted

---

## Risk Assessment

### High Risk Items

1. **Mode 6-7 Interpolation**
   - Risk: Complex algorithm, ambiguous spec
   - Mitigation: Extensive testing, reference implementation study

2. **Color Matching Accuracy**
   - Risk: Poor printing/scanning affects high-color modes
   - Mitigation: Add quality thresholds, warn users

3. **Performance Degradation**
   - Risk: More colors = more processing
   - Mitigation: Optimize hot paths, profile

### Medium Risk Items

1. **Palette Embedding Limits**
   - Risk: Misunderstanding 64-color limit
   - Mitigation: Verify against spec examples

2. **Bit Packing Edge Cases**
   - Risk: Variable bit widths introduce bugs
   - Mitigation: Comprehensive unit tests

---

## Estimated Effort

| Phase | Tasks | Hours | Priority |
|-------|-------|-------|----------|
| Phase 1: Foundation | 3 | 4-6 | High |
| Phase 2: Extended Modes | 3 | 6-8 | High |
| Phase 3: High-Color Modes | 3 | 8-12 | Medium |
| Phase 4: Integration | 3 | 4-6 | High |
| Phase 5: Testing | 4 | 6-10 | High |
| Phase 6: Documentation | 3 | 4-6 | Medium |
| Phase 7: Panama Integration | 3 | 4-6 | High |
| **Total** | **22** | **36-54** | - |

**Recommended Schedule:**
- Week 1: Phases 1-2 (Foundation + Extended Modes)
- Week 2: Phase 3-4 (High-Color + Integration)
- Week 3: Phases 5-7 (Testing + Docs + Panama)

---

## Quick Start

### Minimal Implementation (Modes 1-2 only)

If you want to start with just verifying standard modes:

1. ✅ Implement Mode 1 palette
2. ✅ Implement Mode 2 palette
3. ✅ Add tests for both
4. ✅ Verify against current implementation
5. ✅ Document compliance

**Effort:** 4-6 hours

### Recommended Implementation (Modes 1-5)

For practical use without interpolation complexity:

1. ✅ Complete Phase 1 (Foundation)
2. ✅ Complete Phase 2 (Extended Modes)
3. ✅ Add tests
4. ✅ Integrate with Panama wrapper

**Effort:** 16-24 hours

### Full Implementation (All Modes)

For complete ISO compliance:

1. ✅ Follow all phases in order
2. ✅ Complete all checklist items
3. ✅ Pass all tests

**Effort:** 36-54 hours (1-2 weeks)

---

## References

### Specification Sections

- Section 4.4.1.2 - Module colour mode
- Section 5.7 - Data module encoding
- Section 6.6 - Decoding colour palettes
- Section 8.3 - JAB-Code colour verification
- Annex G - Guidelines for module colour selection
- Table 3, 4 - Standard color palettes
- Table 6 - Part I module colour modes
- Table 7 - Nc encoding (3-color mode)
- Table 21 - Bit encoding using module colours
- Table G.1 - 16-colour mode
- Table G.2 - User-defined colour modes

### Related Documents

- **00-index.md** - Overview and navigation
- **01-color-modes-overview.md** - Detailed mode specifications
- **02-color-palette-construction.md** - Palette implementation
- **05-annex-g-analysis.md** - Deep dive into Annex G

---

## Completion Checklist

After implementation, verify:

- [ ] All phases completed
- [ ] All tests passing
- [ ] Documentation complete
- [ ] Performance acceptable
- [ ] Code reviewed
- [ ] Merged to panama-poc branch
- [ ] Tagged release created

**Status:** Ready for implementation ✅
