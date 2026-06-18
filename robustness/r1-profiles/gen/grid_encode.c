/*
 * grid_encode -- R1 per-medium profile symbol generator.
 *
 * Encodes ONE fixed payload across a focused (Nc x ECC) grid using the
 * src/jabcode encoder (the Fraunhofer reference path), at a fixed module size
 * so every symbol shares the corpus's 12 px/module convention (matches
 * robustness/r0/synthetic/degrade.py SOURCE_PX_PER_MODULE). For each (Nc, ECC)
 * cell it:
 *
 *   - sets color_number = 2^(Nc+1) and symbol_ecc_levels[0] = ECC,
 *   - lets the encoder auto-pick the SMALLEST square symbol version that fits
 *     (payload + ECC) -- so the density cost of lower-Nc / higher-ECC shows up
 *     directly as a larger side_size, exactly the tradeoff we quantify,
 *   - renders to a lossless PNG named  nc<N>-ecc<E>-<tag>.png  (the degrade.py
 *     filename regex parses Nc from the nc<N> prefix; the rig manifest builder
 *     recovers ECC from the ecc<E> field),
 *   - emits one JSONL record on stdout describing the cell.
 *
 * ISO-conformance: Nc and ECC are passed straight to the reference encoder,
 * which selects the standardized Table-21 palette for the colour count and the
 * Annex-legal LDPC (wc,wr) for the ECC level. No palette is overridden. The
 * grid only chooses among legal (Nc, ECC) values; the encoded symbol IS a
 * reference encode.
 *
 * Output (stdout, one JSON object per line):
 *   {"nc":N,"colors":C,"ecc":E,"side":S,"px_per_module":M,"image_px":P,
 *    "fit":0|1,"payload_len":L,"payload_sha256":"<hex>","file":"nc..ecc...png"}
 * fit=0 means the payload did not fit one symbol at that (Nc,ECC) -- recorded,
 * no PNG written. The SHA-256 is of the *encoded payload bytes* (identical for
 * every cell since the payload is fixed) so the R0 rig can verify decodes.
 *
 * Two geometry modes:
 *   - AUTO  (fixed_version=0, the default): the encoder picks the smallest
 *     square version per cell. side_size then VARIES across the grid and IS the
 *     density datum -- the price a robustness choice costs in symbol area.
 *   - PINNED (fixed_version=V>0): every cell is forced to side version V (same
 *     physical raster), so degradations act identically per module and the only
 *     variables are colour-count + ECC redundancy -- the apples-to-apples
 *     robustness comparison. Cells whose payload+ECC won't fit V are fit=0.
 *
 * Usage: grid_encode <out-dir> <payload-file> [module_size=12] [fixed_version=0] [tag=p0]
 *   tag  -- a short payload id woven into each output filename
 *           (nc<N>-ecc<E>-<colors>c-<tag>.png) so a caller sweeping several
 *           payloads per cell -- the way decode-rate is averaged out of single-
 *           image Bernoulli noise -- can aggregate them in one directory without
 *           filename collisions. The degrade.py Nc regex still matches the nc<N>
 *           prefix; the rig manifest builder recovers ecc + tag from the rest.
 *
 * SECURITY: prints only the payload length + SHA-256 digest, never the bytes.
 */
#define _XOPEN_SOURCE 700
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>

#include "jabcode.h"

/* --- minimal SHA-256 (public-domain style), same impl as the rig probe ----- */
typedef struct { uint32_t state[8]; uint64_t bitlen; uint8_t data[64]; uint32_t datalen; } sha256_ctx;
static const uint32_t K256[64] = {
    0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
    0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
    0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
    0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
    0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
    0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
    0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
    0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2 };
