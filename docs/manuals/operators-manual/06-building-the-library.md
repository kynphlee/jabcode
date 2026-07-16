# 6. Building the library and tools

<!-- objective: A developer-literate operator can build libjabcode (static + shared) and both CLI tools from a clean checkout on Linux, and name the Windows variant, producing build/libjabcode.so, bin/jabcodeWriter, bin/jabcodeReader -->

**In this chapter you will** build `libjabcode` (static and shared) and the two command-line tools from a clean checkout on Linux, learn what the Windows variant is called, and understand why two maintenance targets fail out of the box in this tree.

**You should already** be comfortable running `make` and `gcc` in a terminal, and know from [chapter 1](01-what-a-jab-code-is.md) what a JAB Code symbol is — this chapter is about producing the software that writes and reads one.

## What you are building

The repository contains four build units. Each has its own build file, and each produces a distinct artifact.

| Unit | Directory | Build file | Artifact |
|---|---|---|---|
| Core library (Linux) | `src/jabcode/` | `Makefile` | `build/libjabcode.a` and `build/libjabcode.so` <!-- anchor: src/jabcode/Makefile:11-12 --> |
| Core library (Windows) | `src/jabcode/` | `Makefile.win` | `build/libjabcode.dll` <!-- anchor: src/jabcode/Makefile.win:5 --> |
| Writer CLI | `src/jabcodeWriter/` | `Makefile` | `bin/jabcodeWriter` <!-- anchor: src/jabcodeWriter/Makefile:5 --> |
| Reader CLI | `src/jabcodeReader/` | `Makefile` | `bin/jabcodeReader` <!-- anchor: src/jabcodeReader/Makefile:5 --> |

Both CLI tools link against the core library, so the core library must be built first.

## Install the prerequisites first

