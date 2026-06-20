/**
 * libjabcode - JABCode Encoding/Decoding Library
 *
 * Copyright 2016 by Fraunhofer SIT. All rights reserved.
 * See LICENSE file for full terms of use and distribution.
 *
 * Contact: Huajian Liu <liu@sit.fraunhofer.de>
 *			Waldemar Berchtold <waldemar.berchtold@sit.fraunhofer.de>
 *
 * @file ldpc.c
 * @brief LDPC encoder and decoder
 */

#include <stdlib.h>
#include <math.h>
#include <stdint.h>
#include "jabcode.h"
#include "ldpc.h"
#include "pseudo_random.h"   /* WS-4.5.3: deterministic LCG replaces stdlib rand() */
#include <string.h>
#include <stdio.h>
#include "detector.h"
#include "pseudo_random.h"

/* ===================== LDPC decode hot-path primitives (performance) =====================
 * The hard-decision decoder (decodeLDPChd -> decodeMessage) spends almost all of its time
 * in one primitive: the GF(2) inner product of a packed parity-check row with the received
 * bit vector. The stock code walks this one *bit* at a time
 *     for (j=0..length)  temp ^= ((row[j/32] >> (31-j%32)) & 1) & (data[j] & 1);
 * which is ~length branch-laden iterations per check, repeated over every check row, every
 * syndrome test, and every bit-flip iteration. These helpers compute the identical value a
 * 32/256-bit *word* at a time via popcount, with an AVX2 path (host-dispatched) and a scalar
 * fallback. They are bit-exact: the matrix rows are stored MSB-first with zero padding beyond
 * `length` (calloc + valid-position fills; GaussJordan only XORs rows, never sets pad bits),
 * so if the received vector is packed the same way (pad bits zero) then
 *     parity(sum_i row_i&data_i) == ( sum_words popcount(row_word & data_word) ) & 1
 * over the full `offset = ceil(length/32)` words, with no tail special-case. Nothing here
 * changes a decoded bit; it only removes per-bit overhead from the existing arithmetic. */

/* Host AVX2 path is opt-out via -DJAB_LDPC_NO_SIMD (forces the scalar fallback everywhere;
 * used to prove the fallback is byte-identical, and a clean switch for builds that want it). */
#if !defined(JAB_LDPC_NO_SIMD) && defined(__GNUC__) && (defined(__x86_64__) || defined(__i386__))
#  define JAB_LDPC_X86 1
#  include <immintrin.h>
#endif

/* Pack `length` received symbols (each a 0/1-valued byte; only bit 0 is meaningful, exactly
 * as the stock `(data[i] >> 0) & 1`) into MSB-first 32-bit words, matching the parity-check
 * matrix's bit layout. Bits in the final word beyond `length` are left zero so packed[] and
 * each matrix row share identical zero padding. `packed` must hold ceil(length/32) words. */
static inline void ldpc_pack_bits(jab_uint32* packed, const jab_byte* data, jab_int32 length)
{
    jab_int32 offset = (length + 31) >> 5;
    memset(packed, 0, (size_t)offset * sizeof(jab_uint32));
    for (jab_int32 i = 0; i < length; i++)
        if (data[i] & 1)
            packed[i >> 5] |= (jab_uint32)1u << (31 - (i & 31));
}

/* Scalar GF(2) row-dot-parity: ( sum_w popcount(row[w] & vec[w]) ) & 1 over n_words. */
static inline jab_int32 ldpc_row_parity_scalar(const jab_uint32* row, const jab_uint32* vec, jab_int32 n_words)
{
    jab_uint32 acc = 0;
    for (jab_int32 w = 0; w < n_words; w++)
        acc += (jab_uint32)__builtin_popcount(row[w] & vec[w]);
    return (jab_int32)(acc & 1u);
}

#ifdef JAB_LDPC_X86
/* AVX2 GF(2) row-dot-parity. Only the parity (LSB of the popcount sum) is needed, so the
 * per-lane popcounts are reduced by XOR (parity is associative/commutative under XOR). We
 * popcount 64-bit lanes via the SWAR/byte-sum trick (no AVX-512 VPOPCNTQ dependency) and
 * fold to a single parity bit. Bit-identical to the scalar path. */
__attribute__((target("avx2")))
static jab_int32 ldpc_row_parity_avx2(const jab_uint32* row, const jab_uint32* vec, jab_int32 n_words)
{
    const __m256i m4  = _mm256_set1_epi8(0x0f);
    const __m256i lut = _mm256_setr_epi8(
        0,1,1,2,1,2,2,3,1,2,2,3,2,3,3,4, 0,1,1,2,1,2,2,3,1,2,2,3,2,3,3,4);
    const __m256i one = _mm256_set1_epi8(1);
    __m256i parity = _mm256_setzero_si256();   /* 32 byte-lanes, each a running parity bit */
    jab_int32 w = 0;
    for (; w + 8 <= n_words; w += 8)
    {
        __m256i r = _mm256_loadu_si256((const __m256i*)(row + w));
        __m256i v = _mm256_loadu_si256((const __m256i*)(vec + w));
        __m256i x = _mm256_and_si256(r, v);
        /* per-byte popcount (0..8) via a nibble LUT */
        __m256i lo = _mm256_and_si256(x, m4);
        __m256i hi = _mm256_and_si256(_mm256_srli_epi16(x, 4), m4);
        __m256i pc = _mm256_add_epi8(_mm256_shuffle_epi8(lut, lo),
                                     _mm256_shuffle_epi8(lut, hi));
        /* fold each byte popcount to its parity (LSB) and XOR into the accumulator */
        parity = _mm256_xor_si256(parity, _mm256_and_si256(pc, one));
    }
    /* horizontal XOR-reduce the 32 byte-lanes to one parity bit */
    __m128i p128 = _mm_xor_si128(_mm256_castsi256_si128(parity),
                                 _mm256_extracti128_si256(parity, 1));
    uint64_t a = (uint64_t)_mm_extract_epi64(p128, 0) ^ (uint64_t)_mm_extract_epi64(p128, 1);
    a ^= a >> 32; a ^= a >> 16; a ^= a >> 8;
    jab_int32 acc = (jab_int32)(a & 1u);
    /* scalar tail */
    for (; w < n_words; w++)
        acc ^= __builtin_popcount(row[w] & vec[w]) & 1;
    return acc & 1;
}

/* Host capability probe (AVX2). __builtin_cpu_supports relies on a
 * constructor-time __builtin_cpu_init (the GCC runtime runs it before main),
 * but the memoised result `v` is filled lazily on first call -- so under
 * concurrent decode two threads could race to write it. The probe is a pure
 * host-invariant function (every thread computes the same answer), so make the
 * cache _Thread_local: each thread memoises its own copy once, with no shared
 * write and no synchronization. Matches the #91 LDPC-cache _Thread_local idiom
 * used elsewhere in this file and keeps the dispatch decision byte-identical. */
static int ldpc_have_avx2(void)
{
    static _Thread_local int v = -1;
    if (v < 0) v = __builtin_cpu_supports("avx2") ? 1 : 0;
    return v;
}
#endif /* JAB_LDPC_X86 */

/* Dispatch wrapper: GF(2) parity of (row . vec) over n_words packed words. */
static inline jab_int32 ldpc_row_parity(const jab_uint32* row, const jab_uint32* vec, jab_int32 n_words)
{
#ifdef JAB_LDPC_X86
    if (n_words >= 8 && ldpc_have_avx2())
        return ldpc_row_parity_avx2(row, vec, n_words);
#endif
    return ldpc_row_parity_scalar(row, vec, n_words);
}

/* Full syndrome test: returns 1 iff every one of the first `rank` rows of `matrix`
 * (offset words/row) is parity-orthogonal to the packed received vector `vec`. Mirrors the
 * stock "first check syndrom" loop (early-exit on the first violated check). */
static jab_int32 ldpc_syndrome_ok(const jab_int32* matrix, const jab_uint32* vec,
                                  jab_int32 rank, jab_int32 offset)
{
    for (jab_int32 i = 0; i < rank; i++)
        if (ldpc_row_parity((const jab_uint32*)(matrix + (size_t)i * offset), vec, offset))
            return 0;
    return 1;
}

