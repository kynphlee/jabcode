/*
 * Text-mode regression guard for the FNC1/Table-15 work. The Lower-mode latch
 * fix and the Table 15 dispatch sit inside the working text decoder, so this
 * encodes -> decodes multi-mode strings and asserts byte-identical roundtrips,
 * exercising Upper / Lower / Numeric / Punct and the mode switches between them
 * (the emitDataByte routing is the identity when no ECI is active).
 *
 * Build & run:  make -C src/jabcode test-roundtrip
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "jabcode.h"

static int roundtrip(const char* msg)
{
    jab_int32 len = (jab_int32)strlen(msg);
    jab_data* d = (jab_data*)malloc(sizeof(jab_data) + len);
    d->length = len;
    memcpy(d->data, msg, len);

    jab_encode* e = createEncode(8, 1);
    generateJABCode(e, d);

    int ok = 0;
    if (e->bitmap)
    {
        jab_int32 st = 0;
        jab_data* r = decodeJABCode(e->bitmap, NORMAL_DECODE, &st);
        if (r)
        {
            ok = (r->length == len && memcmp(r->data, msg, (size_t)len) == 0);
            free(r);
        }
    }
    printf("  %-28s %s\n", msg, ok ? "ok" : "FAIL");
    destroyEncode(e);
    free(d);
    return ok;
}

int main(void)
{
    int f = 0;
    f += !roundtrip("HELLO WORLD");        /* Upper + space                       */
    f += !roundtrip("hello world");        /* Lower (the latch-fixed mode)         */
    f += !roundtrip("0123456789");         /* Numeric                              */
    f += !roundtrip("Hello World 123");    /* Upper + Lower + Numeric + switches    */
    f += !roundtrip("Hi, there! 42.");     /* + Punct                              */
    printf("text roundtrip: %d/5 byte-identical\n", 5 - f);
    printf("RESULT: %s\n", f ? "FAIL" : "PASS");
    return f ? 1 : 0;
}
