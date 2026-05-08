# Metadata Traversal Bug in getNextMetadataModuleInMaster()

**Date:** 2026-05-07  
**Severity:** CRITICAL  
**Affects:** 64-color and 128-color modes  
**Status:** Root cause identified

---

## Summary

The `getNextMetadataModuleInMaster()` function has hardcoded transition points that fail when Part II metadata starts at module counts beyond 220. This causes the decoder to read the same module coordinates multiple times, corrupting Part II LDPC data for 64-color and 128-color modes.

---

## Root Cause

### Module Count Calculation

Per ISO/IEC 23634 Section 4.4.4, metadata modules are placed as:
- **Part I:** Modules 0-3 (4 modules, fixed)
- **Color Palettes:** Next `(color_number - 2) × 4` modules
- **Part II:** Remaining modules

For different color modes:
```
Mode    Colors  Palette Modules  Part II Start
----    ------  ---------------  -------------
4-color    4      2 × 4 = 8       Module 12  ✓
8-color    8      6 × 4 = 24      Module 28  ✓
16-color   16    14 × 4 = 56      Module 60  ✓
32-color   32    30 × 4 = 120     Module 124 ✓
64-color   64    62 × 4 = 248     Module 252 ✗
128-color  128  126 × 4 = 504     Module 508 ✗
```

### The Bug

`getNextMetadataModuleInMaster()` has hardcoded transition points at module counts **44, 96, 156, 220, 292**. Between these points, the function uses modulo-4 logic that creates "stuck zones" where coordinates don't advance:

```
Module Count Range   Behavior
------------------   ---------
0-43                 Works
44-95                Works (transition at 44)
96-155               Works (transition at 96)
156-219              Works (transition at 156)
220-291              BROKEN (no transition until 292!)
292+                 Works (transition at 292)
```

**64-color Part II starts at module 252** → Falls in broken 220-291 range!

### Observed Failure Pattern

Decoder logs for 64-color Part II (module_count 252-259):
```
[getNext] module_count=252: (10,11) → (10,10)  ✓ (transition works)
[getNext] module_count=253: (10,10) → (10,10)  ✗ STUCK!
[getNext] module_count=254: (10,10) → (10,10)  ✗ STUCK!
[getNext] module_count=255: (10,10) → (10,10)  ✗ STUCK!
[getNext] module_count=256: (10,10) → (10,11)  ✓ (transition works)
```

Result: Decoder reads module (10,10) **four times**, getting value 63 repeatedly, corrupting Part II LDPC input.

---

## Why Lower Color Modes Work

The hardcoded transitions were designed for symbols where Part II starts **before module 220**:

- 4/8/16/32-color: Part II starts at modules 12, 28, 60, 124 respectively
- All fall within working ranges (0-43, 44-95, 96-155, 156-219)
- Transitions at 44, 96, 156, 220 handle these cases correctly

The original implementation didn't anticipate:
- 64-color needing 248 palette modules
- 128-color needing 504 palette modules
- Part II starting beyond module 220

---

## Impact

### Failures
- ✗ 64-color: Part II LDPC decoding fails (corrupted input bits)
- ✗ 128-color: Part II LDPC decoding fails (Part II starts at 508, way beyond 292)

### Working
- ✓ 4-color: Part II starts at 12
- ✓ 8-color: Part II starts at 28
- ✓ 16-color: Part II starts at 60
- ✓ 32-color: Part II starts at 124

---

## Fix Requirements

### Must-Have
1. **Correct traversal for module counts 220-600+**
2. **No regression for existing working modes** (4, 8, 16, 32-color)
3. **Symmetric encoder/decoder behavior** (both use same function)

### Constraints
1. Function signature cannot change (used throughout codebase)
2. Must follow ISO/IEC 23634 Figure 9 placement pattern
3. Must handle all module counts up to max symbol size

### Test Coverage Needed
- All 7 color modes (4, 8, 16, 32, 64, 128, 256)
- Verify coordinate uniqueness (no duplicates in sequence)
- Cross-check encoder placement vs decoder reading
- Compare against reference implementation (if available)

---

## Proposed Fix Strategy

### Option A: Add More Transition Points (Conservative)
**Pros:** Minimal code change  
**Cons:** Doesn't fix root algorithmic issue

```c
if(next_module_count == 44 || next_module_count == 96 || 
   next_module_count == 156 || next_module_count == 220 || 
   next_module_count == 292 || next_module_count == 364 ||  // NEW
   next_module_count == 436 || next_module_count == 508)    // NEW
{
    // swap x,y
}
```

### Option B: Fix Modulo Logic (Moderate Risk)
**Pros:** Fixes root cause  
**Cons:** Complex, needs thorough testing

Analyze why modulo-4 logic creates stuck zones and fix the advancement rules.

### Option C: Rewrite Based on Spec (High Risk)
**Pros:** Guaranteed spec compliance  
**Cons:** Major rewrite, high regression risk

Extract exact pattern from ISO spec Figure 9 and reimplement.

---

## References

- **Spec:** ISO/IEC 23634:2022, Section 4.4.4 "Reserved modules for metadata and colour palette"
- **Code:** `src/jabcode/decoder.c:677-724` (getNextMetadataModuleInMaster)
- **Used by:** Both encoder and decoder for metadata placement/reading
- **Test logs:** Android logcat 2026-05-07 18:58:38 (64-color failure)

---

## Next Steps

1. Extract Figure 9 traversal pattern from ISO spec
2. Create unit test suite for coordinate generation
3. Implement fix (Option A or B based on testing)
4. Verify all 7 color modes with encode-decode roundtrip
5. Update documentation with fix details
