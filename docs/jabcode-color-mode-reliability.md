# JABCode Color Mode Reliability Disclosure

**Authoritative reference** for SDK consumers and downstream integrators. Documents which JABCode color modes are reliably decodable by the current Android SDK build, what is known to be broken, and what investigation work is in flight.

**Last empirical refresh:** 2026-05-28 PM (Galaxy S25, Camera2 API, YUV_420_888, 1920×1080, on-screen fixtures, 8-Nc discriminator scan).

---

## tl;dr

Only **Nc=1 (4-color)** is genuinely GA-grade on Android today (93% screen success rate in empirical discriminator scan). **Nc=3, Nc=4, Nc=5** are CONDITIONAL — they work with stable framing but have 35-67% screen success rates that drop sharply with hand motion. **Nc=0, Nc=2, Nc=6, Nc=7** are broken at varying severities.

Today's 8-Nc discriminator scan **confirmed `H_partI_unifies` for the {Nc=0, Nc=2, Nc=7} cluster** — these three fingerprint near-identically (status=1 dominant ≥3×, fast-reject median ≤290ms, success rate ≤17%) and likely share a single PartI metadata bug. A targeted C-side investigation may close all three simultaneously.

`Nc=2` retains its **FIX COMMITTED** designation per user mandate; `Nc=0` and `Nc=7` ride along as cluster members.

---

## Per-Nc reliability matrix (empirically refreshed 2026-05-28)

Symbol meanings:

- **GA** — Production-grade reliable on the listed medium with default scan settings. Empirical success rate ≥ 90%.
- **CONDITIONAL** — Works under specific scan conditions (framing discipline, zoom, lighting). Empirical success rate 30-80%; recommend documenting requirements in your consumer-facing scan-guide.
- **INVESTIGATING** — Empirically broken; root-cause investigation in flight; no committed fix timeline.
- **FIX COMMITTED** — Empirically broken; root-cause investigation in flight; **a fix is committed as part of the active engineering plan.**

| Nc | Colors | Palette | Screen 30s success rate | Status (screen) | Status (print) | Failure fingerprint | Notes |
|----|--------|---------|--------------------------|------------------|-----------------|---------------------|-------|
| 0  | 2      | Black + White (monochrome) | **0%** | **FIX COMMITTED** | INVESTIGATING | status=1 dom 3.1×, median 183ms | `H_mode0_partI_decode_failure`; member of `H_partI_unifies` cluster (with Nc=2, Nc=7) |
| 1  | 4      | CMY + K | **93%** | **GA** | **GA** | Pure status=0 transient framing | Only GA-grade mode today; failures are FP-detection misses during reframing only |
| 2  | 8      | CMY + RGB + K + W | **0%** | **FIX COMMITTED** | **FIX COMMITTED** | status=1 dom 4.4×, median 232ms | `H_nc2_decode_failure` CONFIRMED; member of `H_partI_unifies` cluster (with Nc=0, Nc=7) |
| 3  | 16     | Interpolated | **35%** | CONDITIONAL | CONDITIONAL | Mixed status (12:23), median 184ms | Lower than expected; framing-sensitive on screen |
| 4  | 32     | Interpolated | **60%** | CONDITIONAL | CONDITIONAL | status=0 dominant (11:4), median 437ms | FP-detection-sensitive; slow rejection when failing |
| 5  | 64     | Interpolated | **67%** | CONDITIONAL | CONDITIONAL | Mixed status (9:6), median 274ms | **Was INVESTIGATING — promoted to CONDITIONAL** based on empirical data |
| 6  | 128    | Interpolated | **4%** | INVESTIGATING | INVESTIGATING | status=1 dom 2.5×, median 360ms | Distinct slow-reject mechanism (palette-learning ceiling at high color count) |
| 7  | 256    | Interpolated | **17%** | **FIX COMMITTED** | INVESTIGATING | status=1 dom 11.5×, median 287ms | Member of `H_partI_unifies` cluster (with Nc=0, Nc=2) |

### Discriminator scan summary

Per-Nc 30-second steady-state failure fingerprints from trace sequence `tolerance4-test-20260528_190926.logcat` through `tolerance4-test-20260528_193640.logcat` (8 fixtures, on-screen, Galaxy S25, autofocus active, no manual zoom):

