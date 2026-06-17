/*
 * r0_decode — host-side single-image JABCode decode probe for the R0
 * decode-rate rig.
 *
 * Given ONE image path, it runs the same decode path the on-device bridge
 * uses (swift-java-wrapper/src/c/mobile_bridge.c:jabMobileDecodeCameraWithMeta):
 *
 *     jabSetStrictPartIIRequired(1);
 *     decodeJABCodeEx(bitmap, NORMAL_DECODE, &status, symbols, MAX_SYMBOL_NUMBER);
 *     jabSetStrictPartIIRequired(0);
 *
 * ...with jabSetDiagVerbose(1) so the decoder/detector emit the SAME greppable
 * FAIL_STAGE / DIAG_* markers the field traces use. The decoder writes those
 * markers to stdout (desktop JAB_REPORT_INFO -> printf). We dup() the real
 * stdout aside, redirect fd 1 into a temp file for the duration of the decode
 * to capture the marker stream, then restore fd 1 and print ONE JSON object
 * describing the outcome — including a filtered slice of the captured markers
 * so the Python runner can attribute a fail-stage.
 *
 * SECURITY: the decoded payload plaintext is NEVER printed or stored. We emit
 * only its length and a SHA-256 hex digest (computed in-process here) so the
 * runner can verify against a known-payload hash by comparison. The captured
 * diagnostic log is filtered to structural markers only (FAIL_*, DIAG_*,
 * [PartI_DIAG], [PartII_DIAG], "DIAG detectMaster") — payload bytes never
 * appear in those lines.
 *
 * Output (one line, stdout): a JSON object. See emit_json() for the schema.
 * Exit code: 0 on clean run (decode success OR honest failure both exit 0 —
 * a failed decode is a valid datapoint, not a harness error); non-zero only on
 * harness errors (bad args, image load failure, OOM).
 *
 * Build: see Makefile in this directory (links ../../../src/jabcode/build/libjabcode.a).
 */
/* _XOPEN_SOURCE>=500 exposes mkstemp / dup2 / lseek prototypes under -std=c11.
 * Must precede every system-header include. */
#define _XOPEN_SOURCE 700
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <time.h>
#include <unistd.h>
#include <fcntl.h>

#include "jabcode.h"

/* ------------------------------------------------------------------ */
/* Minimal self-contained SHA-256 (public-domain style impl).         */
/* Used ONLY to digest decoded payload bytes so we never print them.  */
/* ------------------------------------------------------------------ */
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
        m[i]=(d[j]<<24)|(d[j+1]<<16)|(d[j+2]<<8)|d[j+3];
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

static void sha256_hex(const uint8_t *data, size_t len, char out[65]) {
    sha256_ctx c; uint8_t h[32];
    sha256_init(&c); sha256_update(&c,data,len); sha256_final(&c,h);
    static const char *hx="0123456789abcdef";
    for (int i=0;i<32;i++){ out[i*2]=hx[h[i]>>4]; out[i*2+1]=hx[h[i]&0xf]; }
    out[64]='\0';
}

/* ------------------------------------------------------------------ */
/* JSON string escaping for the captured-marker payload.              */
/* ------------------------------------------------------------------ */
static void json_print_escaped(FILE *out, const char *s, size_t n) {
    for (size_t i=0;i<n;i++) {
        unsigned char ch=(unsigned char)s[i];
        switch(ch) {
            case '"':  fputs("\\\"",out); break;
            case '\\': fputs("\\\\",out); break;
            case '\n': fputs("\\n",out);  break;
            case '\r': fputs("\\r",out);  break;
            case '\t': fputs("\\t",out);  break;
            default:
                if (ch < 0x20) fprintf(out,"\\u%04x",ch);
                else fputc(ch,out);
        }
    }
}

/* Keep only structural diagnostic lines from the captured decoder stdout.
 * Drops GRID dumps, FP_RGB dumps, palette colour values, and anything that
 * could echo image/payload content. Whitelist = the fail/stage/result markers
 * the field traces grep for, PLUS the decoder's own ungated failure strings.
 *
 * "JABCode Error:" is the universal prefix for both JAB_REPORT_ERROR and
 * reportError (encoder.c:2442). Those strings name the stage that failed
 * ("Too few finder pattern found", "Invalid module color ...", "LDPC decoding
 * for master metadata part 2 failed", "Decoding data failed", etc.) and NEVER
 * echo payload bytes — verified by inspecting every JAB_REPORT_ERROR /
 * reportError call site in detector.c, decoder.c, ldpc.c, image.c. They are
 * the primary fail-stage signal when status==0/1 (the verbose PartI/PartII
 * markers only fire once a finder pattern is found). */
