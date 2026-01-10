# Iterative Decoding: Testing Strategy

## Test Scenarios

### Convergence Behavior
- Test typical iteration counts
- Validate convergence criteria
- Check for oscillation

### Improvement Metrics
- Compare single-pass vs iterative
- Measure per-mode improvements
- Expected: +10-15% for modes 3-7

### Performance
- Decode time overhead
- Target: < 2× single-pass time
- Profile iteration bottlenecks

## Validation

Run AllColorModesTest with iterative decoding enabled.
Expected results:
- Mode 3: 80-85% pass rate ✅
- Mode 5: 75-85% pass rate ✅
