/*
 * Finder-pattern look-alike regression.
 *
 * Data modules can, by chance, repeat a finder pattern's colours closely enough that the
 * detector accepts them as one: the same 1:1:1:1:1 runs, in every direction it checks, at the
 * same strength as the real pattern. The detector used to pick each finder pattern type on its
 * own -- most scanlines wins, ties to whichever was scanned first -- so a look-alike above the
 * real bottom-right pattern displaced it. One false corner bends the sampling grid off the image
 * and a clean, undamaged symbol fails with "Sampling master symbol failed". Measured on random
 * 1927-byte payloads at 4 colours / ECC 3: 18 of 9,600 symbols (0.19%), each one deterministic.
 *
 * Two kinds of look-alike are planted into the data region of a freshly encoded symbol, so
 * each case is independent of which mask the encoder chose:
 *
 *  - a copy of a real finder pattern's 5x5 module neighbourhood: exact by construction, it is
 *    found exactly as often as the real one, and the scan order used to break that tie;
 *  - a 9x9 neighbourhood recorded from one of those 18 symbols, whose middle row repeats one
 *    row down: it is found on MORE scanlines than the real pattern, so ranking by how often a
 *    candidate was found picks it outright. Only geometry can tell it apart.
 *
 * The symbol must still decode byte-identically: the detector has to choose the four patterns
 * that form one symbol, not four that merely look right.
 *
 * Build & run:  make -C src/jabcode test-fp-lookalike
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "jabcode.h"

#define PAYLOAD_LENGTH 1927     /* the FCCS FULL mark's sealed envelope: a version-26 symbol */
#define COLOR_NUMBER   4
#define ECC_LEVEL      3
#define MODULE_SIZE    12

static jab_data* makePayload(void)
{
    jab_data* d = (jab_data*)malloc(sizeof(jab_data) + PAYLOAD_LENGTH);
    d->length = PAYLOAD_LENGTH;
    jab_uint32 x = 0x2545F491u;
    for(jab_int32 i = 0; i < PAYLOAD_LENGTH; i++)
    {
        x ^= x << 13; x ^= x >> 17; x ^= x << 5;  /* xorshift32: ciphertext-like bytes */
        d->data[i] = (jab_char)(x & 0xFF);
    }
    return d;
}

/* Copy the 5x5 module block centred on (sx,sy) to the block centred on (tx,ty). */
static void copyModuleBlock(jab_bitmap* bm, jab_int32 sx, jab_int32 sy, jab_int32 tx, jab_int32 ty)
{
    jab_int32 bpp = bm->bits_per_pixel / 8;
    for(jab_int32 my = -2; my <= 2; my++)
    {
        for(jab_int32 py = 0; py < MODULE_SIZE; py++)
        {
            jab_byte* src = bm->pixel + (((sy + my) * MODULE_SIZE + py) * bm->width + (sx - 2) * MODULE_SIZE) * bpp;
            jab_byte* dst = bm->pixel + (((ty + my) * MODULE_SIZE + py) * bm->width + (tx - 2) * MODULE_SIZE) * bpp;
            memmove(dst, src, (size_t)(5 * MODULE_SIZE * bpp));
        }
    }
}

/*
 * An FP1 look-alike recorded from a symbol that failed (random payload, 4 colours, ECC 3):
 * K Y K Y K across its middle row, repeated one row down, so it is found on 14 scanlines
 * against the real FP1's 12.
 */
static const char* const COUNT_BEATING_FP1[9] = {
    "YCMYMYCMY",
    "KMYYKMMMM",
    "CYKYYCYKK",
    "YCCYKKKKY",
    "KCKYKYKCY",
    "MMKYKYKKC",
    "YCYKKYKMK",
    "KKMMCKMCK",
    "YYKYYCCCY",
};

