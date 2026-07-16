# 1. Repository and build architecture

<!-- objective: A maintainer can build every artifact and run every make target from a clean checkout, and explain the four build units, their link lines, and the vendored-vs-system dependency posture, including why refresh-lib/check-lib currently fail (absent repo-root lib/). -->

This chapter is the build-system reference for the `swift-java-poc` fork of libjabcode. Task-level build walkthroughs live in the Operator's Manual, [../operators-manual/06-building-the-library.md](../operators-manual/06-building-the-library.md); this chapter states the facts a maintainer needs to modify the build safely. All paths are relative to the repository root.

## 1.1 Repository identity

- Branch: `swift-java-poc`; HEAD commit `8f76559343bbba75bc83b38bbe8bb1002a68dd0a`. <!-- anchor: .git/HEAD; .git/refs/heads/swift-java-poc (corpus §1.1) -->
- License: LGPL 2.1, full text in `LICENSE`. <!-- anchor: LICENSE:1-504 (corpus §1.1) -->
- Codec sources: `src/jabcode/` — 17 library `.c` files plus headers, a `test/` suite, and vendored third-party headers under `src/jabcode/include/` (libpng 1.6.22, zlib 1.2.8, libtiff 4.0.10). <!-- anchor: corpus §1.3, §2.3 -->
- CLI sources: `src/jabcodeWriter/` (`jabwriter.c`, 507 lines; `jabwriter.h`, empty), `src/jabcodeReader/` (`jabreader.c`, 93 lines). <!-- anchor: corpus §2.3 -->
- `README.md` at the repository root: **NOT FOUND** in this working tree (it exists at the upstream clone's HEAD). <!-- anchor: corpus §1.3 -->

## 1.2 The four build units

| Unit | Directory | Build file | Artifact(s) | Link line (verbatim) |
|---|---|---|---|---|
| Core library (Linux) | `src/jabcode/` | `Makefile` | `build/libjabcode.a`, `build/libjabcode.so` | `$(CC) -shared -Wl,-soname,libjabcode.so -o $@ $(OBJECTS) -lpng16 -lz` <!-- anchor: src/jabcode/Makefile:41 --> |
| Core library (Windows) | `src/jabcode/` | `Makefile.win` | `build/libjabcode.dll` | `$(CC) $^ -L./lib/win64 -ltiff -lpng16 -lz -lm -shared $(CFLAGS) -o $@` <!-- anchor: src/jabcode/Makefile.win:10 --> |
| Writer CLI | `src/jabcodeWriter/` | `Makefile` | `bin/jabcodeWriter` | `$(CC) $^ -L../jabcode/build -ljabcode -L../jabcode/lib -ltiff -lpng16 -lz -lm $(CFLAGS) -o $@` <!-- anchor: src/jabcodeWriter/Makefile:10 --> |
| Reader CLI | `src/jabcodeReader/` | `Makefile` | `bin/jabcodeReader` | `$(CC) $^ -L../jabcode/build -ljabcode -L../jabcode/lib -ltiff -lpng16 -lz -lm $(CFLAGS) -o $@` <!-- anchor: src/jabcodeReader/Makefile:10 --> |

Facts that follow directly from the build files:

- The core library compiles **every** `.c` in `src/jabcode/`: `SOURCES := $(wildcard *.c)`. Adding a source file requires no Makefile edit; conversely, any scratch `.c` dropped into the directory is silently compiled into both libraries. <!-- anchor: src/jabcode/Makefile:21-22 -->
- The static archive is produced with `$(AR) cru $@ $(OBJECTS)` followed by `$(RANLIB) $@`. <!-- anchor: src/jabcode/Makefile:32-33 -->
- Core compile rule: `$(CC) -c -I. -I./include $(CFLAGS) $< -o $@`. <!-- anchor: src/jabcode/Makefile:44 -->
- Writer and reader compile with `-I. -I../jabcode -I../jabcode/include` — the CLIs can (and the writer does not, but could) include internal codec headers, not just `jabcode.h`. <!-- anchor: src/jabcodeWriter/Makefile:13; src/jabcodeReader/Makefile:13 -->
- Writer/reader `CFLAGS = -O2 -std=c11` — no `-fPIC`, no feature macro. <!-- anchor: src/jabcodeWriter/Makefile:3; src/jabcodeReader/Makefile:3 -->
- The `-soname` on the shared library is load-bearing. Makefile comment, verbatim: "-soname is load-bearing for self-loading consumers (qrforge NativeJabLib): they System.load() the bundled .so from a temp path, then the jextract bindings dlopen(\"libjabcode.so\") by NAME — glibc only matches the already-loaded library if its DT_SONAME says so. Without it, hosts need LD_LIBRARY_PATH/java.library.path (as the panama-wrapper tests set)." <!-- anchor: src/jabcode/Makefile:35-39 -->

Dependency edges: `jabcodeWriter → libjabcode`, `jabcodeReader → libjabcode`, `libjabcode → libpng16, zlib` (shared-object link) with `libtiff` added at tool link time for the CMYK TIFF save path. <!-- anchor: src/jabcode/Makefile:41; src/jabcodeWriter/Makefile:10; corpus §2.1 --> The internal (object-level) dependency graph is chapter 2's subject ([02-codec-pipeline.md](02-codec-pipeline.md), §2.3).

## 1.3 CFLAGS and the `_POSIX_C_SOURCE` rationale

```make
CFLAGS	= -O2 -std=c11 -fPIC -D_POSIX_C_SOURCE=199309L
```

<!-- anchor: src/jabcode/Makefile:8 -->

The feature macro is not optional decoration. Makefile comment, verbatim: "\_POSIX\_C\_SOURCE exposes clock\_gettime / CLOCK\_MONOTONIC (used by the opt-in decode stage profiler in decode\_profile.h) under -std=c11. Defined globally so the feature macro precedes every system-header include in every TU." <!-- anchor: src/jabcode/Makefile:5-7 -->

`decode_profile.h` carries a guarded fallback definition (`#ifndef _POSIX_C_SOURCE` / `#define _POSIX_C_SOURCE 199309L`) so the header stays self-contained for a TU that includes it first, but a consumer that has already pulled in `<time.h>` must rely on the command-line definition — a feature macro is only effective before the first libc header. <!-- anchor: src/jabcode/decode_profile.h:38-47 --> The benchmark targets that time with the monotonic clock repeat `-D_POSIX_C_SOURCE=199309L` on their own compile lines (`bench-concurrent`, `bench-cascade`, `profile`, `test-concurrent`). <!-- anchor: src/jabcode/Makefile:91,104,118,183 -->

## 1.4 Make targets — complete table

All phony targets are declared on one line. <!-- anchor: src/jabcode/Makefile:24 --> Stated purposes below are quoted or condensed from the Makefile's own comments.

| Target | Line | Builds / runs | Stated purpose (from Makefile comments) |
|---|---|---|---|
| `all` | 26 | `build/libjabcode.a` + `build/libjabcode.so` | default target <!-- anchor: src/jabcode/Makefile:26 --> |
| `refresh-lib` | 49 | copies `.so` + `.a` to `$(VENDORED_DIR)/` | "the ONLY sanctioned way to update lib/libjabcode.{so,a} — hand-copied builds are how the pre-#110 thread-UNSAFE .so sat there undetected for weeks." <!-- anchor: src/jabcode/Makefile:46-51 --> |
| `check-lib` | 61 | ABI freshness guard | "Compares the DEFINED-GLOBAL dynamic symbol set — type + name — of the vendored .so against a fresh source build"; run by `codec-regression.yml`. <!-- anchor: src/jabcode/Makefile:53-71 --> |
| `clean` | 73 | removes libs, objects, symbol lists, bench/test binaries | see the omission note below <!-- anchor: src/jabcode/Makefile:73-74 --> |
| `bench` | 79 | `build/bench_codec` ← `test/bench_codec.c` | "Suite A: native codec microbenchmark -- encode+decode timing across Nc 0..7"; statically links libjabcode; "JSON on stdout, table on stderr". <!-- anchor: src/jabcode/Makefile:76-81 --> |
| `bench-concurrent` | 90 | `build/bench_concurrent` ← `test/bench_concurrent.c` | "Concurrent-THROUGHPUT benchmark -- the value PR #110 (reentrant codec) unlocked"; CONCURRENT vs SERIALIZED at each thread count; `-lpthread`; "JSONL stdout, table stderr". <!-- anchor: src/jabcode/Makefile:83-92 --> |
| `bench-cascade` | 103 | `build/bench_cascade` ← `test/bench_cascade.c` | "Multi-symbol (CASCADE) benchmark -- the axis PR #113 (high-colour cascade) made sound"; sweeps N (1..61) × Nc; modes `[curves\|matrix\|both]`; "doubles as a regression guard for #113". <!-- anchor: src/jabcode/Makefile:94-105 --> |
| `profile` | 117 | `build/bench_profile` ← `test/bench_profile.c` | "Per-stage decode profiling harness" (DETECT, PALETTE, COLOR\_CLASSIFY, DEINTERLEAVE, LDPC, DATA\_DECODE plus DETECT sub-stages); plotted by `scripts/plot_stage_profile.py` and `scripts/plot_detect_substage.py`. <!-- anchor: src/jabcode/Makefile:107-119 --> |
| `sweep` | 122 | `build/bench_sweep` ← `test/bench_sweep.c` | "Comprehensive capacity/latency/ECC sweep for the full-picture benchmark suite." <!-- anchor: src/jabcode/Makefile:121-124 --> |
| `transcode` | 127 | `build/transcode_tool` ← `test/transcode_tool.c` | "Encode/decode helper for the transcode-survival benchmark (benchmarks/transcode\_survival.py)" — that script is **NOT FOUND** in this repository. <!-- anchor: src/jabcode/Makefile:126-129; corpus §2.2 --> |
| `test-pn` | 133 | builds + runs `test/test_pn_index.c` | "Regression guard for the pn\_index() FP-UB fix. Self-contained -- pn\_index is a static inline in pseudo\_random.h, so no libjabcode link is needed." <!-- anchor: src/jabcode/Makefile:131-135 --> |
| `test-symid` | 139 | builds + runs `test/test_symbology_id.c` | "Regression guard for the Annex H symbology identifier (Table H.1). Self-contained -- the formatter is a header-only function in symbology\_id.h". <!-- anchor: src/jabcode/Makefile:137-141 --> |
| `test-eci` | 147 | builds + runs `test/test_eci.c` | "Bit-level regression guard for ECI decoding (ISO/IEC 23634 5.3.9 / 7.3)"; hand-crafted bit streams; asserts the `"\nnnnnn"` transmission output and the Annex H `]j1` modifier. <!-- anchor: src/jabcode/Makefile:143-149 --> |
| `test-table15` | 153 | builds + runs `test/test_table15.c` | "Bit-level guard for ISO 23634 Table 15 / FNC1 / 7.3 backslash-doubling." <!-- anchor: src/jabcode/Makefile:151-155 --> |
| `test-roundtrip` | 158 | builds + runs `test/test_text_roundtrip.c` | "Text-mode regression guard: encode->decode multi-mode strings byte-identical." <!-- anchor: src/jabcode/Makefile:157-160 --> |
| `test-cascade-hv` | 166 | builds + runs `test/test_cascade_highversion.c` | "High-version cascade + >8207-byte byte-run regression guard: the numeric shift-to-byte continuation token width (was 5 bits, must be 6) and the per-run continuation-factor reset." <!-- anchor: src/jabcode/Makefile:162-168 --> |
| `test-concurrent` | 182 | builds + runs `test/test_concurrent_roundtrip.c` | "Codec reentrancy (thread-safety) guard under ThreadSanitizer"; compiles `$(SOURCES)` directly with `-fsanitize=thread -O1 -g` — "linking the non-instrumented $(STATIC\_LIB) would hide exactly the races we guard against." <!-- anchor: src/jabcode/Makefile:170-186 --> |

Observed asymmetry: `clean` removes `bench_codec`, `bench_concurrent`, `bench_cascade`, `bench_profile`, and six test binaries (`test_pn_index`, `test_symbology_id`, `test_eci`, `test_table15`, `test_text_roundtrip`, `test_concurrent_roundtrip`), but **not** `build/bench_sweep`, `build/transcode_tool`, or `build/test_cascade_highversion`. <!-- anchor: src/jabcode/Makefile:74 -->

The benchmark targets and their arguments are treated at depth in [12-benchmark-estate.md](12-benchmark-estate.md); the regression targets in 13 (regression suite) and 14 (concurrency).

## 1.5 The vendored-library discipline (`refresh-lib` / `check-lib`)

```make
VENDORED_DIR := ../../lib
VENDORED_SO := $(VENDORED_DIR)/libjabcode.so
```

<!-- anchor: src/jabcode/Makefile:18-19 -->

Rationale, verbatim: "Repo-root lib/ holds a VENDORED copy of the built library: panama-wrapper's Maven build loads it via jabcode.lib.path=../lib (pom.xml), so it must track this source tree. Refresh it with \`make refresh-lib\`; CI enforces freshness via \`make check-lib\` (codec-regression.yml)." <!-- anchor: src/jabcode/Makefile:14-17 -->

`check-lib` mechanics: it extracts the defined-global dynamic symbol set of both the fresh build and the vendored `.so` with `readelf --dyn-syms --wide` filtered through `awk '$5=="GLOBAL" && $7!="UND" {print $4, $8}'` (type + name), sorts, and diffs. <!-- anchor: src/jabcode/Makefile:63-65 --> The guard is deliberately ABI-level, not bit-level: "an implementation-only change (same exported symbols) passes this check — the guard is ABI-level, not bit-level; refresh-lib after any codec change regardless." The type column exists because "PR #110 turned codec globals \_Thread\_local (OBJECT -> TLS), so a stale pre-#110 binary diverges here." <!-- anchor: src/jabcode/Makefile:53-60 --> Failure message: "check-lib: FAIL — vendored ... is stale (symbol set differs from source build)." with remediation "run 'make -C src/jabcode refresh-lib' and commit the updated lib/." <!-- anchor: src/jabcode/Makefile:65-70 -->

**Current state: both targets fail from this working tree.** The repo-root `lib/` directory that `VENDORED_DIR := ../../lib` names is **NOT FOUND**; `check-lib` exits 1 at its first guard (`test -f $(VENDORED_SO) || { echo "check-lib: missing $(VENDORED_SO)"; exit 1; }`) and `refresh-lib`'s `cp` has no destination directory until it is created. <!-- anchor: src/jabcode/Makefile:50,62; corpus §2.1, §6 NOT FOUND register --> Downstream consequences for binding consumers are chapter 17's subject ([17-downstream-bindings.md](17-downstream-bindings.md)).

## 1.6 Windows build (`Makefile.win`)

`Makefile.win` builds `build/libjabcode.dll` from the same `$(wildcard *.c)` object set with `CFLAGS = -O2 -std=c11` — no `-fPIC`, no `_POSIX_C_SOURCE`, no soname machinery — and links `-L./lib/win64 -ltiff -lpng16 -lz -lm -shared`. <!-- anchor: src/jabcode/Makefile.win:3,5-10 --> It defines no targets beyond the DLL and `clean`; none of the bench/test/vendoring targets exist on Windows. <!-- anchor: src/jabcode/Makefile.win:1-16 --> The `lib/win64` directory it links against is absent from this working tree (see §1.7), so the Windows link currently depends on toolchain-provided import libraries.

## 1.7 Working-tree caveats

Maintainer-relevant facts about this checkout, stated without remediation advice:

- **Prebuilt dependency archives are absent.** `src/jabcode/lib/{libpng16.a, libtiff.a, libz.a}` (and `lib/win64/`, plus the libpng/zlib license text files) are referenced by the writer/reader link lines (`-L../jabcode/lib`) and `Makefile.win` (`-L./lib/win64`) but do not exist in this tree; link resolution falls through to system libraries. <!-- anchor: corpus §1.3; src/jabcodeWriter/Makefile:10; src/jabcode/Makefile.win:10 -->
- **Repo-root `lib/` is absent** — see §1.5. <!-- anchor: corpus §6 NOT FOUND register -->
- **Scratch headers sit beside the real public header.** `src/jabcode/include/` contains `jabcode.h.bak`, `jabcode.h.bak2`, `jabcode_fixed.h`, `fixed_declaration.txt`, and `fixed_line.txt` alongside `jabcode.h` and `jabcode_wrapper.h`. Only `jabcode.h`/`jabcode_wrapper.h` are part of the public surface (chapter 3); the rest are uncommitted working-layer residue. Because headers are not matched by `$(wildcard *.c)`, they do not affect the build, but any tooling that globs `include/*.h` will pick up `jabcode_fixed.h`. <!-- anchor: src/jabcode/include/ directory listing; corpus §1.2 -->
- **Build residue is present**: `src/jabcode/build-debug/`, `*.o` files, and compiled test binaries beside their sources in `src/jabcode/test/`. <!-- anchor: corpus §1.2-1.3 -->
- **No repo-root `README.md`** in this tree. <!-- anchor: corpus §1.3 -->
- Several test sources in `src/jabcode/test/` (the `test_roundtrip_*`, `test_multi_frame_*`, calibration and mode-specific families) have **no Makefile target** — present-but-unwired; see chapter 13. <!-- anchor: corpus §2.3 -->

## 1.8 Operations note — two clones, one path

Anyone driving this repository from a shell must verify which clone the shell actually sees. During corpus construction, the session shell's mount of this folder resolved to a **different clone** — upstream jabcode 2.0.0 on `master` (HEAD `3b56eef`, 19-line Makefile, 1832-line `decoder.c`, MIT-relicensed `LICENSE`) — while the direct file view holds the `swift-java-poc` fork this manual documents (17-file codec, LGPL 2.1, expanded Makefile). Every content statement in this book comes from the direct file view. A `git status` for this working tree is not obtainable through such a shell; do not trust shell-side git metadata for this repository without first confirming `.git/HEAD` matches `refs/heads/swift-java-poc`. <!-- anchor: corpus §1.2 -->

---

Next: [02-codec-pipeline.md](02-codec-pipeline.md) maps the encode and decode pipelines over these build units. The public linkage surface produced by §1.2's library artifacts is enumerated in [03-public-surface-jabcode-h.md](03-public-surface-jabcode-h.md).
