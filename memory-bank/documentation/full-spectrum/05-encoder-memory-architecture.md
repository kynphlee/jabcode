# Encoder Memory Architecture
**Palette Allocation and the 256-Color Mystery** 🧩

*A technical investigation into JABCode's memory management, palette allocation strategies, and the unresolved malloc corruption in 256-color mode.*

---

## Overview

JABCode's encoder manages complex memory structures for palettes, symbols, and metadata. For most color modes (4-128 colors), this works flawlessly. For 256-color mode, it crashes spectacularly. This document explores why.

---

## Palette Memory Architecture

### The Basic Structure

Every JABCode encoder maintains a palette—a lookup table of RGB colors used in the barcode. The structure is defined in the encoder:

```c
typedef struct {
    jab_int32 color_number;     // Number of colors (4, 8, 16, 32, 64, 128, 256)
    jab_int32 symbol_number;    // Number of symbols in cascade
    jab_int32 module_size;      // Size of each module in pixels
    
    jab_byte* palette;          // THE PALETTE POINTER ⚠️
    
    jab_vector2d* symbol_versions;
    jab_byte* symbol_ecc_levels;
    jab_symbol* symbols;
    // ... more fields ...
} jab_encode;
```

The `palette` pointer is where things get interesting.

### Original Allocation (THE BUG)

Initially, the encoder allocated palette memory like this:

```c
// encoder.c line ~207 (BEFORE FIX)
enc->palette = (jab_byte *)calloc(color_number * 3, sizeof(jab_byte));
```

**Math check:**
- Each color = 3 bytes (R, G, B)
- For 256 colors: `256 * 3 = 768 bytes`

Seems reasonable, right? **Wrong.**

### The Multi-Palette Reality

JABCode doesn't use just ONE palette. For higher color modes, it uses **multiple palettes** for different purposes:

1. **Primary palette**: Main color set for data encoding
2. **Calibration palette**: Reference colors for decoder calibration
3. **Interpolation palette**: Base colors for palette interpolation (128/256-color modes)
4. **Reserved palette**: Fixed colors for metadata and structure

The constant `COLOR_PALETTE_NUMBER` is defined as **4**.

### Fixed Allocation

We corrected the allocation:

```c
// encoder.c line ~210 (AFTER FIX)
jab_int32 num_palettes = COLOR_PALETTE_NUMBER;  // Always 4 palettes
enc->palette = (jab_byte *)calloc(color_number * 3 * num_palettes, sizeof(jab_byte));
```

**Math check:**
- 256 colors × 3 bytes × 4 palettes = **3,072 bytes**

That's **4× larger** than the original allocation!

### Impact of the Bug

With only 768 bytes allocated for 256-color mode, any attempt to write to palettes 2, 3, or 4 would:

1. **Write beyond allocated memory** (buffer overflow)
2. **Corrupt adjacent heap structures**
3. **Trigger malloc metadata corruption**
4. **Cause crash when malloc/free tries to use corrupted metadata**

Classic heap corruption bug. 💥

---

## Why 64 and 128-Color Modes Worked

**Question:** If palette allocation was wrong, why did 64-color and 128-color modes work?

**Answer:** They got lucky (mostly).

### The Allocation Math

| Mode | Original Allocation | Needed (4 palettes) | Buffer Overflow? |
|------|---------------------|---------------------|------------------|
| 4-color | 12 bytes | 48 bytes | Yes, but small |
| 8-color | 24 bytes | 96 bytes | Yes, but small |
| 16-color | 48 bytes | 192 bytes | Yes, moderate |
| 32-color | 96 bytes | 384 bytes | Yes, moderate |
| 64-color | 192 bytes | 768 bytes | Yes, large |
| 128-color | 384 bytes | 1,536 bytes | Yes, very large |
| 256-color | 768 bytes | 3,072 bytes | **Yes, MASSIVE** |

### Why No Crash (Usually)?

1. **Heap allocation patterns**: malloc often allocates more than requested (for alignment/bookkeeping)
2. **Unused palettes**: Not all code paths use all 4 palettes
3. **Write patterns**: The overflow might not hit critical heap metadata
4. **Luck**: Sometimes you write into unused heap space

