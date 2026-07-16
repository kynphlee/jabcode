# 8. `interleave.c` and `pseudo_random.c` — permutation machinery

<!-- objective: A maintainer can state the exact PRNG contract and the INTERLEAVE_SEED Fisher-Yates interleave, and explain why any deviation breaks cross-implementation decode and what the pn_index clamp preserves. -->

**Responsibility.** `src/jabcode/interleave.c` (77 lines) implements clause 5.5 data interleaving; `src/jabcode/pseudo_random.c` (30 lines) + `pseudo_random.h` (40 lines) provide the deterministic PRNG that drives it *and* the LDPC matrix generation ([06-ldpc.md](06-ldpc.md) §6.3). These are the smallest files in the codec and the most interop-critical: every bit position on the wire passes through their permutations. ISO ground: 5.5, Annex F (informative). <!-- anchor: interleave.c:10-11; corpus-model.md §2.3 -->

## 8.1 Public surface

| Item | Signature / value | Notes |
|---|---|---|
| `INTERLEAVE_SEED` | `#define INTERLEAVE_SEED 226759` | file-local to interleave.c <!-- anchor: interleave.c:20 --> |
| `interleaveData` | `void interleaveData(jab_data* data)` | in-place; exported via `encoder.h:294` <!-- anchor: interleave.c:26 --> |
| `deinterleaveData` | `void deinterleaveData(jab_data* data)` | in-place; replays the permutation on an index array <!-- anchor: interleave.c:42 --> |
| `setSeed` | `void setSeed(uint64_t seed)` | assigns the thread-local LCG state <!-- anchor: pseudo_random.h:10; pseudo_random.c:27-30 --> |
| `lcg64_temper` | `uint32_t lcg64_temper()` | one draw: advance LCG64, temper the high 32 bits <!-- anchor: pseudo_random.h:11; pseudo_random.c:21-25 --> |
| `pn_index` | `static inline int32_t pn_index(uint32_t r, int32_t range)` | draw → index in \[0, range−1\]; header-inline so it needs no library link <!-- anchor: pseudo_random.h:32-38 --> |

The PRNG core, complete and verbatim:

```c
static _Thread_local uint64_t lcg64_seed = 42;

uint32_t temper(uint32_t x)
{
    x ^= x>>11;
    x ^= x<<7 & 0x9D2C5680;
    x ^= x<<15 & 0xEFC60000;
    x ^= x>>18;
    return x;
}

uint32_t lcg64_temper()
{
    lcg64_seed = 6364136223846793005ULL * lcg64_seed + 1;
    return temper(lcg64_seed >> 32);
}
```

<!-- anchor: pseudo_random.c:10-25 -->

The reduction, verbatim:

```c
static inline int32_t pn_index(uint32_t r, int32_t range)
{
    int32_t pos = (int32_t)( (float)r / (float)UINT32_MAX * (float)range );
    if (pos >= range)
        pos = range - 1;
    return pos;
}
```

<!-- anchor: pseudo_random.h:32-38 -->

The initial state 42 is only the pre-first-use value; every codec consumer reseeds before drawing (§8.4).

## 8.2 `interleaveData` / `deinterleaveData` vs the Annex F permutation steps

Annex F's permutation, as extracted: "a) Give an initial seed... b) Set the variable L = N. c) Generate a random number R between 0 and L-1. d) Swap the bit bR at index R and the bit bL-1 at index L-1. e) Update L = L-1. f) Repeat the steps 3-5 until L = 0." Source:

```c
void interleaveData(jab_data* data)
{
    setSeed(INTERLEAVE_SEED);
    for (jab_int32 i=0; i<data->length; i++)
    {
        jab_int32 pos = pn_index(lcg64_temper(), data->length - i);
        jab_char  tmp = data->data[data->length - 1 -i];
        data->data[data->length - 1 - i] = data->data[pos];
        data->data[pos] = tmp;
    }
}
```

<!-- anchor: interleave.c:26-36; ISO 23634 Annex F -->

