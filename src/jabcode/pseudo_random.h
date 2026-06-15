#ifndef JAB_PSEUDO_RANDOM_H
#define JAB_PSEUDO_RANDOM_H

#include <inttypes.h>

#ifndef UINT32_MAX
#define UINT32_MAX 4294967295
#endif

void setSeed(uint64_t seed);
uint32_t lcg64_temper();

/**
 * @brief Reduce a 32-bit PRNG draw to a uniform index in [0, range) for the
 *        in-place Fisher-Yates permutations (data interleaving + LDPC matrix
 *        generation).
 *
 * The historical float-scaling mapping is preserved bit-for-bit for every draw
 * that already lands in range, so the permutation -- and thus wire-level
 * interoperability with the Fraunhofer reference / ISO 23634 ecosystem -- is
 * unchanged. Only the single float-rounding edge is guarded: a uint32_t within
 * ~128 of UINT32_MAX rounds to 1.0f in single precision (24-bit mantissa), so
 * the truncated product equals `range` -- one past the valid [0, range-1] and,
 * on the first loop iteration, a latent heap out-of-bounds access. The clamp
 * touches only that edge; the reference's behavior there is undefined, so no
 * defined interoperability is lost.
 *
 * @param r     a draw from lcg64_temper()
 * @param range exclusive upper bound (must be > 0)
 * @return index in [0, range-1]
 */
static inline int32_t pn_index(uint32_t r, int32_t range)
{
    int32_t pos = (int32_t)( (float)r / (float)UINT32_MAX * (float)range );
    if (pos >= range)
        pos = range - 1;
    return pos;
}

#endif /* JAB_PSEUDO_RANDOM_H */
