# gamutlock-wasm — bridge #3 (browser/WebAssembly)

The fork's codec compiled to WebAssembly with Emscripten, exposed behind a
BarcodeDetector-polyfill-shaped JS wrapper, plus a self-contained scan page:
`getUserMedia` → canvas → in-page WASM decode → POST the decoded mark to the
studio verify endpoint → render the verdict. The install-free **ambient lane**
of the two-lane reader model: a code deep-links to the scan page, and the page
IS the reader.

Same portable C source as the JNI and Swift bridges (`src/jabcode`) — no codec
fork. Spike provenance, measurements, and environment receipts live in
`bench/results/` (see `ENV.json` for the exact source commit and toolchains).

## Layout

| file | role |
|---|---|
| `wasm_bridge.c` | the whole bridge ABI (`gw_*`): persistent RGBA frame buffer + strict camera-path decode, mirroring `robustness/r0/rig/r0_decode.c` / the mobile bridge call sequence |
| `Makefile` | `make all` (dist/), `simd`, `size`, `fixture`, `smoke`, `pxsha`, `quietprobe` |
| `gamutlock-detector.mjs` | `GamutlockDetector` — `detect(ImageData\|CanvasImageSource) → [{format:'jab_code', rawValue, rawBytes, colorCount, cornerPoints, boundingBox}]`, deferred WASM instantiation |
| `scan.html` | the scan page (camera loop, file drop, `?img=` deep-link demo, verify POST, verdict panel with the studio verdict vocabulary, tamper-replay proof button) |
| `bench.html` + `bench/` | browser side of the decode-rate A/B + manifest builder + result join |
| `dev-server.mjs` | zero-dep static server + `/api/*` reverse proxy to the studio (same-origin verify, the resolver-hosting shape) |
| `test/node-smoke.mjs` | guard: host-native encode → WASM decode byte-identity, colour metadata, corners, corrupted-frame negative control |
| `tools/` | host-built helpers: fixture encoder (via the mobile bridge), pixel-identity hasher, latency-fair quiet probe |

## Build

```sh
# once: https://emscripten.org — ./emsdk install latest && ./emsdk activate latest
source <emsdk>/emsdk_env.sh
make -C ../src/jabcode build/libjabcode.a   # host lib for fixtures/probes
make all        # dist/gamutlock-codec.{mjs,wasm}
make smoke      # native-encode → wasm-decode guard
node dev-server.mjs   # http://localhost:8321/scan.html
```

**Bundle honesty** — what is excluded and why is documented at the top of the
`Makefile` (`image.c` = PNG/TIFF *file* I/O the raw-bitmap path never touches;
`detector_synthetic.c` = test-only synthetic bypass). Everything else,
including the fork's decode-rate work (strict Part-II gate, adaptive palette,
colour calibration, kd-tree/Lab classification, decode profiler), is compiled
in. The exported ABI is decode-only, so LTO strips the unreachable encode
paths — this is functionally a reader-only module (which keeps the zxing-wasm
"reader-only" comparison like-for-like). Measured: **wasm 106,405 B raw /
50,023 B gzip −9** (+12.6 kB JS glue);
SIMD variant 126,660 B / 53,048 B. zxing-wasm's reader-only build is ~1.04 MiB
for scale.

## The A/B protocol (`bench/`)

Corpus = the R0 rig's existing instrument: clean 4/8/16-colour benchmark
symbols, their deterministic synthetic degradations (blur / chroma / downscale
/ jpeg / lighting / perspective ladders), and the archived ws5 real Camera2
frames. Success = SHA-256 of the decoded bytes equals the clean-source hash
(hashes are never recomputed from degraded decodes).

Fairness gates are *enforced*, not asserted:

1. **Pixel identity** — every image's canvas `ImageData` bytes are SHA-256'd
   in the browser and compared against the native `readImage` bytes
   (`tools/rgba_sha.c`). Images that differ are excluded from rate rows and
   listed. (Outcome: all 57 synthetic/clean images identical; the 5 archived
   ws5 real-frame PNGs differ and are EXCLUDED — root cause: those PNGs carry
   cICP/cHRM/gAMA colour chunks and `readImage`'s libpng simplified API
   applies that colour management, while the browser and PIL do not. The raw
   jsonl shows neither side decodes those frames anyway. The production
   camera path never decodes PNGs, so this is a bench-corpus caveat, not a
   product risk.)
2. **Latency separation** — the rig probe's decode times include its
   diag-verbose capture; latency comparisons use `tools/quiet_probe.c`
   (identical decode call, no instrumentation), and the join asserts the two
   native probes agree on every decode outcome.

Reproduce: `python3 bench/make_manifest.py`, run the rig + quiet probe +
`rgba_sha` (see `bench/join_results.py` header), open
`bench.html` / `bench.html?build=simd`, then `python3 bench/join_results.py`.

## Spike results (2026-09-13, see `bench/results/AB_TABLE.md`)

- **Decode rate: zero delta at the measured colour classes (4/8/16).**
  Native, wasm, and wasm-simd agree on every one of the 57 pixel-identical
  images — including the failures (4c blur@2/3, chroma@0.7 at 8c/16c, 16c
  perspective@35 fail identically on all three). The failing cells double as
  the harness's negative control. Scope note: the R0 corpus also carries nc0
  and 32–256-colour degradation ladders that this spike did not run.
- **Latency:** medians 4c 3.7 ms / 8c 5.0 ms / 16c 5.7 ms in-browser vs
  2.3 ms-class native on the same host — a 1.6–3.1× penalty, inside the
  expected 2–4× band and far under a camera frame budget. `-msimd128`
  autovectorization changed nothing material (hand-written kernels would be
  needed; not worth it at these numbers). Caveats: per-cell times are
  single-shot (n=1 per image), and the wasm side got one unrecorded warm-up
  decode while the native quiet probe ran cold — the one cell where wasm
  beats native (4c blur@1.0, 6.6 vs 4.2 ms) is native cold-start, not a
  wasm win.
- **Verify round-trip:** studio-issued mark → in-browser decode → POST →
  `TRUSTED` (claims rendered); tamper-replay of the same mark → `TAMPERED`
  ("v2 CRC mismatch"). Both verdicts through the real pipeline.

## Known limits / next

- Live-camera pass on physical devices (Android Chrome, iOS Safari) is
  staged but requires hardware: expect lower Nc ceilings than native capture
  (`getUserMedia` has no ROI-locked AE; iOS Safari has no torch) — quantify,
  don't assume. The `?img=` deep-link mode exists so the pipeline is
  demonstrable without a camera.
- The scan page assumes same-origin `/api/studio/verify` (resolver-hosted in
  production; `dev-server.mjs` proxies locally). No CORS story is intended.
- In-browser SD-JWT/CP-ABE verification is a later phase (rabe → wasm32), per
  the handoff.
