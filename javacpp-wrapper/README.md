# JABCode Java Wrapper

<div align="center">
  <img src="../docs/img/jabcode_logo.png" alt="JABCode Logo" width="120"/>
  <h3>Java Library for JABCode Generation and Decoding</h3>
</div>

## Overview

This is a Java wrapper for the [JABCode](https://jabcode.org) library, providing a Java API for generating and decoding JABCode (Just Another Barcode) 2D color barcodes. JABCode is a high-capacity 2D color barcode that can encode more data than traditional black/white codes.

## Features

- Generate JABCode barcodes with customizable parameters
- Decode JABCode barcodes from images
- Support for different color modes (2, 4, 8, 16, 32, 64, 128, 256 colors)
- Support for multiple symbols in a single barcode
- Error correction capabilities
- Image processing options for enhanced visual quality

## Performance

### Recommended Configuration

**8-color (OCTAL) mode is recommended** for all production use cases. Comprehensive benchmarking shows 8-color outperforms 4-color in every metric:

| Metric | 4-Color | 8-Color | Improvement |
|--------|---------|---------|-------------|
| **Speed** | 14 KB/s | 16 KB/s | **+32% faster** |
| **Memory** | 200 KB/op | 105 KB/op | **-47% less** |
| **File Size** | 79 KB (1KB data) | 61 KB (1KB data) | **-24% smaller** |

**Why 8-color is faster:** 8-color packs 50% more data per module (3 bits vs 2 bits), resulting in smaller symbols that require less processing for mask evaluation, LDPC encoding/decoding, and image operations.

### Performance Characteristics

- **Throughput**: ~15-18 KB/s for typical payloads (100B-1KB)
- **JNI Overhead**: 10-19ms fixed cost per operation
- **File Size**: Expect 60-80x overhead vs payload size (PNG compression is inefficient for multi-color patterns)
- **Memory**: 105-200 KB per encode/decode operation depending on color mode

### Best Practices

✅ **Recommended Use Cases:**
- Batch processing and archival applications
- Payloads ≥100 bytes
- High-capacity data storage where capacity matters more than file size

⚠️ **Not Recommended For:**
- Real-time interactive applications (use QR Code instead)
- Small payloads <100 bytes (JNI overhead dominates)
- Bandwidth-constrained scenarios (large file sizes)

### Performance APIs

**For Maximum Performance**, choose the right API for your use case:

| Use Case | API | Performance | When to Use |
|----------|-----|-------------|-------------|
| Single code | `encode()` | Baseline | One-off encoding |
| Batch (10-100 codes) | `encodeBatch()` | **+40-55% faster** | Different data, same config |
| High-volume server | `encodeWithPool()` | **+50-70% faster** | Repeated operations, long-running |

**Example - Batch Processing**:
```java
// Batch API: 40-55% faster than individual encode()
List<byte[]> payloads = Arrays.asList(
    "Message 1".getBytes(),
    "Message 2".getBytes(),
    "Message 3".getBytes()
);
List<BufferedImage> images = OptimizedJABCode.encodeBatch(
    payloads, 
    OptimizedJABCode.ColorMode.OCTAL
);
```

**Example - Server Application with Pooling**:
```java
// Pool API: Reuses encoder across requests (90% reuse rate in tests)
List<BufferedImage> images = OptimizedJABCode.encodeWithPool(payloads);

// Or for fine-grained control:
EncoderPool pool = OptimizedJABCode.getEncoderPool();
try (PooledEncoder encoder = pool.acquire(ColorMode.OCTAL, 1, 3)) {
    BufferedImage img = encoder.encode("Data".getBytes());
}
System.out.println(pool.getStats()); // Track pool efficiency
```

### File Size Optimization

**Optimized PNG Output**: Use indexed color mode to reduce file sizes by **90%**:

```java
BufferedImage img = OptimizedJABCode.encode("Hello, World!");

// Standard save: ~10.8 KB (32-bit ARGB)
OptimizedJABCode.saveToFile(img, "standard.png");

// Optimized save: ~1.1 KB (8-bit indexed) - 90% smaller!
OptimizedJABCode.saveOptimized(img, "optimized.png");

// Analyze compression potential
PNGOptimizer.CompressionStats stats = OptimizedJABCode.analyzeCompression(img);
System.out.println(stats);
// Output: CompressionStats{colors=8, ARGB=10,817 bytes, indexed=1,070 bytes, saved=9,747 bytes (90.1% smaller)}
```

**Why it works**: JABCode images use only 4-256 colors, but standard PNG saves as 32-bit ARGB (16.7M colors). Indexed color mode uses 1-8 bits per pixel instead of 32 bits, and is 100% lossless.

### Production Status

- **4/8-Color Modes**: ✅ Production-ready (stable, well-tested)
- **High-Color Modes (≥16)**: ⚠️ Experimental (requires additional tuning for reliable decode)

For detailed performance analysis and optimization roadmap, see `memory-bank/diagnostics/performance_analysis_and_optimization_plan.md`.

## Requirements

- Java 11 or higher
- Maven 3.6 or higher
- C/C++ compiler (GCC, Clang, or MSVC)
- Make

## Project Structure

```
javacpp-wrapper/
├── src/
│   ├── main/
│   │   ├── c/                  # C/C++ wrapper code
│   │   │   ├── jabcode_c_wrapper.cpp
│   │   │   ├── jabcode_c_wrapper.h
│   │   │   └── JABCodeNative_jni.cpp
│   │   └── java/               # Java wrapper classes
│   │       └── com/
│   │           └── jabcode/    # Main package
│   └── test/                   # Test classes
├── scripts/                    # Build scripts
│   ├── build.sh                # Main build script
│   ├── build-core-library.sh   # Script to build core library
│   ├── build-java-wrapper.sh   # Script to build Java wrapper
│   ├── build-jni-interface.sh  # Script to build JNI interface
│   └── ...
├── lib/                        # Output directory for compiled libraries
├── build/                      # Build artifacts
└── target/                     # Maven build output
```

## Building the Library

### Using the Build Script

The easiest way to build the library is to use the provided build script:

```bash
cd scripts/javacpp-wrapper
./build.sh --all
```

This script automates the entire build process, including:
- Building the core JABCode C/C++ library
- Building the JNI interface
- Building the Java wrapper
- Verifying the build

### Build Options

The build script supports the following options:

```
Usage: ./build.sh [options]
Options:
  --core      Build the core JABCode C/C++ library
  --jni       Build the JNI interface
  --java      Build the Java wrapper
  --fixed     Apply fixes to the build process
  --tests     Run tests after building
  --all       Build everything (core, JNI, Java, fixed)
  --clean     Clean the project before building
  --verbose   Show verbose output
  --help      Show this help message
```

### Manual Build Process

If you prefer to build the components manually, follow these steps:

1. Build the core JABCode C/C++ library:
   ```bash
   cd scripts/javacpp-wrapper
   ./build-core-library.sh
   ```

2. Build the JNI interface:
   ```bash
   cd scripts/javacpp-wrapper
   ./build-jni-interface.sh
   ```

3. Build the Java wrapper:
   ```bash
   cd scripts/javacpp-wrapper
   ./build-java-wrapper.sh
   ```

4. Verify the build:
   ```bash
   cd scripts/javacpp-wrapper
   ./verify-full-build.sh
   ```

### Building with Maven

You can also build the project using Maven:

```bash
cd javacpp-wrapper
mvn clean package
```

This will compile the Java code and package it into a JAR file, but it will not build the native libraries. You should run the build script first to build the native libraries.

## Usage

### Basic Usage

```java
import com.jabcode.JABCode;

public class Example {
    public static void main(String[] args) {
        // Generate a JABCode with default settings (8-color OCTAL mode - recommended)
        BufferedImage image = JABCode.encode("Hello, JABCode!");
        JABCode.save(image, "jabcode.png");
        
        // Decode a JABCode
        String decodedText = JABCode.decodeToString(new File("jabcode.png"));
        System.out.println("Decoded text: " + decodedText);
        
        // Generate with explicit 8-color mode (same as default)
        BufferedImage explicitImage = JABCode.builder()
            .withData("Hello, JABCode!")
            .withColorMode(JABCode.ColorMode.OCTAL)  // 8-color (recommended)
            .build();
        JABCode.save(explicitImage, "jabcode_8color.png");
    }
}
```

**Note:** The default 8-color (OCTAL) mode provides the best balance of speed, memory efficiency, and file size. See the Performance section for details.

### Advanced Usage

For more advanced usage, you can use the builder pattern to customize the JABCode:

```java
import com.jabcode.JABCode;

public class AdvancedExample {
    public static void main(String[] args) {
        // Generate a JABCode with custom settings
        BufferedImage image = JABCode.builder()
            .withData("Hello, JABCode with custom settings!")
            .withColorMode(JABCode.ColorMode.MODE_64)
            .withSymbolCount(2)
            .withEccLevel(5)
            .withImageProcessing(true)
            .build();
        
        // Save the image
        JABCode.save(image, "advanced_jabcode.png");
    }
}
```

## Troubleshooting

### Common Issues

1. **Library not found**: Make sure the native libraries are in the correct location. You can use the `fix-library-names.sh` script to fix the library names.

2. **Symbol lookup error**: This can happen if the native library is not properly linked with the required dependencies. Try running the `build.sh` script with the `--fixed` option.

3. **Unsatisfied link error**: This can happen if the native library is not compatible with your platform. Make sure you're using the correct version of the native library for your platform.

### Debugging

If you encounter issues, you can run the build script with the `--verbose` option to get more detailed output:

```bash
cd scripts/javacpp-wrapper
./build.sh --all --verbose
```

## License

This project has a dual licensing structure:

- **Java Wrapper**: Licensed under the Apache License 2.0
- **JABCode C/C++ Library**: Licensed under the GNU Lesser General Public License v2.1 (LGPL v2.1)

When using this software, you must comply with both licenses.

## Acknowledgments

- JABCode was developed by Fraunhofer SIT
- This Java wrapper uses JavaCPP for JNI bindings
- The native JABCode library is available at [github.com/jabcode/jabcode](https://github.com/jabcode/jabcode)

This should be in the my-branch of my repo
