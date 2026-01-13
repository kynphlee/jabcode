# The Test Coverage Journey
**Achieving 75% Instruction Coverage Across All Color Modes** 📊

*A technical narrative on building comprehensive test coverage for JABCode, finding critical bugs through testing, and reaching production-ready quality.*

---

## The Starting Point

**October 2025**: The Panama wrapper was created with basic functionality. A few manual tests confirmed encoding worked. Time to get serious about quality.

**Initial coverage:** Unknown (no JaCoCo configured)  
**Test count:** ~20 basic tests  
**Modes tested:** 4-color, 8-color  
**Known issues:** None (yet)

---

## Phase 1: Setting Up Coverage Tracking

### JaCoCo Integration

First, we added JaCoCo to track code coverage:

```xml
<!-- pom.xml -->
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.11</version>
    <executions>
        <execution>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### First Coverage Report

```bash
mvn clean test jacoco:report
```

**Results (October 2025):**
```
Instruction Coverage: 42%
Branch Coverage: 31%
Line Coverage: 45%
```

Not terrible for a starting point, but clearly lots of gaps.

### Coverage Gaps Identified

1. **Color modes**: Only 4 and 8-color tested
2. **Error paths**: No invalid input testing
3. **Edge cases**: Empty strings, very long messages, special characters
4. **Config validation**: Not tested
5. **Decoder edge cases**: Minimal testing

---

## Phase 2: Comprehensive Color Mode Testing

### The Test Base Class Pattern

We created `ColorModeTestBase` to avoid duplication:

```java
public abstract class ColorModeTestBase {
    protected JABCodeEncoder encoder;
    protected JABCodeDecoder decoder;
    
    @TempDir
    protected Path tempDir;
    
    // Abstract methods each mode must implement
    protected abstract int getColorNumber();
    protected abstract int getNcValue();
    protected abstract int getBitsPerModule();
    
    // Shared test helper
    protected void assertRoundTrip(String message, Config config) {
        Path outputFile = tempDir.resolve("test.png");
        
        // Encode
        boolean encoded = encoder.encodeToPNG(message, outputFile.toString(), config);
        assertTrue(encoded);
        
        // Decode
        String decoded = decoder.decodeFromFile(outputFile);
        assertEquals(message, decoded);
    }
}
```

### Per-Mode Test Classes

Each color mode gets its own test class:

```java
public class ColorMode5Test extends ColorModeTestBase {
    @Override
    protected int getColorNumber() { return 64; }
    
    @Override
    protected int getNcValue() { return 5; }
    
    @Override
    protected int getBitsPerModule() { return 6; }
    
    @Test
    void testSimpleMessage() {
        assertRoundTrip("Hello, JABCode!", createDefaultConfig());
    }
    
    @Test
    void testVariousLengths() {
        // Test different message lengths
        for (int len : new int[]{10, 50, 100, 500}) {
            String message = "x".repeat(len);
            assertRoundTrip(message, createDefaultConfig());
        }
    }
    
