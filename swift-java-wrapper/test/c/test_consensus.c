/*
 * test_consensus.c — host test for jabMobileDecodeConsensus (anti-fabrication).
 *
 * Verifies the decode-CONSENSUS backstop for the d486388 default-mode (Nc=2)
 * fall-through: decode K frames independently, accept the payload only if >= M
 * frames agree byte-identically. A genuine code reproduces across frames; a
 * one-off fall-through fabrication does not. See
 * docs/cassandra-register/H_nc2_decode_failure.md (2026-06-11 "Open caveat").
 *
 * Build (from repo root, with libjabcode built in src/jabcode/build):
 *   gcc -I src/jabcode/include -I swift-java-wrapper/include \
 *       swift-java-wrapper/src/c/mobile_bridge.c swift-java-wrapper/src/c/mobile_utils.c \
 *       swift-java-wrapper/test/c/test_consensus.c \
 *       -L src/jabcode/build -ljabcode -lpng16 -lz -lm -o /tmp/test_consensus
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "jabcode.h"
#include "mobile_bridge.h"

/* Load a PNG fixture as a width*height*4 RGBA buffer (caller frees). */
static jab_byte* load_rgba(const char* path, jab_int32* w, jab_int32* h) {
    jab_bitmap* bm = readImage((char*)path);
    if (!bm) { fprintf(stderr, "readImage failed: %s\n", path); return NULL; }
    *w = bm->width; *h = bm->height;
    jab_int32 n = bm->width * bm->height;
    jab_byte* rgba = (jab_byte*)malloc((size_t)n * 4);
    if (!rgba) { free(bm); return NULL; }
    if (bm->channel_count == 4) {
        memcpy(rgba, bm->pixel, (size_t)n * 4);
    } else if (bm->channel_count == 3) {
        for (jab_int32 i = 0; i < n; i++) {
            rgba[i*4+0] = bm->pixel[i*3+0];
            rgba[i*4+1] = bm->pixel[i*3+1];
            rgba[i*4+2] = bm->pixel[i*3+2];
            rgba[i*4+3] = 255;
        }
    } else {
        fprintf(stderr, "unexpected channel_count %d for %s\n", bm->channel_count, path);
        free(rgba); free(bm); return NULL;
    }
    free(bm);
    return rgba;
}

/* Independently-noised RGBA copy (keeps alpha), amplitude amp, given seed. */
static jab_byte* noisy_copy(const jab_byte* src, jab_int32 w, jab_int32 h, int amp, unsigned seed) {
    jab_int32 n = w * h * 4;
    jab_byte* out = (jab_byte*)malloc((size_t)n);
    if (!out) return NULL;
    srand(seed);
    for (jab_int32 i = 0; i < n; i++) {
        if ((i & 3) == 3) { out[i] = src[i]; continue; }   /* preserve alpha */
        int v = (int)src[i] + (rand() % (2*amp + 1)) - amp;
        out[i] = (jab_byte)(v < 0 ? 0 : (v > 255 ? 255 : v));
    }
    return out;
}

static int payload_is(jab_data* d, const char* s) {
    if (!d) return 0;
    size_t L = strlen(s);
    return (size_t)d->length == L && memcmp(d->data, s, L) == 0;
}

