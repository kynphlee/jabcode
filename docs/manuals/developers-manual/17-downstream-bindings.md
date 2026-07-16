# 17. Downstream bindings

<!-- objective: A binding author can state the contract the Panama wrapper consumes — the public API of ch. 3 plus the VENDORED_DIR refresh/check discipline and ABI symbol-set guard — and account for the framework-side mapping (pixel-vs-module symbolWidth tension, enablePooling/optimizedSaving not forwarded, cascade exposure), including the historical symbolWidth/symbolHeight reconciliation. -->

**Scope.** The binding contract has two halves: what this repository *promises* (the public API of [03-public-surface-jabcode-h.md](03-public-surface-jabcode-h.md), a vendored library with a refresh/check discipline, an soname, and — nominally — a wrapper header), and what the principal consumer — the `jab-auth-framework` Panama stack — *actually does* with it. Task-level treatment of the consumer chain is JC-U ([../operators-manual/11-service-binding-chain.md](../operators-manual/11-service-binding-chain.md), [../operators-manual/12-service-vs-sdk-configuration.md](../operators-manual/12-service-vs-sdk-configuration.md)); this chapter restates it at maintainer depth with the tensions named.

## 17.1 The vendored-library contract: `VENDORED_DIR`, `refresh-lib`

The Makefile designates a repo-root `lib/` as the sanctioned hand-off point:

> "Repo-root lib/ holds a VENDORED copy of the built library: panama-wrapper's Maven build loads it via jabcode.lib.path=../lib (pom.xml), so it must track this source tree. Refresh it with \`make refresh-lib\`; CI enforces freshness via \`make check-lib\` (codec-regression.yml)."

```make
VENDORED_DIR := ../../lib
VENDORED_SO := $(VENDORED_DIR)/libjabcode.so
```

<!-- anchor: src/jabcode/Makefile:14-19 -->

`refresh-lib` copies both artifacts after a full build, and its comment is policy, quoted verbatim:

> "Refresh the vendored repo-root lib/ from the current source build. This is the ONLY sanctioned way to update lib/libjabcode.{so,a} — hand-copied builds are how the pre-#110 thread-UNSAFE .so sat there undetected for weeks."

```make
refresh-lib: all
	cp $(SHARED_LIB) $(STATIC_LIB) $(VENDORED_DIR)/
	@echo "refreshed $(VENDORED_DIR)/libjabcode.{so,a} from $(CORE_DIR)/"
```

<!-- anchor: src/jabcode/Makefile:46-51 -->

## 17.2 The ABI guard: `check-lib`

`check-lib` diffs the **defined-global dynamic symbol set** — type + name — of the vendored `.so` against a fresh source build, via `readelf --dyn-syms --wide` piped through `awk '$5=="GLOBAL" && $7!="UND" {print $4, $8}' | sort`, failing with "vendored … is stale (symbol set differs from source build)" and instructing `make -C src/jabcode refresh-lib`. <!-- anchor: src/jabcode/Makefile:61-71 --> The rationale comment fixes both the design and its limit:

> "Symbol names/types are compiler-stable where raw bytes are not, and type covers exactly the rot class that bit us: PR #110 turned codec globals \_Thread\_local (OBJECT -> TLS), so a stale pre-#110 binary diverges here. NOTE: an implementation-only change (same exported symbols) passes this check — the guard is ABI-level, not bit-level; refresh-lib after any codec change regardless."

<!-- anchor: src/jabcode/Makefile:53-60 --> CI runs it on every relevant PR: the "Check vendored lib/ freshness" step of `codec-regression.yml`, whose comment retells the same incident ("A stale pre-#110 (thread-UNSAFE, non-TLS-globals) binary sat there undetected because nothing compared it against source"). <!-- anchor: .github/workflows/codec-regression.yml:61-69 -->

**Current failure mode.** No repo-root `lib/` exists in this working tree (corpus §2.1 / NOT FOUND register). Consequences, mechanically: `check-lib` exits 1 at its first line — `test -f $(VENDORED_SO) || { echo "check-lib: missing ../../lib/libjabcode.so"; exit 1; }` — and `refresh-lib`'s `cp` fails because the destination directory is absent. <!-- anchor: src/jabcode/Makefile:50, 62 --> The CI workflow *does* list `lib/**` in its trigger paths and runs `check-lib` unconditionally, so on a checkout matching this tree the job fails at that step. <!-- anchor: .github/workflows/codec-regression.yml:23, 67-69 --> Restoring the discipline means recreating `lib/`, running `refresh-lib`, and committing the artifacts.

