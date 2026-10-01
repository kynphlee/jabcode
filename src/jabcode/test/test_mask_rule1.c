/*
 * Mask penalty rule 1 must recognise every finder pattern the encoder draws.
 *
 * Rule 1 is how the encoder keeps data modules from posing as finder patterns: of the eight
 * masks it prefers the one with the fewest finder-pattern crosses in the data, at 100 points
 * each. Its colour templates were not updated when the finder patterns took their current
 * colours, so it looked for crosses that are never drawn: at 4 colours it missed FP1
 * (black/yellow) and FP2 (yellow/black), and from 8 colours up it missed 23 of the 24 (the
 * 32-colour FP0 matched by coincidence). Only 2 colours escaped, because all four of its
 * finder patterns share FP0's black/white cross.
 * A data cross that matches a real finder pattern can displace it in a decoder -- ours did,
 * until test_fp_lookalike.c, and readers built before that fix still can -- so the encoder has
 * to steer away from them.
 *
 * For each colour mode, each finder pattern's cross is read from a freshly encoded symbol, so
 * the expectation is what the encoder actually drew, not a copy of rule 1's own tables. Set
 * alone into an empty matrix, it must score as exactly one finder pattern.
 *
 * Build & run:  make -C src/jabcode test-mask-rule1
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "jabcode.h"

extern jab_int32 applyRule1(jab_int32* matrix, jab_int32 width, jab_int32 height, jab_int32 color_number);

#define W1            100   /* rule 1's penalty for one finder-pattern cross (mask.c) */
#define BLANK_SIDE    21
#define BLANK_CENTRE  10

static int checkColorMode(jab_int32 color_number)
{
    const char* message = "finder pattern look-alike";
    jab_int32 length = (jab_int32)strlen(message);
    jab_data* d = (jab_data*)malloc(sizeof(jab_data) + length);
    d->length = length;
    memcpy(d->data, message, (size_t)length);

    jab_encode* enc = createEncode(color_number, 1);
    int failures = 0;
    if(generateJABCode(enc, d) != 0)
    {
        printf("  %3d colours: encode FAILED\n", color_number);
        failures = 4;
    }
    else
    {
        jab_symbol* symbol = &enc->symbols[0];
        jab_int32 w = symbol->side_size.x;
        jab_int32 h = symbol->side_size.y;
        /* finder pattern centres sit 3 modules in from each edge: 0 1 / 3 2 */
        jab_int32 cx[4] = {3, w - 4, w - 4, 3};
        jab_int32 cy[4] = {3, 3, h - 4, h - 4};
        for(jab_int32 fp = 0; fp < 4; fp++)
        {
            jab_int32 blank[BLANK_SIDE * BLANK_SIDE];
            for(jab_int32 i = 0; i < BLANK_SIDE * BLANK_SIDE; i++)
                blank[i] = -1;   /* -1: not a data module, as maskCode() leaves non-data modules */
            for(jab_int32 k = -2; k <= 2; k++)
            {
                blank[BLANK_CENTRE * BLANK_SIDE + BLANK_CENTRE + k] = symbol->matrix[cy[fp] * w + cx[fp] + k];
                blank[(BLANK_CENTRE + k) * BLANK_SIDE + BLANK_CENTRE] = symbol->matrix[(cy[fp] + k) * w + cx[fp]];
            }
            jab_byte* row = symbol->matrix + cy[fp] * w + cx[fp];
            jab_int32 score = applyRule1(blank, BLANK_SIDE, BLANK_SIDE, color_number);
            int ok = score == W1;
            failures += !ok;
            printf("  %3d colours FP%d (drawn %d %d %d %d %d): rule 1 scores %3d -> %s\n",
                   color_number, fp, row[-2], row[-1], row[0], row[1], row[2], score, ok ? "ok" : "MISSED");
        }
    }
    destroyEncode(enc);
    free(d);
    return failures;
}

int main(void)
{
    const jab_int32 color_numbers[] = {2, 4, 8, 16, 32, 64, 128, 256};
    int failures = 0;
    for(size_t i = 0; i < sizeof color_numbers / sizeof color_numbers[0]; i++)
        failures += checkColorMode(color_numbers[i]);
    printf("mask rule 1: %d finder pattern(s) not recognised\n", failures);
    printf("RESULT: %s\n", failures ? "FAIL" : "PASS");
    return failures ? 1 : 0;
}
