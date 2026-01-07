# JABCode Color Modes: Complete Technical Overview

## Introduction

JABCode ISO/IEC 23634 defines 8 color modes (Nc = 0 through 7) enabling encoding from 4 to 256 colors per symbol. This document provides complete technical specifications for implementing all color modes.

**Source:** ISO/IEC 23634:2022-04, Section 4.4.1.2, Table 6, and Annex G

---

## Color Mode Architecture

### Nc Variable (Module Colour Mode)

**Definition:** 3-bit value indicating the number of module colors in the symbol

**Encoding:** Part I metadata, encoded in 3-color mode (black, cyan, yellow only)

**Binary Format:**
```
Nc = [b₂ b₁ b₀]  (3 bits)

Values: 000 to 111 (0 to 7 decimal)
```

**Encoding Method (Table 7):**

| Nc Binary | First Module Color | Second Module Color |
|-----------|-------------------|---------------------|
| 000 | Black | Black |
| 001 | Black | Cyan |
| 010 | Black | Yellow |
| 011 | Cyan | Black |
| 100 | Cyan | Cyan |
| 101 | Cyan | Yellow |
| 110 | Yellow | Black |
| 111 | Yellow | Cyan |

**Critical:** Nc itself is encoded using only 3 colors, but the data it represents determines how many colors are used in the rest of the symbol.

---

## Mode 0: Reserved

### Specification

- **Nc Value:** `000` (binary) / 0 (decimal)
- **Number of Colors:** Reserved for future extensions
- **Bit Encoding:** Not defined
- **Status:** Reserved

### Implementation Guidance

**From Specification:**
> "Colour mode 0 is reserved for future extensions. This colour mode can also be used for user-defined colour modes." (Section 4.4.1.2)

**Recommendation:** Treat as error condition or use for custom applications outside ISO compliance.

**Implementation:**
```java
if (nc == 0) {
    throw new IllegalArgumentException(
        "Color mode 0 is reserved and not supported");
    // OR: Use custom user-defined color palette
}
```

---

## Mode 1: 4 Colors (Standard)

### Specification

- **Nc Value:** `001` (binary) / 1 (decimal)
- **Number of Colors:** 4
- **Bits per Module:** log₂(4) = 2 bits
- **Status:** ✅ Fully standardized

### Color Palette (Table 4)

| Index | Color | RGB Values | Binary |
|-------|-------|------------|--------|
| 0 | Black | (0, 0, 0) | 00 |
| 1 | Cyan | (0, 255, 255) | 01 |
| 2 | Magenta | (255, 0, 255) | 10 |
| 3 | Yellow | (255, 255, 0) | 11 |

### Implementation Details

**Bit Encoding:**
```java
// Encode: Color index → 2 bits
byte[] encodeBits(int colorIndex) {
    // 0 → 00, 1 → 01, 2 → 10, 3 → 11
    return new byte[] {
        (byte)((colorIndex >> 1) & 1),
        (byte)(colorIndex & 1)
    };
}

// Decode: 2 bits → Color index
int decodeColorIndex(byte bit1, byte bit0) {
    return (bit1 << 1) | bit0;
}
```

**Module Capacity:**
```java
// 2 bits per module
int bitsPerModule = 2;
int totalModules = symbolWidth * symbolHeight - fixedPatternModules;
int dataCapacity = totalModules * bitsPerModule;
```

### Use Cases

- **Simple applications** with limited color printing
- **High reliability** requirements
- **Low-cost** printing technologies
- **Monochrome printers** with color overlays

### Advantages

- Maximum color distance in RGB space
- Easiest to print and scan
- Best error resilience
- Smallest palette (4 colors)

### Disadvantages

- Lowest data density (2 bits/module)
- Largest symbol size for same data
- Limited data capacity

---

## Mode 2: 8 Colors (Default, Standard)

### Specification

- **Nc Value:** `010` (binary) / 2 (decimal)
- **Number of Colors:** 8
- **Bits per Module:** log₂(8) = 3 bits
- **Status:** ✅ Fully standardized (Default mode)

### Color Palette (Table 3)

