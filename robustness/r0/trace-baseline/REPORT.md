# R0 — Field Decode-Rate Baseline (from on-device diagnostic traces)

Evidence-first first step of the robustness phase. This baseline is mined
entirely from **real on-device decode attempts** captured in 56
`jabauth-android` diagnostic-app logcat traces (camera frames against printed
and on-screen JABCodes). No synthetic data, no re-decoding — just a reduction
of what the field decoder actually did.

- **Generator:** [`mine_traces.py`](./mine_traces.py)
- **Per-group records:** [`baseline.jsonl`](./baseline.jsonl)
- **Chart:** [`baseline.png`](./baseline.png)
- **Traces analysed:** 56 sessions, **7,658 complete attempts**
- **Security:** outcomes and FAIL\_STAGE only. No decoded payload was
  extracted, printed, or stored. `DIAG_SYMBOL_DECODE` was read for its
  `result=ok|fail` field alone.

---

## 1. Failure taxonomy (the enumerated vocabulary)

`DIAG_*_RESULT` outcomes present in the traces: a single marker,
**`DIAG_PARTII_RESULT`**, with `ok=1` (success), `ok=0 result=-1` (Part II
LDPC fail), and `ok=0 result=0` (Part II side/version fail).

Distinct **`FAIL_STAGE`** values, placed in decoder-pipeline order. A failed
attempt is attributed to the **last** stage it reached before giving up:

| Stage          | Pipeline phase | Meaning | Bound class |
|----------------|----------------|---------|-------------|
| `detect`       | Detection      | No finder pattern localized — "No JABCode found in image". *Synthetic stage: no FAIL\_STAGE line is emitted; inferred from the native outcome.* | **Detection** |
| `module_color` | Part I         | A sampled module's colour is not in the valid colour set. | Classification / palette |
| `pair_bits`    | Part I         | Paired-bit value out of range. | Classification / palette |
| `side_version` | Part II        | Decoded side/version inconsistent with the sampled matrix. | Classification / palette |
| `ldpc`         | Part II        | Metadata uncorrectable at the LDPC layer. | Classification / palette |

Only `detect` is detection-bound. Every other stage means a symbol **was**
found and localized, but colour/metadata recovery failed — i.e.
classification / palette-bound.

---

## 2. Decode-rate by Nc / medium

Sorted by colour mode. `n` = complete attempts (truncated session tails
excluded). Dominant FAIL\_STAGE = the most frequent terminal failure stage for
that group.

| Group         |     n | success | decode-rate | dominant FAIL\_STAGE | detect : classify (failures) | bound       |
|---------------|------:|--------:|------------:|---------------------|------------------------------|-------------|
| nc0           | 2,725 |      62 |    **2.3%** | `detect`            | 1640 : 1023                  | detection   |
| nc1           |   410 |     302 |   **73.7%** | `detect`            |   64 : 44                    | detection   |
| nc1 / print   |   102 |      61 |   **59.8%** | `module_color`      |    5 : 36                    | classify    |
| nc1 / screen  |   104 |      71 |   **68.3%** | `detect`            |   33 : 0                     | detection   |
| nc2           | 2,232 |     349 |   **15.6%** | `ldpc`              |  505 : 1378                  | classify    |
| nc3           |   308 |     231 |   **75.0%** | `detect`            |   38 : 39                    | ~tie        |
| nc4           |   242 |     190 |   **78.5%** | `detect`            |   26 : 26                    | tie         |
| nc5           |   295 |     199 |   **67.5%** | `ldpc`              |   33 : 63                    | classify    |
| nc6           |   229 |     153 |   **66.8%** | `ldpc`              |   25 : 51                    | classify    |
| nc7           |   250 |      93 |   **37.2%** | `ldpc`              |   13 : 144                   | classify    |
| ncX (unknown) |   761 |     300 |   **39.4%** | `ldpc`              |  189 : 272                   | classify    |
| **ALL**       | **7,658** | **2,011** | **26.3%** | `detect` (2571) / `ldpc` (2257) | **2571 : 3076** | **classify** |

### Print vs screen (nc1, the only medium-tagged mode)

| Medium | n | decode-rate | dominant fail | reading |
|--------|--:|------------:|---------------|---------|
| print  | 102 | **59.8%** | `module_color` | Failures are **palette/classification**: the symbol is found but printed-gamut colours fall outside the valid set. |
| screen | 104 | **68.3%** | `detect` | Failures are **detection**: once the finder pattern is localized on the bright screen, colour recovery essentially always succeeds (0 classify failures). |

The print-vs-screen contrast is the cleanest signal in the dataset: **the
medium changes the failure mode.** Screen failures are detection-bound; print
failures are palette-bound. This matches the known screen-vs-print physics
(screen = sensor-saturation-limited, print = gamut-limited).

---

## 3. Verdict — detection-bound or classification-bound?

**The field data says failures are classification / palette-bound, not
detection-bound — so the robustness work points at R1, not R2.** Across all
7,658 attempts, detection failures (`detect` = 2,571) are outnumbered by
post-detection classification/palette failures (`module_color` + `pair_bits` +
`side_version` + `ldpc` = 3,076), and detection itself *succeeds* on the large
majority of frames (4,836 `DETECT SUCCESS` markers). The signal sharpens once
the data is read by regime: the aggregate `detect` count is inflated by the
nc0 Mode-0-monochrome sessions (1,640 of the 2,571 detect failures live in
nc0, a known separate low-yield regime at 2.3%); strip nc0 and the field is
decisively classification-bound (~931 detect vs ~2,053 classify). Every
polychrome mode the robustness phase actually targets — nc2 (`ldpc`+`pair_bits`
dominate, 15.6%), nc5/nc6 (`ldpc`), nc7 (`ldpc`, 144:13), ncX (`ldpc`) — fails
*after* the symbol is found, in colour classification and LDPC metadata
recovery. The lone exception that proves the rule is nc1/screen, whose
failures are purely detection — confirming that when classification is easy
(bright screen, low Nc) the only thing left to fail is detection. **Net: invest
R1 in colour classification / palette robustness (module-colour assignment and
LDPC metadata recovery for Nc>=2); detection (R2) is already carrying its
weight in the field and is the secondary lever.**

---

## 4. Method notes (reproducibility)

- **Attempt boundary.** Anchored on the terminal `Native decode
  SUCCESS|FAILED` line, which appears exactly once per attempt in *both*
  logcat formats the diagnostic-app emitted (newer builds also print
  `--- Decode #N START ---`; six older Mode-0 nc0 sessions omit it). This
  counts every attempt; a truncated session tail emits no terminal line and is
  correctly never counted.
- **Per-Nc-try fan-out.** A single failed attempt retries across up to 8 Nc
  values, each logging its own `FAIL_STAGE` / `DIAG_PARTII_RESULT`. The
  attempt is attributed to its **terminal** (last) stage, not to every try, so
  the histogram is per-attempt, not per-try.
- **Reconciliation.** Parser totals match raw line counts exactly: 2,011
  successes, 2,571 `detect` (= "No JABCode found"), 3,076 classify-stage
  (= "JABCode found but not decodable"). Every native-outcome line is
  accounted for.
- **Re-run:**
  ```sh
  /tmp/bench-venv/bin/python mine_traces.py \
      --logs-dir <path>/jabauth-android/diagnostic-app/logs
  ```
