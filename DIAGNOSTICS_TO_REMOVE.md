# Diagnostic Logging to Remove for Production

## Summary
Tests pass perfectly, but diagnostic printf statements remain for debugging. Remove before production build.

## Files with Diagnostics

### `src/jabcode/encoder.c`
**Lines to remove:**
- Line 592-593: `isDefaultMode` check logging
- Lines 2284-2292: Symbol 0 DATA before interleave
- Lines 2297-2304: Symbol 0 DATA after interleave
- Lines 2322-2342: Module counting and pre-masking data
- Line 2346: Default mode mask_type
- Line 2361: Selected mask_type
- Lines 2366-2377: Masked DATA values
- Lines 2387-2391: Masked modules after PartII
- Lines 2394-2402: RGB values for first 6 modules

**Total:** ~10 printf blocks

### `src/jabcode/decoder.c`
**Lines to remove:**
- Lines 1291-1296: LDPC raw_data parameters
- Lines 1304-1308: After deinterleave bits

**Total:** 2 printf blocks

### `swift-java-wrapper/src/c/mobile_bridge.c`
**Keep or remove (optional):**
- Line 170: Encoder wcwr export (useful for debugging)

## Cleanup Method

**Option 1: Manual removal** (safest)
```bash
# Edit files above, remove printf blocks
```

**Option 2: Conditional compilation** (recommended for dev/prod)
```c
#ifdef DEBUG_JABCODE
    printf("[ENCODER] ...");
#endif
```

**Option 3: Batch removal**
```bash
# Use sed to remove [ENCODER] and [LDPC] printf lines
sed -i '/printf.*\[ENCODER\]/,/printf.*\\n"/d' encoder.c
sed -i '/printf.*\[LDPC\]/,/printf.*\\n"/d' decoder.c
```

## Status
- ✅ Core functionality works perfectly
- ✅ All tests pass
- ⚠️ Diagnostic logging remains (non-critical)
- 📝 Remove before production deployment
