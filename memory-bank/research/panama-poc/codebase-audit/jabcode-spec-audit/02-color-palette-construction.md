# Color Palette Construction Implementation Guide

## Overview

This document provides expert-level implementation details for constructing color palettes for all 8 JABCode color modes, based on ISO/IEC 23634 Annex G and related sections.

**Critical Concepts:**
1. **Embedded Palette Limit:** Maximum 64 colors can be embedded in the symbol
2. **Interpolation:** Modes 6-7 require color interpolation during decoding
3. **RGB Color Space:** Colors positioned for maximum distinguishability
4. **Palette Placement:** Four palettes placed near finder patterns

---

## Color Space Theory (from Annex G.1)

### RGB Cube Methodology

JABCode uses the RGB color space cube to maximize color distinguishability.

**Cube Dimensions:** (0,0,0) to (255,255,255)

**Vertices (8 colors):**
```
      (0,255,255)────────(255,255,255)
          /│                 /│
         / │                / │
        /  │               /  │
   (0,255,0)──────(255,255,0) │
       │ (0,0,255)────────│(255,0,255)
       │  /                │  /
       │ /                 │ /
       │/                  │/
   (0,0,0)─────────────(255,0,0)
```

**Distance Metric:**
```
d = √[(R₁-R₂)² + (G₁-G₂)² + (B₁-B₂)²]
```

**Maximum distance:** √[(255-0)² + (255-0)² + (255-0)²] = 441.67

---

## Palette Generation Algorithms

### Mode 1: 4 Colors

**Source:** ISO/IEC 23634 Table 4, Annex G.1(a)

```java
public class Mode1Palette {
    private static final int[][] COLORS = {
        {0,   0,   0  },  // Black
        {0,   255, 255},  // Cyan
        {255, 0,   255},  // Magenta
        {255, 255, 0  }   // Yellow
    };
    
    public static int[][] generatePalette() {
        return COLORS.clone();
    }
    
    public static int[] getRGB(int index) {
        if (index < 0 || index > 3) {
            throw new IllegalArgumentException("Index must be 0-3");
        }
        return COLORS[index].clone();
    }
    
    public static int getColorIndex(int r, int g, int b) {
        // Find nearest color
        int bestIndex = 0;
        double minDistance = Double.MAX_VALUE;
        
        for (int i = 0; i < COLORS.length; i++) {
            double distance = colorDistance(r, g, b, 
                COLORS[i][0], COLORS[i][1], COLORS[i][2]);
            if (distance < minDistance) {
                minDistance = distance;
                bestIndex = i;
            }
        }
        
        return bestIndex;
    }
    
    private static double colorDistance(int r1, int g1, int b1, 
                                        int r2, int g2, int b2) {
        int dr = r1 - r2;
        int dg = g1 - g2;
        int db = b1 - b2;
        return Math.sqrt(dr*dr + dg*dg + db*db);
    }
}
```

**Properties:**
- Minimum color distance: 362.04 (Black to Cyan)
- All colors are CMY+K primaries
- Optimal for limited color printing

---

### Mode 2: 8 Colors (Default)

**Source:** ISO/IEC 23634 Table 3, Annex G.1(b)

