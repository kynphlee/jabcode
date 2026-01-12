# JABCode Metadata Spiral Pattern: Deep Dive Analysis

**Document Version:** 1.0  
**Date:** 2026-01-11  
**Context:** ColorMode5Test (Nc=5, 64 colors) debugging session

---

## Executive Summary

The JABCode metadata spiral pattern in `getNextMetadataModuleInMaster()` is a **geometrically calculated coordinate sequence** designed for the 21×21 master symbol matrix. This analysis documents the spiral's mathematical properties, the 4-cycle advancement pattern, and critical lessons learned when attempting to extend it for 2-palette mode (Nc≥3).

**Key Finding:** Spiral segment boundaries are not arbitrary—they are mathematically derived to produce valid coordinates within the 21×21 grid. Modifying segment ranges without recalculating the entire geometric sequence produces out-of-bounds coordinates.

---

## Spiral Pattern Architecture

### 1. Core Design Principles

The spiral traverses metadata modules in a clockwise pattern starting from the upper-left corner, moving through four quadrants with coordinate transformations applied at specific boundaries.

#### 1.1 Four-Cycle Advancement Pattern

The spiral advances positions only when `module_count % 4 == 0`. Between advances, it applies coordinate transformations to create the spiral geometry:

```c
// Cycle structure for modules N, N+1, N+2, N+3 (where N % 4 == 0):
// Module N:   Advance position, apply y-flip
// Module N+1: Apply x-flip (no position advance)
// Module N+2: Apply y-flip (no position advance)  
// Module N+3: Apply x-flip (no position advance)
// Module N+4: Advance position, apply y-flip (new cycle)
```

**Mathematical Representation:**
- `module % 4 == 0 or module % 4 == 2`: Apply y-flip: `y = matrix_height - 1 - y`
- `module % 4 == 1 or module % 4 == 3`: Apply x-flip: `x = matrix_width - 1 - x`
- `module % 4 == 0`: Advance position (y-increment or x-decrement based on segment)

### 1.2 Segment Ranges (Original Implementation)

The spiral is divided into segments and gaps that control position advancement:

| Segment | Module Range | Advancement | Purpose |
|---------|--------------|-------------|---------|
| Segment 1 | 0-20 (21 modules) | y += 1 | Part I + initial palette |
| Gap 1 | 21-43 (23 modules) | x -= 1 | Transition to right edge |
| Segment 2 | 44-68 (25 modules) | y += 1 | Continue palette |
| Gap 2 | 69-95 (27 modules) | x -= 1 | Transition |
| Segment 3 | 96-124 (29 modules) | y += 1 | Palette continuation |
| Gap 3 | 125-155 (31 modules) | x -= 1 | Transition |
| Segment 4 | 156-172 (17 modules) | y += 1 | Final palette + Part II |

**Corner Turns** (coordinate swap `x ↔ y`):
- Module 44: Transition from segment 1 to segment 2
- Module 96: Transition from segment 2 to segment 3
- Module 156: Transition from segment 3 to segment 4

### 1.3 Geometric Properties

#### Property 1: Segment Length Progression
Segments increase by 4 modules: 21 → 25 → 29 (Δ=4)  
Gaps increase by 4 modules: 23 → 27 → 31 (Δ=4)

This arithmetic progression is **not coincidental**—it's geometrically necessary to maintain valid coordinates as the spiral moves through the 21×21 matrix.

#### Property 2: 4-Cycle Boundary Alignment
All segment boundaries occur at `module_count % 4 == 0` positions:
- 20, 44, 68, 96, 124, 156, 172

This ensures position advancement aligns with the 4-cycle pattern.

#### Property 3: Coordinate Bounds Preservation
The combination of:
- Segment ranges (y-increment zones)
- Gap ranges (x-decrement zones)
- Y-flips at modules % 4 == 0 or 2
- X-flips at modules % 4 == 1 or 3
- Corner turns at 44, 96, 156

...produces coordinates that **always remain within [0, 20]** for the 21×21 matrix.

---

## 2. Failed Extension Attempt: Lessons Learned

### 2.1 The 2-Palette Challenge

**Problem Context:**
- 4-palette mode (Nc≤2): 248 palette modules (62 colors × 4 palettes)
- 2-palette mode (Nc≥3): 128 palette modules (64 colors × 2 palettes)

Part II metadata follows the palette. For 2-palette mode, the palette ends at module 131 (4 Part I + 128 palette - 1). Part II needs ~7 modules (38 bits ÷ 6 bits/module), spanning modules 132-138.

### 2.2 Attempted Fix: Segment Extension

**Approach:** Extended segment 3 from `96-124` to `96-136` (or `96-140`) to cover 2-palette metadata + Part II.

