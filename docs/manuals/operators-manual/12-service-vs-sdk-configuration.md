# 12. Service configuration vs SDK configuration

<!-- objective: A SaaS operator can state which codec knobs are reachable through the service API versus SDK-only, including the reconciled symbolWidth/symbolHeight mapping to masterSymbolWidth/masterSymbolHeight and current cascade exposure -->

**In this chapter you will** map the service's `JabCodeConfig` record field by field onto the C API and writer flags from Part II, learn exactly which knobs each surface can reach, and meet two pieces of honest history: a reconciled defect and a still-open configuration question.

**You should already** know the writer flags ([chapter 7](07-encoding-with-jabcodewriter.md)), the `jab_encode` struct from the C API ([chapter 9](09-embedding-the-c-api.md)), and the service binding chain ([chapter 11](11-service-binding-chain.md)).

## One codec, three configuration surfaces

Every parameter ultimately lands in the same place: the native `jab_encode` struct (`jabcode.h:172-185`), whose settable fields are `color_number`, `symbol_number`, `module_size`, `master_symbol_width`, `master_symbol_height`, `palette`, `symbol_versions`, `symbol_ecc_levels`, and `symbol_positions`. But three different surfaces feed it — the CLI flags, the C API, and the framework's `JabCodeConfig` record — and they do not expose the same subset. Knowing which surface reaches which field is the whole game of this chapter. <!-- anchor: src/jabcode/include/jabcode.h:172-185; docs/manuals/corpus-model.md §3.2 -->

## The field-by-field map

`JabCodeConfig` is a record of nine fields. Here is each one against its C API and CLI counterparts, with how far the REST surface reaches: <!-- anchor: JABCodeCOA-crypto corpus §3.5 (JabCodeConfig.java:34-44) -->

| `JabCodeConfig` field | `jab_encode` field | Writer flag | REST-reachable? |
|---|---|---|---|
| `colorMode` (`COLOR_2`..`COLOR_256`) | `color_number` | `--color-number` | Yes, via `/generate/simple` `colorMode` <!-- anchor: JabCodeConfig.java:172-233; jabcode.h:173; jabwriter.c:132 --> |
| `moduleSize` | `module_size` | `--module-size` | No — fixed at 12 by the simple endpoint <!-- anchor: jabcode.h:175; jabwriter.c:157; SimpleJabCodeRequest.java:52-54 --> |
| `eccLevel` | `symbol_ecc_levels` | `--ecc-level` | Yes, via `/generate/simple` `errorCorrection` (four named levels) <!-- anchor: jabcode.h:180; jabwriter.c:273; SimpleJabCodeRequest.java:79-90 --> |
| `symbolWidth` / `symbolHeight` | `master_symbol_width` / `master_symbol_height` | `--symbol-width` / `--symbol-height` | No — see the reconciliation section below <!-- anchor: jabcode.h:176-177; jabwriter.c:173, 189 --> |
| `symbolNumber` (1-61) | `symbol_number` | `--symbol-number` | No <!-- anchor: JabCodeConfig.java:80, 111-114; jabcode.h:174; jabwriter.c:205 --> |
| `symbolVersions` (list of `(x, y)`, 1-32) | `symbol_versions` | `--symbol-version` | No <!-- anchor: JabCodeConfig.java:54-77; jabcode.h:179; jabwriter.c:311 --> |
| `enablePooling`, `optimizedSaving` | — none — | — | Not forwarded at all: `createPanamaConfig` sets only colour number, ECC, module size, symbol number/versions and the master pixel sizes on the wrapper builder <!-- anchor: PanamaJabCodeService.java:268-332 --> |
| — no field — | `symbol_positions` | `--symbol-position` | Not exposed by `JabCodeConfig`; position assignment happens inside the vendored Panama wrapper, whose source is outside both corpora — behavior NOT FOUND <!-- anchor: jabcode.h:181; jabwriter.c:358 --> |
| — no field — | `palette` | — no flag — | C-API-only; not exercised in [chapter 9](09-embedding-the-c-api.md)'s example — see the Developer's Manual (JC-T), forthcoming <!-- anchor: jabcode.h:178 --> |
| — no field — | `mask_type` (output; library default reference 7) | — no flag — | Diagnostic output, not an input on any surface <!-- anchor: jabcode.h:184; src/jabcode/include/jabcode.h:36 --> |
| — no field — | — (save-time option) | `--color-space` (0 RGB/PNG, 1 CMYK/TIFF) | CLI-only; the service encode path always produces PNG in memory <!-- anchor: jabwriter.c:226, 241-245; PanamaJabCodeService.java:105-121 --> |

