# 5. Interleaving, burst errors, and the determinism contract

<!-- objective: A mathematically mature reader can prove the interleave is a uniform-choice Fisher-Yates permutation given the PRNG, explain burst-error decorrelation as the design goal, and evaluate the Annex F divergence — what "the same PRNG" must mean for a wire format, and what pn_index does and does not preserve. -->

**Where it lives.** `interleaveData`/`deinterleaveData` and `INTERLEAVE_SEED 226759` (`src/jabcode/interleave.c:20-77`); the PRNG core `lcg64_temper`/`setSeed` (`src/jabcode/pseudo_random.c:10-30`) and the draw-to-range reduction `pn_index` (`pseudo_random.h:32-38`); the sibling seeds `LPDC_MESSAGE_SEED 785465` / `LPDC_METADATA_SEED 38545` (`ldpc.h:17-18`); the in-source interop posture (`encoder.h:23-24`, `pseudo_random.h:18-26`). Spec ground: ISO/IEC 23634:2022 clause 5.5 and Annex F (informative). The implementation-level divergence register is [../developers-manual/08-interleave-and-prng.md](../developers-manual/08-interleave-and-prng.md) (JC-T ch. 8) — cited throughout, not re-derived. <!-- anchor: interleave.c:20-77; pseudo_random.c:10-30; pseudo_random.h:32-38; ldpc.h:17-18; encoder.h:23-24; ISO 23634 5.5, Annex F -->

## The problem

Two problems, actually, and the tension between them is the chapter.

**First: damage is spatially correlated.** Scratches, glare, fold lines and occlusions kill *contiguous* modules. The message stream is placed into modules in a fixed serpentine order, so contiguous physical damage becomes contiguous positions in the LDPC codeword — and an error-correcting code fed its entire error budget in one neighbourhood fails long before its average-case capability is exhausted ([03-ldpc-coding-theory.md](03-ldpc-coding-theory.md)). The standard remedy is to permute the codeword before placement so that any physical burst lands on positions scattered across the whole word. A *random-looking* permutation is wanted; Annex F supplies a shuffle driven by a pseudo-random generator.

**Second: the decoder must apply the exact inverse permutation.** There is no side channel — the permutation cannot be transmitted; it must be *re-derived* by every decoder on earth, bit-for-bit. "Random" and "reproducible everywhere forever" must hold simultaneously. The resolution is the standard cryptographic-adjacent trick: a deterministic PRNG with a fixed public seed. Randomness here is a *property of the design distribution* (the permutation behaves, against typical damage, like a uniform draw), while the actual object is a single fixed permutation per length N, as deterministic as the finder patterns. What exactly "the same PRNG" must mean for this to work across implementations is the load-bearing question of §5.4.

## Theory

### 5.1 Fisher-Yates and its correctness — full derivation

This is the book's first permutation argument, so it gets the complete treatment; later chapters (LDPC matrix construction in [03-ldpc-coding-theory.md](03-ldpc-coding-theory.md) uses the same machinery; traversal arguments in [10-cascade-combinatorics.md](10-cascade-combinatorics.md)) will only sketch or cite it.

The algorithm, as Annex F states it: "a) Give an initial seed... b) Set the variable L = N. c) Generate a random number R between 0 and L-1. d) Swap the bit bR at index R and the bit bL-1 at index L-1. e) Update L = L-1. f) Repeat the steps 3-5 until L = 0." This is the Fisher-Yates shuffle in Durstenfeld's in-place form, run from the top index downward. <!-- anchor: ISO 23634 Annex F -->

**Claim.** If the draws R are independent and R at stage L is uniform on {0, ..., L−1}, the resulting permutation is uniform on the symmetric group S_N.

**Proof.** Identify a run of the algorithm with its draw vector

$$
(r_1, r_2, \ldots, r_N), \qquad r_i \in \{0, 1, \ldots, N-i\},
$$

