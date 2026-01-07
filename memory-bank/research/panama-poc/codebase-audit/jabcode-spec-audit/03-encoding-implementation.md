# JABCode Encoding Implementation for Multi-Color Support

## Overview

This document provides expert-level implementation details for encoding JABCode symbols with support for all 8 color modes, based on ISO/IEC 23634 Section 5 (Symbol generation).

**Source:** ISO/IEC 23634:2022, Section 5 (p. 24-35)

---

## Encoding Procedure Overview (Section 5.1)

### Standard Encoding Flow

```
Input Data
    ↓
1. Data Analysis (5.2)
    ↓
2. Mode Selection & Encoding (5.3)
    ↓
3. Error Correction (5.4)
    ↓
4. Data Interleaving (5.5)
    ↓
5. Metadata Module Reservation (5.6)
    ↓
6. Data Module Encoding & Placement (5.7)
    ↓
7. Data Masking (5.8)
    ↓
8. Metadata Generation & Placement (5.9)
    ↓
Output: JABCode Symbol
```

**Color Mode Impact:** Affects steps 6 (bit encoding) and 8 (Nc metadata encoding)

---

## Step 1: Data Analysis (Section 5.2)

### Purpose
Analyze input data to select optimal encoding modes (uppercase, lowercase, numeric, etc.)

### Color Mode Consideration
**None at this stage** - Data analysis is independent of color mode.

---

## Step 2: Encoding Modes (Section 5.3)

### Available Modes

| Mode | Characters | Bits/Char | Usage |
|------|------------|-----------|-------|
| Uppercase | A-Z, space | 5 | All caps text |
| Lowercase | a-z, space | 5 | Lowercase text |
| Numeric | 0-9, comma, period | 4 | Numbers |
| Punctuation | Symbols | 4 | Special chars |
| Mixed | Extended chars | 5 | Mixed content |
| Alphanumeric | A-Z, 0-9 | 6 | Mixed alphanumeric |
| Byte | Any 8-bit | 8 | Binary data |

### Color Mode Consideration
**None** - Character encoding is independent of color mode. Same encoded bit stream for all color modes.

**Critical:** The bit stream is the same; what changes is how those bits are packed into colored modules.

---

## Step 3: Error Correction (Section 5.4)

### LDPC Error Correction

**From Section 5.4.1:**
> "The error correction coding is performed by the LDPC code and operates on binary data."

### Error Correction Levels

| Level | wc | wr | Code Rate R | Recovery % |
|-------|-----|-----|-------------|------------|
| 1 | 4 | 8 | 0.63 | 3% |
| 2 | 3 | 7 | 0.57 | 5% |
| 3 (default) | 4 | 9 | 0.55 | 6% |
| 4 | 3 | 6 | 0.50 | 7% |
| 5 | 4 | 7 | 0.43 | 10% |
| 6 | 4 | 6 | 0.34 | 13% |
| 7 | 3 | 4 | 0.25 | 17% |
| 8 | 4 | 5 | 0.20 | 21% |
| 9 | 5 | 6 | 0.17 | 25% |
| 10 | 6 | 7 | 0.14 | 29% |

### Color Mode Consideration
**None** - Error correction operates on binary bit stream before color mapping.

---

## Step 4: Data Interleaving (Section 5.5)

### Purpose
> "Pseudo-randomly arranges the data in a matrix symbology" (Section 3.1.4)

### Algorithm
See Annex F for detailed interleaving algorithm.

### Color Mode Consideration
**None** - Interleaving operates on bit positions, not colors.

---

## Step 5: Metadata Module Reservation (Section 5.6)

### Reserved Modules Count

**From Table 12:**

| Color Mode | Nc | Colors | Reserved Modules (Primary) |
|------------|-----|--------|---------------------------|
| 1 | 001 | 4 | 41 |
| 2 | 010 | 8 | 41 |
| 3 | 011 | 16 | 41 |
| 4 | 100 | 32 | 41 |
| 5 | 101 | 64 | 41 |
| 6 | 110 | 128 | 41 |
| 7 | 111 | 256 | 41 |

**Breakdown:**
- Part I metadata: up to 4 modules (3-color encoding)
- Part II metadata: up to 19 modules (full color encoding)
- Color palettes: up to 24 modules (for 8 colors)

**Critical:** Number of reserved modules is constant (41) but color palette size varies.

### Implementation