| Index | Color | RGB Values | Binary | Hex |
|-------|-------|------------|--------|-----|
| 0 | Black | (0, 0, 0) | 000 | #000000 |
| 1 | Blue | (0, 0, 255) | 001 | #0000FF |
| 2 | Green | (0, 255, 0) | 010 | #00FF00 |
| 3 | Cyan | (0, 255, 255) | 011 | #00FFFF |
| 4 | Red | (255, 0, 0) | 100 | #FF0000 |
| 5 | Magenta | (255, 0, 255) | 101 | #FF00FF |
| 6 | Yellow | (255, 255, 0) | 110 | #FFFF00 |
| 7 | White | (255, 255, 255) | 111 | #FFFFFF |

**Geometric Property:** These are the 8 vertices of the RGB color cube (0, 0, 0) to (255, 255, 255).

### Implementation Details

**Bit Encoding:**
```java
// Encode: Color index → 3 bits
byte[] encodeBits(int colorIndex) {
    // 0 → 000, 1 → 001, ..., 7 → 111
    return new byte[] {
        (byte)((colorIndex >> 2) & 1),
        (byte)((colorIndex >> 1) & 1),
        (byte)(colorIndex & 1)
    };
}

// Decode: 3 bits → Color index
int decodeColorIndex(byte bit2, byte bit1, byte bit0) {
    return (bit2 << 2) | (bit1 << 1) | bit0;
}
```

**RGB Lookup:**
```java
static final int[][] MODE2_PALETTE = {
    {0,   0,   0  }, // 0: Black
    {0,   0,   255}, // 1: Blue
    {0,   255, 0  }, // 2: Green
    {0,   255, 255}, // 3: Cyan
    {255, 0,   0  }, // 4: Red
    {255, 0,   255}, // 5: Magenta
    {255, 255, 0  }, // 6: Yellow
    {255, 255, 255}  // 7: White
};

int[] getRGB(int colorIndex) {
    return MODE2_PALETTE[colorIndex];
}
```

### Use Cases

- **Default mode** for general applications
- **Good balance** between capacity and reliability
- **Standard color printers**
- **Most applications**

### Advantages

- ISO default mode
- 50% more capacity than mode 1
- Well-tested and widely supported
- All colors are RGB cube vertices (maximum distance)

### Disadvantages

- Still relatively large symbols
- Requires accurate color printing

---

## Mode 3: 16 Colors (Reserved)

### Specification

- **Nc Value:** `011` (binary) / 3 (decimal)
- **Number of Colors:** 16
- **Bits per Module:** log₂(16) = 4 bits
- **Status:** 🔧 Reserved (User-defined, Annex G)

### Color Palette (Table G.1)

**Color Generation Rule (Annex G.3a):**
- R channel: 4 values (0, 85, 170, 255)
- G channel: 2 values (0, 255)
- B channel: 2 values (0, 255)
- Total: 4 × 2 × 2 = 16 colors

| Index | R | G | B | Binary | Hex |
|-------|---|---|---|--------|-----|
| 0 | 0 | 0 | 0 | 0000 | #000000 |
| 1 | 0 | 0 | 255 | 0001 | #0000FF |
| 2 | 0 | 255 | 0 | 0010 | #00FF00 |
| 3 | 0 | 255 | 255 | 0011 | #00FFFF |
| 4 | 85 | 0 | 0 | 0100 | #550000 |
| 5 | 85 | 0 | 255 | 0101 | #5500FF |
| 6 | 85 | 255 | 0 | 0110 | #55FF00 |
| 7 | 85 | 255 | 255 | 0111 | #55FFFF |
| 8 | 170 | 0 | 0 | 1000 | #AA0000 |
| 9 | 170 | 0 | 255 | 1001 | #AA00FF |
| 10 | 170 | 255 | 0 | 1010 | #AAFF00 |
| 11 | 170 | 255 | 255 | 1011 | #AAFFFF |
| 12 | 255 | 0 | 0 | 1100 | #FF0000 |
| 13 | 255 | 0 | 255 | 1101 | #FF00FF |
| 14 | 255 | 255 | 0 | 1110 | #FFFF00 |
| 15 | 255 | 255 | 255 | 1111 | #FFFFFF |

### Implementation Details

