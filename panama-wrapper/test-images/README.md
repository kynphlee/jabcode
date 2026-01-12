# JABCode Sample Images

This directory contains sample JABCode images demonstrating all supported color modes. Each sample encodes its own configuration details for easy reference.

## Generated Samples

| File | Color Mode | Size | Symbols | Description |
|------|------------|------|---------|-------------|
| `sample_4_color_simple.png` | 4-color (Nc=1) | 34K | 1 | Basic mode with 4 distinct colors |
| `sample_8_color_simple.png` | 8-color (Nc=2) | 23K | 1 | Standard mode with 8 colors |
| `sample_16_color_simple.png` | 16-color (Nc=3) | 15K | 1 | Enhanced color palette |
| `sample_32_color_simple.png` | 32-color (Nc=4) | 14K | 1 | Extended color range |
| `sample_64_color_simple.png` | 64-color (Nc=5) | 11K | 1 | High-density adaptive palette |
| `sample_128_color_simple.png` | 128-color (Nc=6) | 11K | 1 | Very high-density with interpolation |

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

- **ECC Level:** 5 (Medium error correction)
- **Module Size:** 12 pixels (optimized for visibility)
- **Encoding:** UTF-8
- **Symbol Number:** 1 (single symbol)

## Cascaded Multi-Symbol Encoding

**Status:** Not yet supported in current API

Cascaded JABCode (multiple symbols: 1 primary + N secondary) requires explicit symbol version configuration which is not currently exposed in the `Config.Builder` API. The C encoder supports this through the `symbol_versions` array, but the Java Panama wrapper needs to be extended to provide this functionality.

**Future Enhancement:** Add `symbolVersions(List<Version>)` method to `Config.Builder` to enable multi-symbol cascaded encoding.

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
LD_LIBRARY_PATH=../lib:$LD_LIBRARY_PATH \
  java -cp "target/classes:target/test-classes:$(mvn -q dependency:build-classpath -DincludeScope=test | tail -1)" \
  com.jabcode.panama.GenerateSamples
```

Or use Maven:
```bash
cd panama-wrapper
mvn test-compile -q
LD_LIBRARY_PATH=../lib:$LD_LIBRARY_PATH \
  mvn exec:java -Dexec.mainClass="com.jabcode.panama.GenerateSamples" -Dexec.classpathScope=test -q
```

## Notes

- **256-color mode** (Nc=7) is not included due to a malloc corruption bug in the encoder initialization
- All samples have been verified to decode correctly
- Samples use the fixed mask metadata synchronization (encoder-decoder alignment)
- File sizes vary based on color mode complexity and encoded message length
- Larger file sizes for lower color modes due to more modules needed for same data capacity

## Status

✅ All 6 color modes working correctly (4, 8, 16, 32, 64, 128 colors)  
✅ Single-symbol encoding fully functional  
⚠️ Multi-symbol cascading requires API enhancement  
❌ 256-color mode excluded (known malloc issue)
