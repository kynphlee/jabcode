/*
 * gen_vectors.c -- golden-vector conformance generator for the JAB Code C codec.
 *
 * Encodes a fixed matrix of parameter vectors against libjabcode and writes one
 * JSON object per line (JSONL) to conformance/vectors.jsonl. Each record pins the
 * exact encode parameters, the deterministic payload (base64), and the GROUND-TRUTH
 * geometry the C encoder produced (symbol count, per-symbol side modules and pixel
 * extent) plus a round-trip self-check (encode -> decodeJABCode -> compare bytes).
 *
 * These vectors are the cross-binding oracle: any other binding (Swift, Java/Panama,
 * Android) encoding the same params + payload must reproduce the same geometry and
 * round-trip result. The C codec is the reference, so its output IS the golden truth.
 *
 * GEOMETRY TRUTH (include/jabcode.h):
 *   VERSION2SIZE(v) = 4*v + 17   ->  side_modules
 *   px              = side_modules * module_size
 *
 * Output is written via fopen() to conformance/vectors.jsonl, NOT stdout: the
 * encoder prints "JABCode Error: Message does not fit ..." to stdout on overflow,
 * which would otherwise corrupt the JSONL stream (the negative-overflow vector
 * deliberately triggers exactly that path).
 *
 * Build/run: see conformance/Makefile (target: vectors) or conformance/README.md.
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "jabcode.h"

#define OUT_PATH "conformance/vectors.jsonl"
#define MAX_SYM 16

/* ---- deterministic payloads -------------------------------------------------
 * SMALL_PAYLOAD: short, fits every single symbol in the matrix (incl. 2c v1).
 * BIG_PAYLOAD:   too large for one 8c v(1,1) symbol -> overflow; fits a 2-symbol
 *                cascade. 600 bytes of a fixed pseudo-random stream.
 */
static const char SMALL_PAYLOAD[] = "JABCode-conformance-v1";  /* 22 bytes */

#define BIG_LEN 600
static unsigned char BIG_PAYLOAD[BIG_LEN];
static void init_big_payload(void){
    for(int i=0;i<BIG_LEN;i++) BIG_PAYLOAD[i]=(unsigned char)((i*131+17)&0xFF);
}

/* ---- base64 ----------------------------------------------------------------- */
static const char B64[]="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
/* encodes n bytes into caller-owned buffer (>= 4*((n+2)/3)+1); returns out */
static char *b64_encode(const unsigned char *in,int n,char *out){
    int o=0;
    for(int i=0;i<n;i+=3){
        int b0=in[i];
        int b1=(i+1<n)?in[i+1]:0;
        int b2=(i+2<n)?in[i+2]:0;
        out[o++]=B64[b0>>2];
        out[o++]=B64[((b0&0x03)<<4)|(b1>>4)];
        out[o++]=(i+1<n)?B64[((b1&0x0F)<<2)|(b2>>6)]:'=';
        out[o++]=(i+2<n)?B64[b2&0x3F]:'=';
    }
    out[o]='\0';
    return out;
}

/* ---- one vector spec -------------------------------------------------------- */
typedef struct {
    const char     *id;
    int             color_number;
    int             symbol_number;
    int             module_size;          /* 0 -> codec default (DEFAULT_MODULE_SIZE=12) */
    jab_vector2d    versions[MAX_SYM];     /* per-symbol (x,y); {0,0} -> auto (single-symbol only) */
    jab_byte        ecc[MAX_SYM];          /* per-symbol ecc level */
    int             versions_set;          /* 1 if versions[] explicitly provided */
    const unsigned char *payload;
    int             payload_len;
} vector_spec;

/* result of encoding one spec */
typedef struct {
    int             ok_encode;             /* bitmap produced */
    int             symbol_count;
    jab_vector2d    side[MAX_SYM];         /* per-symbol side modules (side_size) */
    int             module_size;           /* effective module size used */
    int             roundtrip;             /* decode == payload */
} vector_result;

/* JSON-escape a base64 string (b64 alphabet contains '/' and '+' only -> no escape
 * needed, but '=' padding is JSON-safe; we still copy verbatim). base64 never
 * contains '"' or '\\', so a direct emit is safe. */

