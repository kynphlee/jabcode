# Resolution Plan: JavaCPP Classpath Conflict in JABCode Wrapper (Deployed Artifact)

- **Module**: `javacpp-wrapper` (JABCode Java library)
- **Artifact**: `com.jabcode:jabcode-java:1.0.0`
- **Issue**: Native loader failure in external consumer (QR-Forge) during first JAB encode
- **Scope focus**: 4- and 8-color modes must work now; ≥16 colors tracked separately
- **Date**: 2025-10-22

## Summary
External project reports `ClassCastException` in JavaCPP’s loader during JABCode encode, preventing HiCAP frames. Our wrapper bundles shaded JavaCPP, but also leaks unshaded `org.bytedeco` via JavaCV dependencies, causing both shaded and unshaded JavaCPP classes to coexist at runtime. This mixed-classpath condition triggers the observed error.

## Root cause (from this codebase)
- **POM conflict** (`javacpp-wrapper/pom.xml`):
  - Declares `org.bytedeco:javacv` and `org.bytedeco:javacv-platform` as dependencies.
  - Shade relocation relocates only `javacpp`/`javacpp-platform` (`org.bytedeco → com.jabcode.internal.bytedeco`).
  - Result: unshaded JavaCV (and its transitive `org.bytedeco:javacpp`) remain visible to consumers, while our jar also contains shaded JavaCPP. Mixed shaded/unshaded classes cause `ClassCastException` in `Loader.putMemberOffset`.

## Evidence in repository
- `javacpp-wrapper/pom.xml`:
  - Has `org.bytedeco:javacpp` (OK) and `org.bytedeco:javacv`, `org.bytedeco:javacv-platform` (problematic for consumers).
  - Shade plugin relocates only `javacpp*` artifacts. JavaCV is not shaded → leaks to consumer classpath.
- JNI bindings:
  - `src/main/java/com/jabcode/internal/JABCodeNative.java` and `JABCodeNativePtr.java` import JavaCPP Loader; shading relocates them fine when classpath is clean.

## Strategy (validated with web research)
- Ensure the published artifact does not expose any unshaded `org.bytedeco` dependencies. Either remove JavaCV or relocate it fully. Since 4/8-color functionality does not require JavaCV, remove it for now and keep only JavaCPP (+platform) shaded.

## Implementation steps (library)
1. **Remove JavaCV deps** from `javacpp-wrapper/pom.xml`:
   - Remove `<dependency>org.bytedeco:javacv</dependency>`
   - Remove `<dependency>org.bytedeco:javacv-platform</dependency>`
2. **Add platform natives for JavaCPP** (if not already present):
   - Add `<dependency>org.bytedeco:javacpp-platform:${javacpp.version}</dependency>`
3. **Keep relocation** across all bytedeco packages:
   - Shade plugin already relocates `org.bytedeco → com.jabcode.internal.bytedeco`.
   - Ensure `artifactSet.includes` covers both `javacpp` and `javacpp-platform` so only relocated copies are bundled.
4. **Publish 1.0.1**:
   - Bump artifact version to `1.0.1`.
   - Verify `dependency-reduced-pom.xml` contains no `org.bytedeco:*` entries.
5. **Sanity checks**:
   - `jar tf target/jabcode-java-1.0.1.jar | grep -E "org/bytedeco|com/jabcode/internal/bytedeco"` should show ONLY `com/jabcode/internal/bytedeco` tree, none under `org/bytedeco`.
   - Run `LowColorRoundtripTest` and decode smoke tests.

## Implementation steps (consumer, temporary)
- Until 1.0.1 is consumed, external projects should exclude `org.bytedeco:*` transitive deps from this library’s dependency declaration to avoid mixed-classpath.
- Add a graceful fallback in `HiCapFountain` (consumer): if JAB encode fails, generate a mono QR via ZXing to keep streaming functional.

## Validation plan
- In wrapper:
  - `mvn -q dependency:tree | grep -Ei "bytedeco|javacpp|javacv"` → empty after shading/reduction (for consumer POM view).
  - Run JNI tests: `LowColorRoundtripTest` and `HighColorRoundtripTest` (latter may still fail due to ongoing ≥16-color work; unrelated to loader fix).
- In consumer:
  - `mvn -q dependency:tree | grep -Ei "bytedeco|javacpp|javacv"` → ensure no unshaded org.bytedeco present.
  - Start HiCAP stream and allow a 1–2s warm-up; confirm frames on `/queue/fountain-stream`.

## Impact on color modes
- **4/8 colors**: Fully supported without JavaCV. The fix removes the loader conflict and unblocks these modes immediately.
- **≥16 colors**: Independent of this fix. Continue existing efforts:
  - Default palette override (`setUseDefaultPaletteHighColor`),
  - Forced ECL (`setForceEcl`) to diagnose LDPC/ECC issues,
  - Keep payload sizes modest while tuning.

## Risks and mitigations
- **Risk**: Some environments rely on JavaCV classes (not used by current encode/decode paths). Removing JavaCV could break those niche paths.
  - **Mitigation**: No current code path requires JavaCV for 4/8-color operations. Reintroduce later only if fully relocated.

## Next actions
- Apply POM changes and publish `1.0.1` (wrapper).
- Notify consumers to upgrade or add transitive exclusions.
- Keep ≥16-color work on separate branch/track.

## Appendix: files of interest
- `javacpp-wrapper/pom.xml`
- `src/main/java/com/jabcode/internal/JABCodeNative.java`
- `src/main/java/com/jabcode/internal/JABCodeNativePtr.java`
