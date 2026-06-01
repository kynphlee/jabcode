package com.jabauth.jabcode

/**
 * Options for JABCode encoding
 *
 * Configures how data should be encoded into a JABCode image.
 *
 * @property width Desired width of the output image in pixels
 * @property height Desired height of the output image in pixels
 * @property colorMode Number of colors to use (2, 4, 8, 16, 32, 64, 128, or 256)
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
    
    /** 8-color mode - default, good density and compatibility */
    COLOR_8(8),
    
    /** 16-color mode - higher density, needs very good camera */
    COLOR_16(16),
    
    /** 32-color mode - very high density, requires excellent camera */
    COLOR_32(32),
    
    /** 64-color mode - extreme density, requires professional camera */
    COLOR_64(64),
    
    /** 128-color mode - very high density, requires specialized equipment */
    COLOR_128(128),

    /** 256-color mode - maximum spec-defined density (Nc=7 in JABCode standard).
     *  Decoded successfully on Galaxy S25 via the 2026-06-01 v7 validation
     *  traces (22 native successes with colorNumber=256, text "HELLO-Nc-7").
     *  Encoder-side support for Nc=7 has known issues — see
     *  docs/cassandra-register/H_png_roundtrip_high_nc.md sub-hypothesis #2. */
    COLOR_256(256);

    companion object {
        /**
         * Get ColorMode from integer value.
         *
         * Returns COLOR_8 as default if value is not recognized — guards
         * against forward-incompatible Nc values from native decoders that
         * may add color modes beyond what this SDK knows about.
         *
         * Bayesian Council session bc-2026-06-01-04 caught the prior
         * absence of COLOR_256: when the native bridge returned
         * colorNumber=256 for successful Nc=7 decodes, this fallback
         * silently mapped them to COLOR_8 — surfaced as the user's
         * observation that "nc=7 decodes report COLOR_8 but return the
         * 256-color text." The enum now includes COLOR_256 so the
         * mapping is bijective for spec-defined Nc values 0..7.
         */
        fun fromValue(value: Int): ColorMode {
            return values().find { it.value == value } ?: COLOR_8
        }
    }
}
