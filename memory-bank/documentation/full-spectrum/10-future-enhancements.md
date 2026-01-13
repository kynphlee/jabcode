# Future Enhancements
**JABCode Roadmap and Planned Improvements** 🚀

*A forward-looking view of planned enhancements, known limitations to address, and future capabilities for the JABCode Panama wrapper.*

---

## Overview

JABCode is stable and production-ready for color modes 4-128. This roadmap outlines planned improvements organized by quarter and priority.

**Current Status (January 2026):**
- ✅ Single-symbol encoding: Fully functional
- ✅ All color modes 4-128: Stable with 100% test pass rate
- ✅ 75% instruction coverage: Comprehensive testing
- ⚠️ 256-color mode: Known malloc corruption
- ⚠️ Cascaded multi-symbol: API limitation

---

## Q1 2026: Cascaded Multi-Symbol Encoding

### Goal

Enable full support for multi-symbol JABCode cascades (1 primary + N secondary symbols).

### Current Limitation

```java
// Can set symbol count but not versions
var config = Config.builder()
    .symbolNumber(2)  // Request 2 symbols
    .build();

encoder.encodeToPNG(largeData, output, config);
// ❌ Fails: "Incorrect symbol version for symbol 0"
```

The C encoder requires explicit symbol version configuration, which our Java API doesn't expose.

### Planned API Extension

**Add SymbolVersion class:**
```java
public static class SymbolVersion {
    public final int x;  // Width version (1-32)
    public final int y;  // Height version (1-32)
    
    public SymbolVersion(int x, int y) {
        if (x < 1 || x > 32 || y < 1 || y > 32) {
            throw new IllegalArgumentException("Version must be 1-32");
        }
        this.x = x;
        this.y = y;
    }
}
```

**Extend Config.Builder:**
```java
public static class Builder {
    private List<SymbolVersion> symbolVersions;
    
    public Builder symbolVersions(List<SymbolVersion> versions) {
        if (versions != null && versions.size() != symbolNumber) {
            throw new IllegalArgumentException(
                "Version count must match symbol count");
        }
        this.symbolVersions = versions;
        return this;
    }
}
```

**Usage:**
```java
var config = Config.builder()
    .colorNumber(64)
    .symbolNumber(2)
    .symbolVersions(List.of(
        new SymbolVersion(10, 10),  // Primary symbol: 10×10
        new SymbolVersion(8, 8)      // Secondary symbol: 8×8
    ))
    .eccLevel(6)
    .build();

encoder.encodeToPNG(largeData, output, config);  // ✅ Works!
```

### Implementation Tasks

1. **Define SymbolVersion class** (2 hours)
   - Value object with validation
   - Immutable design
   - JavaDoc documentation

2. **Extend Config.Builder** (2 hours)
   - Add symbolVersions field and builder method
   - Validate version count matches symbol count
   - Add tests for validation

3. **Update JABCodeEncoder** (4 hours)
   - Write versions to native structure
   - Handle memory layout correctly
   - Account for platform differences (32-bit vs 64-bit)

4. **Comprehensive testing** (8 hours)
   - Test 2-symbol cascades (all color modes)
   - Test 3-symbol cascades
   - Test up to 5-symbol cascades
   - Edge cases (mismatched counts, invalid versions)

5. **Documentation** (2 hours)
   - Update getting started guide
   - Add cascaded encoding examples
   - Document best practices

**Estimated effort:** 18 hours  
**Priority:** High  
**Complexity:** Medium  
**Risk:** Low (well-understood requirement)

### Success Criteria

- ✅ Can encode 2-symbol cascade with explicit versions
- ✅ Can encode up to 61-symbol cascade (spec maximum)
- ✅ All existing tests still pass (no regression)
- ✅ New tests covering cascaded scenarios
- ✅ Documentation updated with examples

---

## Q1 2026: Fix 256-Color Mode Malloc Crash

### Goal

Resolve the malloc corruption that prevents 256-color mode from working.

### Current Issue

```bash
[ENCODER] Config: colorNumber=256, eccLevel=7, symbolNumber=1
[ENCODER] After createEncode: color_number in struct = 256
[ENCODER] ECC level set: requested=7, actual=7
malloc(): invalid size (unsorted)
Aborted (core dumped)
```

**Crash point:** Between ECC setup and masking phase  
**Suspected cause:** Metadata offset calculations assume ≤64 colors

### Investigation Plan

**Phase 1: Memory Sanitizer Analysis** (4 hours)

Rebuild with AddressSanitizer to find exact corruption point:
```bash
cd src/jabcode
make clean
CFLAGS="-fsanitize=address -fsanitize=undefined -g" make

LD_LIBRARY_PATH=lib java ... ColorMode7Test
```

Expected output will show exact line where invalid write occurs.

**Phase 2: Root Cause Analysis** (4 hours)