int main(int argc, char** argv) {
    const char* dir = argc > 1 ? argv[1]
        : "/mnt/b34628fa-d41e-4c37-8caf-f06a6ecbb1ae/projects/practice/barcode/jabcode/.claude/worktrees/ws-ccm-d65-exp1/jabauth-android/diagnostic-app/images/full-spectrum";
    char p1[700], p2[700], p3[700];
    snprintf(p1, sizeof p1, "%s/nc1-4c-20260521.png",  dir);
    snprintf(p2, sizeof p2, "%s/nc2-8c-20260521.png",  dir);
    snprintf(p3, sizeof p3, "%s/nc3-16c-20260521.png", dir);

    jab_int32 w1,h1,w2,h2,w3,h3;
    jab_byte* nc1 = load_rgba(p1, &w1, &h1);
    jab_byte* nc2 = load_rgba(p2, &w2, &h2);
    jab_byte* nc3 = load_rgba(p3, &w3, &h3);
    if (!nc1 || !nc2 || !nc3) { fprintf(stderr, "fixture load failed\n"); return 2; }
    /* nc1 and nc2 share dims (used together in the mixed tests); nc3 may differ
     * (it is only ever used in same-dimension groups of its own). */
    if (!(w1==w2 && h1==h2)) {
        fprintf(stderr, "nc1/nc2 differ in size (%dx%d vs %dx%d)\n", w1,h1,w2,h2);
        return 2;
    }
    const jab_int32 W = w1, H = h1;   /* the 252x252 group: nc1, nc2, blank */

    int fails = 0, color;
    jab_data* r;

    /* A — genuine reproduces: 3x nc2, M=2 -> accept nc2 (colour 8). */
    { jab_byte* b[3] = {nc2,nc2,nc2};
      r = jabMobileDecodeConsensus(b, W,H, 3, 2, &color);
      int ok = payload_is(r, "HELLO-Nc-2") && color == 8;
      printf("A genuine 3x nc2        : %s (color=%d, %s)\n", ok?"PASS":"FAIL", color, r?"decoded":"null");
      if (!ok) fails++; jabMobileDataFree(r); }

    /* B — disagreeing frames rejected: nc1, nc2, blank all differ, M=2 -> REJECT. */
    { jab_int32 n = W*H*4; jab_byte* blank = (jab_byte*)malloc((size_t)n); memset(blank, 255, (size_t)n);
      jab_byte* b[3] = {nc1,nc2,blank};
      r = jabMobileDecodeConsensus(b, W,H, 3, 2, &color);
      int ok = (r == NULL);
      printf("B 3 disagreeing frames  : %s (%s)\n", ok?"PASS":"FAIL", r?"WRONGLY accepted":"rejected");
      if (!ok) fails++; jabMobileDataFree(r); free(blank); }

    /* C — negative control: 3x nc3 -> nc3 (colour 16), never nc2. */
    { jab_byte* b[3] = {nc3,nc3,nc3};
      r = jabMobileDecodeConsensus(b, w3,h3, 3, 2, &color);
      int ok = r && color == 16 && !payload_is(r, "HELLO-Nc-2");
      printf("C neg-control 3x nc3    : %s (color=%d)\n", ok?"PASS":"FAIL", color);
      if (!ok) fails++; jabMobileDataFree(r); }

    /* D — majority wins: nc2,nc2,nc1, M=2 -> accept nc2 (the two that agree). */
    { jab_byte* b[3] = {nc2,nc2,nc1};
      r = jabMobileDecodeConsensus(b, W,H, 3, 2, &color);
      int ok = payload_is(r, "HELLO-Nc-2") && color == 8;
      printf("D 2-of-3 nc2 majority   : %s (color=%d)\n", ok?"PASS":"FAIL", color);
      if (!ok) fails++; jabMobileDataFree(r); }

    /* E — negative control: 3x blank white -> REJECT. */
    { jab_int32 n = W*H*4; jab_byte* blank = (jab_byte*)malloc((size_t)n); memset(blank, 255, (size_t)n);
      jab_byte* b[3] = {blank,blank,blank};
      r = jabMobileDecodeConsensus(b, W,H, 3, 2, &color);
      int ok = (r == NULL);
      printf("E neg-control 3x blank  : %s (%s)\n", ok?"PASS":"FAIL", r?"WRONGLY decoded":"rejected");
      if (!ok) fails++; jabMobileDataFree(r); free(blank); }

    /* F — fabrication stress: 3 INDEPENDENTLY-noised nc3 frames must never yield
     * a spurious nc2. If a single noisy frame fabricates "HELLO-Nc-2", the other
     * two won't reproduce it byte-identically, so consensus rejects it. */
    { jab_byte* a = noisy_copy(nc3, w3,h3, 40, 11);
      jab_byte* b = noisy_copy(nc3, w3,h3, 40, 22);
      jab_byte* c = noisy_copy(nc3, w3,h3, 40, 33);
      jab_byte* bufs[3] = {a,b,c};
      r = jabMobileDecodeConsensus(bufs, w3,h3, 3, 2, &color);
      int ok = !payload_is(r, "HELLO-Nc-2");
      printf("F fabrication stress    : %s (%s)\n", ok?"PASS":"FAIL",
             r ? (color==16 ? "decoded nc3 (fine)" : "decoded other (not nc2)") : "rejected (fine)");
      if (!ok) fails++; jabMobileDataFree(r); free(a); free(b); free(c); }

    free(nc1); free(nc2); free(nc3);
    printf("\n%s (%d failure%s)\n", fails==0 ? "ALL CONSENSUS TESTS PASS" : "SOME TESTS FAILED",
           fails, fails==1?"":"s");
    return fails == 0 ? 0 : 1;
}
