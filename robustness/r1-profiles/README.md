# R1 — Evidence-Backed Per-Medium Encoding Profiles

Which **(Nc, ECC)** JABCode encoding maximises *robustness-by-construction* for
each capture medium — measured end-to-end by the **R0 rig**, choosing only among
**ISO/IEC 23634-legal** colour counts (Nc 0–7 → the standardized Table-21
palette) and ECC levels (1–10). No palette is ever altered; we select the most
robust *legal* encoding for the medium and quantify what density it costs.

> **Context.** R0 plus three decode-side refutations showed the robustness lever
> is the *encoded symbol*, not the decoder. JABCode's two conformant robustness
> knobs are **Nc** (colour count — fewer colours = more separable = more robust
> but less dense) and **ECC** (LDPC level — more parity = more correction but
> less capacity / larger symbol). This phase quantifies that tradeoff on the rig
> and derives a profile per medium.

```
robustness/r1-profiles/
├── README.md                this file
├── profile_select.py        the helper: medium hint + payload size -> ISO-conformant (Nc, ECC)
├── gen/
│   ├── grid_encode.c        encodes a fixed payload across the (Nc x ECC) grid (reference encoder)
│   ├── grid_capacity.c      max payload per cell at a fixed geometry (the density datum)
│   ├── build_corpus.py      orchestrator: grid -> degrade.py -> R0 rig -> measurements
│   ├── plot_profiles.py     renders the three charts
│   ├── test_profile_select.py  e2e guard: every profile encodes+decodes+SHA-verifies
│   └── Makefile             builds grid_encode + grid_capacity (links src/jabcode)
├── data/
│   ├── grid_density.csv         AUTO side_size per cell  (area a fixed payload costs)
│   ├── density_capacity.csv     max payload per cell @ side 45  (payload a fixed area buys)
│   ├── decode_rates.csv         (nc, colors, ecc, family, param) -> decode-rate  ← the measurements
│   ├── clean_baseline.csv       every clean symbol decodes to its payload (sanity)
│   ├── profiles_table.csv       the derived profiles + their rig numbers (machine-readable)
│   ├── manifest.jsonl           rig manifest for the degraded corpus (committed; regenerable)
│   ├── rig.per_image.jsonl      raw rig per-image outcomes
│   ├── rig.aggregate.json       rig aggregate (decode-rate + fail-stage per condition)
│   └── symbols/                 the committed clean (Nc x ECC) x N-payload grid (small PNGs)
└── charts/
    ├── decode_rate_vs_nc.png        robustness vs colour count, per family  ← headline
    ├── decode_rate_vs_ecc.png       robustness vs ECC  (shows ECC is second-order)
    └── robustness_vs_density.png    the tradeoff in one frame
```

## Method (how the numbers are produced)

A fixed-length **80-byte COA-style payload** (`COA:RHABI-2026:SN=…;url=…`) is
encoded across a focused **(Nc × ECC) grid** by the reference encoder
(`src/jabcode`, via `grid_encode.c`, the same `createEncode`/`generateJABCode`
path `test/bench_sweep.c` exercises):

- **Nc ∈ {1, 2, 3, 5, 7}** → colours {4, 8, 16, 64, 256}. Spans the
  robust-but-sparse end (Nc1/2) through mid (Nc3) to the dense-but-fragile end
  (Nc5/7). Nc0 (2 c) is the monochrome special case with no colour separation to
  study; Nc4/Nc6 are interior points the five chosen levels already bracket.
- **ECC ∈ {3, 5, 8}** → 3 = reference default (~6 %), 5 = moderate, 8 = heavy.
  Spans the correction axis without the diminishing-returns extremes (1–2, 9–10).

Each clean cell is degraded by the **R0 colour/luminance families** — reusing
`robustness/r0/synthetic/degrade.py` verbatim — on their full param ladders:

| family      | models                                       | ladder        |
|-------------|----------------------------------------------|---------------|
| `chroma`    | screen colour-wash (desat + warm cast)       | 0.3 0.5 0.7   |
| `lighting`  | non-uniform illumination (shadow→glare ramp) | 0.3 0.5 0.7   |
| `blur`      | defocus / motion smear (Gaussian σ px)       | 0.5 1 2 3     |
| `downscale` | distance / low-res sensor (target px/module) | 8 6 4 3       |

then decoded through the **R0 rig** (`robustness/r0/rig`: the `r0_decode` probe
with `strict-PartII` + the `run_rig.py` aggregator) with **known-payload
SHA-256 verification**, so a decode counts only if the bytes are *correct*.

**De-noising.** One degraded image is a single Bernoulli trial, so a one-image
"rate" is one coin flip. We encode **5 distinct fixed-length payloads** per cell
(deterministic, reproducible) and report decode-rate per (Nc, ECC, family, param)
as the mean over the 5 payloads × the family's params. Corpus:
**15 cells × 5 payloads × 14 params = 1 050 degraded images.** Every one of the
**75 clean symbols decodes to its payload** (`clean_baseline.csv`) — the rig is
wired correctly.