Related link-line contract for self-loading consumers: the shared library is built with `-Wl,-soname,libjabcode.so` because jextract-generated bindings `dlopen("libjabcode.so")` **by name** — "glibc only matches the already-loaded library if its DT_SONAME says so. Without it, hosts need LD_LIBRARY_PATH/java.library.path (as the panama-wrapper tests set)." <!-- anchor: src/jabcode/Makefile:35-41 -->

## 17.3 The wrapper header that isn't backed

`include/jabcode_wrapper.h` declares six `extern "C"`-guarded wrappers:

```c
jab_encode* createEncodeWrapper(jab_int32 color_number, jab_int32 symbol_number);
void destroyEncodeWrapper(jab_encode* enc);
jab_int32 generateJABCodeWrapper(jab_encode* enc, jab_data* data);
jab_data* decodeJABCodeWrapper(jab_bitmap* bitmap, jab_int32 mode, jab_int32* status);
jab_data* decodeJABCodeExWrapper(jab_bitmap* bitmap, jab_int32 mode, jab_int32* status, jab_decoded_symbol* symbols, jab_int32 max_symbol_number);
jab_boolean saveImageWrapper(jab_bitmap* bitmap, jab_char* filename);
```

<!-- anchor: src/jabcode/include/jabcode_wrapper.h:10-15 --> **Implementations: NOT FOUND in this tree** (corpus §3.3). Binding authors must treat the header as aspirational: linking against these symbols fails, and the shipped Panama wrapper binds the *real* API names of ch. 3 (`createEncode`, `generateJABCode`, `decodeJABCode(Ex)`, …), not the `*Wrapper` aliases. Any future decision should either implement the six functions in a compiled TU (so they appear in the `check-lib` symbol set) or delete the header; the present state documents an interface no artifact exports.

## 17.4 Consumer-side provenance validation

The framework refuses to *consume* an unverified binary, mirroring this repository's refusal to *publish* a stale one. `jab-auth-jabcode`'s `validateNativeLib` Gradle task runs before `processResources` (`processResources.dependsOn validateNativeLib`) and fails the build on any of: <!-- anchor: JABCodeCOA-crypto/jab-auth-jabcode/build.gradle:4-102 -->

1. missing `src/main/resources/libjabcode.so`; a **symlink** ("Symlinks break Docker COPY when the target is outside the build context"); size below `100_000` bytes ("suspiciously small"); <!-- anchor: jab-auth-jabcode/build.gradle:12-26 -->
2. ELF-shape violations, parsed in pure Groovy/Java because "the `file` utility is not present in the Docker builder image": magic `0x7F 'E' 'L' 'F'`, `EI_CLASS = 2` (64-bit), `EI_DATA = 1` (little-endian), `e_type = 3` (`ET_DYN`), `e_machine = 0x3E` (x86-64); <!-- anchor: jab-auth-jabcode/build.gradle:27-52 -->
3. provenance mismatch: SHA-256 of the `.so` against `libjabcode.so.provenance` (keys `libjabcode.so.sha256`, `jabcode-panama.jar.sha256`, `jabcode.decoder.commit`, `libjabcode.so.buildid`), plus the paired Panama jar's hash — "they are vendored as one unit". The error text names the intent: "libjabcode.so is STALE or MISMATCHED — provenance assertion failed … The vendored .so does not match its provenance." <!-- anchor: jab-auth-jabcode/build.gradle:53-95; framework corpus §3.12 -->

Division of labour, stated once: **`check-lib` proves the published copy matches *this source tree* (freshness, ABI-level); `validateNativeLib` proves the consumed copy matches *its certification record* (identity, bit-level SHA-256).** The ELF checks prove "this is *a* shared object; the SHA-256 checks … prove it is *the* artifact we certified". <!-- anchor: jab-auth-jabcode/build.gradle:54-57 --> Policy document on the consumer side: `LIBJABCODE.md` (framework repo root). The wrapper jar itself is vendored at `libs/jabcode-panama-1.0.0-SNAPSHOT.jar` rather than resolved from mavenLocal "so CI and fresh clones don't depend on a developer having previously run `./gradlew publishToMavenLocal`". <!-- anchor: jab-auth-jabcode/build.gradle:111-116 -->

## 17.5 The framework mapping, at maintainer depth

