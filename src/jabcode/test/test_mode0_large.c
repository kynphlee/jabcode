/*
 * Mode 0 (2-colour) large-symbol regression.
 *
 * A 2-colour symbol has only black and white, so its data modules repeat the finder pattern's
 * black/white runs far more often than a colour symbol's do, and Mode 0 cannot tell the four
 * pattern types apart by colour: the detector types a candidate by the image quadrant it lies
 * in. In a large symbol every quadrant then holds dozens of look-alikes, many found on more
 * scanlines than the real pattern (one per pixel row of its core), and the selection only
 * weighed the most-found few. The real patterns never made that shortlist, and from about
 * version 10 up an undamaged 2-colour symbol failed with "Sampling master symbol failed".
 * Measured before the fix at ECC 5: 19 of 130 message sizes (10..1300 bytes) round-tripped.
 * panama-wrapper's DecodingBenchmark hit it at colorMode=2, messageSize=1000 (version 24).
 *
 * A look-alike can also sit a module from a real pattern and be merged into it, pulling its
 * centre half a module off; then the metadata reads wrong and the decode fails. Whether it
 * merges depends on the module size measured for each, so these cases span ECC levels.
 *
 * Every case must decode byte-identically. The table holds:
 *  - the benchmark's own payload (BenchmarkBase.generateMessage(1000)), version 24;
 *  - one payload per ~30 bytes up to version 32, text and ciphertext-like, so every
 *    version from 1 to 32 is covered;
 *  - payloads whose real pattern had a look-alike merged into it (MERGE_CASES);
 *  - symbols resampled to 0.85 scale, so modules are 10 or 11 px as a camera sees them, not
 *    a whole 12 (RESAMPLED_CASES): a candidate's centre must be the one its cross-check
 *    measured, not the row the scan happened to be on.
 *
 * Build & run:  make -C src/jabcode test-mode0-large
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "jabcode.h"

#define COLOR_NUMBER   2
#define ECC_LEVEL      5    /* the benchmark's */
#define MODULE_SIZE    12
#define RESAMPLE_SCALE 0.85

enum payload_kind { TEXT, CIPHER };

typedef struct {
    enum payload_kind kind;
    jab_uint32 seed;        /* CIPHER only: xorshift32 starts at seed + length */
    jab_int32 length;
    jab_int32 ecc;
} mode0_case;

/* Payloads whose real finder pattern had a look-alike merged into it before the fix. */
static const mode0_case MERGE_CASES[] = {
    {TEXT, 0, 810, 5}, {TEXT, 0, 900, 5}, {TEXT, 0, 450, 7},
    {CIPHER, 11u, 70, 5}, {CIPHER, 11u, 85, 5}, {CIPHER, 11u, 136, 5}, {CIPHER, 11u, 166, 5}, {CIPHER, 11u, 211, 5},
    {CIPHER, 0x2545F491u, 730, 1}, {CIPHER, 0x2545F491u, 1510, 1}, {CIPHER, 0x2545F491u, 690, 3},
    {CIPHER, 0x2545F491u, 240, 10},
};

/* Resampled before decoding; each also decodes unscaled. */
static const mode0_case RESAMPLED_CASES[] = {
    {TEXT, 0, 310, 5}, {TEXT, 0, 400, 5}, {TEXT, 0, 550, 5},
    {CIPHER, 0x2545F491u, 100, 5}, {CIPHER, 0x2545F491u, 250, 5},
};