### Why AUTO geometry (and what "density cost" means)

The symbols are encoded in **AUTO geometry** — the encoder picks the smallest
square version per cell — *not* pinned to one version. This is a measured
property of the reference encoder, not a convenience:

> `fitDataIntoSymbols()` calls `getOptimalECC()` for the master symbol
> (`encoder.c:2087`), which **recomputes the LDPC (wc, wr) to fill the chosen
> version**, overriding the requested ECC level. So pinning a large version with
> a small payload makes ECC 3/5/8 produce **byte-identical** symbols (verified:
> identical md5) — the ECC axis collapses. The one conformant lever that makes
> the requested ECC change the symbol is **version selection**: a higher ECC
> forces a *larger* version (more parity area).

So AUTO geometry is what actually exercises ECC, and **the larger symbol IS how
ECC buys robustness**. The price is recorded two complementary ways:

- `grid_density.csv` — the **area a fixed payload costs** (AUTO side_size per
  cell). At ECC3, Nc1 needs side 29 vs side 25 for Nc2–7 (4 colours carry only
  2 bits/module).
- `density_capacity.csv` — the **payload a fixed area buys** (max bytes per cell
  at the common side-45 version). This is the cleaner density metric, since at a
  small fixed payload many AUTO cells pile on the version floor.

## Findings

### 1. Colour count (Nc) dominates; ECC is a weak second-order lever

Overall decode-rate (mean over all four families × ECC), per Nc:

| Nc | colours | decode-rate |
|---:|--------:|:-----------:|
| 1  | 4       | 85 %        |
| **2** | **8** | **93 %**  |
| 3  | 16      | 90 %        |
| 5  | 64      | 89 %        |
| 7  | 256     | 73 %        |

Robustness is an **inverted-U peaking at Nc2 (8 colours)** and collapsing at
Nc7. Meanwhile the **ECC marginal is nearly flat** (mean over all Nc × families):

| ECC | decode-rate |
|----:|:-----------:|
| 3   | 85.7 %      |
| 5   | 86.0 %      |
| 8   | 86.0 %      |

A ~0.3 pp lift from ECC3 → ECC8. **For these colour/luminance threats the
encoded colour count is the lever; ECC level barely moves robustness** — because
the degradations cause *correlated, structural* errors (whole-symbol colour
collapse, illumination gradients) that overwhelm an LDPC budget tuned for random
errors. ECC's real price is capacity (next section). *(See
`charts/decode_rate_vs_ecc.png` — the lines are visibly flat.)*

### 2. The best Nc is threat-specific (this is what the profiles encode)

Mean decode-rate per Nc, grouped by which channel the degradation attacks
(ECC-averaged):

| Nc | colours | colour threat (chroma) | defocus/low-res (blur+downscale) | illumination (lighting) |
|---:|--------:|:----------------------:|:--------------------------------:|:-----------------------:|
| 1  | 4       | **100 %**              | 81 %                             | **100 %**               |
| 2  | 8       | 67 %                   | **100 %**                        | **100 %**               |
| 3  | 16      | 62 %                   | **98 %**                         | 91 %                    |
| 5  | 64      | 67 %                   | 95 %                             | 82 %                    |
| 7  | 256     | 67 %                   | 75 %                             | **29 %**                |

- **Colour wash (chroma)** → **low Nc wins decisively**: Nc1 = 100 %, everything
  else ≈ 62–67 %. Four widely-spaced colours stay separable when a screen
  desaturates and warm-shifts them; ≥8-colour palettes collapse.
- **Illumination ramp (lighting)** → **low Nc wins monotonically**: Nc1/2 =
  100 % down to Nc7 = 29 %. More colours = less luminance headroom for a
  brightness gradient to eat.
- **Defocus / low-res (blur, downscale)** → **mid Nc wins**: Nc2–3 ≈ 98–100 %,
  while Nc1 dips to 81 % and Nc7 to 75 %. The **Nc1 dip is real** (LDPC-stage
  failures, persistent across 5 payloads × 4 params): at 4 colours each
  blur-induced misclassification corrupts 2 bits *and* the small symbol packs
  fewer parity modules, so the error rate crosses the LDPC floor where Nc2/3 —
  more colour separation, more modules — does not.

*(See `charts/decode_rate_vs_nc.png`.)*

### 3. The density / robustness tradeoff, quantified

Max single-symbol payload at a **common side-45 geometry** (`density_capacity.csv`):

| Nc | colours | ECC3 | ECC5 | ECC8 |
|---:|--------:|-----:|-----:|-----:|
| 1  | 4       | 258 B | 198 B | 90 B |
| 2  | 8       | 391 B | 298 B | 137 B |
| 3  | 16      | 510 B | 393 B | 181 B |
| 5  | 64      | 688 B | 530 B | 245 B |
| 7  | 256     | **921 B** | 709 B | 329 B |

- **Nc density:** Nc7 packs **3.6×** the payload of Nc1 in the same physical
  symbol (921 B vs 258 B at ECC3).