Based on ASan output:
1. Identify the overflowing write
2. Trace back to offset calculation
3. Understand why calculation fails for 256 colors
4. Document the design assumption being violated

**Phase 3: Fix Implementation** (8 hours)

Likely fixes:
1. **Update offset formula:**
   ```c
   // Current (assumes ≤64 colors)
   colors_to_skip = MIN(enc->color_number, 64) - 2;
   
   // Fixed (handles 256 colors)
   colors_to_skip = (enc->color_number <= 64) 
       ? enc->color_number - 2
       : calculate256ColorOffset(enc);
   ```

2. **Increase matrix allocation:**
   ```c
   // May need larger matrix for 256-color metadata
   if (enc->color_number == 256) {
       matrix_size *= METADATA_EXPANSION_FACTOR;
   }
   ```

3. **Adjust palette placement:**
   ```c
   // Palette interpolation for 256 colors might need special handling
   if (enc->color_number == 256) {
       use256ColorPalettePlacement(enc);
   }
   ```

**Phase 4: Testing** (4 hours)

1. ColorMode7Test should pass all tests
2. Regression: Ensure 4-128 color modes still work
3. Stress test with large data
4. Verify with various ECC levels

**Estimated effort:** 20 hours  
**Priority:** Medium  
**Complexity:** High  
**Risk:** Medium (could uncover additional issues)

### Success Criteria

- ✅ ColorMode7Test passes all tests
- ✅ Can encode and decode 256-color JABCodes
- ✅ No regression in other color modes
- ✅ Memory sanitizer shows no errors
- ✅ Performance acceptable (<2× slower than 128-color)

### Fallback Plan

If fix proves too complex or risky:
- Document 256-color as "experimental, not recommended"
- Suggest 128-color mode as alternative (87% of theoretical capacity)
- Revisit after gaining more real-world usage data

---

## Q2 2026: Advanced Configuration Options

### Per-Symbol ECC Levels

**Current:** Single ECC level applies to all symbols in cascade  
**Desired:** Different ECC per symbol

**Use case:** Primary symbol with high ECC (must decode), slaves with lower ECC (optional data).

**API:**
```java
var config = Config.builder()
    .symbolNumber(3)
    .symbolVersions(List.of(
        new SymbolVersion(10, 10),
        new SymbolVersion(8, 8),
        new SymbolVersion(8, 8)
    ))
    .eccLevels(List.of(7, 5, 5))  // ← Per-symbol ECC
    .build();
```

**Effort:** 8 hours  
**Priority:** Low  
**Complexity:** Medium

### Custom Palette Specification

**Current:** Encoder generates palette automatically  
**Desired:** Specify custom colors for branding/special requirements

**API:**
```java
// Define custom 8-color palette
List<Color> customPalette = List.of(
    new Color(255, 0, 0),      // Brand red
    new Color(0, 100, 200),    // Brand blue
    // ... 6 more colors
);

var config = Config.builder()
    .colorNumber(8)
    .customPalette(customPalette)  // ← Override default
    .build();
```