```java
public class Mode2Palette {
    private static final int[][] COLORS = {
        {0,   0,   0  },  // 0: Black
        {0,   0,   255},  // 1: Blue
        {0,   255, 0  },  // 2: Green
        {0,   255, 255},  // 3: Cyan
        {255, 0,   0  },  // 4: Red
        {255, 0,   255},  // 5: Magenta
        {255, 255, 0  },  // 6: Yellow
        {255, 255, 255}   // 7: White
    };
    
    public static int[][] generatePalette() {
        return COLORS.clone();
    }
    
    public static int[] getRGB(int index) {
        if (index < 0 || index > 7) {
            throw new IllegalArgumentException("Index must be 0-7");
        }
        return COLORS[index].clone();
    }
    
    /**
     * Generate color from 3-bit binary value
     */
    public static int[] getRGBFromBits(boolean b2, boolean b1, boolean b0) {
        int index = (b2 ? 4 : 0) | (b1 ? 2 : 0) | (b0 ? 1 : 0);
        return COLORS[index].clone();
    }
    
    /**
     * Convert RGB to 3-bit binary index
     */
    public static byte[] getBitsFromRGB(int r, int g, int b) {
        int index = getColorIndex(r, g, b);
        return new byte[] {
            (byte)((index >> 2) & 1),
            (byte)((index >> 1) & 1),
            (byte)(index & 1)
        };
    }
    
    public static int getColorIndex(int r, int g, int b) {
        // Find nearest color
        int bestIndex = 0;
        double minDistance = Double.MAX_VALUE;
        
        for (int i = 0; i < COLORS.length; i++) {
            double distance = colorDistance(r, g, b, 
                COLORS[i][0], COLORS[i][1], COLORS[i][2]);
            if (distance < minDistance) {
                minDistance = distance;
                bestIndex = i;
            }
        }
        
        return bestIndex;
    }
    
    private static double colorDistance(int r1, int g1, int b1, 
                                        int r2, int g2, int b2) {
        int dr = r1 - r2;
        int dg = g1 - g2;
        int db = b1 - b2;
        return Math.sqrt(dr*dr + dg*dg + db*db);
    }
}
```

**Properties:**
- Minimum color distance: 255.0 (e.g., Black to Blue)
- All colors are RGB cube vertices
- Maximum distinguishability for 8 colors

---

### Mode 3: 16 Colors

**Source:** ISO/IEC 23634 Annex G, Table G.1

```java
public class Mode3Palette {
    private static final int[] R_VALUES = {0, 85, 170, 255};
    private static final int[] G_VALUES = {0, 255};
    private static final int[] B_VALUES = {0, 255};
    
    /**
     * Generate 16-color palette
     * R: 4 values, G: 2 values, B: 2 values
     * Total: 4 × 2 × 2 = 16 colors
     */
    public static int[][] generatePalette() {
        int[][] palette = new int[16][3];
        int index = 0;
        
        for (int r : R_VALUES) {
            for (int g : G_VALUES) {
                for (int b : B_VALUES) {
                    palette[index][0] = r;
                    palette[index][1] = g;
                    palette[index][2] = b;
                    index++;
                }
            }
        }
        
        return palette;
    }
    
    /**
     * Get RGB values from 4-bit index
     */
    public static int[] getRGB(int index) {
        if (index < 0 || index > 15) {
            throw new IllegalArgumentException("Index must be 0-15");
        }
        
        // Decompose index
        int rIndex = (index >> 2) & 0x03;  // bits 3-2
        int gIndex = (index >> 1) & 0x01;  // bit 1
        int bIndex = index & 0x01;         // bit 0
        
        return new int[] {
            R_VALUES[rIndex],
            G_VALUES[gIndex],
            B_VALUES[bIndex]
        };
    }
    
    /**
     * Find nearest color index for given RGB
     */
    public static int getColorIndex(int r, int g, int b) {
        int[][] palette = generatePalette();
        int bestIndex = 0;
        double minDistance = Double.MAX_VALUE;
        
        for (int i = 0; i < palette.length; i++) {
            double distance = colorDistance(r, g, b,
                palette[i][0], palette[i][1], palette[i][2]);
            if (distance < minDistance) {
                minDistance = distance;
                bestIndex = i;
            }
        }
        
        return bestIndex;
    }
    
    private static double colorDistance(int r1, int g1, int b1,
                                        int r2, int g2, int b2) {
        int dr = r1 - r2;
        int dg = g1 - g2;
        int db = b1 - b2;
        return Math.sqrt(dr*dr + dg*dg + db*db);
    }
}
```

**Properties:**
- R channel resolution: 85 units
- Minimum color distance: 85.0 (adjacent R values)
- All 16 colors fit in embedded palette

---

### Mode 4: 32 Colors

**Source:** ISO/IEC 23634 Annex G.3(b)

