# H_roi_detector_quality — Open quality hypothesis for the default ROI detector

| Field        | Value                                                                                              |
| ------------ | -------------------------------------------------------------------------------------------------- |
| **Filed**    | 2026-05-27 (Bayesian Council Session 7 socratic review of ROI implementation plan)                  |
| **Status**   | **Pending external trigger 2026-06-01** — PR 3 (ROI detector swap) has not shipped; this entry is reserved for the moment that work lands and the heuristic begins accumulating field data. NOT active investigation surface. |
| **Binding**  | Externally gated on PR 3 shipment |
| **Owner**    | N/A — assigned at PR 3 shipment |
| **Severity** | Medium-High *if* PR 3 ships and false-positive/negative rates exceed thresholds; pre-PR 3 no production impact |
| **Related**  | `docs/roi-detection-implementation-plan.md` (§3 Heuristic default), `H_mode0_decodeModuleNc_classifier.md` (resolved sibling), `H_partI_clean_data_failure.md` (resolved-by-bridge sibling) |

## Reclassification note (2026-06-01)

Per the 2026-06-01 register-cleanup discipline (Bayesian Council session
bc-2026-06-01-04 + user directive to resolve open entries): this entry was
incorrectly classified as "Open" alongside actively-investigated entries. It
is pre-implementation by design — the heuristic ROI detector it tracks does
not yet exist in any merged code. There is no investigation surface and no
work to claim until PR 3 lands.

Reclassified to "Pending external trigger" to make the gate visible. The
investigation triggers (A-D) remain as documented; this entry will auto-
elevate to "Open — active" when any trigger fires post-PR-3-shipment.

This is a *reframing*, not a closure — the entry's prophetic value is preserved.

## The hypothesis

The default `HeuristicJabCodeROIDetector` shipping in PR 3 (which reuses the existing JABCode finder-pattern detector logic at downscaled ~320×180 resolution) will have a non-trivial false-positive and/or false-negative rate in field conditions that the synthetic test fixtures cannot fully simulate. If that rate exceeds tolerance, the SDK should switch to (or augment with) an ML-based detector — option B from Session 6 — that was deliberately deferred.

## Why this is filed pre-implementation

Session 7's Lazarus raised the concern: "The plan currently treats Option A (heuristic) as default without specifying the trigger conditions under which we'd reconsider Option B (ML)." Filing this hypothesis now — before PR 3 ships — preserves the option to switch implementations later without re-deriving the rationale.

Continuous scan is the SDK's primary use case (§0.1 of the implementation plan); a heuristic detector that occasionally engages tracking on a non-JABCode produces a uniquely bad UX (camera zooms in on nothing, user thinks the scanner is broken). The Must-be tier is non-negotiable; the implementation question is which mechanism best satisfies it.

## Triggers (when this hypothesis activates)

After PR 3 ships, the heuristic default will accumulate empirical data on real device traces. The hypothesis activates when ANY of the following occur:

- **Trigger A — False-positive rate exceeds 5% on field traces**: a single consumer-reported "scanner zoomed in on the wrong thing" is a soft signal; ≥5% incidence in collected diagnostic-app traces is a hard signal.
- **Trigger B — False-negative rate exceeds 20% on field traces**: heuristic fails to find a JABCode that the full decoder would have found at 1× zoom; the SDK is now WORSE than no ROI detector at all.
- **Trigger C — Adversarial regression**: an adversarial fixture from §9.4 starts triggering the detector after a code change. This is a regression-suite signal.
- **Trigger D — Customer/consumer pushback**: a deploying SDK consumer reports that the heuristic underperforms their internal benchmarks vs MLKit or other available barcode detectors.

## Investigation checklist (cold pickup)

When this hypothesis activates, an investigator should be able to start cold:

1. **Read PR 3's measured false-positive / false-negative rates** from the synthetic fixture tests (`src/jabcode/test/data/roi-adversarial/`). Compare against field-trace rates.
2. **Audit recent traces** for `Camera2Controller: scanner state -> TRACKING(bbox=...)` markers followed by failed-decode sequences — these are heuristic false positives.
3. **Reproduce the failing case** on the diagnostic app, capture the bitmap that the heuristic accepted (or rejected).
4. **Evaluate alternatives**:
   - MLKit barcode scanner — does it correctly identify the failing case?
   - Custom TFLite — what training data would we need?
   - Tighter heuristic thresholds — can we just raise the confidence bar?
5. **Decide between**: (a) tighter heuristic, (b) MLKit as fallback secondary, (c) MLKit as default replacement, (d) custom TFLite.

## Why triggered binding (not scheduled)

Per the same pattern established by `H_partI_clean_data_failure.md` and `H_mode0_partI_decode_failure.md`: scheduled commitments create commitment debt. We don't know yet whether the heuristic will be good enough; pre-committing to investigate ML at a specific date would be guessing. The trigger-based pattern activates the investigation only when evidence demands it.

## Cross-references

- `docs/roi-detection-implementation-plan.md` §3 — the heuristic detector being filed against
- `docs/roi-detection-implementation-plan.md` §9.4 — the adversarial fixture test suite that catches false positives at build time
- `H_partI_clean_data_failure.md`, `H_mode0_partI_decode_failure.md` — sibling open hypotheses; if either resolves, the ROI detector may have less work to do
- `project_jabcode_screen_vs_print_physics.md` (memory) — physics that constrains the ROI detector's input quality
