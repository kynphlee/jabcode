/*
 * Bit-level regression guard for ECI decoding (ISO/IEC 23634:2022 5.3.9 / 7.3).
 *
 * The encoder does not emit ECI, so we hand-craft the bit streams decodeData()
 * consumes -- an Upper-mode MS latch (11111 10 = latch to ECI, Table 14) plus a
 * Table 19 assignment number -- and assert:
 *   (a) the transmitted output is the 7.3 escape "\nnnnnn" (backslash + 6-digit
 *       zero-padded ECI number), and
 *   (b) the Annex H symbology-identifier modifier is "]j1" (Table H.1 row 1).
 *
 * Covers all three Table 19 width classes (8 / 16 / 22-bit). Bits are written
 * MSB-first to match readData() (decoder.c).
 *
 * Build & run:  make -C src/jabcode test-eci
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "jabcode.h"

extern jab_data* decodeData(jab_data* bits);   /* internal entry under test */

/* append the low `nbits` of `value`, MSB-first (matches readData) */
static void push(jab_data* d, jab_int32* pos, jab_int32 value, jab_int32 nbits)
{
    for (jab_int32 i = nbits - 1; i >= 0; i--)
        d->data[(*pos)++] = (jab_char)((value >> i) & 1);
}

/* Upper MS (31) + "10" (latch to ECI) + a Table 19 number; the stream then ends,
 * so the next Upper read short-reads and the decode completes. */
static jab_data* eci_stream(jab_int32 width_bits, jab_int32 eci_value)
{
    jab_data* d = (jab_data*)malloc(sizeof(jab_data) + 64);
    jab_int32 pos = 0;
    push(d, &pos, 31, 5);                 /* Upper extension: MS              */
    push(d, &pos, 2,  2);                 /* 10 -> latch to ECI (Table 14)    */
    if (width_bits == 8)       { push(d, &pos, 0, 1);  push(d, &pos, eci_value, 7);  }
    else if (width_bits == 16) { push(d, &pos, 2, 2);  push(d, &pos, eci_value, 14); }
    else                       { push(d, &pos, 3, 2);  push(d, &pos, eci_value, 20); }
    d->length = pos;
    return d;
}

static int check(const char* name, jab_int32 width_bits, jab_int32 eci_value, const char* want)
{
    jab_data* bits = eci_stream(width_bits, eci_value);
    jab_data* out  = decodeData(bits);
    int ok = 0;
    if (out)
    {
        const char* id = jabGetSymbologyIdentifier();
        ok = (out->length == (jab_int32)strlen(want)
              && memcmp(out->data, want, out->length) == 0
              && strcmp(id, "]j1") == 0);
        char buf[40]; jab_int32 L = out->length < 39 ? out->length : 39;
        memcpy(buf, out->data, L); buf[L] = '\0';
        printf("  %-7s -> \"%s\" id=%s  (want \"%s\" ]j1)  %s\n",
               name, buf, id, want, ok ? "ok" : "FAIL");
        free(out);
    }
    else
        printf("  %-7s -> NULL decode  FAIL\n", name);
    free(bits);
    return ok;
}

int main(void)
{
    int fails = 0;
    fails += !check("ECI-8",  8,  26,     "\\000026");   /* 0bbbbbbb              */
    fails += !check("ECI-16", 16, 1000,   "\\001000");   /* 10b..b (14 bits)     */
    fails += !check("ECI-22", 22, 123456, "\\123456");   /* 11b..b (20 bits)     */
    printf("ECI decode: %d/3 cases correct\n", 3 - fails);
    printf("RESULT: %s\n", fails ? "FAIL" : "PASS");
    return fails ? 1 : 0;
}