```java
public class MetadataReservation {
    /**
     * Calculate reserved modules for primary symbol
     */
    public static int getReservedModuleCount(ColorMode mode) {
        // Always 41 for primary symbols
        // Breakdown:
        // - Part I (Nc): 4 modules (3-color mode)
        // - Part II: 19 modules (worst case, 4-color mode)
        // - Part III: encoded in data stream
        // - Palettes: 24 modules (2 palettes × 12 modules for 8 colors)
        // - Error correction: included
        return 41;
    }
    
    /**
     * Calculate reserved modules for secondary symbol
     */
    public static int getReservedModulesSecondary(ColorMode mode) {
        // Only palettes, no metadata
        return getPaletteModuleCount(mode);
    }
    
    /**
     * Calculate palette module count
     */
    public static int getPaletteModuleCount(ColorMode mode) {
        int colorsToEmbed = Math.min(mode.getColorCount(), 64);
        
        // 4 palettes, each missing 2 colors (for finder patterns)
        // Each palette: (total_colors - 2) modules
        // But specification reserves 24 modules total for 8-color mode
        
        // For modes with ≤8 colors: 24 modules
        // For modes with >8 colors: scales up
        if (mode.getColorCount() <= 8) {
            return 24;
        }
        
        // For higher color modes, more palette space needed
        // Up to 128 modules total (64 colors × 2 palettes)
        return Math.min(colorsToEmbed * 2, 128);
    }
}
```

---

## Step 6: Data Module Encoding & Placement (Section 5.7)

### **CRITICAL SECTION FOR COLOR MODES**

**From Section 5.7:**
> "A module represents log₂(Nc) binary bits."

### Bit-to-Module Mapping

**Table 21 - Bit encoding using module colours:**

#### Mode 1 (4 colors, 2 bits per module)

| Module Color | Index | Binary Bits |
|--------------|-------|-------------|
| Black | 0 | 00 |
| Cyan | 1 | 01 |
| Magenta | 2 | 10 |
| Yellow | 3 | 11 |

#### Mode 2 (8 colors, 3 bits per module)

| Module Color | Index | Binary Bits |
|--------------|-------|-------------|
| Black | 0 | 000 |
| Blue | 1 | 001 |
| Green | 2 | 010 |
| Cyan | 3 | 011 |
| Red | 4 | 100 |
| Magenta | 5 | 101 |
| Yellow | 6 | 110 |
| White | 7 | 111 |

#### Modes 3-7 (16-256 colors, 4-8 bits per module)

**General Formula:**
```
bits_per_module = log₂(color_count)
color_index = binary_value_of_bits
```

### Implementation

```java
public class ModuleEncoder {
    /**
     * Encode bit stream into module color indices
     */
    public static int[] encodeBitsToModules(byte[] bitStream, ColorMode mode) {
        int bitsPerModule = mode.getBitsPerModule();
        int moduleCount = (bitStream.length * 8 + bitsPerModule - 1) / bitsPerModule;
        int[] modules = new int[moduleCount];
        
        BitStreamReader reader = new BitStreamReader(bitStream);
        
        for (int i = 0; i < moduleCount; i++) {
            if (reader.hasMore()) {
                modules[i] = reader.readBits(bitsPerModule);
            } else {
                modules[i] = 0; // Padding
            }
        }
        
        return modules;
    }
    
    /**
     * Convert module color indices to RGB values
     */
    public static int[][] modulesToRGB(int[] modules, ColorPalette palette) {
        int[][] rgb = new int[modules.length][3];
        
        for (int i = 0; i < modules.length; i++) {
            rgb[i] = palette.getRGB(modules[i]);
        }
        
        return rgb;
    }
}
```

### Bit Stream Reader

```java
public class BitStreamReader {
    private final byte[] data;
    private int bitPosition = 0;
    
    public BitStreamReader(byte[] data) {
        this.data = data;
    }
    
    /**
     * Read n bits from stream
     */
    public int readBits(int n) {
        if (n <= 0 || n > 32) {
            throw new IllegalArgumentException("Bits must be 1-32");
        }
        
        int result = 0;
        
        for (int i = 0; i < n; i++) {
            if (!hasMore()) {
                // Pad with zeros
                result <<= 1;
            } else {
                int byteIndex = bitPosition / 8;
                int bitIndex = 7 - (bitPosition % 8);
                int bit = (data[byteIndex] >> bitIndex) & 1;
                
                result = (result << 1) | bit;
                bitPosition++;
            }
        }
        
        return result;
    }
    
    public boolean hasMore() {
        return bitPosition < data.length * 8;
    }
}
```

### Module Placement Pattern

