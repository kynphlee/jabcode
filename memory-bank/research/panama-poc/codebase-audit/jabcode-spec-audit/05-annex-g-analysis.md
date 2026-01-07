# Annex G Deep Dive: Color Selection and Palette Construction

## Overview

Annex G (Informative) of ISO/IEC 23634 provides critical implementation guidance for color modes beyond the standard 4 and 8 color modes. This document provides expert-level analysis of Annex G with concrete implementation strategies.

**Source:** ISO/IEC 23634:2022, Annex G (p. 67-70)

---

## Annex G Structure

### G.1 Module Colour Selection

**Purpose:** Define RGB color space methodology for all modes

**Key Principle:**
> "To optimize the decoding of JAB Code, the used colours should be as distinguishable as possible. Therefore, the used colours should keep a distance from each other in the RGB colour space cube."

### G.2 Finder Pattern Colour

**Purpose:** Explain why specific colors chosen for finder patterns

**Key Insight:**
> "The three selected colours, Black, Yellow, and Cyan for each finder pattern pair, are the components of colour office printers and at the vertices of the diagonals so that the colour component in each colour channel keeps a large distance from each other."

### G.3 Module Colour Selection for More Than Eight Colours

**Purpose:** Define extended color modes (16, 32, 64, 128, 256)

**Critical:** Provides specific RGB value tables and interpolation rules

---

## G.1: Module Colour Selection - Detailed Analysis

### RGB Color Space Cube Visualization

**Specification Text:**
> "As listed in Table 6, two module colour modes are specified and up to eight module colours can be used in a symbol."

**Cube Vertices (8 colors):**

```
3D Coordinates:        Binary Mapping:
(R, G, B)              [R G B]

(0,0,0)       Black    000
(0,0,255)     Blue     001
(0,255,0)     Green    010
(0,255,255)   Cyan     011
(255,0,0)     Red      100
(255,0,255)   Magenta  101
(255,255,0)   Yellow   110
(255,255,255) White    111
```

**Implementation:**

```java
/**
 * Represents the RGB color space cube
 */
public class RGBColorCube {
    /**
     * Get cube vertex for given binary coordinates
     */
    public static int[] getVertex(boolean r, boolean g, boolean b) {
        return new int[] {
            r ? 255 : 0,
            g ? 255 : 0,
            b ? 255 : 0
        };
    }
    
    /**
     * Calculate maximum pairwise distance in color set
     * Used to verify optimal color selection
     */
    public static double calculateMinDistance(int[][] colors) {
        double minDistance = Double.MAX_VALUE;
        
        for (int i = 0; i < colors.length; i++) {
            for (int j = i + 1; j < colors.length; j++) {
                double dist = euclideanDistance(
                    colors[i][0], colors[i][1], colors[i][2],
                    colors[j][0], colors[j][1], colors[j][2]
                );
                if (dist < minDistance) {
                    minDistance = dist;
                }
            }
        }
        
        return minDistance;
    }
    
    private static double euclideanDistance(int r1, int g1, int b1,
                                            int r2, int g2, int b2) {
        int dr = r1 - r2;
        int dg = g1 - g2;
        int db = b1 - b2;
        return Math.sqrt(dr*dr + dg*dg + db*db);
    }
}
```

### 4-Color Mode (Mode 1)

**Specification Text (G.1a):**
> "When using the 4-colour mode, black, cyan, magenta, and yellow should be used. Black, cyan, and yellow are used for encoding Nc and cyan and yellow are used for alignment patterns."

**Analysis:**

These 4 colors are:
1. **Maximum distance apart** in RGB space
2. **CMYK primaries** (common in printing)
3. **Two used for metadata encoding** (black, cyan, yellow)

**Color Distances:**

| Pair | Distance | Calculation |
|------|----------|-------------|
| Black-Cyan | 360.6 | √(0² + 255² + 255²) |
| Black-Magenta | 360.6 | √(255² + 0² + 255²) |
| Black-Yellow | 360.6 | √(255² + 255² + 0²) |
| Cyan-Magenta | 360.6 | √(255² + 255² + 0²) |
| Cyan-Yellow | 255.0 | √(0² + 0² + 255²) |
| Magenta-Yellow | 255.0 | √(0² + 255² + 0²) |

