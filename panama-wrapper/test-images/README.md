# JABCode Sample Images

This directory contains sample JABCode images demonstrating all supported color modes with both single-symbol and cascaded multi-symbol encoding. Each sample encodes its own configuration details for easy reference.

## Single-Symbol Samples

| File | Color Mode | Size | Symbols | Description |
|------|------------|------|---------|-------------|
| `sample_4_color_simple.png` | 4-color (Nc=1) | 34K | 1 | Basic mode with 4 distinct colors |
| `sample_8_color_simple.png` | 8-color (Nc=2) | 23K | 1 | Standard mode with 8 colors |
| `sample_16_color_simple.png` | 16-color (Nc=3) | 15K | 1 | Enhanced color palette |
| `sample_32_color_simple.png` | 32-color (Nc=4) | 14K | 1 | Extended color range |
| `sample_64_color_simple.png` | 64-color (Nc=5) | 11K | 1 | High-density adaptive palette |
| `sample_128_color_simple.png` | 128-color (Nc=6) | 11K | 1 | Very high-density with interpolation |

## Cascaded Multi-Symbol Samples

| File | Color Mode | Size | Symbols | Description |
|------|------------|------|---------|-------------|
| `sample_4_color_cascaded.png` | 4-color (Nc=1) | 140K | 2 | Cascaded encoding with primary + secondary |
| `sample_8_color_cascaded.png` | 8-color (Nc=2) | 161K | 2 | Cascaded encoding with extended message |
| `sample_16_color_cascaded.png` | 16-color (Nc=3) | 131K | 2 | Cascaded encoding with enhanced palette |
| `sample_32_color_cascaded.png` | 32-color (Nc=4) | 115K | 2 | Cascaded encoding with extended range |
| `sample_64_color_cascaded.png` | 64-color (Nc=5) | 113K | 2 | Cascaded high-density encoding |
| `sample_128_color_cascaded.png` | 128-color (Nc=6) | 113K | 2 | Cascaded very high-density encoding |

## Sample Content

Each sample encodes a self-describing message that includes:
- Color mode and Nc value
- ECC level
- Module size
- Number of symbols
- Technical details about JABCode technology

Example decoded message:
```
JABCode Sample | Mode: 64-color (Nc=5) | ECC Level: 5 | Module Size: 12px | Symbols: 1 | 
Encoding: UTF-8 | Error Correction: LDPC | Mask: Adaptive | 
This demonstrates JABCode's 2D color barcode technology with advanced error correction capabilities.
```

## Configuration

**Single-Symbol Samples:**
- **ECC Level:** 5 (Medium error correction)
- **Module Size:** 12 pixels (optimized for visibility)
- **Encoding:** UTF-8
- **Symbol Number:** 1 (single symbol)

**Cascaded Samples:**
- **ECC Level:** 5 (Medium error correction)
- **Module Size:** 12 pixels (optimized for visibility)
- **Encoding:** UTF-8
- **Symbol Number:** 2 (1 primary + 1 secondary)
- **Symbol Versions:** 12×12 for both symbols (same size required per JABCode spec)
- **Extended message:** Longer content demonstrating multi-symbol data distribution

## Cascaded Multi-Symbol Encoding

**Status:** ✅ Fully Supported (as of Q1 2026)

Cascaded JABCode encoding is now fully supported through the `SymbolVersion` API. The `Config.Builder` now provides `symbolVersions(List<SymbolVersion>)` to configure explicit symbol dimensions for multi-symbol encoding.

**Important Constraint:** All symbols in a cascade must have identical dimensions (e.g., all 12×12). This is a JABCode specification requirement, not an API limitation.

**Example Usage:**
```java
var config = Config.builder()
    .colorNumber(64)
    .symbolNumber(2)
    .symbolVersions(List.of(
        new SymbolVersion(12, 12),  // Primary symbol
        new SymbolVersion(12, 12)   // Secondary symbol (must match)
    ))
    .eccLevel(5)
    .build();
```

## Testing

These samples are used for:
- Visual verification of encoding quality across all color modes
- Round-trip testing (encode → decode)
- Color mode compatibility validation
- Integration testing
- Performance benchmarking

## Regeneration

To regenerate these samples, run:
```bash
cd panama-wrapper
mvn test-compile -q
cd /mnt/b34628fa-d41e-4c37-8caf-f06a6ecbb1ae/projects/practice/barcode/jabcode/panama-wrapper
LD_LIBRARY_PATH=../lib java --enable-native-access=ALL-UNNAMED \
  -cp target/classes:target/test-classes \
  com.jabcode.panama.GenerateSamples
```

This will generate:
- 6 single-symbol samples (`*_simple.png`)
- 6 cascaded 2-symbol samples (`*_cascaded.png`)
- Total: 12 sample images

## Notes

- **256-color mode** (Nc=7) is not included due to a malloc corruption bug in the encoder initialization
- All samples have been verified to decode correctly
- Samples use the fixed mask metadata synchronization (encoder-decoder alignment)
- File sizes vary based on color mode complexity and encoded message length
- Larger file sizes for lower color modes due to more modules needed for same data capacity
- Cascaded samples are significantly larger due to containing 2 symbols with extended message content

## Status

✅ All 6 color modes working correctly (4, 8, 16, 32, 64, 128 colors)  
✅ Single-symbol encoding fully functional  
✅ Multi-symbol cascading fully supported (Q1 2026)  
✅ SymbolVersion API for explicit cascade configuration  
❌ 256-color mode excluded (known malloc issue)

---

**Last Updated:** January 2026  
**Total Samples:** 12 (6 single-symbol + 6 cascaded)
