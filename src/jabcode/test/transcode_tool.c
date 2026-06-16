/*
 * transcode_tool.c -- encode/decode helper for the transcode-survival benchmark.
 * Used by benchmarks/transcode_survival.py: a JABCode is encoded to PNG (enc),
 * a Python/PIL harness applies real digital transforms (JPEG, downscale, chroma),
 * and the result is decoded (dec) to measure whether the payload survived.
 *
 *   transcode_tool enc <colours> <ecc> <out.png>   -> writes a JABCode PNG
 *   transcode_tool dec <in.png>                     -> prints SURVIVE | FAIL
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "jabcode.h"

static const char *PAYLOAD =
    "JABCode transcode-survival probe -- 0123456789 ABCDEFGHIJ abcdefghij +/:.%";

int main(int argc, char **argv) {
    if (argc < 2) return 2;

    if (!strcmp(argv[1], "enc") && argc >= 5) {
        int colours = atoi(argv[2]), ecc = atoi(argv[3]);
        const char *out = argv[4];
        jab_encode *e = createEncode(colours, 1);
        if (e && e->symbol_ecc_levels) e->symbol_ecc_levels[0] = (jab_byte)ecc;
        int n = (int)strlen(PAYLOAD);
        jab_data *d = (jab_data *)malloc(sizeof(jab_data) + n);
        d->length = n; memcpy(d->data, PAYLOAD, n);
        generateJABCode(e, d);
        int rc = 2;
        if (e->bitmap) { saveImage(e->bitmap, (jab_char *)out); rc = 0; }
        free(d); destroyEncode(e);
        return rc;
    }

    if (!strcmp(argv[1], "dec") && argc >= 3) {
        jab_bitmap *bmp = readImage((jab_char *)argv[2]);
        if (!bmp) { printf("FAIL\n"); return 1; }
        jab_int32 st = 0;
        jab_data *r = decodeJABCode(bmp, NORMAL_DECODE, &st);
        int ok = (r != NULL);
        printf(ok ? "SURVIVE\n" : "FAIL\n");
        if (r) free(r);
        free(bmp);
        return ok ? 0 : 1;
    }
    return 2;
}