**Minimum distance:** 255.0 (excellent for printing reliability)

### 8-Color Mode (Mode 2)

**Specification Text (G.1b):**
> "When using the 8-colour mode, each colour channel of R, G, and B takes two values, 0 and 255. These values will generate the 8 colours at the vertexes of the cube."

**Analysis:**

All 8 cube vertices = maximum possible separation for 8 colors

**Minimum pairwise distance:** 255.0 (single channel difference)

**Maximum pairwise distance:** 441.7 (Black to White, all channels different)

---

## G.2: Finder Pattern Colour - Design Rationale

### Specification Analysis

**Text:**
> "The colours used for finder patterns should keep a large distance from each other in the RGB colour space cube and will consider the printing technology being used to print the colours."

**Finder Pattern Color Pairs:**

| Pattern | Inner Color | Outer Color | Distance |
|---------|-------------|-------------|----------|
| FP0 | Black (0,0,0) | Yellow (255,255,0) | 360.6 |
| FP1 | Black (0,0,0) | Yellow (255,255,0) | 360.6 |
| FP2 | Yellow (255,255,0) | Cyan (0,255,255) | 255.0 |
| FP3 | Cyan (0,255,255) | Black (0,0,0) | 360.6 |

**Why These Colors?**

1. **Printing Technology:** Black, Cyan, Yellow are ink components in CMYK printers
2. **Maximum Contrast:** Black vs Yellow = high contrast
3. **Diagonal Vertices:** Black (0,0,0) and Yellow (255,255,0) are far apart
4. **Reliability:** These colors print most consistently

**Implementation Insight:**

```java
/**
 * Finder pattern colors chosen for maximum printability
 * and detectability
 */
public class FinderPatternColors {
    // Colors at RGB cube diagonal vertices
    public static final int[] BLACK = {0, 0, 0};
    public static final int[] YELLOW = {255, 255, 0};
    public static final int[] CYAN = {0, 255, 255};
    
    /**
     * Verify finder pattern colors meet minimum distance requirement
     */
    public static boolean validateFinderPatternColors() {
        double blackYellow = colorDistance(BLACK, YELLOW);
        double yellowCyan = colorDistance(YELLOW, CYAN);
        double cyanBlack = colorDistance(CYAN, BLACK);
        
        // Minimum distance should be > 255
        return blackYellow > 255 && cyanBlack > 255;
        // Note: yellowCyan = 255 is acceptable
    }
    
    private static double colorDistance(int[] c1, int[] c2) {
        int dr = c1[0] - c2[0];
        int dg = c1[1] - c2[1];
        int db = c1[2] - c2[2];
        return Math.sqrt(dr*dr + dg*dg + db*db);
    }
}
```

---

## G.3: Extended Color Modes (16-256 colors)

### G.3(a): 16-Color Mode

**Specification Text:**
> "In case of 16-colour mode, the colour channel R takes 4 values, 0, 85, 170 and 255, and the channels G and B take two values, 0 and 255, which generates 16 colours as listed in Table G.1."

**Analysis:**

**Strategy:** Subdivide R channel while keeping G and B at cube vertices

**Channel Values:**
- R: 4 values (0, 85, 170, 255) - Step: 85
- G: 2 values (0, 255) - Step: 255
- B: 2 values (0, 255) - Step: 255

**Total:** 4 × 2 × 2 = 16 colors

**Color Generation Pattern:**

```
For R in {0, 85, 170, 255}:
    For G in {0, 255}:
        For B in {0, 255}:
            Add color (R, G, B)
```

**Minimum Color Distance:** 85 (adjacent R values with same G,B)

**Implementation:**