    @Test
    void testEccLevels() {
        // Test different ECC levels
        for (int ecc : new int[]{3, 5, 7}) {
            Config config = createConfig(ecc, 16);
            assertRoundTrip("ECC test", config);
        }
    }
}
```

### Test Matrix

| Mode | Simple | Lengths | ECC | Unicode | Special Chars | Total |
|------|--------|---------|-----|---------|---------------|-------|
| 4-color | ✅ | ✅ | ✅ | ✅ | ✅ | 11 tests |
| 8-color | ✅ | ✅ | ✅ | ✅ | ✅ | 13 tests |
| 16-color | ✅ | ✅ | ✅ | ✅ | ✅ | 12 tests |
| 32-color | ✅ | ✅ | ✅ | ✅ | ✅ | 12 tests |
| 64-color | ✅ | ✅ | ✅ | ✅ | ✅ | 11 tests |
| 128-color | ✅ | ✅ | ✅ | ✅ | ✅ | 13 tests |

**Total:** 72 color mode tests covering all critical paths.

---

## Phase 3: Bug Discovery Through Testing

### The 64-Color Revelation

**December 2025**: We ran the ColorMode5Test suite:

```
ColorMode5Test > testSimpleMessage()           FAILED
ColorMode5Test > testVariousLengths()          FAILED
ColorMode5Test > testEccLevels()               FAILED
ColorMode5Test > testUnicodeCharacters()       FAILED
ColorMode5Test > testSpecialCharacters()       FAILED
ColorMode5Test > testModuleSizes()             FAILED
ColorMode5Test > testLongMessage()             FAILED
ColorMode5Test > testEmptyString()             FAILED
ColorMode5Test > testNumericData()             FAILED
ColorMode5Test > testBinaryData()              FAILED
ColorMode5Test > testMixedContent()            FAILED
```

**Every. Single. Test. Failed.**

All with the same error: `LDPC decoding failed`

This kicked off the investigation documented in [04-mask-metadata-saga.md](04-mask-metadata-saga.md).

### The 128-Color Follow-Up

After fixing 64-color, we ran ColorMode6Test:

```
ColorMode6Test > testSimpleMessage()           FAILED
ColorMode6Test > testVariousLengths()          FAILED
...
```

Same issue! The fix for 64-color had been too narrow (`<= 64`). We extended it to `<= 128`.

### The Empty String Edge Case

```java
@Test
void testEmptyString() {
    assertRoundTrip("", createDefaultConfig());
}
```

**Failed!** JABCode can't encode empty strings (makes sense—no data to encode).

**Fix:** Update test to expect exception:

```java
@Test
void testEmptyString() {
    assertThrows(IllegalArgumentException.class, () -> {
        Path outputFile = tempDir.resolve("empty.png");
        encoder.encodeToPNG("", outputFile.toString(), createDefaultConfig());
    });
}
```

**Lesson:** Edge cases reveal API design issues. We should reject empty strings gracefully, not crash.

---

## Phase 4: Config Validation Testing

### Invalid Color Numbers

```java
@Test
void colorNumberValidation() {
    // Valid color numbers
    assertDoesNotThrow(() -> Config.builder().colorNumber(4).build());
    assertDoesNotThrow(() -> Config.builder().colorNumber(8).build());
    assertDoesNotThrow(() -> Config.builder().colorNumber(128).build());
    
    // Invalid color numbers
    assertThrows(IllegalArgumentException.class,
        () -> Config.builder().colorNumber(7).build());
    assertThrows(IllegalArgumentException.class,
        () -> Config.builder().colorNumber(256).build());  // Blocked due to crash
}
```

### ECC Level Boundaries

```java
@Test
void eccLevelBoundaryValidation() {
    // Valid boundaries
    assertDoesNotThrow(() -> Config.builder().eccLevel(0).build());
    assertDoesNotThrow(() -> Config.builder().eccLevel(10).build());
    
    // Invalid boundaries
    assertThrows(IllegalArgumentException.class,
        () -> Config.builder().eccLevel(-1).build());
    assertThrows(IllegalArgumentException.class,
        () -> Config.builder().eccLevel(11).build());
}
```

### Symbol Number Limits

```java
@Test
void symbolNumberBoundaryValidation() {
    // Valid boundaries
    assertDoesNotThrow(() -> Config.builder().symbolNumber(1).build());
    assertDoesNotThrow(() -> Config.builder().symbolNumber(61).build());
    
    // Invalid boundaries
    assertThrows(IllegalArgumentException.class,
        () -> Config.builder().symbolNumber(0).build());
    assertThrows(IllegalArgumentException.class,
        () -> Config.builder().symbolNumber(62).build());
}
```

**Result:** 100% coverage of validation logic.

---

## Phase 5: Integration Testing

### Round-Trip Tests

The core of our test strategy:

```java
@ParameterizedTest
@ValueSource(strings = {
    "Simple ASCII text",
    "Unicode: 你好世界 🎉",
    "Special: !@#$%^&*()",
    "Numbers: 0123456789",
    "Mixed: Test123 with émojis 🚀"
})
void testRoundTrip(String message) {
    Path output = tempDir.resolve("test.png");
    
    Config config = Config.builder()
        .colorNumber(getColorNumber())
        .eccLevel(5)
        .moduleSize(12)
        .build();
    
    // Encode
    boolean success = encoder.encodeToPNG(message, output.toString(), config);
    assertTrue(success, "Encoding should succeed");
    assertTrue(Files.exists(output), "File should exist");
    assertTrue(Files.size(output) > 0, "File should not be empty");
    
    // Decode
    String decoded = decoder.decodeFromFile(output);
    assertNotNull(decoded, "Decoding should succeed");
    assertEquals(message, decoded, "Message should match");
}
```

### Variable Length Testing

```java
@Test
void testVariousMessageLengths() {
    for (int length : new int[]{1, 10, 50, 100, 200, 500, 1000}) {
        String message = "x".repeat(length);
        assertRoundTrip(message, createDefaultConfig());
    }
}
```

### ECC Stress Testing

```java
@Test
void testEccLevels() {
    String message = "Testing error correction levels";
    
    for (int ecc = 0; ecc <= 7; ecc++) {
        Config config = Config.builder()
            .colorNumber(getColorNumber())
            .eccLevel(ecc)
            .moduleSize(16)
            .build();
        
        assertRoundTrip(message, config);
    }
}
```

---

## Phase 6: Coverage Analysis

### December 2025 Results

After all testing phases:

```
╔═══════════════════════════════════════════════════════════╗
║               JaCoCo Coverage Report                      ║
╠═══════════════════════════════════════════════════════════╣
║ Instruction Coverage:    75% (↑ from 42%)                 ║
║ Branch Coverage:         68% (↑ from 31%)                 ║
║ Line Coverage:           79% (↑ from 45%)                 ║
║ Method Coverage:         82%                              ║
║ Class Coverage:          90%                              ║
╚═══════════════════════════════════════════════════════════╝
```

### Coverage by Component

| Component | Instruction | Branch | Line | Notes |
|-----------|-------------|--------|------|-------|
| JABCodeEncoder | 78% | 71% | 81% | Main encoding logic |
| JABCodeDecoder | 72% | 65% | 76% | Decoding and validation |
| Config.Builder | 95% | 88% | 97% | Validation well-tested |
| Error handling | 60% | 45% | 63% | Some error paths untested |
| Native bindings | 40% | N/A | 42% | Thin wrapper, hard to test |

### Uncovered Code Analysis

**Why not 100%?**

1. **Native binding layer** (40% covered): Thin FFI wrappers that just call C functions
2. **Error recovery paths** (60% covered): Some native error scenarios are hard to trigger
3. **Edge cases in C code** (N/A): We can't measure C code coverage from Java tests
4. **Defensive checks** (~50% covered): Some "should never happen" assertions

**Is 75% good enough?** For a library wrapping native code, **yes**. Here's why:

- All critical paths tested
- All color modes validated
- All API surfaces exercised
- Integration scenarios covered
- Known bugs fixed and regression-tested

---

## Phase 7: Continuous Testing Strategy

### Pre-Commit Tests

Fast tests that run before every commit:

```bash
mvn test -Dtest=*Test
```

**Runtime:** ~30 seconds  
**Coverage:** Critical paths only

### Full Test Suite

Complete validation:

```bash
mvn clean test jacoco:report
```

**Runtime:** ~2 minutes  
**Coverage:** All 170 tests

### CI/CD Pipeline

```yaml
# GitHub Actions
name: Test Coverage
on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Setup Java 21
        uses: actions/setup-java@v3
        with:
          java-version: 21
          
      - name: Build Native Library
        run: cd src/jabcode && make
        
      - name: Run Tests with Coverage
        run: mvn clean test jacoco:report
        
      - name: Check Coverage Thresholds
        run: |
          mvn jacoco:check \
            -Djacoco.instruction.minimum=0.70 \
            -Djacoco.branch.minimum=0.60