**Effort:** 12 hours  
**Priority:** Low  
**Complexity:** Medium  
**Risk:** Low (optional feature, doesn't affect defaults)

### Symbol Position Control

**Current:** Symbol positions auto-calculated  
**Desired:** Explicit positioning for complex layouts

**Use case:** Multi-page documents with specific symbol placement.

**API:**
```java
var config = Config.builder()
    .symbolNumber(4)
    .symbolPositions(List.of(0, 1, 2, 3))  // Explicit order
    .build();
```

**Effort:** 6 hours  
**Priority:** Low  
**Complexity:** Low

---

## Q2 2026: Performance Optimization

### Goal

Improve encoding/decoding performance, especially for higher color modes.

### Profiling Plan

1. **Baseline measurements:**
   ```java
   @Benchmark
   public void encode64Color() {
       encoder.encodeToPNG(testData, output, config64);
   }
   ```

2. **Identify hotspots:**
   - Palette generation
   - Mask pattern evaluation
   - LDPC encoding/decoding
   - Color matching in decoder

3. **Optimization targets:**
   - Cache palette calculations
   - Parallelize mask evaluation
   - Optimize color distance calculations
   - Use SIMD for pixel operations

### Expected Improvements

| Operation | Current | Target | Improvement |
|-----------|---------|--------|-------------|
| 8-color encode | 20ms | 15ms | 25% faster |
| 64-color encode | 40ms | 28ms | 30% faster |
| 128-color encode | 50ms | 32ms | 36% faster |
| 8-color decode | 35ms | 25ms | 29% faster |
| 64-color decode | 70ms | 45ms | 36% faster |

**Effort:** 24 hours  
**Priority:** Medium  
**Complexity:** Medium-High

---

## Q3 2026: Enhanced Error Handling

### Specific Exception Types

**Current:** Generic exceptions with string messages  
**Desired:** Typed exceptions with structured error information

**API:**
```java
// Current
try {
    encoder.encodeToPNG(data, output, config);
} catch (Exception e) {
    // What went wrong? Have to parse message string
}

// Desired
try {
    encoder.encodeToPNG(data, output, config);
} catch (InvalidConfigException e) {
    // Config validation failed
    System.err.println("Invalid: " + e.getParameter());
} catch (DataTooLargeException e) {
    // Data doesn't fit
    System.err.println("Need version >= " + e.getRequiredVersion());
} catch (EncodingException e) {
    // Encoding failed
    System.err.println("Reason: " + e.getReason());
}
```

**Effort:** 10 hours  
**Priority:** Medium  
**Complexity:** Low

### Better Error Messages

Enhance native error reporting to provide actionable information:

**Current:**
```
JABCode Error: Encoding failed
```

**Desired:**
```
JABCode Error: Data too large for configuration
  Data size: 450 bytes
  Max capacity: 350 bytes (64-color, 10×10 version, ECC level 5)
  Suggestions:
    - Increase color mode to 128-color (capacity: ~525 bytes)
    - Increase symbol version to 12×12 (capacity: ~500 bytes)
    - Reduce ECC level to 3 (capacity: ~480 bytes)
```

**Effort:** 16 hours  
**Priority:** Medium  
**Complexity:** Medium

---

## Q3 2026: Quality of Life Improvements

### Convenience Methods

**Auto-detect optimal configuration:**
```java
// Encoder analyzes data and picks best config
Config optimal = JABCodeEncoder.recommendConfig(data);
// Returns config optimized for data characteristics

encoder.encodeToPNG(data, output, optimal);
```

**Fluent encoding:**
```java
// One-liner for simple cases
JABCodeEncoder.quick()
    .encode("Hello, World!")
    .toFile("output.png");
```

**Batch encoding:**
```java
// Encode multiple messages efficiently
List<String> messages = List.of("msg1", "msg2", "msg3");
List<Path> outputs = encoder.encodeBatch(messages, outputDir, config);
```

**Effort:** 12 hours  
**Priority:** Low  
**Complexity:** Low

### Sample Code Generator

**Tool to generate sample codes for testing:**
```java
SampleGenerator.create()
    .withColorModes(4, 8, 16, 32, 64, 128)
    .withEccLevels(3, 5, 7)
    .withMessages("Simple", "Unicode: 你好", "Long text...")
    .generateTo("samples/");

// Creates comprehensive sample set for testing
```

**Effort:** 8 hours  
**Priority:** Low  
**Complexity:** Low

---

## Q4 2026: Production Readiness

### CI/CD Pipeline

**Automated testing:**
```yaml
# .github/workflows/ci.yml
name: JABCode CI

on: [push, pull_request]

jobs:
  test:
    strategy:
      matrix:
        os: [ubuntu-latest, macos-latest]
        java: [21, 22]
    
    steps:
      - name: Checkout
        uses: actions/checkout@v3
      
      - name: Setup Java
        uses: actions/setup-java@v3
        with:
          java-version: ${{ matrix.java }}
      
      - name: Build Native Library
        run: cd src/jabcode && make
      
      - name: Run Tests
        run: mvn test jacoco:report
      
      - name: Coverage Check
        run: mvn jacoco:check
      
      - name: Upload Results
        uses: codecov/codecov-action@v3
```

**Effort:** 8 hours  
**Priority:** High  
**Complexity:** Low

### Release Automation

**Maven Central deployment:**
```bash
# Automated release process
mvn release:prepare release:perform

# Publishes to Maven Central
# Updates version numbers
# Creates GitHub release
```

**Effort:** 12 hours  
**Priority:** High  
**Complexity:** Medium

### Comprehensive Documentation

- **API JavaDoc:** Complete coverage of all public APIs
- **Integration guides:** Spring Boot, Android, etc.
- **Migration guide:** From JNI wrapper to Panama
- **Performance tuning:** Best practices guide
- **Security considerations:** Color accuracy attacks, etc.

**Effort:** 24 hours  
**Priority:** High  
**Complexity:** Low

---

## Long-Term Vision (2027+)

### Additional Color Modes

Research into alternative color spaces:
- **LAB color space:** Better perceptual uniformity
- **Custom gamuts:** Optimized for specific displays/printers
- **Adaptive color selection:** Based on ambient lighting

### Hardware Acceleration

Leverage GPU for intensive operations:
- Palette generation (parallel color optimization)
- Mask evaluation (parallel penalty calculation)
- LDPC encoding/decoding (matrix operations)

Expected speedup: 5-10× for large codes.

### Mobile Optimization

Android/iOS optimizations:
- Smaller library footprint
- Lower memory usage
- Camera integration
- Real-time scanning

### Machine Learning Integration

ML-assisted features:
- Image quality prediction
- Optimal configuration suggestion
- Automatic error recovery
- Quality assessment

---

## Priority Matrix

### High Priority (Next 3 Months)

1. **Cascaded multi-symbol encoding** - Completes core functionality
2. **256-color malloc fix** - Removes major known limitation
3. **Enhanced error handling** - Better developer experience
4. **Performance optimization** - Improves user experience

**Deferred:** CI/CD pipeline and release automation (local testing and deployment sufficient for now)

### Medium Priority (3-6 Months)

1. **Performance optimization** - Improves user experience
2. **Enhanced error handling** - Better developer experience
3. **Advanced configuration** - Power user features

### Low Priority (6-12 Months)

1. **Convenience methods** - Quality of life
2. **Custom palettes** - Niche requirements
3. **Sample generator** - Testing utility

---

## Community Contributions

### Areas Welcoming Contributions

**Easy (Good first issues):**
- Documentation improvements
- Additional test cases
- Sample code examples
- Bug reports with reproducers

**Medium:**
- Performance benchmarking
- Platform testing (different Linux distros, macOS versions)
- Integration examples (Spring, Micronaut, etc.)
- Error message improvements

**Advanced:**
- 256-color mode investigation
- Native library optimizations
- Alternative binding strategies
- Cross-platform build improvements

### Contribution Guidelines

1. **Start with an issue:** Discuss approach before coding
2. **Follow style:** Match existing code patterns
3. **Include tests:** All new features need tests
4. **Update docs:** User-facing changes need documentation
5. **Keep focused:** One feature per PR

---

## Research Areas

### Questions to Explore

1. **What's the sweet spot for ECC vs capacity?**
   - Empirical study with real-world damage scenarios
   - Recommendation engine based on use case

2. **Can we predict decoding success from image quality metrics?**
   - ML model trained on successful/failed decodes
   - Pre-flight check before attempting decode

3. **What color modes actually get used?**
   - Telemetry (opt-in) to understand real usage
   - Optimize for common cases

4. **How does JABCode compare to alternatives?**
   - Benchmarks vs QR codes, Data Matrix, etc.
   - Use case recommendations

---

## Deprecation Policy

### No Planned Deprecations

All current APIs are stable. If we need to make breaking changes:

1. **Announce:** 6 months advance notice
2. **Mark @Deprecated:** With suggested alternative
3. **Maintain:** Support deprecated API for 1 year
4. **Remove:** Only in major version bump

### Semantic Versioning

- **Major (X.0.0):** Breaking changes
- **Minor (x.X.0):** New features, backward compatible
- **Patch (x.x.X):** Bug fixes only

Current: 2.0.0  
Next minor: 2.1.0 (with cascaded encoding)  
Next major: 3.0.0 (only if breaking changes needed)

---

## Success Metrics

### Technical Metrics

- **Test coverage:** Maintain ≥75% instruction coverage
- **Performance:** No regression in existing benchmarks
- **Reliability:** 99.9%+ test pass rate in CI
- **Documentation:** 100% public API JavaDoc coverage

### Adoption Metrics

- **Maven downloads:** Track monthly growth
- **GitHub stars:** Measure interest
- **Issues/PRs:** Community engagement
- **Integration examples:** Ecosystem adoption

---

## Get Involved

### Ways to Help

**Use it:** Try JABCode in your projects, report issues  
**Test it:** Different platforms, edge cases, stress tests  
**Document it:** Tutorials, blog posts, videos  
**Code it:** Bug fixes, features, optimizations  
**Share it:** Spread the word, write reviews

### Stay Updated

- **GitHub:** Watch repository for releases
- **Documentation:** Check for updates quarterly
- **Roadmap:** Review this document (updated monthly)

---

## Conclusion

JABCode is stable and production-ready for most use cases. The roadmap focuses on:

1. **Completing core features** (cascaded encoding)
2. **Removing known limitations** (256-color fix)
3. **Improving developer experience** (better errors, convenience methods)
4. **Ensuring quality** (CI/CD, automated releases)

**Timeline summary:**
- Q1 2026: Core completion (cascading, 256-color)
- Q2 2026: Performance & advanced features
- Q3 2026: Polish & quality of life
- Q4 2026: Production hardening

**Get involved!** This is an open roadmap. Priorities can shift based on community feedback and real-world usage patterns.

---

## Related Documentation

- **[01-getting-started.md](01-getting-started.md)** - Start using JABCode today
- **[06-api-design-evolution.md](06-api-design-evolution.md)** - API history and current state
- **[09-troubleshooting-guide.md](09-troubleshooting-guide.md)** - Fix issues you encounter

---

*"The best way to predict the future is to invent it."* - Alan Kay

We're inventing a future where color barcodes are reliable, accessible, and powerful. Join us! 🚀