where r_i is the draw at the stage with L = N − i + 1. The number of possible draw vectors is

$$
\prod_{i=1}^{N} (N-i+1) = N\,(N-1)\cdots 1 = N!\,,
$$

and under the uniformity assumption each vector has probability 1/N!. It remains to show the map from draw vectors to permutations is a bijection; since both sets have cardinality N!, injectivity suffices.

Injectivity by reconstruction: run the algorithm backwards. After the first stage, position N−1 holds its final value and is never touched again (all later swaps involve indices < L ≤ N−1); in general, after the stage with L = k, position k−1 is frozen. So given the output permutation π, the last-frozen position determines the last swap, and undoing it exposes the state before that stage; inductively,

$$
r_1 = \pi^{-1}\!\left(x_{N-1}\right)\ \text{read off the output},\quad
\text{then recurse on the first } N-1 \text{ positions},
$$

i.e. each r_i is recoverable uniquely from π. Distinct draw vectors therefore produce distinct permutations, the map is a bijection, and π is uniform on S_N. ∎

Two corollaries matter for this codec:

1. **Every permutation is reachable** — the design distribution has full support, so no adversarially convenient permutation is structurally excluded (relevant in [11-adversarial-channel.md](11-adversarial-channel.md)).
2. **Uniformity is exactly as good as the draws.** The theorem consumes "R uniform on {0, ..., L−1}, independent". A PRNG delivers neither, only an approximation — and, more importantly for a wire format, a *specific fixed sequence*. Correctness of decode never depended on uniformity anyway; uniformity is a statement about typical-case burst resistance, decode correctness only needs §5.3.

### 5.2 What interleaving buys LDPC

Order of operations (encode): LDPC-encode → interleave → place into modules. Decode inverts: read modules → de-interleave → LDPC-decode. Call sites: `interleaveData(ecc_encoded_data)` after `encodeLDPC` (encoder.c:2387); `deinterleaveData(raw_data)` before `decodeLDPChd` (decoder.c:1967); metadata is never interleaved (JC-T §8.2). <!-- anchor: encoder.c:2376-2390; decoder.c:1964-1979 -->

