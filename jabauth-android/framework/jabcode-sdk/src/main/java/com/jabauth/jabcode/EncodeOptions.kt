package com.jabauth.jabcode

/**
 * Options for JABCode encoding
 *
 * Configures how data should be encoded into a JABCode image.
 *
 * @property width Desired width of the output image in pixels
 * @property height Desired height of the output image in pixels
 * @property colorMode Number of colors to use (2, 4, or 8)
 * @property errorCorrectionLevel Error correction level (0-10, higher = more redundancy)
 * @property moduleSize Size of each module (pixel) in the barcode (default: auto-calculated)
 */
data class EncodeOptions(
    val width: Int = 512,
    val height: Int = 512,
    val colorMode: ColorMode = ColorMode.COLOR_8,
    val errorCorrectionLevel: Int = 5,
    val moduleSize: Int = -1 // -1 means auto-calculate
) {
    init {
        require(width > 0) { "Width must be positive" }
        require(height > 0) { "Height must be positive" }
        require(errorCorrectionLevel in 0..10) { "Error correction level must be 0-10" }
        require(moduleSize == -1 || moduleSize > 0) { "Module size must be positive or -1 (auto)" }
    }
}

/**
 * JABCode color modes
 *
 * Determines the number of colors used in the barcode.
 * More colors = higher data density, but requires better camera quality.
 */
enum class ColorMode(val value: Int) {
    /** 2-color mode (black and white) - most compatible */
    COLOR_2(2),
    
    /** 4-color mode - good balance of density and readability */
    COLOR_4(4),
    
    /** 8-color mode - highest density, requires good camera */
    COLOR_8(8)
}