#define ROTR(x,n) (((x) >> (n)) | ((x) << (32-(n))))
static void sha256_transform(sha256_ctx *c, const uint8_t *d) {
    uint32_t m[64], a,b,e,f,g,h,t1,t2,cc,dd;
    for (int i=0,j=0;i<16;i++,j+=4) m[i]=(d[j]<<24)|(d[j+1]<<16)|(d[j+2]<<8)|d[j+3];
    for (int i=16;i<64;i++){ uint32_t s0=ROTR(m[i-15],7)^ROTR(m[i-15],18)^(m[i-15]>>3);
        uint32_t s1=ROTR(m[i-2],17)^ROTR(m[i-2],19)^(m[i-2]>>10); m[i]=m[i-16]+s0+m[i-7]+s1; }
    a=c->state[0];b=c->state[1];cc=c->state[2];dd=c->state[3];
    e=c->state[4];f=c->state[5];g=c->state[6];h=c->state[7];
    for (int i=0;i<64;i++){ uint32_t S1=ROTR(e,6)^ROTR(e,11)^ROTR(e,25); uint32_t ch=(e&f)^((~e)&g);
        t1=h+S1+ch+K256[i]+m[i]; uint32_t S0=ROTR(a,2)^ROTR(a,13)^ROTR(a,22); uint32_t maj=(a&b)^(a&cc)^(b&cc);
        t2=S0+maj; h=g;g=f;f=e;e=dd+t1;dd=cc;cc=b;b=a;a=t1+t2; }
    c->state[0]+=a;c->state[1]+=b;c->state[2]+=cc;c->state[3]+=dd;
    c->state[4]+=e;c->state[5]+=f;c->state[6]+=g;c->state[7]+=h;
}
static void sha256_init(sha256_ctx *c){ c->datalen=0;c->bitlen=0;
    c->state[0]=0x6a09e667;c->state[1]=0xbb67ae85;c->state[2]=0x3c6ef372;c->state[3]=0xa54ff53a;
    c->state[4]=0x510e527f;c->state[5]=0x9b05688c;c->state[6]=0x1f83d9ab;c->state[7]=0x5be0cd19; }
static void sha256_update(sha256_ctx *c,const uint8_t *d,size_t len){ for(size_t i=0;i<len;i++){
    c->data[c->datalen++]=d[i]; if(c->datalen==64){ sha256_transform(c,c->data); c->bitlen+=512; c->datalen=0; } } }
static void sha256_final(sha256_ctx *c,uint8_t *hash){ uint32_t i=c->datalen;
    if(c->datalen<56){ c->data[i++]=0x80; while(i<56) c->data[i++]=0x00; }
    else { c->data[i++]=0x80; while(i<64) c->data[i++]=0x00; sha256_transform(c,c->data); memset(c->data,0,56); }
    c->bitlen+=(uint64_t)c->datalen*8;
    for(int k=7;k>=0;k--) c->data[56+(7-k)]=(uint8_t)(c->bitlen>>(k*8));
    sha256_transform(c,c->data);
    for(i=0;i<4;i++) for(int j=0;j<8;j++) hash[i+j*4]=(uint8_t)((c->state[j]>>(24-i*8))&0xff); }
static void sha256_hex(const uint8_t *data,size_t len,char out[65]){ sha256_ctx c; uint8_t h[32];
    sha256_init(&c); sha256_update(&c,data,len); sha256_final(&c,h);
    static const char *hx="0123456789abcdef";
    for(int i=0;i<32;i++){ out[i*2]=hx[h[i]>>4]; out[i*2+1]=hx[h[i]&0xf]; } out[64]='\0'; }

/* --- the focused (Nc x ECC) grid ------------------------------------------ */
/* Nc in {1,2,3,5,7} -> colours {4,8,16,64,256}: spans the robust-sparse end
 * (Nc1/2) through mid (Nc3) to the dense-fragile end (Nc5/7). Nc0 (2c) is the
 * monochrome special case with no colour separation to study; Nc4/Nc6 are
 * redundant interior points the 5 chosen levels already bracket.
 * ECC in {3,5,8}: 3 = reference default (~6%), 5 = moderate, 8 = heavy; spans
 * the correction axis without the diminishing-returns extremes (1-2, 9-10). */
static const int NC_GRID[]  = {1, 2, 3, 5, 7};
static const int ECC_GRID[] = {3, 5, 8};

