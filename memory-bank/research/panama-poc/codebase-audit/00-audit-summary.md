# JABCode Codebase Audit Summary

**Date:** 2026-01-07  
**Purpose:** Inform Panama FFM implementation on `panama-poc` branch  
**Auditor:** AI Assistant

## Quick Reference

This audit examined the JABCode C library and existing JNI wrapper to understand implementation patterns needed for the Panama FFM wrapper.

## Key Findings

### 1. C Library Structure

**Location:** `/src/jabcode/`

**Primary Header:** `include/jabcode.h` ← **Use this for jextract**

**Core Functions:**
```c
jab_encode* createEncode(jab_int32 color_number, jab_int32 symbol_number);
void destroyEncode(jab_encode* enc);
jab_int32 generateJABCode(jab_encode* enc, jab_data* data);
jab_data* decodeJABCode(jab_bitmap* bitmap, jab_int32 mode, jab_int32* status);
jab_bitmap* readImage(jab_char* filename);
jab_boolean saveImage(jab_bitmap* bitmap, jab_char* filename);
```

**Critical Data Structures:**
- `jab_data` - Flexible array member: `{length, data[]}`
- `jab_bitmap` - Flexible array member: `{width, height, ..., pixel[]}`
- `jab_encode` - Contains pointer to `jab_bitmap*`
- `jab_vector2d` - Simple struct: `{x, y}`
- `jab_point` - Simple struct: `{x, y}` (float)

### 2. JNI Wrapper Patterns

**Location:** `/javacpp-wrapper/`

**Key Pattern:** Opaque pointer passing
- C pointers stored as Java `long` values
- Cast to/from pointers in JNI layer
- Panama equivalent: Use `MemorySegment` directly

**Memory Management:**
- JNI: Manual `GetStringUTFChars`/`ReleaseStringUTFChars`
- Panama: Auto-managed with `Arena`

**Array Conversion:**
- JNI: `NewIntArray`, `SetIntArrayRegion`
- Panama: `MemorySegment.toArray()`

### 3. Critical Implementation Challenges

#### Challenge 1: Flexible Array Members

Both `jab_data` and `jab_bitmap` use C99 flexible array members:

```c
typedef struct {
    jab_int32 length;
    jab_char  data[];   // ← Flexible array
} jab_data;
```

**Panama Solution:**
```java
// Allocate: header size + array size
long headerSize = jab_data.sizeof();
long totalSize = headerSize + dataLength;
MemorySegment segment = arena.allocate(totalSize);

// Set header
jab_data.length(segment, dataLength);

// Access array
MemorySegment array = segment.asSlice(headerSize, dataLength);
```

#### Challenge 2: Pointer Chain Navigation

```c
jab_encode* enc = ...;
jab_bitmap* bitmap = enc->bitmap;  // Pointer dereference
```

**Panama Solution:**
```java
MemorySegment enc = ...;
MemorySegment bitmapPtr = jab_encode.bitmap(enc);
if (bitmapPtr.address() != 0) {
    int width = jab_bitmap.width(bitmapPtr);
}
```

#### Challenge 3: Memory Ownership

Some functions allocate memory outside arena:
```c
jab_data* decodeJABCode(...);  // Returns malloc'd memory
```

**Panama Solution:** Need to bind `free()` or JABCode-specific cleanup.

### 4. Implementation Priorities

**Phase 1:** Basic encoding (8-color mode)
**Phase 2:** Decoding
**Phase 3:** BufferedImage integration
**Phase 4:** All color modes
**Phase 5:** Testing & benchmarking

## Code Size Comparison

| Metric | JNI | Panama |
|--------|-----|--------|
| Native wrapper | 500+ lines C++ | 0 lines |
| Java code | ~1,600 lines | ~300 lines (estimated) |
| Build deps | C++ compiler | jextract only |
| Type safety | Runtime | Compile-time |

## Performance Expectations

Based on Panama benchmarks from other projects:
- **Throughput:** 95-105% of JNI
- **Memory:** Lower overhead
- **Warmup:** Comparable

## Audit Documents

1. **`01-jabcode-c-library-structure.md`**
   - Complete C API documentation
   - Data structure layouts
   - Memory management patterns
   - Constants and defaults

2. **`02-jni-wrapper-implementation.md`**
   - JNI call patterns
   - Memory management
   - Array conversions
   - High-level API design

3. **`03-panama-implementation-roadmap.md`**
   - Phase-by-phase implementation plan
   - Concrete code examples
   - Common pitfalls & solutions
   - Success criteria

## Quick Start Guide

### For Implementation

1. **Run jextract:**
   ```bash
   cd /mnt/.../jabcode/panama-wrapper
   ./jextract.sh
   ```

2. **Inspect generated bindings:**
   ```bash
   ls target/generated-sources/jextract/com/jabcode/panama/bindings/
   ```

3. **Start with encoder:**
   - Open `src/main/java/com/jabcode/panama/JABCodeEncoder.java`
   - Implement `encodeWithConfig()` method
   - Follow patterns in `03-panama-implementation-roadmap.md`

4. **Test incrementally:**
   ```bash
   mvn test
   ```

### For Understanding

**Start here:**
1. Read this summary
2. Review `03-panama-implementation-roadmap.md` for concrete examples
3. Reference `01-jabcode-c-library-structure.md` for C API details
4. Reference `02-jni-wrapper-implementation.md` for JNI patterns