- **ECC cost:** at Nc7, going ECC3 → ECC8 costs **64 %** of capacity (921 → 329 B)
  — for a ≈ 0 pp robustness gain on these threats. *The robust choice is fewer
  colours, not more parity.*
- Densest legal cell (Nc7/ECC3, 921 B) carries **10.2×** the sparsest
  (Nc1/ECC8, 90 B).

*(See `charts/robustness_vs_density.png`.)*

## The profiles

Each maps a medium to its dominant threat(s) → the most robust legal (Nc, ECC),
with the rig number that justifies it and the density it costs. Machine-readable
in `data/profiles_table.csv`.

| medium | Nc | colours | ECC | why (rig evidence) | density (side-45 capacity) |
|--------|---:|--------:|----:|--------------------|----------------------------|
| **hostile / screen** | 1 | 4 | 5 | screen = chroma + lighting. At Nc1 those decode **100 %** vs **47 %** at Nc7. Colour count, not ECC, is the lever. | 198 B (baseline) |
| **print / luxury-COA** | 3 | 16 | 5 | print = defocus + low-res. blur + downscale decode **100 %** at Nc3, with **2.0×** the density of the hostile profile. Controlled, gamut-limited, well-lit. | 393 B (**2.0×**) |
| **clean / archival** | 7 | 256 | 3 | clean digital channel = no capture degradation → clean decode **100 %**, so maximise density: **921 B/symbol**, **4.7×** the hostile profile. | 921 B (**4.7×**) |

Notes:
- **ECC 5** for the two capture media buys robustness *margin* against
  combinations of degradations at a modest capacity cost; the data says the Nc
  choice is what carries robustness, so we don't pay for ECC8.
- **clean / archival** faces *no* degradation, so its operative metric is the
  **100 % clean-baseline decode** (`clean_baseline.csv`), not a degraded rate —
  the goal there is purely maximum density.

## The helper

`profile_select.py` turns a **medium hint + payload size** into an
ISO-conformant **(Nc, ECC)**, emitting only legal values (it never invents a
palette — the reference encoder does the standardized encoding):

```bash
python3 robustness/r1-profiles/profile_select.py screen --payload-len 80
# {"medium":"screen","nc":1,"colors":4,"ecc":5,... ,"fits_at_v7":true}

python3 robustness/r1-profiles/profile_select.py luxury-coa --payload-len 80   # Nc3/ECC5
python3 robustness/r1-profiles/profile_select.py clean       --payload-len 80   # Nc7/ECC3
```

```python
from robustness.r1_profiles import profile_select   # or sys.path the dir
rec = profile_select.select("hostile", payload_len=120)
# rec["nc"], rec["ecc"]  -> feed to createEncode(2**(nc+1), 1) + symbol_ecc_levels[0]=ecc
# rec["fits_at_v7"]      -> False means the encoder will grow the symbol (more area)
```

Accepted hints: `hostile`, `screen`, `print`, `luxury-coa`, `clean`, `archival`
(case-insensitive; `-`/`_` interchangeable). Unknown hints fall back to `print`
with a note. With `strict_fit=True` the helper raises if the payload won't fit
the profile's side-45 symbol, so a caller that must keep one fixed-size symbol
can catch it and split into multiple symbols.

## Conformance

- The grid encoder, capacity probe, and helper select **only legal Nc (0–7) and
  ECC (1–10)** and pass them straight to the **reference encoder**, which uses
  the **standardized Table-21 palette** and Annex-legal LDPC. **No palette is
  overridden anywhere.**
- **Every profile round-trips:** `gen/test_profile_select.py` encodes the
  recommended (Nc, ECC) for all six media via `jabcodeWriter`, decodes through
  the strict-PartII rig probe, and asserts the payload SHA-256 matches —
  **6/6 PASS**.
- The three core conformance guards pass unchanged:
  `make -C src/jabcode test-eci test-table15 test-roundtrip` → **PASS / PASS / PASS**.

## Reproduce

Requires `gcc`, `python3`, system `libpng16`/`libtiff`/`zlib`, and Pillow +
numpy (+ matplotlib for charts) — the `/tmp/bench-venv` venv has all three.

```bash
# 1. build the grid tools (also builds libjabcode on demand)
make -C robustness/r1-profiles/gen all

# 2. generate the grid, degrade it, run the rig, write data/*.csv + manifest
/tmp/bench-venv/bin/python robustness/r1-profiles/gen/build_corpus.py --payloads 5

# 3. render the charts
/tmp/bench-venv/bin/python robustness/r1-profiles/gen/plot_profiles.py

# 4. (optional) the helper's end-to-end conformance guard
make -C src/jabcodeWriter
LD_LIBRARY_PATH=src/jabcode/build python3 robustness/r1-profiles/gen/test_profile_select.py
```

The degraded corpus (`data/corpus/`) is large and **regenerable**, so it is
gitignored; only the clean grid (`data/symbols/`), the measurement CSV/JSON, the
manifest, and the charts are committed.
