# Phase 1: Native Decoder Extension for Modes 3-5

**Date:** 2026-01-08  
**Target:** Extend `decoder.c::getPaletteThreshold()` to support 16, 32, 64 colors  
**Estimated Effort:** 12-16 hours  
**Expected Result:** ~25 tests flip from RED to GREEN

---

## Overview

The native JABCode decoder currently only supports 4 and 8 color modes. This phase extends it to support:
- **Mode 3:** 16 colors (4×2×2 RGB)
- **Mode 4:** 32 colors (4×4×2 RGB)
- **Mode 5:** 64 colors (4×4×4 RGB)

## Technical Details

### Palette Structure

All three modes use the same base color values: **{0, 85, 170, 255}**

| Mode | Colors | R levels | G levels | B levels | Thresholds Needed |
|------|--------|----------|----------|----------|-------------------|
| 3    | 16     | 4        | 2        | 2        | 5 total (3+1+1)   |
| 4    | 32     | 4        | 4        | 2        | 7 total (3+3+1)   |
| 5    | 64     | 4        | 4        | 4        | 9 total (3+3+3)   |

### Threshold Calculations

Since the palette values are fixed and known, we use **constant thresholds**:

**4-level channels:** {0, 85, 170, 255}
- Threshold 0: 42.5 (between 0 and 85)
- Threshold 1: 127.5 (between 85 and 170)
- Threshold 2: 212.5 (between 170 and 255)

**2-level channels:** {0, 255}
- Threshold: 127.5 (between 0 and 255)

## Implementation

### File to Modify
```
src/jabcode/decoder.c
Lines: 561-589
Function: getPaletteThreshold()
```

### Changes Required

Add three new `else if` blocks after the existing `color_number == 8` block:

1. **16 colors:** Set 5 thresholds (3 for R, 1 for G, 1 for B)
2. **32 colors:** Set 7 thresholds (3 for R, 3 for G, 1 for B)
3. **64 colors:** Set 9 thresholds (3 for R, 3 for G, 3 for B)

See `decoder_extension_phase1.c` for the complete implementation.

## Build Instructions

```bash
# Navigate to jabcode source
cd /mnt/b34628fa-d41e-4c37-8caf-f06a6ecbb1ae/projects/practice/barcode/jabcode/src/jabcode

# Apply the changes to decoder.c
# (Copy the new code blocks from decoder_extension_phase1.c)

# Rebuild
cd build
cmake .. && make

# Install
sudo make install

# Verify installation
ldconfig -p | grep jabcode
```

## Testing

### Expected Results BEFORE Implementation
```
ColorMode3Test: 10/14 failures (71% fail rate)
ColorMode4Test: 7/10 failures (70% fail rate)
ColorMode5Test: 8/11 failures (73% fail rate)
```

### Expected Results AFTER Implementation
```
ColorMode3Test: 3/14 failures (21% fail rate) ✅
ColorMode4Test: 2/10 failures (20% fail rate) ✅
ColorMode5Test: 2/11 failures (18% fail rate) ✅
```

### Test Commands
```bash
cd panama-wrapper-itest

# Test individual modes
mvn test -Dtest=ColorMode3Test
mvn test -Dtest=ColorMode4Test
mvn test -Dtest=ColorMode5Test

# Test all Phase 1 modes together
mvn test -Dtest=ColorMode3Test,ColorMode4Test,ColorMode5Test

# Verify no regressions in modes 1-2
mvn test -Dtest=JABCodeDecoderIntegrationTest
```

## Validation Checklist

- [ ] Code compiles without errors
- [ ] Native library installs successfully
- [ ] Mode 3 (16 colors) tests mostly pass
- [ ] Mode 4 (32 colors) tests mostly pass
- [ ] Mode 5 (64 colors) tests mostly pass
- [ ] Modes 1-2 still work (no regressions)
- [ ] Round-trip encoding/decoding works
- [ ] Unicode and special characters work
- [ ] Various ECC levels work
- [ ] Various module sizes work

## Known Limitations

Some tests may still fail due to:
- Empty string handling (encoder limitation)
- Extremely large payloads (memory constraints)
- Edge cases in color discrimination

These are expected and acceptable for Phase 1.

## Next Steps After Phase 1

1. Document actual pass rates
2. Update `BASELINE_TEST_RESULTS.md`
3. Move to Phase 2 (modes 6-7 interpolation)
4. Run `/test-coverage-update` workflow

---

**Files in This Directory:**
- `decoder_extension_phase1.c` - Complete extended function
- `README.md` - This file
- `APPLY_CHANGES.md` - Step-by-step application guide (to be created)