```java
public class Mode16ColorGenerator {
    private static final int[] R_VALUES = {0, 85, 170, 255};
    private static final int[] GB_VALUES = {0, 255};
    
    /**
     * Generate all 16 colors per Annex G.3(a)
     */
    public static List<int[]> generateColors() {
        List<int[]> colors = new ArrayList<>();
        
        for (int r : R_VALUES) {
            for (int g : GB_VALUES) {
                for (int b : GB_VALUES) {
                    colors.add(new int[]{r, g, b});
                }
            }
        }
        
        return colors; // 16 colors
    }
    
    /**
     * Verify matches Table G.1 from specification
     */
    public static boolean verifyAgainstTableG1() {
        int[][] expected = {
            {0, 0, 0}, {0, 0, 255}, {0, 255, 0}, {0, 255, 255},
            {85, 0, 0}, {85, 0, 255}, {85, 255, 0}, {85, 255, 255},
            {170, 0, 0}, {170, 0, 255}, {170, 255, 0}, {170, 255, 255},
            {255, 0, 0}, {255, 0, 255}, {255, 255, 0}, {255, 255, 255}
        };
        
        List<int[]> generated = generateColors();
        
        for (int i = 0; i < 16; i++) {
            if (!Arrays.equals(generated.get(i), expected[i])) {
                return false;
            }
        }
        
        return true;
    }
}
```

### G.3(b): 32-Color Mode

**Specification Text:**
> "In case of 32-colour mode, the colour channel R and G take four values, 0, 85, 170 and 255, and the colour channel B takes two values, 0 and 255, which generates 32 colours."

**Analysis:**

**Strategy:** Subdivide R and G channels, keep B at vertices

**Channel Values:**
- R: 4 values (0, 85, 170, 255)
- G: 4 values (0, 85, 170, 255)
- B: 2 values (0, 255)

**Total:** 4 × 4 × 2 = 32 colors

**Minimum Color Distance:** 85

### G.3(c): 64-Color Mode

**Specification Text:**
> "In case of 64-colour mode, each colour channel of R, G and B takes four values, 0, 85, 170 and 255, which generates 64 colours."

**Analysis:**

**Strategy:** Symmetric subdivision of all three channels

**Channel Values:**
- R: 4 values (0, 85, 170, 255)
- G: 4 values (0, 85, 170, 255)
- B: 4 values (0, 85, 170, 255)

**Total:** 4 × 4 × 4 = 64 colors

**Critical Property:** Exactly fills the embedded palette limit (64 colors)

**Minimum Color Distance:** 85 (any single channel differs by 85)

### G.3(d): 128-Color Mode

**Specification Text:**
> "In case of 128-colour mode, the colour channel R takes eight values, 0, 36, 73, 109, 146, 182, 219 and 255, and the colour channel G and B take four values, 0, 85, 170 and 255, which generates 128 colours."

**Analysis:**

**Strategy:** Fine-grain R channel, keep G and B at 64-mode resolution

**Channel Values:**
- R: 8 values (0, 36, 73, 109, 146, 182, 219, 255) - Step: ~37
- G: 4 values (0, 85, 170, 255) - Step: 85
- B: 4 values (0, 85, 170, 255) - Step: 85

**Total:** 8 × 4 × 4 = 128 colors

**Critical Challenge:** Cannot embed all 128 colors (limit is 64)

**Solution (from spec):**

**Embedded Palette Subset:**
- Only embed R values: {0, 73, 182, 255} (4 values)
- Embedded palette: 4 × 4 × 4 = 64 colors

**Reconstruction Rule:**
> "For symbols containing 128 colours, the original full-size colour palette should be reconstructed by interpolating each colour channel of the colours in the extracted colour palettes. For example, the colours whose original R channel value is 36 should be restored by interpolating the colours whose original R channel value is 0 and 73."

**Interpolation Implementation:**

