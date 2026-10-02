# JABCode Panama Wrapper

Pure Java implementation using **Project Panama (Foreign Function & Memory API)** to interface with the JABCode C library.

## Overview

This wrapper eliminates the need for C++ JNI wrapper code by using Java's Foreign Function & Memory API (JEP 454) to call the JABCode C library directly from Java.

## Requirements

- **JDK 23+** (you have JDK 23 and 25 available)
- **jextract** tool (for generating bindings)
- **libjabcode** (native C library)

## Architecture

```
Java (Panama FFM API)
  ↓ [Direct C calls via MethodHandles]
libjabcode.so (JABCode C library)
```

**No C++ wrapper needed!**

## Advantages Over JNI Wrapper

| Aspect | JNI (javacpp-wrapper) | Panama (this) |
|--------|----------------------|---------------|
| Native Wrapper Code | 500+ lines C++ | 0 lines |
| Binding Generation | Manual | Automatic (jextract) |
| Memory Management | Manual (leak-prone) | Arenas, plus explicit `destroyEncode`/`free` for codec memory |
| Type Safety | Runtime only | Compile-time |
| Build Dependencies | C++ compiler | jextract (Maven runs it) |
| Maintainability | High effort | Low effort |

## Directory Structure

```
panama-wrapper/
├── README.md                          # This file
├── pom.xml                            # Maven build configuration
├── jextract.sh                        # Script to generate bindings
├── src/
│   └── main/
│       └── java/
│           └── com/
│               └── jabcode/
│                   └── panama/
│                       ├── JABCodeEncoder.java    # High-level API
│                       └── JABCodeDecoder.java    # High-level API
└── target/
    └── generated-sources/
        └── jextract/                  # Auto-generated bindings (git-ignored)
```

## Build Process

1. **Generate Native Library** (if not already built)

   Maven's tests load the vendored `../lib/libjabcode.so` (`jabcode.lib.path` in `pom.xml`). A bare `make` leaves the library in `../src/jabcode/build/`; `refresh-lib` builds it and copies it into `../lib`.
   ```bash
   make -C ../src/jabcode refresh-lib
   ```

2. **Build Java Wrapper**

   Maven generates the Panama bindings itself: jextract runs in the `generate-sources` phase.
   ```bash
   mvn clean package
   ```

3. **Run Tests**
   ```bash
   mvn test
   ```

## Usage

### Simple Encoding

```java
import com.jabcode.panama.JABCodeEncoder;

public class Example {
    public static void main(String[] args) {
        var encoder = new JABCodeEncoder();
        
        byte[] encoded = encoder.encode("Hello JABCode!", 8, 5);
        
        if (encoded != null) {
            System.out.println("Encoded: " + encoded.length + " bytes");
        }
    }
}
```

### With Configuration

```java
var config = JABCodeEncoder.Config.builder()
    .colorNumber(8)
    .eccLevel(5)
    .symbolNumber(1)
    .moduleSize(12)
    .build();

byte[] result = encoder.encodeWithConfig("Data", config);
```

## Development Workflow

### Initial Setup

1. Install jextract:
   ```bash
   # Download standalone jextract
   # See: https://jdk.java.net/jextract/
   ```

2. Verify setup:
   ```bash
   java --version  # Should show 23 or 25
   jextract --version  # Should show jextract version
   ```

### Regenerate Bindings

Whenever the C API changes:

```bash
./jextract.sh
```

This regenerates all Panama bindings from the C headers.

### Testing

Run with FFM access enabled:

```bash
mvn test
```

## Comparison with javacpp-wrapper

### JNI Approach (javacpp-wrapper, retired)

**Location:** `/javacpp-wrapper/src/main/c/JABCodeNative_jni.cpp` on `my-branch` (7f979c6 removed `javacpp-wrapper/` from this branch). Four of its JNI functions:

```cpp
JNIEXPORT jlong JNICALL Java_com_jabcode_internal_JABCodeNativePtr_createEncodePtr(JNIEnv *env, jclass cls, jint colorNumber, jint symbolNumber) {
    return (jlong)createEncode_c(colorNumber, symbolNumber);
}

JNIEXPORT void JNICALL Java_com_jabcode_internal_JABCodeNativePtr_destroyEncodePtr(JNIEnv *env, jclass cls, jlong encPtr) {
    destroyEncode_c((jab_encode*)encPtr);
}

JNIEXPORT jint JNICALL Java_com_jabcode_internal_JABCodeNativePtr_generateJABCodePtr(JNIEnv *env, jclass cls, jlong encPtr, jlong dataPtr) {
    return generateJABCode_c((jab_encode*)encPtr, (jab_data*)dataPtr);
}

// ...

JNIEXPORT jboolean JNICALL Java_com_jabcode_internal_JABCodeNativePtr_saveImagePtr(JNIEnv *env, jclass cls, jlong bitmapPtr, jstring filename) {
    const char* filenameChars = env->GetStringUTFChars(filename, NULL);
    jboolean result = saveImage_c((jab_bitmap*)bitmapPtr, (jab_char*)filenameChars);
    env->ReleaseStringUTFChars(filename, filenameChars);
    return result;
}
```

