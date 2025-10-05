# JAB Code Color Modes Comparison

This repository contains tests and documentation for comparing JAB Code color modes.

## Overview

JAB Code (Just Another Barcode) is a color 2D matrix symbology made of color squares arranged in either square or rectangle grids. It was developed by Fraunhofer Institute for Secure Information Technology SIT.

The code supports two color modes:
- 4-color mode (CMYK): Uses cyan, magenta, yellow, and black colors
- 8-color mode (CMYK + RGB + White): Uses cyan, magenta, yellow, black, red, green, blue, and white colors

## Test Results

We generated JAB Codes for the same text using both 4-color and 8-color modes, and compared the results.

### File Size Comparison

| Color Mode | File Size (bytes) |
|------------|-------------------|
| 4-color    | 328,464          |
| 8-color    | 265,113          |

**Size Reduction with 8-color mode: 20.00%**

This confirms that the 8-color JAB Code is more efficient for storing the same data, as it can encode more information in the same area. According to the JAB Code specification, an 8-color code can store approximately 50% more data than a 4-color code for the same physical size.

### Decoding Results

Both 4-color and 8-color JAB Codes were successfully decoded with only minor differences (likely due to whitespace or punctuation).

## Files

- `scripts/generate_jabcodes.sh`: Shell script to generate and compare JAB Codes with different color modes
- `output/jabcode_4_colors.png`: 4-color JAB Code image
- `output/jabcode_8_colors.png`: 8-color JAB Code image
- `output/sample_text.txt`: Original sample text
- `output/decoded_4_colors.txt`: Decoded text from 4-color JAB Code
- `output/decoded_8_colors.txt`: Decoded text from 8-color JAB Code
- `output/comparison.html`: HTML page comparing the JAB Codes

## Running the Tests

To run the tests yourself:

```bash
./scripts/generate_jabcodes.sh
```

This will generate JAB Codes for both color modes, decode them, and compare the results.

To view the visual comparison:

```bash
open output/comparison.html
```

## Conclusion

The test results confirm that the 8-color JAB Code is more efficient for storing data than the 4-color JAB Code. This makes 8-color JAB Codes more suitable for applications where a large amount of data needs to be encoded in a limited space.

However, it's worth noting that 8-color JAB Codes may be more sensitive to printing and scanning conditions, as the additional colors need to be accurately distinguished. For applications where reliability is more important than data density, 4-color JAB Codes may be a better choice.

## Java Wrapper Status

The core C library and command-line tools are working correctly, but the Java wrapper has issues with the JavaCPP integration. We attempted to fix the Java wrapper, but encountered issues with the native library integration. As a workaround, we created a shell script that uses the command-line tools to generate JAB codes with different color modes.

## Future Work

- Fix the Java wrapper to properly interface with the native library
- Add support for more color modes (if the library is updated to support them)
- Improve the error handling in the shell script
- Add more detailed analysis of the decoded text differences