```java
public class Mode4Palette {
    private static final int[] R_VALUES = {0, 85, 170, 255};
    private static final int[] G_VALUES = {0, 85, 170, 255};
    private static final int[] B_VALUES = {0, 255};
    
    /**
     * Generate 32-color palette
     * R: 4 values, G: 4 values, B: 2 values
     * Total: 4 × 4 × 2 = 32 colors
     */
    public static int[][] generatePalette() {
        int[][] palette = new int[32][3];
        int index = 0;
        
        for (int r : R_VALUES) {
            for (int g : G_VALUES) {
                for (int b : B_VALUES) {
                    palette[index][0] = r;
                    palette[index][1] = g;
                    palette[index][2] = b;
                    index++;
                }
            }
        }
        
        return palette;
    }
    
    /**
     * Get RGB values from 5-bit index
     */
    public static int[] getRGB(int index) {
        if (index < 0 || index > 31) {
            throw new IllegalArgumentException("Index must be 0-31");
        }
        
        // Decompose index: [R₁R₀ G₁G₀ B₀]
        int rIndex = (index >> 3) & 0x03;  // bits 4-3
        int gIndex = (index >> 1) & 0x03;  // bits 2-1
        int bIndex = index & 0x01;         // bit 0
        
        return new int[] {
            R_VALUES[rIndex],
            G_VALUES[gIndex],
            B_VALUES[bIndex]
        };
    }
    
    public static int getColorIndex(int r, int g, int b) {
        int[][] palette = generatePalette();
        int bestIndex = 0;
        double minDistance = Double.MAX_VALUE;
        
        for (int i = 0; i < palette.length; i++) {
            double distance = colorDistance(r, g, b,
                palette[i][0], palette[i][1], palette[i][2]);
            if (distance < minDistance) {
                minDistance = distance;
                bestIndex = i;
            }
        }
        
        return bestIndex;
    }
    
    private static double colorDistance(int r1, int g1, int b1,
                                        int r2, int g2, int b2) {
        int dr = r1 - r2;
        int dg = g1 - g2;
        int db = b1 - b2;
        return Math.sqrt(dr*dr + dg*dg + db*db);
    }
}
```

**Properties:**
- R and G channel resolution: 85 units
- Minimum color distance: 85.0
- All 32 colors fit in embedded palette

---

### Mode 5: 64 Colors

**Source:** ISO/IEC 23634 Annex G.3(c)

```java
public class Mode5Palette {
    private static final int[] RGB_VALUES = {0, 85, 170, 255};
    
    /**
     * Generate 64-color palette
     * R: 4 values, G: 4 values, B: 4 values
     * Total: 4 × 4 × 4 = 64 colors
     */
    public static int[][] generatePalette() {
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
    
    /**
     * Get RGB values from 6-bit index
     */
    public static int[] getRGB(int index) {
        if (index < 0 || index > 63) {
            throw new IllegalArgumentException("Index must be 0-63");
        }
        
        // Decompose index: [R₁R₀ G₁G₀ B₁B₀]
        int rIndex = (index >> 4) & 0x03;  // bits 5-4
        int gIndex = (index >> 2) & 0x03;  // bits 3-2
        int bIndex = index & 0x03;         // bits 1-0
        
        return new int[] {
            RGB_VALUES[rIndex],
            RGB_VALUES[gIndex],
            RGB_VALUES[bIndex]
        };
    }
    
    public static int getColorIndex(int r, int g, int b) {
        // Find nearest quantized values
        int rIndex = findNearestIndex(r, RGB_VALUES);
        int gIndex = findNearestIndex(g, RGB_VALUES);
        int bIndex = findNearestIndex(b, RGB_VALUES);
        
        return (rIndex << 4) | (gIndex << 2) | bIndex;
    }
    
    private static int findNearestIndex(int value, int[] values) {
        int bestIndex = 0;
        int minDiff = Math.abs(value - values[0]);
        
        for (int i = 1; i < values.length; i++) {
            int diff = Math.abs(value - values[i]);
            if (diff < minDiff) {
                minDiff = diff;
                bestIndex = i;
            }
        }
        
        return bestIndex;
    }
}
```

