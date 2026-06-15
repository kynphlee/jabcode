/*
 * Regression guard for pn_index() (src/jabcode/pseudo_random.h).
 *
 * pn_index() reduces a 32-bit PRNG draw to a Fisher-Yates index in [0, range).
 * It replaced an inline float-scaling expression that returned `range` (one
 * past the valid [0, range-1]) whenever the single-precision ratio rounded to
 * 1.0f -- a latent heap out-of-bounds in interleave.c / ldpc.c.
 *
 * This test pins two invariants permanently:
 *   1. pn_index() NEVER returns an index outside [0, range-1];
 *   2. pn_index() is bit-identical to the legacy mapping for every draw the
 *      legacy code already mapped in range -- so the Fraunhofer / ISO 23634
 *      permutation (and wire-level interoperability) is preserved.
 * It also asserts the legacy mapping really did overflow (the bug was real).
 *
 * Build & run:  make -C src/jabcode test-pn
 */
#include <stdio.h>
#include "pseudo_random.h"   /* the function under test (real, shipped) */

/* the historical pre-fix reduction, verbatim and WITHOUT the clamp */
static int32_t legacy(uint32_t r, int32_t range)
{
    return (int32_t)( (float)r / (float)UINT32_MAX * (float)range );
}

int main(void)
{
    int32_t ranges[] = {2, 5, 17, 256, 1000, 4096, 65536, 1000000};
    int nr = (int)(sizeof(ranges) / sizeof(ranges[0]));
    long long checked = 0, oob_legacy = 0, oob_new = 0, mismatch = 0;

    for (int ri = 0; ri < nr; ri++)
    {
        int32_t range = ranges[ri];
        /* (a) dense boundary region: top 4096 draws, where the single-precision
         *     ratio rounds up to 1.0f */
        for (uint64_t r = (uint64_t)UINT32_MAX - 4096; r <= (uint64_t)UINT32_MAX; r++)
        {
            uint32_t rr = (uint32_t)r;
            int32_t L = legacy(rr, range), N = pn_index(rr, range);
            checked++;
            if (L >= range) oob_legacy++;
            if (N < 0 || N >= range) oob_new++;
            if (L < range && N != L) mismatch++;
        }
        /* (b) broad sweep across the whole 32-bit space (prime stride) */
        for (uint64_t r = 0; r <= (uint64_t)UINT32_MAX; r += 65521)
        {
            uint32_t rr = (uint32_t)r;
            int32_t L = legacy(rr, range), N = pn_index(rr, range);
            checked++;
            if (L >= range) oob_legacy++;
            if (N < 0 || N >= range) oob_new++;
            if (L < range && N != L) mismatch++;
        }
    }

    printf("pn_index regression: draws=%lld legacy_oob=%lld new_oob=%lld in_range_mismatch=%lld\n",
           checked, oob_legacy, oob_new, mismatch);
    int ok = (oob_new == 0 && mismatch == 0 && oob_legacy > 0);
    printf("RESULT: %s\n", ok ? "PASS" : "FAIL");
    return ok ? 0 : 1;
}
