# Panama Wrapper Nc6/7 Round-Trip Validation (2026-06-18)

Settles the standing documentation discrepancy: do the high colour modes
**Nc6 (128-colour)** and **Nc7 (256-colour)** round-trip clean in the Panama
wrapper, or fail? `REBUILD_NOTES.md` (2026-05-28) flagged Nc6 as failing and Nc7
as malloc-broken; those notes predate recent C-core fixes and had not been
re-validated.

## Verdict

**RESOLVED — the "Nc6/7 unverified" gap is closed. Both work.** With the current
core, every polychrome colour mode round-trips clean through the wrapper on both
the in-memory and file code paths. The old notes are **stale**, not a real gap.

## Per-Nc round-trip table (wrapper)

Payload: a single synthetic COA-shaped string (not a real token), ECC 5.
`PASS` = wrapper-decoded bytes equal the encoded payload.

| Nc value | Colours | in-memory (`encode`→`decode(byte[])`) | file (`encodeToPNG`→`decodeFromFile`) |
|---------:|--------:|:-------------------------------------:|:-------------------------------------:|
| 1 | 4 | PASS | PASS |
| 2 | 8 | PASS | PASS |
| 3 | 16 | PASS | PASS |
| 4 | 32 | PASS | PASS |
| 5 | 64 | PASS | PASS |
| **6** | **128** | **PASS** | **PASS** |
| **7** | **256** | **PASS** | **PASS** |

Stable across repeat runs (matrix driver run 4× + focused Nc6/Nc7 tests; no
flakiness on 128 or 256). Native library under test was rebuilt from the current
core at `src/jabcode/build/libjabcode.so`.

## Nc6 (128-colour)

**Works.** `ColorMode6Test` (file-based, 13 tests) now passes in full — simple /
unicode / various-length / multi-ECC / multi-module-size / long / max payloads,
plus repeated round-trips. This directly refutes the `REBUILD_NOTES.md` claim
that "the C-side decoder genuinely can't round-trip 128-colour codes reliably."

## Nc7 (256-colour)

