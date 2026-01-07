# JABCode Decoding Implementation for Multi-Color Support

## Overview

This document provides expert-level implementation details for decoding JABCode symbols with support for all 8 color modes, based on ISO/IEC 23634 Section 6 (Reference decode algorithm).

**Source:** ISO/IEC 23634:2022, Section 6 (p. 35-48)

---

## Decoding Procedure Overview (Section 6.1)

### Standard Decoding Flow

```
Input: JABCode Image
    ↓
1. Pre-processing & Color Classification (6.2)
    ↓
2. Locate Finder Patterns (6.3)
    ↓
3. Locate Alignment Patterns (6.4)
    ↓
4. Establish Sampling Grid (6.5)
    ↓
5. **Decode Part I Metadata (Nc)** (6.6) ← COLOR MODE DETECTED HERE
    ↓
6. **Extract & Construct Color Palettes** (6.6) ← COLOR MODE SPECIFIC
    ↓
7. Decode Part II Metadata (6.6)
    ↓
8. **Decode Data Modules** (6.7) ← COLOR MODE SPECIFIC
    ↓
9. **Release Data Masking** (6.7) ← COLOR MODE SPECIFIC
    ↓
10. De-interleave Data Stream (6.7)
    ↓
11. Error Detection & Correction (6.7)
    ↓
12. Decode Data Stream to Message (6.7)
    ↓
13. Decode Secondary Symbols (6.8)
    ↓
Output: Decoded Data
```

**Color Mode Impact:** Steps 5, 6, 8, and 9 are color mode dependent.

---

## Step 1: Pre-processing & Color Classification (Section 6.2)

### Purpose
Prepare image and classify colors before detailed analysis.

### Color Classification Challenge

**For modes 1-2 (standardized):**
- Known color palettes (Tables 3 & 4)
- Can classify colors immediately

**For modes 3-7 (user-defined):**
- Unknown color palette until metadata decoded
- Must extract palette from symbol first
- **Bootstrap problem:** Need to decode metadata to know colors, but need to know colors to decode metadata

### Solution Strategy

**From Section 6.1(e):**
> "Decode the Part I metadata of the primary symbol and determine the module colour mode used."

**Two-phase approach:**

1. **Phase 1:** Decode Part I (Nc) using 3-color mode (Black, Cyan, Yellow)
2. **Phase 2:** Extract full palette, then decode rest of symbol

### Implementation

```java
public class ColorClassification {
    /**
     * Initial color classification for Nc decoding
     * Uses only Black, Cyan, Yellow (3-color mode)
     */
    public static int classifyNcColor(int r, int g, int b) {
        int[] black = {0, 0, 0};
        int[] cyan = {0, 255, 255};
        int[] yellow = {255, 255, 0};
        
        double dBlack = colorDistance(r, g, b, black);
        double dCyan = colorDistance(r, g, b, cyan);
        double dYellow = colorDistance(r, g, b, yellow);
        
        if (dBlack <= dCyan && dBlack <= dYellow) return 0;
        if (dCyan <= dYellow) return 1;
        return 2;
    }
    
    /**
     * Full color classification after palette extracted
     */
    public static int classifyModuleColor(int r, int g, int b,
                                          int[][] palette) {
        int bestIndex = 0;
        double minDistance = Double.MAX_VALUE;
        
        for (int i = 0; i < palette.length; i++) {
            double dist = colorDistance(r, g, b, palette[i]);
            if (dist < minDistance) {
                minDistance = dist;
                bestIndex = i;
            }
        }
        
        return bestIndex;
    }
    
    private static double colorDistance(int r1, int g1, int b1, int[] c2) {
        int dr = r1 - c2[0];
        int dg = g1 - c2[1];
        int db = b1 - c2[2];
        return Math.sqrt(dr*dr + dg*dg + db*db);
    }
}
```

---

## Step 2-4: Locating Patterns & Sampling

### Finder Pattern Location (Section 6.3)
### Alignment Pattern Location (Section 6.4)
### Sampling Grid Establishment (Section 6.5)

**Color Mode Consideration:** ❌ None - These steps are color-independent

The symbol structure (finder patterns, alignment patterns, grid) is the same for all color modes.

---

## Step 5: Decode Part I Metadata (Nc) - **CRITICAL**

### Purpose
Determine the color mode used in the symbol.

### Nc Decoding (3-Color Mode)

**From Section 6.6:**
> "Decode the Part I metadata of the primary symbol and determine the module colour mode used."

