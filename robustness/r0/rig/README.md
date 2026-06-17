# R0 — Host-Side Decode-Rate Rig

A reusable host harness that runs the **jabcode decoder** over a directory of
labelled images and reports, per image, whether it decoded and — if not — which
pipeline stage it died at. It then aggregates a decode-rate and a fail-stage
histogram per condition. This is the core instrument of the robustness phase:
point it at any image set (camera captures, print scans, the synthetic
degradation generator) and get a comparable decode-rate + failure breakdown.

```
robustness/r0/rig/
├── r0_decode.c        # C probe: decodes ONE image, emits JSON (result + markers)
├── Makefile           # builds r0_decode, links src/jabcode/build/libjabcode.a
├── run_rig.py         # Python runner: manifest -> per-image JSONL + aggregate
├── run.sh             # one-shot: build + run over a manifest
├── manifest.jsonl     # the validation manifest (clean symbols + ws5 frames)
├── images/            # the validation corpus (self-contained, reproducible)
│   ├── clean/                 # 8 benchmark clean encodes  nc0..nc7
│   ├── clean-fullspectrum/    # 8 full-spectrum clean encodes nc0..nc7
│   └── ws5-frames/            # 5 real Nc1 handheld camera frames
└── results/
    ├── manifest.per_image.jsonl   # per-image outcome (one JSON object per line)
    └── manifest.aggregate.json    # decode-rate + fail-stage histogram per condition
```

## Quick start

```bash
cd robustness/r0/rig
./run.sh                       # builds, runs the bundled manifest, writes results/
./run.sh my_set.jsonl          # run a different manifest
./run.sh my_set.jsonl medium,nc   # bucket the aggregate by these manifest keys
```

