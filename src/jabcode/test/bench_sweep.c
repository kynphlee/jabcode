/*
 * bench_sweep.c -- comprehensive JABCode codec sweep for the "full picture"
 * benchmark suite. Real host (x86_64) measurements across colour modes and ECC
 * levels. Emits one JSON object per line (JSONL) to stdout:
 *
 *   sweep=capacity   Nc x ECC -> max single-symbol bytes (text fixture vs binary)
 *   sweep=latency    Nc x payload-size (ecc=3) -> encode/decode median ms
 *   sweep=ecc        8-colour x ECC 1..10 -> latency + capacity
 *   sweep=wikipedia  Nc -> how much of the article fits at ecc=3
 *
 * Usage: bench_sweep <text-fixture-path>
 */
#define _POSIX_C_SOURCE 199309L
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include "jabcode.h"

#if defined(__aarch64__)
#  define PLATFORM "arm64"
#elif defined(__x86_64__)
#  define PLATFORM "x86_64"
#else
#  define PLATFORM "unknown"
#endif

#define ITER 15
#define WARM 3

static double now_ms(void){ struct timespec ts; clock_gettime(CLOCK_MONOTONIC,&ts); return ts.tv_sec*1000.0+ts.tv_nsec/1e6; }

static double med(double *v,int n){
    for(int i=0;i<n;i++) for(int j=i+1;j<n;j++) if(v[j]<v[i]){ double t=v[i]; v[i]=v[j]; v[j]=t; }
    return (n&1) ? v[n/2] : (v[n/2-1]+v[n/2])/2.0;
}

static char *load_file(const char *p,int *len){
    FILE *f=fopen(p,"rb"); if(!f){ *len=0; return NULL; }
    fseek(f,0,SEEK_END); long n=ftell(f); fseek(f,0,SEEK_SET);
    char *b=(char*)malloc(n+1); if(b && fread(b,1,(size_t)n,f)==(size_t)n) b[n]='\0';
    fclose(f); *len=(int)n; return b;
}

/* encode nbytes of payload at (color,ecc); return symbol side modules if it fit, else -1 */
static int try_fit(int color,int ecc,const char *payload,int nbytes,int *side){
    jab_encode *e=createEncode(color,1); if(!e) return -1;
    if(e->symbol_ecc_levels) e->symbol_ecc_levels[0]=(jab_byte)ecc;
    jab_data *d=(jab_data*)malloc(sizeof(jab_data)+nbytes);
    d->length=nbytes; memcpy(d->data,payload,nbytes);
    generateJABCode(e,d);
    int fit = (e->bitmap)?1:0;
    if(fit && side) *side=e->symbols[0].side_size.x;
    free(d); destroyEncode(e);
    return fit?1:0;
}

/* largest payload (<= len) that fits one symbol at (color,ecc); monotonic -> binary search */
static int max_cap(int color,int ecc,const char *payload,int len,int *side){
    int lo=1,hi=len,best=0,bs=0,s;
    while(lo<=hi){ int mid=(lo+hi)/2; s=0;
        if(try_fit(color,ecc,payload,mid,&s)){ best=mid; bs=s; lo=mid+1; } else hi=mid-1; }
    if(side)*side=bs; return best;
}

/* median encode ms at (color,ecc,nb) */
static double enc_ms(int color,int ecc,const char *payload,int nb){
    double t[ITER];
    for(int i=0;i<WARM+ITER;i++){
        jab_encode *e=createEncode(color,1); if(e&&e->symbol_ecc_levels) e->symbol_ecc_levels[0]=(jab_byte)ecc;
        jab_data *d=(jab_data*)malloc(sizeof(jab_data)+nb); d->length=nb; memcpy(d->data,payload,nb);
        double a=now_ms(); generateJABCode(e,d); double b=now_ms();
        if(i>=WARM) t[i-WARM]=b-a;
        free(d); destroyEncode(e);
    }
    return med(t,ITER);
}