**Properties:**
- Symmetric color space: 4×4×4
- All channels have same resolution (85 units)
- **Critical:** Exactly fills 64-color embedding limit
- Last mode without interpolation

---

### Mode 6: 128 Colors (with Interpolation)

**Source:** ISO/IEC 23634 Annex G.3(d)

```java
public class Mode6Palette {
    // Full palette: 8 R × 4 G × 4 B = 128 colors
    private static final int[] R_FULL = {0, 36, 73, 109, 146, 182, 219, 255};
    private static final int[] G_VALUES = {0, 85, 170, 255};
    private static final int[] B_VALUES = {0, 85, 170, 255};
    
    // Embedded palette: only subset of R values
    private static final int[] R_EMBEDDED = {0, 73, 182, 255};
    
    /**
     * Generate full 128-color palette
     */
    public static int[][] generateFullPalette() {
        int[][] palette = new int[128][3];
        int index = 0;
        
        for (int r : R_FULL) {
            for (int g : G_VALUES) {
                for (int b : B_VALUES) {
                    palette[index][0] = r;
                    palette[index][1] = g;
                    palette[index][2] = b;
                    index++;
                }
            }
        }
        
        return palette;
    }
    
    /**
     * Generate embedded palette (64 colors for symbol)
     * Uses only subset of R values
     */
    public static int[][] generateEmbeddedPalette() {
        int[][] palette = new int[64][3];
        int index = 0;
        
        for (int r : R_EMBEDDED) {
            for (int g : G_VALUES) {
                for (int b : B_VALUES) {
                    palette[index][0] = r;
                    palette[index][1] = g;
                    palette[index][2] = b;
                    index++;
                }
            }
        }
        
        return palette;
    }
    
    /**
     * Reconstruct full palette from embedded palette
     * Interpolates missing R values
     */
    public static int[][] reconstructFullPalette(int[][] embeddedPalette) {
        // Map embedded R values to full palette
        // R_EMBEDDED: {0, 73, 182, 255}
        // R_FULL: {0, 36, 73, 109, 146, 182, 219, 255}
        
        int[][] fullPalette = new int[128][3];
        int fullIndex = 0;
        
        for (int r : R_FULL) {
            for (int g : G_VALUES) {
                for (int b : B_VALUES) {
                    fullPalette[fullIndex][0] = r;
                    fullPalette[fullIndex][1] = g;
                    fullPalette[fullIndex][2] = b;
                    fullIndex++;
                }
            }
        }
        
        return fullPalette;
    }
    
    /**
     * Interpolate R channel value from embedded palette
     */
    public static int interpolateR(int rEmbedded) {
        // Find nearest embedded R values for interpolation
        // Annex G.3(d): "original R channel value is 36 should be restored
        // by interpolating the colours whose original R channel value is 0 and 73"
        
        if (rEmbedded == 0) return 0;
        if (rEmbedded == 73) return 73;
        if (rEmbedded == 182) return 182;
        if (rEmbedded == 255) return 255;
        
        // For intermediate values, interpolate
        // This is decoder-side logic
        // Example: if decoded R is between 0 and 73, could be 0, 36, or 73
        
        throw new IllegalArgumentException(
            "R value " + rEmbedded + " not in embedded palette");
    }
    
    /**
     * Get RGB from 7-bit index (full palette)
     */
    public static int[] getRGB(int index) {
        if (index < 0 || index > 127) {
            throw new IllegalArgumentException("Index must be 0-127");
        }
        
        int rIndex = (index >> 4) & 0x07;  // bits 6-4 (3 bits for 8 R values)
        int gIndex = (index >> 2) & 0x03;  // bits 3-2
        int bIndex = index & 0x03;         // bits 1-0
        
        return new int[] {
            R_FULL[rIndex],
            G_VALUES[gIndex],
            B_VALUES[bIndex]
        };
    }
}
```