**From Section 5.7:**
> "The data modules are placed column by column from bottom to top and from right to left, beginning with the bottom-right data module."

**Placement Order:**
```
Symbol grid (example 5×5 data region):

20 15 10  5  0
21 16 11  6  1
22 17 12  7  2
23 18 13  8  3
24 19 14  9  4

Start: bottom-right (module 0)
Direction: up ↑
When top reached: move left one column
Continue: bottom to top
```

**Implementation:**

```java
public class ModulePlacement {
    /**
     * Place modules in symbol grid according to Section 5.7
     */
    public static void placeModules(int[] modules, int[][] grid,
                                     boolean[][] reserved) {
        int width = grid[0].length;
        int height = grid.length;
        
        int moduleIndex = 0;
        
        // Iterate columns right to left
        for (int col = width - 1; col >= 0; col--) {
            // Iterate rows bottom to top
            for (int row = height - 1; row >= 0; row--) {
                // Skip reserved modules (finder, alignment, metadata, palette)
                if (!reserved[row][col]) {
                    if (moduleIndex < modules.length) {
                        grid[row][col] = modules[moduleIndex];
                        moduleIndex++;
                    }
                }
            }
        }
    }
}
```

---

## Step 7: Data Masking (Section 5.8)

### Masking Rules (Section 5.8.1)

**Purpose:**
1. Balance color distribution
2. Avoid patterns similar to finder/alignment patterns

### Mask Patterns (Section 5.8.2)

**Table 22 - Data mask patterns:**

| Reference | Pattern Generator |
|-----------|------------------|
| 000 | (x+y) mod 2^(Nc+1) |
| 001 | x mod 2^(Nc+1) |
| 010 | y mod 2^(Nc+1) |
| 011 | ((x div 2) + (y div 3)) mod 2^(Nc+1) |
| 100 | ((x div 3) + (y div 2)) mod 2^(Nc+1) |
| 101 | ((x+y) div 2 + (x+y) div 3) mod 2^(Nc+1) |
| 110 | (((x × x × y) mod 7) + ((2 × x × x + 2 × y) mod 19)) mod 2^(Nc+1) |
| 111 (default) | (((x × y × y) mod 5) + ((2 × x + y × y) mod 13)) mod 2^(Nc+1) |

**Critical:** Mask value depends on Nc (color mode)!

### Color Mode Impact

**Mask modulus changes with color mode:**

| Color Mode | Nc | Colors | Mask Modulus (2^(Nc+1)) |
|------------|-----|--------|------------------------|
| 1 | 1 | 4 | 2² = 4 |
| 2 | 2 | 8 | 2³ = 8 |
| 3 | 3 | 16 | 2⁴ = 16 |
| 4 | 4 | 32 | 2⁵ = 32 |
| 5 | 5 | 64 | 2⁶ = 64 |
| 6 | 6 | 128 | 2⁷ = 128 |
| 7 | 7 | 256 | 2⁸ = 256 |

**Note:** Modulus equals color count!

### Masking Operation

**From Section 5.8.2:**
> "The data masking is applied to a data module through the bitwise XOR operation between the colour index of the data module and the colour index of the corresponding module in the mask pattern."

**Formula:**
```
masked_color = data_color XOR mask_color
```

### Implementation