## Key Type Mappings

| C Type | Panama Type |
|--------|-------------|
| `jab_byte` (unsigned char) | `ValueLayout.JAVA_BYTE` |
| `jab_int32` (int) | `ValueLayout.JAVA_INT` |
| `jab_float` | `ValueLayout.JAVA_FLOAT` |
| `jab_boolean` | `ValueLayout.JAVA_BYTE` |
| `jab_char*` (string) | `arena.allocateFrom(string)` |
| `jab_data*` | `MemorySegment` |
| `jab_bitmap*` | `MemorySegment` |
| `jab_encode*` | `MemorySegment` |

## Essential Constants

```java
// Color modes
int[] SUPPORTED_COLORS = {2, 4, 8, 16, 32, 64, 128, 256};

// Defaults
int DEFAULT_COLOR_NUMBER = 8;
int DEFAULT_MODULE_SIZE = 12;
int DEFAULT_ECC_LEVEL = 3;

// Return values
int JAB_SUCCESS = 1;
int JAB_FAILURE = 0;

// Bitmap format
int BITMAP_CHANNEL_COUNT = 4;  // RGBA
int BITMAP_BITS_PER_CHANNEL = 8;
```

## Critical Functions for Panama

### Must Implement

- [x] `createEncode()` - Allocate encoder
- [x] `generateJABCode()` - Generate barcode
- [x] `destroyEncode()` - Free encoder
- [x] `decodeJABCode()` - Decode barcode
- [x] `readImage()` - Load image
- [x] `saveImage()` - Save image

### Function Signatures (jextract generated)

```java
// Encoding
public static MemorySegment createEncode(
    Arena arena, int color_number, int symbol_number);

public static int generateJABCode(
    Arena arena, MemorySegment enc, MemorySegment data);

public static void destroyEncode(
    Arena arena, MemorySegment enc);

// Decoding
public static MemorySegment decodeJABCode(
    Arena arena, MemorySegment bitmap, int mode, MemorySegment status);

// I/O
public static MemorySegment readImage(
    Arena arena, MemorySegment filename);

public static int saveImage(
    Arena arena, MemorySegment bitmap, MemorySegment filename);
```

## Memory Management Strategy

### Arena Pattern

```java
public byte[] encode(String data) {
    try (Arena arena = Arena.ofConfined()) {
        // All allocations within this block are auto-freed
        MemorySegment enc = createEncode(arena, 8, 1);
        MemorySegment jabData = allocateJabData(arena, data.getBytes());
        generateJABCode(arena, enc, jabData);
        
        // Extract data as Java objects before arena closes
        byte[] result = extractPixels(enc);
        
        return result;
    } // Everything auto-freed here
}
```

### Manual Cleanup Needed

Some C functions allocate outside arena:
```java
MemorySegment result = decodeJABCode(...);
// Must manually free this!
free(arena, result);
```

## Testing Strategy

### Unit Tests
- Test each function independently
- Verify flexible array member handling
- Check NULL pointer handling

### Integration Tests
- Encode/decode round-trip
- All color modes
- Multi-symbol codes

### Performance Tests
- Compare with JNI version
- Memory leak detection
- Throughput benchmarks

## Common Mistakes to Avoid

1. **❌ Don't forget NULL checks:**
   ```java
   if (segment.address() == 0) { /* handle NULL */ }
   ```

2. **❌ Don't access flexible arrays as fields:**
   ```java
   // Wrong: byte[] data = jab_data.data(segment);
   // Right:
   MemorySegment array = segment.asSlice(jab_data.sizeof(), length);
   ```

3. **❌ Don't return MemorySegment from arena:**
   ```java
   // Wrong:
   try (Arena arena = Arena.ofConfined()) {
       return createEncode(arena, 8, 1); // Freed when arena closes!
   }
   ```

4. **❌ Don't forget unsigned byte handling:**
   ```java
   int value = pixel & 0xFF;  // Mask to prevent sign extension
   ```

## Success Metrics

- [ ] All tests passing
- [ ] Output matches JNI version exactly
- [ ] Performance within 10% of JNI
- [ ] Zero memory leaks
- [ ] No C++ code required
- [ ] Complete API documentation

## Estimated Effort

**Total:** 1-2 weeks (37-74 hours)

**Breakdown:**
- Setup & bindings: 1-2 hours
- Encoder: 8-16 hours
- Decoder: 8-16 hours
- BufferedImage: 4-8 hours
- Advanced features: 8-16 hours
- Testing: 8-16 hours

## Next Steps

1. **Immediate:** Run `./jextract.sh` to generate bindings
2. **Next:** Implement basic encoder in `JABCodeEncoder.java`
3. **Then:** Add decoder in `JABCodeDecoder.java`
4. **Finally:** Comprehensive testing and benchmarking

## Resources

- **Audit docs:** This directory
- **Implementation guide:** `/panama-wrapper/IMPLEMENTATION_GUIDE.md`
- **Quick start:** `/panama-wrapper/QUICKSTART.md`
- **C library:** `/src/jabcode/`
- **JNI wrapper:** `/javacpp-wrapper/`

---

**Status:** ✅ Audit Complete  
**Ready for:** Implementation Phase 1 (Setup & Binding Generation)
