# 6. `ldpc.c` — error correction

<!-- objective: A maintainer can explain matrix generation from the seeds, Gauss-Jordan systematization, and the hard-/soft-decision decode paths, and verify wire compatibility against the Annex D vector after any change. -->

**Responsibility.** `src/jabcode/ldpc.c` (1562 lines) implements LDPC encode and decode for both message data and master metadata: seeded parity-check matrix generation, Gauss-Jordan systematization, generator-matrix encoding, an iterative hard-decision decoder, and two soft-decision decoders. The fork adds a thread-local matrix memoization cache and SIMD syndrome primitives; neither changes a decoded bit. ISO ground: clause 5.4, Annexes B (informative decode algorithms), C (normative metadata ECC), D (worked example). <!-- anchor: ldpc.c:10-11; corpus-model.md §2.3 -->

## 6.1 Public surface

| Item | Signature / value | Notes |
|---|---|---|
| `LPDC_METADATA_SEED` | `#define LPDC_METADATA_SEED 	38545` | note the macro spelling `LPDC_` — transposed from LDPC in the source; preserved verbatim everywhere <!-- anchor: ldpc.h:17 --> |
| `LPDC_MESSAGE_SEED` | `#define LPDC_MESSAGE_SEED 	785465` | same transposed spelling <!-- anchor: ldpc.h:18 --> |
| `encodeLDPC` | `jab_data *encodeLDPC(jab_data* data, jab_int32* coderate_params)` | `coderate_params` = \{wc, wr\}; wr ≤ 0 selects the metadata path; caller owns the returned `jab_data` <!-- anchor: ldpc.h:26; ldpc.c:645 --> |
| `decodeLDPChd` | `jab_int32 decodeLDPChd(jab_byte* data, jab_int32 length, jab_int32 wc, jab_int32 wr)` | in-place hard-decision decode; returns decoded length or 0 <!-- anchor: ldpc.h:27; ldpc.c:906 --> |
| `decodeLDPC` | `jab_int32 decodeLDPC(jab_float* enc, jab_int32 length, jab_int32 wc, jab_int32 wr, jab_byte* dec)` | soft-decision decode; returns decoded length or 0 <!-- anchor: ldpc.h:28; ldpc.c:1376 --> |

Both seeds are interop-critical: they, together with the `lcg64_temper` PRNG and the `pn_index` reduction ([08-interleave-and-prng.md](08-interleave-and-prng.md)), fully determine the parity-check matrices. Any change produces matrices that no other conforming decoder can reproduce. The corpus model records both seed values as matching the project's ISO clause map. <!-- anchor: ldpc.h:17-18; corpus-model.md §3.6 -->

Corpus-model line drift, corrected here by reading the source: `createMatrixA` is defined at ldpc.c:172 (doc block from 165), `GaussJordan` at ldpc.c:235 (doc block from 225), `encodeLDPC` at ldpc.c:645 (not 640), soft `decodeLDPC` at ldpc.c:1376 (not 1368).

## 6.2 Call graph: which path serves what

All call sites in the tree, exhaustively:

| Caller | Call | Serves |
|---|---|---|
| `encodeMasterMetadata` | `encodeLDPC(partI, {2,-1})`, `encodeLDPC(partII, {2,-1})` | master metadata Parts I/II encode <!-- anchor: encoder.c:967-976 --> |
| `updateMasterMetadataPartII` | `encodeLDPC(partII, {2,-1})` | Part II re-encode after mask selection <!-- anchor: encoder.c:1034-1035 --> |
| `generateJABCode` | `encodeLDPC(enc->symbols[i].data, enc->symbols[i].wcwr)` | per-symbol message data encode <!-- anchor: encoder.c:2376 --> |
| `decodeMasterMetadataPartI` | `decodeLDPChd(part1, MASTER_METADATA_PART1_LENGTH, 2, 0)` | metadata Part I decode (6 bits) <!-- anchor: decoder.c:1434 --> |
| `decodeMasterMetadataPartII` | `decodeLDPChd(part2, MASTER_METADATA_PART2_LENGTH, 2, 0)` | metadata Part II decode (38 bits) <!-- anchor: decoder.c:1549 --> |
| `decodeSymbol` path | `decodeLDPChd((jab_byte*)raw_data->data, Pg, symbol->metadata.ecl.x, symbol->metadata.ecl.y)` | message data decode <!-- anchor: decoder.c:1979 --> |