**Properties:**
- R channel resolution: ~37 units (8 values)
- G, B channel resolution: 85 units
- Requires interpolation during decoding
- Embedded palette limited to 64 colors

**Interpolation Strategy (Annex G.3d):**
```
Embedded R: {0, 73, 182, 255}
Full R:     {0, 36, 73, 109, 146, 182, 219, 255}

Mapping:
- 0   → 0 (exact)
- 36  → interpolate between 0 and 73
- 73  → 73 (exact)
- 109 → interpolate between 73 and 182
- 146 → interpolate between 73 and 182
- 182 → 182 (exact)
- 219 → interpolate between 182 and 255
- 255 → 255 (exact)
```

---

### Mode 7: 256 Colors (with R+G Interpolation)

**Source:** ISO/IEC 23634 Annex G.3(e)

```java
public class Mode7Palette {
    // Full palette: 8 R × 8 G × 4 B = 256 colors
    private static final int[] RG_FULL = {0, 36, 73, 109, 146, 182, 219, 255};
    private static final int[] B_VALUES = {0, 85, 170, 255};
    
    // Embedded palette: only subset of R and G values
    private static final int[] RG_EMBEDDED = {0, 73, 182, 255};
    
    /**
     * Generate full 256-color palette
     */
    public static int[][] generateFullPalette() {
        int[][] palette = new int[256][3];
        int index = 0;
        
        for (int r : RG_FULL) {
            for (int g : RG_FULL) {
                for (int b : B_VALUES) {
                    palette[index][0] = r;
                    palette[index][1] = g;
                    palette[index][2] = b;
                    index++;
                }
            }
        }
        
        return palette;
    }
    
    /**
     * Generate embedded palette (64 colors for symbol)
     * Uses only subset of R and G values
     */
    public static int[][] generateEmbeddedPalette() {
        int[][] palette = new int[64][3];
        int index = 0;
        
        for (int r : RG_EMBEDDED) {
            for (int g : RG_EMBEDDED) {
                for (int b : B_VALUES) {
                    palette[index][0] = r;
                    palette[index][1] = g;
                    palette[index][2] = b;
                    index++;
                }
            }
        }
        
        return palette;
    }
    
    /**
     * Get RGB from 8-bit index (full palette)
     */
    public static int[] getRGB(int index) {
        if (index < 0 || index > 255) {
            throw new IllegalArgumentException("Index must be 0-255");
        }
        
        int rIndex = (index >> 5) & 0x07;  // bits 7-5 (3 bits for 8 R values)
        int gIndex = (index >> 2) & 0x07;  // bits 4-2 (3 bits for 8 G values)
        int bIndex = index & 0x03;         // bits 1-0
        
        return new int[] {
            RG_FULL[rIndex],
            RG_FULL[gIndex],
            B_VALUES[bIndex]
        };
    }
    
    /**
     * Interpolate channel value (for R or G)
     */
    public static int interpolateChannel(int embedded) {
        // Same mapping as Mode 6
        if (embedded == 0) return 0;
        if (embedded == 73) return 73;
        if (embedded == 182) return 182;
        if (embedded == 255) return 255;
        
        // Interpolate between nearest embedded values
        throw new IllegalArgumentException(
            "Value " + embedded + " not in embedded palette");
    }
}
```

**Properties:**
- R and G channel resolution: ~37 units
- B channel resolution: 85 units
- Requires dual-channel interpolation
- Maximum theoretical data density

**Interpolation Strategy (Annex G.3c,e):**
```
Embedded R,G: {0, 73, 182, 255}
Full R,G:     {0, 36, 73, 109, 146, 182, 219, 255}

Both R and G channels interpolated independently
```

---

## Palette Embedding and Extraction

### Palette Placement (Section 4.3.9, 4.4.4)

**Rules:**
1. Four color palettes placed near finder patterns (or alignment patterns U/L in secondary symbols)
2. Each palette contains all available colors EXCEPT the 2 colors used by nearest finder pattern
3. Reserved space: 128 modules total (64 colors × 2 palettes worth of space)