But 256-color mode? The overflow was so large it **consistently** corrupted heap metadata, causing reliable crashes.

---

## The 256-Color Malloc Crash

Even after fixing palette allocation, 256-color mode still crashes. Let's investigate.

### The Crash Point

```
[ENCODER] Config: colorNumber=256, eccLevel=7, symbolNumber=1
[ENCODER] After createEncode: color_number in struct = 256
[ENCODER] ECC level set: requested=7, actual=7
malloc(): invalid size (unsorted)
Aborted (core dumped)
```

**Key observations:**
1. `createEncode()` completes successfully
2. ECC level gets set successfully  
3. Crash happens **after** ECC setup, **before** masking
4. Error is "invalid size (unsorted)" - malloc heap corruption

### Narrowing Down the Location

Looking at the call stack between ECC setup and masking:

```c
encodeJABCode()
├─ createEncode()                    ✅ Succeeds
├─ setEccLevel()                     ✅ Succeeds
├─ generateJABCode()
│  ├─ encodeData()                   ❓ Crash somewhere here
│  ├─ encodeMasterMetadata()
│  ├─ encodeMasterMetadataPartII()
│  ├─ updateMasterMetadataPartII()   ❌ Likely crash point
│  └─ placeMasterMetadataPartII()    ❌ Known problematic
└─ maskCode()                        ❌ Never reached
```

The crash happens during metadata encoding phase.

### The Suspicious Function

```c
// encoder.c line ~1064
jab_int32 placeMasterMetadataPartII(jab_encode* enc) {
    // Calculate metadata module positions
    jab_int32 colors_to_skip = 0;
    if(enc->color_number > 8) {
        colors_to_skip = MIN(enc->color_number, 64) - 2;  // ⚠️ SUSPICIOUS
    }
    
    jab_int32 num_palettes_skip = colors_to_skip / COLOR_NUMBER;  // ⚠️ SUSPICIOUS
    jab_int32 module_offset = colors_to_skip + num_palettes_skip * 3;
    
    // Use module_offset to place metadata...
    // If this calculation is wrong, we write to wrong memory!
}
```

### The Math Breaks Down

Let's trace the math for 256-color mode:

```c
colors_to_skip = MIN(256, 64) - 2 = 64 - 2 = 62
num_palettes_skip = 62 / COLOR_NUMBER
```

Wait. `COLOR_NUMBER` is a #define, but which one? There are multiple:

```c
#define COLOR_NUMBER 8              // Default color number
#define COLOR_PALETTE_NUMBER 4      // Number of palettes
```

If `COLOR_NUMBER` is 8:
```c
num_palettes_skip = 62 / 8 = 7 (integer division)
module_offset = 62 + 7 * 3 = 62 + 21 = 83
```

But for 256-color mode with a symbol matrix of, say, 80×80 modules, an offset of 83 might point to **the wrong row** or **beyond allocated memory**.

### The Root Cause Hypothesis

The formula `MIN(enc->color_number, 64) - 2` suggests the original code was designed for **maximum 64 colors**. The `MIN()` caps it at 64, meaning 128-color and 256-color modes both calculate as if they were 64-color mode.

**This is a fundamental design limitation**, not just a simple bug.

For 256-color mode:
1. The offset calculations assume ≤64 colors
2. Matrix sizes might be too small
3. Metadata placement writes to wrong addresses
4. Heap corruption ensues
5. Next malloc/free detects corruption and crashes

---

## Memory Layout Deep Dive

### The Encoder Structure in Memory

On a 64-bit system with 8-byte pointer alignment:

```
Offset | Field                    | Size    | Type
-------|--------------------------|---------|------------------
0      | color_number             | 4 bytes | int32
4      | symbol_number            | 4 bytes | int32  
8      | module_size              | 4 bytes | int32
12     | master_symbol_width      | 4 bytes | int32
16     | master_symbol_height     | 4 bytes | int32
20     | [padding]                | 4 bytes | alignment
24     | palette*                 | 8 bytes | pointer
32     | symbol_versions*         | 8 bytes | pointer
40     | symbol_ecc_levels*       | 8 bytes | pointer
48     | symbol_positions*        | 8 bytes | pointer
56     | symbols*                 | 8 bytes | pointer
...    | more fields              | ...     | ...
```

