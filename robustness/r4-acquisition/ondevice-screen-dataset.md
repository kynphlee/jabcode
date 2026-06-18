# On-device decode-rate dataset — Galaxy S25 (SM-S938U), screen, 2026-06-18

First on-device decode-rate + latency evidence (the benchmark suite is x86-only).
Built `:diagnostic-app` (carries the optimized core + the decode_profile/CMake fix, PR #102).
**Decode rates and fail-stages only — no payloads.** Source: 7 logcat traces
`trace-20260618_18{41,42,43,45,46,47}xx-nc{1..7}.logcat`.

## Per-Nc (one live screen-scan session each)

| Nc | colours | frames | →detect | framing-yield | →decode | decode\|detect | misses die @ | med decode |
|---:|---:|---:|---:|---:|---:|---:|---|---:|
| 1 | 4   | 495 | 54 | 11% | 54 | **100%** | — (no misses) | 4 ms |
| 2 | 8   | 549 | 61 | 11% | 53 | **87%**  | `pair_bits` (nc2 residue) | 4 ms |
| 3 | 16  | 819 | 91 | 11% | 60 | **66%**  | `ldpc` | 4 ms |
| 4 | 32  | 603 | 66 | 11% | 66 | **100%** | — (no misses) | 4 ms |
| 5 | 64  | 531 | 59 | 11% | 49 | **83%**  | `ldpc` | 4 ms |
| 6 | 128 | 567 | 63 | 11% | 39 | **62%**  | `ldpc` | 4 ms |
| 7 | 256 | 486 | 54 | 11% | 46 | **85%**  | `ldpc` | 4 ms |

## Aggregate
- **4,050 frames** processed → **448 detect (11.1% framing yield)** → **367 decoded**.
- **decode|detect (decoder skill): 82% overall, 83% mean-per-Nc**, range 62–100%.
- **end-to-end (decode/frames): 9.1%** — dominated by the framing ceiling, not the decoder.
- **Latency: 4 ms median per frame, flat across all Nc** — real-time at any colour count.

## Three lenses on "decode rate" (don't conflate)
- **Framing yield** = detect/frames = **11% flat, colour-blind** → capture quality (the acquisition target).
- **Decoder skill** = decode/detect = **62–100%** → the decoder; strong, shows the expected Nc-dependence.
- **End-to-end** = decode/frames = **9%** → what one frame yields; framing-limited.

## vs the June-7 screen baseline (decode|detect basis, directional)
Nc2 **0% → 87%** (nc2 consensus backstop, commit `1278fa0`, post-baseline) ·
Nc7 **13.6% → 85%**, Nc6 41%→62%, Nc5 51%→83% — the high-Nc screen collapse is largely repaired.
**Caveat:** Nc2 is cleanly attributable to the commit; the Nc5–7 lift is confounded with capture conditions (a same-screen old-vs-new build A/B would settle it).

## Fail-stage reading
- `none` (Nc1, Nc4): every detected frame decoded.
- `pair_bits` (Nc2): residual metadata failures (mitigated nc2 anomaly).
- `ldpc` (Nc3/5/6/7): colour error-correction overwhelmed — the colour-separability wall (encode-time lever).
- **The dominant loss is upstream of all of these — 89% at DETECT (framing), colour-blind → the acquisition frontier (see DESIGN.md).**