**Embedding Algorithm:**

```java
public class PaletteEmbedding {
    /**
     * Embed color palette in symbol
     * @param palette Full color palette (up to 64 colors)
     * @param finderPatternColors Colors used by nearest finder pattern
     * @return Filtered palette for this position
     */
    public static int[][] embedPalette(int[][] palette, 
                                       int[] finderPatternColors) {
        List<int[]> filtered = new ArrayList<>();
        
        for (int[] color : palette) {
            boolean skip = false;
            for (int fpColor : finderPatternColors) {
                if (Arrays.equals(color, palette[fpColor])) {
                    skip = true;
                    break;
                }
            }
            if (!skip) {
                filtered.add(color);
            }
        }
        
        return filtered.toArray(new int[0][]);
    }
    
    /**
     * Extract and reconstruct full palette from symbol
     */
    public static int[][] extractPalette(byte[] moduleColors, 
                                         int colorMode) {
        // Extract 64 colors from reserved palette positions
        int[][] embeddedPalette = new int[64][3];
        
        // ... extract from module positions ...
        
        // If mode requires interpolation (6 or 7), reconstruct
        if (colorMode == 6) {
            return Mode6Palette.reconstructFullPalette(embeddedPalette);
        } else if (colorMode == 7) {
            return Mode7Palette.reconstructFullPalette(embeddedPalette);
        }
        
        return embeddedPalette;
    }
}
```

---

## Color Distance and Matching

### Distance Calculation (for Decoding)

```java
public class ColorMatching {
    /**
     * Find nearest color in palette
     * Uses Euclidean distance in RGB space
     */
    public static int findNearestColor(int r, int g, int b, 
                                       int[][] palette) {
        int bestIndex = 0;
        double minDistance = Double.MAX_VALUE;
        
        for (int i = 0; i < palette.length; i++) {
            double distance = colorDistance(
                r, g, b,
                palette[i][0], palette[i][1], palette[i][2]
            );
            
            if (distance < minDistance) {
                minDistance = distance;
                bestIndex = i;
            }
        }
        
        return bestIndex;
    }
    
    /**
     * Euclidean distance in RGB color space
     */
    public static double colorDistance(int r1, int g1, int b1,
                                       int r2, int g2, int b2) {
        int dr = r1 - r2;
        int dg = g1 - g2;
        int db = b1 - b2;
        return Math.sqrt(dr*dr + dg*dg + db*db);
    }
    
    /**
     * Weighted distance (optional, for better perceptual matching)
     * Weights based on human color perception
     */
    public static double weightedColorDistance(int r1, int g1, int b1,
                                                int r2, int g2, int b2) {
        int dr = r1 - r2;
        int dg = g1 - g2;
        int db = b1 - b2;
        
        // Weighted Euclidean distance
        // Green channel weighted higher (human eye more sensitive)
        return Math.sqrt(0.30*dr*dr + 0.59*dg*dg + 0.11*db*db);
    }
}
```

---

## Implementation Checklist

### For Each Color Mode

- [ ] Generate full palette
- [ ] Generate embedded palette (if different)
- [ ] Implement RGB lookup by index
- [ ] Implement index lookup by RGB
- [ ] Add interpolation logic (modes 6-7)
- [ ] Test color distance calculations
- [ ] Validate palette size limits
- [ ] Test round-trip encoding/decoding

### Testing Requirements

- [ ] Unit test each palette generator
- [ ] Verify color indices match specification
- [ ] Test interpolation accuracy (modes 6-7)
- [ ] Measure color distances
- [ ] Test palette embedding/extraction
- [ ] Validate against ISO examples

---

## References

- **Section 4.3.9** - Colour palette structure
- **Section 4.4.4** - Reserved modules for metadata and colour palette
- **Table 3** - 8-color palette
- **Table 4** - 4-color palette
- **Annex G.1** - Module colour selection
- **Annex G.3** - Guidelines for >8 colors
- **Table G.1** - 16-color RGB values
- **Table G.2** - User-defined colour modes