**Process:**
1. Sample Nc modules (first 4 modules in metadata region after error correction)
2. Classify each module as Black (0), Cyan (1), or Yellow (2)
3. Decode Nc value using Table 7 (reverse mapping)

**Table 7 (Decoding):**

| First Module | Second Module | Nc Binary | Nc Decimal | Color Mode |
|--------------|---------------|-----------|------------|------------|
| Black (0) | Black (0) | 000 | 0 | Reserved |
| Black (0) | Cyan (1) | 001 | 1 | 4 colors |
| Black (0) | Yellow (2) | 010 | 2 | 8 colors |
| Cyan (1) | Black (0) | 011 | 3 | 16 colors |
| Cyan (1) | Cyan (1) | 100 | 4 | 32 colors |
| Cyan (1) | Yellow (2) | 101 | 5 | 64 colors |
| Yellow (2) | Black (0) | 110 | 6 | 128 colors |
| Yellow (2) | Cyan (1) | 111 | 7 | 256 colors |

### Implementation

```java
public class NcMetadataDecoder {
    /**
     * Decode Nc from two modules encoded in 3-color mode
     */
    public static ColorMode decodeNc(int[][] modules) {
        // modules[0] = first module RGB
        // modules[1] = second module RGB
        
        int firstColor = ColorClassification.classifyNcColor(
            modules[0][0], modules[0][1], modules[0][2]);
        int secondColor = ColorClassification.classifyNcColor(
            modules[1][0], modules[1][1], modules[1][2]);
        
        int ncValue = decodeNcValue(firstColor, secondColor);
        return ColorMode.fromNcValue(ncValue);
    }
    
    /**
     * Reverse mapping of Table 7
     */
    private static int decodeNcValue(int first, int second) {
        // Pattern from Table 7
        if (first == 0) { // Black
            if (second == 0) return 0; // 000
            if (second == 1) return 1; // 001
            if (second == 2) return 2; // 010
        }
        else if (first == 1) { // Cyan
            if (second == 0) return 3; // 011
            if (second == 1) return 4; // 100
            if (second == 2) return 5; // 101
        }
        else if (first == 2) { // Yellow
            if (second == 0) return 6; // 110
            if (second == 1) return 7; // 111
        }
        
        throw new IllegalArgumentException(
            "Invalid Nc encoding: " + first + ", " + second);
    }
}
```

---

## Step 6: Extract & Construct Color Palettes - **CRITICAL**

### Palette Extraction

**From Section 6.6:**
> "Extract and construct the four colour palettes."

### Extraction Process

**Four palette locations:**
- Near finder pattern UL (Upper-Left)
- Near finder pattern UR (Upper-Right)
- Near finder pattern DL (Down-Left)
- Near finder pattern DR (Down-Right)

**Each palette contains:** All colors EXCEPT the 2 colors used by nearest finder pattern

### For Modes 1-5 (≤64 colors)

**Simple extraction:**
1. Sample palette modules
2. Read RGB values directly
3. All colors are embedded

```java
public class PaletteExtractor {
    /**
     * Extract embedded color palette from symbol
     */
    public static int[][] extractEmbeddedPalette(
        BufferedImage image, int[][] palettePositions) {
        
        List<int[]> colors = new ArrayList<>();
        
        for (int[] pos : palettePositions) {
            int x = pos[0];
            int y = pos[1];
            
            int rgb = image.getRGB(x, y);
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            
            colors.add(new int[]{r, g, b});
        }
        
        // Remove duplicates and sort
        return deduplicateAndSort(colors);
    }
    
    /**
     * Merge palettes from all 4 locations
     */
    public static int[][] mergePalettes(int[][][] palettes) {
        Set<String> colorSet = new HashSet<>();
        List<int[]> merged = new ArrayList<>();
        
        for (int[][] palette : palettes) {
            for (int[] color : palette) {
                String key = color[0] + "," + color[1] + "," + color[2];
                if (!colorSet.contains(key)) {
                    colorSet.add(key);
                    merged.add(color);
                }
            }
        }
        
        return merged.toArray(new int[0][]);
    }
}
```

### For Modes 6-7 (>64 colors) - **WITH INTERPOLATION**

**Complex extraction:**
1. Sample palette modules (64 colors)
2. Read RGB values (embedded subset)
3. **Reconstruct full palette via interpolation**