Step-for-step: iteration i has L = `data->length - i` (steps b/e/f); `pos` is R in \[0, L−1\] (step c); the swap exchanges indices `pos` and `L−1` = `data->length - 1 - i` (step d); the loop ends when L reaches 0 (step f). **The permutation algorithm is exactly Annex F's Fisher-Yates.** What Annex F leaves open or specifies differently is the randomness (§8.3): the numeric seed (Annex F names none), the PRNG, and the draw→\[0, L−1\] reduction.

`deinterleaveData` inverts without a stored permutation: it fills `index[i] = i`, replays the identical seeded shuffle on the index array, then scatters a copy of the data back through it (`data->data[index[i]] = tmp_data[i]`). Inverse by construction against the same seed and PRNG. <!-- anchor: interleave.c:42-77 -->

Call sites — one each way, whole-symbol scope, message stream only (metadata is never interleaved): encode `interleaveData(ecc_encoded_data)` after LDPC (encoder.c:2387); decode `deinterleaveData(raw_data)` after demask/bit-expansion and before `decodeLDPChd` (decoder.c:1967). <!-- anchor: encoder.c:2376-2390; decoder.c:1964-1979 -->

## 8.3 Divergence analysis: source PRNG vs ISO Annex F

This is the load-bearing interop question of the module. The two randomness stacks:

| Aspect | ISO Annex F (informative; extract) | Source |
|---|---|---|
| Generator | C routine per ISO/IEC 9899: `next = next * 1103515245 + 12345; return (unsigned int)(next/65536) % 32768;` | `lcg64_seed = 6364136223846793005ULL * lcg64_seed + 1;` then `temper(lcg64_seed >> 32)` <!-- anchor: ISO 23634 Annex F; pseudo_random.c:23-24 --> |
| Output width | 15 bits, "RAND_MAX 32767" | 32 bits (tempered high word of a 64-bit state) <!-- anchor: ISO 23634 Annex F; pseudo_random.c:12-24 --> |
| Temper constants | none | `0x9D2C5680`, `0xEFC60000` <!-- anchor: pseudo_random.c:15-16 --> |
| Seeding | "seed via srand"; **Annex F does NOT specify a numeric seed value** | `setSeed(INTERLEAVE_SEED 226759)` / `setSeed(LPDC_MESSAGE_SEED 785465)` / `setSeed(LPDC_METADATA_SEED 38545)` per operation <!-- anchor: ISO 23634 Annex F; interleave.c:20, 28, 55; ldpc.c:207, 450; ldpc.h:17-18 --> |
| Draw → \[0, L−1\] | "Generate a random number R between 0 and L-1" (no reduction formula given) | `pn_index`: single-precision scaling `r / UINT32_MAX * range`, truncated, clamped (§8.5) <!-- anchor: ISO 23634 Annex F; pseudo_random.h:32-38 --> |

The sequences are unrelated: an implementation that follows Annex F's sample `rand()` literally produces a different permutation and different LDPC matrices from this codec, for every payload.

**What the source actually claims, verbatim.** Two in-tree statements define the intended interop posture:

1. `pseudo_random.h` (the `pn_index` doc block): "The historical float-scaling mapping is preserved bit-for-bit for every draw that already lands in range, so the permutation -- and thus wire-level interoperability with the Fraunhofer reference / ISO 23634 ecosystem -- is unchanged." <!-- anchor: pseudo_random.h:18-21 -->
2. `encoder.h` (the palette-profile comment): "(PRNG is NOT an axis: ISO Annex F is informative; both profiles keep lcg64_temper.)" <!-- anchor: encoder.h:23-24 -->

So the compatibility target this source names is the **Fraunhofer reference implementation's sequence** — `lcg64_temper` with the three fixed seeds — with Annex F classified as informative sample code rather than a conformance requirement. Supporting provenance: the corpus model marks `pseudo_random.c` origin "U (made `_Thread_local`)" — the LCG64+temper core is inherited upstream code; the fork changed only its storage class and the reduction clamp. <!-- anchor: corpus-model.md §2.3 -->

**What consumers actually depend on.** Three seeded draw streams, and nothing else, are wire-relevant:

