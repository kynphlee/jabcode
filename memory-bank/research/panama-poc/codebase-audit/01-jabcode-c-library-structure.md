# JABCode C Library Structure Audit

## Overview

JABCode (Just Another Barcode) is a 2D color barcode system implemented in C. This document audits the C library structure to inform the Panama FFM implementation.

**Location:** `/src/jabcode/`

## Core Components

### 1. Source Files

| File | Purpose | Lines | Key Functions |
|------|---------|-------|---------------|
| `encoder.c` | Barcode encoding | ~21,000 | Encoding logic, symbol generation |
| `decoder.c` | Barcode decoding | ~57,000 | Decoding logic, pattern recognition |
| `detector.c` | Pattern detection | ~129,000 | Finder pattern detection, alignment |
| `ldpc.c` | Error correction | ~45,000 | LDPC error correction codes |
| `binarizer.c` | Image binarization | ~23,000 | Image preprocessing |
| `mask.c` | Data masking | ~13,000 | Mask pattern application |
| `transform.c` | Transformations | ~8,000 | Geometric transformations |
| `sample.c` | Module sampling | ~6,000 | Color sampling |
| `image.c` | Image I/O | ~6,500 | PNG/TIFF reading/writing |
| `interleave.c` | Data interleaving | ~2,000 | Data organization |
| `pseudo_random.c` | Random generation | ~400 | Pseudorandom sequences |

### 2. Header Files (`include/` directory)

| File | Purpose | Content |
|------|---------|---------|
| `jabcode.h` | Main API | Public interface, structs, function declarations |
| `encoder.h` | Encoding internals | Color palettes, encoding tables, constants |
| `decoder.h` | Decoding internals | Decoding tables, mode definitions |
| `detector.h` | Detection internals | Detection algorithms |
| `ldpc.h` | ECC internals | LDPC implementation details |
| `png.h` | PNG support | libpng interface |
| `tiff.h` | TIFF support | libtiff interface |
| `zlib.h` | Compression | zlib interface |

## Public API Functions

### Core Encoding/Decoding (`jabcode.h`)

```c
// Encoder management
extern jab_encode* createEncode(jab_int32 color_number, jab_int32 symbol_number);
extern void destroyEncode(jab_encode* enc);
extern jab_int32 generateJABCode(jab_encode* enc, jab_data* data);

// Decoder functions
extern jab_data* decodeJABCode(jab_bitmap* bitmap, jab_int32 mode, jab_int32* status);
extern jab_data* decodeJABCodeEx(jab_bitmap* bitmap, jab_int32 mode, jab_int32* status, 
                                  jab_decoded_symbol* symbols, jab_int32 max_symbol_number);

// Image I/O
extern jab_boolean saveImage(jab_bitmap* bitmap, jab_char* filename);
extern jab_boolean saveImageCMYK(jab_bitmap* bitmap, jab_boolean isCMYK, jab_char* filename);
extern jab_bitmap* readImage(jab_char* filename);

// Error reporting
extern void reportError(jab_char* message);
```

## Data Structures

### 1. jab_data (Flexible Array Member)

```c
typedef struct {
    jab_int32 length;
    jab_char  data[];   // Flexible array member - C99 feature
} jab_data;
```

**Memory Layout:**
```
[4 bytes: length][variable bytes: data]
```

**Panama Challenge:** Must handle flexible array member correctly.

### 2. jab_bitmap

```c
typedef struct {
    jab_int32 width;
    jab_int32 height;
    jab_int32 bits_per_pixel;     // 32
    jab_int32 bits_per_channel;   // 8
    jab_int32 channel_count;      // 4 (RGBA)
    jab_byte  pixel[];            // Flexible array member
} jab_bitmap;
```

**Pixel Format:** RGBA (Red, Green, Blue, Alpha), 8 bits per channel

**Size Calculation:** `width * height * channel_count` bytes

### 3. jab_encode

