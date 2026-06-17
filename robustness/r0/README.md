# R0 — The Robustness Instrument

R0 is the **evidence-first** entry point of the jabcode decoder robustness
phase. Before changing a single line of the decoder (adaptive binarization,
palette recovery, ...), R0 establishes *where the decoder actually breaks* and
gives a **repeatable, payload-verified decode-rate harness** to measure any
future change against. It is an instrument, not a fix: it tells you which lever
to pull and lets you prove a pull worked.

It has three components, in the order you'd build an argument:

```
robustness/r0/
├── trace-baseline/   1. WHAT THE FIELD DOES   — decode-rate mined from real on-device traces
├── rig/              2. THE MEASURING DEVICE  — host harness: labelled images -> decode-rate + fail-stage
├── synthetic/        3. THE CONTROLLED INPUT  — deterministic degradations of clean symbols, fed to the rig
└── validation/       investigations that ground surprising rig results (e.g. the perspective artifact)
```

1. **`trace-baseline/`** reduces 56 real `jabauth-android` diagnostic traces
   (7,658 decode attempts) into a decode-rate-by-Nc/medium table with a failure
   taxonomy. It answers the strategic question: *is the decoder
   detection-bound or classification-bound?*
2. **`rig/`** is a reusable host harness. Point it at any labelled image set
   (a JSONL manifest) and it reports, per image, whether it decoded and — if not
   — which pipeline stage it died at, then aggregates a decode-rate + fail-stage
   histogram per condition. Known-payload images are verified by **SHA-256 of
   the decoded bytes** (the plaintext never leaves the probe).
3. **`synthetic/`** generates a **deterministic** corpus of degraded symbols
   (blur, perspective, lighting, downscale, jpeg, chroma) from clean
   ground-truth. Same input + same param => identical output bytes, so two
   decoder builds can be A/B-compared on byte-for-byte identical pixels. A
   bridge script converts its manifest into the rig's schema.

Together they form a closed loop: the **field baseline** says where to invest,
the **synthetic corpus** isolates each failure mode on controlled inputs, and
the **rig** scores both — and will score the decoder change that R1 makes.

---

## 1. Field baseline — the verdict

Mined entirely from real on-device decode attempts (no synthetic data, no
re-decoding): **56 traces, 7,658 complete attempts.** Full method and per-group
table in [`trace-baseline/REPORT.md`](./trace-baseline/REPORT.md).

> **Verdict: for the polychrome modes the robustness phase targets, failures are
> classification / palette-bound, not detection-bound — so the work points at
> R1 (colour classification / palette + LDPC metadata recovery), not R2
> (detection).**

The evidence: across all attempts, post-detection classification/palette
failures (`module_color` + `pair_bits` + `side_version` + `ldpc` = 3,076)
outnumber detection failures (`detect` = 2,571), and detection *succeeds* on the
majority of frames. The aggregate `detect` count is inflated by the nc0
Mode-0-monochrome regime (1,640 of those 2,571 live in nc0, a known separate
low-yield case at 2.3%); strip nc0 and the field is decisively
classification-bound (~931 detect vs ~2,053 classify). Every polychrome mode —
nc2 (15.6%, `ldpc`/`pair_bits`), nc5/nc6 (`ldpc`), nc7 (`ldpc`, 144:13),
ncX (`ldpc`) — fails *after* the symbol is found. The clean counter-example is
nc1/screen, whose failures are purely detection: when classification is easy
(bright screen, low Nc), detection is the only thing left to fail.

This is exactly why the synthetic corpus weights its families toward the
colour/registration/luminance surfaces a binarizer and palette classifier must
survive — R0 measures the lever the field says matters.

---

## 2. The rig — how decode-rate is measured

[`rig/`](./rig/) ([README](./rig/README.md)) builds `libjabcode` + a small C
probe (`r0_decode.c`) that links the decoder directly and calls the **exact
decode path the on-device bridge uses** (`decodeJABCodeEx` with
`jabSetStrictPartIIRequired(1)` so fabricated decodes on degraded input are
refused). Per image it emits the decoder `status`, decoded `Nc`, a
`CLOCK_MONOTONIC` decode time, and a whitelisted **diagnostic marker stream** —
no temp files, no payload plaintext.

`run_rig.py` maps each image's `(status, markers)` to a coarse **fail-stage**
taxonomy (deepest-stage-wins):