**Color Generation:**
```java
static int[][] generateMode3Palette() {
    int[] rValues = {0, 85, 170, 255};
    int[] gValues = {0, 255};
    int[] bValues = {0, 255};
    
    int[][] palette = new int[16][3];
    int index = 0;
    
    for (int r : rValues) {
        for (int g : gValues) {
            for (int b : bValues) {
                palette[index][0] = r;
                palette[index][1] = g;
                palette[index][2] = b;
                index++;
            }
        }
    }
    
    return palette;
}
```

**Bit Encoding:**
```java
// 4 bits per module
byte[] encodeBits(int colorIndex) {
    return new byte[] {
        (byte)((colorIndex >> 3) & 1),
        (byte)((colorIndex >> 2) & 1),
        (byte)((colorIndex >> 1) & 1),
        (byte)(colorIndex & 1)
    };
}
```

### Use Cases

- **Higher data density** requirements
- **Custom applications** with controlled printing
- **Laboratory** or specialized environments

### Advantages

- 2× capacity vs mode 2
- All colors fit in embedded palette (≤64 limit)
- No interpolation required

### Disadvantages

- Closer color spacing (R channel)
- Requires precise color control
- Not standardized (user-defined)

---

## Mode 4: 32 Colors (Reserved)

### Specification

- **Nc Value:** `100` (binary) / 4 (decimal)
- **Number of Colors:** 32
- **Bits per Module:** log₂(32) = 5 bits
- **Status:** 🔧 Reserved (User-defined, Annex G)

### Color Palette

**Color Generation Rule (Annex G.3b):**
- R channel: 4 values (0, 85, 170, 255)
- G channel: 4 values (0, 85, 170, 255)
- B channel: 2 values (0, 255)
- Total: 4 × 4 × 2 = 32 colors

### Implementation Details

**Color Generation:**
```java
static int[][] generateMode4Palette() {
    int[] rValues = {0, 85, 170, 255};
    int[] gValues = {0, 85, 170, 255};
    int[] bValues = {0, 255};
    
    int[][] palette = new int[32][3];
    int index = 0;
    
    for (int r : rValues) {
        for (int g : gValues) {
            for (int b : bValues) {
                palette[index][0] = r;
                palette[index][1] = g;
                palette[index][2] = b;
                index++;
            }
        }
    }
    
    return palette;
}
```

**Bit Encoding:**
```java
// 5 bits per module
byte[] encodeBits(int colorIndex) {
    return new byte[] {
        (byte)((colorIndex >> 4) & 1),
        (byte)((colorIndex >> 3) & 1),
        (byte)((colorIndex >> 2) & 1),
        (byte)((colorIndex >> 1) & 1),
        (byte)(colorIndex & 1)
    };
}
```

### Use Cases

- **Very high density** barcodes
- **Specialized applications**
- **Research and development**

### Advantages

- 2.5× capacity vs mode 2
- All colors fit in embedded palette
- No interpolation required

### Disadvantages

- Even closer color spacing
- Difficult to print/scan accurately
- Not standardized

---

## Mode 5: 64 Colors (Reserved)

### Specification

- **Nc Value:** `101` (binary) / 5 (decimal)
- **Number of Colors:** 64
- **Bits per Module:** log₂(64) = 6 bits
- **Status:** 🔧 Reserved (User-defined, Annex G)

### Color Palette

**Color Generation Rule (Annex G.3c):**
- R channel: 4 values (0, 85, 170, 255)
- G channel: 4 values (0, 85, 170, 255)
- B channel: 4 values (0, 85, 170, 255)
- Total: 4 × 4 × 4 = 64 colors

**Special Property:** Exactly fills the 64-color embedded palette limit.

### Implementation Details

**Color Generation:**
```java
static int[][] generateMode5Palette() {
    int[] rgbValues = {0, 85, 170, 255};
    
    int[][] palette = new int[64][3];
    int index = 0;
    
    for (int r : rgbValues) {
        for (int g : rgbValues) {
            for (int b : rgbValues) {
                palette[index][0] = r;
                palette[index][1] = g;
                palette[index][2] = b;
                index++;
            }
        }
    }
    
    return palette;
}
```

**Bit Encoding:**
```java
// 6 bits per module
byte[] encodeBits(int colorIndex) {
    return new byte[] {
        (byte)((colorIndex >> 5) & 1),
        (byte)((colorIndex >> 4) & 1),
        (byte)((colorIndex >> 3) & 1),
        (byte)((colorIndex >> 2) & 1),
        (byte)((colorIndex >> 1) & 1),
        (byte)(colorIndex & 1)
    };
}
```