```c
typedef struct {
    jab_int32     color_number;         // 2, 4, 8, 16, 32, 64, 128, 256
    jab_int32     symbol_number;        // 1-61
    jab_int32     module_size;          // Pixels per module
    jab_int32     master_symbol_width;  // Master symbol width in modules
    jab_int32     master_symbol_height; // Master symbol height in modules
    jab_byte*     palette;              // Color palette (RGB format)
    jab_vector2d* symbol_versions;      // Symbol version info
    jab_byte*     symbol_ecc_levels;    // ECC levels per symbol
    jab_int32*    symbol_positions;     // Symbol positions
    jab_symbol*   symbols;              // Internal symbol representations
    jab_bitmap*   bitmap;               // Generated bitmap
} jab_encode;
```

**Key Fields for Panama:**
- `bitmap` - Access after encoding to get result
- `color_number` - Must be 2, 4, 8, 16, 32, 64, 128, or 256
- `symbol_number` - 1 to 61

### 4. jab_decoded_symbol

```c
typedef struct {
    jab_int32     index;
    jab_int32     host_index;
    jab_int32     host_position;
    jab_vector2d  side_size;
    jab_float     module_size;
    jab_point     pattern_positions[4];
    jab_metadata  metadata;
    jab_metadata  slave_metadata[4];
    jab_byte*     palette;
    jab_data*     data;
} jab_decoded_symbol;
```

### 5. jab_vector2d

```c
typedef struct {
    jab_int32 x;
    jab_int32 y;
} jab_vector2d;
```

### 6. jab_point

```c
typedef struct {
    jab_float x;
    jab_float y;
} jab_point;
```

## Type Definitions

```c
typedef unsigned char      jab_byte;
typedef char               jab_char;
typedef unsigned char      jab_boolean;
typedef int                jab_int32;
typedef unsigned int       jab_uint32;
typedef short              jab_int16;
typedef unsigned short     jab_uint16;
typedef long long          jab_int64;
typedef unsigned long long jab_uint64;
typedef float              jab_float;
typedef double             jab_double;
```

**Panama Mapping:**
- `jab_byte` → `ValueLayout.JAVA_BYTE` (unsigned char → byte)
- `jab_int32` → `ValueLayout.JAVA_INT`
- `jab_float` → `ValueLayout.JAVA_FLOAT`
- `jab_boolean` → `ValueLayout.JAVA_BYTE` (treat as 0/1)

## Constants

### Color Modes

```c
#define DEFAULT_SYMBOL_NUMBER       1
#define DEFAULT_MODULE_SIZE         12
#define DEFAULT_COLOR_NUMBER        8    // 8-color mode (black, blue, green, cyan, red, magenta, yellow, white)
#define DEFAULT_ECC_LEVEL           3
```

**Supported Color Numbers:** 2, 4, 8, 16, 32, 64, 128, 256

### Bitmap Constants

```c
#define BITMAP_BITS_PER_PIXEL   32  // RGBA
#define BITMAP_BITS_PER_CHANNEL 8   // 8 bits per R, G, B, A
#define BITMAP_CHANNEL_COUNT    4   // R, G, B, A
```

### Return Values

```c
#define JAB_SUCCESS  1
#define JAB_FAILURE  0
```

### Decode Modes

```c
#define NORMAL_DECODE       0
#define COMPATIBLE_DECODE   1
```

## Default Color Palette (8-color mode)

```c
static const jab_byte jab_default_palette[] = {
    0,   0,   0,     // 0: black
    0,   0,   255,   // 1: blue
    0,   255, 0,     // 2: green
    0,   255, 255,   // 3: cyan
    255, 0,   0,     // 4: red
    255, 0,   255,   // 5: magenta
    255, 255, 0,     // 6: yellow
    255, 255, 255    // 7: white
};
```

**Format:** RGB triplets (3 bytes per color)

## Memory Management Pattern

### Allocation

- `createEncode()` - Allocates `jab_encode` structure and internal buffers
- `generateJABCode()` - Allocates bitmap within encoder
- `decodeJABCode()` - Allocates and returns `jab_data` (caller must free)
- `readImage()` - Allocates and returns `jab_bitmap` (caller must free)

### Deallocation

- `destroyEncode(jab_encode*)` - Frees encoder and all internal allocations
- Manual `free()` required for:
  - `jab_data*` returned by decode functions
  - `jab_bitmap*` returned by `readImage()`

**Panama Consideration:** Arena-based memory management will handle most of this automatically, but need to be careful with structures that have flexible array members.