/* Paint the recorded 9x9 block centred on module (tx,ty). */
static void paintCountBeatingFP1(jab_bitmap* bm, jab_int32 tx, jab_int32 ty)
{
    jab_int32 bpp = bm->bits_per_pixel / 8;
    for(jab_int32 my = 0; my < 9; my++)
    {
        for(jab_int32 mx = 0; mx < 9; mx++)
        {
            jab_byte r = 0, g = 0, b = 0;
            switch(COUNT_BEATING_FP1[my][mx])
            {
            case 'M': r = 255; b = 255; break;
            case 'Y': r = 255; g = 255; break;
            case 'C': g = 255; b = 255; break;
            default: break;   /* 'K' */
            }
            for(jab_int32 py = 0; py < MODULE_SIZE; py++)
            {
                for(jab_int32 px = 0; px < MODULE_SIZE; px++)
                {
                    jab_int32 x = (tx - 4 + mx) * MODULE_SIZE + px;
                    jab_int32 y = (ty - 4 + my) * MODULE_SIZE + py;
                    jab_byte* p = bm->pixel + (y * bm->width + x) * bpp;
                    p[0] = r; p[1] = g; p[2] = b;
                }
            }
        }
    }
}

/*
 * Encode the payload, plant a look-alike centred on module (tx,ty) -- a copy of finder pattern
 * fp, or the recorded count-beating FP1 when fp is -1 -- and decode.
 */
static int decodesWithLookalike(const jab_data* payload, jab_int32 fp, jab_int32 tx, jab_int32 ty)
{
    jab_encode* enc = createEncode(COLOR_NUMBER, 1);
    enc->module_size = MODULE_SIZE;
    enc->symbol_ecc_levels[0] = ECC_LEVEL;
    if(generateJABCode(enc, (jab_data*)payload) != 0)
    {
        printf("  look-alike at (%d,%d): encode FAILED\n", tx, ty);
        destroyEncode(enc);
        return 0;
    }

    /* finder pattern centres sit 3 modules in from each edge: 0 1 / 3 2 */
    jab_int32 side = enc->bitmap->width / MODULE_SIZE;
    if(fp < 0)
    {
        paintCountBeatingFP1(enc->bitmap, tx, ty);
    }
    else
    {
        jab_int32 cx[4] = {3, side - 4, side - 4, 3};
        jab_int32 cy[4] = {3, 3, side - 4, side - 4};
        copyModuleBlock(enc->bitmap, cx[fp], cy[fp], tx, ty);
    }

    jab_int32 status = 0;
    jab_data* out = decodeJABCode(enc->bitmap, NORMAL_DECODE, &status);
    int ok = out && out->length == payload->length && memcmp(out->data, payload->data, (size_t)payload->length) == 0;
    if(fp < 0)
        printf("  count-beating FP1 look-alike at (%3d,%3d) of %d: %s\n", tx, ty, side, ok ? "ok" : "FAIL");
    else
        printf("  FP%d copy at (%3d,%3d) of %d: %s\n", fp, tx, ty, side, ok ? "ok" : "FAIL");
    free(out);
    destroyEncode(enc);
    return ok;
}

int main(void)
{
    jab_data* payload = makePayload();
    int failures = 0, cases = 0;

    /* Every look-alike sits above the bottom finder patterns, so FP2 and FP3 copies are
     * scanned BEFORE the real ones -- the order that used to decide the tie. The count-beating
     * FP1 is scanned AFTER the real FP1, and wins anyway. */
    const jab_int32 targets[][2] = {{61, 44}, {30, 60}, {90, 30}};
    for(jab_int32 fp = -1; fp < 4; fp++)
    {
        for(size_t t = 0; t < sizeof targets / sizeof targets[0]; t++)
        {
            cases++;
            failures += !decodesWithLookalike(payload, fp, targets[t][0], targets[t][1]);
        }
    }

    free(payload);
    printf("finder-pattern look-alike: %d/%d decoded byte-identically\n", cases - failures, cases);
    printf("RESULT: %s\n", failures ? "FAIL" : "PASS");
    return failures ? 1 : 0;
}