/* Convenience syndrome test over an unpacked 0/1-valued byte vector `data` of `length`
 * symbols: packs `data` once (offset = ceil(length/32) words) and runs ldpc_syndrome_ok.
 * Returns 1 == codeword satisfies all `rank` checks (== stock loop set is_correct stays 1),
 * 0 == at least one check violated, and -1 on allocation failure so the caller can fall back
 * to never claiming a bad codeword is correct. Self-contained buffer: no lifetime to manage
 * at the (many) call sites, and the alloc (<=~85 words) is dwarfed by the per-bit loop it
 * replaces. Bit-identical to the stock syndrome loop. */
static jab_int32 ldpc_syndrome_ok_bytes(const jab_int32* matrix, const jab_byte* data,
                                        jab_int32 rank, jab_int32 length)
{
    jab_int32 offset = (length + 31) >> 5;
    jab_uint32* packed = (jab_uint32*)malloc((size_t)offset * sizeof(jab_uint32));
    if (packed == NULL) return -1;
    ldpc_pack_bits(packed, data, length);
    jab_int32 ok = ldpc_syndrome_ok(matrix, packed, rank, offset);
    free(packed);
    return ok;
}

/**
 * @brief Create matrix A for message data
 * @param wc the number of '1's in a column
 * @param wr the number of '1's in a row
 * @param capacity the number of columns of the matrix
 * @return the matrix A | NULL if failed (out of memory)
*/
jab_int32 *createMatrixA(jab_int32 wc, jab_int32 wr, jab_int32 capacity)
{
    jab_int32 nb_pcb;
    if(wr<4)
        nb_pcb=capacity/2;
    else
        nb_pcb=capacity/wr*wc;

    jab_int32 effwidth=ceil(capacity/(jab_float)32)*32;
    jab_int32 offset=ceil(capacity/(jab_float)32);
    //create a matrix with '0' entries
    jab_int32 *matrixA=(jab_int32 *)calloc(ceil(capacity/(jab_float)32)*nb_pcb,sizeof(jab_int32));
    if(matrixA == NULL)
    {
        reportError("Memory allocation for matrix in LDPC failed");
        return NULL;
    }
    jab_int32* permutation=(jab_int32 *)calloc(capacity, sizeof(jab_int32));
    if(permutation == NULL)
    {
        reportError("Memory allocation for matrix in LDPC failed");
        free(matrixA);
        return NULL;
    }
    for (jab_int32 i=0;i<capacity;i++)
        permutation[i]=i;

    //Fill the first set with consecutive ones in each row
    for (jab_int32 i=0;i<capacity/wr;i++)
    {
        for (jab_int32 j=0;j<wr;j++)
            matrixA[(i*(effwidth+wr)+j)/32] |= 1 << (31 - ((i*(effwidth+wr)+j)%32));
    }
    //Permutate the columns and fill the remaining matrix
    //generate matrixA by following Gallagers algorithm
    setSeed(LPDC_MESSAGE_SEED);
    for (jab_int32 i=1; i<wc; i++)
    {
        jab_int32 off_index=i*(capacity/wr);
        for (jab_int32 j=0;j<capacity;j++)
        {
            jab_int32 pos = pn_index(lcg64_temper(), capacity - j);
            for (jab_int32 k=0;k<capacity/wr;k++)
                matrixA[(off_index+k)*offset+j/32] |= ((matrixA[(permutation[pos]/32+k*offset)] >> (31-permutation[pos]%32)) & 1) << (31-j%32);
            jab_int32  tmp = permutation[capacity - 1 -j];
            permutation[capacity - 1 - j] = permutation[pos];
            permutation[pos] = tmp;
        }
    }
    free(permutation);
    return matrixA;
}

/**
 * @brief Gauss Jordan elimination algorithm
 * @param matrixA the matrix
 * @param wc the number of '1's in a column
 * @param wr the number of '1's in a row
 * @param capacity the number of columns of the matrix
 * @param matrix_rank the rank of the matrix
 * @param encode specifies if function is called by the encoder or decoder
 * @return 0: success | 1: fatal error (out of memory)
*/
jab_int32 GaussJordan(jab_int32* matrixA, jab_int32 wc, jab_int32 wr, jab_int32 capacity, jab_int32* matrix_rank, jab_boolean encode)
{
    jab_int32 loop=0;
    jab_int32 nb_pcb;
    if(wr<4)
        nb_pcb=capacity/2;
    else
        nb_pcb=capacity/wr*wc;

    jab_int32 offset=ceil(capacity/(jab_float)32);

    jab_int32*matrixH=(jab_int32 *)calloc(offset*nb_pcb,sizeof(jab_int32));
    if(matrixH == NULL)
    {
        reportError("Memory allocation for matrix in LDPC failed");
        return 1;
    }
    memcpy(matrixH,matrixA,offset*nb_pcb*sizeof(jab_int32));

    jab_int32* column_arrangement=(jab_int32 *)calloc(capacity, sizeof(jab_int32));
    if(column_arrangement == NULL)
    {
        reportError("Memory allocation for matrix in LDPC failed");
        free(matrixH);
        return 1;
    }
    jab_boolean* processed_column=(jab_boolean *)calloc(capacity, sizeof(jab_boolean));
    if(processed_column == NULL)
    {
        reportError("Memory allocation for matrix in LDPC failed");
        free(matrixH);
        free(column_arrangement);
        return 1;
    }
    jab_int32* zero_lines_nb=(jab_int32 *)calloc(nb_pcb, sizeof(jab_int32));
    if(zero_lines_nb == NULL)
    {
        reportError("Memory allocation for matrix in LDPC failed");
        free(matrixH);
        free(column_arrangement);
        free(processed_column);
        return 1;
    }
    jab_int32* swap_col=(jab_int32 *)calloc(2*capacity, sizeof(jab_int32));
    if(swap_col == NULL)
    {
        reportError("Memory allocation for matrix in LDPC failed");
        free(matrixH);
        free(column_arrangement);
        free(processed_column);
        free(zero_lines_nb);
        return 1;
    }

    jab_int32 zero_lines=0;

    for (jab_int32 i=0; i<nb_pcb; i++)
    {
        jab_int32 pivot_column=capacity+1;
        for (jab_int32 j=0; j<capacity; j++)
        {
            if((matrixH[(offset*32*i+j)/32] >> (31-(offset*32*i+j)%32)) & 1)
            {
                pivot_column=j;
                break;
            }
        }
        if(pivot_column < capacity)
        {
            processed_column[pivot_column]=1;
            column_arrangement[pivot_column]=i;
            if (pivot_column>=nb_pcb)
            {
                swap_col[2*loop]=pivot_column;
                loop++;
            }

            jab_int32 off_index=pivot_column/32;
            jab_int32 off_index1=pivot_column%32;
            for (jab_int32 j=0; j<nb_pcb; j++)
            {
                if (((matrixH[off_index+j*offset] >> (31-off_index1)) & 1) && j != i)
                {
                    //subtract pivot row GF(2)
                    for (jab_int32 k=0;k<offset;k++)
                        matrixH[k+offset*j] ^= matrixH[k+offset*i];
                }
            }
        }
        else //zero line
        {
            zero_lines_nb[zero_lines]=i;
            zero_lines++;
        }
    }

    *matrix_rank=nb_pcb-zero_lines;
    jab_int32 loop2=0;
    for(jab_int32 i=*matrix_rank;i<nb_pcb;i++)
    {
        if(column_arrangement[i] > 0)
        {
            for (jab_int32 j=0;j < nb_pcb;j++)
            {
                if (processed_column[j] == 0)
                {
                    column_arrangement[j]=column_arrangement[i];
                    column_arrangement[i]=0;
                    processed_column[j]=1;
                    processed_column[i]=0;
                    swap_col[2*loop]=i;
                    swap_col[2*loop+1]=j;
                    column_arrangement[i]=j;
                    loop++;
                    loop2++;
                    break;
                }
            }
        }
    }

    jab_int32 loop1=0;
    for (jab_int32 kl=0;kl< nb_pcb;kl++)
    {
        if(processed_column[kl] == 0 && loop1 < loop-loop2)
        {
            column_arrangement[kl]=column_arrangement[swap_col[2*loop1]];
            processed_column[kl]=1;
            swap_col[2*loop1+1]=kl;
            loop1++;
        }
    }

    loop1=0;
    for (jab_int32 kl=0;kl< nb_pcb;kl++)
    {
        if(processed_column[kl]==0)
        {
            column_arrangement[kl]=zero_lines_nb[loop1];
            loop1++;
        }
    }
    //rearrange matrixH if encoder and store it in matrixA
    //rearrange matrixA if decoder
    if(encode)
    {
        for(jab_int32 i=0;i< nb_pcb;i++)
            memcpy(matrixA+i*offset,matrixH+column_arrangement[i]*offset,offset*sizeof(jab_int32));

        //swap columns
        jab_int32 tmp=0;
        for(jab_int32 i=0;i<loop;i++)
        {
            for (jab_int32 j=0;j<nb_pcb;j++)
            {
                tmp ^= (-((matrixA[swap_col[2*i]/32+j*offset] >> (31-swap_col[2*i]%32)) & 1) ^ tmp) & (1 << 0);
                matrixA[swap_col[2*i]/32+j*offset]   ^= (-((matrixA[swap_col[2*i+1]/32+j*offset] >> (31-swap_col[2*i+1]%32)) & 1) ^ matrixA[swap_col[2*i]/32+j*offset]) & (1 << (31-swap_col[2*i]%32));
                matrixA[swap_col[2*i+1]/32+offset*j] ^= (-((tmp >> 0) & 1) ^ matrixA[swap_col[2*i+1]/32+offset*j]) & (1 << (31-swap_col[2*i+1]%32));
            }
        }
    }
    else
    {
    //    memcpy(matrixH,matrixA,offset*nb_pcb*sizeof(jab_int32));
        for(jab_int32 i=0;i< nb_pcb;i++)
            memcpy(matrixH+i*offset,matrixA+column_arrangement[i]*offset,offset*sizeof(jab_int32));

        //swap columns
        jab_int32 tmp=0;
        for(jab_int32 i=0;i<loop;i++)
        {
            for (jab_int32 j=0;j<nb_pcb;j++)
            {
                tmp ^= (-((matrixH[swap_col[2*i]/32+j*offset] >> (31-swap_col[2*i]%32)) & 1) ^ tmp) & (1 << 0);
                matrixH[swap_col[2*i]/32+j*offset]   ^= (-((matrixH[swap_col[2*i+1]/32+j*offset] >> (31-swap_col[2*i+1]%32)) & 1) ^ matrixH[swap_col[2*i]/32+j*offset]) & (1 << (31-swap_col[2*i]%32));
                matrixH[swap_col[2*i+1]/32+offset*j] ^= (-((tmp >> 0) & 1) ^ matrixH[swap_col[2*i+1]/32+offset*j]) & (1 << (31-swap_col[2*i+1]%32));
            }
        }
        memcpy(matrixA,matrixH,offset*nb_pcb*sizeof(jab_int32));
    }

    free(column_arrangement);
    free(processed_column);
    free(zero_lines_nb);
    free(swap_col);
    free(matrixH);
    return 0;
}