| Stream | Seed | Consumer | Wire effect |
|---|---|---|---|
| interleave | 226759 | `interleaveData`/`deinterleaveData` | bit positions of the entire message stream (5.5) <!-- anchor: interleave.c:20-77 --> |
| message matrix | 785465 | `createMatrixA` | message parity bits (5.4) <!-- anchor: ldpc.c:207 --> |
| metadata matrix | 38545 | `createMetadataMatrixA` | master metadata parity bits (Annex C step 3 analogue) <!-- anchor: ldpc.c:450 --> |

Any change to the multiplier, increment, temper constants, seed values, draw order, or `pn_index`'s in-range mapping changes all three streams simultaneously; a foreign decoder then fails at LDPC or produces garbage after de-interleaving. Conversely, decode-only draws (the `decodeMessage` tie-break, [06-ldpc.md](06-ldpc.md) §6.5) are receiver-local and wire-irrelevant.

**Explicit open interop question (not answerable from this corpus).** Whether the upstream Fraunhofer reference itself ever matched Annex F's `rand()` routine — i.e. whether Annex F documents a historical variant, an idealized routine, or something no shipping implementation uses — is NOT FOUND in this tree; the source asserts ecosystem compatibility with the reference, not with Annex F. Equally open: whether any independent ISO 23634 implementation follows Annex F literally, which would make it wire-incompatible with this codec. Resolving either requires the upstream repository history or the full standard text, neither of which is in the corpus. <!-- anchor: pseudo_random.h:18-26; encoder.h:23-24; corpus-model.md §2.3 -->

## 8.4 Per-operation reseeding and the `_Thread_local` change

The reentrancy design is documented at the state variable, verbatim:

> "Thread-safety (codec reentrancy): the LCG state is per-operation, not cross-call — every consumer re-seeds it to a fixed constant via setSeed() immediately before use (interleave.c INTERLEAVE\_SEED; ldpc.c LPDC\_MESSAGE\_SEED / LPDC\_METADATA\_SEED). Making it \_Thread\_local gives each worker thread its own deterministic sequence, so concurrent encode/decode no longer race on this global AND single-threaded output stays byte-identical. Mirrors the established #91 LDPC-cache \_Thread\_local pattern (ldpc.c)."

<!-- anchor: pseudo_random.c:3-9 -->

Reseed sites, exhaustively: `interleaveData` (interleave.c:28), `deinterleaveData` (interleave.c:55), `createMatrixA` (ldpc.c:207), `createMetadataMatrixA` (ldpc.c:450). One consumer draws **without** reseeding: `decodeMessage`'s small-block bit-flip tie-break (ldpc.c:864) — deterministic per thread but dependent on prior PRNG state (e.g. whether the preceding matrix build hit the LDPC cache); decode-side only. <!-- anchor: interleave.c:28, 55; ldpc.c:207, 450, 864 -->

Because every wire-relevant stream is reseeded at its point of use, the `_Thread_local` change is *provably* output-neutral for single-threaded use and race-free for concurrent use — this is the fork's wire-compatibility argument for the reentrancy fix. The TSan-guarded round trip `test-concurrent` (`Makefile:182`) is the regression; the concurrency posture as a whole is [14-concurrency.md](14-concurrency.md). <!-- anchor: pseudo_random.c:3-10; corpus-model.md §2.2 -->

## 8.5 The `pn_index` clamp and its regression guard

The historical reduction was the inline float expression now wrapped by `pn_index`. Its defect and the fix's exact scope, from the header doc, verbatim:

> "Only the single float-rounding edge is guarded: a uint32\_t within ~128 of UINT32\_MAX rounds to 1.0f in single precision (24-bit mantissa), so the truncated product equals `range` -- one past the valid \[0, range-1\] and, on the first loop iteration, a latent heap out-of-bounds access. The clamp touches only that edge; the reference's behavior there is undefined, so no defined interoperability is lost."

<!-- anchor: pseudo_random.h:13-26 -->

