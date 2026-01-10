# LAB Color Space: Technical Design

**Document:** Technical Design Specification  
**Subsystem:** E1 - LAB Color Space  
**Version:** 1.0

---

## Architecture Overview

### Color Pipeline Transformation

**Current (RGB-only):**
```
Print → RGB → RGB Distance → Nearest Color
         ↓                      ↓
      Display              Decode Result
```

**Enhanced (Dual RGB+LAB):**
```
Print → RGB ──┬─→ Display
              │
              └─→ LAB → ΔE Distance → Nearest Color
                   ↑                      ↓
                Lookup Table          Decode Result
```

---

## API Design

### C Implementation

```c
// Core structures
typedef struct {
    double L;  // Lightness (0-100)
    double a;  // Green-Red axis (-128 to +127)
    double b;  // Blue-Yellow axis (-128 to +127)
} jab_lab_color;

// Conversion functions
jab_lab_color rgb_to_lab(jab_byte r, jab_byte g, jab_byte b);
void lab_to_rgb(jab_lab_color lab, jab_byte* r, jab_byte* g, jab_byte* b);

// Distance calculations
jab_float delta_e_76(jab_lab_color c1, jab_lab_color c2);
jab_float delta_e_2000(jab_lab_color c1, jab_lab_color c2);

// Optimized decoder integration
jab_int32 find_nearest_color_lab(
    jab_byte r, jab_byte g, jab_byte b,
    jab_lab_color* lab_palette,
    jab_int32 color_count
);
```

### Java Bindings

```java
public class LabColor {
    public final double L;
    public final double a;
    public final double b;
    
    public static LabColor fromRGB(int r, int g, int b);
    public RGB toRGB();
    public double deltaE76(LabColor other);
    public double deltaE2000(LabColor other);
}

public class LabColorPalette {
    private final LabColor[] labColors;
    private final byte[] rgbColors;
    
    public int findNearest(LabColor observed);
    public int findNearest(int r, int g, int b);
}
```

---

## Conversion Algorithms

### RGB → XYZ → LAB

**Step 1: RGB to XYZ (D65 illuminant)**
```c
// Linearize RGB (inverse gamma correction)
double linear_rgb(jab_byte channel) {
    double v = channel / 255.0;
    if (v <= 0.04045)
        return v / 12.92;
    return pow((v + 0.055) / 1.055, 2.4);
}

// Matrix transformation (sRGB to XYZ, D65)
double r_lin = linear_rgb(r);
double g_lin = linear_rgb(g);
double b_lin = linear_rgb(b);

double X = r_lin * 0.4124564 + g_lin * 0.3575761 + b_lin * 0.1804375;
double Y = r_lin * 0.2126729 + g_lin * 0.7151522 + b_lin * 0.0721750;
double Z = r_lin * 0.0193339 + g_lin * 0.1191920 + b_lin * 0.9503041;
```

**Step 2: XYZ to LAB**
```c
// Reference white D65
const double Xn = 0.95047;
const double Yn = 1.00000;
const double Zn = 1.08883;

double f(double t) {
    const double delta = 6.0/29.0;
    const double delta_cubed = delta * delta * delta;
    
    if (t > delta_cubed)
        return pow(t, 1.0/3.0);
    return (t / (3.0 * delta * delta)) + (4.0/29.0);
}

double fx = f(X / Xn);
double fy = f(Y / Yn);
double fz = f(Z / Zn);

jab_lab_color lab;
lab.L = 116.0 * fy - 16.0;
lab.a = 500.0 * (fx - fy);
lab.b = 200.0 * (fy - fz);
```

### ΔE Calculations

**CIE76 (Simple Euclidean):**
```c
jab_float delta_e_76(jab_lab_color c1, jab_lab_color c2) {
    double dL = c1.L - c2.L;
    double da = c1.a - c2.a;
    double db = c1.b - c2.b;
    return sqrt(dL*dL + da*da + db*db);
}
```

**CIEDE2000 (Advanced, perceptually weighted):**
```c
// Complex formula accounting for:
// - Lightness weighting (kL)
// - Chroma weighting (kC)
// - Hue weighting (kH)
// - Rotation term for blue region
// See full implementation in lab_color.c
```

---

## Performance Optimizations

### Lookup Table Strategy

**Problem:** RGB→LAB conversion is expensive (pow, sqrt operations)

**Solution:** Pre-compute LAB values for palette
```c
typedef struct {
    jab_byte rgb[3 * MAX_PALETTE_SIZE];    // RGB values
    jab_lab_color lab[MAX_PALETTE_SIZE];   // Pre-computed LAB
    jab_int32 size;                         // Number of colors
} jab_lab_palette;

// Initialize once during decoder setup
void init_lab_palette(jab_lab_palette* palette, jab_byte* rgb_palette, jab_int32 size) {
    for (int i = 0; i < size; i++) {
        palette->rgb[i*3+0] = rgb_palette[i*3+0];
        palette->rgb[i*3+1] = rgb_palette[i*3+1];
        palette->rgb[i*3+2] = rgb_palette[i*3+2];
        palette->lab[i] = rgb_to_lab(
            rgb_palette[i*3+0],
            rgb_palette[i*3+1],
            rgb_palette[i*3+2]
        );
    }
}

// Fast decoding: only convert observed color once
int find_nearest_fast(jab_byte r, jab_byte g, jab_byte b, jab_lab_palette* palette) {
    jab_lab_color observed = rgb_to_lab(r, g, b);  // Convert once
    
    int nearest = 0;
    double min_distance = INFINITY;
    
    for (int i = 0; i < palette->size; i++) {
        double dist = delta_e_76(observed, palette->lab[i]);  // Compare in LAB
        if (dist < min_distance) {
            min_distance = dist;
            nearest = i;
        }
    }
    return nearest;
}
```

