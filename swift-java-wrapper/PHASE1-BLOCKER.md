# Phase 1 Blocker: generateJABCode Not Exported

**Date:** 2026-01-18  
**Status:** 🔴 BLOCKING  
**Priority:** P0 (blocks all mobile encoding)

## Problem

The JABCode desktop library (`libjabcode.a`) does **not** export `generateJABCode()`, which orchestrates the full encoding pipeline. This function exists only in the `jabcodeWriter` application layer.

## Evidence

```bash
# Library does NOT export generateJABCode
$ nm lib/libjabcode.a | grep generate
(no results)

# Application binary DOES contain it
$ nm bin/jabcodeWriter | grep generate
0000000000007530 T generateJABCode
```

## Impact

- ❌ Mobile encoding completely blocked
- ❌ Cannot create RGBA bitmaps from input data
- ✅ Decoding still functional (uses `decodeJABCode()` which IS exported)
- ✅ All validation tests passing (parameter checks, error handling)

## Root Cause

Desktop library architecture separates concerns:
- **libjabcode.a**: Low-level primitives (`createEncode`, `InitSymbols`, `analyzeInputData`, `encodeData`)
- **jabcodeWriter**: High-level orchestration (`generateJABCode` calls primitives in sequence)

This design assumes consumers will write their own orchestration logic, but mobile needs the full pipeline.

## Resolution: Extract to Library (Option A)

### Steps

1. **Extract function** from `jabcodeWriter.c`:
   ```c
   // Copy lines 430-502 to new file
   jab_int32 generateJABCode(jab_encode* enc, jab_data* data) {
       // Initialize, analyze, encode pipeline
   }
   ```

2. **Create** `@/src/jabcode/encode_pipeline.c`:
   ```c
   #include "jabcode.h"
   #include "encoder.h"
   
   jab_int32 generateJABCode(jab_encode* enc, jab_data* data) {
       // Full implementation
   }
   ```

3. **Update** `@/src/jabcode/include/jabcode.h`:
   ```c
   extern jab_int32 generateJABCode(jab_encode* enc, jab_data* data);
   ```

4. **Update** `@/src/jabcode/Makefile`:
   ```makefile
   SOURCES = encoder.c decoder.c ldpc.c detector.c binarizer.c \
             mask.c sample.c transform.c interleave.c pseudo_random.c \
             encode_pipeline.c  # ← ADD THIS
   ```

5. **Rebuild** desktop library:
   ```bash
   cd src/jabcode
   make clean && make
   ```

6. **Verify** desktop apps still work:
   ```bash
   bin/jabcodeWriter --input "test" --output test.png
   bin/jabcodeReader --input test.png
   ```

7. **Update** mobile bridge:
   ```c
   // Remove TODO, implement actual encoding
   jab_int32 encode_result = generateJABCode(enc, data_struct);
   ```

8. **Test** mobile build:
   ```bash
   cd swift-java-wrapper/build
   make && ./mobile_bridge_tests
   ```

### Estimated Effort

- **Extraction:** 1 hour
- **Desktop rebuild & testing:** 1 hour
- **Mobile integration:** 30 min
- **End-to-end testing:** 2 hours
- **TOTAL:** 4.5 hours

### Risk Assessment

**Low Risk:**
- No logic changes, just moving code
- Desktop apps continue using same binary code (via linking)
- Mobile gets access to existing, tested implementation

**Validation:**
- Desktop encode/decode tests must pass
- Mobile validation tests must pass
- Round-trip test: mobile encode → desktop decode

## Alternative: Link Application Objects (Option B)

**NOT RECOMMENDED** - violates clean architecture

```cmake
# BAD: Link jabwriter.o into mobile library
target_link_libraries(jabcode-mobile 
    ../src/jabcodeWriter/jabwriter.o  # ← Hacky
)
```

**Problems:**
- Pulls in application-level dependencies (CLI parsing, file I/O)
- Tight coupling to desktop build artifacts
- Fragile maintenance

## Current Workaround

Mobile bridge returns error:
```c
setError("Encoding not yet implemented (Phase 1 blocker - generateJABCode not in library)");
return NULL;
```

All parameter validation tests pass, but actual encoding fails gracefully.

## Decision Required

**USER INPUT NEEDED:** Approve Option A (extract to library) before proceeding with implementation?

---

**Related Files:**
- `@/src/jabcodeWriter/jabwriter.c:430-502` - Source implementation
- `@/swift-java-wrapper/src/c/mobile_bridge.c:105-115` - Blocked code
- `@/src/jabcode/Makefile` - Requires update
- `@/src/jabcode/include/jabcode.h` - Requires extern declaration

**Test Plan:**
1. Desktop: Encode "Hello, World!" → verify PNG output
2. Desktop: Decode PNG → verify "Hello, World!" extracted
3. Mobile: Call `jabMobileEncode()` → verify RGBA buffer returned
4. Cross-platform: Mobile encode → desktop decode (parity test)