**From Annex G.3(d) for Mode 6:**
> "The original full-size colour palette should be reconstructed by interpolating each colour channel of the colours in the extracted colour palettes. For example, the colours whose original R channel value is 36 should be restored by interpolating the colours whose original R channel value is 0 and 73."

### Mode 6 Palette Reconstruction

```java
public class Mode6PaletteReconstructor {
    // Embedded R values: {0, 73, 182, 255}
    // Full R values: {0, 36, 73, 109, 146, 182, 219, 255}
    
    /**
     * Reconstruct full 128-color palette from 64-color embedded palette
     */
    public static int[][] reconstructPalette(int[][] embeddedPalette) {
        // Extract unique R, G, B values from embedded palette
        Set<Integer> embeddedR = extractChannel(embeddedPalette, 0);
        Set<Integer> gValues = extractChannel(embeddedPalette, 1);
        Set<Integer> bValues = extractChannel(embeddedPalette, 2);
        
        // Expected: R = {0, 73, 182, 255}, G & B = {0, 85, 170, 255}
        
        // Generate full R values via interpolation
        int[] fullR = {0, 36, 73, 109, 146, 182, 219, 255};
        int[] fullG = gValues.stream().sorted().mapToInt(i -> i).toArray();
        int[] fullB = bValues.stream().sorted().mapToInt(i -> i).toArray();
        
        // Generate full palette
        List<int[]> fullPalette = new ArrayList<>();
        for (int r : fullR) {
            for (int g : fullG) {
                for (int b : fullB) {
                    fullPalette.add(new int[]{r, g, b});
                }
            }
        }
        
        return fullPalette.toArray(new int[0][]);
    }
    
    private static Set<Integer> extractChannel(int[][] palette, int channel) {
        Set<Integer> values = new TreeSet<>();
        for (int[] color : palette) {
            values.add(color[channel]);
        }
        return values;
    }
    
    /**
     * Alternative: Map decoded module to possible full palette indices
     * This is needed during actual decoding
     */
    public static int[] getPossibleColorIndices(int r, int g, int b,
                                                 int[][] embeddedPalette) {
        // Find nearest embedded color
        int embeddedIndex = findNearestColor(r, g, b, embeddedPalette);
        int[] embeddedColor = embeddedPalette[embeddedIndex];
        
        // Determine possible full R values
        int[] possibleR = getPossibleRValues(embeddedColor[0]);
        
        // For each possible R, find matching full color
        List<Integer> possibleIndices = new ArrayList<>();
        int[][] fullPalette = reconstructPalette(embeddedPalette);
        
        for (int fullR : possibleR) {
            for (int i = 0; i < fullPalette.length; i++) {
                if (fullPalette[i][0] == fullR &&
                    fullPalette[i][1] == embeddedColor[1] &&
                    fullPalette[i][2] == embeddedColor[2]) {
                    possibleIndices.add(i);
                }
            }
        }
        
        return possibleIndices.stream().mapToInt(i -> i).toArray();
    }
    
    /**
     * Get possible full R values for given embedded R value
     */
    private static int[] getPossibleRValues(int embeddedR) {
        // Per Annex G.3(d)
        if (embeddedR == 0) return new int[]{0, 36};
        if (embeddedR == 73) return new int[]{36, 73, 109, 146};
        if (embeddedR == 182) return new int[]{146, 182, 219};
        if (embeddedR == 255) return new int[]{219, 255};
        
        // If not exact match, find nearest
        int[] embedded = {0, 73, 182, 255};
        int nearest = findNearest(embeddedR, embedded);
        return getPossibleRValues(nearest);
    }
}
```

### Mode 7 Palette Reconstruction

