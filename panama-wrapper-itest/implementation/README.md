# JABCode Color Mode Implementation

This directory contains implementation artifacts for adding full color mode support (Nc 0-7) to the JABCode Panama wrapper.

## Directory Structure

```
implementation/
├── phase1-native-decoder/    # C code patches for native decoder (modes 3-5)
├── phase2-interpolation/     # Java interpolation integration (modes 6-7)
└── patches/                  # Git patches ready to apply
```

## Phase 1: Native Decoder Extension (Modes 3-5)

**Status:** In Progress  
**Target:** Add 16, 32, 64 color support to native decoder  
**Files:** `phase1-native-decoder/`

### What's Included
- Extended `getPaletteThreshold()` function
- Threshold calculations for 16, 32, 64 colors
- Documentation and comments

### How to Apply
1. Review the extended function in `phase1-native-decoder/decoder_extension_phase1.c`
2. Apply changes to `src/jabcode/decoder.c` (lines 561-589)
3. Rebuild native library:
   ```bash
   cd src/jabcode/build
   cmake .. && make
   sudo make install
   ```
4. Re-run tests to validate:
   ```bash
   cd panama-wrapper-itest
   mvn test -Dtest=ColorMode3Test,ColorMode4Test,ColorMode5Test
   ```

## Phase 2: Palette Interpolation (Modes 6-7)

**Status:** Pending  
**Target:** Integrate Java-based palette interpolation for 128, 256 colors  
**Files:** `phase2-interpolation/` (to be created)

### What Will Be Included
- Java decoder integration
- Palette extraction logic
- Interpolation invocation
- Color discrimination updates

## Testing Strategy

After each phase:
```bash
# Run specific mode tests
mvn test -Dtest=ColorMode3Test  # 16 colors
mvn test -Dtest=ColorMode4Test  # 32 colors
mvn test -Dtest=ColorMode5Test  # 64 colors
mvn test -Dtest=ColorMode6Test  # 128 colors
mvn test -Dtest=ColorMode7Test  # 256 colors

# Run all color mode tests
mvn test -Dtest=ColorMode*Test

# Generate coverage report
mvn clean test jacoco:report
```

## Success Criteria

### Phase 1 Complete
- ✅ Modes 3-5 tests pass (~25 tests flip to GREEN)
- ✅ Native library rebuilt successfully
- ✅ No regressions in modes 1-2

### Phase 2 Complete
- ✅ Modes 6-7 tests pass (~21 tests flip to GREEN)
- ✅ Interpolation working correctly
- ✅ Overall pass rate: 76.9%

## References

- **Test Results:** `../BASELINE_TEST_RESULTS.md`
- **Implementation Plan:** `../../panama-wrapper/FULL_COLOR_MODE_IMPLEMENTATION_PLAN.md`
- **Decoder Analysis:** `../DECODER_ANALYSIS.md`
- **ISO Spec:** ISO/IEC 23634 Annex F

---

**Last Updated:** 2026-01-08 21:07 EST
