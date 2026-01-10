# LAB Color Space: Testing Strategy

## Test Coverage Requirements

- Unit tests: >95% for conversion functions
- Integration tests: All color modes 1-7
- Performance benchmarks: < 5% overhead vs RGB
- Accuracy validation: ΔE < 0.5 from reference

## Key Test Scenarios

### Conversion Accuracy
- Reference color validation (known LAB values)
- Round-trip RGB→LAB→RGB (error ≤ 1 unit)
- Edge cases (black, white, primary colors)

### ΔE Calculation
- Identity (same color = 0)
- Symmetry (ΔE(a,b) = ΔE(b,a))
- Known distances validation

### Mode-Specific Tests
- Mode 3: R-channel discrimination improvement
- Mode 5: All-channel improvement
- Modes 6-7: Marginal improvement validation

See test files in `src/jabcode/test_lab_color.c` and integration tests.