```java
public class Mode7PaletteReconstructor {
    // Embedded R & G values: {0, 73, 182, 255}
    // Full R & G values: {0, 36, 73, 109, 146, 182, 219, 255}
    
    /**
     * Reconstruct full 256-color palette from 64-color embedded palette
     */
    public static int[][] reconstructPalette(int[][] embeddedPalette) {
        // Extract unique R, G, B values
        Set<Integer> embeddedRG = extractChannels(embeddedPalette, 0, 1);
        Set<Integer> bValues = extractChannel(embeddedPalette, 2);
        
        // Generate full R & G values
        int[] fullRG = {0, 36, 73, 109, 146, 182, 219, 255};
        int[] fullB = bValues.stream().sorted().mapToInt(i -> i).toArray();
        
        // Generate full palette
        List<int[]> fullPalette = new ArrayList<>();
        for (int r : fullRG) {
            for (int g : fullRG) {
                for (int b : fullB) {
                    fullPalette.add(new int[]{r, g, b});
                }
            }
        }
        
        return fullPalette.toArray(new int[0][]);
    }
    
    /**
     * Get possible color indices for decoded module
     * More complex than Mode 6 due to dual-channel interpolation
     */
    public static int[] getPossibleColorIndices(int r, int g, int b,
                                                 int[][] embeddedPalette) {
        int embeddedIndex = findNearestColor(r, g, b, embeddedPalette);
        int[] embeddedColor = embeddedPalette[embeddedIndex];
        
        // Get possible R and G values independently
        int[] possibleR = getPossibleValues(embeddedColor[0]);
        int[] possibleG = getPossibleValues(embeddedColor[1]);
        
        // Combine all possibilities
        List<Integer> possibleIndices = new ArrayList<>();
        int[][] fullPalette = reconstructPalette(embeddedPalette);
        
        for (int fullR : possibleR) {
            for (int fullG : possibleG) {
                for (int i = 0; i < fullPalette.length; i++) {
                    if (fullPalette[i][0] == fullR &&
                        fullPalette[i][1] == fullG &&
                        fullPalette[i][2] == embeddedColor[2]) {
                        possibleIndices.add(i);
                    }
                }
            }
        }
        
        return possibleIndices.stream().mapToInt(i -> i).toArray();
    }
}
```

---

## Step 7: Decode Part II Metadata

### Purpose
Decode symbol parameters using full color palette.

### Part II Contents

- Side-version (V): 10 bits
- Error correction parameters (E): 6 bits
- Masking reference (MSK): 3 bits

**Total:** 19 bits

### Color Mode Impact

**Variable bits per module:**
- Mode 1 (2 bits): Needs 10 modules (19 bits ÷ 2 = 9.5, round up to 10)
- Mode 2 (3 bits): Needs 7 modules (19 bits ÷ 3 = 6.3, round up to 7)
- Mode 3 (4 bits): Needs 5 modules
- Mode 4 (5 bits): Needs 4 modules
- Mode 5+ (6+ bits): Needs 4 modules

### Implementation

```java
public class Part2MetadataDecoder {
    /**
     * Decode Part II metadata using full color palette
     */
    public static Part2Metadata decodePart2(int[] moduleIndices,
                                            ColorMode mode) {
        int bitsPerModule = mode.getBitsPerModule();
        int requiredModules = (19 + bitsPerModule - 1) / bitsPerModule;
        
        // Extract bits from modules
        int part2Bits = 0;
        for (int i = 0; i < requiredModules; i++) {
            part2Bits = (part2Bits << bitsPerModule) | moduleIndices[i];
        }
        
        // Decode fields
        int vBits = (part2Bits >> 9) & 0x3FF; // 10 bits
        int eBits = (part2Bits >> 3) & 0x3F;  // 6 bits
        int mskBits = part2Bits & 0x07;       // 3 bits
        
        // Parse V
        int sideVersionX = (vBits >> 5) & 0x1F;
        int sideVersionY = vBits & 0x1F;
        
        // Parse E
        int wc = ((eBits >> 3) & 0x07) + 3;
        int wr = (eBits & 0x07) + 4;
        
        // MSK is direct
        int maskRef = mskBits;
        
        return new Part2Metadata(sideVersionX, sideVersionY, wc, wr, maskRef);
    }
}
```

---

## Step 8: Decode Data Modules - **COLOR MODE SPECIFIC**

### Module Sampling

**From Section 6.7:**
> "Decode the data modules by determining their colour index in the nearest colour palette."

### For Modes 1-5 (No Interpolation)

**Simple process:**
1. Sample module RGB value
2. Find nearest color in palette
3. Color index = module value

```java
public class DataModuleDecoder {
    /**
     * Decode data modules (modes 1-5, no interpolation)
     */
    public static int[] decodeModules(int[][] moduleRGB, int[][] palette) {
        int[] indices = new int[moduleRGB.length];
        
        for (int i = 0; i < moduleRGB.length; i++) {
            indices[i] = ColorClassification.classifyModuleColor(
                moduleRGB[i][0], moduleRGB[i][1], moduleRGB[i][2], palette);
        }
        
        return indices;
    }
    
    /**
     * Convert module indices to bit stream
     */
    public static byte[] modulesToBits(int[] modules, ColorMode mode) {
        int bitsPerModule = mode.getBitsPerModule();
        int totalBits = modules.length * bitsPerModule;
        byte[] bits = new byte[(totalBits + 7) / 8];
        
        BitStreamWriter writer = new BitStreamWriter(bits);
        
        for (int module : modules) {
            writer.writeBits(module, bitsPerModule);
        }
        
        return bits;
    }
}
```