One semantic mismatch deserves its own sentence: the CLI accepts ECC level `0` as "use the default level, for slaves ... the same level as its host", but `JabCodeConfig` rejects it — its constant block says "Level 0 is the codec's 'unset -> default' sentinel, not a spec level, so it is rejected here" (`ECC_MIN = 1`, `ECC_MAX = 10`, `ECC_DEFAULT = 3`). Java callers must pin an explicit level. <!-- anchor: src/jabcodeWriter/jabwriter.c:40-44; JabCodeConfig.java:82-94, 115-121 -->

## The `symbolWidth`/`symbolHeight` reconciliation

These two fields carry the framework's one documented configuration defect and its repair. The record's Javadoc tells the story:

> "The legacy `symbolWidth`/`symbolHeight` fields are **pixel** dimensions of the master symbol (the native `--symbol-width` / `--symbol-height` CLI options, i.e. `masterSymbolWidth` / `masterSymbolHeight` in the Panama Config). They are orthogonal to `symbolVersions` (which is a module-count side-version, not pixels): the native encoder derives an effective module size by dividing the requested pixel size by the symbol's module side-size. They were previously dropped on the floor by `PanamaJabCodeService.createPanamaConfig`; they are now wired through to `masterSymbolWidth`/`masterSymbolHeight`, so both may be set independently of `symbolVersions`."

<!-- anchor: JABCodeCOA-crypto corpus §3.5 (JabCodeConfig.java:22-32) -->

The wiring has a pragmatic twist you should know when predicting output size. The service resolves the two fields through `reconcileSymbolVersions`, whose rules are: an explicit `symbolVersions` list "wins verbatim"; otherwise, for a single-symbol config with both fields set, the master's side-version is derived via `version = (modules - 17) / 4` — and only as a third resort does the encoder auto-size. The implementation comment is candid about why it derives a side-version instead of trusting pixel sizing: "the shipped Panama JAR's encoder ignores the pixel-based `masterSymbolWidth`", so the fields "are interpreted as the master symbol's per-axis **module count** (not raw pixels), because that is the dimension the native codec actually consumes via `symbol_versions[0]`". The pixel values are still forwarded to `masterSymbolWidth`/`masterSymbolHeight` for forward compatibility with a future wrapper jar. <!-- anchor: PanamaJabCodeService.java:334-374, 315-328 -->

Read the two quotes side by side and be honest with yourself about the current state: the record documents the fields as pixels; the shipping service consumes them as module counts. Until the wrapper honours pixel master sizing, treat them as module counts when you predict geometry — a value of `41` yields side-version `(41 - 17) / 4 = 6`, a 41-module side.

Historically, these fields were accepted and then silently ignored — an operator could set them, get an auto-sized symbol, and have no error to tell them why. The repair (reconcile to a side-version, forward the pixel values, document the seam) is a compact case study in retiring a silent-drop defect without breaking existing callers; the full history, including the earlier cascade-exposure gap it travelled with, belongs to the framework Developer's Manual (AF-T), forthcoming. <!-- anchor: JabCodeConfig.java:29-32; JABCodeCOA-crypto corpus §7 item 2 -->

## What each REST endpoint actually reaches

From [chapter 11](11-service-binding-chain.md) you know `/api/jabcode/generate` pins `JabCodeConfig.defaultConfig()` — its request DTO carries only `certificateId`, `tokenId`, `data`, so **no codec knob is reachable there at all**. The `/api/jabcode/generate/simple` endpoint is the only REST surface with knobs, and it exposes exactly two: <!-- anchor: JabCodeService.java:50-52; JabCodeRequest.java:12-17 -->

| Request field | Accepted values | Mapped to |
|---|---|---|
| `colorMode` | 2, 4, 8, 16, 32, 64, 128, 256 (default 4) | `JabCodeConfig.ColorMode` <!-- anchor: SimpleJabCodeRequest.java:62-77 --> |
| `errorCorrection` | "low" → 1, "medium" → 3, "high" → 5, "maximum" → 7 (default 3) | `eccLevel` <!-- anchor: SimpleJabCodeRequest.java:79-90 --> |

Mind the fallback behavior: an unrecognized `colorMode` or `errorCorrection` value does not error — both switches fall through to the default (4-colour, ECC 3). A typo like `"hihg"` silently gives you ECC 3, so verify the metadata echoed in the response rather than trusting the request. <!-- anchor: SimpleJabCodeRequest.java:75, 88 -->

Everything else — module size, master sizing, cascading (`symbolNumber`, `symbolVersions`), pooling — is **SDK-only today**: reachable by Java callers constructing a `JabCodeConfig` (including the `cascade(int eccLevel, int... squareVersions)` factory), by C API callers ([chapter 9](09-embedding-the-c-api.md)), or at the CLI ([chapter 7](07-encoding-with-jabcodewriter.md)). CMYK output is narrower still: CLI-only. <!-- anchor: JABCodeCOA-crypto corpus §3.5 (JabCodeConfig.java:331-349) -->

