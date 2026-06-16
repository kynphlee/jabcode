/*
 * Bit-level regression guard for ISO/IEC 23634:2022 Table 15 (additional
 * switches in uppercase mode) + the FNC1/GS1 semantics (5.3.10 / 7.2) + the
 * 7.3 backslash-doubling.
 *
 * The encoder emits none of these, so the streams are hand-crafted: Upper MS
 * (31) + "11" reaches Table 15, then a 3-bit selector. Bits are MSB-first to
 * match readData() (decoder.c).
 *
 * Build & run:  make -C src/jabcode test-table15
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "jabcode.h"

extern jab_data* decodeData(jab_data* bits);   /* internal entry under test */

typedef struct { jab_int32 v, n; } bit;        /* value, nbits (MSB-first) */

static jab_data* build(const bit* seq, int nseq)
{
    jab_data* d = (jab_data*)malloc(sizeof(jab_data) + 256);
    jab_int32 pos = 0;
    for (int k = 0; k < nseq; k++)
        for (jab_int32 i = seq[k].n - 1; i >= 0; i--)
            d->data[pos++] = (jab_char)((seq[k].v >> i) & 1);
    d->length = pos;
    return d;
}

static int check(const char* name, const bit* seq, int nseq,
                 const char* want, int want_len, const char* want_id)
{
    jab_data* bits = build(seq, nseq);
    jab_data* out  = decodeData(bits);
    int ok = 0;
    if (out)
    {
        const char* id = jabGetSymbologyIdentifier();
        ok = (out->length == want_len
              && memcmp(out->data, want, (size_t)want_len) == 0
              && strcmp(id, want_id) == 0);
        printf("  %-14s len=%d id=%s  (want len=%d %s)  %s\n",
               name, out->length, id, want_len, want_id, ok ? "ok" : "FAIL");
        free(out);
    }
    else
        printf("  %-14s NULL decode  FAIL\n", name);
    free(bits);
    return ok;
}

int main(void)
{
    int f = 0;
    /* Upper MS=31, "11"=value 3 -> Table 15; then a 3-bit selector. 'A'=Upper value 1. */
    const bit fnc1_pre[] = {{31,5},{3,2},{4,3},{1,5}};                 /* FNC1(preceding) + 'A' */
    f += !check("FNC1-preceding", fnc1_pre, 4, "A", 1, "]j2");

    const bit fnc1_fol[] = {{1,5},{31,5},{3,2},{4,3}};                 /* 'A' + FNC1(following) */
    f += !check("FNC1-following", fnc1_fol, 4, "A", 1, "]j3");

    const bit fnc1_sep[] = {{31,5},{3,2},{4,3},{31,5},{3,2},{4,3}};    /* start FNC1 + internal FNC1 */
    f += !check("FNC1-separator", fnc1_sep, 6, "\x1D", 1, "]j2");

    const bit eot[]      = {{31,5},{3,2},{5,3}};                       /* EoT -> 0x04 */
    f += !check("EoT", eot, 3, "\x04", 1, "]j0");

    const bit url[]      = {{31,5},{3,2},{1,3}};                       /* https:// */
    f += !check("https://", url, 3, "https://", 8, "]j0");

    const bit iso[]      = {{31,5},{3,2},{0,3}};                       /* ISO 15434 -> [)> + RS */
    f += !check("ISO15434", iso, 3, "[)>\x1E", 4, "]j0");

    /* ECI(1) sets the active flag, then a Byte 0x5C is doubled per 7.3. */
    const bit bsl[]      = {{31,5},{2,2},{0,1},{1,7}, {31,5},{0,2}, {1,4},{0x5C,8}};
    f += !check("eci-backslash", bsl, 8, "\\000001\\\\", 9, "]j1");

    printf("Table15/FNC1: %d/7 cases correct\n", 7 - f);
    printf("RESULT: %s\n", f ? "FAIL" : "PASS");
    return f ? 1 : 0;
}