So the clamp preserves: bit-for-bit identity with the legacy mapping for every draw the legacy code mapped in range (the entire defined permutation), while converting the out-of-range edge from heap OOB into `range - 1`. Guard: `make test-pn` builds and runs `test/test_pn_index.c` self-contained ("pn\_index is a static inline in pseudo\_random.h, so no libjabcode link is needed" — Makefile comment). The test pins two invariants — never out of \[0, range−1\]; bit-identical to the verbatim legacy mapping for all in-range draws — across a dense sweep of the top 4096 draws (the rounding edge) and a prime-stride sweep of the full 32-bit space, for ranges \{2, 5, 17, 256, 1000, 4096, 65536, 1000000\}, and additionally asserts the legacy mapping really did overflow. <!-- anchor: Makefile:131-135; test/test_pn_index.c:9-14, 29, 36-56 -->

## 8.6 Invariants

- Single-threaded encode/decode output is byte-identical to the pre-`_Thread_local` code; concurrent threads see independent, identically-seeded sequences. <!-- anchor: pseudo_random.c:3-10 -->
- `pn_index(r, range)` ∈ \[0, range−1\] for all r, range > 0. <!-- anchor: pseudo_random.h:29-38 -->
- `deinterleaveData(interleaveData(x)) = x` for any stream, given the shared seed and PRNG. <!-- anchor: interleave.c:26-77 -->
- Interleaving consumes exactly `data->length` draws; matrix generation consumes exactly its fill-loop draw count — draw-count changes are wire-format changes. <!-- anchor: interleave.c:29-35; ldpc.c:208-219, 454-463 -->

## 8.7 Failure modes

- `deinterleaveData`: allocation failure of `index` or `tmp_data` reports (`"Memory allocation for index buffer in deinterleaver failed"` / `"...temporary buffer..."`) and returns with the data **still interleaved** — the caller is not told; and the `tmp_data` failure path leaks `index`. <!-- anchor: interleave.c:44-49, 64-69 -->
- `interleaveData` cannot fail (no allocation).
- `pn_index` with `range <= 0` is documented out of contract ("range exclusive upper bound (must be > 0)"). <!-- anchor: pseudo_random.h:29 -->

## 8.8 Extension points

None by design: this module is wire format. The only sanctioned change class is receiver-local (decode-side draws) or provably output-neutral refactors in the `pn_index`/`_Thread_local` mould, each with a pinned regression (`test-pn`, `test-concurrent`, `test-roundtrip`). The in-source profile note explicitly excludes the PRNG from the conformance-profile axis (§8.3). <!-- anchor: encoder.h:23-24; Makefile:133, 158, 182 -->

## 8.9 Performance notes

Costs are linear and negligible against LDPC: one draw + one swap per stream byte, plus in `deinterleaveData` two length-sized heap buffers per call. Stage profiling buckets de-interleaving under `JAB_STAGE_DEINTERLEAVE` (`profile` target, [12-benchmark-estate.md](12-benchmark-estate.md)). <!-- anchor: decoder.c:1966-1968; Makefile:117 -->

## 8.10 Known defects

- The `deinterleaveData` silent-failure-plus-leak path (§8.7).
- `temper` and `lcg64_temper` are exported without a namespace prefix — link-visible generic names in a C library (collision risk for embedders); noted, not fixed. <!-- anchor: pseudo_random.h:10-11 -->
- The Annex F relationship is a documented divergence, not a defect in the source's own terms (§8.3); it becomes a defect only under a conformance regime that reads Annex F as binding — currently NOT the source's reading. <!-- anchor: encoder.h:23-24 -->

**Divergence-analysis conclusion.** The source implements Annex F's Fisher-Yates permutation exactly, but drives it with the inherited Fraunhofer LCG64-with-tempering PRNG (multiplier `6364136223846793005ULL`, increment 1, temper constants `0x9D2C5680`/`0xEFC60000`) and fixed seeds 226759/785465/38545, and explicitly targets wire compatibility with the reference-implementation ecosystem while classifying Annex F's C89 `rand()` routine as informative. Whether the reference itself, or any independent implementation, ever matched Annex F literally is an open interop question this corpus cannot answer.

PRNG and permutation theory (LCG spectra, tempering, Fisher-Yates uniformity): Special Topics (JC-S), forthcoming. Consumers: [06-ldpc.md](06-ldpc.md), [04-encoder.md](04-encoder.md); pipeline position: [02-codec-pipeline.md](02-codec-pipeline.md).
