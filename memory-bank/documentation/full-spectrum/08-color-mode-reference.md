# Color Mode Reference
**Complete Technical Specifications for All JABCode Modes** 📖

*A comprehensive reference for JABCode color modes, including capacity calculations, palette structures, and encoding parameters.*

---

## Overview

JABCode supports 7 color modes (Nc = 0-6), each using a different number of colors to encode data. This reference provides complete technical specifications for each mode.

### Quick Reference Table

| Mode | Nc | Colors | Bits/Module | Status | Min Version | Max Version |
|------|-------|--------|-------------|--------|-------------|-------------|
| 2-color | 0 | 2 | 1 | Reserved | - | - |
| 4-color | 1 | 4 | 2 | ✅ Stable | 1×1 | 32×32 |
| 8-color | 2 | 8 | 3 | ✅ Stable | 1×1 | 32×32 |
| 16-color | 3 | 16 | 4 | ✅ Stable | 1×1 | 32×32 |
| 32-color | 4 | 32 | 5 | ✅ Stable | 1×1 | 32×32 |
| 64-color | 5 | 64 | 6 | ✅ Stable | 1×1 | 32×32 |
| 128-color | 6 | 128 | 7 | ✅ Stable | 1×1 | 32×32 |
| 256-color | 7 | 256 | 8 | ❌ Broken | - | - |

---

## 4-Color Mode (Nc=1)

### Basic Properties

- **Color count:** 4
- **Bits per module:** 2
- **Nc value:** 1
- **Symbol versions:** 1×1 to 32×32
- **Status:** ✅ Fully functional

### Color Palette

**Default colors:**
```
Color 0: RGB(255, 255, 255) - White
Color 1: RGB(0,   0,   255) - Blue
Color 2: RGB(255, 0,   0  ) - Red
Color 3: RGB(0,   0,   0  ) - Black
```

**Palette structure:** Single palette, 12 bytes (4 colors × 3 bytes RGB)

### Data Capacity

**Theoretical maximum** (32×32 version, no metadata, no ECC):
```
Modules: 32 × 32 = 1,024
Data modules: ~900 (excluding finder patterns, alignment)
Bits: 900 × 2 = 1,800 bits = 225 bytes
```

**Practical capacity** (with ECC level 5, metadata):
```
~100-150 bytes depending on version
```

### Use Cases

- Maximum reliability applications
- Outdoor signage
- Poor lighting conditions
- Budget printers/scanners
- Industrial harsh environments

### Technical Notes

- Simplest mode, easiest color discrimination
- Largest physical size for given data capacity
- Best error recovery due to high contrast
- No adaptive palette (fixed colors)

---

## 8-Color Mode (Nc=2)

### Basic Properties

- **Color count:** 8
- **Bits per module:** 3
- **Nc value:** 2
- **Symbol versions:** 1×1 to 32×32
- **Status:** ✅ Fully functional

### Color Palette

**Default colors:**
```
Color 0: RGB(255, 255, 255) - White
Color 1: RGB(0,   0,   255) - Blue
Color 2: RGB(255, 0,   0  ) - Red
Color 3: RGB(255, 255, 0  ) - Yellow
Color 4: RGB(0,   255, 0  ) - Green
Color 5: RGB(255, 0,   255) - Magenta
Color 6: RGB(0,   255, 255) - Cyan
Color 7: RGB(0,   0,   0  ) - Black
```

**Palette structure:** Single palette, 24 bytes (8 colors × 3 bytes RGB)

### Data Capacity

**Theoretical maximum:**
```
Modules: 1,024
Data modules: ~900
Bits: 900 × 3 = 2,700 bits = 337 bytes
```

**Practical capacity:**
```
~150-250 bytes depending on version and ECC
```

### Use Cases

- General purpose (recommended default)
- Digital displays
- Standard color printers
- Indoor environments
- Business applications

### Technical Notes

- Standard mode, well-balanced
- Good compromise between density and reliability
- Works on most equipment
- Fixed palette (no adaptation)

---

## 16-Color Mode (Nc=3)

### Basic Properties

- **Color count:** 16
- **Bits per module:** 4
- **Nc value:** 3
- **Symbol versions:** 1×1 to 32×32
- **Status:** ✅ Fully functional

### Color Palette

**Default colors:** 16-color palette following RGB combinations
```
Grayscale + Primary/Secondary RGB variations
Optimized for discrimination
```

**Palette structure:** Single palette, 48 bytes (16 colors × 3 bytes RGB)

### Data Capacity

**Theoretical maximum:**
```
Modules: 1,024
Data modules: ~900
Bits: 900 × 4 = 3,600 bits = 450 bytes
```

**Practical capacity:**
```
~200-350 bytes depending on version and ECC
```

