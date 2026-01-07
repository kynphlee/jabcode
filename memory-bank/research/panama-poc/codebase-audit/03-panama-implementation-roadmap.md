# Panama Implementation Roadmap

## Executive Summary

Based on the audit of JABCode C library and existing JNI wrapper, this document provides a concrete roadmap for implementing the Panama FFM wrapper.

**Goal:** Pure Java implementation using Panama FFM that replicates the functionality of the JNI wrapper without requiring any C++ code.

## Critical Implementation Challenges

### Challenge 1: Flexible Array Members

**Problem:** Both `jab_data` and `jab_bitmap` use C99 flexible array members:

```c
typedef struct {
    jab_int32 length;
    jab_char  data[];   // Flexible array member
} jab_data;

typedef struct {
    jab_int32 width;
    jab_int32 height;
    jab_int32 bits_per_pixel;
    jab_int32 bits_per_channel;
    jab_int32 channel_count;
    jab_byte  pixel[];  // Flexible array member
} jab_bitmap;
```

**Solution:**

```java
// For jab_data
public MemorySegment allocateJabData(Arena arena, byte[] data) {
    // jextract will generate jab_data.sizeof() which gives header size only
    long headerSize = jab_data.sizeof();
    long totalSize = headerSize + data.length;
    
    // Allocate full structure
    MemorySegment segment = arena.allocate(totalSize);
    
    // Set header fields
    jab_data.length(segment, data.length);
    
    // Access flexible array member
    MemorySegment flexArray = segment.asSlice(headerSize, data.length);
    MemorySegment.copy(
        MemorySegment.ofArray(data), 0,
        flexArray, 0,
        data.length
    );
    
    return segment;
}

// For jab_bitmap
public byte[] extractBitmapPixels(MemorySegment bitmap) {
    int width = jab_bitmap.width(bitmap);
    int height = jab_bitmap.height(bitmap);
    int channels = jab_bitmap.channel_count(bitmap);
    int pixelCount = width * height * channels;
    
    // Access flexible array member
    long headerSize = jab_bitmap.sizeof();
    MemorySegment pixelArray = bitmap.asSlice(headerSize, pixelCount);
    
    return pixelArray.toArray(ValueLayout.JAVA_BYTE);
}
```

### Challenge 2: Pointer Chain Navigation

**Problem:** `jab_encode` contains a pointer to `jab_bitmap`:

```c
typedef struct {
    jab_int32     color_number;
    // ... other fields
    jab_bitmap*   bitmap;  // Pointer to bitmap
} jab_encode;
```

**Solution:**

```java
// After encoding
MemorySegment enc = ...;

// Get bitmap pointer (this is a MemorySegment address)
MemorySegment bitmapPtr = jab_encode.bitmap(enc);

// Dereference to access actual bitmap
if (bitmapPtr.address() != 0) {
    int width = jab_bitmap.width(bitmapPtr);
    int height = jab_bitmap.height(bitmapPtr);
    // ... extract pixels
}
```

### Challenge 3: Memory Ownership

**Problem:** Some functions allocate memory that must be freed:

```c
jab_data* decodeJABCode(jab_bitmap* bitmap, jab_int32 mode, jab_int32* status);
// Returns allocated jab_data - caller must free
```

**Solution:**

```java
public String decode(byte[] imageData) {
    try (Arena arena = Arena.ofConfined()) {
        // Arena-allocated memory (auto-freed)
        MemorySegment bitmap = createBitmap(arena, imageData);
        MemorySegment statusSegment = arena.allocate(ValueLayout.JAVA_INT);
        
        // decodeJABCode allocates jab_data outside arena
        MemorySegment resultPtr = decodeJABCode(arena, bitmap, 0, statusSegment);
        
        if (resultPtr.address() == 0) {
            return null;
        }
        
        // Extract data before freeing
        int length = jab_data.length(resultPtr);
        MemorySegment dataArray = resultPtr.asSlice(jab_data.sizeof(), length);
        byte[] decoded = dataArray.toArray(ValueLayout.JAVA_BYTE);
        
        // Free C-allocated memory
        // Need to bind stdlib free() function
        free(arena, resultPtr);
        
        return new String(decoded, StandardCharsets.UTF_8);
    }
}
```

