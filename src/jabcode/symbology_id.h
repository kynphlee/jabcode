/**
 * libjabcode - Symbology identifier (ISO/IEC 23634:2022 Annex H, NORMATIVE)
 *
 * The JAB Code symbology identifier, reported per ISO/IEC 15424, is:
 *
 *     ]jm
 *
 *   ]  symbology identifier flag (ASCII 93)
 *   j  code character for the JAB Code symbology
 *   m  modifier digit from Annex H Table H.1:
 *        0  No options
 *        1  ECI protocol implemented
 *        2  FNC1 preceding 1st message character
 *        3  FNC1 following an initial letter or pair of digits
 *        4  FNC1 preceding 1st char, ECI implemented
 *        5  FNC1 following an initial letter/digit-pair, ECI implemented
 *
 * The formatter is a pure, header-only function (plain int/char, no libjabcode
 * link needed) so it is callable from the decoder and directly unit-testable --
 * see test/test_symbology_id.c (make test-symid).
 */
#ifndef JABCODE_SYMBOLOGY_ID_H
#define JABCODE_SYMBOLOGY_ID_H

/* FNC1 position, mirroring the Annex H Table H.1 rows. */
enum {
    JAB_FNC1_NONE      = 0,  /* no FNC1                                   */
    JAB_FNC1_PRECEDING = 1,  /* FNC1 preceding the 1st message character  */
    JAB_FNC1_FOLLOWING = 2   /* FNC1 after an initial letter/digit-pair   */
};

/**
 * @brief Map (ECI used, FNC1 position) to the Annex H Table H.1 modifier digit.
 * @return the modifier digit 0..5 (defaults to 0 for out-of-range fnc1_mode)
 */
static inline int jab_symbology_modifier(int eci_used, int fnc1_mode)
{
    /* rows: [none, preceding, following]; cols: [no ECI, ECI] -- Table H.1 */
    static const int m[3][2] = { {0, 1}, {2, 4}, {3, 5} };
    if (fnc1_mode < 0 || fnc1_mode > 2) fnc1_mode = 0;
    return m[fnc1_mode][eci_used ? 1 : 0];
}

/**
 * @brief Write the JAB Code symbology identifier "]j<m>" + NUL into out.
 * @param out buffer of at least 4 bytes
 */
static inline void jab_format_symbology_identifier(int eci_used, int fnc1_mode, char* out)
{
    out[0] = ']';
    out[1] = 'j';
    out[2] = (char)('0' + jab_symbology_modifier(eci_used, fnc1_mode));
    out[3] = '\0';
}

#endif /* JABCODE_SYMBOLOGY_ID_H */