## Typical Usage Flows

### Encoding Flow

```c
// 1. Create encoder
jab_encode* enc = createEncode(8, 1);  // 8 colors, 1 symbol

// 2. Configure (optional, using defaults otherwise)
enc->module_size = 12;
enc->master_symbol_width = 0;   // Auto
enc->master_symbol_height = 0;  // Auto

// 3. Prepare data
jab_data data;
data.length = strlen(message);
// data.data = message (flexible array member)

// 4. Generate
jab_int32 result = generateJABCode(enc, &data);

// 5. Access bitmap
if (result == JAB_SUCCESS) {
    jab_bitmap* bitmap = enc->bitmap;
    // Use bitmap->pixel, bitmap->width, bitmap->height
}

// 6. Cleanup
destroyEncode(enc);
```

### Decoding Flow

```c
// 1. Load image
jab_bitmap* bitmap = readImage("jabcode.png");

// 2. Decode
jab_int32 status = 0;
jab_data* decoded = decodeJABCode(bitmap, NORMAL_DECODE, &status);

// 3. Extract data
if (decoded != NULL) {
    // Access decoded->data, decoded->length
}

// 4. Cleanup
free(decoded);
free(bitmap);
```

## Panama Implementation Considerations

### 1. Flexible Array Members

Both `jab_data` and `jab_bitmap` use C99 flexible array members. Panama must:
- Calculate total size: `struct_size + array_size`
- Allocate correctly with `Arena.allocate(totalSize)`
- Access array data with proper offset

### 2. Pointer Management

Many structures contain pointers (`jab_byte*`, `jab_symbol*`, etc.). Panama must:
- Use `MemorySegment` for all pointers
- Handle NULL pointers (check `.address() == 0`)
- Navigate pointer chains correctly

### 3. Function Signatures

All functions return primitives or pointers. Panama will:
- Use `MethodHandle` for each function
- Return `MemorySegment` for pointer returns
- Pass `MemorySegment` for pointer parameters

### 4. Struct Access

Generated Panama bindings will provide:
- Static methods for field access: `jab_data.length(segment)`, `jab_data.length(segment, value)`
- Layout information: `jab_data.sizeof()`, `jab_data.layout()`
- Allocation: `jab_data.allocate(arena)`

## Build Requirements

### Dependencies

- **libpng** - PNG image support
- **libtiff** - TIFF image support
- **zlib** - Compression
- **Standard C library** - math, string functions

### Library Output

- **Static:** `libjabcode.a`
- **Shared:** `libjabcode.so` (Linux), `libjabcode.dylib` (macOS), `jabcode.dll` (Windows)

**Panama requires:** Shared library for loading via FFM

## File Organization Summary

```
src/jabcode/
├── encoder.c           # Encoding implementation
├── decoder.c           # Decoding implementation
├── detector.c          # Pattern detection
├── ldpc.c              # Error correction
├── binarizer.c         # Image processing
├── mask.c              # Masking
├── transform.c         # Transformations
├── sample.c            # Sampling
├── image.c             # I/O
├── interleave.c        # Interleaving
├── pseudo_random.c     # PRNG
├── encoder.h           # Encoder internals
├── decoder.h           # Decoder internals
├── detector.h          # Detector internals
├── ldpc.h              # LDPC internals
├── pseudo_random.h     # PRNG internals
├── Makefile            # Build script
└── include/
    ├── jabcode.h       # ← PRIMARY HEADER FOR JEXTRACT
    ├── png.h           # libpng
    ├── tiff.h          # libtiff
    └── zlib.h          # zlib
```

## Next Steps for Panama

1. **Run jextract on `jabcode.h`** - This is the primary public API
2. **Handle flexible array members** - Special care for `jab_data` and `jab_bitmap`
3. **Implement memory arenas** - Automatic cleanup for most allocations
4. **Map color palette** - RGB byte array to Java structures
5. **Handle image I/O** - Integrate with Java's ImageIO or generate raw RGBA data

## References

- **Header:** `src/jabcode/include/jabcode.h`
- **Encoder:** `src/jabcode/encoder.c`
- **Decoder:** `src/jabcode/decoder.c`
- **Build:** `src/jabcode/Makefile`