```java
public class Mode128Interpolation {
    private static final int[] R_EMBEDDED = {0, 73, 182, 255};
    private static final int[] R_FULL = {0, 36, 73, 109, 146, 182, 219, 255};
    
    /**
     * Interpolate missing R values from embedded palette
     */
    public static int[] interpolateRChannel(int rEmbedded, int gValue, int bValue,
                                            int[][] embeddedPalette) {
        // Find which embedded R values to interpolate between
        
        if (rEmbedded == 0) {
            // Could be 0 or 36
            // Use color matching against actual module color
        } else if (rEmbedded == 73) {
            // Could be 36, 73, 109, or 146
        } else if (rEmbedded == 182) {
            // Could be 146, 182, or 219
        } else if (rEmbedded == 255) {
            // Could be 219 or 255
        }
        
        // Return possible R values for decoder to choose from
        return getPossibleRValues(rEmbedded);
    }
    
    private static int[] getPossibleRValues(int rEmbedded) {
        switch (rEmbedded) {
            case 0: return new int[]{0, 36};
            case 73: return new int[]{36, 73, 109, 146};
            case 182: return new int[]{146, 182, 219};
            case 255: return new int[]{219, 255};
            default: throw new IllegalArgumentException("Invalid embedded R value");
        }
    }
}
```

### G.3(e): 256-Color Mode

**Specification Text:**
> "In case of 256-colour mode, the colour channel R and G take eight values, 0, 36, 73, 109, 146, 182, 219 and 255, and the colour channel B takes four values, 0, 85, 170 and 255, which generates 256 colours."

**Analysis:**

**Strategy:** Fine-grain R and G channels

**Channel Values:**
- R: 8 values (0, 36, 73, 109, 146, 182, 219, 255)
- G: 8 values (0, 36, 73, 109, 146, 182, 219, 255)
- B: 4 values (0, 85, 170, 255)

**Total:** 8 × 8 × 4 = 256 colors

**Embedded Palette Subset:**
- R: {0, 73, 182, 255} (4 values)
- G: {0, 73, 182, 255} (4 values)
- B: {0, 85, 170, 255} (4 values)
- Embedded: 4 × 4 × 4 = 64 colors

**Reconstruction:** Interpolate both R and G channels independently

---

## Palette Embedding Rules (Critical)

### Specification Text:

> "In either primary or secondary symbols, the colour palettes embedded in the symbol contains only up to 64 colours, indexed from 0 to 63 in the reserved positions."

### Rules Summary:

**Modes 1-5 (≤64 colors):**
- Embed all available colors
- No interpolation needed

**Mode 6 (128 colors):**
- Embed 64-color subset (R channel reduced)
- Decoder interpolates R channel

**Mode 7 (256 colors):**
- Embed 64-color subset (R and G channels reduced)
- Decoder interpolates R and G channels

### Reserved Space Analysis

**Specification Text:**
> "In either primary or secondary symbols, there are 128 modules reserved for two colour palettes. Therefore, each colour palette can contain up to 64 colours."

**Calculation:**
- 128 modules total
- 2 palette locations
- 64 colors per location
- Each color represented by 1 module

**Implementation:**

```java
public class PaletteEmbedding {
    public static final int MAX_EMBEDDED_COLORS = 64;
    public static final int RESERVED_MODULES = 128;
    public static final int PALETTE_LOCATIONS = 2;
    
    /**
     * Determine if full palette can be embedded
     */
    public static boolean canEmbedFullPalette(int colorMode) {
        int colorCount = getColorCount(colorMode);
        return colorCount <= MAX_EMBEDDED_COLORS;
    }
    
    /**
     * Get embedded palette for given mode
     */
    public static int[][] getEmbeddedPalette(int colorMode, 
                                             int[][] fullPalette) {
        if (canEmbedFullPalette(colorMode)) {
            return fullPalette; // All colors fit
        }
        
        // Mode 6 or 7: select subset
        if (colorMode == 6) {
            return selectMode6Subset(fullPalette);
        } else if (colorMode == 7) {
            return selectMode7Subset(fullPalette);
        }
        
        throw new IllegalArgumentException("Invalid color mode");
    }
    
    private static int[][] selectMode6Subset(int[][] fullPalette) {
        // Select only colors with R in {0, 73, 182, 255}
        List<int[]> subset = new ArrayList<>();
        int[] validR = {0, 73, 182, 255};
        
        for (int[] color : fullPalette) {
            for (int r : validR) {
                if (color[0] == r) {
                    subset.add(color);
                    break;
                }
            }
        }
        
        return subset.toArray(new int[0][]);
    }
    
    private static int[][] selectMode7Subset(int[][] fullPalette) {
        // Select only colors with R,G in {0, 73, 182, 255}
        List<int[]> subset = new ArrayList<>();
        int[] validRG = {0, 73, 182, 255};
        
        for (int[] color : fullPalette) {
            boolean validR = false, validG = false;
            
            for (int val : validRG) {
                if (color[0] == val) validR = true;
                if (color[1] == val) validG = true;
            }
            
            if (validR && validG) {
                subset.add(color);
            }
        }
        
        return subset.toArray(new int[0][]);
    }
    
    private static int getColorCount(int colorMode) {
        switch (colorMode) {
            case 1: return 4;
            case 2: return 8;
            case 3: return 16;
            case 4: return 32;
            case 5: return 64;
            case 6: return 128;
            case 7: return 256;
            default: return 0;
        }
    }
}
```

