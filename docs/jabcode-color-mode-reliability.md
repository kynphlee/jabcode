# JABCode Color Mode Reliability Disclosure

**Authoritative reference** for SDK consumers and downstream integrators. Documents which JABCode color modes are reliably decodable by the current Android SDK build, what is known to be broken, and what investigation work is in flight.

**Last empirical refresh:** 2026-05-28 (Galaxy S25, Camera2 API, YUV_420_888, 1920×1080, on-screen + printed fixtures).

---

## tl;dr

Use only **Nc=1, Nc=3, Nc=4, Nc=6** for production-facing scanning workflows on Android right now. Other modes are either broken (Nc=0, Nc=2, Nc=5, Nc=7) or have media-specific limitations. Investigation is in flight for all broken modes; **Nc=2 has a committed fix on the roadmap** based on empirical confirmation of root-cause hypothesis `H_nc2_decode_failure` (see [docs/cassandra-register/H_nc2_decode_failure.md](cassandra-register/H_nc2_decode_failure.md)).

---

## Per-Nc reliability matrix

Symbol meanings:

- **GA** — Production-grade reliable on the listed medium with default scan settings.
- **CONDITIONAL** — Works under specific scan conditions (zoom, lighting, distance); document the conditions in your consumer-facing scan-guide.
- **INVESTIGATING** — Empirically broken at 0% decode rate; root-cause investigation in flight; no committed fix timeline.
- **FIX COMMITTED** — Empirically broken; root-cause investigation in flight; **a fix is committed as part of the active engineering plan.**

| Nc | Colors | Palette | Screen (display) | Print (paper) | Notes |
|----|--------|---------|------------------|---------------|-------|
| 0  | 2      | Black + White (monochrome) | INVESTIGATING | INVESTIGATING | `H_mode0_partI_decode_failure` — PartI metadata decode failure suspected; under hypothesis `H_partI_unifies` may share root cause with Nc=2 |
| 1  | 4      | CMY + K | **GA** | **GA** | Default fallback path; widely reliable |
| 2  | 8      | CMY + RGB + K + W | **FIX COMMITTED** | **FIX COMMITTED** | `H_nc2_decode_failure` CONFIRMED (2026-05-28) via 4-5× status=1 dominance + fast-rejection timing; **investigation prioritized** |
| 3  | 16     | Interpolated | **GA** | **GA** | Reliable across media |
| 4  | 32     | Interpolated | **GA** | CONDITIONAL | Print: requires manual zoom (pinch-zoom) to compensate for module-size + gamut margin |
| 5  | 64     | Interpolated | INVESTIGATING | INVESTIGATING | Slave-decode failure at clustering stage; failure-mode pattern matches `H_partI_unifies` candidate hypothesis |
| 6  | 128    | Interpolated | CONDITIONAL | INVESTIGATING | Screen: works with manual zoom. Print: gamut-limited |
| 7  | 256    | Interpolated | INVESTIGATING | INVESTIGATING | Compound failure: slave-decode + gamut + chroma resolution limits |

---

## Recommendation for SDK consumers building today

1. **Restrict the color-mode option exposed to your end-users to {Nc=1, Nc=3, Nc=4, Nc=6}** if you cannot tolerate any decode-failure surface.
2. If your application generates JABCodes for later decoding, use Nc=1 for maximum compatibility, Nc=3 for higher payload density.
3. For high-data-density use cases on screen-displayed codes, Nc=6 with manual zoom is the highest-payload reliable mode currently.
4. **Do not use Nc=0, Nc=2, Nc=5, or Nc=7 for production decoding workflows.** Generating these codes is supported by the encoder; decoding them is not currently reliable.

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