```java
public class DataMasking {
    /**
     * Generate mask pattern for entire symbol
     */
    public static int[][] generateMaskPattern(int width, int height,
                                               int maskReference, ColorMode mode) {
        int[][] mask = new int[height][width];
        int modulus = mode.getColorCount();
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                mask[y][x] = calculateMaskValue(x, y, maskReference, modulus);
            }
        }
        
        return mask;
    }
    
    /**
     * Calculate mask value for position (x,y)
     */
    private static int calculateMaskValue(int x, int y, int ref, int modulus) {
        switch (ref) {
            case 0: // (x+y) mod modulus
                return (x + y) % modulus;
            
            case 1: // x mod modulus
                return x % modulus;
            
            case 2: // y mod modulus
                return y % modulus;
            
            case 3: // ((x/2) + (y/3)) mod modulus
                return ((x / 2) + (y / 3)) % modulus;
            
            case 4: // ((x/3) + (y/2)) mod modulus
                return ((x / 3) + (y / 2)) % modulus;
            
            case 5: // ((x+y)/2 + (x+y)/3) mod modulus
                return (((x + y) / 2) + ((x + y) / 3)) % modulus;
            
            case 6: // Complex pattern 1
                int term1 = ((x * x * y) % 7);
                int term2 = ((2 * x * x + 2 * y) % 19);
                return (term1 + term2) % modulus;
            
            case 7: // Complex pattern 2 (default)
            default:
                int term3 = ((x * y * y) % 5);
                int term4 = ((2 * x + y * y) % 13);
                return (term3 + term4) % modulus;
        }
    }
    
    /**
     * Apply mask to data modules
     */
    public static void applyMask(int[][] modules, int[][] mask,
                                  boolean[][] reserved) {
        int height = modules.length;
        int width = modules[0].length;
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // Only mask data modules, not reserved modules
                if (!reserved[y][x]) {
                    modules[y][x] ^= mask[y][x];
                }
            }
        }
    }
    
    /**
     * Evaluate masking quality (Section 5.8.3)
     */
    public static int evaluateMask(int[][] maskedModules, ColorMode mode) {
        int penalty = 0;
        
        // Feature 1: Same color/structure as finder pattern
        penalty += evaluateFinderPatternPenalty(maskedModules);
        
        // Feature 2: Blocks of same color
        penalty += evaluateBlockPenalty(maskedModules);
        
        // Feature 3: Adjacent modules in same color
        penalty += evaluateAdjacentPenalty(maskedModules);
        
        return penalty;
    }
}
```

---

## Step 8: Metadata Generation & Placement (Section 5.9)

### **CRITICAL FOR COLOR MODES**

### Part I: Nc Encoding (3-Color Mode)

**From Section 4.4.1.2:**
> "Nc shall be encoded in a three-colour mode which uses only the first, the fourth and the seventh module colour in the colour palette... The three used colours shall be black, cyan and yellow in all colour modes."

**Table 7 - Nc Encoding:**

| Nc Binary | First Module | Second Module |
|-----------|--------------|---------------|
| 000 | Black | Black |
| 001 | Black | Cyan |
| 010 | Black | Yellow |
| 011 | Cyan | Black |
| 100 | Cyan | Cyan |
| 101 | Cyan | Yellow |
| 110 | Yellow | Black |
| 111 | Yellow | Cyan |

**Implementation:**

```java
public class NcMetadataEncoder {
    // 3-color palette for Nc encoding (always these colors)
    private static final int[] BLACK = {0, 0, 0};
    private static final int[] CYAN = {0, 255, 255};
    private static final int[] YELLOW = {255, 255, 0};
    
    /**
     * Encode Nc value (0-7) into two modules using 3-color mode
     */
    public static int[][] encodeNc(int ncValue) {
        if (ncValue < 0 || ncValue > 7) {
            throw new IllegalArgumentException("Nc must be 0-7");
        }
        
        // After error correction, encode according to Table 7
        int firstColor = getFirstModuleColor(ncValue);
        int secondColor = getSecondModuleColor(ncValue);
        
        return new int[][] {
            getColorRGB(firstColor),
            getColorRGB(secondColor)
        };
    }
    
    private static int getFirstModuleColor(int nc) {
        // Extract first bit of Nc
        // 0-2 → Black, 3-5 → Cyan, 6-7 → Yellow
        if (nc <= 2) return 0; // Black
        if (nc <= 5) return 1; // Cyan
        return 2; // Yellow
    }
    
    private static int getSecondModuleColor(int nc) {
        // Pattern from Table 7
        switch (nc) {
            case 0: return 0; // Black
            case 1: return 1; // Cyan
            case 2: return 2; // Yellow
            case 3: return 0; // Black
            case 4: return 1; // Cyan
            case 5: return 2; // Yellow
            case 6: return 0; // Black
            case 7: return 1; // Cyan
            default: throw new IllegalArgumentException();
        }
    }
    
    private static int[] getColorRGB(int colorIndex) {
        switch (colorIndex) {
            case 0: return BLACK;
            case 1: return CYAN;
            case 2: return YELLOW;
            default: throw new IllegalArgumentException();
        }
    }
}
```

### Part II: Full-Color Encoding

**From Section 5.9:**
> "The other metadata shall be encoded using all available colours."

Part II includes:
- Side-version (V): 10 bits
- Error correction parameters (E): 6 bits  
- Masking reference (MSK): 3 bits

**Implementation:**