### SIMD Optimization (Future)

**Potential for vectorization:**
```c
// Process 4 colors simultaneously using AVX2
__m256d delta_e_simd(
    jab_lab_color observed,
    jab_lab_color* palette,
    int count
);
```

---

## Integration Points

### Encoder Changes (Minimal)

```c
// Only add LAB metadata to palette
void generate_palette_with_lab(jab_encode* enc) {
    // Generate RGB palette (existing code)
    genColorPalette(enc->color_number, enc->palette);
    
    // NEW: Pre-compute LAB values for metadata
    for (int i = 0; i < enc->color_count; i++) {
        enc->palette_lab[i] = rgb_to_lab(
            enc->palette[i*3+0],
            enc->palette[i*3+1],
            enc->palette[i*3+2]
        );
    }
    
    // Store in barcode metadata (optional optimization)
    // OR: Decoder can compute on-the-fly from RGB
}
```

### Decoder Changes (Critical)

```c
// Replace in decodeModuleHD()
jab_int32 decodeModuleHD(...) {
    // ... existing code to sample RGB ...
    
    jab_byte module_color[3];
    sampleColorValueHD(ch_index, module, scaling, &module_color);
    
    // OLD: RGB distance
    // color_index = findNearestColorRGB(module_color, palette, color_count);
    
    // NEW: LAB distance
    color_index = findNearestColorLAB(
        module_color[0],
        module_color[1],
        module_color[2],
        lab_palette,  // Pre-computed during decoder init
        color_count
    );
    
    // ... rest of existing code ...
}
```

---

## Testing Strategy

### Unit Tests

**Conversion Accuracy:**
```c
void test_rgb_to_lab_reference_colors() {
    // Test against known good values
    assert_lab_close(rgb_to_lab(255, 255, 255), {100.0, 0.0, 0.0});
    assert_lab_close(rgb_to_lab(0, 0, 0), {0.0, 0.0, 0.0});
    assert_lab_close(rgb_to_lab(255, 0, 0), {53.24, 80.09, 67.20});
    // ... more reference colors
}

void test_round_trip() {
    // RGB → LAB → RGB should be close
    for (int i = 0; i < 1000; i++) {
        jab_byte r = rand() % 256;
        jab_byte g = rand() % 256;
        jab_byte b = rand() % 256;
        
        jab_lab_color lab = rgb_to_lab(r, g, b);
        jab_byte r2, g2, b2;
        lab_to_rgb(lab, &r2, &g2, &b2);
        
        assert(abs(r - r2) <= 1);  // Allow 1 unit error
        assert(abs(g - g2) <= 1);
        assert(abs(b - b2) <= 1);
    }
}
```

**ΔE Validation:**
```c
void test_delta_e_properties() {
    jab_lab_color c1 = {50.0, 10.0, 20.0};
    jab_lab_color c2 = {50.0, 10.0, 20.0};
    
    // Identity
    assert(delta_e_76(c1, c1) == 0.0);
    
    // Symmetry
    assert(delta_e_76(c1, c2) == delta_e_76(c2, c1));
    
    // Known values
    jab_lab_color c3 = {51.0, 10.0, 20.0};
    assert_close(delta_e_76(c1, c3), 1.0);
}
```

### Integration Tests

**Mode 3 Improvement Validation:**
```java
@Test
public void testMode3LABImprovement() {
    // Test problematic R-channel pairs
    int[] darkRed = {85, 0, 0};
    int[] medRed = {170, 0, 0};
    
    // RGB distance: 85 units (ambiguous)
    // LAB ΔE: Should be > 10 (clearer)
    
    LabColor lab1 = LabColor.fromRGB(darkRed);
    LabColor lab2 = LabColor.fromRGB(medRed);
    
    double deltaE = lab1.deltaE76(lab2);
    assertTrue("LAB should show clearer separation", deltaE > 10.0);
    
    // Decode test with noise
    int decoded = decodeWithLAB(addNoise(darkRed, ±5));
    assertEquals("Should correctly identify dark red", 0, decoded);
}
```

---

## Rollout Strategy

### Phase 1: Add LAB Support (Non-Breaking)
- Implement RGB↔LAB conversions
- Add ΔE calculators
- Keep RGB path active
- Feature flag: `USE_LAB_COLOR_SPACE`

### Phase 2: A/B Testing
- Decode same barcode with RGB and LAB
- Compare accuracy
- Measure performance impact
- Validate improvement metrics

### Phase 3: Default LAB
- Make LAB the default
- Keep RGB as fallback
- Deprecate RGB-only path

### Phase 4: LAB-Only (Future)
- Remove RGB distance code
- Optimize for LAB-only workflow

---

## Risk Mitigation

### Performance Risk
**Mitigation:** Lookup table strategy (pre-compute palette LAB values)

### Accuracy Risk
**Mitigation:** Comprehensive unit tests against reference implementation

### Compatibility Risk
**Mitigation:** Gradual rollout with feature flags

### Regression Risk
**Mitigation:** Keep RGB path until LAB proven superior

---

**Status:** Detailed design complete, ready for implementation  
**Review:** Pending architecture approval  
**Next:** Begin implementation sessions