**Finding:** both metadata and message data are decoded by the *hard-decision* decoder. The soft-decision `decodeLDPC` (ldpc.c:1376) and its inner `decodeMessageILL` (ldpc.c:1066) have **zero callers anywhere in `src/`** — verified by exhaustive search; only the declaration (ldpc.h:28) and definitions exist. Annex B, by contrast, assigns metadata to the soft "iterative Log Likelihood decoding algorithm" and message data to the "hard decision decoding algorithm". The implementation ships the recommended soft machinery as dead code and hard-decodes everything. Slave metadata is not separately LDPC-coded at all — it rides inside the host's LDPC-protected data stream (`decodeSlaveMetadata` parses already-decoded bits; encoder counterpart [04-encoder.md](04-encoder.md) §4.7). <!-- anchor: ISO 23634 Annex B; ldpc.c:1066, 1376; decoder.c:1161-1234 -->

## 6.3 Matrix generation from the seeds

### 6.3.1 `createMatrixA` (ldpc.c:172) — message parity-check matrix

Check count: `nb_pcb = capacity/2` if `wr < 4`, else `capacity/wr*wc`. Rows are packed MSB-first into 32-bit words (`offset = ceil(capacity/32)` words per row). Construction is Gallager-style: the first `capacity/wr` rows get consecutive runs of `wr` ones; each further block of `capacity/wr` rows is a column permutation of the first block, driven by the seeded PRNG:

```c
setSeed(LPDC_MESSAGE_SEED);
for (jab_int32 i=1; i<wc; i++)
{
    jab_int32 off_index=i*(capacity/wr);
    for (jab_int32 j=0;j<capacity;j++)
    {
        jab_int32 pos = pn_index(lcg64_temper(), capacity - j);
        ...
    }
}
```

<!-- anchor: ldpc.c:174-220, 207 -->

The permutation is the same in-place Fisher-Yates walk as the interleaver, applied to column indices ([08-interleave-and-prng.md](08-interleave-and-prng.md) §8.2). <!-- anchor: ldpc.c:211-218; interleave.c:29-35 -->

### 6.3.2 `createMetadataMatrixA` (ldpc.c:430) — metadata parity-check matrix

`nb_pcb = capacity/2` (rate 1/2). Row weight:

```c
setSeed(LPDC_METADATA_SEED);
jab_int32 nb_once=capacity*nb_pcb/(jab_float)wc+3;
nb_once=nb_once/nb_pcb;
```

then each of the `nb_pcb` rows sets `nb_once` ones at positions drawn by the same seeded Fisher-Yates draw. <!-- anchor: ldpc.c:450-464 -->

### 6.3.3 `GaussJordan` (ldpc.c:235) and `createGeneratorMatrix` (ldpc.c:476)

`GaussJordan(matrixA, wc, wr, capacity, &matrix_rank, encode)` performs GF(2) elimination with column bookkeeping (`column_arrangement`, `swap_col`, zero-line tracking), returning `matrix_rank = nb_pcb - zero_lines`. With `encode = 1` the systematized form is rearranged back into `matrixA` for generator construction; with `encode = 0` the decoder's check matrix is produced. Return 0 = success, 1 = out of memory. `createGeneratorMatrix(matrixA, capacity, Pn)` then assembles G from the systematic form — comment, verbatim: "remember matrixA is now A = \[I CT\], now use it and create G=\[CT / I\]" — with an identity block for the `Pn = capacity - rank` message bits. <!-- anchor: ldpc.c:235-422, 476-513 -->

