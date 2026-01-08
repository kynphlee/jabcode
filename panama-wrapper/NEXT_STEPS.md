# Next Steps - Panama Implementation

**Current Status:** Phases 1-6 Complete ✅ | Phase 7 Blocked ⚠️  
**Date:** 2026-01-07  
**Blocker:** jextract tool not installed

## Immediate Action Required

### 1. Install jextract

**Priority:** High  
**Effort:** 10 minutes  
**Blocker for:** All remaining work

```bash
# Download jextract
wget https://download.java.net/java/early_access/jextract/22/5/openjdk-22-jextract+5-33_linux-x64_bin.tar.gz

# Extract to tools directory
cd ~/tools/compilers/java
tar xzf ~/Downloads/openjdk-22-jextract+5-33_linux-x64_bin.tar.gz

# Add to PATH (add to ~/.bashrc for persistence)
export PATH="$HOME/tools/compilers/java/jextract-22/bin:$PATH"

# Verify
jextract --version
```

**Reference:** `JEXTRACT_SETUP.md` for detailed instructions

### 2. Generate Panama Bindings

**Priority:** High  
**Effort:** 5 minutes (automated)  
**Prerequisites:** jextract installed

```bash
cd /mnt/b34628fa-d41e-4c37-8caf-f06a6ecbb1ae/projects/practice/barcode/jabcode/panama-wrapper

# Set Java 23+ environment
export JAVA_HOME=/home/kynphlee/tools/compilers/java/jdk-23.0.1
export PATH="$JAVA_HOME/bin:$PATH"

# Generate bindings
./jextract.sh
```

**Expected Result:**
- ~50+ Java files in `target/generated-sources/jextract/`
- Bindings for all JABCode C functions and structs
- No compilation errors

**Validation:**
```bash
ls -l target/generated-sources/jextract/com/jabcode/panama/bindings/
# Should show: jabcode_h.java, jab_encode.java, jab_data.java, etc.
```

## Development Phases (Post-Bindings)

### Phase 7: Encoder Integration

**Priority:** High  
**Effort:** 2-3 hours  
**Prerequisites:** Bindings generated

**Tasks:**

1. **Update JABCodeEncoder.java to use generated bindings:**
   ```java
   import com.jabcode.panama.bindings.*;
   
   public byte[] encode(String data, int colorNumber, int eccLevel) {
       try (Arena arena = Arena.ofConfined()) {
           // Use generated createEncode binding
           MemorySegment enc = jabcode_h.createEncode(
               arena, colorNumber, 1
           );
           
           // Set up jab_data using generated struct
           MemorySegment jabData = jab_data.allocate(arena);
           jab_data.length(jabData, data.length());
           jab_data.data(jabData, arena.allocateFrom(data));
           
           // Generate using binding
           int result = jabcode_h.generateJABCode(enc, jabData);
           
           // Extract bitmap
           MemorySegment bitmap = jab_encode.bitmap(enc);
           // ... continue implementation
       }
   }
   ```

2. **Wire ColorPalette into encoding:**
   - Map `colorNumber` to `ColorMode`
   - Get palette via `ColorPaletteFactory`
   - Use palette for module color assignment

3. **Apply data masking:**
   - Use `DataMasking.maskAt()` for each module
   - Apply mask based on position and color count

4. **Embed palette metadata:**
   - Use `PaletteEmbedding.encodePalette()`
   - Write to metadata region

5. **Encode Nc metadata:**
   - Use `NcMetadata.encodeNcPart1()` for 3-color region
   - Use `NcMetadata.encodeNcPart2()` for full-palette region

**Tests to Write:**
- `JABCodeEncoderIntegrationTest`
  - Encode simple string with each color mode
  - Verify bitmap dimensions
  - Validate palette is embedded correctly
  - Confirm Nc is encoded properly

**Acceptance Criteria:**
- ✅ Encoder creates valid JABCode bitmaps
- ✅ All 7 color modes work (4/8/16/32/64/128/256)
- ✅ Palette is correctly embedded in metadata
- ✅ Nc Part I and Part II are encoded
- ✅ Data masking is applied
- ✅ No memory leaks (arena auto-cleanup)

### Phase 8: Decoder Integration

**Priority:** High  
**Effort:** 2-3 hours  
**Prerequisites:** Encoder working

**Tasks:**

1. **Update JABCodeDecoder.java:**
   ```java
   public String decode(byte[] imageData) {
       try (Arena arena = Arena.ofConfined()) {
           // Prepare bitmap
           MemorySegment bitmap = jab_bitmap.allocate(arena);
           // ... populate from imageData
           
           // Decode using binding
           MemorySegment decoded = jabcode_h.decodeJABCode(bitmap, 0, null);
           
           // Extract data
           int length = jab_decoded_symbol.data_length(decoded);
           MemorySegment dataPtr = jab_decoded_symbol.data(decoded);
           
           return dataPtr.reinterpret(length)
               .getString(0, StandardCharsets.UTF_8);
       }
   }
   ```

2. **Extract embedded palette:**
   - Read palette metadata region
   - Use `PaletteEmbedding.decodePalette()`

3. **Decode Nc metadata:**
   - Extract Nc Part I (3-color)
   - Use `NcMetadata.decodeNcPart1()`
   - Map to `ColorMode`

4. **Reconstruct full palette for high-color modes:**
   - For Mode 6-7, embedded palette is subset
   - Implement linear interpolation in `ColorInterpolator`
   - Reconstruct full 128/256 color palette

