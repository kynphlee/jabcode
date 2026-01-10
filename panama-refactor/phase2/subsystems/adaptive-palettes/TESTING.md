# Adaptive Palettes: Testing Strategy

## Test Scenarios

### Lighting Variations
- Tungsten (warm, red-shifted)
- Fluorescent (cool, green-shifted)
- Daylight (reference)
- LED (variable spectrum)

### Camera Variations
- Different white balance settings
- Different sensor types
- Different color profiles

### Expected Results
- Consistent improvement across lighting conditions
- No regression in ideal conditions
- Graceful degradation with poor calibration

## Validation
Run comparative tests:
```
Fixed palette vs Adaptive palette
Expected: +8-15% improvement in variable lighting
```
