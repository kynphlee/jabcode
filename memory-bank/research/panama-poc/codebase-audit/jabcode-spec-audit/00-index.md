# JABCode ISO/IEC 23634 Specification Audit

## Executive Summary

This audit analyzes ISO/IEC 23634 ("JAB Code polychrome bar code symbology specification") with a focus on implementing all 8 color modes in the Panama FFM wrapper.

**Key Finding:** JABCode supports **8 color modes** (0-7), encoding from 4 to 256 colors, though only modes 1 and 2 are currently standardized. Modes 3-7 are reserved for future extensions or user-defined applications.

**Critical Implementation Requirement:** Annex G provides detailed guidance for implementing all color modes, including color space calculations, palette construction, and interpolation for high-color modes.

---

## Document Structure

### Quick Reference

- **00-index.md** (this file) - Overview and navigation
- **01-color-modes-overview.md** - Complete analysis of all 8 color modes
- **02-color-palette-construction.md** - Color palette implementation details
- **03-encoding-implementation.md** - Encoding algorithm for multi-color support
- **04-decoding-implementation.md** - Decoding algorithm for multi-color support
- **05-annex-g-analysis.md** - Deep dive into Annex G guidelines
- **06-implementation-checklist.md** - Concrete implementation tasks

---

## Color Modes Summary

| Mode | Nc Value | Colors | Status | Bit Encoding | Implementation |
|------|----------|--------|--------|--------------|----------------|
| 0 | `000` | Reserved | Future/User | N/A | Not standardized |
| 1 | `001` | 4 | ✅ Standardized | 2 bits/module | Standard |
| 2 | `010` | 8 | ✅ Standardized (Default) | 3 bits/module | Standard |
| 3 | `011` | 16 | 🔧 Reserved | 4 bits/module | Annex G |
| 4 | `100` | 32 | 🔧 Reserved | 5 bits/module | Annex G |
| 5 | `101` | 64 | 🔧 Reserved | 6 bits/module | Annex G |
| 6 | `110` | 128 | 🔧 Reserved | 7 bits/module | Annex G |
| 7 | `111` | 256 | 🔧 Reserved | 8 bits/module | Annex G |

**Legend:**
- ✅ Fully standardized in main specification
- 🔧 Reserved for future/user-defined use (guidelines in Annex G)

---

## Critical Specification Sections

### Section 4.3.9: Colour Palette (p. 13)

**Purpose:** Defines color palette structure and placement

**Key Points:**
- Minimum 4 colors, maximum 8 colors in standard modes
- Colors indexed 0-7
- Four color palettes placed near finder patterns
- Each palette excludes 2 colors used by nearest finder pattern

**Implementation Impact:** HIGH - Affects all color modes

### Section 4.4.1.2: Module Colour Mode (p. 14-15)

**Purpose:** Defines Nc variable and encoding

**Key Points:**
- Nc takes 3 bits (8 possible values)
- Encoded in 3-color mode (black, cyan, yellow)
- Default mode is 2 (8 colors)
- Modes 0, 3-7 reserved for extensions

**Implementation Impact:** CRITICAL - Must support all 8 modes

### Section 5.7: Data Module Encoding (p. 32)

**Purpose:** Defines bit-to-module mapping

**Key Points:**
- Module represents log₂(Nc) bits
- Bits correspond to color index in palette
- Table 21 shows encoding for modes 1 and 2

**Implementation Impact:** HIGH - Core encoding logic

### Annex G: Color Selection Guidelines (p. 67-70)

**Purpose:** Provides implementation guidance for all color modes

**Key Points:**
- RGB color space cube methodology
- Specific RGB values for each mode (16, 32, 64, 128, 256 colors)
- Palette embedding rules for >64 colors
- Color interpolation for 128 and 256 color modes

**Implementation Impact:** CRITICAL - Required for modes 3-7

---

## Implementation Priority

### Phase 1: Standard Modes (Required)

**Modes 1-2:** 4 and 8 colors
- Already supported in current implementation
- Verify correct encoding/decoding
- Test palette construction

### Phase 2: Extended Modes (High Priority)

**Mode 3:** 16 colors
- Add RGB value table from Annex G.1
- Implement 4-bit encoding
- Test with full palette embedding

**Mode 5:** 64 colors
- Maximum colors with full palette embedding
- No interpolation needed
- Good balance of capacity and complexity

