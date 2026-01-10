# Adaptive Palettes Subsystem

**Subsystem ID:** E2  
**Priority:** High  
**Estimated Effort:** 2-3 weeks  
**Dependencies:** E1 (LAB Color Space)  
**Impact:** +10-15% reliability improvement for modes 3-7

---

## 🎯 Objective

Dynamically optimize color palettes based on actual lighting conditions, camera characteristics, and substrate properties to maximize color discrimination in real-world environments.

---

## 📋 Problem Statement

### Fixed Palette Limitations

**Current Approach:**
```
Spec-defined RGB values (fixed):
├─ Mode 3: {0, 85, 170, 255} for R-channel
├─ Assumes perfect sRGB display/print
├─ Ignores actual environment
└─ One-size-fits-all approach

Reality:
├─ Lighting varies (tungsten, fluorescent, daylight, LED)
├─ Cameras have different white balance
├─ Print substrates affect color appearance
└─ Fixed palette is suboptimal
```

**The Problem:**
- 85-unit spacing assumes ideal conditions
- Real-world lighting shifts color appearance
- Camera sensors have different characteristics
- Print quality varies by substrate and printer

---

## 🎯 Adaptive Solution

### Dynamic Palette Optimization

**Core Idea:**
```
Instead of: Fixed {0, 85, 170, 255}
Use: Optimized {0, 92, 178, 255} (for this environment)

Adapt to:
├─ Actual measured lighting conditions
├─ Camera white balance and color response
├─ Substrate reflectance properties
└─ Maximize perceptual separation
```

**Benefits:**
- Better color separation in actual conditions
- Compensate for environmental variations
- Exploit camera-specific characteristics
- Improve 85-unit spacing discrimination

---

## 🏗️ Architecture

```
┌──────────────────────────────────────────────────┐
│        Adaptive Palettes Subsystem               │
├──────────────────────────────────────────────────┤
│                                                   │
│  ┌──────────────────┐    ┌──────────────────┐   │
│  │ Environment      │───►│ Palette          │   │
│  │ Profiler         │    │ Optimizer        │   │
│  └──────────────────┘    └─────────┬────────┘   │
│           │                        │             │
│           ▼                        ▼             │
│  ┌──────────────────┐    ┌──────────────────┐   │
│  │ Lighting Model   │    │ Optimized        │   │
│  │ (Calibration)    │───►│ Palette          │   │
│  └──────────────────┘    └──────────────────┘   │
│                                                   │
└───────────────────────────────────────────────────┘
```

### Adaptation Strategies

**Strategy 1: Calibration Card (Recommended)**
```
Use reference colors in finder patterns:
├─ Cyan corners are known colors
├─ Measure actual RGB values
├─ Compute transformation matrix
└─ Apply to entire palette
```

**Strategy 2: Statistical Analysis**
```
Analyze barcode color distribution:
├─ Identify likely palette colors
├─ Cluster observed values
├─ Refine palette to match observations
└─ Iterative optimization
```

**Strategy 3: Machine Learning (Future)**
```
Train adaptive model:
├─ Learn environment-to-palette mapping
├─ Use historical successful scans
└─ Predict optimal palette
```

---

## 📊 Expected Improvements

| Mode | Baseline (+LAB) | +Adaptive | Total Improvement |
|------|-----------------|-----------|-------------------|
| 3 | 46-51% | +8-12% | 54-63% |
| 4 | 38-45% | +8-12% | 46-57% |
| 5 | 37-42% | +10-15% | 47-57% |
| 6 | 28-33% | +5-8% | 33-41% |
| 7 | 23-28% | +3-5% | 26-33% |

**Key Insight:** Greatest benefit for modes with 85-unit spacing.

---

## 🔬 Technical Approach

### Calibration Using Finder Patterns

**Known Colors (Cyan corners):**
```c
// Expected: Cyan = (0, 255, 255) in sRGB
jab_byte expected[3] = {0, 255, 255};

// Observed: Measure actual cyan in image
jab_byte observed[3] = sample_finder_pattern_color();

// Compute color shift
jab_float r_shift = observed[0] - expected[0];
jab_float g_shift = observed[1] - expected[1];  
jab_float b_shift = observed[2] - expected[2];

// Apply to entire palette
for (int i = 0; i < palette_size; i++) {
    palette_adapted[i*3+0] = clamp(palette[i*3+0] + r_shift);
    palette_adapted[i*3+1] = clamp(palette[i*3+1] + g_shift);
    palette_adapted[i*3+2] = clamp(palette[i*3+2] + b_shift);
}
```

### Advanced: Color Matrix Transformation

**3×3 Color Correction Matrix:**
```c
// Measure multiple reference colors
// Solve for matrix M such that: observed = M × expected
double M[3][3] = compute_color_correction_matrix(
    expected_colors,
    observed_colors,
    num_references
);

// Apply to palette
for (int i = 0; i < palette_size; i++) {
    jab_byte rgb[3] = {palette[i*3], palette[i*3+1], palette[i*3+2]};
    jab_byte rgb_adapted[3];
    matrix_multiply(M, rgb, rgb_adapted);
    // ... store adapted palette
}
```

---

## 🎯 Implementation Phases

### Phase 1: Simple Offset Calibration
- Use cyan finder pattern as reference
- Compute RGB shift
- Apply uniform offset
- Test improvement

### Phase 2: Full Matrix Calibration
- Use multiple reference colors
- Compute 3×3 transformation
- Apply per-channel correction
- Validate enhancement

### Phase 3: ML-Based Adaptation (Future)
- Train environment classifier
- Predict optimal palette
- Continuous adaptation

---

## 📁 Implementation Files

### Core
- `src/jabcode/adaptive_palette.c`
- `src/jabcode/adaptive_palette.h`
- `src/jabcode/calibration.c`

### Java Wrapper
- `panama-wrapper/.../AdaptivePalette.java`
- `panama-wrapper/.../ColorCalibration.java`

### Tests
- `src/jabcode/test_adaptive_palette.c`
- `panama-wrapper-itest/.../AdaptivePaletteTest.java`

---

## 🚀 Session Guides

- `SESSIONS_1-2_CALIBRATION.md` - Implement calibration
- `SESSIONS_3-4_OPTIMIZATION.md` - Palette optimization
- `SESSIONS_5-6_VALIDATION.md` - Testing and validation

---

**Status:** 📋 Designed, ready for implementation  
**Dependencies:** Complete E1 (LAB Color Space) first  
**Next Steps:** Begin calibration implementation
