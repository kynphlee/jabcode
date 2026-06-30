package com.jabcode.panama;

import java.util.Set;

/**
 * Single authoritative surface for JABCode encode parameter limits and their
 * validation.
 *
 * <p>All ranges are pinned to the C codec (the source of truth):</p>
 * <ul>
 *   <li><b>Colour number</b> — the eight ISO/extension colour modes
 *       {@code 2,4,8,16,32,64,128,256} (Nc = 0..7).</li>
 *   <li><b>ECC level</b> — {@code 0..10}. The encoder indexes the
 *       {@code ecclevel2wcwr[11][2]} lookup table directly with the symbol's
 *       ECC level (see {@code src/jabcode/encoder.h:231} and the uses in
 *       {@code src/jabcode/encoder.c}, e.g. line 1877
 *       {@code enc->symbols[0].wcwr[0] = ecclevel2wcwr[enc->symbol_ecc_levels[0]][0]}).
 *       The table has 11 rows (indices 0..10) and the codec performs no clamp,
 *       so the authoritative maximum is 10. The default is
 *       {@code DEFAULT_ECC_LEVEL = 3} ({@code src/jabcode/include/jabcode.h:35}).</li>
 *   <li><b>Symbol number</b> — {@code 1..61} (master plus up to 60 docked
 *       slaves).</li>
 *   <li><b>Symbol version</b> — {@code 1..32} per axis.</li>
 * </ul>
 *
 * <p>This class is not instantiable; it exposes constants and {@code validate*}
 * helpers that throw {@link IllegalArgumentException} with messages that name
 * the offending value and the allowed range.</p>
 */
public final class JabCodeLimits {

    private JabCodeLimits() {
        // No instances.
    }

    /**
     * The valid JABCode colour modes (Nc = 0..7 → 2,4,8,16,32,64,128,256).
     * Immutable.
     */
    public static final Set<Integer> COLOR_NUMBERS =
        Set.of(2, 4, 8, 16, 32, 64, 128, 256);

    /** Minimum LDPC ECC level accepted by the codec. */
    public static final int ECC_MIN = 0;

    /**
     * Maximum LDPC ECC level accepted by the codec.
     *
     * <p>Pinned from {@code ecclevel2wcwr[11][2]}
     * ({@code src/jabcode/encoder.h:231}); the highest valid index is 10.</p>
     */
    public static final int ECC_MAX = 10;

    /**
     * Default ECC level ({@code DEFAULT_ECC_LEVEL},
     * {@code src/jabcode/include/jabcode.h:35}).
     */
    public static final int ECC_DEFAULT = 3;

    /** Minimum symbol number (the master symbol). */
    public static final int SYMBOL_MIN = 1;

    /** Maximum symbol number (master plus up to 60 docked slaves). */
    public static final int SYMBOL_MAX = 61;

    /** Minimum symbol version per axis. */
    public static final int VERSION_MIN = 1;

    /** Maximum symbol version per axis. */
    public static final int VERSION_MAX = 32;

    /**
     * Validates a colour number against {@link #COLOR_NUMBERS}.
     *
     * @param colorNumber the colour number to validate
     * @return the validated colour number
     * @throws IllegalArgumentException if not one of the allowed colour modes
     */
    public static int validateColorNumber(int colorNumber) {
        if (!COLOR_NUMBERS.contains(colorNumber)) {
            throw new IllegalArgumentException(
                "Color number must be one of 2,4,8,16,32,64,128,256, got: "
                    + colorNumber);
        }
        return colorNumber;
    }

    /**
     * Validates an ECC level against {@code [ECC_MIN, ECC_MAX]}.
     *
     * @param eccLevel the ECC level to validate
     * @return the validated ECC level
     * @throws IllegalArgumentException if outside the allowed range
     */
    public static int validateEccLevel(int eccLevel) {
        if (eccLevel < ECC_MIN || eccLevel > ECC_MAX) {
            throw new IllegalArgumentException(
                "ECC level must be between " + ECC_MIN + " and " + ECC_MAX
                    + ", got: " + eccLevel);
        }
        return eccLevel;
    }

    /**
     * Validates a symbol number against {@code [SYMBOL_MIN, SYMBOL_MAX]}.
     *
     * @param symbolNumber the symbol number to validate
     * @return the validated symbol number
     * @throws IllegalArgumentException if outside the allowed range
     */
    public static int validateSymbolNumber(int symbolNumber) {
        if (symbolNumber < SYMBOL_MIN || symbolNumber > SYMBOL_MAX) {
            throw new IllegalArgumentException(
                "Symbol number must be between " + SYMBOL_MIN + " and "
                    + SYMBOL_MAX + ", got: " + symbolNumber);
        }
        return symbolNumber;
    }

    /**
     * Validates a symbol version (one axis) against
     * {@code [VERSION_MIN, VERSION_MAX]}.
     *
     * @param axis  a label for the axis (e.g. {@code "X"} or {@code "Y"}) used
     *              in the error message
     * @param value the version value to validate
     * @return the validated version value
     * @throws IllegalArgumentException if outside the allowed range
     */
    public static int validateVersion(String axis, int value) {
        if (value < VERSION_MIN || value > VERSION_MAX) {
            throw new IllegalArgumentException(
                axis + " version must be between " + VERSION_MIN + " and "
                    + VERSION_MAX + ", got: " + value);
        }
        return value;
    }
}