### The Palette Block

Once allocated, the palette memory layout:

```
For 256-color mode with 4 palettes:

[Palette 0: 768 bytes]  ← Primary palette (256 colors × 3 bytes RGB)
[Palette 1: 768 bytes]  ← Calibration palette
[Palette 2: 768 bytes]  ← Interpolation palette  
[Palette 3: 768 bytes]  ← Reserved palette

Total: 3,072 bytes (0xC00 hex)
```

### Symbol Matrix Allocation

Each symbol has a matrix of modules:

```c
jab_bitmap* symbol_matrix = (jab_bitmap*)calloc(1, sizeof(jab_bitmap));
symbol_matrix->width = version_x * MODULE_SIZE;
symbol_matrix->height = version_y * MODULE_SIZE;
symbol_matrix->pixel = (jab_byte*)calloc(
    symbol_matrix->width * symbol_matrix->height, 
    sizeof(jab_byte)
);
```

For 256-color mode, if `version_x = version_y = 32`:
- Matrix size: 32 × 32 = 1,024 modules
- Each module: 1 byte (color index 0-255)
- Total: 1,024 bytes

But the **metadata placement** code assumes smaller matrices based on the `MIN(color_number, 64)` cap.

---

## Attempted Fixes and Results

### Fix #1: Palette Allocation ✅

**Change:** Allocate 4 palettes instead of 1  
**Result:** Fixed 64-color and 128-color modes, but 256-color still crashes  
**Conclusion:** Necessary but not sufficient

### Fix #2: Metadata Update Restriction 🛡️

**Change:** Skip `placeMasterMetadataPartII()` for 256-color  
**Result:** Prevents crash, but also prevents 256-color encoding  
**Conclusion:** Workaround, not a fix

### Fix #3: Attempted Symbol Version Adjustment ❌

**Change:** Tried adjusting symbol version calculations  
**Result:** No improvement  
**Conclusion:** Problem is deeper than version calculation

---

## Path Forward for 256-Color Support

### What's Needed

1. **Memory sanitizer analysis**: Run with AddressSanitizer to find exact corruption point
   ```bash
   gcc -fsanitize=address -g encoder.c -o encoder_debug
   ```

2. **Offset calculation review**: Audit all offset formulas for 256-color assumptions

3. **Matrix size verification**: Ensure symbol matrices are large enough for 256-color metadata

4. **Palette interpolation check**: Verify interpolation logic handles 256 colors

### Likely Issues

**Theory #1: Matrix Too Small**  
The metadata placement assumes matrix positions that don't exist in 256-color mode's actual allocated matrix.

**Theory #2: Offset Formula Wrong**  
The `MIN(color_number, 64)` cap causes incorrect offset calculations that write beyond allocated memory.

**Theory #3: Palette Index Overflow**  
Some code might use `jab_byte` (uint8) for palette indices, which maxes at 255. For 256 colors (0-255), this works, but off-by-one errors could occur.

**Theory #4: Multiple Issues**  
Probably a combination of the above. 256-color mode was likely never fully tested or implemented.

---

## Performance Implications

### Memory Usage by Mode

| Mode | Palette Memory | Matrix (32×32) | Total Overhead |
|------|----------------|----------------|----------------|
| 4-color | 48 bytes | 1,024 bytes | ~1 KB |
| 8-color | 96 bytes | 1,024 bytes | ~1 KB |
| 64-color | 768 bytes | 1,024 bytes | ~2 KB |
| 128-color | 1,536 bytes | 1,024 bytes | ~2.5 KB |
| 256-color | 3,072 bytes | 1,024 bytes | ~4 KB |

The memory overhead scales with color count, but even 256-color mode only needs ~4KB. Very reasonable.

### Cache Implications

Modern CPUs have:
- **L1 cache**: 32-64 KB per core
- **L2 cache**: 256-512 KB per core
- **L3 cache**: Several MB shared

All JABCode structures fit comfortably in L1 cache, making encoding very fast. The memory architecture is actually quite efficient—when it works. 😅

---

## Lessons Learned

### Design for Growth

