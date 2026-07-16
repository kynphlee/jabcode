# 4. `encoder.c` — symbol encoding

<!-- objective: A maintainer can analyze the encode path — capacity computation, optimal-ECC selection, data encoding, metadata assembly, matrix construction, cascade assignment — and modify one stage without breaking wire compatibility. -->

**Responsibility.** `src/jabcode/encoder.c` (2453 lines) owns the entire encode pipeline behind `generateJABCode`: input-mode analysis, data encoding, capacity and ECC selection, master/slave metadata assembly, matrix construction with pattern/palette/metadata placement, cascade assignment, and bitmap rendering. It delegates error correction to `ldpc.c` ([06-ldpc.md](06-ldpc.md)), permutation to `interleave.c` ([08-interleave-and-prng.md](08-interleave-and-prng.md)) and mask selection to `mask.c` ([07-mask.md](07-mask.md)). Its tables live in `encoder.h`. <!-- anchor: encoder.c:10-11; corpus-model.md §2.3 -->

Task-level encoding (CLI flags, parameter choice) is JC-U territory: [../operators-manual/07-encoding-with-jabcodewriter.md](../operators-manual/07-encoding-with-jabcodewriter.md), [../operators-manual/10-choosing-parameters.md](../operators-manual/10-choosing-parameters.md).

## 4.1 Entry point and ISO stage order

