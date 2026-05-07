# JABCode Metadata Part II LDPC Failure - Deep Dive Investigation

**Date:** 2026-05-07  
**Issue:** Synthetic JABCode images fail to decode due to Metadata Part II LDPC decoding failure across all color modes (4-128)  
**Status:** UNRESOLVED after multiple fix attempts

---

## Executive Summary

After discovering and fixing three critical encoder bugs in `placeMasterMetadataPartII()`, synthetic JABCode images continue to fail LDPC decoding on Metadata Part II for ALL color modes. This suggests a deeper structural issue in how Part II metadata is encoded, written to the matrix, or decoded.

---

## Timeline of Investigation

### Session Start: 2026-05-07 17:40 UTC-4

**Initial Problem:**
- Synthetic test images (4, 8, 16, 32, 64, 128-color) all fail to decode
- Decoder reports: `[PartII] FAILED: LDPC decoding failed`
- Part I metadata decodes successfully for 4-32 color modes
- Camera-scanned 4-color and 8-color codes decode successfully

### Fix Attempt #1: Remove Conditional Part II Update (17:44)

**Bug Found:** `encoder.c:2319-2324`
```c
// WRONG: Skipped Part II update if mask_reference == DEFAULT (7)
if(mask_reference != DEFAULT_MASKING_REFERENCE)
{
    updateMasterMetadataPartII(enc, mask_reference);
    placeMasterMetadataPartII(enc);
}
```

**Fix Applied:**
```c
// Always update Part II metadata regardless of mask_reference value
updateMasterMetadataPartII(enc, mask_reference);
placeMasterMetadataPartII(enc);
```

**Result:** ❌ No change - Part II LDPC still fails

---

### Fix Attempt #2: Off-by-One Error (17:52)

**Bug Found:** `encoder.c:1050-1051`
```c
// WRONG: Reads 39 bits instead of 38
while(metadata_index <= partII_bit_end)
```

**Analysis:**
- `partII_bit_start = 6` (after Part I)
- `partII_bit_end = 6 + 38 = 44`
- Loop with `<=` reads bits 6-44 = 39 bits
- Should read bits 6-43 = 38 bits

**Fix Applied:**
```c
// FIXED: Reads exactly 38 bits
while(metadata_index < partII_bit_end)
```

**Result:** ❌ No change - Part II LDPC still fails

---

### Fix Attempt #3: Uninitialized Padding Bits (17:58)

**Bug Found:** `encoder.c:1053-1054`
```c
// WRONG: Reads garbage from matrix when Part II ends mid-module
jab_byte color_index = enc->symbols[0].matrix[y*enc->symbols[0].side_size.x + x];
```

**Analysis:**
- Part II is 38 bits
- For 16-color (4 bits/module): 38 bits = 9.5 modules
- For 32-color (5 bits/module): 38 bits = 7.6 modules
- Last module gets partial bits from Part II + garbage padding bits

**Fix Applied:**
```c
// FIXED: Initialize to 0 for clean padding
jab_byte color_index = 0;
```

**Result:** ❌ No change - Part II LDPC still fails

---

## Current Decoder Observations

### Test Results (17:59:47)

**4-color:** ❌ Part II LDPC failed  
**8-color:** ❌ Part II LDPC failed  
**16-color:** ❌ Part II LDPC failed  
**32-color:** ❌ Part II LDPC failed  
**64-color:** ❌ Part I invalid → falls back to 8-color → LDPC failed  
**128-color:** ❌ Part I invalid → falls back to 8-color → LDPC failed

### Part II Decoding Log Pattern (32-color example)

```
[PartII] Reading 8 modules for Part II metadata (bits_per_module=5, total_bits=40 including padding)
[PartII] Module (8,8) decoded as 31 (0x1f)
[PartII] Module (12,8) decoded as 3 (0x03)
[PartII] Module (12,12) decoded as 2 (0x02)
[PartII] Module (8,12) decoded as 2 (0x02)
[PartII] Module (7,8) decoded as 31 (0x1f)
[PartII] Module (13,8) decoded as 2 (0x02)
[PartII] Module (13,12) decoded as 2 (0x02)
[PartII] Module (7,12) decoded as 2 (0x02)
[PartII] Running LDPC decode on 40 bits (including 2 padding bits) with wc=2...
[PartII] FAILED: LDPC decoding failed
```

**Key Observations:**
1. Decoder reads correct number of modules (8 for 32-color)
2. Module decoding appears correct (values look reasonable)
3. LDPC parameters match encoder (wc=2)
4. LDPC fails consistently across ALL color modes

---

## Encoder Verification

### Part II Encoding Confirmed Working

```bash
# Encoder output during image generation:
[ENCODER] updateMasterMetadataPartII called: mask_ref=6, color_number=4
[ENCODER] placeMasterMetadataPartII called: color_number=4

[ENCODER] updateMasterMetadataPartII called: mask_ref=0, color_number=8
[ENCODER] placeMasterMetadataPartII called: color_number=8

[ENCODER] updateMasterMetadataPartII called: mask_ref=0, color_number=16
[ENCODER] placeMasterMetadataPartII called: color_number=16
```

**Verification:**
- ✅ Both functions called for all color modes
- ✅ `mask_reference` values are valid (0-7)
- ✅ No crashes or errors during encoding

---

## Technical Specifications

### LDPC Parameters (Part II)

**Encoder:** `encoder.c:1011`
```c
jab_int32 wcwr[2] = {2, -1};
jab_data* encoded_partII = encodeLDPC(partII, wcwr);
```

**Decoder:** `decoder.c:914`
```c
if( !decodeLDPChd(part2, total_bits, 2, 0) )
```

- Both use `wc=2` (weight column = 2)
- Net data: 19 bits (V=10, E=6, MSK=3)
- Encoded: 38 bits
- With padding: 40 bits for most color modes

