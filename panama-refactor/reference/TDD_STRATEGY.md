# Test-Driven Development Strategy for JABCode Enhancement

**Version:** 1.0.0  
**Last Updated:** 2026-01-09

---

## 🎯 TDD Principles for This Project

### Core Philosophy
**Write the test first, make it pass, then refactor.**

Every feature, function, and subsystem must have tests written **before** implementation code.

---

## 🔄 TDD Cycle

### Red-Green-Refactor Loop

```
1. RED: Write failing test
   ├─ Define expected behavior
   ├─ Write assertion first
   └─ Run test (should fail)
   
2. GREEN: Make test pass
   ├─ Write minimum code
   ├─ Don't optimize yet
   └─ Run test (should pass)
   
3. REFACTOR: Improve code
   ├─ Clean up implementation
   ├─ Remove duplication
   └─ Run test (still passes)
   
4. REPEAT for next feature
```

---

## 📋 Testing Hierarchy

### 1. Unit Tests (C Functions)

**Location:** `src/jabcode/test/test_*.c`

**Coverage Target:** 100% of new functions

**Example:**
```c
// test/test_colorspace.c
void test_rgbToLab_black() {
    // RED: Write test first
    LAB result = rgbToLab(0, 0, 0);
    
    // Expected: L=0, a=0, b=0 for pure black
    assert_float_equal(result.L, 0.0f, 0.01f);
    assert_float_equal(result.a, 0.0f, 0.01f);
    assert_float_equal(result.b, 0.0f, 0.01f);
}

// Now implement rgbToLab() in colorspace.c
```

**Run Command:**
```bash
cd src/jabcode
make test
./test_colorspace
```

---

### 2. Integration Tests (Java)

**Location:** `panama-wrapper-itest/src/test/java/com/jabcode/panama/`

**Coverage Target:** 95%+ lines, 90%+ branches

**Example:**
```java
// Test before implementing feature
@Test
public void testLargerBarcodesHaveAlignmentPatterns() {
    // RED: Write test first
    Config config = Config.builder()
        .colorNumber(16)
        .eccLevel(7)
        .build();
    
    encoder.encodeToPNG("Test", "test.png", config);
    
    // Expected: Barcode should be >= 41x41 (version 6+)
    Metadata meta = decoder.readMetadata("test.png");
    assertTrue(meta.getVersion() >= 6, "Version should be >= 6 for alignment patterns");
}

// Now implement version forcing in JABCodeEncoder.java
```

**Run Command:**
```bash
cd panama-wrapper-itest
mvn test -Dtest=ColorMode3Test
```

---

### 3. End-to-End Tests

**Purpose:** Validate complete workflows

**Example:**
```java
@Test
public void testRoundTripWithAllSubsystems() {
    // Use all Phase 2 features
    Config config = Config.builder()
        .colorNumber(64)
        .errorAware(true)
        .adaptivePalette("desktop_lcd")
        .build();
    
    String original = "Complex message with 日本語 Unicode";
    encoder.encodeToPNG(original, "test.png", config);
    
    DecodeConfig decodeConfig = DecodeConfig.builder()
        .maxIterations(3)
        .build();
    
    String decoded = decoder.decode("test.png", decodeConfig);
    assertEquals(original, decoded);
}
```

---

## 🧪 Coverage Requirements

### Java Code (JaCoCo)

**Minimum Coverage:**
- **Lines:** 95%
- **Branches:** 90%
- **Methods:** 95%

**Generate Report:**
```bash
cd panama-wrapper
mvn clean test jacoco:report
open target/site/jacoco/index.html
```

**CI Integration:**
```xml
<!-- pom.xml -->
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.10</version>
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
        <execution>
            <id>check</id>
            <goals>
                <goal>check</goal>
            </goals>
            <configuration>
                <rules>
                    <rule>
                        <element>PACKAGE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.95</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

---

### C Code (gcov/lcov)

**Minimum Coverage:** 95% lines

**Generate Report:**
```bash
cd src/jabcode
make clean
make COVERAGE=1
make test
lcov --capture --directory . --output-file coverage.info
genhtml coverage.info --output-directory coverage_html
open coverage_html/index.html
```

**Makefile Addition:**
```makefile
# Add to Makefile
ifeq ($(COVERAGE),1)
    CFLAGS += --coverage
    LDFLAGS += --coverage