### Use Cases

- **Maximum density** without interpolation
- **Critical:** Last mode with full palette embedding

### Advantages

- 3× capacity vs mode 2
- All colors embedded (no interpolation)
- Symmetric color space (4×4×4)

### Disadvantages

- Very close color spacing
- Challenging printing requirements
- Requires precise color measurement

---

## Mode 6: 128 Colors (Reserved)

### Specification

- **Nc Value:** `110` (binary) / 6 (decimal)
- **Number of Colors:** 128
- **Bits per Module:** log₂(128) = 7 bits
- **Status:** 🔧 Reserved (User-defined, Annex G)

### Color Palette

**Color Generation Rule (Annex G.3d):**
- R channel: 8 values (0, 36, 73, 109, 146, 182, 219, 255)
- G channel: 4 values (0, 85, 170, 255)
- B channel: 4 values (0, 85, 170, 255)
- Total: 8 × 4 × 4 = 128 colors

**Critical:** Only 64 colors can be embedded. Requires interpolation.

### Embedded Palette (Annex G.3b)

**Subset Rule:** Include only R values {0, 73, 182, 255}
- Embedded: 4 × 4 × 4 = 64 colors
- Missing R values: {36, 109, 146, 219}

### Implementation Details

**Full Palette Generation:**
```java
static int[][] generateMode6Palette() {
    int[] rValues = {0, 36, 73, 109, 146, 182, 219, 255};
    int[] gValues = {0, 85, 170, 255};
    int[] bValues = {0, 85, 170, 255};
    
    int[][] palette = new int[128][3];
    int index = 0;
    
    for (int r : rValues) {
        for (int g : gValues) {
            for (int b : bValues) {
                palette[index][0] = r;
                palette[index][1] = g;
                palette[index][2] = b;
                index++;
            }
        }
    }
    
    return palette;
}
```

**Embedded Palette Generation:**
```java
static int[][] generateMode6EmbeddedPalette() {
    int[] rValues = {0, 73, 182, 255};  // Subset
    int[] gValues = {0, 85, 170, 255};
    int[] bValues = {0, 85, 170, 255};
    
    int[][] palette = new int[64][3];
    int index = 0;
    
    for (int r : rValues) {
        for (int g : gValues) {
            for (int b : bValues) {
                palette[index][0] = r;
                palette[index][1] = g;
                palette[index][2] = b;
                index++;
            }
        }
    }
    
    return palette;
}
```

**R-Channel Interpolation (Decoding):**
```java
static int interpolateR(int rEmbedded) {
    // Map embedded R values to full palette
    // 0 → 0, 73 → 73, 182 → 182, 255 → 255 (no change)
    // Intermediate values:
    // 36  → interpolate between 0 and 73
    // 109 → interpolate between 73 and 182
    // 146 → interpolate between 73 and 182
    // 219 → interpolate between 182 and 255
    
    int[] embedded = {0, 73, 182, 255};
    int[] full = {0, 36, 73, 109, 146, 182, 219, 255};
    
    // Find nearest embedded values and interpolate
    // (Implementation depends on decoder logic)
}
```

### Use Cases

- **Extreme density** requirements
- **Research applications**
- **Specialized high-precision** printing

### Advantages

- 3.5× capacity vs mode 2
- Systematic interpolation

### Disadvantages

- Requires interpolation logic
- Very challenging to print/scan
- Ambiguous color matching possible

---

## Mode 7: 256 Colors (Reserved)

### Specification

- **Nc Value:** `111` (binary) / 7 (decimal)
- **Number of Colors:** 256
- **Bits per Module:** log₂(256) = 8 bits (1 byte)
- **Status:** 🔧 Reserved (User-defined, Annex G)

### Color Palette

**Color Generation Rule (Annex G.3e):**
- R channel: 8 values (0, 36, 73, 109, 146, 182, 219, 255)
- G channel: 8 values (0, 36, 73, 109, 146, 182, 219, 255)
- B channel: 4 values (0, 85, 170, 255)
- Total: 8 × 8 × 4 = 256 colors