/**
 * @brief Create the error correction matrix for the metadata
 * @param wc the number of '1's in a column
 * @param capacity the number of columns of the matrix
 * @return the error correction matrix | NULL if failed
*/
jab_int32 *createMetadataMatrixA(jab_int32 wc, jab_int32 capacity)
{
    jab_int32 nb_pcb=capacity/2;
    jab_int32 offset=ceil(capacity/(jab_float)32);
    //create a matrix with '0' entries
    jab_int32*matrixA=(jab_int32 *)calloc(offset*nb_pcb,sizeof(jab_int32));
    if(matrixA == NULL)
    {
        reportError("Memory allocation for matrix in LDPC failed");
        return NULL;
    }
    jab_int32* permutation=(jab_int32 *)calloc(capacity, sizeof(jab_int32));
    if(permutation == NULL)
    {
        reportError("Memory allocation for matrix in LDPC failed");
        free(matrixA);
        return NULL;
    }
    for (jab_int32 i=0;i<capacity;i++)
        permutation[i]=i;
    setSeed(LPDC_METADATA_SEED);
    jab_int32 nb_once=capacity*nb_pcb/(jab_float)wc+3;
    nb_once=nb_once/nb_pcb;
    //Fill matrix randomly
    for (jab_int32 i=0;i<nb_pcb;i++)
    {
        for (jab_int32 j=0; j< nb_once; j++)
        {
            jab_int32 pos = pn_index(lcg64_temper(), capacity-j);
            matrixA[i*offset+permutation[pos]/32] |= 1 << (31-permutation[pos]%32);
            jab_int32  tmp = permutation[capacity - 1 -j];
            permutation[capacity - 1 -j] = permutation[pos];
            permutation[pos] = tmp;
        }
    }
    free(permutation);
    return matrixA;
}

/**
 * @brief Create the generator matrix to encode messages
 * @param matrixA the error correction matrix
 * @param capacity the number of columns of the matrix
 * @param Pn the number of net message bits
 * @return the generator matrix | NULL if failed (out of memory)
*/
jab_int32 *createGeneratorMatrix(jab_int32* matrixA, jab_int32 capacity, jab_int32 Pn)
{
    jab_int32 effwidth=ceil(Pn/(jab_float)32)*32;
    jab_int32 offset=ceil(Pn/(jab_float)32);
    jab_int32 offset_cap=ceil(capacity/(jab_float)32);
    //create G [I C]
    //remember matrixA is now A = [I CT], now use it and create G=[CT
                //                                                 I ]
    jab_int32* G=(jab_int32 *)calloc(offset*capacity, sizeof(jab_int32));
    if(G == NULL)
    {
        reportError("Memory allocation for matrix in LDPC failed");
        return NULL;
    }

    //fill identity matrix
    for (jab_int32 i=0; i< Pn; i++)
        G[(capacity-Pn+i) * offset+i/32] |= 1 << (31-i%32);

    //copy CT matrix from A to G
    jab_int32 matrix_index=capacity-Pn;
    jab_int32 loop=0;

    for (jab_int32 i=0; i<(capacity-Pn)*effwidth; i++)
    {
        if(matrix_index >= capacity)
        {
            loop++;
            matrix_index=capacity-Pn;
        }
        if(i % effwidth < Pn)
        {
            G[i/32] ^= (-((matrixA[matrix_index/32+offset_cap*loop] >> (31-matrix_index%32)) & 1) ^ G[i/32]) & (1 << (31-i%32));
            matrix_index++;
        }
    }
    return G;
}

/* ===================== LDPC matrix memoization (performance) =====================
 * createMatrixA + GaussJordan (+ createGeneratorMatrix) are deterministic functions of
 * (wc, wr, capacity): fixed seeds (LPDC_MESSAGE_SEED) + a deterministic LCG, so they
 * produce byte-identical matrices on every call -- yet the original code rebuilt them on
 * every encode and every decode. GaussJordan is ~O(nb_pcb^2 * capacity/32) and dominates
 * codec time for colour modes with color_number > 8 (the 8->16 throughput cliff). We
 * memoize the built matrices per (wc, wr, capacity) and hand each caller a fresh malloc'd
 * COPY, so callers keep their existing ownership/free semantics and can never corrupt the
 * cache. The cache is thread-local: no locking, and each worker thread warms its own small
 * cache once per colour mode (a deployment uses a handful of keys). Only the dense message
 * path (wr > 3) is cached; the small metadata path is built as before.
 *
 * Eviction is LRU: a thread-local clock stamps each access, and a full cache evicts the
 * least-recently-used entry. A single LDPC encode touches several distinct keys (one per
 * full sub-block plus the trailing partial block, each a different capacity), and realistic
 * callers interleave fit-probing across many payload sizes -- without eviction a fixed table
 * saturates with transient keys and starves the hot ones, collapsing back to the rebuild
 * cost. LRU keeps the working set (the handful of repeatedly-encoded keys) resident. */
