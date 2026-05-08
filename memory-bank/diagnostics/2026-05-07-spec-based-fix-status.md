# Metadata Traversal Fix - ISO Spec Analysis Complete

**Date:** 2026-05-07  
**Status:** ⚠️ PARTIAL SUCCESS (70% working)

## Summary

Successfully analyzed ISO/IEC 23634 Figure 9 to understand metadata placement pattern. Implemented fix extends coordinate traversal from module 172 to module 524, covering 64-color and 128-color modes.

## Findings from ISO Spec

### Pattern Discovery (Figure 9)

1. **Modulo-4 Rotation**: Metadata spirals through 4 corners in clockwise pattern
   - mod 0 → Upper Left (NW)  
   - mod 1 → Upper Right (NE)
   - mod 2 → Lower Right (SE)
   - mod 3 → Lower Left (SW)

2. **Range Progression**: 
   - Y-increment ranges: 21, 25, 29, 33, 37, 41, 45, 49 (+4 per cycle)
   - X-decrement ranges: 23, 27, 31, 35, 39, 43, 47, 51 (+4 per cycle)
   - Swap points: 44, 96, 156, 224, 300, 384, 476

3. **Formula**: `side_size = version × 4 + 17`
   - Version 1: 21×21 = 441 modules (supports up to 32-color)
   - Version 2: 25×25 = 625 modules (required for 64/128-color)

4. **Starting Position**: (6, 1) NOT center! (from `MASTER_METADATA_X/Y` in decoder.h)

## Implementation

### Extended Ranges

```c
// Y-increment (mod4 == 0):
[0, 20], [44, 68], [96, 124], [156, 188],  
[224, 260], [300, 340], [384, 428], [476, 524]

// X-decrement (mod4 == 0):
[21, 43], [69, 95], [125, 155], [189, 223],
[261, 299], [341, 383], [429, 475]

// Swap points:
44, 96, 156, 224, 300, 384, 476
```

### Fixed-Point Escape Logic

For modules > 156, advance on **ALL modulo cases** (not just mod4==0) to escape fixed-point coordinates created by coordinate flipping.

## Test Results

### ✅ Working (Modules 0-155)
- Correct starting position: (6, 1)
- All coordinates unique for modules 0-155
- Matches original JABCode behavior for low color modes (4, 8, 16, 32)

### ❌ Failing (Modules 156+)
- Module 156 starts duplicate cycle at (9, 1)
- Modules 157-196 stuck in repeating pattern
- Indicates advancement logic for mod1/2/3 creates excessive movement

## Root Cause of Remaining Issue

The coordinate flipping formula creates **fixed points**:
```c
x_flipped = width - 1 - x
// When x = width/2, x_flipped = x (STUCK!)
```

For 25×25 matrix, certain positions (like x=12, y=12 or x=9, y=1) can become fixed points that repeat every 4 iterations if advancement isn't sufficient.

## Next Steps

1. **Option A**: Analyze modules 156-160 coordinate sequence to understand cycle
2. **Option B**: Adjust advancement ranges for >156 to break the cycle
3. **Option C**: Consult official JABCode test vectors for 64/128-color modes

## Files Modified

- `@/src/jabcode/decoder.c:677-756` - Extended getNextMetadataModuleInMaster()
- `@/test/test_metadata_correct_size.c` - Validation test with correct matrix sizes

## Key Lessons

1. **Always check spec first**: Figure 9 provided exact pattern
2. **Matrix size varies by version**: Not all modes use 21×21
3. **Starting position matters**: (6,1) not center
4. **Fixed points are real**: Coordinate flipping creates mathematical constraints

## Progress: 70%

- ✅ Spec analysis complete
- ✅ Pattern formula derived  
- ✅ Ranges extended to module 524
- ✅ Modules 0-155 working
- ⚠️ Modules 156+ need refinement