static void encode_spec(const vector_spec *s, vector_result *r){
    memset(r,0,sizeof(*r));
    jab_encode *enc = createEncode(s->color_number, s->symbol_number);
    if(!enc) return;

    if(s->module_size>0) enc->module_size = s->module_size;
    r->module_size = enc->module_size;     /* createEncode sets DEFAULT_MODULE_SIZE */

    for(int i=0;i<s->symbol_number;i++){
        if(s->symbol_number>1){
            /* cascade: positions sequential (0=master, edge-adjacent docks) */
            enc->symbol_positions[i] = i;
        }
        if(s->versions_set){
            enc->symbol_versions[i] = s->versions[i];
        }
        if(enc->symbol_ecc_levels) enc->symbol_ecc_levels[i] = s->ecc[i];
    }

    jab_data *d = (jab_data*)malloc(sizeof(jab_data)+s->payload_len);
    d->length = s->payload_len;
    memcpy(d->data, s->payload, s->payload_len);

    generateJABCode(enc, d);

    if(enc->bitmap){
        r->ok_encode    = 1;
        r->module_size  = enc->module_size;
        r->symbol_count = enc->symbol_number;
        for(int i=0;i<enc->symbol_number && i<MAX_SYM;i++)
            r->side[i] = enc->symbols[i].side_size;

        /* round-trip self-check: decode the freshly-encoded bitmap and compare.
         * Pin Nc to the known encode colour count so the decoder's auto-detect
         * fallback ladder is collapsed to the correct mode (the bitmap's Nc is
         * not in question here -- we encoded it). This is the cross-binding
         * contract: a consumer decoding this golden bitmap knows its Nc and
         * should pin likewise. Single-threaded, so mutating the global is safe. */
        jab_int32 saved_pref = jabGetPreferredColorCount();
        jabSetPreferredColorCount(s->color_number);
        jab_int32 st = 0;
        jab_data *back = decodeJABCode(enc->bitmap, NORMAL_DECODE, &st);
        jabSetPreferredColorCount(saved_pref);
        if(back){
            if(back->length == s->payload_len &&
               memcmp(back->data, s->payload, s->payload_len)==0)
                r->roundtrip = 1;
            free(back);
        }
    }

    free(d);
    destroyEncode(enc);
}

/* emit one JSONL record */
static void emit(FILE *f, const vector_spec *s, const vector_result *r){
    /* base64 payload */
    int b64cap = 4*((s->payload_len+2)/3)+1;
    char *b64 = (char*)malloc(b64cap);
    b64_encode(s->payload, s->payload_len, b64);

    fprintf(f,"{\"id\":\"%s\",", s->id);

    /* params */
    fprintf(f,"\"params\":{");
    fprintf(f,"\"colorNumber\":%d,", s->color_number);
    fprintf(f,"\"eccLevel\":%d,", s->ecc[0]);
    fprintf(f,"\"symbolNumber\":%d,", s->symbol_number);
    fprintf(f,"\"symbolVersions\":[");
    for(int i=0;i<s->symbol_number;i++){
        int vx,vy;
        if(s->versions_set){ vx=s->versions[i].x; vy=s->versions[i].y; }
        else { vx=0; vy=0; }   /* auto-sized */
        fprintf(f,"%s[%d,%d]", i?",":"", vx, vy);
    }
    fprintf(f,"],");
    fprintf(f,"\"moduleSize\":%d", (s->module_size>0)?s->module_size:r->module_size);
    fprintf(f,"},");

    /* payload */
    fprintf(f,"\"payload_b64\":\"%s\",", b64);

    /* expect */
    fprintf(f,"\"expect\":{");
    fprintf(f,"\"symbol_count\":%d,", r->symbol_count);
    fprintf(f,"\"symbols\":[");
    for(int i=0;i<r->symbol_count && i<MAX_SYM;i++){
        int mx=r->side[i].x, my=r->side[i].y;
        int ms=r->module_size;
        fprintf(f,"%s{\"modules_x\":%d,\"modules_y\":%d,\"px_x\":%d,\"px_y\":%d}",
                i?",":"", mx, my, mx*ms, my*ms);
    }
    fprintf(f,"],");
    fprintf(f,"\"roundtrip\":%s", r->roundtrip?"true":"false");
    fprintf(f,"}}\n");

    free(b64);
}