/* median decode ms (and ok) at (color,ecc,nb) on a pristine bitmap */
static double dec_ms(int color,int ecc,const char *payload,int nb,int *ok){
    *ok=0;
    jab_encode *e=createEncode(color,1); if(e&&e->symbol_ecc_levels) e->symbol_ecc_levels[0]=(jab_byte)ecc;
    jab_data *d=(jab_data*)malloc(sizeof(jab_data)+nb); d->length=nb; memcpy(d->data,payload,nb);
    generateJABCode(e,d);
    double r_ms=0;
    if(e->bitmap){
        double t[ITER];
        for(int i=0;i<WARM+ITER;i++){
            jab_int32 st=0; double a=now_ms(); jab_data *r=decodeJABCode(e->bitmap,NORMAL_DECODE,&st); double b=now_ms();
            if(i>=WARM) t[i-WARM]=b-a;
            if(r){ *ok=1; free(r); }
        }
        r_ms=med(t,ITER);
    }
    free(d); destroyEncode(e);
    return r_ms;
}

int main(int argc,char **argv){
    const char *fixture = argc>1?argv[1]:NULL;
    int tlen=0; char *text = fixture?load_file(fixture,&tlen):NULL;
    int blen=20000; char *bin=(char*)malloc(blen);
    for(int i=0;i<blen;i++) bin[i]=(char)((i*131+17)&0xFF);
    /* repeated article so text capacity isn't capped by the fixture size */
    int btlen = (tlen>0) ? ((20000/tlen)+1)*tlen : 0;
    char *bigtext = (btlen>0) ? (char*)malloc(btlen) : NULL;
    if(bigtext) for(int o=0;o<btlen;o+=tlen) memcpy(bigtext+o,text,tlen);

    const int colors[]={2,4,8,16,32,64,128,256};

    /* 1. capacity heatmap: Nc x ECC, text + binary */
    for(int m=0;m<8;m++){ int c=colors[m];
        for(int ecc=1;ecc<=10;ecc++){
            int sB=0,sT=0;
            int capB=max_cap(c,ecc,bin,blen,&sB);
            int capT=bigtext?max_cap(c,ecc,bigtext,btlen,&sT):0;
            printf("{\"sweep\":\"capacity\",\"platform\":\"%s\",\"colors\":%d,\"ecc\":%d,"
                   "\"cap_binary\":%d,\"cap_text\":%d,\"side_binary\":%d,\"side_text\":%d}\n",
                   PLATFORM,c,ecc,capB,capT,sB,sT);
        }
    }

    /* 2. latency vs payload size: Nc x sizes, ecc=3 */
    const int sizes[]={16,64,256,1024};
    for(int m=0;m<8;m++){ int c=colors[m];
        for(int s=0;s<4;s++){ int nb=sizes[s]; if(nb>blen) continue;
            int dok=0;
            double e=enc_ms(c,3,bin,nb);
            double d=dec_ms(c,3,bin,nb,&dok);
            printf("{\"sweep\":\"latency\",\"platform\":\"%s\",\"colors\":%d,\"ecc\":3,\"bytes\":%d,"
                   "\"encode_ms\":%.4f,\"decode_ms\":%.4f,\"dec_ok\":%d}\n",
                   PLATFORM,c,nb,e,d,dok);
        }
    }

    /* 3. ECC effect: 8-colour, ecc 1..10, fixed 256 B */
    for(int ecc=1;ecc<=10;ecc++){
        int dok=0,sB=0;
        double e=enc_ms(8,ecc,bin,256);
        double d=dec_ms(8,ecc,bin,256,&dok);
        int capB=max_cap(8,ecc,bin,blen,&sB);
        printf("{\"sweep\":\"ecc\",\"platform\":\"%s\",\"colors\":8,\"ecc\":%d,\"bytes\":256,"
               "\"encode_ms\":%.4f,\"decode_ms\":%.4f,\"cap_binary\":%d,\"dec_ok\":%d}\n",
               PLATFORM,ecc,e,d,capB,dok);
    }

    /* 4. wikipedia text capacity per Nc at ecc=3 */
    if(text){
        for(int m=0;m<8;m++){ int c=colors[m]; int sT=0,sB=0;
            int capT=max_cap(c,3,text,tlen,&sT);
            int capB=max_cap(c,3,bin,blen,&sB);
            printf("{\"sweep\":\"wikipedia\",\"platform\":\"%s\",\"colors\":%d,\"ecc\":3,"
                   "\"article_bytes\":%d,\"cap_text\":%d,\"cap_binary\":%d,\"pct_article\":%.2f,\"side_text\":%d}\n",
                   PLATFORM,c,tlen,capT,capB, tlen>0?100.0*capT/tlen:0.0, sT);
        }
    }

    free(bin); if(text) free(text); if(bigtext) free(bigtext);
    return 0;
}