## Implementation Phases

### Phase 1: Setup & Binding Generation

**Deliverables:**
- [x] Project structure created
- [x] Maven POM configured
- [x] jextract.sh script ready
- [ ] jextract bindings generated
- [ ] Verify generated code structure

**Steps:**
1. Ensure jextract is installed
2. Run `./jextract.sh`
3. Inspect generated bindings in `target/generated-sources/jextract/`
4. Verify all key structs are present

**Validation:**
```bash
# Check generated files
ls -la target/generated-sources/jextract/com/jabcode/panama/bindings/
cat target/generated-sources/jextract/com/jabcode/panama/bindings/jabcode_h.java | head -50
grep "jab_data" target/generated-sources/jextract/com/jabcode/panama/bindings/*.java
```

### Phase 2: Basic Encoder Implementation

**Deliverables:**
- [ ] `JABCodeEncoder.encode()` working for simple text
- [ ] Handle 8-color mode encoding
- [ ] Extract RGBA bitmap
- [ ] Convert to Java `BufferedImage`

**Implementation Steps:**

```java
// JABCodeEncoder.java
public byte[] encodeWithConfig(String data, Config config) {
    try (Arena arena = Arena.ofConfined()) {
        // 1. Create encoder
        MemorySegment enc = createEncode(
            arena,
            config.getColorNumber(),
            config.getSymbolNumber()
        );
        
        if (enc.address() == 0) {
            throw new RuntimeException("Failed to create encoder");
        }
        
        // 2. Set encoder parameters
        jab_encode.module_size(enc, config.getModuleSize());
        jab_encode.master_symbol_width(enc, config.getMasterSymbolWidth());
        jab_encode.master_symbol_height(enc, config.getMasterSymbolHeight());
        
        // 3. Prepare data
        byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
        MemorySegment jabData = allocateJabData(arena, dataBytes);
        
        // 4. Generate code
        int result = generateJABCode(arena, enc, jabData);
        
        if (result == 0) { // JAB_FAILURE
            destroyEncode(arena, enc);
            throw new RuntimeException("Encoding failed");
        }
        
        // 5. Extract bitmap
        MemorySegment bitmapPtr = jab_encode.bitmap(enc);
        if (bitmapPtr.address() == 0) {
            destroyEncode(arena, enc);
            throw new RuntimeException("No bitmap generated");
        }
        
        // 6. Get bitmap data
        int width = jab_bitmap.width(bitmapPtr);
        int height = jab_bitmap.height(bitmapPtr);
        int channels = jab_bitmap.channel_count(bitmapPtr);
        byte[] pixels = extractBitmapPixels(bitmapPtr);
        
        // 7. Cleanup
        destroyEncode(arena, enc);
        
        return pixels;
    }
}

private MemorySegment allocateJabData(Arena arena, byte[] data) {
    long headerSize = jab_data.sizeof();
    long totalSize = headerSize + data.length;
    
    MemorySegment segment = arena.allocate(totalSize);
    jab_data.length(segment, data.length);
    
    MemorySegment flexArray = segment.asSlice(headerSize, data.length);
    MemorySegment.copy(
        MemorySegment.ofArray(data), 0,
        flexArray, 0,
        data.length
    );
    
    return segment;
}

private byte[] extractBitmapPixels(MemorySegment bitmap) {
    int width = jab_bitmap.width(bitmap);
    int height = jab_bitmap.height(bitmap);
    int channels = jab_bitmap.channel_count(bitmap);
    int pixelCount = width * height * channels;
    
    long headerSize = jab_bitmap.sizeof();
    MemorySegment pixelArray = bitmap.asSlice(headerSize, pixelCount);
    
    return pixelArray.toArray(ValueLayout.JAVA_BYTE);
}
```