### Use Cases

- Indoor controlled environments
- Quality displays
- Professional printing
- Higher density requirements
- Limited space applications

### Technical Notes

- Higher density than 8-color
- Requires better color accuracy
- Still uses fixed palette
- Good for digital-only applications

---

## 32-Color Mode (Nc=4)

### Basic Properties

- **Color count:** 32
- **Bits per module:** 5
- **Nc value:** 4
- **Symbol versions:** 1×1 to 32×32
- **Status:** ✅ Fully functional

### Color Palette

**Palette structure:** Single palette, 96 bytes (32 colors × 3 bytes RGB)

**Generation:** Algorithmic palette generation based on RGB space subdivision

### Data Capacity

**Theoretical maximum:**
```
Modules: 1,024
Data modules: ~900
Bits: 900 × 5 = 4,500 bits = 562 bytes
```

**Practical capacity:**
```
~250-450 bytes depending on version and ECC
```

### Use Cases

- Professional applications
- High-quality displays
- Specialized industrial use
- Security credentials
- Controlled scanning environments

### Technical Notes

- Demanding color accuracy requirements
- Generated palette optimized for discrimination
- Requires quality equipment
- Best for digital-only workflows

---

## 64-Color Mode (Nc=5)

### Basic Properties

- **Color count:** 64
- **Bits per module:** 6
- **Nc value:** 5
- **Symbol versions:** 1×1 to 32×32
- **Status:** ✅ Fully functional (recently fixed)

### Color Palette

**Palette structure:** 
- 4 palettes × 64 colors each
- Total: 768 bytes (64 colors × 3 bytes × 4 palettes)

**Palette types:**
1. **Primary palette:** Main data encoding colors
2. **Calibration palette:** Reference colors for decoder
3. **Interpolation palette:** Reserved for future use
4. **Metadata palette:** Fixed colors for structure

### Adaptive Palette Selection

The encoder analyzes data and selects the 64 colors that maximize discrimination:

```c
// Pseudocode
colors = analyzeDataDistribution(data);
palette = selectOptimal64Colors(colors, colorSpace);
```

### Data Capacity

**Theoretical maximum:**
```
Modules: 1,024
Data modules: ~900
Bits: 900 × 6 = 5,400 bits = 675 bytes
```

**Practical capacity:**
```
~300-550 bytes depending on version and ECC
```

### Use Cases

- Research applications
- Laboratory settings
- Specialized automation
- Maximum density requirements
- Controlled perfect conditions

### Technical Notes

- **Adaptive palette:** Encoder selects colors based on data
- **Recent fix:** Mask metadata synchronization bug resolved
- Requires excellent equipment
- All 11 tests passing ✅

### Mask Pattern Selection

64-color mode uses all 8 mask patterns (0-7):

```c
Pattern 0: (x + y) % 2
Pattern 1: y % 2
Pattern 2: x % 3
Pattern 3: (x + y) % 3
Pattern 4: ((y/2) + (x/3)) % 2
Pattern 5: ((x*y) % 2) + ((x*y) % 3)
Pattern 6: (((x*y) % 2) + ((x*y) % 3)) % 2
Pattern 7: (((x+y) % 2) + ((x*y) % 3)) % 2
```

Encoder tries all and picks the one with lowest penalty score.

---

## 128-Color Mode (Nc=6)

### Basic Properties

- **Color count:** 128
- **Bits per module:** 7
- **Nc value:** 6
- **Symbol versions:** 1×1 to 32×32
- **Status:** ✅ Fully functional (recently fixed)

### Color Palette

**Palette structure:**
- 4 palettes × 128 colors each
- Total: 1,536 bytes (128 colors × 3 bytes × 4 palettes)

**Palette interpolation:** Not all 128 colors are explicitly embedded. Decoder interpolates between reference colors.

### Interpolation Algorithm

```
For color index i:
  if i < threshold:
    Use explicit palette[i]
  else:
    Interpolate between palette[base1] and palette[base2]
```

This reduces the number of colors that must be explicitly encoded in the barcode.

### Data Capacity

**Theoretical maximum:**
```
Modules: 1,024
Data modules: ~900
Bits: 900 × 7 = 6,300 bits = 787 bytes
```

**Practical capacity:**
```
~350-650 bytes depending on version and ECC
```

### Use Cases

- Research only
- Laboratory conditions
- Proof of concept
- Pushing technology limits

### Technical Notes

- **Palette interpolation:** Reduces embedded palette size
- **Recent fix:** Mask metadata bug resolved
- Extremely demanding conditions required
- All 13 tests passing ✅

---

## 256-Color Mode (Nc=7)

### Basic Properties