## The open question: `jabauth.jabcode.*` properties

The framework's global configuration record `JabAuthProperties` declares a `jabauth.jabcode.*` namespace with defaults `default-size = "20x20mm"`, `color-depth = 8`, `error-correction = "HIGH"`. Set them in `application.properties` and they will bind — but the framework corpus model's discrepancy register is blunt about what happens next: "no code path connecting `JabAuthProperties.JabCode` to `JabCodeConfig` was found — treat the record as declarative only", and "Do not document the record's values as codec behaviour." Notice the record's own defaults (8-colour, "HIGH") do not even agree with the codec path's real default (`defaultConfig()` = 4-colour, ECC 3) — a second clue that nothing downstream consumes them. If you need to change service encode behavior today, change the code path (`JabCodeConfig`), not these properties. <!-- anchor: JABCodeCOA-crypto corpus §3.1 (JabAuthProperties.java:48-50), §7 item 3 -->

## Worked example: the same symbol from all three surfaces

Constructed from source, not executed. Target: a 4-colour, ECC 5, module-size-12, single-symbol code.

```sh
# Surface 1 — CLI (chapter 7)
jabcodeWriter --input 'COA-2026-000123' --output coa.png --color-number 4 --ecc-level 5
```

```java
// Surface 2 — Java SDK (full JabCodeConfig; 7-arg compatibility constructor)
JabCodeConfig config = new JabCodeConfig(
    JabCodeConfig.ColorMode.COLOR_4, 12, 5, null, null, true, true);
BufferedImage img = jabCodeService.generateJabCode(payload, config);
```

```json
// Surface 3 — REST, POST /api/jabcode/generate/simple
{ "data": "Q09BLTIwMjYtMDAwMTIz", "colorMode": 4, "errorCorrection": "high" }
```

All three land in the native encoder with `color_number = 4`, ECC level 5, `module_size = 12`. Now push the example one step: make it a two-symbol cascade. Surface 1 adds `--symbol-number 2 --symbol-position 0 3 --symbol-version 3 2 4 2` ([chapter 7](07-encoding-with-jabcodewriter.md)); surface 2 sets `symbolNumber = 2` with two `SymbolVersion` entries; surface 3 has no field for it — the REST wall is exactly where the table above says it is. <!-- anchor: src/jabcodeWriter/jabwriter.c:58; JabCodeConfig.java:34-44, 140-151; SimpleJabCodeRequest.java:12-31 -->

## Try it

1. A REST client needs a two-symbol cascade. Which surface must they move to, and what is the minimal Java-side change?
2. You set `jabauth.jabcode.color-depth=4` in `application.properties` and symbols keep coming out 4-colour. Did your property work?
3. A Java caller sets `symbolWidth = 41`, `symbolHeight = 41`, no `symbolVersions`. What geometry does today's service produce?
4. Why does `new JabCodeConfig(..., /* eccLevel */ 0, ...)` throw, when `--ecc-level 0` is legal at the CLI?

<details><summary>Answers</summary>

1. The Java SDK (or CLI): no REST field maps to `symbolNumber`/`symbolVersions`. Minimal change: build the config with `JabCodeConfig.cascade(eccLevel, v0, v1)` or the 9-field constructor with `symbolNumber = 2` and two `SymbolVersion` entries. <!-- anchor: JabCodeConfig.java:331-349 -->
2. No — trick question in both directions. The property binds but has no found codec-path consumer; symbols are 4-colour because `defaultConfig()` is 4-colour. Deleting your property would change nothing either. <!-- anchor: JABCodeCOA-crypto corpus §3.1, §7 item 3 -->
3. A side-version-6 master, 41 modules per side: with no explicit versions, `reconcileSymbolVersions` derives `(41 - 17) / 4 = 6` for each axis — the fields are consumed as module counts by the shipped wrapper, whatever the record's Javadoc calls them. <!-- anchor: PanamaJabCodeService.java:356-374 -->
4. The CLI treats `0` as "default for the master, inherit for slaves"; the record rejects it because "Level 0 is the codec's 'unset -> default' sentinel, not a spec level" — the framework forces callers to pin an explicit 1-10 level. <!-- anchor: src/jabcodeWriter/jabwriter.c:40-44; JabCodeConfig.java:85-86 -->

</details>

## Where to go next

- Next: the appendices — [troubleshooting](appendix-a-troubleshooting.md), the [samples cross-index](appendix-b-samples-cross-index.md), and the [quick-reference card](appendix-c-quick-reference.md).
- Deeper: the reconciliation's full defect history and the `JabAuthProperties` constructor-binding saga are framework Developer's Manual material (AF-T), forthcoming.
