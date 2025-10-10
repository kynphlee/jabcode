# JABCode Incremental Implementation Plan

This plan outlines practical, low-risk increments to harden our JNI integration, expand functional coverage, and prepare for real‑world usage and distribution.

## Phase 0 — Baseline (Completed)
- **Goal**: Stabilize decode pipeline and native loading for tests.
- **Key work**:
  - Pointer-based JNI decode (`readImagePtr` + `decodeJABCodePtr`) with status ≥ 2 treated as success; NORMAL→COMPAT fallback, diagnostics.
    - Files: `src/main/java/com/jabcode/OptimizedJABCode.java`, `src/main/java/com/jabcode/internal/JABCodeNativePtr.java`
  - Align JavaCPP with manual JNI; skip pointer symbols in `InfoMap`.
    - Files: `src/main/java/com/jabcode/JABCodePresets.java`, `src/main/java/com/jabcode/internal/JABCodeNative.java`
  - Build pointer JNI as separate lib and co-locate natives; stabilize loader path reporting.
    - Files: `pom.xml` (exec id `build-jni-ptr`), `scripts/create_symlinks.sh`, `src/main/java/com/jabcode/internal/NativeLibraryLoader.java`
- **Acceptance**: `mvn clean install` green. All color modes (4–256) round-trip digitally.

## Phase 1 — API consistency and decodeEx migration
- **Goal**: Make all decode entry points consistent with the stabilized pointer path and semantics.
- **Tasks**:
  - Migrate `OptimizedJABCode.decodeEx(...)` to pointer JNI and apply status ≥ 2 success + NORMAL→COMPAT fallback.
    - Files: `src/main/java/com/jabcode/OptimizedJABCode.java`, `src/main/java/com/jabcode/internal/JABCodeNativePtr.java`
  - Unify image-processing default to disabled across encode/decode public APIs.
    - Files: `src/main/java/com/jabcode/OptimizedJABCode.java`, `src/main/java/com/jabcode/core/JABCode.java`
  - Gate or label any 2-color tests as experimental (non-standard) without breaking existing runs.
    - Files: `src/test/java/...`
- **Acceptance**: All tests pass; decodeEx behavior and error messaging match main decode.

## Phase 2 — Capacity and topology coverage
- **Goal**: Validate multi-symbol, capacity selection, and metadata correctness.
- **Tasks**:
  - Add tests: multi-symbol (2+), ECC sweeps, larger payloads, symbol version sweeps.
  - Verify capacity from `encoder.c` (`getSymbolCapacity()`), and master metadata via `encodeMasterMetadata()`.
    - Files: `javacpp-wrapper/src/jabcode/encoder.c`
- **Acceptance**: Parameterized tests pass across matrix; no regressions in round-trip.

## Phase 3 — Robustness suite (digital)
- **Goal**: Evaluate resilience across common degradations.
- **Tasks**:
  - Add image transforms (rotation, scale), blur/noise, JPEG compression, and color jitter tests.
  - Track results by `Nc`, version, module size; assert minimum success thresholds (e.g., ≥95% per bucket).
- **Acceptance**: Thresholds met; failures include diagnostics from `OptimizedJABCode`.

## Phase 4 — Physical pipeline harness (print/capture)
- **Goal**: Validate 16–256 color modes with real devices.
- **Tasks**:
  - Create harness to print codes and capture via camera/scanner; record decode metrics.
  - Start with controlled illumination and fixed media; optionally add ICC-aware guidance later.
- **Acceptance**: Success criteria documented per `Nc`; known good configurations captured.

## Phase 5 — Packaging and CI hygiene
- **Goal**: Clean packaging and cross-platform confidence.
- **Tasks**:
  - Resolve shade warnings by excluding/merging overlapping resources (MANIFEST, module-info).
    - Files: `pom.xml`
  - Verify native loading on Linux/macOS/Windows; consistent `$ORIGIN`/rpath behavior.
  - Add CI matrix jobs to build, run full suite, and publish artifacts; add a tiny CLI smoke test.
- **Acceptance**: CI green on all targets; shaded artifact contains correct natives and loads at runtime.

## Phase 6 — Diagnostics and observability
- **Goal**: Make field triage easy without noisy logs by default.
- **Tasks**:
  - Add a runtime flag to surface detailed native diagnostics (NORMAL→COMPAT traces, status/V/module info).
  - Structured logging with correlation ids in decode paths.
- **Acceptance**: Toggle verified in tests; logs actionable when enabled, quiet by default.

## Phase 7 — Concurrency and lifecycle
- **Goal**: Ensure thread-safety and proper native resource management.
- **Tasks**:
  - Audit pointer JNI resource lifecycle and synchronization; add concurrency tests (parallel encode/decode).
- **Acceptance**: No leaks/races under load; tests pass repeatedly.

## Phase 8 — Docs and licensing readiness
- **Goal**: Prepare for internal/external consumers and eventual distribution.
- **Tasks**:
  - Update README and Javadoc:
    - Document decode status mapping (2/3 = success), NORMAL→COMPAT fallback, and pointer JNI usage.
    - Provide examples for 16–256 encode/decode.
  - Add LGPL-2.1 notice and guidance on dynamic vs static linking in `README.md` before distribution.
- **Acceptance**: Docs current; legal guidance present.

---

## References
- Encode palette generation and capacity: `javacpp-wrapper/src/jabcode/encoder.c`
  - `setDefaultPalette()`, `genColorPalette()`, `getSymbolCapacity()`, `encodeMasterMetadata()`
- Decode palette learning and interpolation: `javacpp-wrapper/src/jabcode/decoder.c`
  - `readColorPaletteInMaster()`, `interpolatePalette()`, `copyAndInterpolateSubblockFrom16To32()`
- Java pointer JNI and loader: `src/main/java/com/jabcode/internal/JABCodeNativePtr.java`, `src/main/java/com/jabcode/internal/NativeLibraryLoader.java`
- Build and native staging: `pom.xml` (exec-maven-plugin), `scripts/create_symlinks.sh`

## Risks and mitigations
- **Higher color modes (128/256) in uncontrolled environments**: sensitive to color drift.
  - Mitigation: rely on in-symbol palette learning; provide guidance or ICC-aware option for harsh conditions; start with 16–64 if needed.
- **Platform-native loading differences**: rpath/$ORIGIN issues.
  - Mitigation: CI smoke tests and explicit loader diagnostics; keep natives co-located.

## Acceptance summary (per phase)
- **Phase 1**: decodeEx parity, defaults unified, tests green.
- **Phase 2**: capacity/topology matrix green.
- **Phase 3**: robustness thresholds met with diagnostics.
- **Phase 4**: physical test harness with documented success criteria.
- **Phase 5**: clean shaded artifacts; CI matrix green.
- **Phase 6**: diagnostics toggle verified.
- **Phase 7**: concurrency and lifecycle proven safe.
- **Phase 8**: docs and licensing readiness.