`PanamaJabCodeService` (the production `JabCodeService`) reflectively drives `com.jabcode.panama.JABCodeEncoder$Config$Builder` from a `JabCodeConfig` record. <!-- anchor: JABCodeCOA-crypto/.../jabcode/PanamaJabCodeService.java:35-38, 268-332 --> The record:

```java
public record JabCodeConfig(
    ColorMode colorMode, int moduleSize, int eccLevel,
    Integer symbolWidth, Integer symbolHeight,
    boolean enablePooling, boolean optimizedSaving,
    int symbolNumber, List<SymbolVersion> symbolVersions)
```

<!-- anchor: JabCodeConfig.java:34-44 -->

### Forwarded faithfully

`colorMode` → `colorNumber` (via `mode.getColorCount()`), `eccLevel`, `moduleSize`, `symbolNumber`, `symbolVersions` (reflectively constructed `com.jabcode.panama.SymbolVersion(x, y)` instances). Builder-order constraint recorded in source: "symbolNumber MUST be set before symbolVersions: the Panama builder validates that versions.size() == symbolNumber". <!-- anchor: PanamaJabCodeService.java:275-313, 398-400 --> Validation lives in the record's canonical constructor: `symbolNumber ∈ [1, 61]`, `eccLevel ∈ [1, 10]` ("Level 0 is the codec's 'unset -> default' sentinel, not a spec level, so it is rejected"), `symbolVersions.size() == symbolNumber`, per-element side-versions in \[1, 32\]. <!-- anchor: JabCodeConfig.java:80-131 --> This is deliberately *stricter* than the native writer CLI, which admits ECC 0 as "use the default level" ([11-cli-internals.md](11-cli-internals.md) §11.3 item 10).

### The pixel-vs-module `symbolWidth` tension

The two Javadocs contradict each other, and both are current source:

- `JabCodeConfig` declares the legacy fields to be **pixels**: "The legacy symbolWidth/symbolHeight fields are **pixel** dimensions of the master symbol (the native `--symbol-width` / `--symbol-height` CLI options, i.e. masterSymbolWidth/masterSymbolHeight in the Panama Config) … the native encoder derives an effective module size by dividing the requested pixel size by the symbol's module side-size." <!-- anchor: JabCodeConfig.java:22-32 --> The native CLI agrees: "Master symbol width in pixel." <!-- anchor: src/jabcodeWriter/jabwriter.c:37-38, 173 -->
- `PanamaJabCodeService.reconcileSymbolVersions` interprets the same fields as **module counts**: "The legacy fields are interpreted as the master symbol's per-axis **module count** (not raw pixels), because that is the dimension the native codec actually consumes via symbol_versions\[0\]; the shipped Panama JAR's encoder ignores the pixel-based masterSymbolWidth." <!-- anchor: PanamaJabCodeService.java:350-354 -->

What actually executes: if `symbolVersions` is null and `symbolNumber == 1` and both legacy fields are set, the service derives `version = (modules - 17) / 4` (the native `SIZE2VERSION` inverse <!-- anchor: PanamaJabCodeService.java:376-379; src/jabcode/include/jabcode.h:54 -->) and installs it as the master's side-version; out-of-range results fall back to auto-sizing. It *also* forwards the raw values to the builder's `masterSymbolWidth`/`masterSymbolHeight` as declared future-compat pixels ("setting them here keeps us correct if a future JAR honours pixel master sizing"). <!-- anchor: PanamaJabCodeService.java:286-291, 315-328, 356-374 --> The hazard is quantitative: `(px - 17) / 4 ∈ [1, 32]` for `px ∈ [21, 145]` — exactly the range of plausible small pixel dimensions — so a caller passing true pixels (say 128) silently gets a version-27 symbol (125 modules per side), not a 128-pixel one. Until the wrapper honours pixel sizing, callers must pass module counts (or, better, explicit `symbolVersions`) and maintainers must keep the two Javadocs' disagreement visible rather than "harmonising" it away.

### Not forwarded at all

`enablePooling` and `optimizedSaving` are record fields that **never appear** in `createPanamaConfig` or anywhere else in `PanamaJabCodeService` — declared-but-inert at the Panama boundary. <!-- anchor: JabCodeConfig.java:40-41; PanamaJabCodeService.java:268-332 (no reference; verified by search) --> Similarly inert one level up: the `jabauth.jabcode.*` Spring properties (`color-depth 8`, `error-correction "HIGH"`, `default-size "20x20mm"`) have no found consumer on the codec path, whose real defaults are `JabCodeConfig.defaultConfig()` = 4-colour, module size 12, ECC 3 — "treat the record as declarative only". <!-- anchor: framework corpus §3.1 note and discrepancy log #3 -->