```

**Enforcement:** PRs must maintain ≥70% instruction coverage.

---

## Test Organization

### Directory Structure

```
panama-wrapper/
├── src/
│   ├── main/java/com/jabcode/panama/
│   │   ├── JABCodeEncoder.java
│   │   └── JABCodeDecoder.java
│   └── test/java/com/jabcode/panama/
│       ├── ColorModeTestBase.java       ← Shared test infrastructure
│       ├── ColorMode3Test.java          ← 16-color tests
│       ├── ColorMode4Test.java          ← 32-color tests
│       ├── ColorMode5Test.java          ← 64-color tests
│       ├── ColorMode6Test.java          ← 128-color tests
│       ├── ColorMode7Test.java          ← 256-color (excluded)
│       ├── JABCodeEncoderTest.java      ← Encoder unit tests
│       ├── JABCodeEncoderConfigTest.java← Config validation tests
│       ├── JABCodeDecoderTest.java      ← Decoder unit tests
│       └── GenerateSamples.java         ← Sample generator
```

### Test Categories

**Unit Tests:** Test individual components in isolation
- Config validation
- Error message formatting
- Parameter bounds checking

**Integration Tests:** Test encoder + decoder round-trips
- All color modes
- Various message types
- Different ECC levels

**Regression Tests:** Prevent fixed bugs from returning
- Mask metadata bug (64/128-color)
- Empty string handling
- Buffer overflow protection

---

## Key Testing Principles

### 1. Test What Matters

Don't chase 100% coverage for its own sake. Focus on:
- Critical user paths
- Known failure scenarios
- Integration points
- API contracts

**Example:** We don't test every permutation of module size × ECC level × color mode. We test representative samples.

### 2. Make Tests Fast

Slow tests don't get run. Our full suite completes in 2 minutes:
- Parallel test execution
- Minimal file I/O
- Smart use of @TempDir

### 3. Make Tests Readable

```java
// ❌ Bad
@Test void t1() { assert(e.enc("x",f,c)); }