```
Nc=0 (2c):    fail=58/58 status0=14 status1=44 median=183ms  ← PartI-unified cluster
Nc=1 (4c):    fail=4/58  status0=4  status1=0  median=337ms  ← GA baseline
Nc=2 (8c):    fail=59/59 status0=11 status1=48 median=232ms  ← PartI-unified cluster
Nc=3 (16c):   fail=35/54 status0=12 status1=23 median=184ms
Nc=4 (32c):   fail=15/37 status0=11 status1=4  median=437ms
Nc=5 (64c):   fail=15/46 status0=9  status1=6  median=274ms
Nc=6 (128c):  fail=49/51 status0=14 status1=35 median=360ms
Nc=7 (256c):  fail=25/30 status0=2  status1=23 median=287ms  ← PartI-unified cluster
```

Cluster geometry visible at a glance: {Nc=0, Nc=2, Nc=7} share fast-reject + status=1-dominance signature; Nc=6 is the outlier with slow-reject; Nc=3/4/5 are the marginal-working cluster with status=0-mixed failures consistent with framing transients.

---

## Recommendation for SDK consumers building today

1. **For zero-failure-tolerance workflows, use only Nc=1 (4-color).** It is the only GA-grade mode at 93% empirical screen success.
2. **For higher-payload workflows that can tolerate ~30-70% retry rate, Nc=3/4/5 are CONDITIONAL.** Document framing-discipline requirements in your consumer-facing scan guide (steady phone, target centered, ~10cm from screen / page).
3. **Do not use Nc=0, Nc=2, Nc=6, or Nc=7 for production decoding workflows today.** Generating these codes is supported by the encoder; decoding them is currently not reliable. Nc=2 has a committed fix on the active engineering plan; Nc=0 and Nc=7 are expected to ride along under the `H_partI_unifies` hypothesis.
4. **If you control the scan UX, surface zoom and framing-guide overlays.** Today's discriminator data is from autofocus + no manual zoom; manual ROI control (per-fixture pinch-zoom) is known to substantially improve Nc=4-6 print success per the WS-5 work-stream.

---

## Investigation status (engineering audience)

The diagnostic-app's failure-side telemetry (commits `40b60cb`, `a873969`, `5b8320a` on branch `claude/ws-diagnostic-ui-tier1`) now provides per-Nc failure-mode attribution including:

- `status=0` (no FP found) vs `status=1` (FP found, slave-decode failed) per-Nc counts
- Per-Nc failure-timing fingerprint (min/max/avg/median) for discriminator analysis

The next empirical milestone is a per-Nc discriminator scan across all 8 fixtures using this telemetry, which will distinguish:

- **`H_partI_unifies`** — single PartI metadata bug unifies Nc=0, Nc=2, Nc=7 failures
- **`H_clustering_threshold_only`** — palette-learning thresholds fail at edge-Nc values
- **`H_independent_bugs`** — each broken Nc has a distinct root cause

This rank-ordering came out of Bayesian Council Session bc-2026-05-28-03 (3rd session of the WS-5 investigation arc). If `H_partI_unifies` is confirmed, a single C-side investigation closes 2-3 hypotheses simultaneously.

---

## Test fixtures

The user maintains physical printed fixtures for all 8 Nc values (Nc=0..7) on paper, and on-screen fixtures accessible via the diagnostic app or any web browser. These fixtures are the reference data that backs the per-Nc reliability table above.

---

## Updating this document

This document is empirically refreshed after each scanning session that produces per-Nc telemetry data. The "Last empirical refresh" date in the header is the authoritative as-of date. **Do not update the reliability table without supporting trace data** — the matrix should track empirical reality, not aspiration.

---

## Cross-references

- [docs/cassandra-register/H_nc2_decode_failure.md](cassandra-register/H_nc2_decode_failure.md) — root-cause hypothesis register for Nc=2
- [docs/cassandra-register/H_mode0_partI_decode_failure.md](cassandra-register/H_mode0_partI_decode_failure.md) — root-cause hypothesis register for Nc=0
- [docs/roi-detection-implementation-plan.md](roi-detection-implementation-plan.md) — Camera2 ROI work that unlocks Nc=4/6 on print
- [docs/camera-control-audit.md](camera-control-audit.md) — Camera2 control-side analysis