**Testing:**
```java
@Test
void testSimpleEncode() {
    var encoder = new JABCodeEncoder();
    byte[] pixels = encoder.encode("Hello World", 8, 5);
    
    assertNotNull(pixels);
    assertTrue(pixels.length > 0);
    // Verify it's RGBA data
    assertTrue(pixels.length % 4 == 0);
}
```

### Phase 3: Decoder Implementation

**Deliverables:**
- [ ] `JABCodeDecoder.decode()` working
- [ ] Handle image loading
- [ ] Extract decoded data
- [ ] Proper memory cleanup

**Implementation Steps:**

```java
// JABCodeDecoder.java
public DecodedResult decodeEx(byte[] imageData) {
    try (Arena arena = Arena.ofConfined()) {
        // 1. Create bitmap from image data
        MemorySegment bitmap = createBitmapFromImageData(arena, imageData);
        
        // 2. Allocate status output
        MemorySegment statusSegment = arena.allocate(ValueLayout.JAVA_INT);
        
        // 3. Decode
        MemorySegment resultPtr = decodeJABCode(arena, bitmap, 0, statusSegment);
        
        int status = statusSegment.get(ValueLayout.JAVA_INT, 0);
        
        if (resultPtr.address() == 0) {
            return new DecodedResult(null, 0, false);
        }
        
        // 4. Extract data
        int length = jab_data.length(resultPtr);
        MemorySegment dataArray = resultPtr.asSlice(jab_data.sizeof(), length);
        byte[] decoded = dataArray.toArray(ValueLayout.JAVA_BYTE);
        
        // 5. Free C-allocated result
        // TODO: Bind stdlib free() or use jab-specific cleanup
        
        return new DecodedResult(
            new String(decoded, StandardCharsets.UTF_8),
            1,
            status == 1
        );
    }
}

private MemorySegment createBitmapFromImageData(Arena arena, byte[] imageData) {
    // Option 1: Use Java ImageIO to decode, then create bitmap
    BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageData));
    return createBitmapFromBufferedImage(arena, image);
    
    // Option 2: Write to temp file and use readImage()
    // Path tempFile = Files.createTempFile("jabcode", ".png");
    // Files.write(tempFile, imageData);
    // MemorySegment filenamePtr = arena.allocateFrom(tempFile.toString());
    // return readImage(arena, filenamePtr);
}

private MemorySegment createBitmapFromBufferedImage(Arena arena, BufferedImage image) {
    int width = image.getWidth();
    int height = image.getHeight();
    int channels = 4; // RGBA
    int pixelCount = width * height * channels;
    
    // Calculate total size
    long headerSize = jab_bitmap.sizeof();
    long totalSize = headerSize + pixelCount;
    
    // Allocate
    MemorySegment bitmap = arena.allocate(totalSize);
    
    // Set fields
    jab_bitmap.width(bitmap, width);
    jab_bitmap.height(bitmap, height);
    jab_bitmap.bits_per_pixel(bitmap, 32);
    jab_bitmap.bits_per_channel(bitmap, 8);
    jab_bitmap.channel_count(bitmap, 4);
    
    // Copy pixels
    MemorySegment pixelArray = bitmap.asSlice(headerSize, pixelCount);
    
    int[] rgb = image.getRGB(0, 0, width, height, null, 0, width);
    for (int i = 0; i < rgb.length; i++) {
        int argb = rgb[i];
        pixelArray.set(ValueLayout.JAVA_BYTE, i * 4 + 0, (byte)((argb >> 16) & 0xFF)); // R
        pixelArray.set(ValueLayout.JAVA_BYTE, i * 4 + 1, (byte)((argb >> 8) & 0xFF));  // G
        pixelArray.set(ValueLayout.JAVA_BYTE, i * 4 + 2, (byte)(argb & 0xFF));         // B
        pixelArray.set(ValueLayout.JAVA_BYTE, i * 4 + 3, (byte)((argb >> 24) & 0xFF)); // A
    }
    
    return bitmap;
}
```