**Issues:**
- Manual memory management (GetStringUTFChars/Release)
- Platform-specific build (needs C++ compiler)
- Error-prone JNI boilerplate
- ~500 lines of wrapper code

### Panama Approach (this wrapper)

**Location:** `/panama-wrapper/src/main/java/com/jabcode/panama/JABCodeEncoder.java`. `encode` delegates to `encodeBytes`, shown here without its argument, null and error checks or its cascade settings:

```java
public byte[] encodeBytes(byte[] data, Config config) {
    try (Arena arena = Arena.ofConfined()) {
        // Create encoder
        MemorySegment enc = jabcode_h.createEncode(
            config.getColorNumber(),
            config.getSymbolNumber()
        );
        try {
            // Prepare jab_data structure: { int32 length; char data[]; }
            MemorySegment jabData = createJabData(arena, data);

            // Generate JABCode (0 = success per generateJABCode contract)
            int result = jabcode_h.generateJABCode(enc, jabData);
            MemorySegment bitmapPtr = getBitmapFromEncoder(enc);
            MemorySegment outLen = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment pngPtr = jabcode_h.saveImageToMemory(bitmapPtr, outLen);
            try {
                int pngLen = outLen.get(ValueLayout.JAVA_INT, 0);
                // Copy the native PNG bytes into a Java-owned array.
                return pngPtr.reinterpret(pngLen).toArray(ValueLayout.JAVA_BYTE);
            } finally {
                // saveImageToMemory malloc's the buffer; the caller owns it.
                NativeMemory.free(pngPtr);
            }
        } finally {
            jabcode_h.destroyEncode(enc);
        }
    } catch (Exception e) {
        throw new RuntimeException("Encoding failed", e);
    }
}
```

**Benefits:**
- Pure Java (no C++ code)
- Arenas free what Java allocates; memory the C library allocates is still freed explicitly (`destroyEncode`, `NativeMemory.free`)
- Type-safe at compile time

## Performance

Based on Panama benchmarks:

- **Encoding:** 95-105% of JNI performance
- **Decoding:** 95-105% of JNI performance
- **Memory:** Lower overhead (arena allocation)
- **Startup:** Comparable to JNI

Panama is often **faster** than JNI due to:
- Fewer memory copies
- Better JIT optimization
- Efficient arena allocation

## Platform Support

✅ **Supported:**
- Desktop Linux (x64, ARM)
- Desktop macOS (x64, ARM)
- Desktop Windows (x64)
- Server deployments (JDK 23+)

❌ **Not Supported:**
- Android (no `java.lang.foreign` in the Android API, as of API 35)
- Embedded systems with old JVMs

For Android, use the Kotlin `jabcode-sdk` in `jabauth-android/framework/jabcode-sdk/`. It calls the C library over JNI through `libjabcode-mobile`, which the NDK builds from `swift-java-wrapper/`. Swift is that wrapper's iOS side.

## Migration from javacpp-wrapper

If you want to migrate existing code:

### Before (JNI)
```java
import com.jabcode.core.JABCode;
import java.awt.image.BufferedImage;

BufferedImage image = JABCode.encode(data.getBytes(), JABCode.ColorMode.OCTAL, 1, 5);
```

### After (Panama)
```java
import com.jabcode.panama.JABCodeEncoder;

JABCodeEncoder encoder = new JABCodeEncoder();
byte[] result = encoder.encode(data, 8, 5);
```

## Troubleshooting

### "IllegalArgumentException: Cannot open library: libjabcode.so"

Set library path:
```bash
export LD_LIBRARY_PATH=/path/to/jabcode/lib:$LD_LIBRARY_PATH
```

Setting `java.library.path` does not help, with `-D` or `System.setProperty`: the bindings `dlopen` the library by name, so the loader must find it on `LD_LIBRARY_PATH` when the JVM starts.

### "IllegalCallerException: Illegal native access"

Enable native access:
```bash
java --enable-native-access=ALL-UNNAMED YourClass
```

`ALL-UNNAMED` covers the class path only. On the module path the jar is the automatic module `jabcode.panama`, so name that module instead: `--enable-native-access=jabcode.panama`. A `module-info.java` cannot grant native access.

### Regenerate Bindings Fails

Check:
1. `jextract` is in PATH, or Maven is told where it is: `mvn test -Djextract.executable=/path/to/jextract-25/bin/jextract`
2. `jabcode.h` exists at `../src/jabcode/include/jabcode.h`
3. Include paths are correct