endif

coverage: test
	lcov --capture --directory . --output-file coverage.info
	genhtml coverage.info --output-directory coverage_html
	@echo "Coverage report in coverage_html/index.html"
```

---

## 📝 Test Organization

### Directory Structure

```
src/jabcode/test/
├── test_colorspace.c          # Unit: RGB↔LAB conversions
├── test_adaptive_palette.c    # Unit: Palette generation
├── test_error_profile.c       # Unit: Error learning
├── test_encoder_v2.c          # Unit: Error-aware encoding
├── test_hybrid_mode.c         # Unit: Hybrid encoding/decoding
├── test_iterative_decode.c    # Unit: Iterative refinement
├── test_enhance.c             # Unit: Image enhancement
├── data/                      # Test data
│   ├── rgb_lab_pairs.csv      # Reference conversions
│   ├── ciede2000_test_cases.csv
│   └── test_images/           # Sample barcodes
└── common.h                   # Shared test utilities

panama-wrapper-itest/src/test/java/com/jabcode/panama/
├── ColorMode0Test.java        # Integration: 4 colors
├── ColorMode1Test.java        # Integration: 8 colors
├── ColorMode2Test.java        # Integration: 16 colors
├── ColorMode3Test.java        # Integration: 32 colors
├── ColorMode4Test.java        # Integration: 64 colors
├── ColorMode5Test.java        # Integration: 128 colors
├── ColorMode6Test.java        # Integration: 256 colors
├── ColorModeTestBase.java     # Base test class
├── HybridModeTest.java        # Integration: Hybrid encoding
├── AdaptivePaletteTest.java   # Integration: Adaptive palettes
├── e2e/                       # End-to-end tests
│   ├── E2E_SimpleMessages.java
│   ├── E2E_LongMessages.java
│   └── E2E_RealWorld.java
└── performance/               # Performance tests
    ├── EncodeBenchmark.java
    └── DecodeBenchmark.java
```

---

## ✅ Test-Coverage-Update Workflow

### After Each Phase

```bash
# Run the workflow
/test-coverage-update

# This executes:
# 1. Run all unit tests (C)
# 2. Run all integration tests (Java)
# 3. Generate coverage reports (gcov + JaCoCo)
# 4. Validate coverage >= 95%
# 5. Check for untested code
# 6. Report statistics
```

### Expected Output

```
=== Test Coverage Report ===

C Code Coverage:
  Lines:    96.2% (1543/1604) ✅
  Functions: 98.1% (52/53)   ✅
  Branches:  91.3% (487/533) ⚠️

Java Code Coverage:
  Lines:    97.1% (823/847)  ✅
  Branches:  93.4% (201/215) ✅
  Methods:   96.8% (61/63)   ✅

Overall Status: PASS ✅
Uncovered Files:
  - None

Action Items:
  - Improve branch coverage in hybrid_mode.c (88% < 90%)
```

---

## 🎯 Writing Good Tests

### Test Naming Convention

```java
// Pattern: test[Feature][Scenario][ExpectedBehavior]

@Test
public void testRgbToLab_PureBlack_ReturnsZeroValues() { ... }

@Test
public void testAdaptivePalette_LowLight_IncreasesContrast() { ... }

