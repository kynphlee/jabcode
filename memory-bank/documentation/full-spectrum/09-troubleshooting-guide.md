# Troubleshooting Guide
**Common Issues and Solutions** 🔧

*A practical guide to diagnosing and fixing JABCode encoding and decoding problems.*

---

## Quick Diagnostic Checklist

Before diving into specific issues, run through this checklist:

- [ ] JABCode library (libjabcode.so) is accessible
- [ ] Java 21 or newer is being used
- [ ] Native library path (LD_LIBRARY_PATH) is set correctly
- [ ] Color mode is supported (4-128, NOT 256)
- [ ] Image is saved as PNG (not JPEG or other lossy format)
- [ ] Config parameters are valid (use builder validation)

---

## Library Loading Issues

### Error: `UnsatisfiedLinkError: Cannot open library: libjabcode.so`

**Symptom:**
```
java.lang.UnsatisfiedLinkError: Cannot open library: libjabcode.so
```

**Cause:** The native library isn't in the Java library path.

**Solutions:**

**Option 1: Set LD_LIBRARY_PATH (Linux/Mac)**
```bash
export LD_LIBRARY_PATH=/path/to/jabcode/lib:$LD_LIBRARY_PATH
java -jar your-app.jar
```

**Option 2: Set DYLD_LIBRARY_PATH (Mac)**
```bash
export DYLD_LIBRARY_PATH=/path/to/jabcode/lib:$DYLD_LIBRARY_PATH
java -jar your-app.jar
```

**Option 3: Use -Djava.library.path**
```bash
java -Djava.library.path=/path/to/jabcode/lib -jar your-app.jar
```

**Option 4: Configure in IDE**

**IntelliJ IDEA:**
1. Run → Edit Configurations
2. Environment variables: `LD_LIBRARY_PATH=/path/to/jabcode/lib`

**Eclipse:**
1. Run → Run Configurations
2. Environment tab → New
3. Name: `LD_LIBRARY_PATH`, Value: `/path/to/jabcode/lib`

**Verification:**
```bash
# Check if library exists and is readable
ls -la /path/to/jabcode/lib/libjabcode.so
file /path/to/jabcode/lib/libjabcode.so

# Should show: ELF 64-bit LSO shared object
```

---

## Encoding Failures

### Error: Encoding returns false (no exception)

**Symptom:**
```java
boolean success = encoder.encodeToPNG(data, output, config);
// success is false, but no exception thrown
```

**Possible causes:**

**1. Empty or null data**
```java
// ❌ Fails silently
encoder.encodeToPNG("", output, config);
encoder.encodeToPNG(null, output, config);

// ✅ Check first
if (data == null || data.isEmpty()) {
    throw new IllegalArgumentException("Data cannot be null or empty");
}
```

**2. Data too long for chosen color mode/version**

The encoder can't fit your data into the barcode with current settings.

**Solution:** Increase color number or let encoder choose version automatically:
```java
var config = Config.builder()
    .colorNumber(16)  // ← Increase from 8
    .eccLevel(5)
    .build();
```

**3. Invalid output path**

**Solution:** Ensure directory exists and you have write permissions:
```java
Path output = Paths.get("/tmp/output.png");
Files.createDirectories(output.getParent());  // Create dirs if needed
encoder.encodeToPNG(data, output.toString(), config);
```

**4. File I/O error**

**Solution:** Check disk space and permissions:
```bash
df -h /tmp  # Check disk space
ls -ld /tmp  # Check permissions
```

### Error: `IllegalArgumentException: Invalid color number`

**Symptom:**
```java
Config.builder().colorNumber(7).build();
// IllegalArgumentException: Color number must be 4, 8, 16, 32, 64, 128, or 256
```

**Cause:** Color number must be a power of 2 from 4 to 128 (256 is currently broken).

**Solution:**
```java
// ✅ Valid
Config.builder().colorNumber(4).build();
Config.builder().colorNumber(8).build();
Config.builder().colorNumber(64).build();

// ❌ Invalid
Config.builder().colorNumber(7).build();   // Not a power of 2
Config.builder().colorNumber(3).build();   // Too few
Config.builder().colorNumber(256).build(); // Broken (malloc crash)
```

### Error: `IllegalArgumentException: ECC level must be 0-10`

**Symptom:**
```java
Config.builder().eccLevel(11).build();
// IllegalArgumentException: ECC level must be between 0 and 10
```

**Solution:** Use ECC level 0-10:
```java
Config.builder().eccLevel(5).build();  // ✅ Standard
Config.builder().eccLevel(7).build();  // ✅ High
```

---

## Decoding Failures

### Error: Decoder returns null

**Symptom:**
```java
String decoded = decoder.decodeFromFile(Paths.get("code.png"));
// decoded is null
```

**Possible causes:**

**1. Image quality too low**