#define LDPC_CACHE_SIZE 32
typedef struct {
    jab_boolean valid;
    jab_int32   wc, wr, capacity;   /* key */
    jab_int32   rank;               /* GaussJordan matrix_rank for this key */
    jab_int32   n_ints;             /* length of matrix[] in jab_int32 words */
    jab_uint32  lru;                /* access tick; larger == more recently used */
    jab_int32*  matrix;             /* cached generator G (encode) or post-GJ A (decode) */
} ldpc_cache_entry;

static _Thread_local ldpc_cache_entry g_ldpc_enc_cache[LDPC_CACHE_SIZE];
static _Thread_local ldpc_cache_entry g_ldpc_dec_cache[LDPC_CACHE_SIZE];
static _Thread_local jab_uint32       g_ldpc_lru_clock = 0;

/* Return a fresh copy of the cached matrix for (wc,wr,capacity), or NULL on miss. */
static jab_int32* ldpcCacheGet(ldpc_cache_entry* cache, jab_int32 wc, jab_int32 wr, jab_int32 capacity, jab_int32* out_rank)
{
    for(jab_int32 i=0; i<LDPC_CACHE_SIZE; i++)
    {
        ldpc_cache_entry* e = &cache[i];
        if(e->valid && e->wc==wc && e->wr==wr && e->capacity==capacity)
        {
            jab_int32* copy = (jab_int32*)malloc((size_t)e->n_ints * sizeof(jab_int32));
            if(copy == NULL) return NULL;
            memcpy(copy, e->matrix, (size_t)e->n_ints * sizeof(jab_int32));
            e->lru = ++g_ldpc_lru_clock;   /* mark recently used */
            *out_rank = e->rank;
            return copy;
        }
    }
    return NULL;
}

/* Store a copy of a freshly built matrix. Uses a free slot if available, otherwise evicts
 * the least-recently-used entry. Best-effort: on OOM the entry is simply not cached. */
static void ldpcCachePut(ldpc_cache_entry* cache, jab_int32 wc, jab_int32 wr, jab_int32 capacity,
                         jab_int32 rank, const jab_int32* matrix, jab_int32 n_ints)
{
    jab_int32 victim = 0;
    for(jab_int32 i=0; i<LDPC_CACHE_SIZE; i++)
    {
        if(!cache[i].valid) { victim = i; break; }           /* prefer an empty slot */
        if(cache[i].lru < cache[victim].lru) victim = i;      /* else track the LRU entry */
    }
    jab_int32* stored = (jab_int32*)malloc((size_t)n_ints * sizeof(jab_int32));
    if(stored == NULL) return;
    memcpy(stored, matrix, (size_t)n_ints * sizeof(jab_int32));
    if(cache[victim].valid) free(cache[victim].matrix);       /* evict LRU if reusing a slot */
    cache[victim].wc=wc; cache[victim].wr=wr; cache[victim].capacity=capacity;
    cache[victim].rank=rank; cache[victim].n_ints=n_ints; cache[victim].matrix=stored;
    cache[victim].lru=++g_ldpc_lru_clock;
    cache[victim].valid=1;
}

/* Generator matrix for the LDPC encoder, memoized for the dense message path (wr>3).
 * Returns a heap matrix the caller owns and frees (as before); *out_rank gets the rank.
 * Mirrors encodeLDPC's original build: createMatrixA/createMetadataMatrixA -> GaussJordan
 * (encode) -> createGeneratorMatrix. */
static jab_int32* buildOrCacheGenerator(jab_int32 wc, jab_int32 wr, jab_int32 capacity, jab_int32* out_rank)
{
    jab_boolean cacheable = (wr > 3);
    if(cacheable)
    {
        jab_int32* hit = ldpcCacheGet(g_ldpc_enc_cache, wc, wr, capacity, out_rank);
        if(hit) return hit;
    }
    jab_int32* matrixA = (wr > 0) ? createMatrixA(wc, wr, capacity) : createMetadataMatrixA(wc, capacity);
    if(matrixA == NULL) return NULL;
    jab_int32 rank = 0;
    if(GaussJordan(matrixA, wc, wr, capacity, &rank, 1)) { free(matrixA); return NULL; }
    jab_int32* G = createGeneratorMatrix(matrixA, capacity, capacity - rank);
    free(matrixA);
    if(G == NULL) return NULL;
    if(cacheable)
    {
        jab_int32 offset = (jab_int32)ceil((capacity - rank)/(jab_float)32);
        ldpcCachePut(g_ldpc_enc_cache, wc, wr, capacity, rank, G, offset * capacity);
    }
    *out_rank = rank;
    return G;
}

/* Post-GaussJordan parity-check matrix for the LDPC decoder, memoized for wr>3.
 * Returns a heap matrix the caller owns and frees (as before); *out_rank gets the rank. */
static jab_int32* buildOrCacheDecodeMatrix(jab_int32 wc, jab_int32 wr, jab_int32 capacity, jab_int32* out_rank)
{
    jab_boolean cacheable = (wr > 3);
    if(cacheable)
    {
        jab_int32* hit = ldpcCacheGet(g_ldpc_dec_cache, wc, wr, capacity, out_rank);
        if(hit) return hit;
    }
    jab_int32* matrixA = (wr > 0) ? createMatrixA(wc, wr, capacity) : createMetadataMatrixA(wc, capacity);
    if(matrixA == NULL) return NULL;
    jab_int32 rank = 0;
    if(GaussJordan(matrixA, wc, wr, capacity, &rank, 0)) { free(matrixA); return NULL; }
    if(cacheable)
    {
        jab_int32 nb_pcb = capacity/wr*wc;   /* wr>3 => same formula as createMatrixA */
        jab_int32 offset = (jab_int32)ceil(capacity/(jab_float)32);
        ldpcCachePut(g_ldpc_dec_cache, wc, wr, capacity, rank, matrixA, offset * nb_pcb);
    }
    *out_rank = rank;
    return matrixA;
}