The original code's `MIN(color_number, 64)` suggests 64 colors was seen as the maximum. When 128 and 256-color modes were added later, the code wasn't fully updated.

**Lesson:** When designing memory layouts and offset calculations, either:
1. **Parameterize fully**: Use actual values, not hardcoded limits
2. **Assert limits**: `assert(color_number <= MAX_SUPPORTED)` to fail early
3. **Document assumptions**: Comment what range is actually supported

### Test Allocation Sizes

We discovered the palette allocation bug through test failures, not static analysis.

**Lesson:** Add explicit allocation size tests:
```c
void test_palette_allocation() {
    jab_encode* enc = createEncode(256, 1);
    size_t expected = 256 * 3 * COLOR_PALETTE_NUMBER;
    size_t actual = malloc_usable_size(enc->palette);
    assert(actual >= expected);
}
```

### Use Memory Tools Early

We should have run with AddressSanitizer from the start. It would have caught the buffer overflow immediately.

**Lesson:** Include sanitizer builds in CI/CD:
```makefile
debug: CFLAGS += -fsanitize=address -g
debug: all
```

---

## Current Status

### What Works ✅

- **4-128 color modes**: Full palette allocation, all metadata functions working
- **Memory efficiency**: Reasonable overhead even at 128 colors
- **No leaks**: All allocated memory properly freed

### What's Broken ❌

- **256-color mode**: Malloc crash during metadata placement
- **Root cause**: Unknown, likely offset calculation or matrix size issue
- **Workaround**: Skip `placeMasterMetadataPartII()` for 256-color

### Priority 🎯

**Medium**. Why?
1. 128-color mode already provides extreme density
2. 256-color is rare in practice (perfect conditions required)
3. Fix requires deep investigation, not quick patch
4. All common use cases covered by 4-128 color modes

---

## Investigation Tools

### Recommended Approach

1. **Build with sanitizers**:
   ```bash
   gcc -fsanitize=address -fsanitize=undefined -g src/jabcode/*.c
   ```

2. **Run ColorMode7Test**:
   ```bash
   LD_LIBRARY_PATH=lib java -cp ... ColorMode7Test
   ```

3. **Analyze output**: AddressSanitizer will show exactly where invalid write occurs

4. **Fix the root cause**: Adjust offset calculations or matrix allocations

5. **Test thoroughly**: Ensure fix doesn't break other modes

### Expected Output

```
==12345==ERROR: AddressSanitizer: heap-buffer-overflow
WRITE of size 1 at 0x7f1234567890 thread T0
    #0 placeMasterMetadataPartII encoder.c:1123
    #1 encodeJABCode encoder.c:2637
    
0x7f1234567890 is located 24 bytes to the right of 1024-byte region
allocated by thread T0 at:
    #0 calloc
    #1 createSymbolMatrix encoder.c:456
```

This would tell us exactly which write goes out of bounds and why.

---

## Code References

### Palette Allocation Fix
**File:** `src/jabcode/encoder.c:210-217`

### Metadata Placement (Problematic)
**File:** `src/jabcode/encoder.c:1064-1123`

### Workaround
**File:** `src/jabcode/encoder.c:2633`

---

## Conclusion

Memory management in JABCode is generally solid, but the 256-color mode reveals a design assumption (max 64 colors) that wasn't fully updated when higher modes were added.

**The good news:** We fixed the palette allocation bug, making 64 and 128-color modes rock solid.

**The remaining work:** Find and fix the metadata placement issue to unlock 256-color mode.

**The reality:** Most users will never need 256 colors. The modes we have working cover 99% of real-world use cases.

Sometimes "good enough" really is good enough. But it'd still be satisfying to solve the mystery someday. 🔍

---

## Further Reading

- **[04-mask-metadata-saga.md](04-mask-metadata-saga.md)** - Another critical bug hunt
- **[07-test-coverage-journey.md](07-test-coverage-journey.md)** - How testing revealed these issues
- **[10-future-enhancements.md](10-future-enhancements.md)** - Roadmap including 256-color fix

---

*"Memory is like a battlefield. The best victories are the ones where you know exactly why you won—and why you lost."* - Anonymous Systems Programmer

We won the battle for 4-128 colors. The 256-color war continues. ⚔️
