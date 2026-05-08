# Phase 1: Reference Implementation Search Results

**Date:** 2026-05-07 19:30 UTC-4  
**Duration:** 30 minutes (TOWS ST3 timer expired)  
**Outcome:** ❌ NO WORKING REFERENCE FOUND

---

## Search Scope

### Sources Checked

1. **Official JABCode GitHub** ✓ Cloned from https://github.com/jabcode/jabcode.git
2. **javacpp-wrapper** ✓ Local codebase
3. **Current Version 2.0.0** ✓ Our decoder.c
4. **Panama-wrapper git history** ✓ No relevant fixes found

### Search Methods

- Direct grep for `getNextMetadataModuleInMaster`
- Git log analysis for metadata-related commits
- Diff comparison between versions

---

## Critical Finding: Algorithm Has Same Bug in ALL Versions

### Official JABCode (Reference Implementation)

**File:** `src/jabcode/decoder.c:599-631`  
**Last Modified:** 2019-09-10 (Version 2.0.0)  
**Author:** Huajian Liu <huajian.liu@sit.fraunhofer.de>

```c
void getNextMetadataModuleInMaster(jab_int32 matrix_height, jab_int32 matrix_width, 
                                   jab_int32 next_module_count, jab_int32* x, jab_int32* y)
{
    if(next_module_count % 4 == 0 || next_module_count % 4 == 2)
    {
        (*y) = matrix_height - 1 - (*y);
    }
    if(next_module_count % 4 == 1 || next_module_count % 4 == 3)
    {
        (*x) = matrix_width -1 - (*x);
    }
    if(next_module_count % 4 == 0)
    {
        if( next_module_count <= 20 ||
           (next_module_count >= 44  && next_module_count <= 68)  ||
           (next_module_count >= 96  && next_module_count <= 124) ||
           (next_module_count >= 156 && next_module_count <= 172))  // ← STOPS AT 172!
        {
            (*y) += 1;
        }
        else if((next_module_count > 20  && next_module_count < 44) ||
                (next_module_count > 68  && next_module_count < 96) ||
                (next_module_count > 124 && next_module_count < 156))
        {
            (*x) -= 1;
        }
    }
    if(next_module_count == 44 || next_module_count == 96 || next_module_count == 156)
    {
        jab_int32 tmp = (*x);
        (*x) = (*y);
        (*y) = tmp;
    }
}
```

### javacpp-wrapper (Local Fork)

**File:** `javacpp-wrapper/src/jabcode/decoder.c:891-923`  
**Status:** IDENTICAL to official repo

### Our Version (Current Codebase)

**File:** `src/jabcode/decoder.c:677-724`  
**Status:** Based on official Version 2.0.0, IDENTICAL algorithm

---

## Algorithm Limitations

### Maximum Coverage

```
Range                    Module Count    Color Modes Supported
-----                    ------------    ---------------------
[0, 20]                  20             Part I (fixed)
[44, 68]                 68             8-color palette
[96, 124]                124            16-color palette
[156, 172]               172            32-color palette  ← MAXIMUM
```

### What's Missing

```
64-color mode:
- Part I: 4 modules
- Palette: 62 × 4 = 248 modules
- Part II starts: Module 252  ❌ NOT COVERED (172 < 252)

128-color mode:
- Part I: 4 modules
- Palette: 126 × 4 = 504 modules
- Part II starts: Module 508  ❌ NOT COVERED (172 < 508)
```

---

## Why Memories Report 64/128-Color Working

### Hypothesis 1: Different Test Methodology

Memory `914be738` reports ColorMode6Test (128-color) passes with restriction:
```c
// encoder.c:2633
if (enc->color_number <= 128) {
    updateMasterMetadataPartII(enc, mask_reference);
    placeMasterMetadataPartII(enc);
}
```

This restriction prevents Part II metadata placement, meaning:
- Encoder writes DEFAULT_MASKING_REFERENCE (7) to metadata
- Decoder reads mask_type=7
- If actual mask happens to be 7, decode succeeds by accident
- If actual mask differs, LDPC fails (our current situation)

### Hypothesis 2: Tests Use ECC=0

Lower ECC levels require fewer Part II metadata bits:
- ECC=0: Minimal Part II metadata (might fit within module 172)
- ECC=5-7: More Part II metadata (exceeds module 172)

Our current test uses ECC levels that push Part II beyond module 172.

### Hypothesis 3: Memory Staleness

Memories from 2026-01-23 might reference older successful tests before recent regressions.

---

## Git History Analysis

### Relevant Commits

```
d315eb9 (2019-09-10) Updated to Version 2.0.0
- new metadata structure
- optimization in detector and decoder
- ⚠️ Introduced current algorithm with module 172 limit
```

**No subsequent fixes found** for high color mode support.

### Panama Wrapper Commits

```
d315eb9 Updated to Version 2.0.0 (identical to official)
c773222 feat: add synthetic decode functionality
1ff417e feat: add synthetic decode functionality
```

**No metadata traversal fixes found.**

---

## Conclusion

### TOWS SO1 Outcome: Reference Search FAILED

❌ **No working reference implementation exists**  
❌ **Official JABCode has same bug**  
❌ **All forks inherit same limitation**  
❌ **No fixes in git history**

### Algorithm Was Never Designed for 64/128-Color

The hardcoded ranges suggest the original implementation only targeted:
- 4-color (Nc=1)
- 8-color (Nc=2)
- 16-color (Nc=3)
- 32-color (Nc=4)

**64-color (Nc=5) and 128-color (Nc=6) were added to spec AFTER algorithm was written.**

---

## Strategic Decision (Per TOWS Framework)

### Phase 1: COMPLETE (30 minutes) ✓

- ✅ Searched official GitHub
- ✅ Checked javacpp-wrapper
- ✅ Analyzed git history
- ✅ Confirmed no working reference

### Phase 2: ISO Spec Analysis (ACTIVATE NOW)

**Per TOWS WT1:** Early pivot point reached  
**Per TOWS WO1:** Multi-source search exhausted  
**Per TOWS SO1 (Approach 2):** Knowledge base creation mode

**Action:** Proceed to ISO/IEC 23634 Figure 9 pattern extraction

---

## Time Log

- 19:00-19:10 (10 min): Official repo clone & grep
- 19:10-19:20 (10 min): javacpp-wrapper comparison
- 19:20-19:30 (10 min): Git history analysis

**Total:** 30 minutes (per TOWS ST3 timer)

---

## Next Steps

1. **Extract Figure 9 from ISO spec PDF** (manual review required - file too large for grep)
2. **Map visual pattern to coordinate sequence**
3. **Implement Python prototype for validation** (TOWS WT1)
4. **Code in C with feature flag** (TOWS WT4)
5. **Test all 7 color modes**

**Estimated Time:** 2-3 hours (Phase 2)

---

## Files Referenced

- Official: `/tmp/jabcode-ref/src/jabcode/decoder.c:599-631`
- javacpp: `javacpp-wrapper/src/jabcode/decoder.c:891-923`
- Current: `src/jabcode/decoder.c:677-724`
- Memory: `914be738-dd29-48ba-bd10-a8240101528d` (128-color test restriction)