**Rationale:** Allow Part II modules to use y-increment advancement instead of falling into gap 3 (x-decrement).

**Implementation:**
```c
// Attempted modification:
if (next_module_count >= 96 && next_module_count <= 140) {
    (*y) += 1;  // Extended from 124 to 140
}
```

### 2.3 Failure Analysis

**Simulation Results:**
```
Test with extended segment 3 (96-140):
After palette (module 131): x=8, y=-7  ← INVALID
Module 132: x=8, y=28  ← OUT OF BOUNDS (exceeds 21×21)
Module 133: x=12, y=28 ← OUT OF BOUNDS
Module 134: x=12, y=-8 ← NEGATIVE COORDINATE
```

**Root Cause:**
The spiral's y-flip transformation `y = matrix_height - 1 - y` assumes valid input coordinates. When segment ranges are extended arbitrarily, the accumulated y-increments produce coordinates that, after y-flip, exceed matrix bounds.

**Mathematical Breakdown:**

For a 21×21 matrix (`matrix_height = 21`):
1. Y-increment zone produces increasing y values
2. Y-flip formula: `y_new = 21 - 1 - y_old = 20 - y_old`
3. If `y_old > 20`, then `y_new < 0` (negative coordinate)
4. If `y_old` becomes very large, y-flip produces very negative values

The original segment ranges were **calculated to ensure y never exceeds 20 before y-flip**.

### 2.4 Critical Insight: Geometric Interdependence

**The spiral pattern is a cohesive mathematical system.** Each component depends on others:

- **Segment lengths** determine how many y-increments occur
- **Gap lengths** control x-decrements for horizontal traversal  
- **Y-flips** convert accumulated y-increments into valid coordinates
- **X-flips** handle horizontal reflections
- **Corner turns** transition between spiral arms

Modifying any single component (e.g., segment 3 boundary) without adjusting others breaks the mathematical relationships that guarantee valid coordinates.

---

## 3. Current Solution: Original Ranges + 2-Palette Design

### 3.1 Working Implementation

**Approach:** Keep original spiral ranges unchanged. Implement 2-palette infrastructure (encoder/decoder) to dynamically allocate 2 vs. 4 palettes based on `color_number`.

**Key Changes:**
```c
// Dynamic palette allocation
jab_int32 num_palettes = (color_number > 8) ? 2 : COLOR_PALETTE_NUMBER;

// Palette module count
jab_int32 palette_modules = colors_to_skip * num_palettes;
// For 64 colors: 64 * 2 = 128 modules (vs. 62 * 4 = 248 for 4-palette)
```

**Result:**
- ✅ Palette read completes at module 131 (4 Part I + 128 palette - 1)
- ✅ Part II starts at spiral position for module 132
- ✅ Encoder/decoder synchronized (both use same spiral function)
- ✅ Coordinates remain valid within 21×21 matrix

### 3.2 Part II Position for 2-Palette Mode

With original spiral ranges:
- Module 132 (% 4 = 0): Position advances, falls in **gap 3** (x-decrement zone)
- Modules 125-155 use x-decrement advancement

**Part II Placement:**
```
Module 132 (first Part II): (6, 8)
Module 133: (14, 8) 
Module 134: (14, 12)
...continues through gap 3 with x-decrement pattern
```

This is **geometrically correct** for the 21×21 matrix. The spiral naturally accommodates different metadata lengths by continuing through gap 3.

---

## 4. Recommendations for Future Work

### 4.1 Larger Symbol Sizes

If JABCode supports larger symbol sizes (e.g., 31×31, 41×41), the spiral pattern would need:

1. **Recalculated segment boundaries** based on new matrix dimensions
2. **Adjusted segment length progressions** to maintain coordinate validity
3. **Additional segments/gaps** to cover increased module capacity

**Do NOT simply scale existing ranges linearly.** The geometric relationships must be recalculated from first principles.

### 4.2 Alternative Spiral Designs

For modes with vastly different metadata lengths, consider:

**Option A: Parameterized Spiral**
- Calculate segment ranges dynamically based on `color_number` and matrix dimensions
- Use geometric formulas to derive valid segment boundaries
- Requires deep understanding of the mathematical relationships

**Option B: Multiple Spiral Patterns**
- Define separate spiral functions for different color modes
- `getNextMetadataModuleInMaster_2Palette()` vs. `_4Palette()`
- Clearer separation but increases code complexity

**Option C: Coordinate Validation**
- Add bounds checking after each coordinate transformation
- Clamp or wrap coordinates to valid ranges
- Simpler but may produce unexpected spiral patterns

### 4.3 Documentation Needs

