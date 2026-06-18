# R4 — The Acquisition Frontier (capture-side quality gate)

**Date:** 2026-06-18 · **Evidence:** on-device Galaxy S25 screen-scan dataset (see `ondevice-screen-dataset.md`)

## Premise — the decoder is solved; acquisition is not

From 4,050 live camera frames across Nc 1–7 (screen):

```
4,050 frames  →  448 DETECT (11.1% framing yield)  →  367 decoded
                 └─ decoder skill (decode|detect): 82% overall, 4 ms/frame
```

- **Compute** is solved (latency campaign: encode 19.6×, decode popcount-parity, 4 ms on ARM).
- **Correctness** is solved (nc2 consensus backstop; 8-colour 0%→87%).
- **The remaining loss is ACQUISITION:** the human–camera loop produces a decodable frame only **11%** of the time, **flat across every colour mode** (it's colour-blind — geometry/focus/distance), and the app gives the user **zero feedback** about why.

## What is NOT the lever (closed lines)
- Decode-side colour fixes — R1 refuted (6 non-results: ΔE, WB, adaptive-binarize).
- Capture-side signal processing (white-balance, multi-frame fusion) — R3 refuted (Δ=0).
- The colour-LDPC misses at high Nc — an **encode-time** lever (Nc-per-medium, #95 profiles), not optimizable downstream.

## The lever: acquisition UX, not signal processing
89% of frames die at DETECT. The detector currently fails silently. The user is framing blind. The fix is to **close the feedback loop** and **feed the decoder only good frames** — a product/UX layer, not a new algorithm. It lifts all 8 modes at once.

## Approach — three layers, in priority order

### Layer 1 — Make the invisible visible (instrumentation + guidance) — highest leverage, cheapest
- **Surface the detector's own failure reason** (too-few-finders vs sampling-failed) — it already computes this; it just isn't logged or shown.
- **Cheap pre-decode quality probe** (the gate's signals, all O(pixels), far under the 33 ms frame budget):
  - **Sharpness** — Laplacian variance over the ROI (blur).
  - **Finder pre-scan** — fast candidate-finder count → "code in frame & roughly square."
  - **Coverage / module size** — finder bounding box → px/module (detector wants 3–20; <3 = too far, clipped = too close).
  - **Stability** — frame-to-frame delta (camera steady?).
- **Real-time guidance** — reticle + hints ("move closer", "hold steady", "center the code", "more light"). This is the loop the app has never had.

### Layer 2 — Best-frame selection (NOT fusion)
R3 proved fusion adds nothing; **selection** does. Among recent frames, decode the **sharpest** one rather than every frame. Single-frame, cheap, raises each attempt's odds.

### Layer 3 — Confidence gate
Accept a read on **confidence**, not first-decode. The consensus backstop already does anti-fabrication on the decode side; extend that to the UI "success" gate.

## Methodology (R0-style, evidence-first)
1. **Instrument the funnel** — add the quality metrics + DETECT-failure-reason logging to the diagnostic app. Re-capture → now we *see* why each frame fails (too-far vs blur vs oblique).
2. **Build the predictor on real data** — correlate the metrics with `DECODE SUCCESS` over a capture corpus → the gate's decision thresholds.
3. **Wire the guidance** — reticle + hints driven by the predictor.
4. **Validate** — A/B **framing yield** and **time-to-first-decode**, gate on vs off. Target: lift the 11% yield and cut seconds-to-decode.

## Principle
The decoder does not change. We help the human produce a decodable frame faster and hand the decoder only good frames. This is the one capture-side lever R3 endorsed — UX, not signal processing — now quantified by the 89%-at-DETECT funnel.
