/*
 * rgba_sha — pixel-identity receipt for the WASM-vs-native A/B.
 *
 * For each PNG argument, loads it with the SAME loader the native probe uses
 * (libjabcode readImage → RGBA jab_bitmap) and prints one JSONL record with
 * the SHA-256 of the raw RGBA bytes. bench.html computes the same digest over
 * canvas ImageData bytes; equal hashes prove both decoders consumed
 * byte-identical pixels, which is the premise the decode-rate comparison
 * stands on. Any mismatch demotes that image's A/B cell from "same pixels"
 * to "same file" — a distinction the report must keep.
 *
 * SHA-256 implementation mirrors robustness/r0/rig/r0_decode.c (public-domain
 * style, kept local so the rig instrument stays untouched).
 */

#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include "jabcode.h"

typedef struct {
    uint32_t state[8];
    uint64_t bitlen;
    uint8_t  data[64];
    uint32_t datalen;
} sha256_ctx;

static const uint32_t K256[64] = {
    0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
    0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
    0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
    0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
    0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
    0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
    0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
    0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2
};

#define ROTR(x,n) (((x) >> (n)) | ((x) << (32-(n))))

static void sha256_transform(sha256_ctx *c, const uint8_t *d) {
    uint32_t m[64], a,b,e,f,g,h,t1,t2,cc,dd;
    for (int i=0,j=0;i<16;i++,j+=4)
        m[i]=(uint32_t)(d[j]<<24)|(d[j+1]<<16)|(d[j+2]<<8)|d[j+3];
    for (int i=16;i<64;i++) {
        uint32_t s0=ROTR(m[i-15],7)^ROTR(m[i-15],18)^(m[i-15]>>3);
        uint32_t s1=ROTR(m[i-2],17)^ROTR(m[i-2],19)^(m[i-2]>>10);
        m[i]=m[i-16]+s0+m[i-7]+s1;
    }
    a=c->state[0];b=c->state[1];cc=c->state[2];dd=c->state[3];
    e=c->state[4];f=c->state[5];g=c->state[6];h=c->state[7];
    for (int i=0;i<64;i++) {
        uint32_t S1=ROTR(e,6)^ROTR(e,11)^ROTR(e,25);
        uint32_t ch=(e&f)^((~e)&g);
        t1=h+S1+ch+K256[i]+m[i];
        uint32_t S0=ROTR(a,2)^ROTR(a,13)^ROTR(a,22);
        uint32_t maj=(a&b)^(a&cc)^(b&cc);
        t2=S0+maj;
        h=g;g=f;f=e;e=dd+t1;dd=cc;cc=b;b=a;a=t1+t2;
    }
    c->state[0]+=a;c->state[1]+=b;c->state[2]+=cc;c->state[3]+=dd;
    c->state[4]+=e;c->state[5]+=f;c->state[6]+=g;c->state[7]+=h;
}

static void sha256_init(sha256_ctx *c) {
    c->datalen=0;c->bitlen=0;
    c->state[0]=0x6a09e667;c->state[1]=0xbb67ae85;c->state[2]=0x3c6ef372;c->state[3]=0xa54ff53a;
    c->state[4]=0x510e527f;c->state[5]=0x9b05688c;c->state[6]=0x1f83d9ab;c->state[7]=0x5be0cd19;
}

static void sha256_update(sha256_ctx *c, const uint8_t *d, size_t len) {
    for (size_t i=0;i<len;i++) {
        c->data[c->datalen++]=d[i];
        if (c->datalen==64){ sha256_transform(c,c->data); c->bitlen+=512; c->datalen=0; }
    }
}

static void sha256_final(sha256_ctx *c, uint8_t *hash) {
    uint32_t i=c->datalen;
    if (c->datalen<56){ c->data[i++]=0x80; while(i<56) c->data[i++]=0x00; }
    else { c->data[i++]=0x80; while(i<64) c->data[i++]=0x00; sha256_transform(c,c->data); memset(c->data,0,56); }
    c->bitlen += (uint64_t)c->datalen*8;
    for (int k=7;k>=0;k--) c->data[56+(7-k)]=(uint8_t)(c->bitlen>>(k*8));
    sha256_transform(c,c->data);
    for (i=0;i<4;i++)
        for (int j=0;j<8;j++)
            hash[i+j*4]=(uint8_t)((c->state[j]>>(24-i*8))&0xff);
}

int main(int argc, char **argv) {
    if (argc < 2) {
        fprintf(stderr, "usage: %s <image.png> [...]\n", argv[0]);
        return 2;
    }
    for (int i = 1; i < argc; i++) {
        jab_bitmap *bm = readImage((jab_char *)argv[i]);
        if (!bm) {
            printf("{\"file\":\"%s\",\"error\":\"readImage failed\"}\n", argv[i]);
            continue;
        }
        size_t n = (size_t)bm->width * bm->height * bm->channel_count;
        sha256_ctx c; uint8_t h[32]; char hex[65];
        sha256_init(&c); sha256_update(&c, bm->pixel, n); sha256_final(&c, h);
        static const char *hx = "0123456789abcdef";
        for (int j = 0; j < 32; j++) { hex[j*2]=hx[h[j]>>4]; hex[j*2+1]=hx[h[j]&0xf]; }
        hex[64] = '\0';
        printf("{\"file\":\"%s\",\"width\":%d,\"height\":%d,\"channels\":%d,\"px_sha\":\"%s\"}\n",
               argv[i], bm->width, bm->height, bm->channel_count, hex);
        free(bm);
    }
    return 0;
}