**Diagnosis:**
```bash
identify code.png
# Check: size, color depth, format
```

**Solution:** Use larger module size when encoding:
```java
Config.builder()
    .moduleSize(16)  // ← Increase from 12
    .build();
```

**2. Image was compressed (JPEG artifacts)**

**Diagnosis:**
```bash
file code.png
# Should be: PNG image data
# NOT: JPEG image data
```

**Solution:** Always save as PNG:
```java
// ✅ Good
encoder.encodeToPNG(data, "output.png", config);

// ❌ Bad - don't convert to JPEG after
BufferedImage img = ImageIO.read(new File("output.png"));
ImageIO.write(img, "JPEG", new File("output.jpg"));  // Destroys colors!
```

**3. Colors shifted (color space conversion)**

**Diagnosis:** Visual inspection - do colors look right?

**Solution:** Avoid color space conversions. Keep in sRGB:
```java
BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
```

**4. Image resized or rotated**

**Diagnosis:** Check image dimensions match encoding dimensions.

**Solution:** Don't resize JABCodes. Scan/use original resolution.

**5. Barcode not found in image**

**Cause:** Finder patterns not detected.

**Solution:** 
- Ensure good contrast
- Check image isn't inverted (colors flipped)
- Verify entire barcode is in frame

### Error: `LDPC decoding failed`

**Symptom:**
```
JABCode Error: LDPC decoding failed
```

**This is serious.** LDPC failure means the error correction couldn't recover the data.

**Possible causes:**

**1. Too much damage/noise**

**Solution:** Increase ECC level when encoding:
```java
Config.builder()
    .eccLevel(7)  // ← Maximum error correction
    .build();
```

**2. Encoder/decoder mask mismatch** (should be fixed now)

This was the bug documented in [04-mask-metadata-saga.md](04-mask-metadata-saga.md).

**Verification:**
```bash
# Ensure you're using fixed version
grep -A2 "color_number <= 128" src/jabcode/encoder.c
# Should show the fix allowing mask updates for <=128 colors
```

**3. Color discrimination failed**

Higher color modes are more sensitive to color accuracy.

**Solution:** Drop to lower color mode:
```java
// If 64-color fails
Config.builder().colorNumber(32).build();  // Try 32-color

// If 32-color fails
Config.builder().colorNumber(16).build();  // Try 16-color
```

---

## Color Mode Specific Issues

### 64-Color Mode: All tests fail with LDPC error

**Cause:** You're using an old version with the mask metadata bug.

**Solution:** 
1. Pull latest code (fix committed December 2025)
2. Rebuild native library:
   ```bash
   cd src/jabcode
   make clean && make
   ```
3. Verify fix:
   ```bash
   cd panama-wrapper
   mvn test -Dtest=ColorMode5Test
   # Should pass 11/11 tests
   ```

### 128-Color Mode: Decoding fails

**Same as 64-color.** Ensure you have the fix for mask metadata updates.

### 256-Color Mode: VM crashes

**Symptom:**
```
malloc(): invalid size (unsorted)
Aborted (core dumped)
```

**Cause:** Known malloc corruption bug in encoder initialization.

**Solution:** **Don't use 256-color mode.** It's broken.

Use 128-color instead (provides 87% of theoretical 256-color capacity anyway).

**Status:** Documented issue, see [05-encoder-memory-architecture.md](05-encoder-memory-architecture.md)

---

## Image Quality Issues

### Barcode looks blurry

**Cause:** Module size too small for resolution.

**Solution:**
```java
Config.builder()
    .moduleSize(16)  // ← Increase
    .build();
```

**Rule of thumb:** Module size ≥ 12 pixels for digital, ≥ 16 for print.

### Colors look wrong

**Cause:** Color space conversion or JPEG compression.

**Solution:**
1. Save as PNG (never JPEG)
2. Use sRGB color space
3. Avoid any image manipulation after encoding

### Barcode is too large

**Cause:** Low color mode or high ECC creates more modules.

**Solutions:**

**Option 1: Use higher color mode**
```java
Config.builder()
    .colorNumber(16)  // ← More colors = smaller barcode
    .build();
```

**Option 2: Reduce ECC (if conditions allow)**
```java
Config.builder()
    .eccLevel(3)  // ← Less ECC = more capacity = smaller barcode
    .build();
```

**Option 3: Reduce module size (digital only)**
```java
Config.builder()
    .moduleSize(10)  // ← Smaller modules = smaller image
    .build();
```

---

## Build Issues

### Maven build fails

**Symptom:**
```
[ERROR] Failed to execute goal ... compilation failure
```

**Solution 1: Check Java version**
```bash
java -version
# Must be 21 or newer for Panama FFM
```

**Solution 2: Clean and rebuild**
```bash
mvn clean install
```

**Solution 3: Update dependencies**
```bash
mvn dependency:resolve
```