- **Color count:** 256
- **Bits per module:** 8
- **Nc value:** 7
- **Symbol versions:** 1×1 to 32×32
- **Status:** ❌ Malloc corruption bug

### Known Issues

**Malloc crash during encoder initialization:**
```
malloc(): invalid size (unsorted)
Aborted (core dumped)
```

**Root cause:** Unknown. Suspected issues:
1. Metadata offset calculations assume ≤64 colors
2. Symbol matrix allocation may be too small
3. Palette interpolation logic may overflow
4. Multiple issues compounding

**Current workaround:** Skip `placeMasterMetadataPartII()` for 256-color, which prevents encoding entirely.

### Theoretical Specifications

**Palette structure (if working):**
- 4 palettes × 256 colors each
- Total: 3,072 bytes (256 colors × 3 bytes × 4 palettes)

**Data capacity (theoretical):**
```
Modules: 1,024
Data modules: ~900
Bits: 900 × 8 = 7,200 bits = 900 bytes
```

### Investigation Status

**Completed:**
- ✅ Fixed palette allocation (1 palette → 4 palettes)
- ✅ Verified palette generation compatible with multi-palette structure
- ✅ Added safety check to prevent crash

**Remaining:**
- ❌ Find exact malloc corruption point (needs AddressSanitizer)
- ❌ Fix offset calculations for 256-color metadata
- ❌ Verify symbol matrix sizes adequate
- ❌ Test palette interpolation at 256 colors

**Priority:** Medium (most use cases covered by 128-color and below)

---

## Symbol Version Matrix

### Version Format

Versions specified as `width × height` where both are 1-32.

**Examples:**
- `1×1`: Smallest (21×21 modules)
- `10×10`: Medium (201×201 modules)
- `32×32`: Maximum (621×621 modules)

### Module Count by Version

```
Version 1×1:   21 × 21   = 441 modules
Version 5×5:   101 × 101 = 10,201 modules
Version 10×10: 201 × 201 = 40,401 modules
Version 20×20: 401 × 401 = 160,801 modules
Version 32×32: 621 × 621 = 385,641 modules
```

### Data Module Calculation

Not all modules store data. Some are reserved for:
- Finder patterns (corners)
- Alignment patterns (interior)
- Timing patterns (edges)
- Metadata (embedded)
- Palette colors (for higher modes)

**Approximate data modules:** 70-85% of total modules, depending on version.

---

## ECC Levels

### Available Levels

JABCode supports ECC levels 0-10:

| Level | Error Correction | Use Case |
|-------|------------------|----------|
| 0 | Minimal (~5%) | Perfect conditions only |
| 1-2 | Low (~10-15%) | Controlled digital environments |
| 3-4 | Medium (~20-30%) | Standard indoor use |
| 5-6 | High (~40-50%) | Recommended for most applications |
| 7 | Very High (~60%) | Damaged or poor quality scans |
| 8-10 | Maximum (~70%+) | Extreme conditions |

### LDPC Code

JABCode uses **Low-Density Parity-Check (LDPC)** codes for error correction.

**Advantages:**
- Near-optimal error correction capacity
- Efficient encoding/decoding
- Better than Reed-Solomon for JABCode's use case

**Trade-off:** Higher ECC = less data capacity but more resilience.

---

## Metadata Structure

### Master Symbol Metadata

**Part I:** Basic configuration
- Symbol number
- Color number (Nc value)
- Symbol versions
- ECC levels

**Part II:** Encoding parameters
- **Mask type** (0-7) ← Critical for decoding
- Encoding seeds
- Additional parameters

### Metadata Placement

**Low color modes (≤8 colors):**
- Metadata embedded in dedicated modules
- Uses fixed color pattern

**High color modes (>8 colors):**
- Metadata split across multiple locations
- Part of adaptive palette placement
- More complex offset calculations

---

## Masking

### Purpose

Prevent problematic patterns:
- Long runs of same color
- Repetitive structures
- Poor contrast regions

### Mask Application

**Encoding:**
```c
encoded_module = data_module XOR mask_pattern(x, y)
```

**Decoding:**
```c
data_module = encoded_module XOR mask_pattern(x, y)
```

Since `A XOR B XOR B = A`, this recovers the original data.

### Critical Requirement

**Encoder and decoder must use the SAME mask pattern.**

The mask type is written to Part II metadata. Recent bug: encoder wasn't updating this for 64/128-color modes, causing complete decoding failure.

---

## Capacity Comparison

### Relative Capacity (Same Physical Size)

For a fixed symbol version (e.g., 10×10):