| fail_stage | meaning |
|------------|---------|
| `NONE` | success (and, if known-payload, SHA-256 verified) |
| `DETECT` | finder / alignment / sampling / grid geometry |
| `PALETTE_CLASSIFY` | colour palette / module-colour classification (Part I) |
| `PARTII` | Part II metadata: side version, EC params, matrix, Part I LDPC |
| `LDPC` | data-layer LDPC uncorrectable |
| `DATA` | final byte / encode-mode decode |
| `PAYLOAD_MISMATCH` | pipeline succeeded but bytes ≠ known payload (hash mismatch) |
| `UNKNOWN` | failed but no marker matched (status-only fallback) |

**Known-payload verification (security):** for `payload_known: true` rows the
probe computes a SHA-256 of the decoded bytes in-process and emits only the
digest; the runner compares it to the manifest's `payload_sha256`. A clean
decode whose bytes don't match is `PAYLOAD_MISMATCH`, not a success. The
decoded plaintext is never printed, logged, or stored.

The rig is corpus-agnostic — a new image set plugs in by writing a manifest.

---

## 3. The synthetic corpus — controlled inputs

[`synthetic/`](./synthetic/) ([README](./synthetic/README.md)) applies six
parameterized, **deterministic** degradation families to the clean
ground-truth symbols (one per colour mode Nc 0..7):

| family | models | param | attacks |
|--------|--------|-------|---------|
| `blur` | defocus / motion smear merging modules | Gaussian sigma px | luminance |
| `perspective` | off-axis capture; projective tilt | tilt angle deg | registration |
| `lighting` | non-uniform illumination (the key adaptive-threshold case) | peak swing 0..1 | luminance |
| `downscale` | distance / low-res sensor toward the detector floor | target px/module | luminance |
| `jpeg` | lossy compression: 8×8 ringing + chroma bleed | JPEG quality | colour |
| `chroma` | screen-induced pink/white wash | desat+warm 0..1 | colour |

The committed **sample** set is 144 images (8 Nc × 6 families × 3 params), all
PNG and rig-readable. (The `jpeg` family does a genuine in-memory JPEG roundtrip
to bake real ringing, then saves the result as a **lossless PNG** — the rig's
`readImage` links libpng/libtiff and cannot decode JPEG, and a PNG container is
lossless so the compression damage survives in the pixels.) Determinism is the
whole point: a decoder A/B compares two builds on the *same* pixels.

---

## How to run the whole thing from a clean checkout

No regeneration of the image corpus is needed — the 144-image sample is
committed in full. Two steps: build the rig manifest from the committed
synthetic corpus, then run the rig over it.

Prereqs: `gcc`, `python3`, system `libpng16` / `libtiff` / `zlib` dev libs (the
rig builds `libjabcode` from `src/jabcode` on demand). The synthetic scripts use
only the Python stdlib for the bridge; regenerating images additionally needs
Pillow + numpy.

```bash
# From the repo root.

# 1. Build the rig manifest from the committed synthetic corpus.
#    to_rig_manifest.py reads synthetic/out/manifest.jsonl and reuses the
#    verified per-Nc payload_sha256 from rig/manifest.jsonl (the synthetic
#    images are deterministic degradations of those same source symbols, so
#    they share the payload). Output is generated (it embeds absolute image
#    paths) and gitignored — regenerate it, don't commit it.
python3 robustness/r0/synthetic/to_rig_manifest.py

# 2. Run the rig over the bridge manifest, bucketed by degradation condition.
#    NB: run.sh cd's into the rig dir, so pass the manifest as an ABSOLUTE path.
robustness/r0/rig/run.sh \
  "$(pwd)/robustness/r0/synthetic/out/rig_manifest.jsonl" conditions
```

This writes `robustness/r0/rig/results/rig_manifest.per_image.jsonl` (per-image
outcome) and `robustness/r0/rig/results/rig_manifest.aggregate.json`
(decode-rate + fail-stage histogram per condition), and prints a summary.

To regenerate the image corpus itself (e.g. after editing `degrade.py`), see
[`synthetic/README.md`](./synthetic/README.md) — `degrade.py --full` writes the
larger ladder to the gitignored `out-full/`.

---

## First-pass results (synthetic corpus, 144 images)