```java
public class Part2MetadataEncoder {
    /**
     * Encode Part II metadata using full color palette
     */
    public static int[] encodePart2(int sideVersionX, int sideVersionY,
                                     int eccWc, int eccWr, int maskRef,
                                     ColorMode mode) {
        // Encode V (side-version)
        int vBits = (sideVersionX << 5) | sideVersionY; // 10 bits
        
        // Encode E (ECC parameters)
        int eBits = ((eccWc - 3) << 3) | (eccWr - 4); // 6 bits
        
        // Encode MSK (masking reference)
        int mskBits = maskRef; // 3 bits
        
        // Total: 19 bits
        int part2Bits = (vBits << 9) | (eBits << 3) | mskBits;
        
        // Pack into modules using full color palette
        int bitsPerModule = mode.getBitsPerModule();
        int moduleCount = (19 + bitsPerModule - 1) / bitsPerModule;
        
        int[] modules = new int[moduleCount];
        
        for (int i = 0; i < moduleCount; i++) {
            int shift = 19 - (i + 1) * bitsPerModule;
            if (shift < 0) shift = 0;
            
            int mask = (1 << bitsPerModule) - 1;
            modules[i] = (part2Bits >> shift) & mask;
        }
        
        return modules;
    }
}
```

---

## Complete Encoding Example

```java
public class JABCodeEncoderComplete {
    
    public BufferedImage encode(String data, ColorMode mode, 
                                 int eccLevel, int symbolNumber) {
        // 1. Data Analysis (not shown - same for all modes)
        
        // 2. Encoding modes (not shown - same for all modes)
        byte[] encodedBits = encodeToBytes(data);
        
        // 3. Error Correction (not shown - same for all modes)
        byte[] eccBits = applyLDPC(encodedBits, eccLevel);
        
        // 4. Data Interleaving (not shown - same for all modes)
        byte[] interleavedBits = interleave(eccBits);
        
        // 5. Metadata Module Reservation
        int reservedModules = MetadataReservation.getReservedModuleCount(mode);
        
        // 6. DATA MODULE ENCODING (COLOR MODE SPECIFIC)
        int[] dataModules = ModuleEncoder.encodeBitsToModules(
            interleavedBits, mode);
        
        // 7. DATA MASKING (COLOR MODE SPECIFIC)
        int maskRef = selectBestMask(dataModules, mode);
        int[][] maskPattern = DataMasking.generateMaskPattern(
            width, height, maskRef, mode);
        DataMasking.applyMask(dataModules, maskPattern, reserved);
        
        // 8. METADATA GENERATION (COLOR MODE SPECIFIC)
        // Part I: Nc (3-color encoding)
        int[][] ncModules = NcMetadataEncoder.encodeNc(mode.getNcValue());
        
        // Part II: Full color encoding
        int[] part2Modules = Part2MetadataEncoder.encodePart2(
            sideVersionX, sideVersionY, wc, wr, maskRef, mode);
        
        // 9. Module Placement
        int[][] symbolGrid = new int[height][width];
        ModulePlacement.placeModules(dataModules, symbolGrid, reserved);
        placeMetadata(ncModules, part2Modules, symbolGrid);
        
        // 10. Generate Color Palette
        ColorPalette palette = ColorPaletteFactory.createPalette(mode);
        
        // 11. Embed Palette in Symbol
        embedColorPalette(palette, symbolGrid);
        
        // 12. Convert to RGB Image
        int[][] rgbModules = ModuleEncoder.modulesToRGB(
            flatten(symbolGrid), palette);
        
        return createBufferedImage(rgbModules, width, height);
    }
}
```

---

## Summary: Color Mode Impact on Encoding

| Encoding Step | Color Mode Impact |
|---------------|------------------|
| 1. Data Analysis | ❌ None |
| 2. Mode Selection | ❌ None |
| 3. Error Correction | ❌ None |
| 4. Interleaving | ❌ None |
| 5. Module Reservation | ⚠️ Palette size varies |
| 6. **Data Encoding** | ✅ **Variable bits per module** |
| 7. **Masking** | ✅ **Modulus = color count** |
| 8. **Metadata (Nc)** | ✅ **Always 3-color mode** |
| 8. **Metadata (Part II)** | ✅ **Uses full color palette** |
| 9. Palette Embedding | ✅ **Subset for modes 6-7** |

---

## References

- **Section 5** - Symbol generation (p. 24-35)
- **Section 5.7** - Data module encoding and placement (p. 32)
- **Section 5.8** - Data masking (p. 33-34)
- **Section 5.9** - Metadata generation (p. 34-35)
- **Table 21** - Bit encoding using module colours (p. 32)
- **Table 22** - Data mask patterns (p. 33)
- **Table 7** - Nc encoding (p. 15)
- **Annex G** - Color palette construction
