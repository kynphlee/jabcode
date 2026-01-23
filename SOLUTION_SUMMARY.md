# JABCode Encoder-Decoder Solution Summary

**Date:** 2026-01-22  
**Branch:** swift-java-poc  
**Status:** ✅ COMPLETE - Full roundtrip working  
**Time Investment:** 16+ hours

---

## Problem Statement

JABCode mobile decoder failed to decode encoder-generated bitmaps due to:
1. Incorrect module position detection (data_map mismatch)
2. LDPC parameter mismatch (encoder's dynamic wcwr vs decoder's fixed lookup)

---

## Root Causes & Solutions

### Issue 1: Data Extraction Pipeline (15 hours)

**Problem:** Decoder couldn't determine which modules contained metadata vs data.

**Solution:** Export encoder's `data_map` to decoder
- Added `data_map` field to `jab_mobile_encode_result`
- Decoder inverts convention (encoder 0=pattern, decoder 1=pattern)
- Perfect bit-for-bit data match achieved

**Files Modified:**
- `swift-java-wrapper/include/mobile_bridge.h:43`
- `swift-java-wrapper/src/c/mobile_bridge.c:172-177`
- `src/jabcode/detector_synthetic.c:283-287`

### Issue 2: LDPC Parameter Mismatch (1 hour)

**Problem:** Encoder's `getOptimalECC()` dynamically changes wcwr parameters
- Example: ECC level 3 typically uses [wc=4, wr=9]
- But encoder optimizes to [wc=8, wr=9] for certain payloads
- Decoder used fixed `ecclevel2wcwr[3]` → [4, 9] ❌
- Result: LDPC syndrome check fails immediately

**Solution:** Export encoder's actual wcwr values
- Added `wcwr[2]` field to `jab_mobile_encode_result`
- Decoder uses encoder's actual values, not lookup table

**Files Modified:**
- `swift-java-wrapper/include/mobile_bridge.h:44`
- `swift-java-wrapper/src/c/mobile_bridge.c:168-170`
- `src/jabcode/include/jabcode.h:176`
- `src/jabcode/detector_synthetic.c:122,158-168`

### Issue 3: Multi-Symbol Encoding (30 minutes)

**Problem:** Encoder validation required `symbol_versions` to be set (1-32 range).

**Solution:** Initialize with reasonable defaults
- Single-symbol: Auto-calculated by encoder
- Multi-symbol: Default to version 10 (medium size)

**Files Modified:**
- `swift-java-wrapper/src/c/mobile_bridge.c:107-115`

---

## Verification Results

### ✅ All Tests Pass

**Basic Roundtrip:**
```
Round-trip: 'A' -> encoded -> decoded -> 'A' ✓
```

**Multi-Symbol:**
```
Multi-symbol: 95 chars -> 684x1368 ✓
```

**Comprehensive Testing:**
- ✅ Short messages (1-4 bytes): 4/4 pass
- ✅ ECC levels (0, 3, 5, 7, 10): 5/5 pass
- ✅ Medium message (85 bytes): 1/1 pass
- ✅ 8-color mode: 1/1 pass

**Total:** 11/11 tests pass (100%)

---

## Architecture

### Data Flow

```
Mobile App
    ↓
jabMobileEncode()
    ↓
generateJABCode() [encoder.c]
    ├→ getOptimalECC() [dynamic wcwr optimization]
    ├→ encodeLDPC() [with optimized wcwr]
    └→ createBitmap()
    ↓
Export: bitmap + data_map + wcwr
    ↓
jabMobileDecode()
    ↓
decodeJABCodeSynthetic() [detector_synthetic.c]
    ├→ Use encoder's data_map (exact module positions)
    ├→ Use encoder's wcwr (exact LDPC parameters)
    └→ decodeLDPChd() [succeeds with matching parameters]
    ↓
Decoded data ✓
```

### Key Insight

**Synthetic decoder must use encoder's exact metadata:**
1. **data_map:** Module positions (metadata, palette, data)
2. **wcwr:** LDPC parameters (may differ from ecclevel2wcwr table)

This is fundamentally different from camera decoder which:
- Detects patterns visually
- Reads metadata from bitmap
- Derives wcwr from encoded metadata

---

## Production Checklist

### Before Deployment

- [ ] Remove diagnostic logging (see `DIAGNOSTICS_TO_REMOVE.md`)
- [ ] Test on real mobile devices (iOS/Android)
- [ ] Build mobile artifacts (.xcframework, .so)
- [ ] Create platform-specific bindings
- [ ] Performance benchmarks

### Optional Improvements

- [ ] Conditional compilation (`#ifdef DEBUG_JABCODE`)
- [ ] Memory leak testing (Valgrind/ASan)
- [ ] Fuzzing for edge cases
- [ ] Cross-branch comparison with panama-poc

---

## API Usage

### Encoding
```c
jab_mobile_encode_params params = {
    .color_number = 4,    // 4 or 8 colors
    .symbol_number = 1,   // Single or multi-symbol
    .ecc_level = 3,       // 0-10
    .module_size = 12     // Pixels per module
};

jab_mobile_encode_result* result = jabMobileEncode(data, length, &params);
// result contains: bitmap, data_map, wcwr, dimensions
```

### Decoding
```c
jab_data* decoded = jabMobileDecode(result, color_number, ecc_level);
// Uses result->data_map and result->wcwr internally
```

---

## Lessons Learned

1. **Don't assume lookup tables:** Encoder may optimize parameters dynamically
2. **Export all metadata:** Synthetic decoding needs exact encoder state
3. **Convention matters:** Encoder/decoder data_map conventions differ
4. **Test thoroughly:** Small messages, large messages, all ECC levels
5. **Document diagnostics:** Separate debug code from production code

---

## Credits

**Investigation:** 16+ hours of debugging, tracing, and testing  
**Key Tools:** GDB, printf debugging, AddressSanitizer  
**Breakthrough:** Realizing encoder uses `getOptimalECC()` to override ecclevel2wcwr  

---

## Next Steps

See TODO list in plan:
1. Clean up diagnostic logging
2. Build mobile artifacts
3. Platform-specific bindings
4. Real device testing