5. **Unmask data:**
   - Reverse data masking using `DataMasking`

**Tests to Write:**
- `JABCodeDecoderIntegrationTest`
  - Decode barcodes encoded in Phase 7
  - Round-trip test for each color mode
  - Validate palette reconstruction
  - Verify Nc decoding

**Acceptance Criteria:**
- ✅ Decoder extracts data from bitmaps
- ✅ All 7 color modes decode correctly
- ✅ Palette is extracted and reconstructed
- ✅ Nc is decoded from Part I
- ✅ High-color palettes (128/256) interpolate correctly
- ✅ Data unmasking works

### Phase 9: Interpolation Implementation

**Priority:** Medium  
**Effort:** 1-2 hours  
**Prerequisites:** Decoder working

**Tasks:**

1. **Implement ColorInterpolator:**
   ```java
   public class LinearColorInterpolator implements ColorInterpolator {
       @Override
       public int[][] interpolate(int[][] embeddedPalette, ColorMode targetMode) {
           // Linear interpolation for R channel (Mode 6)
           // Linear interpolation for R+G channels (Mode 7)
           // Return full 128 or 256 color palette
       }
   }
   ```

2. **Wire into decoder:**
   - Detect high-color modes (6-7)
   - Apply interpolation after palette extraction
   - Use reconstructed palette for decoding

**Tests:**
- `ColorInterpolatorTest`
  - Verify interpolation produces correct color count
  - Validate embedded colors are preserved
  - Check interpolated colors are evenly distributed

**Acceptance Criteria:**
- ✅ Mode 6 reconstructs 128 colors from 64
- ✅ Mode 7 reconstructs 256 colors from 64
- ✅ Embedded colors exactly match original
- ✅ Interpolated colors follow linear distribution

### Phase 10: End-to-End Testing

**Priority:** High  
**Effort:** 2-3 hours  
**Prerequisites:** All phases complete

**Tasks:**

1. **Create comprehensive integration tests:**
   - Encode/decode round-trip for all modes
   - Test with various data sizes
   - Test with special characters, Unicode
   - Error handling and edge cases

2. **Performance benchmarks:**
   - Encode/decode speed per mode
   - Memory usage per mode
   - Compare to JNI wrapper baseline

3. **Visual validation:**
   - Generate sample barcodes for each mode
   - Save as PNG images
   - Manual scan verification (if scanner available)

**Tests:**
- `JABCodeEndToEndTest`
  - Full encode/decode cycles
  - Data integrity validation
  - Performance measurements

**Acceptance Criteria:**
- ✅ 100% round-trip success rate for all modes
- ✅ Data integrity maintained
- ✅ Performance within 90-110% of JNI baseline
- ✅ No memory leaks
- ✅ All quality metrics pass

## Documentation Updates

**After Phase 7-10 completion:**

1. Update `README.md`:
   - Add "Quick Start" section with working examples
   - Include performance benchmarks
   - Add troubleshooting for common issues

2. Update `IMPLEMENTATION_SUMMARY.md`:
   - Mark Phase 7-10 as complete
   - Add final test counts and coverage
   - Document performance results

3. Create `EXAMPLES.md`:
   - Simple encode/decode
   - Color mode selection
   - Custom palette usage
   - Batch processing
   - CLI tool usage

4. Create `PERFORMANCE.md`:
   - Benchmark methodology
   - Results per color mode
   - Comparison with JNI wrapper
   - Memory profiling results

## Milestone Timeline (Post-jextract)

| Milestone | Effort | Cumulative |
|-----------|--------|------------|
| Install jextract | 10 min | 10 min |
| Generate bindings | 5 min | 15 min |
| Phase 7: Encoder | 2-3 hours | ~3 hours |
| Phase 8: Decoder | 2-3 hours | ~6 hours |
| Phase 9: Interpolation | 1-2 hours | ~8 hours |
| Phase 10: E2E Testing | 2-3 hours | ~11 hours |
| Documentation | 1 hour | ~12 hours |

**Total Estimated Effort:** ~12 hours of focused development

## Success Metrics

**Phase 7-10 Complete When:**
- ✅ All 7 color modes encode and decode successfully
- ✅ 150+ tests passing (current 78 + ~72 new)
- ✅ >90% code coverage
- ✅ Zero memory leaks detected
- ✅ Performance within acceptable range
- ✅ Documentation complete with working examples
- ✅ Ready for production use

## Current Blockers

1. **jextract installation** (Phase 7)
   - **Impact:** Blocks all remaining work
   - **Mitigation:** Install from https://jdk.java.net/jextract/
   - **Time to resolve:** 10 minutes

## Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| jextract incompatibility | Low | High | Use tested jextract version 22 |
| C struct alignment issues | Medium | Medium | Verify with native tests first |
| Performance regression | Low | Medium | Benchmark early, optimize if needed |
| Memory leaks | Medium | High | Use arena scopes, validate with profiler |
| Interpolation accuracy | Low | Low | Validate against spec examples |

## Support Resources

- **jextract Docs:** https://github.com/openjdk/jextract
- **Panama Tutorial:** https://foojay.io/today/project-panama-for-newbies-part-1/
- **FFM API:** https://openjdk.org/jeps/454
- **JABCode Spec:** ISO/IEC 23634
- **Spec Audit:** `/memory-bank/research/panama-poc/codebase-audit/`

---

**Status:** Ready to proceed once jextract is installed.  
**Next Action:** Install jextract following `JEXTRACT_SETUP.md`
