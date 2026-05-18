/*
 * Nc=0 (Mode 0 Monochrome) Roundtrip Test
 *
 * Initially asserts the PRE-FIX state: encoder rejects color_number=2
 * and silently upgrades to DEFAULT_COLOR_NUMBER=8.
 *
 * After Step 0.3 (validation extension) and downstream fixes, this test
 * will be extended to assert FULL roundtrip success at Nc=0.
 *
 * See: docs/jabcode-all-nc-plan/00b-mode-0-monochrome.md Step 0.2/0.6/0.8
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "jabcode.h"

/* Exit codes:
 *   0 = Nc=0 fully supported (FINAL STATE after WS-0 complete)
 *   1 = Nc=0 acceptance-only (validation passes; downstream may still fail)
 *   2 = Nc=0 rejected via silent upgrade (PRE-FIX STATE)
 *   3 = test infrastructure failure (createEncode returned NULL, etc.)
 */

int main(void) {
    printf("========================================\n");
    printf("Nc=0 (Mode 0 Monochrome) Roundtrip Test\n");
    printf("========================================\n\n");

    /* === Stage 1: encoder validation === */
    printf("--- Stage 1: createEncode(color_number=2) ---\n");
    jab_encode* enc = createEncode(2, 1);
    if (!enc) {
        printf("UNEXPECTED: createEncode(2,1) returned NULL\n");
        return 3;
    }
    printf("  enc->color_number = %d\n", enc->color_number);

    if (enc->color_number == 2) {
        printf("  STATE: ACCEPTED (validation allows Nc=0)\n");
    } else if (enc->color_number == 8) {
        printf("  STATE: REJECTED — silently upgraded to DEFAULT_COLOR_NUMBER=8\n");
        printf("\n========================================\n");
        printf("PRE-FIX STATE captured (Nc=0 not yet supported)\n");
        printf("========================================\n");
        destroyEncode(enc);
        return 2;
    } else {
        printf("  STATE: UNEXPECTED — silently upgraded to %d\n", enc->color_number);
        destroyEncode(enc);
        return 3;
    }

    /* === Stage 2: default palette === */
    printf("\n--- Stage 2: default palette [K, W] for Nc=0 ---\n");
    int palette_ok =
        enc->palette[0] == 0   && enc->palette[1] == 0   && enc->palette[2] == 0 &&
        enc->palette[3] == 255 && enc->palette[4] == 255 && enc->palette[5] == 255;
    if (!palette_ok) {
        printf("  FAIL: palette is [(%d,%d,%d) (%d,%d,%d)] — expected [(0,0,0) (255,255,255)]\n",
               enc->palette[0], enc->palette[1], enc->palette[2],
               enc->palette[3], enc->palette[4], enc->palette[5]);
        destroyEncode(enc);
        return 1;
    }
    printf("  PASS: palette is [K=(0,0,0), W=(255,255,255)]\n");

    /* === Stage 3: encode === */
    printf("\n--- Stage 3: generateJABCode(\"HELLO\") at Nc=0 ---\n");
    const char* payload = "HELLO";
    int payload_len = 5;
    jab_data* in = (jab_data*)malloc(sizeof(jab_data) + payload_len);
    in->length = payload_len;
    memcpy(in->data, payload, payload_len);

    jab_int32 gen_rc = generateJABCode(enc, in);
    printf("  generateJABCode return code: %d (0=success, Unix-style)\n", gen_rc);
    if (gen_rc != 0 || !enc->bitmap) {
        printf("  FAIL: generateJABCode failed at Nc=0\n");
        free(in);
        destroyEncode(enc);
        return 1;
    }
    printf("  PASS: encoder produced %dx%d bitmap\n",
           enc->bitmap->width, enc->bitmap->height);

    /* === Stage 4: decode === */
    printf("\n--- Stage 4: decodeJABCode ---\n");
    jab_int32 status = -1;
    jab_data* out = decodeJABCode(enc->bitmap, NORMAL_DECODE, &status);
    if (!out) {
        printf("  FAIL: decode returned NULL, status=%d\n", status);
        free(in);
        destroyEncode(enc);
        return 1;
    }

    /* === Stage 5: roundtrip integrity === */
    printf("\n--- Stage 5: roundtrip integrity ---\n");
    if (out->length != in->length || memcmp(out->data, in->data, in->length) != 0) {
        printf("  FAIL: roundtrip mismatch (got %d bytes, expected %d)\n",
               out->length, in->length);
        free(in);
        free(out);
        destroyEncode(enc);
        return 1;
    }
    printf("  PASS: decoded %d bytes match input\n", out->length);

    printf("\n========================================\n");
    printf("Nc=0 FULLY SUPPORTED — WS-0 implementation complete\n");
    printf("========================================\n");

    free(in);
    free(out);
    destroyEncode(enc);
    return 0;
}
