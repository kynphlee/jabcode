# JABCode Color Modes Support

This document describes the JABCode color modes support in the Java wrapper.

## Overview

JABCode (Just Another Barcode) is a color 2D matrix symbology made of color squares arranged in either square or rectangle grids. It supports multiple color modes, which determine the number of colors used in the barcode.

The Java wrapper now supports all color modes:

- **Binary (2 colors)**: Black and white
- **Quaternary (4 colors)**: Cyan, Magenta, Yellow, Black (CMYK)
- **Octal (8 colors)**: CMYK + Red, Green, Blue, White
- **Hexadecimal (16 colors)**: Extended color palette
- **32 colors**: Extended color palette
- **64 colors**: Extended color palette
- **128 colors**: Extended color palette
- **256 colors**: Extended color palette

## Implementation Details

The native JABCode library only directly supports 4 and 8 color modes. For other color modes, the Java wrapper implements the following strategies:

### Binary Mode (2 colors)

For binary mode, the wrapper uses the 4-color mode of the native library but restricts the palette to only black and white colors.

### Higher Color Modes (16, 32, 64, 128, 256 colors)

For higher color modes, the wrapper implements:

1. **Color Quantization**: Maps higher color counts to 8 colors using color quantization algorithms
2. **Custom Palettes**: Creates mathematically distributed color palettes for each mode
3. **Dithering**: Implements Floyd-Steinberg dithering to simulate higher color counts

## Usage

To use different color modes in your code:

```java
import com.jabcode.OptimizedJABCode;
import com.jabcode.OptimizedJABCode.ColorMode;

// Generate a JABCode with 8 colors (default)
BufferedImage octalImage = OptimizedJABCode.builder()
    .withData("Hello, JABCode!")
    .withColorMode(ColorMode.OCTAL)
    .build();

// Generate a JABCode with 4 colors
BufferedImage quaternaryImage = OptimizedJABCode.builder()
    .withData("Hello, JABCode!")
    .withColorMode(ColorMode.QUATERNARY)
    .build();

// Generate a JABCode with 2 colors
BufferedImage binaryImage = OptimizedJABCode.builder()
    .withData("Hello, JABCode!")
    .withColorMode(ColorMode.BINARY)
    .build();

// Generate a JABCode with 16 colors
BufferedImage hexImage = OptimizedJABCode.builder()
    .withData("Hello, JABCode!")
    .withColorMode(ColorMode.HEXADECIMAL)
    .build();

// Higher color modes (32, 64, 128, 256)
BufferedImage mode32Image = OptimizedJABCode.builder()
    .withData("Hello, JABCode!")
    .withColorMode(ColorMode.MODE_32)
    .build();
```

## Testing

You can test all color modes using the provided test script:

```bash
./test_color_modes.sh
```

This will generate JABCodes in all supported color modes and save them to the `test-output` directory.

## Performance Considerations

- **4 and 8 color modes** are natively supported and offer the best performance
- **Binary mode (2 colors)** has minimal overhead
- **Higher color modes (16+ colors)** involve additional processing for color quantization and dithering

## Data Capacity

The data capacity of JABCode varies with the color mode:

- **Binary (2 colors)**: Lowest capacity
- **Quaternary (4 colors)**: 2x capacity of binary
- **Octal (8 colors)**: 3x capacity of binary
- **Higher color modes**: Simulated through dithering, effective capacity is similar to 8-color mode

## Compatibility

All color modes are compatible with the JABCode specification, but for optimal scanning and decoding:

- **4 and 8 color modes** are recommended for most applications
- **Binary mode** offers the highest compatibility with standard barcode scanners
- **Higher color modes** may require specialized scanning equipment

## Future Improvements

Future versions may include:

- Direct support for higher color modes in the native library
- Improved color quantization algorithms
- Enhanced dithering techniques
- Color calibration for different printing and scanning environments