### Phase 4: BufferedImage Integration

**Deliverables:**
- [ ] Convert Panama byte[] to `BufferedImage`
- [ ] Support different color modes
- [ ] Handle PNG/TIFF I/O

**Implementation:**

```java
// In JABCodeEncoder
public BufferedImage encodeToImage(String data, Config config) {
    byte[] pixels = encodeWithConfig(data, config);
    
    // Determine dimensions from encoder
    // (need to save width/height during encoding)
    return createBufferedImageFromRGBA(pixels, width, height);
}

private BufferedImage createBufferedImageFromRGBA(byte[] pixels, int width, int height) {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    
    int[] argb = new int[width * height];
    for (int i = 0; i < argb.length; i++) {
        int r = pixels[i * 4 + 0] & 0xFF;
        int g = pixels[i * 4 + 1] & 0xFF;
        int b = pixels[i * 4 + 2] & 0xFF;
        int a = pixels[i * 4 + 3] & 0xFF;
        argb[i] = (a << 24) | (r << 16) | (g << 8) | b;
    }
    
    image.setRGB(0, 0, width, height, argb, 0, width);
    return image;
}
```

### Phase 5: Advanced Features

**Deliverables:**
- [ ] Multi-symbol support
- [ ] Extended decoding (decodeJABCodeEx)
- [ ] Custom palettes
- [ ] All color modes (2, 4, 8, 16, 32, 64, 128, 256)

### Phase 6: Testing & Validation

**Deliverables:**
- [ ] Unit tests for all functions
- [ ] Compare output with JNI version
- [ ] Performance benchmarks
- [ ] Memory leak testing

**Test Suite:**

```java
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JABCodeIntegrationTest {
    
    @Test
    void testEncodeDecodeRoundtrip() {
        String original = "Test Data 12345";
        
        // Encode
        var encoder = new JABCodeEncoder();
        BufferedImage image = encoder.encodeToImage(original, Config.defaults());
        
        // Decode
        var decoder = new JABCodeDecoder();
        String decoded = decoder.decodeFromImage(image);
        
        assertEquals(original, decoded);
    }
    
    @Test
    void testAllColorModes() {
        for (int colors : new int[]{2, 4, 8, 16, 32, 64, 128, 256}) {
            var config = Config.builder().colorNumber(colors).build();
            byte[] result = encoder.encodeWithConfig("Test", config);
            assertNotNull(result, "Failed for " + colors + " colors");
        }
    }
    
    @Test
    void testMemoryCleanup() {
        // Encode/decode many times to test for leaks
        for (int i = 0; i < 1000; i++) {
            encoder.encode("Test " + i, 8, 5);
        }
        // Monitor memory usage
    }
}
```

## Performance Comparison

### Benchmark Framework

```java
@State(Scope.Benchmark)
public class EncodingBenchmark {
    
    @Param({"Panama", "JNI"})
    private String implementation;
    
    private JABCodeEncoder panamaEncoder;
    private OptimizedJABCode jniEncoder;
    
    @Setup
    public void setup() {
        panamaEncoder = new JABCodeEncoder();
        // JNI encoder setup
    }
    
    @Benchmark
    public byte[] encodeSmall() {
        String data = "Hello World";
        return implementation.equals("Panama") 
            ? panamaEncoder.encode(data, 8, 5)
            : jniEncoder.encode(data.getBytes(), 8);
    }
    
    @Benchmark
    public byte[] encodeLarge() {
        String data = "x".repeat(1000);
        return implementation.equals("Panama")
            ? panamaEncoder.encode(data, 8, 5)
            : jniEncoder.encode(data.getBytes(), 8);
    }
}
```

