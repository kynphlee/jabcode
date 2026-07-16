# JABCode Ecosystem Research — INDEX

**Project**: JABCode (`swift-java-poc` branch)
**Last Updated**: 2026-07-05
**Skill driving this aggregator**: `.claude/skills/ecosystem-research/SKILL.md`

This INDEX is the canonical entry point for the JABCode ecosystem research aggregator.
Each research session produces a dated daily report under `daily-reports/`. This file
tracks what has been covered, when, and which components each session touched.

---

## Research Domains

Eight standing research domains are defined in the skill. Coverage status:

| Domain | First Covered | Last Touched | Status |
|--------|--------------|--------------|--------|
| JABCode standard / Fraunhofer SIT upstream | — | — | Not yet covered |
| Color-barcode competitive landscape (HCC2D, CCB, Microsoft Tag) | — | — | Not yet covered |
| Camera2 / CameraX / ML Kit Android scanning quality | — | — | Not yet covered |
| Project Panama FFM / Java FFI maturity | — | — | Not yet covered |
| Swift–Java interop ecosystem | — | — | Not yet covered |
| Color-mode deployment signals (Nc=1..7, Mode 0 monochrome) | — | — | Not yet covered |
| Print-substrate and scanning robustness | — | — | Not yet covered |
| Barcode adoption by industry vertical (regulatory forcing functions) | — | — | Not yet covered |

---

## Daily Reports

No reports yet. The first session will create `daily-reports/YYYY-MM-DD.md`.

---

## Cross-Domain Patterns

*(To be populated as research sessions identify recurring cross-domain themes.)*

---

## Key Differentiators Tracked

Research findings are mapped against these JABCode differentiators:

1. Color-encoding capacity advantage (~50% more data than QR at Nc=3; research target
   for higher Nc modes)
2. ISO/IEC 23634 international standardization (JTC1/SC31)
3. Fraunhofer SIT institutional lineage + MIT license
4. Panama FFM pure-JVM delivery (no JNI)
5. Multiple Nc modes from a single implementation (Nc=1..7)
6. LEVEL_3 Camera2 production-grade Android scanning (AWB/AE convergence-lock)
7. Active QA surface (conformance + robustness + benchmarks)

---

## Research Priorities Quick Reference

See `../.claude/skills/ecosystem-research/references/research-priorities.md` for the
full priority list with refresh cadence guidance.

High-priority next sessions (as of 2026-07-05):

- **A1**: Fraunhofer SIT upstream activity since 2026-Q1
- **B1**: Camera2 LEVEL_3 AWB/AE convergence-lock patterns (direct connection to
  current android_reader development)
- **D1**: Mode 0 (monochrome, Nc=1) viability research (direct connection to active
  `docs/cassandra-register/H_mode0_partI_decode_failure.md` investigation)
- **C1**: Panama FFM production maturity (direct connection to panama-wrapper delivery
  story)

---

*Update this file after every research session. Add a row to the Daily Reports table
and update the Domain coverage status. If a new cross-domain pattern emerges, add a
bullet under Cross-Domain Patterns.*