int main(void){
    init_big_payload();

    FILE *f = fopen(OUT_PATH, "wb");
    if(!f){ fprintf(stderr,"cannot open %s for writing\n", OUT_PATH); return 1; }

    vector_spec specs[64];
    int n=0;

    /* ---- single-symbol matrix: colorNumber {2,8,128} x version {(1,1),(6,6)}
     *      x eccLevel {0,3}, symbolNumber=1, default module size (12). --------- */
    const int colors[] = {2, 8, 128};
    const int vers[]   = {1, 6};
    const int eccs[]   = {0, 3};
    for(int ci=0; ci<3; ci++) for(int vi=0; vi<2; vi++) for(int ei=0; ei<2; ei++){
        vector_spec *s = &specs[n];
        memset(s,0,sizeof(*s));
        static char idbuf[3*2*2][40];
        int slot = ((ci*2)+vi)*2+ei;
        snprintf(idbuf[slot], sizeof(idbuf[slot]),
                 "single_%dc_v%d_ecc%d", colors[ci], vers[vi], eccs[ei]);
        s->id            = idbuf[slot];
        s->color_number  = colors[ci];
        s->symbol_number = 1;
        s->module_size   = 0;                 /* default 12 */
        s->versions_set  = 1;
        s->versions[0].x = vers[vi];
        s->versions[0].y = vers[vi];
        s->ecc[0]        = (jab_byte)eccs[ei];
        s->payload       = (const unsigned char*)SMALL_PAYLOAD;
        s->payload_len   = (int)sizeof(SMALL_PAYLOAD)-1;
        n++;
    }

    /* ---- canonical create-site vector: 8c, moduleSize 12, version (1,1), ecc 0
     *      -> modules 21, px 252. (Distinct id so consumers can pin it directly.) */
    {
        vector_spec *s = &specs[n++];
        memset(s,0,sizeof(*s));
        s->id            = "create_site_8c_v1_ms12_ecc0";
        s->color_number  = 8;
        s->symbol_number = 1;
        s->module_size   = 12;
        s->versions_set  = 1;
        s->versions[0].x = 1;
        s->versions[0].y = 1;
        s->ecc[0]        = 0;
        s->payload       = (const unsigned char*)SMALL_PAYLOAD;
        s->payload_len   = (int)sizeof(SMALL_PAYLOAD)-1;
    }

    /* ---- cascade: 8c symbolNumber=2 versions [[6,6],[4,4]] ------------------- */
    {
        vector_spec *s = &specs[n++];
        memset(s,0,sizeof(*s));
        s->id            = "cascade_8c_n2_v66_v44";
        s->color_number  = 8;
        s->symbol_number = 2;
        s->module_size   = 0;
        s->versions_set  = 1;
        s->versions[0].x = 6; s->versions[0].y = 6;
        s->versions[1].x = 4; s->versions[1].y = 4;
        s->ecc[0]=3; s->ecc[1]=3;
        s->payload       = (const unsigned char*)SMALL_PAYLOAD;
        s->payload_len   = (int)sizeof(SMALL_PAYLOAD)-1;
    }

    /* ---- cascade: 8c symbolNumber=4, four uniform versions (6,6) ------------- */
    {
        vector_spec *s = &specs[n++];
        memset(s,0,sizeof(*s));
        s->id            = "cascade_8c_n4_v66";
        s->color_number  = 8;
        s->symbol_number = 4;
        s->module_size   = 0;
        s->versions_set  = 1;
        for(int i=0;i<4;i++){ s->versions[i].x=6; s->versions[i].y=6; s->ecc[i]=3; }
        s->payload       = (const unsigned char*)SMALL_PAYLOAD;
        s->payload_len   = (int)sizeof(SMALL_PAYLOAD)-1;
    }

    /* ---- cascade: 8c symbolNumber=8, eight uniform versions (6,6) ------------ */
    {
        vector_spec *s = &specs[n++];
        memset(s,0,sizeof(*s));
        s->id            = "cascade_8c_n8_v66";
        s->color_number  = 8;
        s->symbol_number = 8;
        s->module_size   = 0;
        s->versions_set  = 1;
        for(int i=0;i<8;i++){ s->versions[i].x=6; s->versions[i].y=6; s->ecc[i]=3; }
        s->payload       = (const unsigned char*)SMALL_PAYLOAD;
        s->payload_len   = (int)sizeof(SMALL_PAYLOAD)-1;
    }

    /* ---- negative-overflow: BIG_PAYLOAD too large for one 8c v(1,1) symbol
     *      -> roundtrip false (single). Same payload at symbolNumber=2 -> true. */
    {
        vector_spec *s = &specs[n++];
        memset(s,0,sizeof(*s));
        s->id            = "overflow_single_8c_v1";
        s->color_number  = 8;
        s->symbol_number = 1;
        s->module_size   = 0;
        s->versions_set  = 1;
        s->versions[0].x = 1; s->versions[0].y = 1;
        s->ecc[0]        = 0;
        s->payload       = BIG_PAYLOAD;
        s->payload_len   = BIG_LEN;
    }
    {
        vector_spec *s = &specs[n++];
        memset(s,0,sizeof(*s));
        s->id            = "overflow_resolved_8c_n2_v66";
        s->color_number  = 8;
        s->symbol_number = 2;
        s->module_size   = 0;
        s->versions_set  = 1;
        s->versions[0].x = 6; s->versions[0].y = 6;
        s->versions[1].x = 6; s->versions[1].y = 6;
        s->ecc[0]=3; s->ecc[1]=3;
        s->payload       = BIG_PAYLOAD;
        s->payload_len   = BIG_LEN;
    }

    int created_modules=0, created_px=0;
    for(int i=0;i<n;i++){
        vector_result r;
        encode_spec(&specs[i], &r);
        emit(f, &specs[i], &r);
        if(strcmp(specs[i].id,"create_site_8c_v1_ms12_ecc0")==0 && r.symbol_count>0){
            created_modules = r.side[0].x;
            created_px      = r.side[0].x * r.module_size;
        }
    }

    fclose(f);

    fprintf(stderr,"wrote %d vectors to %s\n", n, OUT_PATH);
    fprintf(stderr,"create-site geometry: modules=%d px=%d\n", created_modules, created_px);
    if(created_modules!=21 || created_px!=252){
        fprintf(stderr,"FAIL: create-site expected modules=21 px=252\n");
        return 2;
    }
    return 0;
}