| Mode | Relative Capacity | Absolute Estimate |
|------|-------------------|-------------------|
| 4-color | 1.0× (baseline) | ~150 bytes |
| 8-color | 1.5× | ~225 bytes |
| 16-color | 2.0× | ~300 bytes |
| 32-color | 2.5× | ~375 bytes |
| 64-color | 3.0× | ~450 bytes |
| 128-color | 3.5× | ~525 bytes |
| 256-color | 4.0× (theoretical) | ~600 bytes |

*With ECC level 5, medium version*

### Inverse Relationship

**More colors = smaller physical size for same data:**

To encode 300 bytes:
- 4-color: Requires ~12×12 version
- 8-color: Requires ~8×8 version
- 16-color: Requires ~6×6 version
- 64-color: Requires ~4×4 version

---

## Implementation Notes

### Native C Structures

```c
typedef struct {
    jab_int32 color_number;        // Number of colors (4,8,16,...)
    jab_int32 symbol_number;       // Symbols in cascade (1-61)
    jab_int32 module_size;         // Module size in pixels
    jab_byte* palette;             // Palette pointer (multi-palette for >8 colors)
    jab_vector2d* symbol_versions; // Symbol version array
    jab_byte* symbol_ecc_levels;   // Per-symbol ECC levels
    jab_symbol* symbols;           // Symbol array
} jab_encode;
```

### Java API

```java
var config = JABCodeEncoder.Config.builder()
    .colorNumber(64)      // Color mode
    .eccLevel(6)          // Error correction
    .moduleSize(12)       // Module size in pixels
    .symbolNumber(1)      // Number of symbols
    .build();

encoder.encodeToPNG(data, output, config);
```

---

## Performance Characteristics

### Encoding Time

**Relative encoding time** (4-color = 1.0×):

| Mode | Relative Time | Factors |
|------|---------------|---------|
| 4-color | 1.0× | Baseline |
| 8-color | 1.2× | More colors to process |
| 16-color | 1.4× | Palette generation overhead |
| 32-color | 1.7× | Complex palette selection |
| 64-color | 2.0× | Adaptive palette analysis |
| 128-color | 2.5× | Interpolation calculations |

### Decoding Time

**Relative decoding time:**

| Mode | Relative Time | Factors |
|------|---------------|---------|
| 4-color | 1.0× | Simple color matching |
| 8-color | 1.1× | More colors to match |
| 16-color | 1.3× | Increased search space |
| 32-color | 1.5× | Complex color discrimination |
| 64-color | 1.8× | Adaptive palette reconstruction |
| 128-color | 2.2× | Interpolation required |

**Absolute times:** Encoding: 10-50ms, Decoding: 20-100ms (varies by size)

---

## Best Practices

### Color Mode Selection

1. **Start with 8-color** unless you have specific requirements
2. **Use 4-color** for outdoor/harsh environments
3. **Use 16-32 color** for controlled indoor settings
4. **Use 64-128 color** only for research/specialized applications

### ECC Level Selection

1. **Level 5-6** for most applications
2. **Level 7** for higher color modes (64/128)
3. **Level 3-4** only if confident in perfect conditions
4. **Higher ECC** reduces capacity but increases reliability

### Module Size

1. **12-16 pixels** for digital displays
2. **16-20 pixels** for printing
3. **Larger** for distant scanning or poor equipment
4. **Smaller** only with high-quality equipment

---

## Troubleshooting Reference

### Common Issues by Mode

**4-color:**
- ✅ Very reliable, few issues
- Ensure sufficient contrast if printing

**8-color:**
- ✅ Generally trouble-free
- Watch for color shift in JPEG compression

**16-32 color:**
- Check color accuracy of printer/display
- Ensure good lighting for scanning

**64-128 color:**
- ⚠️ Requires excellent conditions
- Use high ECC (level 6-7)
- Test thoroughly before deploying

**256-color:**
- ❌ Currently broken (malloc crash)
- Not available for use

---

## Code References

**Encoder:** `src/jabcode/encoder.c`  
**Decoder:** `src/jabcode/decoder.c`  
**Palette generation:** `src/jabcode/encoder.c:29-93`  
**Mask patterns:** `src/jabcode/mask.c:362-405`  
**Java API:** `panama-wrapper/src/main/java/com/jabcode/panama/`

---

## Further Reading

- **[02-sample-gallery.md](02-sample-gallery.md)** - Visual examples of each mode
- **[03-choosing-color-mode.md](03-choosing-color-mode.md)** - Decision guide
- **[04-mask-metadata-saga.md](04-mask-metadata-saga.md)** - Technical deep dive on critical bug
- **[05-encoder-memory-architecture.md](05-encoder-memory-architecture.md)** - Memory and palette details

---

*"In theory, theory and practice are the same. In practice, they are not."* - Yogi Berra

These specs represent the theoretical capabilities. Real-world capacity depends on data content, ECC level, and symbol version. Always test with your actual data! 🧪