### Part II Structure

```
Bit 0-9:   V (version) - 10 bits
Bit 10-12: E1 (ECC level word) - 3 bits
Bit 13-15: E2 (ECC level row) - 3 bits
Bit 16-18: MSK (mask reference) - 3 bits
Total: 19 bits raw → 38 bits after LDPC encoding
```

### Module Calculation

- 4-color (2 bits/mod): 38 bits = 19 modules (exact)
- 8-color (3 bits/mod): 38 bits = 12.67 modules → 13 modules (40 bits)
- 16-color (4 bits/mod): 38 bits = 9.5 modules → 10 modules (40 bits)
- 32-color (5 bits/mod): 38 bits = 7.6 modules → 8 modules (40 bits)
- 64-color (6 bits/mod): 38 bits = 6.33 modules → 7 modules (42 bits)
- 128-color (7 bits/mod): 38 bits = 5.43 modules → 6 modules (42 bits)

---

## Hypotheses for Further Investigation

### Hypothesis 1: Module Placement Order Mismatch
- Encoder writes Part II modules in one order
- Decoder reads Part II modules in different order
- **Test:** Add logging to verify module coordinates match between encoder/decoder

### Hypothesis 2: Bit Packing/Unpacking Mismatch
- Encoder packs bits MSB-first but decoder expects LSB-first (or vice versa)
- **Test:** Log raw bit sequences from encoder and compare with decoder input

### Hypothesis 3: LDPC Padding Not Symmetric
- Encoder adds padding zeros when Part II doesn't fill last module
- Decoder expects different padding strategy
- **Test:** Verify decoder handles partial modules correctly

### Hypothesis 4: Metadata Buffer Corruption
- `updateMasterMetadataPartII()` overwrites Part II in metadata buffer correctly
- But `placeMasterMetadataPartII()` reads from wrong offset or corrupted buffer
- **Test:** Dump metadata buffer after encoding and before placement

### Hypothesis 5: Color Index Bit Order
- When placing Part II, bits are assigned to color_index in wrong order
- MSB/LSB confusion in the bit manipulation logic
- **Test:** Verify bit order in color index construction (line 1063-1065)

### Hypothesis 6: LDPC Implementation Bug
- The LDPC decoder itself has a bug that manifests only for Part II
- Part I uses same LDPC but decodes successfully
- **Test:** Compare Part I and Part II LDPC decode paths for differences

---

## Files Modified

1. **`/src/jabcode/encoder.c`**
   - Line 2319-2324: Removed conditional Part II update
   - Line 1051: Fixed off-by-one error (`<` not `<=`)
   - Line 1054: Initialize color_index to 0 (not from matrix)

2. **Synthetic Images Regenerated:** `output/synthetic-tests/*.png` (17:52, 17:58)

3. **Android Native Library Rebuilt:** Multiple times with fixes

---

## Working vs. Failing Scenarios

### ✅ Working
- Camera-scanned 4-color JABCode with real-world conditions
- Camera-scanned 8-color JABCode with real-world conditions
- Part I metadata decoding (4-32 color modes)

### ❌ Failing
- ALL synthetic images (perfect pixel-accurate)
- Part II metadata LDPC decoding
- 64/128-color Part I metadata (separate issue)

### Key Insight
**Synthetic perfect images fail but camera-scanned imperfect images work.**

This suggests:
1. Real images might have slight color variations that help LDPC error correction
2. Synthetic images might expose a hidden bug that real-world noise masks
3. Encoder might be writing Part II correctly for real codes but incorrectly for test codes

---

## Next Steps for Investigation

1. **Compare Working vs Failing:**
   - Decode a camera-scanned 4-color code
   - Log Part II raw bits
   - Compare with synthetic 4-color Part II bits
   - Identify the difference

2. **Bit-level Audit:**
   - Add encoder logging to dump Part II bits before LDPC encoding
   - Add encoder logging to dump Part II bits after LDPC encoding
   - Add decoder logging to dump Part II bits before LDPC decoding
   - Compare all three

3. **Reference Implementation Search:**
   - Search for official JABCode reference implementation
   - Check if there's known issues with Part II encoding
   - Look for test vectors or validation tools

4. **LDPC Deep Dive:**
   - Analyze LDPC encoder implementation (`ldpc.c:encodeLDPC`)
   - Analyze LDPC decoder implementation (`ldpc.c:decodeLDPChd`)
   - Verify parity check matrix generation matches

5. **Module Order Verification:**
   - Trace `getNextMetadataModuleInMaster()` during encoding
   - Trace decoder's Part II module read order
   - Verify they match exactly

---

## Research Questions

1. **LDPC Encoding Standards:**
   - What is the correct way to pad LDPC codewords when data doesn't align with module boundaries?
   - Should padding be all zeros or follow a specific pattern?

2. **JABCode Specification:**
   - ISO/IEC 23634 specification for Metadata Part II encoding
   - Official test vectors for Part II validation

3. **Similar Issues:**
   - Has anyone reported Part II LDPC failures in JABCode implementations?
   - Are there known encoder/decoder compatibility issues?

4. **LDPC Implementation:**
   - Common pitfalls in LDPC encoder/decoder implementations
   - Bit order conventions (MSB-first vs LSB-first)

---

## Conclusion

Despite fixing three legitimate encoder bugs, the core issue remains unresolved. The consistent failure across ALL color modes and ALL fix attempts suggests a fundamental misunderstanding or mismatch in how Part II metadata is:

1. Encoded with LDPC
2. Packed into modules
3. Written to the matrix
4. Read from the matrix
5. Unpacked from modules
6. Decoded with LDPC

A systematic bit-level audit and comparison with working codes (camera-scanned) is required to identify the root cause.