static char *load_file(const char *p, long *len) {
    FILE *f=fopen(p,"rb"); if(!f){ *len=0; return NULL; }
    fseek(f,0,SEEK_END); long n=ftell(f); fseek(f,0,SEEK_SET);
    char *b=(char*)malloc((size_t)n+1);
    if(b && fread(b,1,(size_t)n,f)==(size_t)n) b[n]='\0'; else { free(b); fclose(f); *len=0; return NULL; }
    fclose(f); *len=n; return b;
}

int main(int argc, char **argv) {
    if (argc < 3) { fprintf(stderr,"usage: %s <out-dir> <payload-file> [module_size=12] [fixed_version=0]\n",argv[0]); return 2; }
    const char *outdir = argv[1];
    const char *payfile = argv[2];
    int module_size = (argc>3) ? atoi(argv[3]) : 12;
    if (module_size <= 0) module_size = 12;
    int fixed_version = (argc>4) ? atoi(argv[4]) : 0;   /* 0 = auto-size per cell */
    if (fixed_version < 0 || fixed_version > 32) fixed_version = 0;
    const char *tag = (argc>5) ? argv[5] : "p0";        /* payload id for filenames */

    long plen=0; char *payload = load_file(payfile,&plen);
    if (!payload || plen<=0) { fprintf(stderr,"grid_encode: cannot read payload %s\n",payfile); return 3; }

    char paysha[65]; sha256_hex((const uint8_t*)payload,(size_t)plen,paysha);

    for (size_t ni=0; ni<sizeof(NC_GRID)/sizeof(NC_GRID[0]); ni++) {
        int nc = NC_GRID[ni];
        int colors = 1 << (nc + 1);
        for (size_t ei=0; ei<sizeof(ECC_GRID)/sizeof(ECC_GRID[0]); ei++) {
            int ecc = ECC_GRID[ei];

            char fname[256];
            snprintf(fname,sizeof(fname),"nc%d-ecc%d-%dc-%s.png", nc, ecc, colors, tag);

            jab_encode *enc = createEncode(colors, 1);
            if (!enc) { fprintf(stderr,"createEncode failed nc=%d ecc=%d\n",nc,ecc); continue; }
            enc->module_size = module_size;
            if (enc->symbol_ecc_levels) enc->symbol_ecc_levels[0] = (jab_byte)ecc;
            /* PINNED mode: force a common side version so the whole grid shares
             * one physical raster. Leaving it 0 lets the encoder auto-size. */
            if (fixed_version > 0 && enc->symbol_versions) {
                enc->symbol_versions[0].x = fixed_version;
                enc->symbol_versions[0].y = fixed_version;
            }

            jab_data *d = (jab_data*)malloc(sizeof(jab_data)+(size_t)plen);
            d->length = (jab_int32)plen; memcpy(d->data,payload,(size_t)plen);

            int rc = generateJABCode(enc, d);   /* 0 = success */
            int fit = (rc==0 && enc->bitmap) ? 1 : 0;
            int side = fit ? enc->symbols[0].side_size.x : -1;
            int image_px = fit ? enc->bitmap->width : -1;

            if (fit) {
                char path[512];
                snprintf(path,sizeof(path),"%s/%s",outdir,fname);
                if (!saveImage(enc->bitmap,(jab_char*)path)) {
                    fprintf(stderr,"saveImage failed for %s\n",path);
                    fit = 0;
                }
            }

            printf("{\"nc\":%d,\"colors\":%d,\"ecc\":%d,\"side\":%d,"
                   "\"fixed_version\":%d,\"px_per_module\":%d,\"image_px\":%d,\"fit\":%d,"
                   "\"payload_len\":%ld,\"payload_sha256\":\"%s\",\"file\":\"%s\"}\n",
                   nc, colors, ecc, side, fixed_version, module_size, image_px, fit,
                   plen, paysha, fname);

            free(d);
            destroyEncode(enc);
        }
    }

    free(payload);
    return 0;
}