### For Modes 6-7 (With Interpolation)

**Complex process:**
1. Sample module RGB value
2. Find nearest embedded color
3. Determine possible full color indices
4. **Use context to disambiguate** (error correction, adjacent modules, etc.)

```java
public class InterpolatedDataDecoder {
    /**
     * Decode data modules with interpolation (modes 6-7)
     */
    public static int[] decodeModules(int[][] moduleRGB,
                                      int[][] embeddedPalette,
                                      ColorMode mode) {
        int[] indices = new int[moduleRGB.length];
        int[][] fullPalette;
        
        if (mode == ColorMode.MODE_128) {
            fullPalette = Mode6PaletteReconstructor.reconstructPalette(
                embeddedPalette);
        } else if (mode == ColorMode.MODE_256) {
            fullPalette = Mode7PaletteReconstructor.reconstructPalette(
                embeddedPalette);
        } else {
            throw new IllegalArgumentException("Mode requires interpolation");
        }
        
        for (int i = 0; i < moduleRGB.length; i++) {
            int[] rgb = moduleRGB[i];
            
            // Find nearest color in full palette
            indices[i] = findNearestColorInFullPalette(
                rgb[0], rgb[1], rgb[2], fullPalette, embeddedPalette);
        }
        
        return indices;
    }
    
    /**
     * Find nearest color considering interpolation ambiguity
     */
    private static int findNearestColorInFullPalette(
        int r, int g, int b, int[][] fullPalette, int[][] embeddedPalette) {
        
        // Strategy: Find nearest in full palette directly
        // (Error correction will handle ambiguous cases)
        
        int bestIndex = 0;
        double minDistance = Double.MAX_VALUE;
        
        for (int i = 0; i < fullPalette.length; i++) {
            double dist = colorDistance(r, g, b, fullPalette[i]);
            if (dist < minDistance) {
                minDistance = dist;
                bestIndex = i;
            }
        }
        
        return bestIndex;
    }
}
```

---

## Step 9: Release Data Masking - **COLOR MODE SPECIFIC**

### Unmasking Process

**From Section 6.7:**
> "Release the data masking using the mask pattern corresponding to the decoded mask pattern reference."

### Color Mode Impact

**Mask modulus = color count:**

```java
public class DataUnmasking {
    /**
     * Remove mask from data modules
     */
    public static void removeMask(int[][] modules, int maskRef,
                                   ColorMode mode, boolean[][] reserved) {
        int modulus = mode.getColorCount();
        int height = modules.length;
        int width = modules[0].length;
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!reserved[y][x]) {
                    int maskValue = calculateMaskValue(x, y, maskRef, modulus);
                    modules[y][x] ^= maskValue;
                }
            }
        }
    }
    
    /**
     * Calculate mask value (same as encoding)
     */
    private static int calculateMaskValue(int x, int y, int ref, int modulus) {
        // Same as encoding (see 03-encoding-implementation.md)
        // ... implementation omitted for brevity
    }
}
```

---

## Step 10-12: De-interleave, Error Correction, Data Decoding

### Color Mode Consideration
❌ None - These steps operate on the decoded bit stream, independent of color mode.

---

## Complete Decoding Example