/**
 * @brief LDPC encoding
 * @param data the data to be encoded
 * @param coderate_params the two code rate parameter wc and wr indicating how many '1' in a column (Wc) and how many '1' in a row of the parity check matrix
 * @return the encoded data | NULL if failed
*/
jab_data *encodeLDPC(jab_data* data, jab_int32* coderate_params)
{
    jab_int32 matrix_rank=0;
    jab_int32 wc, wr, Pg, Pn;       //number of '1' in column //number of '1' in row //gross message length //number of parity check symbols //calculate required parameters
    wc=coderate_params[0];
    wr=coderate_params[1];
    Pn=data->length;
    if(wr > 0)
    {
        Pg=ceil((Pn*wr)/(jab_float)(wr-wc));
        Pg = wr * (ceil(Pg / (jab_float)wr));
    }
    else
        Pg=Pn*2;

#if TEST_MODE
	JAB_REPORT_INFO(("wc: %d, wr: %d\tPg: %d, Pn: %d", wc, wr, Pg, Pn))
#endif // TEST_MODE

    //in order to speed up the ldpc encoding, sub blocks are established
    jab_int32 nb_sub_blocks=0;
    for(jab_int32 i=1;i<10000;i++)
    {
        if(Pg / i < 2700)
        {
            nb_sub_blocks=i;
            break;
        }
    }
    jab_int32 Pg_sub_block=0;
    jab_int32 Pn_sub_block=0;
    if(wr > 0)
    {
        Pg_sub_block=((Pg / nb_sub_blocks) / wr) * wr;
        Pn_sub_block=Pg_sub_block * (wr-wc) / wr;
    }
    else
    {
        Pg_sub_block=Pg;
        Pn_sub_block=Pn;
    }
    jab_int32 encoding_iterations=nb_sub_blocks=Pg / Pg_sub_block;//nb_sub_blocks;
    if(Pn_sub_block * nb_sub_blocks < Pn)
        encoding_iterations--;
    //Generator matrix (memoized for the dense message path; see buildOrCacheGenerator)
    jab_int32* G = buildOrCacheGenerator(wc, wr, Pg_sub_block, &matrix_rank);
    if(G == NULL)
    {
        reportError("Generator matrix could not be created in LDPC encoder.");
        return NULL;
    }

    jab_data* ecc_encoded_data = (jab_data *)malloc(sizeof(jab_data) + Pg*sizeof(jab_char));
    if(ecc_encoded_data == NULL)
    {
        reportError("Memory allocation for LDPC encoded data failed");
        free(G);
        return NULL;
    }

    ecc_encoded_data->length = Pg;
    jab_int32 temp,loop;
    jab_int32 offset=ceil((Pg_sub_block - matrix_rank)/(jab_float)32);
    //G * message = ecc_encoded_Data
    for(jab_int32 iter=0; iter < encoding_iterations; iter++)
    {
        for (jab_int32 i=0;i<Pg_sub_block;i++)
        {
            temp=0;
            loop=0;
            jab_int32 offset_index=offset*i;
            for (jab_int32 j=iter*Pn_sub_block; j < (iter+1)*Pn_sub_block; j++)
            {
                temp ^= (((G[offset_index + loop/32] >> (31-loop%32)) & 1) & ((data->data[j] >> 0) & 1)) << 0;
                loop++;
            }
            ecc_encoded_data->data[i+iter*Pg_sub_block]=(jab_char) ((temp >> 0) & 1);
        }
    }
    free(G);
    if(encoding_iterations != nb_sub_blocks)
    {
        jab_int32 start=encoding_iterations*Pn_sub_block;
        jab_int32 last_index=encoding_iterations*Pg_sub_block;
        matrix_rank=0;
        Pg_sub_block=Pg - encoding_iterations * Pg_sub_block;
        Pn_sub_block=Pg_sub_block * (wr-wc) / wr;
        //partial sub-block: same deterministic generator as the main block, so route it
        //through the memoized builder (the trailing partial block was the last uncached
        //createMatrixA/GaussJordan in the encode path -- the high-ECC hotspot)
        jab_int32* G = buildOrCacheGenerator(wc, wr, Pg_sub_block, &matrix_rank);
        if(G == NULL)
        {
            reportError("Generator matrix could not be created in LDPC encoder.");
            return NULL;
        }
        offset=ceil((Pg_sub_block - matrix_rank)/(jab_float)32);
        for (jab_int32 i=0;i<Pg_sub_block;i++)
        {
            temp=0;
            loop=0;
            jab_int32 offset_index=offset*i;
            for (jab_int32 j=start; j < data->length; j++)
            {
                temp ^= (((G[offset_index + loop/32] >> (31-loop%32)) & 1) & ((data->data[j] >> 0) & 1)) << 0;
                loop++;
            }
            ecc_encoded_data->data[i+last_index]=(jab_char) ((temp >> 0) & 1);
        }
        free(G);
    }
    return ecc_encoded_data;
}

/**
 * @brief Iterative hard decision error correction decoder
 * @param data the received data
 * @param matrix the parity check matrix
 * @param length the encoded data length
 * @param height the number of check bits
 * @param max_iter the maximal number of iterations
 * @param is_correct indicating if decodedMessage function could correct all errors
 * @param start_pos indicating the position to start reading in data array
 * @return 1: error correction succeeded | 0: fatal error (out of memory)
*/
jab_int32 decodeMessage(jab_byte* data, jab_int32* matrix, jab_int32 length, jab_int32 height, jab_int32 max_iter, jab_boolean *is_correct, jab_int32 start_pos)
{
    jab_int32* max_val=(jab_int32 *)calloc(length, sizeof(jab_int32));
    if(max_val == NULL)
    {
        reportError("Memory allocation for LDPC decoder failed");

        return 0;
    }
    jab_int32* equal_max=(jab_int32 *)calloc(length, sizeof(jab_int32));
    if(equal_max == NULL)
    {
        reportError("Memory allocation for LDPC decoder failed");
        free(max_val);
        return 0;
    }
    jab_int32* prev_index=(jab_int32 *)calloc(length, sizeof(jab_int32));
    if(prev_index == NULL)
    {
        reportError("Memory allocation for LDPC decoder failed");
        free(max_val);
        free(equal_max);
        return 0;
    }

    *is_correct=(jab_boolean)1;
    jab_int32 check=0;
    jab_int32 counter=0, prev_count=0;
    jab_int32 max=0;
    jab_int32 offset=ceil(length/(jab_float)32);

    /* Packed MSB-first copy of the received vector (data[start_pos .. +length)), refreshed
     * each iteration because the bit-flips below mutate data. Lets the per-check parity use
     * the word-at-a-time popcount primitive (ldpc_row_parity) instead of a per-bit scan. */
    jab_uint32* packed=(jab_uint32 *)malloc((size_t)offset * sizeof(jab_uint32));
    if(packed == NULL)
    {
        reportError("Memory allocation for LDPC decoder failed");
        free(prev_index);
        free(equal_max);
        free(max_val);
        return 0;
    }

    for (jab_int32 kl=0;kl<max_iter;kl++)
    {
        max=0;
        ldpc_pack_bits(packed, data+start_pos, length);   /* re-pack mutated data */
        for(jab_int32 j=0;j<height;j++)
        {
            const jab_uint32* row = (const jab_uint32*)(matrix + (size_t)j*offset);
            check = ldpc_row_parity(row, packed, offset);   /* == (sum_i row_i&data_i) % 2 */
            if(check)
            {
                for(jab_int32 k=0;k<length;k++)
                {
                    if(((matrix[j*offset+k/32] >> (31-k%32)) & 1))
                        max_val[k]++;
                }
            }
        }
        //find maximal values in max_val
        jab_boolean is_used=0;
        for (jab_int32 j=0;j<length;j++)
        {
            is_used=(jab_boolean)0;
            for(jab_int32 i=0;i< prev_count;i++)
            {
                if(prev_index[i]==j)
                    is_used=(jab_boolean)1;
            }
            if(max_val[j]>=max && !is_used)
            {
                if(max_val[j]!=max)
                    counter=0;
                max=max_val[j];
                equal_max[counter]=j;
                counter++;
            }
            max_val[j]=0;
        }
        //flip bits
        if(max>0)
        {
            *is_correct=(jab_boolean) 0;
            if(length < 36)
            {
                /* WS-4.5.3: was rand() — unseeded stdlib PRNG. Replaced with
                 * project's deterministic LCG (lcg64_temper) so the LDPC
                 * bit-flip tiebreak is reproducible across processes.
                 * Original behavior matched the divisor formula
                 *   rand_tmp = rand() / (jab_float)UINT32_MAX * counter
                 * Using lcg64_temper() (returns full uint32_t range)
                 * preserves the same statistical distribution. */
                uint32_t r = lcg64_temper();
                jab_int32 rand_tmp = pn_index(r, counter);
                prev_index[0]=start_pos+equal_max[rand_tmp];
                data[start_pos+equal_max[rand_tmp]]=(data[start_pos+equal_max[rand_tmp]]+1)%2;
            }
            else
            {
                for(jab_int32 j=0; j< counter;j++)
                {
                    prev_index[j]=start_pos+equal_max[j];
                    data[start_pos+equal_max[j]]=(data[start_pos+equal_max[j]]+1)%2;
                }
            }
            prev_count=counter;
            counter=0;
        }
        else
            *is_correct=(jab_boolean) 1;

        if(*is_correct == 0 && kl+1 < max_iter)
            *is_correct=(jab_boolean)1;
        else
            break;
    }
#if TEST_MODE
    JAB_REPORT_INFO(("start position:%d, stop position:%d, correct:%d", start_pos, start_pos+length,(jab_int32)*is_correct))
#endif
    free(packed);
    free(prev_index);
    free(equal_max);
    free(max_val);
    return 1;
}

