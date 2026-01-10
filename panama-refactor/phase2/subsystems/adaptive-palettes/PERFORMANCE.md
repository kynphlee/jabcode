# Adaptive Palettes: Performance

## Performance Targets
- Calibration overhead: < 100ms one-time cost
- Palette adaptation: < 10ms
- No impact on per-module decode time

## Optimization
- Pre-compute adapted palette during initialization
- Reuse across entire barcode decode
- Minimal memory overhead (< 2KB)