---

## Color Distance Calculation (for dR, dG, dB)

### Specification References

**Section 8.3.1 (Colour Palette Accuracy):**
> "dR, dG, dB is half the distance to the next colour in this colour channel (see Annex G.1)."

**Analysis:**

For each color mode, calculate half-distance to next color in each channel:

**Mode 1 & 2:** dR = dG = dB = 255/2 = 127.5

**Mode 3:**
- dR = 85/2 = 42.5
- dG = 255/2 = 127.5
- dB = 255/2 = 127.5

**Mode 4:**
- dR = 85/2 = 42.5
- dG = 85/2 = 42.5
- dB = 255/2 = 127.5

**Mode 5:**
- dR = dG = dB = 85/2 = 42.5

**Mode 6:**
- dR = 36.5/2 ≈ 18.25 (average of ~37 step)
- dG = 85/2 = 42.5
- dB = 85/2 = 42.5

**Mode 7:**
- dR = dG = 36.5/2 ≈ 18.25
- dB = 85/2 = 42.5

**Implementation:**

```java
public class ColorDistanceCalculator {
    /**
     * Calculate half-distance to next color in each channel
     * Used for quality verification per Section 8.3.1
     */
    public static double[] calculateChannelHalfDistances(int colorMode) {
        switch (colorMode) {
            case 1:
            case 2:
                return new double[]{127.5, 127.5, 127.5};
            
            case 3:
                return new double[]{42.5, 127.5, 127.5};
            
            case 4:
                return new double[]{42.5, 42.5, 127.5};
            
            case 5:
                return new double[]{42.5, 42.5, 42.5};
            
            case 6:
                return new double[]{18.25, 42.5, 42.5};
            
            case 7:
                return new double[]{18.25, 18.25, 42.5};
            
            default:
                throw new IllegalArgumentException("Invalid color mode");
        }
    }
}
```

---

## Summary of Key Implementation Points

### 1. Color Generation

✅ **Modes 1-5:** Generate all colors directly from channel value combinations

✅ **Modes 6-7:** Generate full palette, then create embedded subset

### 2. Palette Embedding

✅ **≤64 colors:** Embed all colors

✅ **>64 colors:** Embed subset, mark for interpolation

### 3. Interpolation (Decoding)

✅ **Mode 6:** Interpolate R channel from 4 embedded values to 8 full values

✅ **Mode 7:** Interpolate R and G channels independently

### 4. Color Distances

✅ Calculate minimum pairwise distance for each mode

✅ Use for quality verification (Section 8.3)

---

## References

- **Annex G** - Guidelines for module colour selection (p. 67-70)
- **Table G.1** - 16-colour mode RGB values (p. 67-68)
- **Table G.2** - User-defined colour modes (p. 69)
- **Figure G.2** - Reserved space in secondary symbol (p. 69)
- **Figure G.3** - Reserved space in primary symbol (p. 70)
- **Section 8.3.1** - Colour Palette Accuracy (p. 54)
- **Section 8.3.2** - Colour Variation in Data Modules (p. 54-55)
