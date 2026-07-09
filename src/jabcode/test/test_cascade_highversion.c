/*
 * test_cascade_highversion.c -- regression guard for the high-version cascade
 * decode failure at N>=3.
 *
 * Finding (2026-07-09, downstream FSMA-204 Phase-3 testing): a docked cascade
 * of 3+ symbols at 16 colours and near-max symbol versions (~v31) ENCODES
 * successfully but decodeJABCode returns an empty/absent payload. The same
 * per-symbol load succeeds at N=2; lower versions succeed at N=3. Distinct
 * from the documented v>=10 && v%5==0 slave-alignment family (PR #113).
 *
 * Cascade construction follows bench_cascade.c: sequential positions 0..N-1,
 * uniform versions, per-symbol ECC. Decode straight from enc->bitmap -- no
 * PNG, no FFM -- so a failure here is native-codec-only.
 *
 * Build/run: make test-cascade-hv   (see Makefile)
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "jabcode.h"

static jab_data *make_payload(jab_int32 nbytes, unsigned seed)
{
    jab_data *d = (jab_data *)malloc(sizeof(jab_data) + nbytes);
    if (!d) return NULL;
    d->length = nbytes;
    /* deterministic LCG so runs are reproducible */
    unsigned x = seed;
    for (jab_int32 i = 0; i < nbytes; i++) {
        x = x * 1664525u + 1013904223u;
        d->data[i] = (jab_char)(x >> 24);
    }
    return d;
}

/* java.util.Random-compatible nextBytes, so payloads found failing through the
 * JVM harnesses reproduce bit-identically here. */
static jab_data *make_payload_javarandom(jab_int32 nbytes, long long jseed)
{
    jab_data *d = (jab_data *)malloc(sizeof(jab_data) + nbytes);
    if (!d) return NULL;
    d->length = nbytes;
    unsigned long long s = ((unsigned long long)jseed ^ 0x5DEECE66DULL) & ((1ULL << 48) - 1);
    for (jab_int32 i = 0; i < nbytes; ) {
        s = (s * 0x5DEECE66DULL + 0xBULL) & ((1ULL << 48) - 1);
        int rnd = (int)(s >> 16);
        for (int n = 0; n < 4 && i < nbytes; n++) {
            d->data[i++] = (jab_char)rnd;
            rnd >>= 8;
        }
    }
    return d;
}

/* Encode a uniform-version cascade from the given payload and round-trip it.
 * Takes ownership of `in`. Returns 0 on success. */
static int roundtrip_payload(jab_data *in, jab_int32 nc, jab_int32 N, jab_int32 V,
                             jab_int32 ecc, const char *label)
{
    int rc = 1;
    jab_encode *enc = createEncode(nc, N);
    if (!in || !enc) { fprintf(stderr, "%s: alloc failed\n", label); goto done; }

    for (jab_int32 i = 0; i < N; i++) {
        enc->symbol_positions[i]  = i;
        enc->symbol_versions[i].x = V;
        enc->symbol_versions[i].y = V;
        /* ecc < 0 mimics the Panama wrapper's historical behaviour: ECC set on
         * symbol 0 ONLY, slaves left at 0 (createEncode zero-init). */
        if (enc->symbol_ecc_levels) {
            if (ecc >= 0) enc->symbol_ecc_levels[i] = (jab_byte)ecc;
            else if (i == 0) enc->symbol_ecc_levels[0] = (jab_byte)(-ecc);
        }
    }

    if (generateJABCode(enc, in) != 0 || !enc->bitmap) {
        fprintf(stderr, "%s: ENCODE failed\n", label);
        goto done;
    }

    /* Round-trip the bitmap through the in-memory PNG path (the transport the
     * JVM consumers use) so the guard covers saveImageToMemory ->
     * readImageFromMemory as well as raw decode. */
    jab_int32 png_len = 0;
    jab_byte *png = saveImageToMemory(enc->bitmap, &png_len);
    if (!png || png_len <= 0) {
        fprintf(stderr, "%s: saveImageToMemory failed\n", label);
        goto done;
    }
    jab_bitmap *reread = readImageFromMemory(png, png_len);
    free(png);
    if (!reread) {
        fprintf(stderr, "%s: readImageFromMemory failed\n", label);
        goto done;
    }

    jab_int32 status = 0;
    jab_data *out = decodeJABCode(reread, NORMAL_DECODE, &status);
    free(reread);
    if (!out) {
        fprintf(stderr, "%s: DECODE returned NULL (status=%d)\n", label, status);
        goto done;
    }
    if (out->length != in->length ||
        memcmp(out->data, in->data, (size_t)in->length) != 0) {
        fprintf(stderr, "%s: PAYLOAD MISMATCH (in=%d bytes, out=%d bytes, status=%d)\n",
                label, in->length, out->length, status);
        free(out);
        goto done;
    }
    free(out);
    printf("%s: OK (%d bytes, status=%d)\n", label, in->length, status);
    rc = 0;

done:
    if (enc) destroyEncode(enc);
    free(in);
    return rc;
}