A physical burst wiping *b* consecutive positions of the transmitted stream becomes, after de-interleaving, *b* errors at positions π⁻¹ of a contiguous block. Under the uniform-permutation model of §5.1, those positions are a uniformly random *b*-subset of {0, ..., N−1}: the burst is converted into scattered errors — precisely the regime the hard-decision bit-flipping decoder is built for, where each parity check sees few errors and the flipping majority logic gets traction. Without the permutation, a burst concentrates errors so that many checks in one region are violated simultaneously and the local majority information is itself corrupted. The formal error-model discussion (what "i.i.d.-ish" means for Gallager's construction, and where the analogy breaks) lives in [03-ldpc-coding-theory.md](03-ldpc-coding-theory.md); this chapter needs only the direction of the reduction: interleaving maps the adversary's cheapest damage geometry (contiguous) onto the code's easiest error geometry (scattered). The game-theoretic reading — the attacker is forced from cheap bursts to expensive scatter — is developed in [11-adversarial-channel.md](11-adversarial-channel.md).

### 5.3 Determinism as a distributed contract

Strip the randomness folklore away and what remains on the wire is this: the permutation applied to a length-N stream is a pure function

$$
\pi_N = F(\text{generator},\ \text{seed},\ \text{reduction},\ \text{draw order})
$$

and *every term is part of the wire format*. The codec fixes:

| Term | Value | Anchor |
|---|---|---|
| generator | LCG64: state ← state × 6364136223846793005 + 1; output = temper(state >> 32) with temper constants `0x9D2C5680`, `0xEFC60000` | pseudo_random.c:12-25 |
| seeds | 226759 (interleave), 785465 (LDPC message matrix), 38545 (LDPC metadata matrix) | interleave.c:20; ldpc.h:17-18 |
| reduction | `pn_index`: single-precision float scaling `r / UINT32_MAX × range`, truncated, clamped to range−1 | pseudo_random.h:32-38 |
| draw order | exactly one draw per stream position, front-to-back stage order | interleave.c:29-35 |

Three fixed seeds, three wire-relevant draw streams; change any row and every symbol ever encoded becomes undecodable to you (JC-T §8.3's consumer table). Two consequences deserve theory-level emphasis:

**The reduction is not a detail.** "A random number R between 0 and L−1" (Annex F's step c) admits at least two natural realizations — modulo, `r mod L`, and scaling, `floor(r/2³² × L)` — and they disagree even given identical draws. Concretely, with the codec's own first draw from seed 226759, r = 3605414883: modulo gives `3605414883 mod 8 = 3`, while `pn_index(3605414883, 8) = 6`. Same PRNG, same seed, different permutation. Annex F specifies no reduction formula, so "implement Annex F" underdetermines the wire format even before the generator question arises. <!-- anchor: pseudo_random.h:32-38; ISO 23634 Annex F -->

**Draw count is state.** Because all consumers share one PRNG state stream per seeding, the *number* of draws each stage makes is as binding as their values — an extra draw anywhere shifts every subsequent index (JC-T §8.6). Determinism contracts are consumed whole.

### 5.4 The divergence: Annex F's rand() vs the implementation's LCG64 — at theory depth

Annex F exhibits sample C code per ISO/IEC 9899:

```c
next = next * 1103515245 + 12345;
return (unsigned int)(next/65536) % 32768;
```

with RAND_MAX 32767. Annex F's routine itself names no seed — the numeric seeds are fixed by the body clauses (5.5 for interleaving, 5.4.4 for the LDPC matrices), so what the annex leaves underdetermined is the generator's *reduction* to a bounded range, not the seed. The implementation ships something else entirely (pseudo_random.c:21-25, quoted in full in JC-T §8.1). Both are linear congruential generators at core, and the differences are instructive rather than cosmetic. <!-- anchor: ISO 23634 Annex F; pseudo_random.c:10-30 -->

**Low-bit pathology and why rand() discards 16 bits.** For an LCG with power-of-two modulus 2^m,

$$
x_{n+1} \equiv a\,x_n + c \pmod{2^m},
$$

reducing the recurrence mod 2^k (k ≤ m) shows the low k bits evolve as an LCG mod 2^k on their own — the high bits never feed back downward. Hence bit k−1 has period at most 2^k: the lowest bit alternates with period ≤ 2, the next has period ≤ 4, and so on. This is why the C89 sample outputs `(next/65536) % 32768` — bits 16-30 — rather than the raw low bits: the divide-by-65536 discards the shortest-period bits. (Statement with proof sketch; the full spectral-quality story is Knuth's, see Further reading.)

**The implementation's answer to the same problem.** The codec's generator uses modulus 2^64 with Knuth's MMIX multiplier 6364136223846793005 and increment 1, outputs only the *high* 32 bits of state — sidestepping the low-bit pathology wholesale — and then passes them through a tempering network whose constants `0x9D2C5680`/`0xEFC60000` are the Mersenne Twister's: a bijective GF(2)-linear map on 32-bit words designed to improve the equidistribution of output bits. Tempering being bijective, it changes no period property, only which bit-patterns appear where — but that is exactly what a wire format cares about. <!-- anchor: pseudo_random.c:12-25 -->

**Sequence inequality.** The two generators share no state width, multiplier, output window, or post-processing; their output sequences are unrelated for every seed. Therefore a decoder that implements Annex F's sample literally — any seed, any reduction — derives a different π_N for every N > 1 in the generic case, and different LDPC matrices too (same PRNG drives both, ch. 3), and fails on this codec's output at the de-interleave/LDPC stage. Exercise 1 makes this concrete at N = 4.

**A curiosity with a moral.** Annex F's routine is better-defined than it first looks: since the recurrence is congruential, the state mod 2^32 evolves autonomously, and the output uses only bits 16-30 — which lie inside the low 32 bits. So the output sequence is *independent of the width of the state variable* (32-bit and 64-bit `unsigned long` agree; exercise 3). The genuinely underdetermined parts are the seed (unspecified) and the reduction (§5.3). The lesson generalizes: reproducibility failures hide not in the arithmetic core but at the *edges* — seeding, reduction, draw accounting. That is precisely where the fork's one intervention sits.

**What `pn_index` preserves — the FP-UB quarantine.** The historical reduction was an inline float expression with a genuine undefined-behaviour edge: a draw within about 128 of UINT32_MAX rounds to 1.0f in single precision (24-bit mantissa), the truncated product equals `range`, and the first-iteration swap indexes one past the buffer. The fork's `pn_index` wrapper preserves, verbatim per its contract, "the historical float-scaling mapping ... bit-for-bit for every draw that already lands in range, so the permutation — and thus wire-level interoperability with the Fraunhofer reference / ISO 23634 ecosystem — is unchanged", clamping only the out-of-range edge, where "the reference's behavior there is undefined, so no defined interoperability is lost" (pseudo_random.h:18-26). Read as contract law: the defined portion of the legacy function is frozen; the undefined portion — which no conforming counterparty could have relied on — is replaced by `range − 1`. The regression guard `test-pn` pins both properties across the rounding edge (JC-T §8.5). <!-- anchor: pseudo_random.h:13-38 -->

**The supported claim, framed precisely.** What this source claims, and what its tests defend, is *reference-ecosystem interoperability*: byte-identity of all three draw streams with the inherited Fraunhofer implementation's sequence — `lcg64_temper`, the three seeds, float-scaling reduction. What it does **not** claim is conformance to a literal reading of Annex F, which it classifies as informative: "(PRNG is NOT an axis: ISO Annex F is informative; both profiles keep lcg64_temper.)" (encoder.h:23-24). Whether any independent implementation follows Annex F literally — which would make it wire-incompatible with this entire ecosystem — and whether the reference ever matched Annex F historically, are open questions this corpus cannot answer (JC-T §8.3, "Explicit open interop question"). The theory-level summary: *the permutation algorithm is Annex F's; the randomness stack is the ecosystem's; and only the pair is a wire format.* <!-- anchor: encoder.h:23-24; ISO 23634 Annex F -->

## Back to the code

`interleaveData` (interleave.c:26-36) realizes §5.1's algorithm exactly, step for step: iteration i has L = `length − i`; `pn_index(lcg64_temper(), L)` is step c; the swap partners are `pos` and `length − 1 − i` = L − 1 (step d). One draw per position, seeded once at entry — the draw-count invariant of §5.3. `deinterleaveData` (interleave.c:42-77) inverts without storing π: it replays the identical seeded shuffle on an index array `index[i] = i`, then scatters through it (`data[index[i]] = tmp_data[i]`) — inverse by construction, valid for any generator, which is itself a small design point: the inversion technique is PRNG-agnostic, so the entire wire dependence is concentrated in the forward draw stream. Reseeding at every operation entry (`setSeed(INTERLEAVE_SEED)`, interleave.c:28, 55) makes each permutation a pure function of (seed, N), independent of call history — the property that let the fork make the state `_Thread_local` with a *provable* output-neutrality argument (pseudo_random.c:3-9; concurrency posture in [../developers-manual/14-concurrency.md](../developers-manual/14-concurrency.md)). Failure modes (silent still-interleaved return on allocation failure) are register items, not theory: JC-T §8.7. <!-- anchor: interleave.c:26-77; pseudo_random.c:3-9 -->

## Exercises

**1 (guided).** For N = 4, compute the interleave permutation under (a) the codec's stack (seed 226759, `lcg64_temper`, `pn_index`) and (b) Annex F's rand() with the same numeric seed and modulo reduction, and confirm they differ. For (a) the first four draws are 3605414883, 3579144034, 3982877425, 87907282; for (b) the first four rand() outputs are 11845, 16399, 13855, 21175.

<details><summary>Answers</summary>

(a) Stage L = 4: `pn_index(3605414883, 4)` = floor(0.8394... × 4) = 3 — swap of position 3 with itself. L = 3: floor(0.8333... × 3) = 2, self-swap. L = 2: floor(0.9273... × 2) = 1, self-swap. L = 1: 0, self-swap. Every draw lands on its own stage index: the codec's N = 4 permutation from this seed is the **identity**.

(b) R = 11845 mod 4 = 1: swap positions 1, 3 → (b0, b3, b2, b1). R = 16399 mod 3 = 1: swap 1, 2 → (b0, b2, b3, b1). R = 13855 mod 2 = 1: self-swap. Final: (b0, b2, b3, b1) — a 3-cycle, not the identity.

Two "correct Fisher-Yates implementations of Annex F", two different wire formats. Note also what (a) illustrates about design vs realization: the *distribution* is uniform over S_4, and uniform distributions assign 1/24 to the identity too.
</details>

**2 (guided).** Using Annex F's PRNG with seed 1 (state width ≥ 32 bits) and modulo reduction, compute the first two swaps for N = 8.

<details><summary>Answer</summary>

Draw 1: next = 1 × 1103515245 + 12345 = 1103527590; output = floor(1103527590 / 65536) mod 32768 = 16838 mod 32768 = 16838. (16838 is the classic first output of the C reference rand() from seed 1 — a useful sanity check.) R = 16838 mod 8 = 6: swap b6 and b7. Draw 2: advance the state, output 5758; R = 5758 mod 7 = 4: swap b4 and b6 (which now holds the old b7).
</details>

**3 (open, with hint).** Prove that Annex F's sample routine produces the same output sequence whether `next` is a 32-bit or a 64-bit unsigned integer.

<details><summary>Hint</summary>

Show by induction that the 64-bit state is congruent to the 32-bit state mod 2^32 (multiplication and addition respect congruences), then observe that `(next/65536) % 32768` reads exactly bits 16-30 of `next`, all inside the low 32 bits. Conclude where the routine's reproducibility problems actually live (§5.3-§5.4).
</details>

**4 (open).** For an LCG mod 2^m with odd increment, prove the bound "bit k−1 has period at most 2^k" of §5.4, and exhibit the period of the lowest bit of Annex F's generator. Then explain in one paragraph why the codec's generator is immune to this particular defect and what the tempering step adds beyond taking high bits.

## Further reading

- ISO/IEC 23634:2022 clause 5.5 and Annex F.
- R. A. Fisher and F. Yates, *Statistical Tables for Biological, Agricultural and Medical Research*, Oliver & Boyd, 1938 — example 12, the original shuffle; R. Durstenfeld, "Algorithm 235: Random permutation", *CACM* 7(7), 1964 — the in-place O(N) form used here.
- D. E. Knuth, *The Art of Computer Programming*, vol. 2, *Seminumerical Algorithms*, 3rd ed., Addison-Wesley, 1997 — ch. 3: LCG theory, the spectral test, the MMIX multiplier, and the shuffle (algorithm P).
- M. Matsumoto and T. Nishimura, "Mersenne Twister: a 623-dimensionally equidistributed uniform pseudo-random number generator", *ACM TOMACS* 8(1), 1998 — the origin of the tempering constants.
- R. G. Gallager, *Low-Density Parity-Check Codes*, MIT Press, 1963 — the decoder whose error model §5.2 serves.
- Siblings: [03-ldpc-coding-theory.md](03-ldpc-coding-theory.md) (the same PRNG building matrices), [11-adversarial-channel.md](11-adversarial-channel.md) (interleaving in the damage game); implementation register: [../developers-manual/08-interleave-and-prng.md](../developers-manual/08-interleave-and-prng.md).
