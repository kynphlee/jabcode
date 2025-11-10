# OptimizedJABCode

A high-performance implementation of the JABCode library for Java applications.

## Overview

OptimizedJABCode is a direct interface to the native JABCode library with minimal overhead and maximum performance. It provides a clean, fluent API for generating and decoding JABCode barcodes.

## Features

- Direct JNI access to the native JABCode library for maximum performance
- Fluent builder API for easy configuration
- Support for all JABCode color modes (2, 4, 8, 16, 32, 64, 128, 256 colors)
- Customizable error correction levels
- Optional image processing for enhanced visual quality
- Performance-optimized for critical applications

## Usage

### Basic Usage

```java
// Generate a JABCode with default settings (8 colors, 1 symbol, ECC level 3)
BufferedImage image = OptimizedJABCode.encode("Hello, JABCode!");

// Save the image to a file
OptimizedJABCode.save(image, "jabcode.png");
```

### Advanced Usage with Builder

```java
// Generate a JABCode with custom settings
BufferedImage image = OptimizedJABCode.builder()
    .withData("Hello, JABCode with custom settings!")
    .withColorMode(OptimizedJABCode.ColorMode.HEXADECIMAL) // 16 colors
    .withSymbolCount(2)
    .withEccLevel(5)
    .withImageProcessing(true) // Enable image processing for better visual quality
    .build();

// Save the image to a file
OptimizedJABCode.save(image, "jabcode_custom.png");
```

### Decoding

```java
// Decode a JABCode from a file
byte[] data = OptimizedJABCode.decode("jabcode.png");
String text = new String(data);
System.out.println("Decoded text: " + text);

// Or decode directly to a string
String text = OptimizedJABCode.decodeToString("jabcode.png");
System.out.println("Decoded text: " + text);
```

### Extended Decoding

```java
// Decode a JABCode with extended information
OptimizedJABCode.DecodedResult result = OptimizedJABCode.decodeEx(image, 10);
byte[] data = result.getData();
String text = result.getDataAsString();
System.out.println("Decoded text: " + text);
```

## Color Modes

OptimizedJABCode supports the following color modes:

- `BINARY` - 2 colors (black and white)
- `QUATERNARY` - 4 colors
- `OCTAL` - 8 colors (default)
- `HEXADECIMAL` - 16 colors
- `MODE_32` - 32 colors
- `MODE_64` - 64 colors
- `MODE_128` - 128 colors
- `MODE_256` - 256 colors

## Performance Considerations

For maximum performance, you can disable image processing:

```java
BufferedImage image = OptimizedJABCode.builder()
    .withData("Performance-critical data")
    .withImageProcessing(false) // Disable image processing for maximum performance
    .build();
```

When decoding, you can also disable image processing:

```java
byte[] data = OptimizedJABCode.decode(image, false); // Disable image processing for maximum performance
```

## Building and Testing

To build and test the OptimizedJABCode class:

1. Compile the native library:
   ```bash
   scripts/javacpp-wrapper/build.sh --all
   ```

2. Run the JUnit tests:
   ```bash
   scripts/javacpp-wrapper/test-optimized.sh
   ```

The tests will verify all functionality of the OptimizedJABCode class, including:
- Encoding with default settings
- Encoding with custom settings
- Testing all color modes
- Testing with image processing disabled
- Testing with byte array data
- Testing error cases
- Testing the ColorMode enum
- Testing encode and decode roundtrip (if decoding is supported)

## License

This library is licensed under the same terms as the original JABCode library.