### Cascade exposure

Cascades are fully exposed through the mapping (`symbolNumber` up to `MAX_SYMBOL_NUMBER = 61`, per-symbol `SymbolVersion` with `moduleWidth() = 4*x + 17`), and a `cascade(int eccLevel, int... squareVersions)` preset exists. <!-- anchor: JabCodeConfig.java:79-80, 54-76; framework corpus §3.5 --> The native-side construction rules the wrapper must honour are those of `bench_cascade.c` ([12-benchmark-estate.md](12-benchmark-estate.md) §12.3): sequential positions, explicit versions for every symbol, per-symbol ECC.

## 17.6 Case study: the `symbolWidth`/`symbolHeight` reconciliation

The current mapping is the third state of a documented history, and it is the estate's best worked example of a binding defect and its remediation. State one: the fields existed on `JabCodeConfig` but `PanamaJabCodeService.createPanamaConfig` never read them — "They were previously dropped on the floor" <!-- anchor: JabCodeConfig.java:29-30 --> — a silent-loss defect recorded as a standing finding ("JabCodeConfig symbolWidth/Height dropped"). State two (resolved 2026-07-15): the reconciliation shipped — explicit `symbolVersions` win verbatim; otherwise single-symbol configs derive the master version from the legacy fields via `SIZE2VERSION`; the raw values are additionally forwarded as pixel knobs for a future wrapper. <!-- anchor: PanamaJabCodeService.java:334-374; JabCodeConfig.java:30-32; framework corpus discrepancy log #2 --> State three is the residue this chapter documents: the fields now *do something*, but under a module-count interpretation that contradicts their pixel-denominated declaration (§17.5) — resolved for behaviour, unresolved for semantics.

The same binding lineage left a second fingerprint in *this* repository's regression fixtures: `test_cascade_highversion.c`'s "wrapper-parity" cases encode with "ECC set on symbol 0 ONLY, slaves left at 0 (createEncode zero-init)" because that is "the shape the Panama wrapper actually produced" historically — a wrapper convention preserved as a codec test fixture so the native side keeps decoding payloads produced under it. <!-- anchor: src/jabcode/test/test_cascade_highversion.c:68-70, 141-145 --> The general lesson for binding authors: every mapping decision (defaulting, per-symbol fan-out, unit interpretation) eventually becomes either a regression fixture or a bug report; write it down at the boundary when you make it.

## 17.7 Binding-author checklist (contract summary)

| Contract element | Producer side (this repo) | Consumer side (framework) |
|---|---|---|
| API surface | `include/jabcode.h` externs (ch. 3); `generateJABCode` returns 0 on success | Panama wrapper binds these names; `PanamaJabCodeService` wraps them <!-- anchor: PanamaJabCodeService.java:94-139 --> |
| Library hand-off | `make refresh-lib` into repo-root `lib/` — "the ONLY sanctioned way" <!-- anchor: src/jabcode/Makefile:46-48 --> | vendored copy at `src/main/resources/libjabcode.so` + jar at `libs/` |
| Staleness guard | `make check-lib` symbol-set diff (ABI-level), CI-enforced <!-- anchor: src/jabcode/Makefile:61-71 --> | `validateNativeLib` ELF + SHA-256 provenance (bit-level) <!-- anchor: jab-auth-jabcode/build.gradle:4-102 --> |
| Load mechanics | `DT_SONAME libjabcode.so` for by-name lookup <!-- anchor: src/jabcode/Makefile:35-41 --> | `SymbolLookup.libraryLookup` by name; `java.library.path`/`LD_LIBRARY_PATH`; `--enable-native-access=ALL-UNNAMED` <!-- anchor: framework corpus §3.12 --> |
| Threading | reentrant per-op state, process-global toggles ([14-concurrency.md](14-concurrency.md)) | unlocked multi-threaded use; JNA-side `jna.protected` livelock lesson (§14.6) |
| Known gaps | repo-root `lib/` absent (refresh/check currently fail); `jabcode_wrapper.h` unimplemented | `enablePooling`/`optimizedSaving` inert; pixel-vs-module `symbolWidth` semantics; `jabauth.jabcode.*` inert |