**Public surface (this module's exported entry points).**

| Item | Signature / value | Notes |
|---|---|---|
| `createEncode` | `jab_encode* createEncode(jab_int32 color_number, jab_int32 symbol_number)` | caller owns result; free with `destroyEncode` <!-- anchor: encoder.c:182 --> |
| `destroyEncode` | `void destroyEncode(jab_encode* enc)` | frees palette, versions, ecc levels, positions, bitmap, per-symbol data/data\_map/metadata/matrix <!-- anchor: encoder.c:261-280 --> |
| `generateJABCode` | `jab_int32 generateJABCode(jab_encode* enc, jab_data* data)` | doc comment, verbatim: "0:success \| 1: out of memory \| 2:no input data \| 3:incorrect symbol version or position \| 4: input data too long" — **0 on success**, the inversion of `JAB_SUCCESS 1` (see [03-public-surface-jabcode-h.md](03-public-surface-jabcode-h.md)) <!-- anchor: encoder.c:2305-2307; jabcode.h:47 --> |
| `genColorPalette` | `void genColorPalette(jab_int32 color_number, jab_byte* palette)` | exported via `encoder.h:301` so the decoder can synthesize procedural palettes <!-- anchor: encoder.c:29; encoder.h:298-301 --> |
| `reportError` | `void reportError(jab_char* message)` | `printf("JABCode Error: %s\n", message)` <!-- anchor: encoder.c:2450-2453 --> |

`generateJABCode` executes, in order: `InitSymbols` → `analyzeInputData` → `encodeData` → `setMasterSymbolVersion` (single-symbol auto-size only) → `setSlaveMetadata` → `fitDataIntoSymbols` → `encodeMasterMetadata` (non-default mode) → per symbol \{`encodeLDPC` → `interleaveData` → `createMatrix`\} → `getCodePara` → mask (`maskSymbols` with reference 7 in default mode, else `maskCode` + `updateMasterMetadataPartII` + `placeMasterMetadataPartII`) → `createBitmap`. <!-- anchor: encoder.c:2307-2444 -->

Mapping to the ISO/IEC 23634 encode procedure (clause 5.1 lists eight steps):

| ISO 5.1 step | Spec wording (extract) | Source realization |
|---|---|---|
| 1 Data analysis | "Analyse the input data to identify the most efficient encoding modes" | `analyzeInputData` <!-- anchor: ISO 23634 5.1; encoder.c:288 --> |
| 2 Data encoding (5.3) | — | `encodeData` <!-- anchor: ISO 23634 5.3; encoder.c:723 --> |
| 3 Error correction coding (5.4) | "Encode the binary stream using LDPC... append the parity data to the end" | `encodeLDPC` call <!-- anchor: ISO 23634 5.4; encoder.c:2376 --> |
| 4 Data interleaving (5.5) | "Interleave the encoded data in each symbol and add padding bits if necessary" | `interleaveData` call; padding bits are written later, during data placement <!-- anchor: ISO 23634 5.5; encoder.c:2387, 1550-1565 --> |
| 5 Metadata module reservation (optional, 4.4.4) | — | folded into `createMatrix`: metadata/palette modules zero their `data_map` cells as they are placed <!-- anchor: ISO 23634 4.4.4; encoder.c:1438-1494 --> |
| 6 Data module placement (5.7) | "Place the finder patterns, the alignment patterns and the colour palettes... then place the data modules... skipping the reserved modules for metadata" | `createMatrix` <!-- anchor: ISO 23634 5.7; encoder.c:1171 --> |
| 7 Data masking (5.8) | "Apply every available data mask pattern... select the masking pattern, which results in the most balanced module colour distribution and minimizes the occurrence of undesirable patterns" | `maskCode` ([07-mask.md](07-mask.md)) <!-- anchor: ISO 23634 5.8; encoder.c:2413 --> |
| 8 Metadata generation and placement (optional, 4.4.3-4.4.4) | — | split: `encodeMasterMetadata` runs *before* steps 3-7 with the default MSK value; after mask selection the MSK field is re-encoded and re-placed (`updateMasterMetadataPartII`, `placeMasterMetadataPartII`) <!-- anchor: ISO 23634 4.4.3-4.4.4; encoder.c:2363-2370, 2428-2430 --> |

## 4.2 Lifecycle: `createEncode` / `destroyEncode`

`createEncode` normalizes invalid arguments instead of failing: any `color_number` outside \{2, 4, 8, 16, 32, 64, 128, 256\} falls back to `DEFAULT_COLOR_NUMBER` (8); `symbol_number` outside 1..`MAX_SYMBOL_NUMBER` falls back to `DEFAULT_SYMBOL_NUMBER` (1). Acceptance of 2 and 256 is fork work (comment: "WS-0: Accept color_number=2 (Nc=0, Mode 0 monochrome)"). <!-- anchor: encoder.c:189-198; jabcode.h:31-33 -->

The palette allocation is `color_number * 3 * COLOR_PALETTE_NUMBER` bytes — four palette copies, with the default palette replicated into slots 1-3. The in-source rationale, verbatim: "CRITICAL: Must allocate space for 4 palettes to match decoder and avoid buffer overflow" and "decoder expects COLOR_PALETTE_NUMBER (4) palettes". <!-- anchor: encoder.c:206-223; jabcode.h:41 --> Defaults set here: `module_size = DEFAULT_MODULE_SIZE` (12), `master_symbol_width/height = 0` (auto), all ECC levels 0 (= default level at use time, via `setDefaultEccLevels` memset). <!-- anchor: encoder.c:200-204, 129-132 -->

`destroyEncode` frees every member allocation including per-symbol arrays; it does not NULL-check `enc` itself. On mid-`createEncode` allocation failure the partially built object is returned as NULL **without** freeing earlier members — see Known defects. <!-- anchor: encoder.c:211-253, 261-280 -->

## 4.3 Capacity and ECC selection

### 4.3.1 `getSymbolCapacity` (encoder.c:651)

Capacity in bits = (side\_x × side\_y − FP − AP − palette − metadata modules) × bits-per-module, with these terms, verbatim:

```c
nb_modules_fp = 4 * 17;                     // master symbol
nb_modules_fp = 4 * 7;                      // slave symbol
jab_int32 nb_modules_palette = enc->color_number > 64 ?
        (64-2)*COLOR_PALETTE_NUMBER : (enc->color_number-2)*COLOR_PALETTE_NUMBER;
jab_int32 nb_modules_ap = (number_of_aps_x * number_of_aps_y - 4) * 7;
jab_int32 nb_of_bpm = (jab_int32)round(log(enc->color_number) / log(2));
```

<!-- anchor: encoder.c:653-672 -->

Master metadata modules (non-default mode only): `(nb_metadata_bits - MASTER_METADATA_PART1_LENGTH) / nb_of_bpm` rounded up, plus `MASTER_METADATA_PART1_MODULE_NUMBER` (4). `getMetadataLength` returns 6 + 38 = 44 encoded bits for a non-default master, 0 for default mode; for slaves it returns the net Part I + optional V/E lengths (2, +5, +6). <!-- anchor: encoder.c:674-688, 606-643; decoder.h:20-25 -->

### 4.3.2 `getOptimalECC` (encoder.c:698)

```c
for (jab_int32 k=3; k<=6+2; k++)
    for (jab_int32 j=k+1; j<=6+3; j++)
    {
        jab_int32 dist = (capacity/j)*j - (capacity/j)*k - net_data_length;
        if(dist<min && dist>=0) { wcwr[1] = j; wcwr[0] = k; min = dist; }
    }
```

<!-- anchor: encoder.c:700-713 -->

The search space is all `(wc, wr)` with 3 ≤ wc ≤ 8 and wc+1 ≤ wr ≤ 9 (21 pairs) — a superset of the ten Table 20 pairs, consistent with Annex B's recommendation "It is recommended to select wc ≥ 3 and wr ≥ wc+1". The winner is the pair whose net capacity `floor(capacity/wr)*wr − floor(capacity/wr)*wc` exceeds the payload by the smallest non-negative margin. Results always fit the metadata E fields (E1 = wc − 3, E2 = wr − 4, 3 bits each). <!-- anchor: ISO 23634 Annex B; encoder.c:698-714, 939-940 -->

`getOptimalECC` runs only when a symbol carries explicit ECC metadata: for the master in non-default mode, and for slaves with SE = 1. Otherwise the level table applies: `wcwr_for_level(lvl)` = `ecclevel2wcwr[(lvl ? lvl : DEFAULT_ECC_LEVEL) - 1]` (see [06-ldpc.md](06-ldpc.md) §6.7 for the tables). <!-- anchor: encoder.c:2093-2113; encoder.h:241-244 -->

### 4.3.3 `setMasterSymbolVersion` (encoder.c:1881) — auto-sizing

For a single symbol with unset version, payload = `encoded_data->length + 5` ("plus S and flag bit"), then the smallest square side-version 1..32 whose net capacity fits is chosen. **Fork divergence:** the scan starts at version 2 for `color_number >= 16` — comment, verbatim: "Version 1 (21×21) creates fixed point at x=10 where flip(10)=21-1-10=10". If nothing fits, the error suggests lower ECC levels if one would fit, else "Message does not fit into one symbol. Use more symbols." <!-- anchor: encoder.c:1884-1926 -->

## 4.4 Data analysis and encoding — clause 5.2/5.3

### 4.4.1 Mode tables (`encoder.h`)

Mode order, verbatim comment: "1.upper, 2.lower, 3.numeric, 4.punct, 5.mixed, 6.alphanumeric, 7.byte". Indices 0-6 are the modes; 7-13 are their shift states ("First latch then shift", `latch_shift_to` trailing comment). <!-- anchor: encoder.h:202-203, 200 -->

```c
static const jab_int32 character_size[7]={5,5,4,4,5,6,8};
```

<!-- anchor: encoder.h:207 -->

`jab_enconing_table` — the source spelling, sic — is `jab_int32 [MAX_SIZE_ENCODING_MODE][JAB_ENCODING_MODES]` = 256 × 6: one row per byte value, one column per non-byte mode, −1 = not encodable, values < −18 = lookahead pairs (CR+LF, ". ", ", ", ": " handling). Representative rows: byte 32 (space) `{ 0, 0, 0,-1,-1, 0}`; byte 74 ('J') `{ 10,-1,-1,-1,-1,20}`; byte 33 ('!') `{-1,-1,-1, 0,-1,-1}`; byte 13 (CR) `{-1,-1,-1,-1,-19,-1}`; byte 44 (',') `{-1,-1,11, 8,-20,-1}`. Full table at the anchor. <!-- anchor: encoder.h:129-181; jabcode.h:26-27 -->

Mode-switch costs (bits), 14 × 14, verbatim:

```c
static const jab_int32 latch_shift_to[14][14]=
        {{0,5,5,ENC_MAX,ENC_MAX,5,ENC_MAX,ENC_MAX,ENC_MAX,ENC_MAX,5,7,ENC_MAX,11},
         {7,0,5,ENC_MAX,ENC_MAX,5,ENC_MAX,5,ENC_MAX,ENC_MAX,5,7,ENC_MAX,11},
         {4,6,0,ENC_MAX,ENC_MAX,9,ENC_MAX,6,ENC_MAX,ENC_MAX,4,6,ENC_MAX,10},
         {ENC_MAX,ENC_MAX,ENC_MAX,ENC_MAX,ENC_MAX,ENC_MAX,ENC_MAX,0,0,0,ENC_MAX,ENC_MAX,0,ENC_MAX},
         {ENC_MAX,ENC_MAX,ENC_MAX,ENC_MAX,ENC_MAX,ENC_MAX,ENC_MAX,0,0,0,ENC_MAX,ENC_MAX,0,ENC_MAX},
         {8,13,13,ENC_MAX,ENC_MAX,0,ENC_MAX,ENC_MAX,ENC_MAX,ENC_MAX,8,8,ENC_MAX,12},
         {ENC_MAX,ENC_MAX,ENC_MAX,ENC_MAX,ENC_MAX,ENC_MAX,0,0,0,0,ENC_MAX,ENC_MAX,0,0},
         {0,5,5,ENC_MAX,ENC_MAX,5,ENC_MAX,ENC_MAX,ENC_MAX,ENC_MAX,5,7,ENC_MAX,11},
         {7,0,5,ENC_MAX,ENC_MAX,5,ENC_MAX,5,ENC_MAX,ENC_MAX,5,7,ENC_MAX,11},
         {4,6,0,ENC_MAX,ENC_MAX,9,ENC_MAX,6,ENC_MAX,ENC_MAX,4,6,ENC_MAX,10},
         {ENC_MAX,ENC_MAX,ENC_MAX,ENC_MAX,ENC_MAX,ENC_MAX,ENC_MAX,0,0,0,ENC_MAX,ENC_MAX,0,ENC_MAX},
         {ENC_MAX,ENC_MAX,ENC_MAX,ENC_MAX,ENC_MAX,ENC_MAX,ENC_MAX,0,0,0,ENC_MAX,ENC_MAX,0,ENC_MAX},
         {8,13,13,ENC_MAX,ENC_MAX,0,ENC_MAX,ENC_MAX,ENC_MAX,ENC_MAX,8,8,ENC_MAX,12},
         {ENC_MAX,ENC_MAX,ENC_MAX,ENC_MAX,ENC_MAX,ENC_MAX,0,0,0,0,ENC_MAX,ENC_MAX,0,0}};//First latch then shift
```

<!-- anchor: encoder.h:186-200 -->

Switch tokens ("first latch followed by shift to and the last two are ECI and FNC1" — columns 0-6 latch, 7-13 shift, 14-15 ECI/FNC1), verbatim:

```c
static const jab_int32 mode_switch[7][16]=
        {{-1,28,29,-1,-1,30,-1,-1,-1,-1,27,125,-1,124,126,-1},   //from upper case mode to all other modes
         {126,-1,29,-1,-1,30,-1,28,-1,127,27,125,-1,124,-1,127}, //lower case mode
         {14,63,-1,-1,-1,478,-1,62,-1,-1,13,61,-1,60,-1,-1},     //numeric mode
         {-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},      //punctuation mode
         {-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},      //mixed mode
         {255,8188,8189,-1,-1,-1,-1,-1,-1,-1,254,253,-1,252,-1,-1}, //alphanumeric
         {-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1}};     //byte mode
```

<!-- anchor: encoder.h:210-220 -->

### 4.4.2 `analyzeInputData` (encoder.c:288) and `encodeData` (encoder.c:723)

`analyzeInputData` is a shortest-path DP over the 14 mode states per character (`curr_seq_len`/`prev_mode` arrays sized `(input->length+2)*14`), starting in upper-case mode, with shift-back bookkeeping and two-character lookahead for the `-18`/`< -18` table entries. It returns the optimal mode sequence and its bit length; byte runs longer than 15 add 13 bits, and runs beyond 8207 (comment: "2^13+15") add per-8207-block re-shift costs (`modeswitch` = 11/10/12 bits depending on the origin mode). <!-- anchor: encoder.c:288-584, 497-524 -->

`encodeData` serializes: for each mode switch it writes `mode_switch[from][to]` in `latch_shift_to[from][to]` bits (minus 4 when the target is byte mode — the 4-bit count field is written separately), then the per-character codes in `character_size[mode]` bits. Byte-mode headers: a 4-bit count (0 means > 15), then 13 bits `byte_counter-15-1` when 15 < count ≤ 8207, else `8191`. On crossing each 8207 boundary the encoder re-emits a shift-to-byte token: 124 in 7 bits from upper, 60 in 6 bits from numeric, 252 in 8 bits from alphanumeric. The 6-bit numeric token is a fork fix; comment, verbatim: "This was written with width 5, dropping one bit and shearing the entire remaining stream out of alignment — the length estimator (modeswitch=10 = 6+4) always had it right." Regression guard: `test-cascade-hv` (`Makefile:166`, corpus §2.2). A second fork fix resets the continuation multiplier `factor=1` at each new byte run (comment at encoder.c:841-845). <!-- anchor: encoder.c:750-896, 847-884, 856-861; corpus-model.md §2.2 -->

### 4.4.3 Annex D as the wire-compatibility regression vector

The ISO worked example encodes "JAB Code 2016!" as: Uppercase (J,A,B,SP,C = 10,1,2,0,3) → L/L (28, "11100") → Lowercase (o,d,e = 15,4,5) → N/L (29, "11101") → Numeric (SP,2,0,1,6 = 0,3,1,2,5) → P/S (13, "1101") → Punctuation (! = 0, "0000"); "The resulting binary message length is 78 bits... Pg = 1071 and K = 476. According to Table 1, the symbol Side-Version 1 is selected, with the side size 21"; default mode, so no metadata is encoded. <!-- anchor: ISO 23634 Annex D -->

The source tables reproduce every token of that sequence: `jab_enconing_table[74][0]` = 10 ('J', upper), `latch_shift_to[0][1]` = 5 with `mode_switch[0][1]` = 28 (U→L latch "11100"), `latch_shift_to[1][2]` = 5 with `mode_switch[1][2]` = 29 (L→N latch "11101"), `latch_shift_to[2][10]` = 4 with `mode_switch[2][10]` = 13 (N→punct shift "1101"), `jab_enconing_table[33][3]` = 0 ('!'), and `character_size` \{5,5,4,4\} gives 25+5+15+5+20+4+4 = 78 bits. <!-- anchor: encoder.h:129-220; ISO 23634 Annex D -->

**Open verification item.** Evaluating the quoted `getSymbolCapacity` terms for the same configuration (side 21, 8 colours, default mode) gives (441 − 4×17 − 24 − 0 − 0) × 3 = 1047 bits, hence Pg = floor(1047/9)×9 = 1044 and 464 checks — not the extracted Annex D figures Pg = 1071 / K = 476 (which imply 357 data modules). Whether the difference lies in Annex D's module accounting or in the spec extraction cannot be resolved from this corpus. The operative regression for any encoder change is therefore the byte-identical round trip of the Annex D message (guarded by `test-roundtrip`, `Makefile:158`), not the extracted Pg figure. <!-- anchor: encoder.c:653-688; ISO 23634 Annex D; corpus-model.md §2.2 -->

## 4.5 Master metadata assembly — clause 4.4

`encodeMasterMetadata` (encoder.c:925) builds Part I (net length `MASTER_METADATA_PART1_LENGTH/2` = 3 bits: the Nc field) and Part II (net `MASTER_METADATA_PART2_LENGTH/2` = 19 bits: V 10 + E1 3 + E2 3 + MSK 3), with:

```c
jab_int32 Nc = (jab_int32)round(log(enc->color_number)/log(2.0)) - 1;
jab_int32 V = ((enc->symbol_versions[0].x -1) << 5) + (enc->symbol_versions[0].y - 1);
jab_int32 E1 = enc->symbols[0].wcwr[0] - 3;
jab_int32 E2 = enc->symbols[0].wcwr[1] - 4;
jab_int32 MSK = DEFAULT_MASKING_REFERENCE;
```

<!-- anchor: encoder.c:927-941; decoder.h:23-24 -->

The `round()` on the Nc computation is a fork defensive fix (comment: ARM glibc "returns 5.999... for log2(64), 6.999... for log2(128)"). Both parts are LDPC-encoded at rate 1/2 with `wcwr = {2, -1}` (the metadata path of `encodeLDPC`; [06-ldpc.md](06-ldpc.md) §6.4), yielding 6 + 38 = 44 encoded bits concatenated into `symbols[0].metadata`. <!-- anchor: encoder.c:933-937, 967-993 -->

Because MSK is written before mask selection, `updateMasterMetadataPartII` (encoder.c:1008) re-encodes Part II with the selected reference and `placeMasterMetadataPartII` (encoder.c:1053) rewrites the Part II modules in place, skipping Part I plus `MIN(color_number-2, 64-2) * COLOR_PALETTE_NUMBER` palette modules along the metadata spiral (`getNextMetadataModuleInMaster`, defined at decoder.c:1080). The placement loop bound is a fork fix — comment, verbatim: "bounds use `<` not `<=` ... The previous `<=` triggered a 1-byte heap OOB read at index partII\_bit\_end, whose stale value was interpreted as an LDPC unpacked bit and silently corrupted one master-metadata module's color (process-state-dependent)." <!-- anchor: encoder.c:1053-1108, 1075-1081; decoder.c:1080 -->

`isDefaultMode` (encoder.c:591) is `color_number == 8 && (ecc_levels[0] == 0 || ecc_levels[0] == DEFAULT_ECC_LEVEL)`: in default mode no master metadata is encoded or placed at all, matching Annex D's "default mode → no metadata". <!-- anchor: encoder.c:591-598; ISO 23634 Annex D -->

## 4.6 `createMatrix` placement — clauses 5.6/5.7

`createMatrix` (encoder.c:1171) allocates `matrix` (colour indices) and `data_map`, with the **encoder-side convention `data_map = 1` for data modules**; every placed FP/AP/palette/metadata module zeroes its cell. (The decoder uses the opposite convention — 0 = data module; see [07-mask.md](07-mask.md) §7.6.) <!-- anchor: encoder.c:1174-1187 -->

Placement order within the function:

1. **Alignment patterns** at `jab_ap_pos` crossings, alternating "left" and "right" 7-module shapes (6 periphery + 1 core), colours `apn_core_color_index[Nc]` / `apx_core_color_index[Nc]`; the four corner crossings are skipped (they host FPs/docking APs). <!-- anchor: encoder.c:1189-1260; encoder.h:74-75, 249-292 -->
2. **Master finder patterns** (index 0): three rings k = 0..2, mirrored writes producing 17 modules per FP (hence `4 * 17` in `getSymbolCapacity`), colours alternating between the FP core index and its k%2 partner. Mode 0 (Nc = 0) forces K/W alternation — comment: "even rings (k=0,2) = K (palette\[0\]), odd rings (k=1) = W (palette\[1\])... geometric position disambiguates corners (per ISO 23634 Section 4.3.7 monochrome reduction)". <!-- anchor: encoder.c:1262-1321, 1275-1294 -->
3. **Slave docking APs** (index > 0): two rings k = 0..1, with the analogous Mode 0 K/W fix ("W2.10-enc"). <!-- anchor: encoder.c:1322-1385 -->
4. **Metadata Part I** (master, non-default mode): each 3 encoded bits select a row of `nc_color_encode_table[8][2] = {{0,0},{0,3},{0,6},{3,0},{3,3},{3,6},{6,0},{6,3}}`, placing two modules. For `color_number <= 8` the index is `base_index % color_number`; above 8 the encoder searches the palette for the module whose RGB equals the default-palette colour (black/cyan/yellow bootstrap). <!-- anchor: encoder.c:1404-1445; encoder.h:124 -->
5. **Colour palettes**: four copies, colours 2..`MIN(color_number, 64)`−1 ("skip the first two colors in finder pattern"); for ≤ 8 colours the per-copy orders are `master_palette_placement_index[4][8] = {{0,3,5,6,1,2,4,7},{0,6,5,3,1,2,4,7},{6,0,5,3,1,2,4,7},{3,0,5,6,1,2,4,7}}`; above 8 colours placement is sequential (fork comment: "For 16+ colors, use sequential indexing instead of placement mapping"), through `getColorPaletteIndex`, which subsamples 64 representative indices for 128/256-colour modes. Slave palettes use `slave_palette_placement_index[8] = {3,6,5,0,1,2,4,7}` at the boustrophedon `slave_palette_position` coordinates, stamped into all four sides by symmetry. <!-- anchor: encoder.c:1446-1516, 1117-1162; encoder.h:39-45; decoder.h:36-45 -->
6. **Metadata Part II** modules (master, non-default mode), packed `nb_of_bits_per_mod` bits per module. <!-- anchor: encoder.c:1474-1495 -->
7. **Data placement**: column-major (`for start_i` over x, stepping down each column), packing `nb_of_bits_per_mod` bits per free `data_map` cell; once `ecc_encoded_data` is exhausted, alternating 0/1 padding bits fill the remainder ("add padding bits if necessary", clause 5.5). <!-- anchor: encoder.c:1521-1567; ISO 23634 5.5 -->

Per-mode FP/AP colour index tables, verbatim:

```c
static const jab_byte fp0_core_color_index[] = {0, 0, FP0_CORE_COLOR, 0, 0, 0, 0, 0};
static const jab_byte fp1_core_color_index[] = {0, 0, FP1_CORE_COLOR, 0, 0, 0, 0, 0};
static const jab_byte fp2_core_color_index[] = {0, 2, FP2_CORE_COLOR, 14, 30, 60, 124, 252};
static const jab_byte fp3_core_color_index[] = {0, 3, FP3_CORE_COLOR, 3, 7, 15, 15, 31};
static const jab_byte apn_core_color_index[] = {0, 3, AP0_CORE_COLOR, 3, 7, 15, 15, 31};
static const jab_byte apx_core_color_index[] = {0, 2, APX_CORE_COLOR, 14, 30, 60, 124, 252};
```

with `FP0_CORE_COLOR 0`, `FP1_CORE_COLOR 0`, `FP2_CORE_COLOR 6`, `FP3_CORE_COLOR 3`, `AP0..AP3_CORE_COLOR 3`, `APX_CORE_COLOR 6`. Indexed by Nc = 0..7. Extended-mode consequences are covered in [16-extended-colour-modes.md](16-extended-colour-modes.md). <!-- anchor: encoder.h:50-75 -->

## 4.7 Cascade assignment

`InitSymbols` (encoder.c:2161) validates versions (1..32) and positions (0..`MAX_SYMBOL_NUMBER`), then enforces master position 0 in two stages — the "enforcement split": first it *reorders* so the symbol at position 0 becomes index 0 (swapping positions, versions, ECC levels), then, if no position-0 symbol exists at all, it fails with `"Master symbol missing"`; a single symbol at a non-zero position is silently forced to 0. Duplicate positions fail with `"Duplicate symbol position"`. <!-- anchor: encoder.c:2180-2215 -->

`assignDockedSymbols` (encoder.c:1598) walks hosts in index order and docking directions top/bottom/left/right (j = 0..3), matching neighbours in `jab_symbol_pos` coordinates (`{0,0}, {0,-1}, {0,1}, {-1,0}, {1,0}, ...` — the 61-entry decode order, encoder.h:111-119). Each found slave is moved to the next slot via `swap_symbols` (encoder.c:1580, swaps positions/versions/ecc levels and the whole `jab_symbol` struct) and records `host`; `slaves[j]` holds the slave's index, `-1` marks the host-facing side, 0 means no slave. Any symbol left with `host == -1` fails: `"Slave symbol at position %d has no host"`. `checkDockedSymbolSize` (encoder.c:1845) requires docked sides to share the side-version. <!-- anchor: encoder.c:1598-1677, 1580-1591, 1845-1873; encoder.h:111-119 -->

`setSlaveMetadata` (encoder.c:2239) builds each slave's net metadata: SS, SE flags (Part I, 2 bits); V = `symbol_versions[i].x - 1` or `.y - 1` (5 bits) when the free side's version differs from the host; E1/E2 (6 bits) when the ECC level differs. `fitDataIntoSymbols` (encoder.c:2023) then splits the encoded stream proportionally to net capacity and lays out each symbol's payload tail-first: flag bit `1` at the end, then 4 (master) or 3 (slave) S bits, then the docked slaves' metadata bit-reversed into the stream; slack capacity ≥ 6 bits upgrades docked slaves to explicit E (`addE2SlaveMetadata`, SE := 1) and re-runs `getOptimalECC`, patching the already-written E field in the host stream via `updateSlaveMetadataE`'s reverse scan from the flag bit. Slave metadata therefore travels **inside the host's LDPC-protected data stream**, not as separately coded metadata (decoder counterpart: `decodeSlaveMetadata`, decoder.c:1161). <!-- anchor: encoder.c:2239-2298, 2023-2153, 1940-2014; decoder.c:1161-1234 -->

`getCodePara` (encoder.c:1684) computes the cascade canvas: `dimension` (module pixels; derived from `master_symbol_width/height` when set, else `module_size`), per-row/column module extents, and `code_size`. `createBitmap` (encoder.c:1781) renders every symbol's matrix through the palette into a 32-bpp RGBA `jab_bitmap` (alpha 255). <!-- anchor: encoder.c:1684-1773, 1781-1837; jabcode.h:43-45 -->

## 4.8 Palette generation

`setDefaultPalette` (encoder.c:95): 2-colour → K/W (fork, "WS-0"); 4-colour → black/magenta/yellow/cyan drawn from `jab_default_palette` entries \{0, 5, 6, 3\} (comments: "000 for 00 ... 011 for 11"); 8-colour → `jab_default_palette` verbatim (`{0,0,0, 0,0,255, 0,255,0, 0,255,255, 255,0,0, 255,0,255, 255,255,0, 255,255,255}`, order comment "\[K,B,G,C,R,M,Y,W\] = ISO/IEC 23634 Table 21 (the Fraunhofer reference)"); above 8 → `genColorPalette`. <!-- anchor: encoder.c:95-122; encoder.h:26-34 -->

`genColorPalette` (encoder.c:29) builds procedural palettes from per-channel level counts `(vr, vg, vb)`: 16 → (4,2,2), 32 → (4,4,2), 64 → (4,4,4), 128 → (8,4,4), 256 → (8,8,4); channel step `d = 256/(v-1)`, except exactly 85 when `v-1 == 3`; values clamped to 255. The same routine is exported so the decoder synthesizes the identical palette ("the decoder can synthesize the procedural palette and recover indices the encoder never places in the matrix (specifically palette\[1\] for color_number > 8)" — encoder.h comment). <!-- anchor: encoder.c:29-88; encoder.h:298-301 -->

## 4.9 Invariants

- Master symbol is always `symbols[0]` after `InitSymbols`; every later stage indexes it as such. <!-- anchor: encoder.c:2180-2203 -->
- Encoder `data_map`: 1 = data module. Every non-data write zeroes its cell before data placement runs. <!-- anchor: encoder.c:1187, 1230-1253 -->
- The MSK field in placed Part II always matches `enc->mask_type` on return: default mode writes reference 7 without evaluation; non-default mode re-encodes and re-places Part II after `maskCode`. <!-- anchor: encoder.c:2406-2430 -->
- `symbols[i].Pg` records `ecc_encoded_data->length` for the synthetic decoder before interleaving. <!-- anchor: encoder.c:2383-2384; jabcode.h:162 -->
- Metadata net→encoded lengths are fixed: Part I 3→6, Part II 19→38 (rate-1/2 metadata LDPC). <!-- anchor: encoder.c:927-928; decoder.h:23-24 -->

## 4.10 Failure modes

| Failure | Trigger | Return |
|---|---|---|
| `"No input data specified!"` | NULL or empty `data` | 2 <!-- anchor: encoder.c:2310-2319 --> |
| `"Incorrect symbol version/position"`, `"Master symbol missing"`, `"Duplicate symbol position"`, docking errors | `InitSymbols` chain | 3 <!-- anchor: encoder.c:2322-2323, 2161-2231 --> |
| `"Message does not fit into one symbol..."` / `"...Use higher symbol version."` | auto-size or `fitDataIntoSymbols` overflow | 4 <!-- anchor: encoder.c:2343-2347, 2356-2360, 2069-2073 --> |
| allocation failures, LDPC/matrix/bitmap failures | any stage | 1 <!-- anchor: encoder.c:2328-2338, 2376-2396, 2434-2442 --> |

`encodeData` hard-fails (`"Encoding data failed"`) on any character not encodable in the planned mode — a mode-plan/serializer disagreement, not a user error. <!-- anchor: encoder.c:761-763, 794-796, 806-810, 887-891 -->

## 4.11 Extension points

- New colour modes plug in at `setDefaultPalette`/`genColorPalette`, the six per-mode FP/AP index tables, and `nc_color_encode_table` — the Part I bootstrap constrains any palette to contain black/cyan/yellow equivalents. <!-- anchor: encoder.c:95-122; encoder.h:50-75, 124 -->
- The palette-order profile seam: `jab_default_palette`'s header comment designates the palette (with `setDefaultPalette`) as "the first real profile axis" for a future BSI TR-03137 ordering — see `src/jabcode/CONFORMANCE_PROFILE.md`. <!-- anchor: encoder.h:17-25 -->
- ECC search policy is isolated in `getOptimalECC`; level-table policy in `wcwr_for_level`. <!-- anchor: encoder.c:698-714; encoder.h:241-244 -->

## 4.12 Performance notes

Encode cost is dominated by the LDPC generator-matrix build, memoized per (wc, wr, capacity) in `ldpc.c` ([06-ldpc.md](06-ldpc.md) §6.9); `analyzeInputData` is O(length × 14²). Benchmarks: `bench` (Nc 0..7 microbench), `bench-cascade` (N 1..61 × Nc) — [12-benchmark-estate.md](12-benchmark-estate.md). <!-- anchor: ldpc.c:515-532; Makefile:79, 103 -->

## 4.13 Known defects

- `createEncode` leaks earlier members when a later allocation fails (returns NULL without freeing `enc`, `palette`, ...). <!-- anchor: encoder.c:211-253 -->
- `encodeMasterMetadata` leaks `partI` when the Part II allocation or either `encodeLDPC` call fails (early returns skip the frees at encoder.c:995-998). <!-- anchor: encoder.c:945-998 -->
- `getOptimalECC`'s doc comment declares "@return JAB_SUCCESS | JAB_FAILURE" but the function returns `void`. <!-- anchor: encoder.c:696-698 -->
- The Annex D Pg/K reconciliation is open (§4.4.3).
- History: the Part II placement 1-byte OOB read (fixed, encoder.c:1075-1081); the width-5 numeric shift-to-byte token (fixed, encoder.c:856-861, guarded by `test-cascade-hv`); the cumulative byte-run `factor` bug (fixed, encoder.c:841-845).

Deeper LDPC/masking theory: Special Topics (JC-S), forthcoming. Pipeline context: [02-codec-pipeline.md](02-codec-pipeline.md). Decode counterpart: [05-detector-and-decoder.md](05-detector-and-decoder.md).