static int is_marker_line(const char *line) {
    return  strstr(line,"JABCode Error:")         ||  /* reportError / JAB_REPORT_ERROR */
            strstr(line,"FAIL_STAGE")             ||
            strstr(line,"FAIL_ATTR")              ||
            strstr(line,"DECODE_OK")              ||
            strstr(line,"DIAG_SYMBOL_DECODE")     ||
            strstr(line,"DIAG_PARTII_RESULT")     ||
            strstr(line,"DIAG_MODE0")             ||
            strstr(line,"[PartI_DIAG]")           ||
            strstr(line,"[PartII_DIAG]")          ||
            strstr(line,"DIAG detectMaster")      ||
            strstr(line,"Nc_FALLBACK")            ||
            strstr(line,"Nc_PIN");
}

int main(int argc, char **argv) {
    if (argc < 2) {
        fprintf(stderr,"usage: %s <image.png>\n",argv[0]);
        return 2;
    }
    const char *img_path = argv[1];

    /* Load image first (before redirecting stdout) so readImage errors are
     * visible on the real stderr. */
    jab_bitmap *bitmap = readImage((jab_char*)img_path);
    if (!bitmap) {
        fprintf(stderr,"r0_decode: readImage failed for %s\n",img_path);
        return 3;
    }

    /* --- redirect fd 1 (stdout) into a temp file to capture diag markers --- */
    int saved_stdout = dup(1);
    if (saved_stdout < 0) { fprintf(stderr,"dup failed\n"); free(bitmap); return 4; }

    char tmpl[] = "/tmp/r0_diag_XXXXXX";
    int tmpfd = mkstemp(tmpl);
    if (tmpfd < 0) { fprintf(stderr,"mkstemp failed\n"); free(bitmap); return 4; }
    fflush(stdout);
    dup2(tmpfd, 1);

    /* --- decode: mirror the field bridge (strict PartII + verbose diag) --- */
    jabSetDiagVerbose(1);
    jabSetStrictPartIIRequired(1);

    jab_int32 status = -1;
    jab_decoded_symbol symbols[MAX_SYMBOL_NUMBER];

    struct timespec t0, t1;
    clock_gettime(CLOCK_MONOTONIC, &t0);
    jab_data *result = decodeJABCodeEx(bitmap, NORMAL_DECODE, &status,
                                       symbols, MAX_SYMBOL_NUMBER);
    clock_gettime(CLOCK_MONOTONIC, &t1);

    jabSetStrictPartIIRequired(0);
    jabSetDiagVerbose(0);

    double decode_ms = (t1.tv_sec - t0.tv_sec) * 1000.0 +
                       (t1.tv_nsec - t0.tv_nsec) / 1.0e6;

    /* --- restore real stdout, slurp the captured marker stream --- */
    fflush(stdout);
    dup2(saved_stdout, 1);
    close(saved_stdout);

    off_t cap_len = lseek(tmpfd, 0, SEEK_END);
    lseek(tmpfd, 0, SEEK_SET);
    char *cap = NULL;
    if (cap_len > 0) {
        cap = (char*)malloc((size_t)cap_len + 1);
        if (cap) {
            ssize_t rd = read(tmpfd, cap, (size_t)cap_len);
            if (rd < 0) rd = 0;
            cap[rd] = '\0';
        }
    }
    close(tmpfd);
    unlink(tmpl);

    /* --- decode result fields --- */
    int decode_ok = (result != NULL) ? 1 : 0;
    int nc = -1;
    int color_count = -1;
    char payload_sha[65] = "";
    long payload_len = -1;

    if (result) {
        nc = (int)symbols[0].metadata.Nc;
        color_count = 1 << (nc + 1);
        payload_len = (long)result->length;
        sha256_hex((const uint8_t*)result->data, (size_t)result->length, payload_sha);
    }

    /* --- emit JSON (one line) --- */
    /* status semantics (detector.c): 0=not detectable, 1=not decodable,
     * 2=partly decoded (COMPATIBLE only), 3=fully decoded. */
    printf("{");
    printf("\"decode_ok\":%d,", decode_ok);
    printf("\"status\":%d,", status);
    printf("\"nc\":%d,", nc);
    printf("\"color_count\":%d,", color_count);
    printf("\"decode_ms\":%.3f,", decode_ms);
    printf("\"payload_len\":%ld,", payload_len);
    printf("\"payload_sha256\":\"%s\",", payload_sha);

    /* filtered markers array */
    printf("\"markers\":[");
    int first = 1;
    if (cap) {
        char *save = NULL;
        for (char *line = strtok_r(cap, "\n", &save);
             line != NULL;
             line = strtok_r(NULL, "\n", &save)) {
            if (!is_marker_line(line)) continue;
            if (!first) printf(",");
            first = 0;
            printf("\"");
            json_print_escaped(stdout, line, strlen(line));
            printf("\"");
        }
    }
    printf("]");
    printf("}\n");

    free(cap);
    free(bitmap);
    if (result) free(result);
    return 0;
}