### Phase 3: High-Color Modes (Advanced)

**Modes 4, 6, 7:** 32, 128, 256 colors
- Implement partial palette embedding
- Add color interpolation logic
- Test reconstruction algorithms

---

## Key Technical Challenges

### Challenge 1: Color Palette Embedding Limits

**Problem:** Only 128 modules reserved for color palettes (64 colors × 2 palettes)

**Solution (per Annex G):**
- Modes 1-5: Embed all colors
- Mode 6 (128 colors): Embed subset, interpolate R channel
- Mode 7 (256 colors): Embed subset, interpolate R and G channels

**Code Impact:** New interpolation functions needed

### Challenge 2: Variable Bit Encoding

**Problem:** Different modes use different bit widths per module

**Solution:**
- Mode 1: 2 bits → 4 colors
- Mode 2: 3 bits → 8 colors
- Mode 3: 4 bits → 16 colors
- Mode 4: 5 bits → 32 colors
- Mode 5: 6 bits → 64 colors
- Mode 6: 7 bits → 128 colors
- Mode 7: 8 bits → 256 colors

**Code Impact:** Dynamic bit packing/unpacking logic

### Challenge 3: Metadata Encoding

**Problem:** Nc encoded in 3-color mode only (black, cyan, yellow)

**Solution:**
- Part I metadata always uses 3 colors
- Part II and data use full color palette
- Table 7 provides 3-color to binary mapping

**Code Impact:** Separate encoding path for Nc

---

## RGB Color Space Strategy

### Core Principle (from Annex G.1)

Colors should be maximally distinguishable in RGB color space cube.

**RGB Cube Vertices (8 colors - Mode 2):**
```
(0,0,0)       Black     [000]
(0,0,255)     Blue      [001]
(0,255,0)     Green     [010]
(0,255,255)   Cyan      [011]
(255,0,0)     Red       [100]
(255,0,255)   Magenta   [101]
(255,255,0)   Yellow    [110]
(255,255,255) White     [111]
```

**Extension Strategy:**
- 16 colors: Add R = 85, 170 (4 R values × 2 G × 2 B)
- 32 colors: Add G = 85, 170 (4 R × 4 G × 2 B)
- 64 colors: Add B = 85, 170 (4 R × 4 G × 4 B)
- 128 colors: Add R = 36, 73, 109, 146, 182, 219 (8 R × 4 G × 4 B)
- 256 colors: Add G = 36, 73, 109, 146, 182, 219 (8 R × 8 G × 4 B)

---

## Implementation Guidelines by Mode

### Mode 1: 4 Colors (Standard)

**Colors:** Black, Cyan, Magenta, Yellow

**Encoding:** 2 bits per module
- `00` → Black (0)
- `01` → Cyan (1)
- `10` → Magenta (2)
- `11` → Yellow (3)

**Palette:** All 4 colors embedded

**Implementation:** Already supported, verify correctness

### Mode 2: 8 Colors (Default, Standard)

**Colors:** RGB cube vertices

**Encoding:** 3 bits per module (see table above)

**Palette:** All 8 colors embedded

**Implementation:** Already supported, verify correctness

### Mode 3: 16 Colors (Reserved/User)

**Colors:** Per Annex G, Table G.1

**Encoding:** 4 bits per module

**R Values:** 0, 85, 170, 255  
**G Values:** 0, 255  
**B Values:** 0, 255

**Palette:** All 16 colors embedded (fits in 64-color limit)

**Implementation:** Add RGB table from Annex G

### Mode 4: 32 Colors (Reserved/User)

**Colors:** Per Annex G.3(b)

**Encoding:** 5 bits per module

**R Values:** 0, 85, 170, 255  
**G Values:** 0, 85, 170, 255  
**B Values:** 0, 255

**Palette:** All 32 colors embedded (fits in 64-color limit)

**Implementation:** Generate from channel values

### Mode 5: 64 Colors (Reserved/User)

**Colors:** Per Annex G.3(c)

**Encoding:** 6 bits per module

**R Values:** 0, 85, 170, 255  
**G Values:** 0, 85, 170, 255  
**B Values:** 0, 85, 170, 255

**Palette:** All 64 colors embedded (exactly fills limit)

**Implementation:** Generate from channel values

### Mode 6: 128 Colors (Reserved/User)