The JABCode specification (ISO/IEC 23634:2022) should document:

1. **Spiral geometry derivation** - how were segment ranges calculated?
2. **Matrix size dependencies** - relationship between symbol size and spiral parameters
3. **Extension guidelines** - how to adapt spiral for new color modes or symbol sizes
4. **Coordinate validity proofs** - mathematical guarantees that coordinates remain in bounds

Currently, the spiral pattern appears to be **implementation-defined** rather than spec-defined, making it difficult to modify correctly.

---

## 5. Testing Methodology

### 5.1 Spiral Simulation Tool

Create a standalone test program to validate spiral patterns:

```c
// Test spiral coordinates for module range
void test_spiral_range(int start_module, int end_module, 
                       int matrix_height, int matrix_width) {
    int x = 6, y = 1;  // Starting position
    
    // Simulate to start_module
    for(int i = 4; i <= start_module; i++) {
        getNextMetadataModuleInMaster(matrix_height, matrix_width, i, &x, &y);
    }
    
    // Test target range
    for(int i = start_module + 1; i <= end_module; i++) {
        getNextMetadataModuleInMaster(matrix_height, matrix_width, i, &x, &y);
        
        // Validate coordinates
        if(x < 0 || x >= matrix_width || y < 0 || y >= matrix_height) {
            printf("INVALID: Module %d → (%d, %d)\n", i, x, y);
        } else {
            printf("Valid: Module %d → (%d, %d)\n", i, x, y);
        }
    }
}
```

### 5.2 Visual Spiral Mapping

Generate a visual representation of the spiral pattern:

```python
import matplotlib.pyplot as plt
import numpy as np

# Simulate spiral and plot module positions
matrix_size = 21
positions = simulate_spiral(matrix_size, num_modules=200)

plt.figure(figsize=(10, 10))
for i, (x, y) in enumerate(positions):
    plt.plot(x, y, 'o', markersize=5, color='blue')
    if i < 50:  # Label first 50 modules
        plt.text(x, y, str(i), fontsize=6)

plt.xlim(-1, matrix_size)
plt.ylim(-1, matrix_size)
plt.grid(True)
plt.title('JABCode Metadata Spiral Pattern')
plt.savefig('spiral_pattern.png')
```

---

## 6. Mathematical Formulation (Future Research)

The spiral pattern likely follows a **discrete parametric curve** with constraints:

**Position Function:**
```
P(n) = (x(n), y(n)) where n is module_count
```

**Constraints:**
- 0 ≤ x(n) < matrix_width  
- 0 ≤ y(n) < matrix_height
- P(n) ≠ P(m) for n ≠ m (no collisions)
- P(n) covers all valid metadata positions

**Transformation Rules:**
- Y-flip: `y_new = (matrix_height - 1) - y_old`
- X-flip: `x_new = (matrix_width - 1) - x_old`
- Corner turn: `(x, y) → (y, x)`

**Advancement Patterns:**
- Y-increment segments: `y_new = y_old + 1` (when n % 4 == 0)
- X-decrement gaps: `x_new = x_old - 1` (when n % 4 == 0)

**Open Questions:**
1. What is the closed-form expression for segment boundaries?
2. How do segment lengths relate to matrix dimensions?
3. Can the spiral be generalized to arbitrary M×N matrices?
4. What is the maximum number of modules the spiral can address?

---

## 7. Conclusion

The JABCode metadata spiral pattern is a sophisticated geometric construction that cannot be modified piecemeal. Attempts to extend segment ranges for 2-palette mode demonstrated that the spiral's mathematical properties are tightly coupled.

**Key Takeaways:**
1. ✅ **Original spiral ranges are geometrically correct** for 21×21 matrix
2. ✅ **4-cycle advancement pattern is fundamental** to the spiral's design
3. ❌ **Arbitrary segment extensions break coordinate validity**
4. ✅ **2-palette mode works with original spiral** - Part II naturally continues through gap 3
5. ⚠️ **Future modifications require complete geometric recalculation**

The successful 2-palette implementation maintains the spiral's integrity while adapting the encoder/decoder infrastructure to handle dynamic palette counts. This approach respects the spiral's mathematical foundation while achieving the goal of 64-color support.

---

## References

- ISO/IEC 23634:2022 - JABCode Specification
- `decoder.c:getNextMetadataModuleInMaster()` - Spiral implementation
- ColorMode5Test debugging session (2026-01-11)
- Spiral coordinate simulation test program

**Document Maintenance:**  
This document should be updated if:
- New spiral patterns are discovered in the specification
- Alternative implementations are developed for different symbol sizes
- Mathematical formulation is derived
- Additional color modes require spiral modifications