Requires: `gcc`, `python3`, and the system `libpng16` / `libtiff` / `zlib` dev
libs (already present in this repo's build environment). `run.sh` builds
`libjabcode` from `src/jabcode` on demand.

---

## How it runs the decoder

`r0_decode.c` links `libjabcode` directly and calls the **exact decode path the
on-device bridge uses** (`swift-java-wrapper/src/c/mobile_bridge.c`,
`jabMobileDecodeCameraWithMeta`):

```c
jabSetDiagVerbose(1);               // emit FAIL_STAGE / DIAG_* markers
jabSetStrictPartIIRequired(1);      // refuse fabricated decodes on degraded input
status = ...;
result = decodeJABCodeEx(bitmap, NORMAL_DECODE, &status, symbols, MAX_SYMBOL_NUMBER);
jabSetStrictPartIIRequired(0);
jabSetDiagVerbose(0);
```

Running in-process (rather than shelling out to `jabcodeReader`) gives us, per
image: the decoder **`status`** code, the decoded **`Nc`** (`symbols[0].metadata.Nc`),
a precise `CLOCK_MONOTONIC` **decode time**, and the full **diagnostic marker
stream**, all without temp files.

`status` semantics (from `src/jabcode/detector.c`):

| status | meaning                                            |
|:------:|----------------------------------------------------|
| 0      | not detectable (no finder pattern found)           |
| 1      | finder pattern found, but not decodable            |
| 2      | partly decoded (COMPATIBLE_DECODE only — unused here) |
| 3      | fully decoded                                       |

## How fail-stage is extracted

The decoder is instrumented with greppable markers — the **same markers the
field traces use**. With `jabSetDiagVerbose(1)` they print to stdout. The probe
redirects stdout into a temp buffer for the duration of the decode, then keeps
only the **structural** marker lines (a whitelist; GRID/FP/colour dumps are
dropped so no image or payload content is ever surfaced). Sources of markers:

- **`JABCode Error:` lines** — the decoder's own ungated failure strings
  (`reportError` / `JAB_REPORT_ERROR`), e.g. `Too few finder pattern found`,
  `Invalid module color in primary metadata part 1 found`,
  `LDPC decoding for master metadata part 2 failed`, `Decoding data failed`.
  These name the failing stage and never echo payload bytes (verified across
  every call site in `detector.c` / `decoder.c` / `ldpc.c` / `image.c`).
- **`FAIL_STAGE=...`** — PartI / PartII metadata markers in `decoder.c`
  (`module_color`, `pair_bits`, `ldpc`, `side_version`, `ec_params`).
- **`DIAG_SYMBOL_DECODE`**, **`DIAG_PARTII_RESULT`**, **`DIAG_MODE0_DETECT`**,
  **`[PartI_DIAG]` / `[PartII_DIAG]`** — stage-result markers.

`run_rig.py` maps the markers (+ `status`) to a coarse taxonomy
(deepest-stage-wins, because the Nc fallback ladder can emit early-stage chatter
before failing deeper):

| fail_stage         | meaning                                                         |
|--------------------|-----------------------------------------------------------------|
| `NONE`             | success (decode_ok)                                             |
| `DETECT`           | finder / alignment / sampling / grid geometry (status 0 lands here) |
| `PALETTE_CLASSIFY` | colour palette / module-colour classification (PartI)          |
| `PARTII`           | PartII metadata: side version, EC params, matrix, PartI LDPC   |
| `LDPC`             | data-layer LDPC uncorrectable                                  |
| `DATA`             | final byte / encode-mode decode (`decodeData`)                 |
| `PAYLOAD_MISMATCH` | pipeline succeeded but bytes ≠ known payload (hash mismatch)    |
| `UNKNOWN`          | failed but no marker matched (should be rare; status-only fallback) |

## decode_ok + payload verification (security)

- **`payload_known: false`** → any clean decoder success (`status==3`) is `ok`.
- **`payload_known: true`** → the probe computes a **SHA-256 of the decoded
  bytes in-process** and emits only the digest + length. The runner compares
  that digest against the manifest's `payload_sha256`. A clean decode whose
  bytes don't match is **not** ok (`fail_stage = PAYLOAD_MISMATCH`).

The decoded payload **plaintext is never printed, logged, or stored** — only its
length and SHA-256 digest leave the probe. This holds for both the per-image
JSONL and the captured marker stream.

---

## Manifest schema (JSONL — one JSON object per line)

| field            | type    | required | meaning                                                        |
|------------------|---------|:--------:|----------------------------------------------------------------|
| `id`             | string  | yes      | unique id for the image                                        |
| `file`           | string  | yes      | image path: absolute, or relative to the manifest's directory  |
| `nc`             | int     | no       | expected colour index 0..7 (informational; `decoded_nc` is reported) |
| `ecc`            | any     | no       | informational ECC label                                        |
| `payload_known`  | bool    | no       | default `false`; if `true`, `payload_sha256` is required       |
| `payload_sha256` | hex(64) | iff known| expected SHA-256 of the decoded payload bytes                  |
| `medium`         | string  | no       | e.g. `print` / `screen` / `camera`                             |
| `conditions`     | string  | no       | free-form condition label (the default aggregate bucket key)   |

Lines that are blank or start with `#` are ignored.

Example:

```json
{"id":"bench-nc7","file":"images/clean/nc7-256c.png","nc":7,"payload_known":true,"payload_sha256":"e6816b03...","medium":"print","conditions":"clean-benchmark"}
{"id":"frame-01","file":"images/ws5-frames/analyzer-frame-01.png","nc":1,"payload_known":false,"medium":"camera","conditions":"ws5-realframe"}
```

## Per-image output schema (`results/<name>.per_image.jsonl`)

```json
{"id","file","nc","decoded_nc","medium","conditions",
 "decode_ok":0|1,"fail_stage","decode_ms","status","payload_match":true|false|null}
```

## Aggregate output schema (`results/<name>.aggregate.json`)

```json
{
  "manifest", "bucket_keys",
  "overall":      {"n","decoded","decode_rate","mean_decode_ms","fail_stage_histogram"},
  "by_condition": { "<bucket label>": { ...same shape... } }
}
```

---

## Pointing the rig at a NEW image set

The rig is fully decoupled from any particular corpus — a new set plugs in by
writing a manifest. Two common cases:

### 1. Unknown-payload set (e.g. field captures)

Drop the images somewhere, then write a manifest that points at them. No hashes
needed:

```bash
# one line per image; medium/conditions are your free-form labels
for f in /path/to/captures/*.png; do
  printf '{"file":"%s","id":"%s","payload_known":false,"medium":"camera","conditions":"lab-2026"}\n' \
    "$f" "$(basename "$f" .png)"
done > captures.jsonl

./run.sh captures.jsonl medium,conditions
```

### 2. Known-payload set (e.g. the synthetic degradation generator)

The synthetic generator knows each image's source payload, so it can emit the
expected SHA-256 directly into the manifest (compute `sha256(payload_bytes)` at
generation time). Then `decode_ok` becomes a true correctness check, and
`PAYLOAD_MISMATCH` surfaces decodes that "succeeded" to the wrong bytes.

Recommended generator contract — emit, alongside each generated PNG, one
manifest line:

```json
{"id":"syn-nc4-blur3-0007","file":"out/syn-nc4-blur3-0007.png","nc":4,
 "payload_known":true,"payload_sha256":"<sha256 of the source payload bytes>",
 "medium":"synthetic","conditions":"gaussian-blur sigma=3.0"}
```

Put one degradation knob per `conditions` value (e.g. `blur sigma=1.0`,
`jpeg q=40`, `rotate 7deg`) so the aggregate's `by_condition` buckets become a
decode-rate-vs-degradation curve, with the fail-stage histogram showing *where*
the decoder breaks first as that knob worsens.

> The probe never needs the plaintext — only the generator does, to compute the
> hash. The manifest carries the digest, so the corpus can be shared without
> leaking payloads.

---

## Validation results (bundled manifest)

`./run.sh` over the 21-image bundled manifest produces:

| condition          | set                          | decode-rate | fail stages |
|--------------------|------------------------------|:-----------:|-------------|
| clean-benchmark    | 8 clean encodes (nc0..nc7)   | **8/8 = 100%** | NONE×8   |
| clean-fullspectrum | 8 clean encodes (nc0..nc7)   | **8/8 = 100%** | NONE×8   |
| ws5-realframe      | 5 real Nc1 camera frames     | **0/5 = 0%**   | DETECT×5 |
| **overall**        | 21 images                    | **16/21 = 76.2%** | NONE×16, DETECT×5 |

- The **clean ground-truth symbols decode at 100%** across every colour mode
  Nc0..Nc7 in both sets, and every one passes known-payload SHA-256
  verification — the sanity check that the rig and the decode path are wired
  correctly.
- The **ws5 real handheld frames all fail at DETECT** (`Too few finder pattern
  found`): in these ~900 KB frames the JABCode is too small / motion-blurred for
  the finder-pattern search. This is the expected "mix" for raw single-frame
  camera input — and exactly the failure mode the robustness phase exists to
  drive down. Fail-stage attribution pinpoints it as a *detection* problem, not
  a colour/LDPC problem, which is the actionable signal.

Marker capture is verified end-to-end: clean decodes show the
`[PartI_DIAG] SUCCESS → [PartII_DIAG] SUCCESS → DIAG_SYMBOL_DECODE result=ok`
chain; ws5 failures show `DIAG_MODE0_DETECT … → Too few finder pattern found`.
