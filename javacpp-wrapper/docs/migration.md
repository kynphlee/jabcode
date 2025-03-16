# Migration Guide: Moving to OptimizedJABCode

This guide explains how to migrate from the deprecated SimpleJABCode and JABCodeWrapper classes to the new OptimizedJABCode implementation.

## Why Migrate?

The OptimizedJABCode implementation offers several advantages:

- **Direct JNI Access**: Bypasses multiple wrapper layers for better performance and reliability
- **Modern API**: Uses the builder pattern for a more flexible and intuitive API
- **Better Error Handling**: Provides detailed error messages and proper exception handling
- **Enhanced Features**: Includes image processing options and more configuration options
- **Improved Performance**: Eliminates unnecessary abstraction layers for better performance
- **Proper Resource Management**: Ensures resources are properly cleaned up

## Migration Examples

### From SimpleJABCode

#### Before:

```java
// Generate a JABCode with default settings
SimpleJABCode.generateJABCode("Hello, JABCode!", "jabcode.png");

// Generate a JABCode with custom color mode
SimpleJABCode.generateJABCodeWithColorMode("Hello, JABCode!", "jabcode_16.png", SimpleJABCode.COLOR_MODE_16);

// Decode a JABCode
String decodedText = SimpleJABCode.decodeJABCode("jabcode.png");
```

#### After:

```java
// Generate a JABCode with default settings
BufferedImage image = OptimizedJABCode.encode("Hello, JABCode!");
OptimizedJABCode.save(image, "jabcode.png");

// Generate a JABCode with custom color mode
BufferedImage image = OptimizedJABCode.builder()
    .withData("Hello, JABCode!")
    .withColorMode(OptimizedJABCode.ColorMode.HEXADECIMAL) // 16 colors
    .build();
OptimizedJABCode.save(image, "jabcode_16.png");

// Decode a JABCode
String decodedText = OptimizedJABCode.decodeToString("jabcode.png");
```

### From JABCodeWrapper

#### Before:

```java
// Create an encoder
JABCodeWrapper.Encoder encoder = JABCodeWrapper.createEncoder(JABCodeWrapper.COLOR_MODE_16, 1);

// Generate a JABCode
encoder.generate("Hello, JABCode!", "jabcode.png");

// Generate a JABCode with default settings
JABCodeWrapper.generateJABCode("Hello, JABCode!", "jabcode.png");

// Generate a JABCode with custom color mode
JABCodeWrapper.generateJABCodeWithColorMode("Hello, JABCode!", "jabcode_16.png", JABCodeWrapper.COLOR_MODE_16);
```

#### After:

```java
// Create a builder and generate a JABCode
BufferedImage image = OptimizedJABCode.builder()
    .withData("Hello, JABCode!")
    .withColorMode(OptimizedJABCode.ColorMode.HEXADECIMAL) // 16 colors
    .withSymbolCount(1)
    .build();
OptimizedJABCode.save(image, "jabcode.png");

// Generate a JABCode with default settings
BufferedImage image = OptimizedJABCode.encode("Hello, JABCode!");
OptimizedJABCode.save(image, "jabcode.png");

// Generate a JABCode with custom color mode
BufferedImage image = OptimizedJABCode.builder()
    .withData("Hello, JABCode!")
    .withColorMode(OptimizedJABCode.ColorMode.HEXADECIMAL) // 16 colors
    .build();
OptimizedJABCode.save(image, "jabcode_16.png");
```

## Color Mode Mapping

| Old Constant | New Enum Value |
|--------------|---------------|
| `SimpleJABCode.COLOR_MODE_4` | `OptimizedJABCode.ColorMode.QUATERNARY` |
| `SimpleJABCode.COLOR_MODE_8` | `OptimizedJABCode.ColorMode.OCTAL` |
| `SimpleJABCode.COLOR_MODE_16` | `OptimizedJABCode.ColorMode.HEXADECIMAL` |
| `SimpleJABCode.COLOR_MODE_32` | `OptimizedJABCode.ColorMode.MODE_32` |
| `SimpleJABCode.COLOR_MODE_64` | `OptimizedJABCode.ColorMode.MODE_64` |
| `SimpleJABCode.COLOR_MODE_128` | `OptimizedJABCode.ColorMode.MODE_128` |
| `SimpleJABCode.COLOR_MODE_256` | `OptimizedJABCode.ColorMode.MODE_256` |

## Additional Features in OptimizedJABCode

### Image Processing Control

```java
// Disable image processing for maximum performance
BufferedImage image = OptimizedJABCode.builder()
    .withData("Hello, JABCode!")
    .withImageProcessing(false)
    .build();
```

### Error Correction Level

```java
// Set a custom error correction level (0-10)
BufferedImage image = OptimizedJABCode.builder()
    .withData("Hello, JABCode!")
    .withEccLevel(5)
    .build();
```

### Extended Decoding

```java
// Decode with extended information
OptimizedJABCode.DecodedResult result = OptimizedJABCode.decodeEx(image, 10);
byte[] data = result.getData();
String text = result.getDataAsString();
```

## Error Handling

OptimizedJABCode uses exceptions for error handling, which provides more detailed error information:

```java
try {
    BufferedImage image = OptimizedJABCode.encode("Hello, JABCode!");
    OptimizedJABCode.save(image, "jabcode.png");
} catch (OptimizedJABCode.JABCodeException e) {
    System.err.println("Error generating JABCode: " + e.getMessage());
} catch (IOException e) {
    System.err.println("Error saving JABCode: " + e.getMessage());
}
```

## Timeline for Deprecation

The SimpleJABCode and JABCodeWrapper classes are now deprecated and will be removed in a future release. It is recommended to migrate to OptimizedJABCode as soon as possible.