/**
 * @brief LDPC decoding to perform hard decision
 * @param data the encoded data
 * @param length the encoded data length
 * @param wc the number of '1's in a column
 * @param wr the number of '1's in a row
 * @return the decoded data length | 0: fatal error (out of memory)
*/
jab_int32 decodeLDPChd(jab_byte* data, jab_int32 length, jab_int32 wc, jab_int32 wr)
{
    jab_int32 matrix_rank=0;
    jab_int32 max_iter=25;
    jab_int32 Pn, Pg, decoded_data_len = 0;
    if(wr > 3)
    {
        Pg = wr * (length / wr);
        Pn = Pg * (wr - wc) / wr;                //number of source symbols
    }
    else
    {
        Pg=length;
        Pn=length/2;
        wc=2;
        if(Pn>36)
            wc=3;
    }
    decoded_data_len=Pn;

    //in order to speed up the ldpc encoding, sub blocks are established
    jab_int32 nb_sub_blocks=0;
    for(jab_int32 i=1;i<10000;i++)
    {
        if(Pg / i < 2700)
        {
            nb_sub_blocks=i;
            break;
        }
    }
    jab_int32 Pg_sub_block=0;
    jab_int32 Pn_sub_block=0;
    if(wr > 3)
    {
        Pg_sub_block=((Pg / nb_sub_blocks) / wr) * wr;
        Pn_sub_block=Pg_sub_block * (wr-wc) / wr;
    }
    else
    {
        Pg_sub_block=Pg;
        Pn_sub_block=Pn;
    }
    jab_int32 decoding_iterations=nb_sub_blocks=Pg / Pg_sub_block;//nb_sub_blocks;
    if(Pn_sub_block * nb_sub_blocks < Pn)
        decoding_iterations--;

    //parity check matrix (memoized for the dense message path; see buildOrCacheDecodeMatrix)
    jab_int32* matrixA = buildOrCacheDecodeMatrix(wc, wr, Pg_sub_block, &matrix_rank);
    if(matrixA == NULL)
    {
        reportError("LDPC matrix could not be created in decoder.");
        return 0;
    }

    jab_int32 old_Pg_sub=Pg_sub_block;
    jab_int32 old_Pn_sub=Pn_sub_block;
    for (jab_int32 iter = 0; iter < nb_sub_blocks; iter++)
    {
        if(decoding_iterations != nb_sub_blocks && iter == decoding_iterations)
        {
            matrix_rank=0;
            Pg_sub_block=Pg - decoding_iterations * Pg_sub_block;
            Pn_sub_block=Pg_sub_block * (wr-wc) / wr;
            jab_int32* matrixA1 = createMatrixA(wc, wr, Pg_sub_block);
            if(matrixA1 == NULL)
            {
                reportError("LDPC matrix could not be created in decoder.");
                return 0;
            }
            jab_boolean encode=0;
            if(GaussJordan(matrixA1, wc, wr, Pg_sub_block, &matrix_rank,encode))
            {
                reportError("Gauss Jordan Elimination in LDPC encoder failed.");
                free(matrixA1);
                return 0;
            }
            //ldpc decoding
            //first check syndrom (word-at-a-time popcount parity; see ldpc_syndrome_ok_bytes)
            jab_boolean is_correct=1;
            if(ldpc_syndrome_ok_bytes(matrixA1, &data[iter*old_Pg_sub], matrix_rank, Pg_sub_block) != 1)
                is_correct=(jab_boolean) 0;//message not correct

            if(is_correct==0)
            {
                jab_int32 start_pos=iter*old_Pg_sub;
                jab_int32 success=decodeMessage(data, matrixA1, Pg_sub_block, matrix_rank, max_iter, &is_correct,start_pos);
                if(success == 0)
                {
                    reportError("LDPC decoder error.");
                    free(matrixA1);
                    return 0;
                }
            }
            if(is_correct==0)
            {
                jab_boolean is_correct=1;
                if(ldpc_syndrome_ok_bytes(matrixA1, &data[iter*old_Pg_sub], matrix_rank, Pg_sub_block) != 1)
                    is_correct=(jab_boolean) 0;//message not correct
                if(is_correct==0)
                {
                    reportError("Too many errors in message. LDPC decoding failed.");
                    free(matrixA1);
                    return 0;
                }
            }
            free(matrixA1);
        }
        else
        {
            //ldpc decoding
            //first check syndrom (word-at-a-time popcount parity; see ldpc_syndrome_ok_bytes)
            jab_boolean is_correct=1;
            if(ldpc_syndrome_ok_bytes(matrixA, &data[iter*old_Pg_sub], matrix_rank, Pg_sub_block) != 1)
                is_correct=(jab_boolean) 0;//message not correct

            if(is_correct==0)
            {
                jab_int32 start_pos=iter*old_Pg_sub;
                jab_int32 success=decodeMessage(data, matrixA, Pg_sub_block, matrix_rank, max_iter, &is_correct, start_pos);
                if(success == 0)
                {
                    reportError("LDPC decoder error.");
                    free(matrixA);
                    return 0;
                }
                is_correct=1;
                if(ldpc_syndrome_ok_bytes(matrixA, &data[iter*old_Pg_sub], matrix_rank, Pg_sub_block) != 1)
                    is_correct=(jab_boolean)0;//message not correct
                if(is_correct==0)
                {
                    reportError("Too many errors in message. LDPC decoding failed.");
                    free(matrixA);
                    return 0;
                }
            }
        }
        jab_int32 loop=0;
        for (jab_int32 i=iter*old_Pg_sub;i < iter * old_Pg_sub + Pn_sub_block; i++)
        {
            data[iter*old_Pn_sub+loop]=data[i+ matrix_rank];
            loop++;
        }
    }
    free(matrixA);
    return decoded_data_len;
}

