 # Panama Implementation Roadmap (Updated for Full Color Modes)
 
 ## Executive Summary
 
 This roadmap operationalizes the findings from the JABCode ISO/IEC 23634 specification audit to implement support for all 8 color modes (Nc = 0–7) in the Panama FFM wrapper, replacing JNI where applicable. It integrates palette construction, variable-bit module encoding/decoding, data masking, Nc metadata handling, and interpolation for high-color modes.
 
 - **Target:** Pure Java, Project Panama FFM-based distribution
 - **Focus:** Full color mode enablement (4 → 256 colors), encoder/decoder parity
 - **Reference Implementation Docs:** See “Linked Sources” below
 
 ---
 
 ## Linked Sources (Spec Audit)
 
 The following documents contain the authoritative specification mapping and code guidance used in this roadmap:
 
 - [00-index.md](jabcode-spec-audit/00-index.md)
 - [01-color-modes-overview.md](jabcode-spec-audit/01-color-modes-overview.md)
 - [02-color-palette-construction.md](jabcode-spec-audit/02-color-palette-construction.md)
 - [03-encoding-implementation.md](jabcode-spec-audit/03-encoding-implementation.md)
 - [04-decoding-implementation.md](jabcode-spec-audit/04-decoding-implementation.md)
 - [05-annex-g-analysis.md](jabcode-spec-audit/05-annex-g-analysis.md)
 - [06-implementation-checklist.md](jabcode-spec-audit/06-implementation-checklist.md)
 - [README.md](jabcode-spec-audit/README.md)
 
 ---
 
 ## Context: Prior Technical Challenges (Panama FFM)
 
 From the JNI → Panama migration analysis:
 - **Flexible Array Members (FAM):** `jab_data.data[]`, `jab_bitmap.pixel[]` → allocate header + payload slices via Arena and `sizeof()` offsets.
 - **Pointer Chains:** Access to nested structs (e.g., `jab_encode.bitmap`) via memory addresses and Panama dereference patterns.
 - **Memory Ownership:** Native allocation ownership for `decodeJABCode()` results → bind `free()` and copy before release.
 
 These remain valid. This roadmap adds color-mode specific work on top of that foundation.
 
 ---
 
 ## Scope of This Update: Color Modes Integration
 
 - **Modes:** 0 (reserved), 1 (4), 2 (8 default), 3 (16), 4 (32), 5 (64), 6 (128), 7 (256)
 - **Encoder/Decoder:** Variable bits per module, palette embed/extract, masking per mode, Nc metadata (3-color), interpolation (modes 6–7)
 - **Quality:** Implement ISO Section 8.3 checks (palette accuracy, colour variation)
 
 ---
 
 ## Architecture and API Impact
 
 - **ColorMode enum:** `Nc` value, color count, `getBitsPerModule()`, `requiresInterpolation()`
 - **ColorPalette strategy:** Per-mode palette generation; embedded vs full; nearest-colour mapping
 - **ColorInterpolator:** Linear interpolation for modes 6–7 (R-only for 128, R+G for 256)
 - **Bit streams:** `BitStreamEncoder/Decoder` with 2–8 bit widths
 - **Masking:** Modulus equals colour count; XOR index mask (see Table 22 patterns)
 - **Metadata:** Part I Nc in 3-colour mode (Black/Cyan/Yellow), Part II in full palette
 - **Placement:** Bottom-right upward placement, right-to-left columns; reserved regions unchanged
 
 Code touch-points:
 - `panama-wrapper/src/main/java/com/jabcode/panama/JABCodeEncoder.java`
 - `panama-wrapper/src/main/java/com/jabcode/panama/JABCodeDecoder.java`
 - New support classes: `ColorMode`, `ColorPalette` (Mode1–Mode7), `ColorInterpolator`, `BitStream*`, `PaletteEmbedding`, `DataMasking`
 
 ---
 
 ## Phased Plan (Aligned with Spec Audit Checklist)
 
 See details in [06-implementation-checklist.md](jabcode-spec-audit/06-implementation-checklist.md). Summary here:
 
 ### Phase 1: Foundation
 - **Deliverables:** `ColorMode`, `ColorPalette` interface, utilities, tests scaffold
 - **Refs:** [01], [02]
 - **/test-coverage-update:** Run coverage workflow for this phase to enforce TDD and coverage.
 
 ### Phase 2: Extended Modes (No Interpolation)
 - **Implement:** Mode 3 (16), Mode 4 (32), Mode 5 (64)
 - **Deliverables:** Palette generators, 4–6 bit packing, end-to-end tests
 - **Refs:** [02], [03], [04], [05]
 - **/test-coverage-update:** Run coverage workflow to validate new palettes and bit packing.
 
 ### Phase 3: High-Color Modes (With Interpolation)
 - **Implement:** Mode 6 (128, R interpolation), Mode 7 (256, R+G interpolation)
 - **Deliverables:** Interpolator, embedded-64 subset selection, reconstruction, ambiguity handling
 - **Refs:** [02], [04], [05]
 - **/test-coverage-update:** Run coverage workflow focusing on interpolation branches and edge cases.
 
 ### Phase 4: Encoder/Decoder Integration
 - **Encoder:** Mode parameter, variable bit packing, masking by modulus, Nc Part I encoding, palette embedding
 - **Decoder:** Nc detection (3-color), palette extraction, reconstruction (6–7), unmask, variable bit unpacking
 - **Refs:** [03], [04]
 - **/test-coverage-update:** Run coverage workflow across encode/decode integration paths.
 
 ### Phase 5: Testing & ISO Quality
 - **Unit & Integration:** All modes, round-trip, capacity, performance
 - **ISO 8.3 Metrics:** Palette accuracy (dR,dG,dB), colour variation checks
 - **Refs:** [02] (dR/dG/dB), [04]
 - **/test-coverage-update:** Run coverage workflow to ensure quality tests are included in coverage.
 
 ### Phase 6: Docs & Examples
 - **User Guide:** Mode selection guidance, trade-offs, limitations
 - **Examples:** One per mode, programmatic and CLI usage
 - **Refs:** [00], [01], [README]
 - **/test-coverage-update:** Final coverage workflow run; verify examples backed by tests.
 
 ---
 
 ## Deliverables (By Path)
 
 - **Core:**
   - `ColorMode.java` (Nc mapping, bit width)
   - `ColorPalette` + `Mode{1..7}Palette.java`
   - `ColorInterpolator.java` + `LinearInterpolator.java`
   - `BitStreamEncoder.java`, `BitStreamDecoder.java`
   - `PaletteEmbedding.java`, `DataMasking.java`
 - **Encoder/Decoder Integration:**
   - `JABCodeEncoder.java` (palette embed, masking, Nc Part I)
   - `JABCodeDecoder.java` (Nc detect, palette extract/reconstruct, unmask)
 - **Tests:** `ColorModeTest`, `PaletteTest_Mode{1..7}`, `RoundTrip_Mode{1..7}`, Quality tests (8.3)
 - **Docs:** Usage guide, API Javadoc, examples
 
 ---
 
 ## Success Criteria
 
 - **Functional:**
   - All modes (0–7) recognized; 1–7 fully operable
   - Modes 3–5: full embedded palette, no interpolation
   - Modes 6–7: correct subset embed and palette reconstruction
   - Nc metadata encoded/decoded via 3-colour mode
 - **Quality:**
   - Minimum colour distances per mode validated
   - Section 8.3 palette accuracy and colour variation tests pass
   - No regressions for modes 1–2
 - **Performance:**
   - Mode 1–2 within 10% of JNI baseline; other modes acceptable within 20% of mode 2
 
 ---
 
 ## Risks & Mitigations
 
 - **Interpolation Ambiguity (6–7):**
   - Mitigate with nearest-colour in full palette + ECC + neighbor context
   - Provide higher ECC presets for high-colour modes
 - **Printing/Scanning Variability:**
   - Enforce quality thresholds; expose diagnostics (dR/dG/dB, CAP/CVDM)
 - **Variable Bit-Width Edge Cases:**
   - Comprehensive unit tests; bit boundary fuzzing; golden vectors
 - **Palette Embed Limit (64):**
   - Strict subset selection rules per Annex G; validation tests
 
 ---
 
 ## Timeline (Estimate)
 
 - Week 1: Phases 1–2 (foundation + modes 3–5)
 - Week 2: Phases 3–4 (modes 6–7 + integration)
 - Week 3: Phases 5–6 (testing, docs, examples)
 
 ---
 
 ## Next Actions
 
 - Bootstrap Phase 1 scaffolding and tests
 - Implement Mode 3–5 palettes and variable bit packing
 - Add interpolator and reconstruction for Mode 6–7
 - Integrate into `JABCodeEncoder`/`JABCodeDecoder`
 - Execute ISO 8.3 quality suite and finalize docs
 
 ---
 
 ## Appendix: Cross-References
 
 - Encoding specifics: [03-encoding-implementation.md](jabcode-spec-audit/03-encoding-implementation.md)
 - Decoding specifics: [04-decoding-implementation.md](jabcode-spec-audit/04-decoding-implementation.md)
 - Palette generation & distances: [02-color-palette-construction.md](jabcode-spec-audit/02-color-palette-construction.md)
 - Annex G analysis: [05-annex-g-analysis.md](jabcode-spec-audit/05-annex-g-analysis.md)
 - Checklist for execution: [06-implementation-checklist.md](jabcode-spec-audit/06-implementation-checklist.md)
 