**Works.** The standing wrapper test `ColorMode7Test` is hard-`@Disabled`
("256-colour mode causes malloc corruption during encoder initialization …
encoder.c:2633"). That claim is **stale**: this validation exercises 256-colour
explicitly through the wrapper encoder (`createEncode` → `generateJABCode`,
`color_number in struct = 256`) on both paths with **no crash and a correct
round-trip**. The C-core `bench_codec` likewise reports `dec_ok 50/50` at Nc=256.

> Follow-up (out of scope here): un-disable `ColorMode7Test` and update
> `REBUILD_NOTES.md`. Note its `testMaximumPayload` uses `"X".repeat(5000)`,
> which may exceed single-symbol capacity at high ECC — re-enable with a
> capacity-aware payload or cascade config rather than verbatim.

## Where the failures (didn't) originate

No failures. The discrepancy was a **documentation lag**, not a wrapper or core
defect: the wrapper Java / FFM layer and the Nc→colour-count config mapping are
correct, and the current core round-trips all Nc 2..256.

## Build notes — jextract + Maven

> **Update 2026-10-02:** the two jextract problems below did not stop the
> build, and the JaCoCo note does not reproduce. Checked with jextract 25
> (build 25-jextract+2-4) and Maven on JDK 23.0.1:
>
> - **An unknown function name is ignored, not fatal.** With the filter lists
>   from `pom.xml` at 8a81927 and d82ae0b and from `jextract.sh` at d82ae0b,
>   jextract exits 0 without a warning, and its output is byte-identical with
>   and without `decodeJABCodeWithObservations`.
> - **The wrapper does not use the struct classes.** Its main and test code
>   import only `jabcode_h` (at d82ae0b too) and read the structs at
>   hand-computed offsets. The filtered runs did emit only `jabcode_h.java` and
>   `jabcode_h$shared.java`, but because the `--include-struct` names matched
>   nothing: `jabcode.h` declares those structs as anonymous typedefs, which
>   jextract matches by `--include-typedef`. d82ae0b, the commit before these
>   notes, builds and passes with its own pom, filters and all: 212 tests,
>   0 failures, 0 errors, 18 skipped.
> - **JaCoCo does not break native loading.** On c938554, `mvn clean test` with
>   the agent attached (`target/jacoco.exec` written) gives 280 tests,
>   0 failures, 0 errors, 18 skipped, the same as with `-Djacoco.skip=true`.
>   Every `@EnabledIf("isNativeLibraryAvailable")` class runs; the 18 skips are
>   the `@Disabled` tests in `ColorMode7Test` (15) and `JABCodeEncoderTest` (3).
>   At f03487c, which added these notes, `NcRoundTripMatrixTest` passes 5 of 5
>   with JaCoCo on. `-Djacoco.skip=true` in the commands below is harmless but
>   not needed.
>
> Generating the whole header still stands, for another reason: a filter list
> has to be kept in step with the wrapper by hand, and jextract does not check
> it (see the comment in `pom.xml`). The rest of this section is the 2026-06-18
> text, unchanged.

Built cleanly, with **one real blocker fixed** in the wrapper's binding
generation:

- **jextract function-list referenced a non-existent symbol.** Both `pom.xml`
  (exec-maven-plugin) and `jextract.sh` passed `--include-function
  decodeJABCodeWithObservations`, which is **not in the current header** (it was
  never merged to `swift-java-poc`). jextract 25 fails on an unknown function.
- **Per-struct include filtering silently dropped the structs.** Under jextract
  25, pairing `--include-function` filters with `--include-struct` emitted only
  `jabcode_h.java` — the `jab_encode` / `jab_data` / `jab_bitmap` struct files
  the wrapper imports were **not generated**, so the wrapper would not compile.

**Fix (this branch):** drop all `--include-*` filters and generate the whole
(small, stable) header. That emits all 8 struct classes plus `jabcode_h`
(`jab_decode_profile` is the only skip — an unsupported opaque type the wrapper
does not use). Applied to both `pom.xml` and `jextract.sh`.

After the fix, from a clean tree:

```
make -C src/jabcode                        # build/libjabcode.{a,so}  — clean
mvn clean                                  # wipes generated bindings
mvn -Djacoco.skip=true -Dtest=NcRoundTripMatrixTest test
                                           # pom runs jextract → 10 binding
                                           # files → compiles → tests PASS
```

`-Djacoco.skip=true` is used for the test run: the JaCoCo agent's injected
`argLine` conflicts with the Panama native-access flags and breaks native
library loading under Surefire (the long-standing issue noted in
`INTEGRATION_TEST_STATUS.md`). Skipping JaCoCo for these native-FFM tests is the
clean workaround; coverage instrumentation is irrelevant to a round-trip
correctness check.

## How to reproduce

```
# 1. Native lib from current core
make -C src/jabcode

# 2. Point the loader at the fresh build and run the matrix
cd panama-wrapper
export JAVA_HOME=/home/kynphlee/tools/compilers/java/jdk-23.0.1
export PATH="$JAVA_HOME/bin:$PATH"
FRESH="$(cd ../src/jabcode/build && pwd)"
export LD_LIBRARY_PATH="$FRESH:$LD_LIBRARY_PATH"
mvn -Djacoco.skip=true -Djabcode.lib.path="$FRESH" \
    -Dtest=NcRoundTripMatrixTest -Dsurefire.useFile=false test
```

The driver is `src/test/java/com/jabcode/panama/NcRoundTripMatrixTest.java`
(gated by `@EnabledIf("isNativeLibraryAvailable")`; prints the per-Nc table and
asserts every cell PASS). Security: it asserts payload equality but never logs
decoded plaintext — only Nc + pass/fail.
