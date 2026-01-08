# CRITICAL FINDING: Missing `generateJABCode` Function

**Date:** 2026-01-07 22:28  
**Status:** 🔴 BLOCKER for encoder integration

## Problem

The JABCode native library (`libjabcode.so`) is **missing the `generateJABCode()` function** that is declared in the header file but not implemented in the compiled library.

## Evidence

### Header Declaration ✅
```c
// From jabcode.h
extern jab_int32 generateJABCode(jab_encode* enc, jab_data* data);
```

### Library Symbols ❌
```bash
$ nm -D ../src/jabcode/build/libjabcode.so | grep generate
# NO RESULTS
```

### Available Encoder Functions
```
createEncode        ✅ Present
destroyEncode       ✅ Present  
generateJABCode     ❌ MISSING
analyzeInputData    ✅ Present
getMetadataLength   ✅ Present
getSymbolCapacity   ✅ Present
getOptimalECC       ✅ Present
```

## Impact

**Our Panama implementation fails at runtime:**
```
java.util.NoSuchElementException: Symbol not found: generateJABCode
    at java.base/java.lang.foreign.SymbolLookup.findOrThrow(SymbolLookup.java:179)
```

This means:
- ✅ Library loading works
- ✅ Symbol lookup works  
- ❌ **The function we need doesn't exist**

## Root Cause Analysis

### Possible Reasons

1. **Incomplete Library Build**
   - The library was only partially compiled
   - Encoder implementation is incomplete
   - Missing source files in build

2. **JABCode Architecture**
   - Maybe encoding is done differently than expected
   - The `generateJABCode` function might not exist at all
   - Encoding might use a different workflow

3. **Version/Fork Mismatch**
   - Header file doesn't match library version
   - Different JABCode fork/branch
   - Outdated build artifacts

## Investigation

Checked source files:
```bash
$ ls ../src/jabcode/*.c
binarizer.c   encoder.c     ldpc.c         sample.c
decoder.c     image.c       mask.c         transform.c
detector.c    interleave.c  pseudo_random.c
```

**`encoder.c` contains:**
- `createEncode()` ✅
- `destroyEncode()` ✅
- `genColorPalette()` ✅
- `setDefaultPalette()` ✅
- Various helper functions ✅
- **NO `generateJABCode()`** ❌

## Solutions

### Option 1: Implement Missing Function (Recommended)

The `generateJABCode()` function needs to be implemented. Based on the available functions, it should:

1. Call `analyzeInputData()` to analyze input
2. Call `getSymbolCapacity()` to determine capacity
3. Call `getOptimalECC()` for error correction
4. Encode the data into the symbol structure
5. Apply masking
6. Generate the bitmap

**Pseudo-implementation:**
```c
jab_int32 generateJABCode(jab_encode* enc, jab_data* data) {
    // 1. Analyze input data
    jab_int32 encoded_length;
    jab_int32* mode_sequence = analyzeInputData(data, &encoded_length);
    
    // 2. Get capacity
    jab_int32 capacity = getSymbolCapacity(enc, 0);
    
    // 3. Get optimal ECC
    jab_int32 wcwr[2];
    getOptimalECC(capacity, encoded_length, wcwr);
    
    // 4. Encode data (TODO: implement encoding logic)
    
    // 5. Apply mask
    maskCode(enc);
    
    // 6. Generate bitmap
    // (TODO: implement bitmap generation)
    
    return JAB_SUCCESS;
}
```

### Option 2: Use Alternative Encoding Workflow

If JABCode doesn't use `generateJABCode()`, find the actual encoding pipeline:
- Check if there's a different entry point
- Look for example code in the repository
- Check documentation or tests

### Option 3: Focus on Decoder Only

Since decoding functions ARE present:
- Implement decoder first (Phase 9)
- Defer encoder until library is complete
- Use external tools for encoding in tests

## Immediate Actions

1. **Check JABCode Repository**
   - Look for official implementation of `generateJABCode`
   - Check if there are examples or tests
   - Verify we have the complete source

2. **Search for Alternative Entry Points**
   - Maybe encoding uses different function names
   - Check wrapper files (saw `jabcode_wrapper.h`)
   - Look for Python/Java bindings in repo

3. **Contact/Research**
   - Check JABCode documentation
   - Look for existing Java wrappers
   - Check issue trackers for similar problems

## Temporary Workaround

For now, we can:
1. **Skip encoder integration tests** - Mark as TODO
2. **Proceed with Phase 9 (Decoder)** - Decoding works!
3. **Come back to encoding** once we understand the proper workflow

## Updated Phase 8 Status

| Component | Status | Blocker |
|-----------|--------|---------|
| Encoder API | ✅ Implemented | - |
| PNG Output | ❌ Blocked | Missing `generateJABCode` |
| Color Modes | ⏸️ Untested | Missing `generateJABCode` |
| Integration Tests | ❌ Failing | Missing `generateJABCode` |

**Recommendation:** Proceed to Phase 9 (Decoder) while investigating the encoder issue.

---

**Status:** Critical blocker identified  
**Next:** Investigate JABCode repository for proper encoding workflow