**Critical:** Only 64 colors can be embedded. Requires R and G interpolation.

### Embedded Palette (Annex G.3c)

**Subset Rule:** Include only R, G values {0, 73, 182, 255}
- Embedded: 4 × 4 × 4 = 64 colors
- Missing R, G values: {36, 109, 146, 219}

### Implementation Details

**Full Palette Generation:**
```java
static int[][] generateMode7Palette() {
    int[] rValues = {0, 36, 73, 109, 146, 182, 219, 255};
    int[] gValues = {0, 36, 73, 109, 146, 182, 219, 255};
    int[] bValues = {0, 85, 170, 255};
    
    int[][] palette = new int[256][3];
    int index = 0;
    
    for (int r : rValues) {
        for (int g : gValues) {
            for (int b : bValues) {
                palette[index][0] = r;
                palette[index][1] = g;
                palette[index][2] = b;
                index++;
            }
        }
    }
    
    return palette;
}
```

**Embedded Palette Generation:**
```java
static int[][] generateMode7EmbeddedPalette() {
    int[] rgValues = {0, 73, 182, 255};  // Subset for both R and G
    int[] bValues = {0, 85, 170, 255};
    
    int[][] palette = new int[64][3];
    int index = 0;
    
    for (int r : rgValues) {
        for (int g : rgValues) {
            for (int b : bValues) {
                palette[index][0] = r;
                palette[index][1] = g;
                palette[index][2] = b;
                index++;
            }
        }
    }
    
    return palette;
}
```

**R and G Channel Interpolation:**
```java
static int[] interpolateRG(int rEmbedded, int gEmbedded) {
    // Interpolate both R and G channels independently
    int rFull = interpolateChannel(rEmbedded);
    int gFull = interpolateChannel(gEmbedded);
    return new int[] {rFull, gFull};
}

static int interpolateChannel(int embedded) {
    // Map: {0, 73, 182, 255} → {0, 36, 73, 109, 146, 182, 219, 255}
    // Using linear interpolation between embedded values
}
```

### Use Cases

- **Maximum theoretical density**
- **Extreme data capacity** needs
- **Experimental applications**

### Advantages

- 4× capacity vs mode 2 (theoretical maximum)
- 1 byte per module (clean mapping)

### Disadvantages

- Extremely challenging to implement
- Requires dual-channel interpolation
- High error rates likely
- Not practical for most applications

---

## Mode Comparison Summary

| Mode | Nc | Colors | Bits/Module | Capacity vs Mode 2 | Palette | Interpolation |
|------|-----|--------|-------------|-------------------|---------|---------------|
| 0 | 000 | Reserved | N/A | N/A | N/A | N/A |
| 1 | 001 | 4 | 2 | 66.7% | All embedded | None |
| 2 | 010 | 8 (Default) | 3 | 100% | All embedded | None |
| 3 | 011 | 16 | 4 | 133.3% | All embedded | None |
| 4 | 100 | 32 | 5 | 166.7% | All embedded | None |
| 5 | 101 | 64 | 6 | 200% | All embedded | None |
| 6 | 110 | 128 | 7 | 233.3% | Subset (64) | R channel |
| 7 | 111 | 256 | 8 | 266.7% | Subset (64) | R+G channels |

---

## Implementation Priority

### Phase 1: Verify Standard Modes
- ✅ Mode 1 (4 colors)
- ✅ Mode 2 (8 colors)

### Phase 2: Add Extended Modes (No Interpolation)
- 🔧 Mode 3 (16 colors)
- 🔧 Mode 4 (32 colors)
- 🔧 Mode 5 (64 colors)

### Phase 3: Add High-Color Modes (With Interpolation)
- 🔧 Mode 6 (128 colors)
- 🔧 Mode 7 (256 colors)

---

## References

- **ISO/IEC 23634:2022** Section 4.4.1.2 (Module colour mode)
- **Table 6** - Part I module colour modes (p. 15)
- **Table 7** - Module colour encoding for Nc (p. 15)
- **Table 21** - Bit encoding using module colours (p. 32)
- **Annex G** - Guidelines for module colour selection (p. 67-70)
- **Table G.1** - 16-colour mode RGB values (p. 67-68)
- **Table G.2** - User-defined colour modes (p. 69)