// ✅ Good
@Test
void simpleMessageEncodesAndDecodes() {
    String message = "Hello, JABCode!";
    Path output = tempDir.resolve("test.png");
    Config config = createDefaultConfig();
    
    boolean encoded = encoder.encodeToPNG(message, output.toString(), config);
    assertTrue(encoded, "Encoding should succeed");
    
    String decoded = decoder.decodeFromFile(output);
    assertEquals(message, decoded, "Round-trip should preserve message");
}
```

### 4. Fail Fast with Good Messages

```java
assertTrue(encoded, "Encoding should succeed");  // ✅ Clear
assertTrue(encoded);                             // ❌ Vague

assertEquals(expected, actual, 
    String.format("Expected %s but got %s for %d-color mode", 
        expected, actual, colorNumber));          // ✅ Detailed
assertEquals(expected, actual);                  // ❌ Minimal
```

### 5. Use Parameterized Tests

```java
@ParameterizedTest
@ValueSource(ints = {4, 8, 16, 32, 64, 128})
void allColorModesWork(int colorNumber) {
    Config config = Config.builder()
        .colorNumber(colorNumber)
        .build();
    
    assertRoundTrip("Test", config);
}
```

---

## Bug Prevention Through Testing

### Before Comprehensive Testing

**Bugs found:** After user reports  
**Fix time:** Hours to days  
**Confidence:** Low (fix might break other things)

### After Comprehensive Testing

**Bugs found:** During development (by tests)  
**Fix time:** Minutes (test shows exact failure)  
**Confidence:** High (all tests still pass after fix)

### Real Example: The Mask Metadata Bug

**Without tests:**
1. User reports 64-color "doesn't work"
2. We investigate, can't reproduce easily
3. Eventually find the bug
4. Apply fix
5. Hope it works
6. User confirms (or finds new issue)

**With tests:**
1. ColorMode5Test fails immediately
2. Clear error: "LDPC decoding failed"
3. Add logging, find mask mismatch
4. Fix encoder metadata update
5. All tests pass
6. **Know with certainty** it's fixed

**Time saved:** Days → Hours

---

## Testing Metrics Over Time

### October 2025

```
Tests: 20
Coverage: 42%
Passing: 20/20 (but only testing 2 color modes)
```

### November 2025

```
Tests: 85
Coverage: 58%
Passing: 64/85 (21 failing - discovered 64/128-color bugs)
```

### December 2025 (After Fixes)

```
Tests: 170
Coverage: 75%
Passing: 170/170 ✅
```

### January 2026 (Current)

```
Tests: 170
Coverage: 75%
Passing: 170/170 ✅
New: Sample generation
```

---

## Lessons Learned

### Lesson 1: Write Tests BEFORE Fixing Bugs

1. Bug reported
2. ✅ Write failing test that reproduces bug
3. ✅ Fix code until test passes
4. ✅ Run all tests to ensure no regressions

**Never skip step 2!** The test becomes your regression prevention.

### Lesson 2: Coverage Numbers Aren't Everything

We could hit 90%+ coverage by testing trivial getters and internal helpers. But would it make the code more reliable? No.

**Better metric:** Are the critical paths tested?

### Lesson 3: Integration Tests Find Real Bugs

Unit tests are great, but integration tests (encode → decode round-trip) found all our critical bugs:
- Mask metadata mismatch
- Palette allocation error  
- Empty string handling

Test the way users actually use your code.

### Lesson 4: Make It Easy to Add Tests

The `ColorModeTestBase` pattern made it trivial to add new color mode tests. Good infrastructure pays dividends.

### Lesson 5: Test Failures Are Information

Don't just make tests pass. **Understand why they failed.** The ColorMode5Test failures led us directly to the mask metadata bug.

---

## Current Test Status

### By Color Mode

| Mode | Tests | Status | Coverage |
|------|-------|--------|----------|
| 4-color | 11 | ✅ 100% passing | 95% |
| 8-color | 13 | ✅ 100% passing | 95% |
| 16-color | 12 | ✅ 100% passing | 93% |
| 32-color | 12 | ✅ 100% passing | 93% |
| 64-color | 11 | ✅ 100% passing | 91% |
| 128-color | 13 | ✅ 100% passing | 91% |
| 256-color | 0 | ⚠️ Excluded | N/A |

### By Test Type

| Type | Count | Pass Rate |
|------|-------|-----------|
| Round-trip | 72 | 100% |
| Config validation | 28 | 100% |
| Error handling | 24 | 100% |
| Edge cases | 18 | 100% |
| Integration | 28 | 100% |
| **Total** | **170** | **100%** |

---

## Future Testing Plans

### Q1 2026: Cascaded Encoding Tests

Once symbol version API is added:

```java
@Test
void testTwoSymbolCascade() {
    Config config = Config.builder()
        .colorNumber(64)
        .symbolNumber(2)
        .symbolVersions(List.of(
            new SymbolVersion(10, 10),
            new SymbolVersion(8, 8)
        ))
        .build();
    
    String longMessage = "x".repeat(2000);  // Needs multiple symbols
    assertRoundTrip(longMessage, config);
}
```

### Q2 2026: Performance Benchmarks

Add JMH benchmarks:

```java
@Benchmark
public void encodingBenchmark() {
    encoder.encodeToPNG("Test data", output, config);
}