/**
 * @brief LDPC Iterative Log Likelihood decoding algorithm for binary codes
 * @param enc the received reliability value for each bit
 * @param matrix the error correction decoding matrix
 * @param length the encoded data length
 * @param checkbits the rank of the matrix
 * @param height the number of check bits
 * @param max_iter the maximal number of iterations
 * @param is_correct indicating if decodedMessage function could correct all errors
 * @param start_pos indicating the position to start reading in enc array
 * @param dec is the tentative decision after each decoding iteration
 * @return 1: success | 0: fatal error (out of memory)
*/
jab_int32 decodeMessageILL(jab_float* enc, jab_int32* matrix, jab_int32 length, jab_int32 checkbits, jab_int32 height, jab_int32 max_iter, jab_boolean *is_correct, jab_int32 start_pos, jab_byte* dec)
{
    jab_double* lambda=(jab_double *)malloc(length * sizeof(jab_double));
    if(lambda == NULL)
    {
        reportError("Memory allocation for Lambda in LDPC decoder failed");
        return 0;
    }
    jab_double* old_nu_row=(jab_double *)malloc(length * sizeof(jab_double));
    if(old_nu_row == NULL)
    {
        reportError("Memory allocation for Lambda in LDPC decoder failed");
        free(lambda);
        return 0;
    }
    jab_double* nu=(jab_double *)malloc(length*height * sizeof(jab_double));
    if(nu == NULL)
    {
        reportError("Memory allocation for nu in LDPC decoder failed");
        free(old_nu_row);
        free(lambda);
        return 0;
    }
    memset(nu,0,length*height *sizeof(jab_double));
    jab_int32* index=(jab_int32 *)malloc(length * sizeof(jab_int32));
    if(index == NULL)
    {
        reportError("Memory allocation for index in LDPC decoder failed");
        free(old_nu_row);
        free(lambda);
        free(nu);
        return 0;
    }
    jab_int32 offset=ceil(length/(jab_float)32);
    jab_double product=1.0;

    //set last bits
    for (jab_int32 i=length-1;i >= length-(height-checkbits);i--)
    {
        enc[start_pos+i]=1.0;
        dec[start_pos+i]=0;
    }
    jab_double meansum=0.0;
    for (jab_int32 i=0;i<length;i++)
        meansum+=enc[start_pos+i];

    //calc variance
    meansum/=length;
    jab_double var=0.0;
    for (jab_int32 i=0;i<length;i++)
        var+=(enc[start_pos+i]-meansum)*(enc[start_pos+i]-meansum);
    var/=(length-1);

    //initialize lambda
    for (jab_int32 i=0;i<length;i++)
    {
        if(dec[start_pos+i])
            enc[start_pos+i]=-enc[start_pos+i];
        lambda[i]=(jab_double)2.0*enc[start_pos+i]/var;
    }

    //check node update
    jab_int32 count;
    for (jab_int32 kl=0;kl<max_iter;kl++)
    {
        for(jab_int32 j=0;j<height;j++)
        {
            product=1.0;
            count=0;
            for (jab_int32 i=0;i<length;i++)
            {
                if((matrix[j*offset+i/32] >> (31-i%32)) & 1)
                {
                    product*=tanh(-(lambda[i]-nu[j*length+i])*0.5);
                    index[count]=i;
                    ++count;
                }
                old_nu_row[i]=nu[j*length+i];
            }
            //update nu
            for (jab_int32 i=0;i<count;i++)
            {
                if(((matrix[j*offset+index[i]/32] >> (31-index[i]%32)) & 1) && tanh(-(lambda[index[i]]-old_nu_row[index[i]])*0.5) != 0.0)
                    nu[j*length+index[i]]=-2*atanh(product/tanh(-(lambda[index[i]]-old_nu_row[index[i]])*0.5));
                else
                    nu[j*length+index[i]]=-2*atanh(product);
            }
        }
        //update lambda
        jab_double sum;
        for (jab_int32 i=0;i<length;i++)
        {
            sum=0.0;
            for(jab_int32 k=0;k<height;k++)
                sum+=nu[k*length+i];
            lambda[i]=(jab_double)2.0*enc[start_pos+i]/var+sum;
            if(lambda[i]<0)
                dec[start_pos+i]=1;
            else
                dec[start_pos+i]=0;
        }
        //check matrix times dec
        *is_correct=(jab_boolean) 1;
        for (jab_int32 i=0;i< height; i++)
        {
            jab_int32 temp=0;
            for (jab_int32 j=0;j<length;j++)
                temp ^= (((matrix[i*offset+j/32] >> (31-j%32)) & 1) & ((dec[start_pos+j] >> 0) & 1)) << 0;
            if (temp)
            {
                *is_correct=(jab_boolean) 0;
                break;//message not correct
            }
        }
        if(!*is_correct && kl<max_iter-1)
            *is_correct=(jab_boolean) 1;
        else
            break;
    }
#if TEST_MODE
    JAB_REPORT_INFO(("start position:%d, stop position:%d, correct:%d", start_pos, start_pos+length,(jab_int32)*is_correct))
#endif
    free(lambda);
    free(nu);
    free(old_nu_row);
    free(index);
    return 1;
}


/**
 * @brief LDPC Iterative belief propagation decoding algorithm for binary codes
 * @param enc the received reliability value for each bit
 * @param matrix the decoding matrixreliability value for each bit
 * @param length the encoded data length
 * @param checkbits the rank of the matrix
 * @param height the number of check bits
 * @param max_iter the maximal number of iterations
 * @param is_correct indicating if decodedMessage function could correct all errors
 * @param start_pos indicating the position to start reading in enc array
 * @param dec is the tentative decision after each decoding iteration
 * @return 1: error correction succeded | 0: decoding failed
*/
jab_int32 decodeMessageBP(jab_float* enc, jab_int32* matrix, jab_int32 length, jab_int32 checkbits, jab_int32 height, jab_int32 max_iter, jab_boolean *is_correct, jab_int32 start_pos, jab_byte* dec)
{
    jab_double* lambda=(jab_double *)malloc(length * sizeof(jab_double));
    if(lambda == NULL)
    {
        reportError("Memory allocation for Lambda in LDPC decoder failed");
        return 0;
    }
    jab_double* old_nu_row=(jab_double *)malloc(length * sizeof(jab_double));
    if(old_nu_row == NULL)
    {
        reportError("Memory allocation for Lambda in LDPC decoder failed");
        free(lambda);
        return 0;
    }
    jab_double* nu=(jab_double *)malloc(length*height * sizeof(jab_double));
    if(nu == NULL)
    {
        reportError("Memory allocation for nu in LDPC decoder failed");
        free(old_nu_row);
        free(lambda);
        return 0;
    }
    memset(nu,0,length*height *sizeof(jab_double));
    jab_int32* index=(jab_int32 *)malloc(length * sizeof(jab_int32));
    if(index == NULL)
    {
        reportError("Memory allocation for index in LDPC decoder failed");
        free(old_nu_row);
        free(lambda);
        free(nu);
        return 0;
    }
    jab_int32 offset=ceil(length/(jab_float)32);
    jab_double product=1.0;

    //set last bits
    for (jab_int32 i=length-1;i >= length-(height-checkbits);i--)
    {
        enc[start_pos+i]=1.0;
        dec[start_pos+i]=0;
    }

    jab_double meansum=0.0;
    for (jab_int32 i=0;i<length;i++)
        meansum+=enc[start_pos+i];

    //calc variance
    meansum/=length;
    jab_double var=0.0;
    for (jab_int32 i=0;i<length;i++)
        var+=(enc[start_pos+i]-meansum)*(enc[start_pos+i]-meansum);
    var/=(length-1);

    //initialize lambda
    for (jab_int32 i=0;i<length;i++)
    {
        if(dec[start_pos+i])
            enc[start_pos+i]=-enc[start_pos+i];
        lambda[i]=(jab_double)2.0*enc[start_pos+i]/var;
    }

    //check node update
    jab_int32 count;
    for (jab_int32 kl=0;kl<max_iter;kl++)
    {
        for(jab_int32 j=0;j<height;j++)
        {
            product=1.0;
            count=0;
            for (jab_int32 i=0;i<length;i++)
            {
                if((matrix[j*offset+i/32] >> (31-i%32)) & 1)
                {
                    if (kl==0)
                        product*=tanh(lambda[i]*0.5);
                    else
                        product*=tanh(nu[j*length+i]*0.5);
                    index[count]=i;
                    ++count;
                }
            }
            //update nu
            jab_double num=0.0, denum=0.0;
            for (jab_int32 i=0;i<count;i++)
            {
                if(((matrix[j*offset+index[i]/32] >> (31-index[i]%32)) & 1) && tanh(nu[j*length+index[i]]*0.5) != 0.0 && kl>0)// && tanh(-(lambda[index[i]]-old_nu_row[index[i]])/2) != 0.0)
                {
                    num     = 1 + product / tanh(nu[j*length+index[i]]*0.5);
                    denum   = 1 - product / tanh(nu[j*length+index[i]]*0.5);
                }
                else if(((matrix[j*offset+index[i]/32] >> (31-index[i]%32)) & 1) && tanh(lambda[index[i]]*0.5) != 0.0 && kl==0)// && tanh(-(lambda[index[i]]-old_nu_row[index[i]])/2) != 0.0)
                {
                    num     = 1 + product / tanh(lambda[index[i]]*0.5);
                    denum   = 1 - product / tanh(lambda[index[i]]*0.5);
                }
                else
                {
                    num     = 1 + product;
                    denum   = 1 - product;
                }
                if (num == 0.0)
                    nu[j*length+index[i]]=-1;
                else if(denum == 0.0)
                    nu[j*length+index[i]]= 1;
                else
                    nu[j*length+index[i]]= log(num / denum);
            }
        }
        //update lambda
        jab_double sum;
        for (jab_int32 i=0;i<length;i++)
        {
            sum=0.0;
            for(jab_int32 k=0;k<height;k++)
            {
                sum+=nu[k*length+i];
                old_nu_row[k]=nu[k*length+i];
            }
            for(jab_int32 k=0;k<height;k++)
            {
                if((matrix[k*offset+i/32] >> (31-i%32)) & 1)
                    nu[k*length+i]=lambda[i]+(sum-old_nu_row[k]);
            }
            lambda[i]=2.0*enc[start_pos+i]/var+sum;
            if(lambda[i]<0)
                dec[start_pos+i]=1;
            else
                dec[start_pos+i]=0;
        }
        //check matrix times dec
        *is_correct=(jab_boolean) 1;
        for (jab_int32 i=0;i< height; i++)
        {
            jab_int32 temp=0;
            for (jab_int32 j=0;j<length;j++)
                temp ^= (((matrix[i*offset+j/32] >> (31-j%32)) & 1) & ((dec[start_pos+j] >> 0) & 1)) << 0;
            if (temp)
            {
                *is_correct=(jab_boolean) 0;
                break;//message not correct
            }
        }
        if(!*is_correct && kl<max_iter-1)
            *is_correct=(jab_boolean) 1;
        else
            break;
    }
#if TEST_MODE
    JAB_REPORT_INFO(("start position:%d, stop position:%d, correct:%d", start_pos, start_pos+length,(jab_int32)*is_correct))
#endif
    free(lambda);
    free(nu);
    free(old_nu_row);
    free(index);
    return 1;
}

