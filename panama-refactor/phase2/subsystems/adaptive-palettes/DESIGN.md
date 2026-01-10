# Adaptive Palettes: Technical Design

## Calibration Approaches

### Approach 1: Finder Pattern Reference
Use cyan corners as known colors to compute color shift.

### Approach 2: Statistical Clustering
Analyze color distribution and refine palette iteratively.

### Approach 3: ML-Based Prediction
Train model on successful scans to predict optimal palette.

## API Design

```c
typedef struct {
    jab_byte rgb_expected[3];
    jab_byte rgb_observed[3];
} jab_color_reference;

jab_palette* create_adaptive_palette(
    jab_byte* base_palette,
    jab_int32 color_count,
    jab_color_reference* references,
    jab_int32 ref_count
);

double[3][3] compute_color_matrix(
    jab_color_reference* refs,
    jab_int32 count
);
```

See full specification in implementation files.