### Native library not found during tests

**Symptom:**
```
Tests run: 10, Failures: 10
java.lang.UnsatisfiedLinkError: Cannot open library
```

**Solution:** Set library path in Maven:
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <environmentVariables>
            <LD_LIBRARY_PATH>${project.basedir}/../lib</LD_LIBRARY_PATH>
        </environmentVariables>
    </configuration>
</plugin>
```

Or run tests with library path:
```bash
LD_LIBRARY_PATH=../lib mvn test
```

### JaCoCo report generation fails

**Symptom:**
```
[ERROR] Failed to execute goal jacoco:report
```

**Solution:**
```bash
# Ensure tests ran first
mvn clean test jacoco:report
```

---

## Runtime Performance Issues

### Encoding is very slow

**Expected times:**
- Small messages (<100 bytes): 10-30ms
- Medium messages (100-500 bytes): 30-100ms
- Large messages (>500 bytes): 100-500ms

**If slower:**

**Check 1: Color mode**

Higher color modes take longer due to palette selection:
```
4-color:   ~1.0× (baseline)
64-color:  ~2.0× slower
128-color: ~2.5× slower
```

**Check 2: Module size**

Larger modules = larger images = more pixel operations.

**Check 3: JVM warmup**

First few encodings are slow (JIT compilation). Benchmark after warmup:
```java
// Warmup
for (int i = 0; i < 100; i++) {
    encoder.encodeToPNG(data, output, config);
}

// Now benchmark
long start = System.nanoTime();
encoder.encodeToPNG(data, output, config);
long duration = System.nanoTime() - start;
```

### Decoding is very slow

**Expected times:** 2-3× encoding time (decoding is more complex).

**If much slower:**

**Check 1: Image size**

Very large images take longer. Consider reducing module size when encoding.

**Check 2: Color mode**

Higher color modes require more color matching:
```
4-color:   ~1.0× (baseline)
64-color:  ~1.8× slower
128-color: ~2.2× slower
```

---

## Integration Issues

### JABCode works in tests but not in production

**Checklist:**

1. **Library path:** Is `LD_LIBRARY_PATH` set in production environment?
   ```bash
   echo $LD_LIBRARY_PATH
   # Should include path to libjabcode.so
   ```

2. **File permissions:** Can application write to output directory?
   ```bash
   ls -ld /path/to/output/dir
   # Should show write permissions
   ```

3. **Dependencies:** Are all dependencies deployed?
   ```bash
   ldd /path/to/libjabcode.so
   # Should show all dependencies resolved
   ```

4. **Java version:** Is production using Java 21+?
   ```bash
   java -version
   ```

### Works on my machine, not on server

**Common causes:**

**1. Architecture mismatch**

Native library compiled for different architecture (x86 vs ARM, 32-bit vs 64-bit).

**Solution:** Compile on target architecture or provide multiple builds.

**2. Missing system libraries**

**Diagnosis:**
```bash
ldd libjabcode.so
# Look for "not found"
```

**Solution:** Install missing dependencies.

**3. Path differences**

Absolute paths in config don't match server structure.

**Solution:** Use relative paths or environment variables:
```java
String libPath = System.getenv("JABCODE_LIB_PATH");
if (libPath != null) {
    System.setProperty("java.library.path", libPath);
}
```

---

## Testing Issues

### Test fails: "File should not be empty"

**Symptom:**
```java
assertTrue(outputFile.toFile().length() > 0, "Output file should not be empty");
// AssertionError: Output file should not be empty
```

**Cause:** Encoding failed but didn't throw exception.

**Solution:** Check that encoding actually succeeded:
```java
boolean success = encoder.encodeToPNG(message, output, config);
assertTrue(success, "Encoding should succeed");  // ← Add this check
assertTrue(Files.exists(output), "File should exist");
assertTrue(Files.size(output) > 0, "File should not be empty");
```

### Test fails: Round-trip doesn't match

**Symptom:**
```java
assertEquals(original, decoded, "Round-trip should preserve data");
// AssertionError: expected:<Hello> but was:<null>
```

**Diagnosis:** Check each step:
```java
// Step 1: Encoding
boolean encoded = encoder.encodeToPNG(original, output, config);
System.out.println("Encoded: " + encoded);

// Step 2: File created
System.out.println("File exists: " + Files.exists(output));
System.out.println("File size: " + Files.size(output));

// Step 3: Decoding
String decoded = decoder.decodeFromFile(output);
System.out.println("Decoded: " + (decoded != null ? decoded : "null"));
```

This will show where the chain breaks.

---

## Platform-Specific Issues

### Linux: Library not found despite correct path

**Cause:** SELinux or AppArmor blocking library access.

**Diagnosis:**
```bash
getenforce
# If "Enforcing", SELinux might be blocking
```

**Solution:**
```bash
# Temporarily disable (for testing)
sudo setenforce 0