```java
public class JABCodeDecoderComplete {
    
    public String decode(BufferedImage image) {
        // 1. Pre-processing
        preprocessImage(image);
        
        // 2. Locate finder patterns
        FinderPattern[] finders = locateFinders(image);
        
        // 3. Locate alignment patterns
        AlignmentPattern[] alignments = locateAlignments(image, finders);
        
        // 4. Establish sampling grid
        Grid grid = establishGrid(finders, alignments);
        
        // 5. DECODE Nc (3-COLOR MODE)
        int[][] ncModules = sampleNcModules(image, grid);
        ColorMode mode = NcMetadataDecoder.decodeNc(ncModules);
        
        // 6. EXTRACT COLOR PALETTE (MODE-SPECIFIC)
        int[][] embeddedPalette = PaletteExtractor.extractEmbeddedPalette(
            image, grid.getPalettePositions());
        
        int[][] fullPalette;
        if (mode.requiresInterpolation()) {
            if (mode == ColorMode.MODE_128) {
                fullPalette = Mode6PaletteReconstructor.reconstructPalette(
                    embeddedPalette);
            } else { // MODE_256
                fullPalette = Mode7PaletteReconstructor.reconstructPalette(
                    embeddedPalette);
            }
        } else {
            fullPalette = embeddedPalette;
        }
        
        // 7. Decode Part II metadata (uses full palette)
        int[] part2Modules = samplePart2Modules(image, grid, fullPalette);
        Part2Metadata part2 = Part2MetadataDecoder.decodePart2(
            part2Modules, mode);
        
        // 8. DECODE DATA MODULES (MODE-SPECIFIC)
        int[][] dataRGB = sampleDataModules(image, grid);
        int[] dataIndices;
        
        if (mode.requiresInterpolation()) {
            dataIndices = InterpolatedDataDecoder.decodeModules(
                dataRGB, embeddedPalette, mode);
        } else {
            dataIndices = DataModuleDecoder.decodeModules(
                dataRGB, fullPalette);
        }
        
        // 9. REMOVE MASKING (MODE-SPECIFIC)
        int[][] dataGrid = arrangeModules(dataIndices, grid);
        DataUnmasking.removeMask(dataGrid, part2.getMaskRef(), mode, 
            grid.getReservedMap());
        
        // 10. De-interleave
        byte[] interleavedBits = DataModuleDecoder.modulesToBits(
            flatten(dataGrid), mode);
        byte[] deinterleavedBits = deinterleave(interleavedBits);
        
        // 11. Error correction
        byte[] correctedBits = applyLDPCDecoding(
            deinterleavedBits, part2.getWc(), part2.getWr());
        
        // 12. Decode data
        String message = decodeMessage(correctedBits);
        
        return message;
    }
}
```

---

## Summary: Color Mode Impact on Decoding

| Decoding Step | Color Mode Impact |
|---------------|------------------|
| 1. Pre-processing | ❌ None |
| 2. Locate Finders | ❌ None |
| 3. Locate Alignments | ❌ None |
| 4. Sampling Grid | ❌ None |
| 5. **Decode Nc** | ✅ **Always 3-color mode** |
| 6. **Extract Palette** | ✅ **Interpolation for modes 6-7** |
| 7. **Decode Part II** | ✅ **Variable bits per module** |
| 8. **Decode Data** | ✅ **Interpolation for modes 6-7** |
| 9. **Unmasking** | ✅ **Modulus = color count** |
| 10. De-interleave | ❌ None |
| 11. Error Correction | ❌ None |
| 12. Decode Message | ❌ None |

---

## Error Handling for Interpolated Modes

### Challenge
Modes 6-7 have ambiguous color decoding due to interpolation.

### Mitigation Strategies

1. **Rely on Error Correction**
   - LDPC can correct misclassified modules
   - Higher ECC levels recommended for modes 6-7

2. **Use Context Clues**
   - Check adjacent modules
   - Use spatial correlation

3. **Quality Thresholds**
   - Reject poor-quality images
   - Check color distance metrics

```java
public class InterpolationErrorHandling {
    /**
     * Validate decoded module with context
     */
    public static int validateWithContext(int decodedIndex,
                                          int[][] adjacentIndices,
                                          int[][] fullPalette) {
        // Use adjacent modules to validate
        // If decoded color is very different from neighbors,
        // consider alternative interpretations
        
        int[] decodedRGB = fullPalette[decodedIndex];
        
        // Calculate expected color based on neighbors
        int[] expectedRGB = averageNeighborColors(
            adjacentIndices, fullPalette);
        
        double distance = colorDistance(
            decodedRGB, expectedRGB);
        
        // If too far, try alternative
        if (distance > THRESHOLD) {
            return findAlternativeIndex(
                decodedRGB, expectedRGB, fullPalette);
        }
        
        return decodedIndex;
    }
}
```

---

## References

- **Section 6** - Reference decode algorithm (p. 35-48)
- **Section 6.1** - Decoding procedure overview (p. 35)
- **Section 6.2** - Pre-processing (p. 35-36)
- **Section 6.6** - Decoding metadata and constructing colour palettes (p. 45-47)
- **Section 6.7** - Decoding the data stream (p. 47-48)
- **Table 7** - Nc encoding (reverse for decoding) (p. 15)
- **Annex G.3** - Interpolation for modes 6-7 (p. 67-70)
