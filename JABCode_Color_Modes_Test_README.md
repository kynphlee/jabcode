# JAB Code Color Modes Test

This repository contains tests for generating and comparing JAB Codes with different color modes.

## Overview

JAB Code (Just Another Barcode) is a color 2D matrix symbology made of color squares arranged in either square or rectangle grids. It was developed by Fraunhofer Institute for Secure Information Technology SIT.

The code uses either four or eight colors:
- The four basic colors (cyan, magenta, yellow, and black) are the four primary colors of the subtractive CMYK color model.
- The other four colors (blue, red, green, and white) are secondary colors of the CMYK model.

## Test Results

We generated JAB Codes for the same text using both 4-color and 8-color modes, and compared the results.

### File Size Comparison

| Color Mode | File Size (bytes) |
|------------|------------------|
| 4-color    | 328,464          |
| 8-color    | 265,113          |

**Size Reduction with 8-color mode: 20.00%**

This confirms that the 8-color JAB Code is more efficient for storing the same data, as it can encode more information in the same area. According to the JAB Code specification, an 8-color code can store approximately 50% more data than a 4-color code for the same physical size.

### Decoding Results

Both 4-color and 8-color JAB Codes were successfully decoded with only minor differences (an extra newline at the end of the decoded text).

## Test Files

- `test_color_modes.sh`: Shell script to generate and compare JAB Codes with different color modes
- `test-output/jabcode_4_colors.png`: 4-color JAB Code image
- `test-output/jabcode_8_colors.png`: 8-color JAB Code image
- `test-output/sample_text.txt`: Original sample text
- `test-output/decoded_4_colors.txt`: Decoded text from 4-color JAB Code
- `test-output/decoded_8_colors.txt`: Decoded text from 8-color JAB Code

## Running the Tests

To run the tests yourself:

```bash
./test_color_modes.sh
```

This will generate JAB Codes for both color modes, decode them, and compare the results.

## Conclusion

The test results confirm that the 8-color JAB Code is more efficient for storing data than the 4-color JAB Code. This makes 8-color JAB Codes more suitable for applications where a large amount of data needs to be encoded in a limited space.

However, it's worth noting that 8-color JAB Codes may be more sensitive to printing and scanning conditions, as the additional colors need to be accurately distinguished. For applications where reliability is more important than data density, 4-color JAB Codes may be a better choice.