/**
 * @brief LDPC decoding to perform soft decision
 * @param enc the probability value for each bit position
 * @param length the encoded data length
 * @param wc the number of '1's in each column
 * @param wr the number of '1's in each row
 * @param dec the decoded data
 * @return the decoded data length | 0: decoding error
*/
jab_int32 decodeLDPC(jab_float* enc, jab_int32 length, jab_int32 wc, jab_int32 wr, jab_byte* dec)
{
    jab_int32 matrix_rank=0;
    jab_int32 max_iter=25;
    jab_int32 Pn, Pg, decoded_data_len = 0;
    if(wr > 3)
    {
        Pg = wr * (length / wr);
        Pn = Pg * (wr - wc) / wr; //number of source symbols
    }
    else
    {
        Pg=length;
        Pn=length/2;
        wc=2;
        if(Pn>36)
            wc=3;
    }
    decoded_data_len=Pn;

    //in order to speed up the ldpc encoding, sub blocks are established
    jab_int32 nb_sub_blocks=0;
    for(jab_int32 i=1;i<10000;i++)
    {
        if(Pg / i < 2700)
        {
            nb_sub_blocks=i;
            break;
        }
    }
    jab_int32 Pg_sub_block=0;
    jab_int32 Pn_sub_block=0;
    if(wr > 3)
    {
        Pg_sub_block=((Pg / nb_sub_blocks) / wr) * wr;
        Pn_sub_block=Pg_sub_block * (wr-wc) / wr;
    }
    else
    {
        Pg_sub_block=Pg;
        Pn_sub_block=Pn;
    }
    jab_int32 decoding_iterations=nb_sub_blocks=Pg / Pg_sub_block;//nb_sub_blocks;
    if(Pn_sub_block * nb_sub_blocks < Pn)
        decoding_iterations--;

    //parity check matrix (memoized for the dense message path; see buildOrCacheDecodeMatrix)
    jab_int32* matrixA = buildOrCacheDecodeMatrix(wc, wr, Pg_sub_block, &matrix_rank);
    if(matrixA == NULL)
    {
        reportError("LDPC matrix could not be created in decoder.");
        return 0;
    }
#if TEST_MODE
	//JAB_REPORT_INFO(("GaussJordan matrix done"))
#endif
    jab_int32 old_Pg_sub=Pg_sub_block;
    jab_int32 old_Pn_sub=Pn_sub_block;
    for (jab_int32 iter = 0; iter < nb_sub_blocks; iter++)
    {
        if(decoding_iterations != nb_sub_blocks && iter == decoding_iterations)
        {
            matrix_rank=0;
            Pg_sub_block=Pg - decoding_iterations * Pg_sub_block;
            Pn_sub_block=Pg_sub_block * (wr-wc) / wr;
            jab_int32* matrixA1 = createMatrixA(wc, wr, Pg_sub_block);
            if(matrixA1 == NULL)
            {
                reportError("LDPC matrix could not be created in decoder.");
                return 0;
            }
            jab_boolean encode=0;
            if(GaussJordan(matrixA1, wc, wr, Pg_sub_block, &matrix_rank,encode))
            {
                reportError("Gauss Jordan Elimination in LDPC encoder failed.");
                free(matrixA1);
                return 0;
            }
            //ldpc decoding
            //first check syndrom
            jab_boolean is_correct=1;
            jab_int32 offset=ceil(Pg_sub_block/(jab_float)32);
            for (jab_int32 i=0;i< matrix_rank; i++)
            {
                jab_int32 temp=0;
                for (jab_int32 j=0;j<Pg_sub_block;j++)
                    temp ^= (((matrixA1[i*offset+j/32] >> (31-j%32)) & 1) & ((dec[iter*old_Pg_sub+j] >> 0) & 1)) << 0; //
                if (temp)
                {
                    is_correct=(jab_boolean) 0; //message not correct
                    break;
                }
            }

            if(is_correct==0)
            {
                jab_int32 start_pos=iter*old_Pg_sub;
                jab_int32 success=decodeMessageBP(enc, matrixA1, Pg_sub_block, matrix_rank, wr<4 ? Pg_sub_block/2 : Pg_sub_block/wr*wc, max_iter, &is_correct,start_pos,dec);
                if(success == 0)
                {
                    reportError("LDPC decoder error.");
                    free(matrixA1);
                    return 0;
                }
            }
            if(is_correct==0)
            {
                jab_boolean is_correct=1;
                for (jab_int32 i=0;i< matrix_rank; i++)
                {
                    jab_int32 temp=0;
                    for (jab_int32 j=0;j<Pg_sub_block;j++)
                        temp ^= (((matrixA1[i*offset+j/32] >> (31-j%32)) & 1) & ((dec[iter*old_Pg_sub+j] >> 0) & 1)) << 0;
                    if (temp)
                    {
                        is_correct=(jab_boolean) 0;//message not correct
                        break;
                    }
                }
                if(is_correct==0)
                {
 //                   reportError("Too many errors in message. LDPC decoding failed.");
                    free(matrixA1);
                    return 0;
                }
            }
            free(matrixA1);
        }
        else
        {
            //ldpc decoding
            //first check syndrom
            jab_boolean is_correct=1;
            jab_int32 offset=ceil(Pg_sub_block/(jab_float)32);
            for (jab_int32 i=0;i< matrix_rank; i++)
            {
                jab_int32 temp=0;
                for (jab_int32 j=0;j<Pg_sub_block;j++)
                    temp ^= (((matrixA[i*offset+j/32] >> (31-j%32)) & 1) & ((dec[iter*old_Pg_sub+j] >> 0) & 1)) << 0;
                if (temp)
                {
                    is_correct=(jab_boolean) 0;//message not correct
                    break;
                }
            }

            if(is_correct==0)
            {
                jab_int32 start_pos=iter*old_Pg_sub;
                jab_int32 success=decodeMessageBP(enc, matrixA, Pg_sub_block, matrix_rank, wr<4 ? Pg_sub_block/2 : Pg_sub_block/wr*wc, max_iter, &is_correct, start_pos,dec);
                if(success == 0)
                {
                    reportError("LDPC decoder error.");
                    free(matrixA);
                    return 0;
                }
                is_correct=1;
                for (jab_int32 i=0;i< matrix_rank; i++)
                {
                    jab_int32 temp=0;
                    for (jab_int32 j=0;j<Pg_sub_block;j++)
                        temp ^= (((matrixA[i*offset+j/32] >> (31-j%32)) & 1) & ((dec[iter*old_Pg_sub+j] >> 0) & 1)) << 0;
                    if (temp)
                    {
                        is_correct=(jab_boolean)0;//message not correct
                        break;
                    }
                }
                if(is_correct==0)
                {
       //             reportError("Too many errors in message. LDPC decoding failed.");
                    free(matrixA);
                    return 0;
                }
            }
        }

        jab_int32 loop=0;
        for (jab_int32 i=iter*old_Pg_sub;i < iter * old_Pg_sub + Pn_sub_block; i++)
        {
            dec[iter*old_Pn_sub+loop]=dec[i+ matrix_rank];
            loop++;
        }
    }
    free(matrixA);
    return decoded_data_len;
}