@Test
public void testErrorAwareEncoder_AvoidsProbematicPairs() { ... }
```

### Test Structure (AAA Pattern)

```java
@Test
public void testFeature() {
    // ARRANGE: Set up test data
    Config config = Config.builder()
        .colorNumber(16)
        .build();
    String testData = "Test message";
    
    // ACT: Execute the code under test
    encoder.encodeToPNG(testData, "test.png", config);
    String result = decoder.decode("test.png");
    
    // ASSERT: Verify expected behavior
    assertEquals(testData, result);
}
```

### Test Data Management

**Use Reference Data:**
```c
// Load known-good test cases
FILE* fp = fopen("test/data/ciede2000_test_cases.csv", "r");
while (fscanf(fp, "%f,%f,%f,%f,%f,%f,%f",
    &L1, &a1, &b1, &L2, &a2, &b2, &expected_delta_e) != EOF) {
    
    LAB lab1 = {L1, a1, b1};
    LAB lab2 = {L2, a2, b2};
    
    float actual = deltaE2000(lab1, lab2);
    assert_float_equal(actual, expected_delta_e, 0.01);
}
```

**Generate Test Data:**
```java
// For parametric testing
@ParameterizedTest
@CsvSource({
    "4, 100, 21",    // 4 colors, 100 chars, expect version 21x21
    "16, 100, 41",   // 16 colors, 100 chars, expect version 41x41
    "64, 200, 65"    // 64 colors, 200 chars, expect version 65x65
})
public void testVersionScaling(int colors, int dataLength, int expectedSize) {
    // ...
}
```

---

## 🐛 Testing Edge Cases

### Always Test

**Boundary Values:**
```java
@Test public void testMinimumDataLength() { ... }
@Test public void testMaximumDataLength() { ... }
@Test public void testEmptyString() { ... }
@Test public void testSingleCharacter() { ... }
```

**Invalid Inputs:**
```java
@Test(expected = IllegalArgumentException.class)
public void testInvalidColorMode_ThrowsException() {
    Config config = Config.builder()
        .colorNumber(17)  // Invalid: must be 4, 8, 16, 32, 64, 128, 256
        .build();
}
```

**Special Characters:**
```java
@Test public void testUnicodeCharacters() { ... }
@Test public void testControlCharacters() { ... }
@Test public void testEmojiCharacters() { ... }
```

**Resource Limits:**
```java
@Test public void testLargeBarcode_UnderMemoryLimit() { ... }
@Test public void testManyIterations_Converges() { ... }
```

---

## 🔍 Debugging Failed Tests

### Systematic Approach

**1. Reproduce Reliably**
```bash
# Run specific test multiple times
mvn test -Dtest=ColorMode6Test#testLongMessage -DfailIfNoTests=false
```

**2. Enable Debug Logging**
```java
@Test
public void testWithLogging() {
    System.setProperty("jabcode.debug", "true");
    // Test code...
}
```

**3. Inspect Test Artifacts**
```bash
# View generated barcode
open panama-wrapper-itest/target/test-output/test.png

# Check decoder output
cat panama-wrapper-itest/target/test-output/decode.log
```

**4. Use Debugger**
```bash
# For Java tests
mvn test -Dmaven.surefire.debug

# Attach debugger to port 5005
```

---

## 📊 Continuous Integration

### GitHub Actions Example

```yaml
name: Test Coverage

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 21
      uses: actions/setup-java@v3
      with:
        java-version: '21'
        distribution: 'temurin'
    
    - name: Build Native Library
      run: |
        cd src/jabcode
        make clean
        make COVERAGE=1
    
    - name: Run Tests
      run: |
        cd panama-wrapper-itest
        mvn clean test jacoco:report
    
    - name: Check Coverage
      run: |
        mvn jacoco:check
    
    - name: Upload Coverage Reports
      uses: codecov/codecov-action@v3
      with:
        files: panama-wrapper-itest/target/site/jacoco/jacoco.xml
```

---

## 🎓 Best Practices

### DO

✅ Write test before implementation  
✅ Test one thing per test  
✅ Use descriptive test names  
✅ Keep tests independent  
✅ Test edge cases and errors  
✅ Aim for high coverage (95%+)  
✅ Use meaningful assertions  
✅ Clean up resources (files, memory)

### DON'T

❌ Test implementation details  
❌ Write tests after code  
❌ Skip edge case testing  
❌ Allow flaky tests  
❌ Ignore test failures  
❌ Test multiple things in one test  
❌ Hard-code magic numbers  
❌ Depend on test execution order

---

## 📞 Quick Reference

**Commands:**
```bash
# Java unit tests
mvn test

# Java specific test
mvn test -Dtest=ColorMode6Test

# Java coverage
mvn jacoco:report

# C unit tests
cd src/jabcode && make test

# C coverage
cd src/jabcode && make coverage

# Full workflow
/test-coverage-update
```

**Coverage Reports:**
- Java: `panama-wrapper-itest/target/site/jacoco/index.html`
- C: `src/jabcode/coverage_html/index.html`

**Coverage Threshold:**
- Minimum: 95% line coverage
- Target: 98% line coverage
- Aspirational: 100% line coverage

---

**Document Status:** ✅ Complete  
**Last Updated:** 2026-01-09  
**Maintained By:** AI-Driven Development