# Or add exception for your library
# (consult SELinux documentation)
```

### macOS: "dyld: Library not loaded"

**Cause:** Library path not set correctly for macOS.

**Solution:** Use `DYLD_LIBRARY_PATH` instead of `LD_LIBRARY_PATH`:
```bash
export DYLD_LIBRARY_PATH=/path/to/jabcode/lib
```

**Note:** macOS has restrictions on `DYLD_*` variables. May need to:
1. Copy library to `/usr/local/lib`
2. Or use absolute path in library
3. Or code-sign the library

### Windows: Not currently supported

JABCode Panama wrapper is Linux/macOS only due to native library compilation.

**Workaround:** Use WSL (Windows Subsystem for Linux):
```bash
wsl --install
# Then work within WSL environment
```

---

## Advanced Debugging

### Enable debug logging

Add logging to see what's happening:

**Java side:**
```java
// Before encoding
System.out.println("Config: " + config);
System.out.println("Data length: " + data.length());

// After encoding
System.out.println("Encoding result: " + success);
```

**Native side (if you built with DEBUG flag):**
```bash
# Rebuild with debug logging
cd src/jabcode
make clean
CFLAGS="-DDEBUG -g" make

# Run and see native debug output
LD_LIBRARY_PATH=../lib java ...
```

### Use a debugger

**Java debugging:**
```bash
# Run with debug port
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005 -jar app.jar

# Attach IDE debugger to port 5005
```

**Native debugging (GDB):**
```bash
# Run under GDB
LD_LIBRARY_PATH=../lib gdb --args java -jar app.jar

# Set breakpoint in native code
(gdb) break jab_encode_create
(gdb) run
```

### Memory debugging

**Check for leaks with Valgrind:**
```bash
LD_LIBRARY_PATH=../lib valgrind --leak-check=full java -jar app.jar
```

**Check for corruption with AddressSanitizer:**
```bash
# Rebuild with ASan
cd src/jabcode
make clean
CFLAGS="-fsanitize=address -g" make

# Run - will catch buffer overflows
LD_LIBRARY_PATH=../lib java -jar app.jar
```

---

## Getting Help

### Before asking for help

1. ✅ Read this troubleshooting guide
2. ✅ Check existing documentation
3. ✅ Search GitHub issues
4. ✅ Try minimal reproducer
5. ✅ Collect diagnostic information

### What to include in bug reports

```markdown
**Environment:**
- OS: Ubuntu 22.04 (or macOS 13.0, etc.)
- Java version: 21.0.1
- JABCode version: 2.0.0 (commit hash if from git)

**Issue:**
[Clear description of problem]

**Steps to reproduce:**
1. Create Config with colorNumber=64
2. Call encoder.encodeToPNG(...)
3. Observe [specific error]

**Expected behavior:**
[What should happen]

**Actual behavior:**
[What actually happens]

**Code sample:**
```java
// Minimal code that reproduces issue
```

**Error output:**
```
[Full error message/stack trace]
```

**Diagnostic info:**
```bash
$ ldd lib/libjabcode.so
[output]

$ java -version
[output]

$ echo $LD_LIBRARY_PATH
[output]
```
```

---

## Quick Reference

### Error to Section Mapping

| Error Message | Go To |
|---------------|-------|
| `UnsatisfiedLinkError` | Library Loading Issues |
| `IllegalArgumentException: Invalid color number` | Encoding Failures |
| `LDPC decoding failed` | Decoding Failures |
| `malloc(): invalid size` | Color Mode Specific (256-color) |
| Round-trip test fails | Testing Issues |
| File size is 0 | Encoding Failures |
| Decoder returns null | Decoding Failures |

### Command Reference

```bash
# Check library
ldd lib/libjabcode.so
file lib/libjabcode.so

# Set library path
export LD_LIBRARY_PATH=/path/to/lib:$LD_LIBRARY_PATH

# Rebuild native library
cd src/jabcode && make clean && make

# Run tests
mvn test

# Run specific test
mvn test -Dtest=ColorMode5Test

# Generate coverage report
mvn clean test jacoco:report

# Debug mode
CFLAGS="-DDEBUG -g" make
```

---

## Related Documentation

- **[01-getting-started.md](01-getting-started.md)** - Basic usage guide
- **[03-choosing-color-mode.md](03-choosing-color-mode.md)** - Color mode selection
- **[04-mask-metadata-saga.md](04-mask-metadata-saga.md)** - LDPC decoding bug details
- **[08-color-mode-reference.md](08-color-mode-reference.md)** - Technical specifications

---

*"The most effective debugging tool is still careful thought, coupled with judiciously placed print statements."* - Brian Kernighan

When in doubt, add logging and trace the problem. Most issues reveal themselves with enough information. 🔍
