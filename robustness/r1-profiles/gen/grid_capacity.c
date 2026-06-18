/*
 * grid_capacity -- density quantifier for the R1 profiles.
 *
 * For each (Nc, ECC) cell in the SAME focused grid grid_encode uses, binary-
 * searches the maximum single-symbol payload (bytes) that fits at a FIXED
 * common side version. Holding geometry constant isolates the pure density
 * tradeoff: how many payload bytes a colour-count / ECC choice buys in one
 * symbol of a given physical size. (grid_encode's AUTO side_size answers the
 * dual question -- the area a fixed payload costs.)
 *
 * Bits/module = log2(colours) = Nc+1, so capacity rises ~linearly with Nc and
 * falls as ECC takes a larger parity share; this tool reports the realised
 * bytes after the encoder's actual (wc,wr) and metadata overhead, not the ideal.
 *
 * ISO-conformance: pure reference-encoder probing; nothing is rendered or
 * altered, only generateJABCode fit/no-fit is consulted.
 *
 * Output (stdout, one JSON object per line):
 *   {"nc":N,"colors":C,"ecc":E,"version":V,"side":S,"max_payload_bytes":B}
 *
 * Usage: grid_capacity [version=7]
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "jabcode.h"

#define VERSION2SIDE(v) ((v) * 4 + 17)

static const int NC_GRID[]  = {1, 2, 3, 5, 7};
static const int ECC_GRID[] = {3, 5, 8};

/* does `nbytes` of (arbitrary) payload fit one symbol at (colors, ecc, version)? */
static int fits(int colors, int ecc, int version, int nbytes) {
    jab_encode *e = createEncode(colors, 1);
    if (!e) return 0;
    if (e->symbol_ecc_levels) e->symbol_ecc_levels[0] = (jab_byte)ecc;
    if (e->symbol_versions) { e->symbol_versions[0].x = version; e->symbol_versions[0].y = version; }
    jab_data *d = (jab_data*)malloc(sizeof(jab_data) + nbytes);
    d->length = nbytes;
    /* deterministic filler; capacity is content-length-driven, not value-driven */
    for (int i = 0; i < nbytes; i++) d->data[i] = (jab_byte)((i * 131 + 17) & 0xFF);
    int rc = generateJABCode(e, d);
    int ok = (rc == 0 && e->bitmap) ? 1 : 0;
    free(d); destroyEncode(e);
    return ok;
}

int main(int argc, char **argv) {
    int version = (argc > 1) ? atoi(argv[1]) : 7;
    if (version < 1 || version > 32) version = 7;
    int side = VERSION2SIDE(version);

    for (size_t ni = 0; ni < sizeof(NC_GRID)/sizeof(NC_GRID[0]); ni++) {
        int nc = NC_GRID[ni];
        int colors = 1 << (nc + 1);
        for (size_t ei = 0; ei < sizeof(ECC_GRID)/sizeof(ECC_GRID[0]); ei++) {
            int ecc = ECC_GRID[ei];
            /* binary-search the largest fitting payload; capacity is monotone */
            int lo = 1, hi = side * side, best = 0;
            while (lo <= hi) {
                int mid = (lo + hi) / 2;
                if (fits(colors, ecc, version, mid)) { best = mid; lo = mid + 1; }
                else hi = mid - 1;
            }
            printf("{\"nc\":%d,\"colors\":%d,\"ecc\":%d,\"version\":%d,\"side\":%d,"
                   "\"max_payload_bytes\":%d}\n", nc, colors, ecc, version, side, best);
        }
    }
    return 0;
}