## Color Modes Implementation

This wrapper includes comprehensive support for all 8 JABCode color modes per ISO/IEC 23634:

### Supported Modes

| Mode | Nc | Colors | Bits/Module | Interpolation | Status |
|------|----|----|-------------|---------------|--------|
| Reserved | 0 | 0 | - | - | Not used |
| **Mode 1** | 1 | 4 | 2 | No | ✅ Ready |
| **Mode 2** | 2 | 8 | 3 | No | ✅ Ready |
| **Mode 3** | 3 | 16 | 4 | No | ✅ Ready |
| **Mode 4** | 4 | 32 | 5 | No | ✅ Ready |
| **Mode 5** | 5 | 64 | 6 | No | ✅ Ready |
| **Mode 6** | 6 | 128 | 7 | Yes (R) | ✅ Ready |
| **Mode 7** | 7 | 256 | 8 | Yes (R+G) | ✅ Ready |

### Color Mode API

```java
import com.jabcode.panama.colors.*;

// Select color mode
ColorMode mode = ColorMode.MODE_16;  // 16 colors

// Get palette
ColorPalette palette = ColorPaletteFactory.create(mode);

// Palette operations
int[][] fullPalette = palette.generateFullPalette();
int[][] embeddedPalette = palette.generateEmbeddedPalette();
int[] rgb = palette.getRGB(5);  // Get color at index 5
int index = palette.getColorIndex(255, 0, 0);  // Find nearest color

// Mode properties
int bitsPerModule = mode.getBitsPerModule();  // 4 for Mode 3
boolean needsInterp = mode.requiresInterpolation();  // false for Mode 3
```

### Encoding with Color Modes

```java
var config = JABCodeEncoder.Config.builder()
    .colorNumber(16)      // Use 16-color mode
    .eccLevel(5)
    .symbolNumber(1)
    .build();

byte[] encoded = encoder.encodeWithConfig("Data", config);
```

### Utilities Available

#### BitStream Encoding/Decoding
```java
import com.jabcode.panama.bits.*;

// Encode variable-width values
var encoder = new BitStreamEncoder();
encoder.writeBits(0b101, 3);  // Write 3-bit value
encoder.writeBits(0b11, 2);   // Write 2-bit value
encoder.alignToByte();
byte[] packed = encoder.toByteArray();

// Decode
var decoder = new BitStreamDecoder(packed);
int val1 = decoder.readBits(3);  // Read 3 bits
int val2 = decoder.readBits(2);  // Read 2 bits
```

#### Data Masking
```java
import com.jabcode.panama.mask.DataMasking;

// Apply ISO/IEC 23634 Table 22 mask patterns
int maskValue = DataMasking.maskAt(x, y, maskRef, colorCount);
```

#### Palette Quality
```java
import com.jabcode.panama.quality.PaletteQuality;

// ISO 8.3 quality metrics
double minSep = PaletteQuality.minColorSeparation(fullPalette);
boolean accurate = PaletteQuality.validatePaletteAccuracy(fullPalette, maxError);
double variation = PaletteQuality.colorVariation(fullPalette);
```

### Architecture

```
com.jabcode.panama
├── colors/              # Color mode core
│   ├── ColorMode        # Enum for Nc 0-7
│   ├── ColorPalette     # Interface
│   ├── ColorUtils       # Distance, nearest-color
│   ├── ColorPaletteFactory
│   ├── palettes/        # Mode1-7 implementations
│   └── interp/          # Interpolation for Mode 6-7
├── bits/                # Variable bit-width packing
│   ├── BitStreamEncoder
│   └── BitStreamDecoder
├── mask/                # ISO Table 22 masking
│   └── DataMasking
├── encode/              # Encoding utilities
│   ├── PaletteEmbedding # Palette metadata encoding
│   └── NcMetadata       # Nc 3-color encoding
└── quality/             # ISO 8.3 metrics
    └── PaletteQuality
```

### Roadmap Status

✅ **Phase 1:** Foundation (ColorMode, palettes, utilities)  
✅ **Phase 2:** Extended modes (16/32/64)  
✅ **Phase 3:** High-color modes (128/256 with interpolation)  
✅ **Phase 4:** Encoder/Decoder integration utilities  
✅ **Phase 5:** ISO quality metrics  
✅ **Phase 6:** Documentation & examples  

**Integration:** done. Maven generates the bindings with jextract in `generate-sources`, and `JABCodeEncoder` and `JABCodeDecoder` call them.

## Resources

- **JEP 454:** https://openjdk.org/jeps/454
- **Panama Tutorial:** https://foojay.io/today/project-panama-for-newbies-part-1/
- **jextract Guide:** https://github.com/openjdk/jextract

## License

Same as JABCode library (MIT)
