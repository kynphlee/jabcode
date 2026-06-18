# Tier-2 Real-Capture Confirmation (2026-06-18)

This field-match analysis predicted the R0 synthetic corpus under-represents the
field's colour-cast failure, and specified a ~20-image Tier-2 print set to close
the gap. That set was captured and run through the rig. **The prediction is
confirmed.**

## Capture set
23 warm-stock prints (4-colour + 8-colour JABCode, known payloads), photographed
in normal indoor light, well-framed and in focus. (No captured images or
payloads are committed — decode-rate / fail-stage statistics only.)

## Rig results — stock decoder
- **3/23 = 13% decoded; 20/23 failed at `LDPC`.**
- A genuine colour-recovery collapse: the symbol is detected and its metadata
  read, but the data-module colours drift far enough on warm/low-gamut stock
  that error-correction is overwhelmed.
- This is exactly the regime the synthetic corpus could **not** produce (its
  uniform chroma wash dies at `DETECT`, never reaching colour recovery) — the
  field-vs-synthetic gap, now measured on real data.

## Decode-side A/B — the reopened WB candidate
- The gated WB-normalization candidate on the identical captures: **13% → 13%
  (no lift).** Its palette-resolution gate suppresses it on the strong 8c cast,
  as the WB experiment predicted.
- With the three synthetic refutations, this is the **fourth** decode-side colour
  candidate to show no lift — now on representative data.

## Verdict (confirmed)
The robustness lever is **encode-side** (Nc / medium selection — see
`robustness/r1-profiles/`), not decode-side. The decoder is adequate for the
colour it is handed; the lever is choosing colours the medium can preserve.

## Caveat
The 4-colour shots carried a multi-symbol framing wrinkle (a partial neighbour
code at the frame edge), so the per-Nc split from this set is not clean; the
"low Nc for hostile media" direction rests on the synthetic Nc-curve plus the
field baseline (nc1 73.7% vs nc7 13.6%), not on this set.