**Expected Results:**
- **Throughput:** Panama 95-105% of JNI
- **Memory:** Lower overhead (arena allocation)
- **Warmup:** Comparable to JNI

## Common Pitfalls & Solutions

### Pitfall 1: Forgetting to Check NULL Pointers

**Problem:**
```java
MemorySegment bitmap = jab_encode.bitmap(enc);
int width = jab_bitmap.width(bitmap); // Crash if bitmap is NULL!
```

**Solution:**
```java
MemorySegment bitmap = jab_encode.bitmap(enc);
if (bitmap.address() == 0) {
    throw new RuntimeException("Bitmap not generated");
}
int width = jab_bitmap.width(bitmap);
```

### Pitfall 2: Incorrect Flexible Array Access

**Problem:**
```java
// Wrong: tries to access data as a field
byte[] data = jab_data.data(segment); // This won't work!
```

**Solution:**
```java
// Correct: slice past header to access flexible array
long headerSize = jab_data.sizeof();
int length = jab_data.length(segment);
MemorySegment dataArray = segment.asSlice(headerSize, length);
byte[] data = dataArray.toArray(ValueLayout.JAVA_BYTE);
```

### Pitfall 3: Arena Lifetime Issues

**Problem:**
```java
public MemorySegment dangerousMethod() {
    try (Arena arena = Arena.ofConfined()) {
        MemorySegment enc = createEncode(arena, 8, 1);
        return enc; // WRONG! enc is freed when arena closes!
    }
}
```

**Solution:**
```java
public byte[] safeMethod() {
    try (Arena arena = Arena.ofConfined()) {
        MemorySegment enc = createEncode(arena, 8, 1);
        // Extract data before arena closes
        byte[] pixels = extractPixels(enc);
        return pixels; // Safe: pixels is a copy
    }
}
```

### Pitfall 4: Unsigned Byte Handling

**Problem:**
```java
byte pixel = pixelArray.get(ValueLayout.JAVA_BYTE, i);
int argb = 0xFF000000 | (pixel << 16); // Wrong! Sign extension!
```

**Solution:**
```java
byte pixel = pixelArray.get(ValueLayout.JAVA_BYTE, i);
int argb = 0xFF000000 | ((pixel & 0xFF) << 16); // Correct: mask to unsigned
```

## Success Criteria

### Functional Requirements

- [ ] Encode text to JABCode bitmap
- [ ] Decode JABCode bitmap to text
- [ ] Support all color modes (2, 4, 8, 16, 32, 64, 128, 256)
- [ ] Handle multi-symbol codes
- [ ] Read/write PNG images
- [ ] Match JNI wrapper output exactly

### Non-Functional Requirements

- [ ] Performance within 90-110% of JNI
- [ ] No memory leaks (validate with profiler)
- [ ] Type-safe API (compile-time checks)
- [ ] Zero C++ code
- [ ] Comprehensive test coverage (>80%)

### Documentation Requirements

- [ ] API documentation (Javadoc)
- [ ] Usage examples
- [ ] Migration guide from JNI
- [ ] Performance comparison report

## Timeline Estimate

| Phase | Duration | Effort |
|-------|----------|--------|
| Phase 1: Setup | 1-2 hours | Low |
| Phase 2: Encoder | 8-16 hours | High |
| Phase 3: Decoder | 8-16 hours | High |
| Phase 4: BufferedImage | 4-8 hours | Medium |
| Phase 5: Advanced | 8-16 hours | High |
| Phase 6: Testing | 8-16 hours | High |
| **Total** | **37-74 hours** | **~1-2 weeks** |

## References

- **C Library Audit:** `01-jabcode-c-library-structure.md`
- **JNI Wrapper Audit:** `02-jni-wrapper-implementation.md`
- **Panama Guide:** `/panama-wrapper/IMPLEMENTATION_GUIDE.md`
- **Quickstart:** `/panama-wrapper/QUICKSTART.md`