Decode-rate by degradation cell from the run above (stock decoder, strict
Part II). Overall **119/144 = 82.6%**, mean ~39 ms/image, **0 PAYLOAD_MISMATCH**
— every successful decode passed SHA-256 verification, which is the sanity
check that the bridge hashes and the decode path are wired correctly.

| degradation | param | n | decoded | rate | fail stages |
|-------------|-------|--:|--------:|-----:|-------------|
| `blur`        | 1.0 | 8 | 8 | **100%** | NONE×8 |
| `blur`        | 2.0 | 8 | 7 | 88%  | NONE×7, LDPC×1 |
| `blur`        | 3.0 | 8 | 4 | 50%  | NONE×4, DETECT×1, LDPC×3 |
| `perspective` | 20  | 8 | 8 | **100%** | NONE×8 |
| `perspective` | 30  | 8 | 7 | 88%  | NONE×7, LDPC×1 |
| `perspective` | 35  | 8 | 2 | 25%  | NONE×2, LDPC×6 |
| `lighting`    | 0.3 | 8 | 7 | 88%  | NONE×7, LDPC×1 |
| `lighting`    | 0.5 | 8 | 6 | 75%  | NONE×6, LDPC×2 |
| `lighting`    | 0.7 | 8 | 5 | 62%  | NONE×5, LDPC×3 |
| `downscale`   | 3   | 8 | 8 | **100%** | NONE×8 |
| `downscale`   | 4   | 8 | 8 | **100%** | NONE×8 |
| `downscale`   | 6   | 8 | 8 | **100%** | NONE×8 |
| `jpeg`        | 30  | 8 | 7 | 88%  | NONE×7, LDPC×1 |
| `jpeg`        | 50  | 8 | 8 | **100%** | NONE×8 |
| `jpeg`        | 70  | 8 | 8 | **100%** | NONE×8 |
| `chroma`      | 0.3 | 8 | 8 | **100%** | NONE×8 |
| `chroma`      | 0.5 | 8 | 8 | **100%** | NONE×8 |
| `chroma`      | 0.7 | 8 | 2 | 25%  | NONE×2, DETECT×6 |
| **overall**   | —   | **144** | **119** | **82.6%** | NONE×119, DETECT×7, LDPC×18 |

How to read it:

- **The low-degradation cells decode at ~100%** (`blur@1.0`, `chroma@0.3`,
  `jpeg@70`, `downscale@6`), all SHA-256-verified — the rig and the bridge are
  correct. (If the bridge hashes were wrong these cells would read
  `PAYLOAD_MISMATCH`, not `NONE`.)
- **`perspective` holds to ~30° tilt, then fails through `LDPC`.** 100% at 20°,
  88% at 30°, 25% at 35° — and the failures are `LDPC`, not `DETECT`: the decoder
  finds and samples the tilted symbol, but grid-sampling precision erodes the
  colours faster than error-correction can recover. (An earlier corpus bug — a
  too-thin 4-module quiet zone that let the warped symbol run flush to the canvas
  edge — masqueraded as a 0% `DETECT` wall; the realistic pinhole warp + 12-module
  quiet zone here is the corrected model. See
  [`validation/perspective.md`](./validation/perspective.md): jabcode's true tilt
  ceiling is ~30°, a *sampling-precision* limit, vs ~66° for a mature QR reader on
  the identical warp.)
- **`lighting` and `blur` degrade through `LDPC`.** As the gradient steepens or
  the smear widens, the symbol is still *found* but colour/metadata recovery
  fails at the LDPC layer — the *classification* surface, the R1 lever the field
  baseline pointed at.
- **`downscale` and `jpeg` are robust** down to the detector floor (3 px/module)
  and to quality 30 — resolution loss and block ringing are largely absorbed,
  with only the harshest jpeg cell dropping a single LDPC failure.
- **`chroma` is bimodal:** harmless until the warm/desaturate wash is heavy
  (`0.7`), where it collapses into `DETECT` — the colour cast defeats the finder
  search rather than just the palette.

This first pass is the **baseline a decoder change is measured against**: rerun
the identical command after an R1 change and diff the per-condition decode-rates
cell-by-cell. The `LDPC`-dominated `lighting`/`blur`/`chroma` cells are where
adaptive binarization / palette recovery should move the number — the R1 lever
the field baseline pointed at. `perspective` (a ~30° sampling-precision ceiling)
and the harsh `chroma@0.7` cliff are the residual detection (R2) story — milder,
and lower-priority once the quiet-zone artifact is removed.