**Colors:** Per Annex G.3(d)

**Encoding:** 7 bits per module

**R Values:** 0, 36, 73, 109, 146, 182, 219, 255 (8 values)  
**G Values:** 0, 85, 170, 255 (4 values)  
**B Values:** 0, 85, 170, 255 (4 values)

**Palette:** Only 64 colors embedded (R = 0, 73, 182, 255)

**Interpolation:** R channel interpolated during decoding

**Implementation:** Add interpolation logic

### Mode 7: 256 Colors (Reserved/User)

**Colors:** Per Annex G.3(e)

**Encoding:** 8 bits per module (1 byte)

**R Values:** 0, 36, 73, 109, 146, 182, 219, 255 (8 values)  
**G Values:** 0, 36, 73, 109, 146, 182, 219, 255 (8 values)  
**B Values:** 0, 85, 170, 255 (4 values)

**Palette:** Only 64 colors embedded (R, G = 0, 73, 182, 255)

**Interpolation:** R and G channels interpolated during decoding

**Implementation:** Add interpolation logic

---

## Test Strategy

### Unit Tests

1. **Color Palette Generation**
   - Test each mode generates correct RGB values
   - Verify color indices match specification
   - Check palette size limits

2. **Bit Encoding/Decoding**
   - Test variable bit width packing
   - Verify color index to RGB mapping
   - Test boundary conditions

3. **Interpolation (Modes 6-7)**
   - Test R channel interpolation
   - Test R+G channel interpolation
   - Verify reconstructed palette accuracy

### Integration Tests

1. **Round-trip Encoding/Decoding**
   - Encode data in each mode
   - Decode and verify match
   - Test all supported modes

2. **Metadata Handling**
   - Test Nc encoding in 3-color mode
   - Verify correct mode selection
   - Test mode detection in decoder

### Compliance Tests

1. **ISO Conformance**
   - Verify against Annex D example
   - Test symbol structure
   - Validate metadata format

2. **Color Accuracy**
   - Test against Section 8.3.1 (Colour Palette Accuracy)
   - Verify Section 8.3.2 (Colour Variation)
   - Measure against quality thresholds

---

## Implementation Files

### New Files Needed

1. **`ColorPalettes.java`**
   - Generate RGB values for all modes
   - Implement palette embedding logic
   - Handle interpolation for modes 6-7

2. **`ColorModeEncoder.java`**
   - Variable bit-width encoding
   - Mode-specific module placement
   - Nc metadata encoding

3. **`ColorModeDecoder.java`**
   - Variable bit-width decoding
   - Palette extraction and reconstruction
   - Interpolation logic

4. **`ColorSpaceUtils.java`**
   - RGB distance calculations
   - Color matching algorithms
   - Interpolation functions

### Modified Files

1. **`JABCodeEncoder.java`**
   - Add color mode parameter
   - Support modes 1-7
   - Use new palette generation

2. **`JABCodeDecoder.java`**
   - Detect color mode from Nc
   - Apply mode-specific decoding
   - Reconstruct palettes

---

## References

### Specification Sections

- **Section 3.3** - Nc mathematical symbol definition (p. 3)
- **Section 4.3.9** - Colour palette structure (p. 13)
- **Section 4.4.1.2** - Module colour mode (p. 14-15)
- **Section 5.7** - Data module encoding (p. 32)
- **Section 6.6** - Decoding metadata and constructing colour palettes (p. 45)
- **Annex G** - Guidelines for module colour selection (p. 67-70)
- **Table 6** - Part I module colour modes (p. 15)
- **Table G.1** - 16-colour mode RGB values (p. 67-68)
- **Table G.2** - User-defined colour modes (p. 69)

### Related Documents

- **01-color-modes-overview.md** - Detailed analysis of each mode
- **02-color-palette-construction.md** - Palette implementation
- **05-annex-g-analysis.md** - Deep dive into Annex G
- **06-implementation-checklist.md** - Implementation tasks

---

## Status

✅ **Audit Complete**

**Next Steps:**
1. Review detailed analysis documents
2. Implement color palette generation
3. Add support for modes 3-7
4. Test against specification examples

**Estimated Effort:** 16-24 hours for full implementation
- Core palette logic: 8 hours
- Interpolation: 4 hours
- Testing: 4-8 hours
- Integration: 4 hours