## 6.4 `encodeLDPC` (ldpc.c:645)

Parameters and gross length, verbatim logic: `wc=coderate_params[0]; wr=coderate_params[1]; Pn=data->length;` then

```c
if(wr > 0)
{
    Pg=ceil((Pn*wr)/(jab_float)(wr-wc));
    Pg = wr * (ceil(Pg / (jab_float)wr));
}
else
    Pg=Pn*2;
```

<!-- anchor: ldpc.c:649-658 -->

wr ≤ 0 is the metadata path: rate 1/2, matrix from `createMetadataMatrixA` (the encoder always passes `{2, -1}` for master metadata — [04-encoder.md](04-encoder.md) §4.5). For speed, the stream is split into sub-blocks: the smallest `nb_sub_blocks` with `Pg / nb_sub_blocks < 2700`; `Pg_sub_block` is rounded down to a multiple of wr and a trailing partial block (if any) is encoded with its own, smaller generator matrix. Encoding is the bit-serial product G × message into `ecc_encoded_data` (one bit per output byte — the codec's unpacked-bit convention). Systematic layout: parity occupies the first `rank` positions of each sub-block, message bits follow — the decode step copies `data[i + matrix_rank]` back to the front. <!-- anchor: ldpc.c:664-756, 1042-1047 -->

## 6.5 Hard-decision decode: `decodeLDPChd` (ldpc.c:906) and `decodeMessage` (ldpc.c:770)

`decodeLDPChd` recomputes geometry from `(length, wc, wr)`:

```c
jab_int32 max_iter=25;
...
if(wr > 3)
{
    Pg = wr * (length / wr);
    Pn = Pg * (wr - wc) / wr;
}
else
{
    Pg=length;
    Pn=length/2;
    wc=2;
    if(Pn>36)
        wc=3;
}
```

<!-- anchor: ldpc.c:909-923 -->

`wr ≤ 3` is the metadata path; the caller's wc is overridden. Per sub-block: syndrome test first (`ldpc_syndrome_ok_bytes`; early exit on the first violated check), then `decodeMessage` if needed, then a second syndrome test — failure reports `"Too many errors in message. LDPC decoding failed."` and returns 0. <!-- anchor: ldpc.c:962-1048 -->

`decodeMessage` is Gallager bit-flipping: each iteration accumulates, for every failed check, a per-bit violation count `max_val[k]`; then

- **length ≥ 36:** every bit attaining the maximum count is flipped ("flips those bits with the maximum λ\[l\]v > 0 in each iteration step" — matches the Annex B hard-decision description);
- **length < 36:** exactly one of the maximal bits is flipped, chosen by a PRNG draw `pn_index(lcg64_temper(), counter)`. The fork comment, verbatim: "WS-4.5.3: was rand() — unseeded stdlib PRNG. Replaced with project's deterministic LCG (lcg64_temper) so the LDPC bit-flip tiebreak is reproducible across processes." Note this draw is *not* re-seeded — it consumes whatever thread-local PRNG state is current, which depends on whether the preceding matrix build was a cache hit (§6.9). Decode-side only; no wire effect. <!-- anchor: ldpc.c:814-887, 855-867; ISO 23634 Annex B -->

Bits flipped in one iteration are excluded from the next (`prev_index`). Termination: clean syndrome or `max_iter` (25) iterations.

**Iteration caps vs ISO.** Annex B: "It is recommended to use L = 25" for both decoders. Source: `max_iter=25` in `decodeLDPChd` (ldpc.c:909) and in soft `decodeLDPC` (ldpc.c:1379). Exact match, hard-coded (not configurable). <!-- anchor: ISO 23634 Annex B; ldpc.c:909, 1379 -->

## 6.6 Soft-decision decode: `decodeLDPC` (ldpc.c:1376) — present, unused

`decodeLDPC` mirrors `decodeLDPChd`'s block structure over per-bit reliability values `enc[]`, calling `decodeMessageBP` (ldpc.c:1209), a belief-propagation variant using `log((1+p)/(1-p))` check-node updates. `decodeMessageILL` (ldpc.c:1066) is the tanh-product "Iterative Log Likelihood" decoder matching Annex B's metadata algorithm by name — it is called by nothing. Both soft decoders initialize channel LLRs as `lambda[i] = 2.0*enc[start_pos+i]/var` with the variance estimated from the received block, and make tentative decisions `if(lambda[i]<0) dec[...]=1; else dec[...]=0;`. The Annex B extract records the tentative decision as "λ>0→1"; the source assigns 1 on λ<0. Whether this is a real sign-convention divergence or an artifact of reliability-value definition in the lossy spec extraction cannot be resolved from this corpus — flagged, not resolved. <!-- anchor: ldpc.c:1066-1193, 1209-1365, 1119-1125, 1162-1165, 1334-1337; ISO 23634 Annex B -->

## 6.7 ECC parameter tables (`encoder.h`) and Table 20

```c
static const jab_int32 ecclevel2wcwr[10][2] = {{3, 8}, {3, 7}, {4, 9}, {3, 6}, {4, 7}, {4, 6}, {3, 4}, {4, 5}, {5, 6}, {6, 7}};
```

Header doc, verbatim: "Per ISO/IEC 23634:2022 Table 20. ECC levels run 1..10 (default 3). Row i (0-based) holds (wc, wr) for level i+1; there is no level-0 row (an unset/0 level normalizes to DEFAULT_ECC_LEVEL — see wcwr_for_level())." `src/jabcode/CONFORMANCE_PROFILE.md` additionally records: "`ecclevel2wcwr` is identical across ISO Table 20 / BSI Table 18." The pairs agree with Table 20; access is exclusively through `wcwr_for_level`. <!-- anchor: encoder.h:228-244; src/jabcode/CONFORMANCE_PROFILE.md:22-25 -->

```c
static const jab_float ecclevel2coderate[11] = {0.55f, 0.63f, 0.57f, 0.55f, 0.50f, 0.43f, 0.34f, 0.25f, 0.20f, 0.17f, 0.14f};
```

**Indexing asymmetry, stated precisely:** `ecclevel2coderate` has 11 entries indexed by *level directly* (levels 1..10 at indices 1..10), with index 0 holding 0.55 — the default level 3's rate — as the unset-level placeholder. `ecclevel2wcwr` has 10 rows indexed by *level − 1*. The two tables therefore disagree by one on their index origin; `wcwr_for_level` papers over it for the wcwr table only. Every entry equals R = 1 − wc/wr of its level's pair rounded to two decimals (Annex B: "R = 1−wc/wr"). Exhaustive search shows **no code in `src/` reads `ecclevel2coderate`** — it is documentation-only data; the live rate computation is `(capacity/wr)*wr - (capacity/wr)*wc` at the call sites. <!-- anchor: encoder.h:226, 234, 241-244; ISO 23634 Annex B; encoder.c:1901, 2034 -->

`detector_synthetic.c` carries its own private copy of `ecclevel2wcwr`/`wcwr_for_level` (detector_synthetic.c:32-48) — a duplication to keep in sync when Table 20 handling changes. <!-- anchor: detector_synthetic.c:31-48 -->

## 6.8 Annex C (normative metadata ECC) vs the source, rule by rule

| Annex C rule (extract) | Source realization | Verdict |
|---|---|---|
| "1) Set wc = 2, if the metadata length is shorter than 36 bits, otherwise wc = 3." | Encode: caller hard-codes `wcwr = {2, -1}`; master metadata nets are 3 and 19 bits — both < 36, so wc = 2 is correct for everything actually encoded. Decode: `wc=2; if(Pn>36) wc=3;` | Match for all reachable inputs. Boundary divergence at exactly 36 net bits: the rule as extracted switches to wc = 3 *at* 36 ("shorter than 36" → wc 2), the source switches only *above* 36 (`Pn>36`). Unreachable in this tree (no 36-bit metadata exists). <!-- anchor: ISO 23634 Annex C; encoder.c:967; ldpc.c:919-922 --> |
| "2) Set the number of '1' in each row of matrix H to: C × K / wc + 3 / K \[floor/ceiling brackets lost in extraction\]." | `nb_once = capacity*nb_pcb/(jab_float)wc + 3;` then `nb_once = nb_once/nb_pcb;` — i.e. trunc((trunc(C·K/wc) + 3)/K) with C = capacity, K = nb_pcb. | Same formula shape; the extraction lost the floor/ceiling brackets, so exact rounding agreement cannot be certified from the extract. The source's truncations are the de-facto wire behaviour. <!-- anchor: ISO 23634 Annex C; ldpc.c:451-452 --> |
| "3) The '1's in each row shall be equally distributed. Matrix H is obtained by using the interleaving algorithm listed in Annex F to specify the position of the '1's in each row." | Positions drawn by the seeded Fisher-Yates draw (`setSeed(LPDC_METADATA_SEED)`; `pn_index(lcg64_temper(), capacity-j)`) — structurally the Annex F permutation, but driven by the LCG64 PRNG, not Annex F's C89 `rand()` (the central divergence analysed in [08-interleave-and-prng.md](08-interleave-and-prng.md) §8.3). | Algorithm matches; PRNG differs from the Annex F sample routine. <!-- anchor: ISO 23634 Annex C, Annex F; ldpc.c:450-463 --> |
| "c = m ⊗ G over GF(2)" | `createGeneratorMatrix` + the G × message loop in `encodeLDPC`. | Match. <!-- anchor: ISO 23634 Annex C; ldpc.c:476-513, 709-723 --> |

## 6.9 Fork additions (performance; bit-exact by design)

- **Matrix memoization** (ldpc.c:515-637): `createMatrixA` + `GaussJordan` (+ `createGeneratorMatrix`) are deterministic in (wc, wr, capacity), so results are cached — `LDPC_CACHE_SIZE 32` entries, separate `_Thread_local` encode/decode caches, LRU eviction by a thread-local clock, callers receive fresh malloc'd copies ("callers keep their existing ownership/free semantics and can never corrupt the cache"). Only the dense path (`wr > 3`) is cached; metadata matrices are rebuilt each time. Motivation, verbatim: "GaussJordan ... dominates codec time for colour modes with color_number > 8 (the 8->16 throughput cliff)." <!-- anchor: ldpc.c:515-637 -->
- **SIMD syndrome/decode primitives** (ldpc.c:25-163): GF(2) row-dot-parity via word-at-a-time popcount with an AVX2 path (host-dispatched through a `_Thread_local`-memoised `__builtin_cpu_supports("avx2")` probe) and a scalar fallback; opt-out with `-DJAB_LDPC_NO_SIMD`. Bit-exactness argument quoted in-source: rows are MSB-first with zero padding, "so if the received vector is packed the same way (pad bits zero) then parity(sum_i row_i&data_i) == ( sum_words popcount(row_word & data_word) ) & 1". <!-- anchor: ldpc.c:25-58, 40-45, 108-122 -->
- **Deterministic tie-break** (WS-4.5.3): stdlib `rand()` replaced by `lcg64_temper` in `decodeMessage` (§6.5). <!-- anchor: ldpc.c:855-865 -->

## 6.10 Invariants

- One bit per byte throughout: `data[i] & 1` is the only meaningful content of every codeword byte. <!-- anchor: ldpc.c:47-51, 718 -->
- Wire compatibility = \{`LPDC_MESSAGE_SEED`, `LPDC_METADATA_SEED`, `lcg64_temper`, `pn_index`, the createMatrixA/createMetadataMatrixA fill orders, Gauss-Jordan column bookkeeping\}. Changing any element changes the code ensemble and breaks cross-implementation decode. Regression: byte-identical round trips (`test-roundtrip`, Annex D message — [04-encoder.md](04-encoder.md) §4.4.3). <!-- anchor: ldpc.h:17-18; ldpc.c:207, 450; Makefile:158 -->
- Message wr is always ≥ 4 in practice (Table 20 minimum and `getOptimalECC`'s `j ≥ k+1 ≥ 4`), so the `wr < 4` branches of `createMatrixA`/`GaussJordan` and the `wr ∈ (0,3]` gap between `encodeLDPC`'s `wr > 0` test and `decodeLDPChd`'s `wr > 3` test are unreachable for real streams. <!-- anchor: encoder.h:234; encoder.c:700-703; ldpc.c:652, 911 -->
- `max_iter` is 25 in both decoders — equal to Annex B's recommended L. <!-- anchor: ldpc.c:909, 1379; ISO 23634 Annex B -->

## 6.11 Failure modes

- Out of memory at any stage: `reportError(...)`, return NULL/0/1 per function contract; `decodeMessage` returns 0 = fatal (distinct from "did not converge", which is `*is_correct = 0` with return 1). <!-- anchor: ldpc.c:184-194, 770-777, 895 -->
- Uncorrectable block: `"Too many errors in message. LDPC decoding failed."` → `decodeLDPChd` returns 0; the soft path returns 0 silently (its equivalent `reportError` lines are commented out). <!-- anchor: ldpc.c:1006, 1036, 1497, 1546 -->
- `ldpc_syndrome_ok_bytes` returns −1 on allocation failure "so the caller can fall back to never claiming a bad codeword is correct" — the `!= 1` tests at the call sites treat it as a failed syndrome. <!-- anchor: ldpc.c:149-152, 985, 1018 -->

## 6.12 Extension points

Iteration count, sub-block threshold (2700), and cache size (`LDPC_CACHE_SIZE 32`) are compile-time constants — natural tuning knobs that do not affect the wire format (decode-side) except the sub-block threshold, which *does* shape the encode layout and must never change. Soft-decision decode could be wired into the metadata path (the Annex B recommendation) without wire impact, since decoders are receiver-local. <!-- anchor: ldpc.c:533, 666-673, 909 -->

## 6.13 Performance notes

Decode hot path is `decodeMessage`'s syndrome loops — now word-parallel; encode hot path was the per-call matrix rebuild — now cached (§6.9). Benchmarks: `bench`, `bench-cascade` (the "regression guard for high-colour cascade"), `profile` stage `JAB_STAGE_LDPC` — [12-benchmark-estate.md](12-benchmark-estate.md). <!-- anchor: Makefile:79, 103, 117; decoder.c:1978-1980 -->

## 6.14 Known defects

- The Annex B-recommended soft-decision metadata decode is not wired up; `decodeLDPC`/`decodeMessageILL`/`decodeMessageBP` are dead code (§6.2, §6.6).
- The λ sign-convention question against the Annex B extract is unresolved (§6.6).
- The metadata wc boundary at exactly 36 net bits diverges from the extracted Annex C rule 1 (unreachable today; a trap for anyone adding a 36-bit metadata structure) (§6.8).
- `decodeLDPChd`'s partial-block path frees `matrixA1` but not the outer cached `matrixA` on some early-return failures inside the partial-block branch (returns 0 at ldpc.c:973, 980, 996, 1008 without freeing `matrixA`). <!-- anchor: ldpc.c:962-1011, 1049 -->

Coding theory (Gallager ensembles, dmin, "detectable errors, correctable = (dmin−1)/2"): Special Topics (JC-S), forthcoming. Capacity/ECC selection context: [04-encoder.md](04-encoder.md) §4.3; operator-level ECC guidance: [../operators-manual/02-capacity-size-robustness.md](../operators-manual/02-capacity-size-robustness.md).
