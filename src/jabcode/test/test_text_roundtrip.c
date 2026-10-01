/*
 * Text-mode regression guard for the FNC1/Table-15 work. The Lower-mode latch
 * fix and the Table 15 dispatch sit inside the working text decoder, so this
 * encodes -> decodes multi-mode strings and asserts byte-identical roundtrips,
 * exercising Upper / Lower / Numeric / Punct and the mode switches between them
 * (the emitDataByte routing is the identity when no ECI is active).
 *
 * The Mixed-mode pairs ", " ". " ": " are single characters (ISO/IEC 23634
 * Table 13). CR followed by SPACE is not one, but the encoder used to plan it as
 * one and then wrote CR alone: the SPACE vanished and the decode still reported
 * success.
 *
 * Build & run:  make -C src/jabcode test-roundtrip
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "jabcode.h"

static int roundtripLabelled(const char* label, const char* msg)
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
    printf("  %-28s %s\n", label, ok ? "ok" : "FAIL");
    destroyEncode(e);
    free(d);
    return ok;
}

static int roundtrip(const char* msg)
{
    return roundtripLabelled(msg, msg);
}

int main(void)
{
    int f = 0, n = 0;
    n++; f += !roundtrip("HELLO WORLD");        /* Upper + space                       */
    n++; f += !roundtrip("hello world");        /* Lower (the latch-fixed mode)         */
    n++; f += !roundtrip("0123456789");         /* Numeric                              */
    n++; f += !roundtrip("Hello World 123");    /* Upper + Lower + Numeric + switches    */
    n++; f += !roundtrip("Hi, there! 42.");     /* + Punct                              */
    n++; f += !roundtrip("Note: done. Next");   /* Mixed ": " and ". " pairs            */
    n++; f += !roundtripLabelled("Line one\\r next", "Line one\r next");  /* CR SPACE: not a pair */
    n++; f += !roundtripLabelled("A\\r B", "A\r B");
    n++; f += !roundtripLabelled("x\\r\\ny", "x\r\ny");                     /* CR LF */
    printf("text roundtrip: %d/%d byte-identical\n", n - f, n);
    printf("RESULT: %s\n", f ? "FAIL" : "PASS");
    return f ? 1 : 0;
}