You need `gcc` and `make`, plus development packages for three image libraries: **libpng16**, **libtiff**, and **zlib** (for example `libpng-dev`, `libtiff-dev`, and `zlib1g-dev` on Debian-family systems — check your distribution's package names).

Here is why the system packages matter in this particular tree, and it is worth understanding rather than memorizing: the tool Makefiles pass `-L../jabcode/lib` on their link lines, which historically pointed at prebuilt archives (`libpng16.a`, `libtiff.a`, `libz.a`) vendored inside `src/jabcode/lib/`. **Those archives are absent from this working tree.** The `-L` path therefore contributes nothing, and the linker falls back to whatever your system provides — so if the dev packages are missing, the tool builds fail at link time with unresolved `-ltiff`, `-lpng16`, or `-lz`. <!-- anchor: src/jabcodeWriter/Makefile:10 -->

One related subtlety: the *headers* for those libraries are still vendored in `src/jabcode/include/` at pinned versions — libpng `1.6.22`, zlib `1.2.8`, libtiff `4.0.10` — so you compile against those pinned headers while linking against your system's library binaries. A large version skew between the two is worth avoiding. <!-- anchor: docs/manuals/corpus-model.md §1.3 (png.h:316, zlib.h:40, tiffvers.h:1) -->

## Build the core library

From the repository root:

```sh
make -C src/jabcode
```

The default target (`all`) produces both `build/libjabcode.a` and `build/libjabcode.so`. <!-- anchor: src/jabcode/Makefile:26 -->

Two build facts are worth knowing before you file a bug against either of them:

- The compile flags are exactly `CFLAGS = -O2 -std=c11 -fPIC -D_POSIX_C_SOURCE=199309L`. The feature-test macro is not decoration — the Makefile's own comment explains it "exposes clock\_gettime / CLOCK\_MONOTONIC (used by the opt-in decode stage profiler in decode\_profile.h) under -std=c11". <!-- anchor: src/jabcode/Makefile:5-8 -->
- The shared library is linked with `-Wl,-soname,libjabcode.so` and against `-lpng16 -lz`. The soname is deliberate: per the Makefile comment, consumers that load the library by name need the `DT_SONAME` set, and "Without it, hosts need LD\_LIBRARY\_PATH/java.library.path". <!-- anchor: src/jabcode/Makefile:35-41 -->

## Build the two CLI tools

With the core library in place:

```sh
make -C src/jabcodeWriter
make -C src/jabcodeReader
```

Both tools use the same link line: `-L../jabcode/build -ljabcode -L../jabcode/lib -ltiff -lpng16 -lz -lm`, with `CFLAGS = -O2 -std=c11`. <!-- anchor: src/jabcodeWriter/Makefile:10 / src/jabcodeReader/Makefile:10 -->

Because `build/` contains both `libjabcode.a` and `libjabcode.so`, the linker's default resolution of `-ljabcode` picks the shared library — which means the run-time loader must be able to find `libjabcode.so`. The simplest way from the repo root:

```sh
export LD_LIBRARY_PATH="$PWD/src/jabcode/build"
```

This is the same requirement the core Makefile's soname comment describes for hosts that do not bundle the library. <!-- anchor: src/jabcode/Makefile:35-39 -->

## The Windows variant

On Windows the core library is built with MinGW using the separate build file:

```sh
make -f Makefile.win
```

It produces `build/libjabcode.dll` and links with `-L./lib/win64 -ltiff -lpng16 -lz -lm -shared` under `CFLAGS = -O2 -std=c11`. Note the same caveat applies doubly here: the `lib/win64/` directory of prebuilt Windows libraries is also absent from this working tree, so you must supply your own MinGW builds of libtiff, libpng16, and zlib. <!-- anchor: src/jabcode/Makefile.win:3-10; docs/manuals/corpus-model.md §1.3 -->

## `refresh-lib` and `check-lib` — and why they fail today

The core Makefile defines `VENDORED_DIR := ../../lib` — a repo-root `lib/` directory that, per the Makefile comment, "holds a VENDORED copy of the built library" consumed by an external panama-wrapper Maven build. Two maintenance targets serve it: <!-- anchor: src/jabcode/Makefile:14-19 -->

- `refresh-lib` copies the freshly built `.so` and `.a` into `../../lib/`; the comment calls it "the ONLY sanctioned way to update lib/libjabcode.{so,a}". <!-- anchor: src/jabcode/Makefile:46-51 -->
- `check-lib` is an ABI freshness guard: it compares the defined-global dynamic symbol set of the vendored `.so` against a fresh build using `readelf`, and is run by CI (`codec-regression.yml`). <!-- anchor: src/jabcode/Makefile:53-71 -->

Here is the honest state of this tree: **no repo-root `lib/` directory exists**, so both targets fail until you create it. `check-lib` exits 1 with the message `check-lib: missing ../../lib/libjabcode.so`, and `refresh-lib`'s `cp` into the nonexistent directory fails. If you are only building and using the CLI tools, you never need either target; if you maintain the vendored copy, `mkdir lib` at the repo root first, then `make -C src/jabcode refresh-lib`. <!-- anchor: src/jabcode/Makefile:62, 50; docs/manuals/corpus-model.md §6 NOT FOUND register -->

The Makefile also carries a family of test and benchmark targets (`test-roundtrip`, `test-eci`, `bench`, `profile`, and others in the `.PHONY` list) — useful once you start changing the codec, and covered in the Developer's Manual. <!-- anchor: src/jabcode/Makefile:24 -->

## Worked example: clean checkout to first round trip

From a clean checkout on Linux, with the dev packages installed:

```sh
make -C src/jabcode
make -C src/jabcodeWriter
make -C src/jabcodeReader

export LD_LIBRARY_PATH="$PWD/src/jabcode/build"

src/jabcodeWriter/bin/jabcodeWriter --input 'Hello world' --output test.png
src/jabcodeReader/bin/jabcodeReader test.png
```

Expected behavior: the three `make` invocations complete without error, leaving `src/jabcode/build/libjabcode.a`, `src/jabcode/build/libjabcode.so`, `src/jabcodeWriter/bin/jabcodeWriter`, and `src/jabcodeReader/bin/jabcodeReader`. The writer command creates `test.png` and exits 0; the reader prints the decoded message bytes to stdout followed by a newline and exits 0. (The encode command line is the writer's own built-in single-symbol example.) <!-- anchor: src/jabcodeWriter/jabwriter.c:55; src/jabcodeReader/jabreader.c:85-87 -->

One trap to know before you script around these tools: invoking either tool with `--help` (or no arguments) prints the usage text but exits **non-zero** — the writer returns 1 and the reader returns 255 — so a naive smoke test that runs `--help` and checks for exit 0 will always "fail". <!-- anchor: src/jabcodeWriter/jabwriter.c:435-439; src/jabcodeReader/jabreader.c:27-31 -->

## Try it

1. Your tool build fails at link time with `cannot find -ltiff`. What is the root cause in this tree, and the fix?
2. What exact artifacts does `make -C src/jabcode` produce, and which one do the CLI tools load at run time?
3. Why does `make -C src/jabcode check-lib` fail on a fresh checkout, and what would you do before running it for real?
4. What is the Windows build file called, and what does it produce?

<details><summary>Answers</summary>

1. The prebuilt archives in `src/jabcode/lib/` are absent from this working tree, so `-L../jabcode/lib` resolves nothing and the linker needs a system libtiff. Install your distribution's libtiff development package (plus libpng16 and zlib dev packages). <!-- anchor: src/jabcodeWriter/Makefile:10; docs/manuals/corpus-model.md §1.3 -->
2. `build/libjabcode.a` and `build/libjabcode.so`; the tools' `-ljabcode` resolves to the shared library, so they load `libjabcode.so` at run time (hence `LD_LIBRARY_PATH`). <!-- anchor: src/jabcode/Makefile:11-12, 35-41 -->
3. The repo-root `lib/` (`VENDORED_DIR := ../../lib`) does not exist in this tree, so the guard exits with `check-lib: missing ../../lib/libjabcode.so`. Create `lib/` at the repo root and populate it with `make -C src/jabcode refresh-lib` first. <!-- anchor: src/jabcode/Makefile:18, 62 -->
4. `Makefile.win`, producing `build/libjabcode.dll` (MinGW; you must supply the Windows third-party libraries since `lib/win64/` is also absent). <!-- anchor: src/jabcode/Makefile.win:5, 10 -->

</details>

## Where to go next

- Next: [chapter 7](07-encoding-with-jabcodewriter.md) puts `jabcodeWriter` to work — every flag, with three worked encodes.
- Deeper: build internals, the test-target suite, and CI's codec-regression guard are covered in the Developer's Manual (JC-T), forthcoming.