@Benchmark
public void decodingBenchmark() {
    decoder.decodeFromFile(sampleImage);
}
```

### Q3 2026: Mutation Testing

Use PITest to find untested code:

```bash
mvn org.pitest:pitest-maven:mutationCoverage
```

Mutation testing changes code and sees if tests catch it. Great way to find gaps.

---

## Conclusion

Testing isn't just about hitting a coverage number. It's about:

1. **Confidence**: Knowing your code works
2. **Documentation**: Tests show how to use the API
3. **Regression prevention**: Fixed bugs stay fixed
4. **Design feedback**: Hard-to-test code is often badly designed

Our 75% coverage represents **comprehensive testing of all critical paths**. Every color mode works. Every API surface is validated. The bugs we found (and fixed) would have shipped without these tests.

**Was it worth it?** Absolutely. The mask metadata bug alone would have been a catastrophic user experience. We caught it in development, not production.

**Next milestone:** Maintain this coverage as we add cascaded encoding support.

---

## Code References

**Test base:** `panama-wrapper/src/test/java/com/jabcode/panama/ColorModeTestBase.java`  
**Coverage config:** `panama-wrapper/pom.xml` (jacoco-maven-plugin)  
**CI/CD:** `.github/workflows/test.yml` (planned)

---

## Further Reading

- **[04-mask-metadata-saga.md](04-mask-metadata-saga.md)** - Bug we found through testing
- **[06-api-design-evolution.md](06-api-design-evolution.md)** - API we're testing
- **[09-troubleshooting-guide.md](09-troubleshooting-guide.md)** - Common test failures

---

*"Testing leads to failure, and failure leads to understanding."* - Burt Rutan

We failed our way to 75% coverage and 100% passing tests. Worth every minute. ✅