static int roundtrip(jab_int32 nc, jab_int32 N, jab_int32 V, jab_int32 ecc,
                     jab_int32 payload_bytes, const char *label)
{
    return roundtrip_payload(
        make_payload(payload_bytes, 0xC0FFEE ^ (unsigned)(nc * 131 + N * 17 + V)),
        nc, N, V, ecc, label);
}

int main(void)
{
    int failures = 0;

    /* Controls -- these passed before the fix and must keep passing. */
    failures += roundtrip(16, 2, 31, 5, 6 * 1024, "control  nc16 N=2 v31 6KB");
    failures += roundtrip(16, 3, 26, 5, 6 * 1024, "control  nc16 N=3 v26 6KB");

    /* The bug: same per-symbol load as the N=2 control, one more symbol. */
    failures += roundtrip(16, 3, 31, 5, 9 * 1024, "REGRESSION nc16 N=3 v31 9KB");
    failures += roundtrip(16, 4, 31, 5, 12 * 1024, "REGRESSION nc16 N=4 v31 12KB");

    /* Wrapper-parity cases: ECC on symbol 0 only, slaves zero-init (the shape
     * the Panama wrapper actually produced). ecc<0 = master-only |ecc|. */
    failures += roundtrip(16, 2, 31, -5, 6 * 1024, "wrapper-parity nc16 N=2 v31 6KB ecc0=5 slaves=0");
    failures += roundtrip(16, 3, 31, -5, 9 * 1024, "wrapper-parity nc16 N=3 v31 9KB ecc0=5 slaves=0");
    failures += roundtrip(16, 4, 31, -5, 12 * 1024, "wrapper-parity nc16 N=4 v31 12KB ecc0=5 slaves=0");

    /* THE ROOT-CAUSE REGRESSION: 9216 bytes from java.util.Random(0xFA0003)
     * .nextBytes — a payload whose mode plan enters a >8207-byte byte run from
     * NUMERIC mode. The encoder's >8207 continuation wrote the numeric
     * shift-to-byte token (111100 = 60) with width 5 instead of 6, dropping a
     * bit and shearing the rest of the stream ("Not enough bits to decode").
     * Content-dependent: payloads whose giant runs are entered from upper or
     * alphanumeric (widths 7/8, which were correct) decode fine. */
    failures += roundtrip_payload(make_payload_javarandom(9 * 1024, 0xFA0003LL),
                                  16, 3, 31, -5, "REGRESSION nc16 N=3 v31 9KB javaRandom(0xFA0003)");
    failures += roundtrip_payload(make_payload_javarandom(9 * 1024, 0xFA0003LL),
                                  16, 3, 31, 5, "REGRESSION nc16 N=3 v31 9KB javaRandom(0xFA0003) allECC5");

    /* Guard for the stale-factor companion bug: TWO >8207-byte byte runs in one
     * message. factor was cumulative across the message, so the second run's
     * continuation compared against factor*8207 bytes it never reached and
     * streamed past the 8207 cap headerless. 0xFF stays in byte mode; the
     * "AAAA..." bridge forces the plan back to upper between the runs. */
    {
        jab_int32 run = 8300, bridge = 64;
        jab_int32 nbytes = run + bridge + run;
        jab_data *two = (jab_data *)malloc(sizeof(jab_data) + nbytes);
        two->length = nbytes;
        memset(two->data, 0xFF, run);
        memset(two->data + run, 'A', bridge);
        memset(two->data + run + bridge, 0xFF, run);
        failures += roundtrip_payload(two, 16, 6, 31, 5,
                                      "REGRESSION nc16 N=6 v31 two >8207-byte runs");
    }

    if (failures) {
        fprintf(stderr, "test_cascade_highversion: %d failure(s)\n", failures);
        return 1;
    }
    printf("test_cascade_highversion: all cases OK\n");
    return 0;
}