static jab_data* makePayload(const mode0_case* c)
{
    static const char text[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    jab_data* d = (jab_data*)malloc(sizeof(jab_data) + c->length);
    d->length = c->length;
    jab_uint32 x = c->seed + (jab_uint32)c->length;
    for(jab_int32 i = 0; i < c->length; i++)
    {
        if(c->kind == TEXT)
        {
            d->data[i] = text[i % (sizeof text - 1)];   /* BenchmarkBase.generateMessage */
        }
        else
        {
            x ^= x << 13; x ^= x >> 17; x ^= x << 5;    /* xorshift32: ciphertext-like bytes */
            d->data[i] = (jab_char)(x & 0xFF);
        }
    }
    return d;
}

/* Nearest-neighbour resampling: modules come out a mix of two whole pixel sizes. */
static jab_bitmap* resample(const jab_bitmap* src, double scale)
{
    jab_int32 w = (jab_int32)(src->width * scale), h = (jab_int32)(src->height * scale);
    jab_int32 bpp = src->bits_per_pixel / 8;
    jab_bitmap* dst = (jab_bitmap*)calloc(1, sizeof(jab_bitmap) + (size_t)w * h * bpp);
    dst->width = w;
    dst->height = h;
    dst->bits_per_pixel = src->bits_per_pixel;
    dst->bits_per_channel = src->bits_per_channel;
    dst->channel_count = src->channel_count;
    for(jab_int32 y = 0; y < h; y++)
    {
        for(jab_int32 x = 0; x < w; x++)
        {
            jab_int32 sx = (jab_int32)(x / scale), sy = (jab_int32)(y / scale);
            memcpy(dst->pixel + ((size_t)y * w + x) * bpp, src->pixel + ((size_t)sy * src->width + sx) * bpp, (size_t)bpp);
        }
    }
    return dst;
}

/* Encode at 2 colours and decode the encoder's own bitmap, resampled when scale != 1.
 * 1 = byte-identical, 0 = not, -1 = the payload does not fit one symbol (not a decoder case). */
static int roundTrips(const mode0_case* c, double scale)
{
    jab_data* payload = makePayload(c);
    jab_encode* enc = createEncode(COLOR_NUMBER, 1);
    if(enc->color_number != COLOR_NUMBER)
    {
        /* A silent upgrade to 8 colours is how this bug once hid: the symbol decoded, but it
         * was never a 2-colour symbol. */
        printf("  %s %5d bytes: encoder made %d colours, not %d -- FAIL\n",
               c->kind == TEXT ? "text  " : "cipher", c->length, enc->color_number, COLOR_NUMBER);
        destroyEncode(enc);
        free(payload);
        return 0;
    }
    enc->module_size = MODULE_SIZE;
    enc->symbol_ecc_levels[0] = (jab_byte)c->ecc;
    if(generateJABCode(enc, payload) != 0)
    {
        destroyEncode(enc);
        free(payload);
        return -1;
    }

    jab_bitmap* bitmap = scale == 1.0 ? enc->bitmap : resample(enc->bitmap, scale);
    jab_int32 status = 0;
    jab_data* out = decodeJABCode(bitmap, NORMAL_DECODE, &status);
    int ok = out && out->length == payload->length && memcmp(out->data, payload->data, (size_t)payload->length) == 0;
    jab_int32 side = enc->bitmap->width / MODULE_SIZE;
    if(!ok)
        printf("  %s %5d bytes, ECC %2d (version %2d), scale %.2f: FAIL\n",
               c->kind == TEXT ? "text  " : "cipher", c->length, c->ecc, (side - 17) / 4, scale);
    if(bitmap != enc->bitmap)
        free(bitmap);
    free(out);
    destroyEncode(enc);
    free(payload);
    return ok;
}

static void run(const mode0_case* c, double scale, int* cases, int* failures)
{
    int r = roundTrips(c, scale);
    if(r < 0)
        return;
    (*cases)++;
    *failures += !r;
}

int main(void)
{
    int failures = 0, cases = 0;

    /* The DecodingBenchmark case: colorMode=2, messageSize=1000, ECC 5, module 12. */
    const mode0_case benchmark = {TEXT, 0, 1000, ECC_LEVEL};
    run(&benchmark, 1.0, &cases, &failures);

    /* Every version from 1 to 32. Ciphertext needs more room, so it runs out sooner. */
    for(jab_int32 length = 10; length <= 1720; length += 30)
    {
        const mode0_case text = {TEXT, 0, length, ECC_LEVEL};
        const mode0_case cipher = {CIPHER, 0x2545F491u, length, ECC_LEVEL};
        run(&text, 1.0, &cases, &failures);
        run(&cipher, 1.0, &cases, &failures);
    }

    for(size_t i = 0; i < sizeof MERGE_CASES / sizeof MERGE_CASES[0]; i++)
        run(&MERGE_CASES[i], 1.0, &cases, &failures);

    for(size_t i = 0; i < sizeof RESAMPLED_CASES / sizeof RESAMPLED_CASES[0]; i++)
    {
        run(&RESAMPLED_CASES[i], 1.0, &cases, &failures);
        run(&RESAMPLED_CASES[i], RESAMPLE_SCALE, &cases, &failures);
    }

    printf("Mode 0 large symbols: %d/%d decoded byte-identically\n", cases - failures, cases);
    printf("RESULT: %s\n", failures ? "FAIL" : "PASS");
    return failures ? 1 : 0;
}
