# Dependency Profile & Ecosystem-Report Reconciliation — Panama (FFM) and Swift-Java (Mobile Bridge)

| Field | Value |
|---|---|
| **Scope** | `panama-*` (FFM/jextract) and `swift-java-wrapper` (mobile bridge) layers in the `kynphlee/jabcode` fork, **plus their downstream consumer** — the `jab-auth-framework` (`business-plans/JABCodeCOA-crypto`) |
| **Branches profiled** | jabcode: `panama-poc` (checked out) and `swift-java-poc`; framework: `main` |
| **Downstream consumer** | `JABCodeCOA-crypto` = `org.nexus.jabauth:jab-auth-framework` (GitLab `business-ops/jab-auth-framework`), Spring Boot 3.3.4, **Java 23**, 8 Gradle modules — see §4 |
| **Compared against** | Ecosystem-research daily report `business-plans/.../ecosystem-research/daily-reports/2026-05-20.md` (Session 2 — JABCode Technical Validation) and downstream citations (2026-06-01) |
| **Profiler** | J.A.R.V.I.S. (read-only audit; no code modified) |
| **Date** | 2026-06-13 |

---

## Executive Summary

Both integration layers are **real and substantive**, but the ecosystem reports describe them with **material drift in two places**. The Panama (Java FFM) layer is a genuine, JMH-benchmarked, jextract-generated wrapper — accurately reported. The "Swift-Java" layer is **not** Apple's `swiftlang/swift-java` toolchain at all; it is a hand-rolled **cross-platform C bridge** (Swift→C interop on iOS + hand-written JNI on Android over a shared `mobile_bridge.c`). The 2026-05-20 report escalated the repo's own *"Experimental"* label on Apple swift-java into *"✅ Integrated (Apple Official Tools)"* with functional macros and bidirectional `jextract` bindings — **none of which exist in the code or build**. Separately, the recommended `v1.0-mobile-ready` tag was **never created**, yet later reports cite it as established fact.

The **downstream consumer** closes the loop (§4): the `jab-auth-framework` (the `JABCodeCOA-crypto` project) consumes the **Panama layer only** — it vendors the `jabcode-panama` JAR plus a provenance-pinned `libjabcode.so` and loads them via FFM at runtime, behind a rigorous build-time validation gate. The **mobile Swift-Java bridge is not consumed by the framework at all** — it is a parallel mobile track. The framework's own provenance record also resolves the JDK-version ambiguity (**JDK 23 runtime, jextract 25 for bindings**) and adds a *third* color-mode figure (it asserts **8/8 colour modes nc0-nc7** roundtrip via FFM).

Headline verdict: the *engineering* is sound across producer and consumer; the *reporting* over-claims the Swift-Java toolchain and a release tag, and is loose on the color-mode count. Corrections are itemized in §7.

---

## 1. Panama (FFM) Layer — Profile

**Locations**: `panama-wrapper/` (the FFM Maven module), `panama-refactor/` (phased C-decomposition design docs), `panama-poc/` (thin README + example), `panama-wrapper-itest/` (integration tests). `javacpp-wrapper/` is the **legacy JNI/JavaCPP predecessor** (carries an `unused-dependencies.xml`).

| Attribute | Value | Source |
|---|---|---|
| Build system | Maven; `artifactId` `jabcode-panama` `1.0.0-SNAPSHOT` | `panama-wrapper/pom.xml` |
| Language level | `maven.compiler.source/target = 21`; README says "JDK 23+"; `jextract.sh` says "JDK 25+" | `pom.xml`, `README.md`, `jextract.sh` |
| Runtime dependencies | **Zero** — FFM (Foreign Function & Memory API) is built into the JDK | `pom.xml` ("No external dependencies! Panama FFM is built into JDK 23+") |
| Test/bench deps | JUnit 5.10.1, Mockito 5.8.0, JMH 1.37; `build-helper-maven-plugin` 3.5.0 | `pom.xml` |
| Binding generation | `jextract` → `com.jabcode.panama.bindings` from `src/jabcode/include/jabcode.h` → `target/generated-sources/jextract` (git-ignored) | `jextract.sh` |
| Native linkage | `--enable-native-access=ALL-UNNAMED`, `-Djava.library.path=../lib` (`libjabcode` in `../lib`) | baseline JSON `jvmArgs` |
| Maturity | **Phase 9 decoder integration COMPLETE** (2026-01-08); **25/27** integration tests passing (2 fail in high-color modes); `decode(byte[])` / `decodeEx(byte[])` still **stubs** | `PHASE9_COMPLETE.md` |
| Benchmarks | JMH baselines committed 2026-05-29 (encode + decode), run on **JDK 23.0.1** | `baseline-benchmarks/2026-05-29-*.json` |
| Perf sample | decode `colorMode=2, messageSize=100` → **10.53 ms** avg-time (scoreError ±0.24) | decode baseline JSON |
| Intended target | Server-side / desktop **pure-Java** JABCode (cloud services, AWS Lambda/Azure) | report §"Deployment Scenarios" |

**Assessment**: a legitimate FFM wrapper, well past PoC on the decode path, benchmarked with real JMH data. The only softness is JDK-version messaging (21 vs 23 vs 25 appear in different files) and the `decode(byte[])` stubs. FFM stabilized in JDK 22, so "JDK 25+" is conservative; the artifact actually compiles/benches under JDK 23.

---

## 2. Swift-Java Layer — Profile (branch `swift-java-poc`)

> **Naming caveat (important):** `swift-java-wrapper/` is **not** Apple's `swiftlang/swift-java`. It is a bespoke cross-platform mobile bridge. The branch name is the likely source of the report's conflation.

**Architecture** (per `swift-java-wrapper/CROSS_PLATFORM.md`): a shared C **`mobile_bridge.c` / `mobile_bridge.h`** (`jabMobileEncode`, `jabMobileDecode`, `jabMobileDecodeCamera`, thread-local error handling, calibration) sits over the ANSI-C JABCode core and is consumed by two native front-ends:

| Platform | Interop mechanism | Build | Key settings |
|---|---|---|---|
| **iOS** | Swift→C **direct interop via modulemap** (no Apple swift-java) | SwiftPM `Package.swift`, `swift-tools 5.9` | iOS 13+/macOS 10.15+; C targets compiled `-O3 -ffast-math`, `-DMOBILE_BUILD`; targets `JABCodeCore` (C) + `JABCodeMobile` (Swift) + tests |
| **Android** | **Hand-written JNI** (`jabcode_jni.c`) | Gradle Kotlin DSL + NDK + CMake | `namespace com.jabcode.mobile`, `compileSdk 35`, `minSdk 24`, `externalNativeBuild { cmake → ../../CMakeLists.txt }` |

**Dependencies**: iOS has **no external Swift package dependencies** (pure C interop). Android uses AGP/Gradle + NDK/CMake with hand-written JNI. A repo-wide grep for `swiftlang/swift-java`, `JavaKit`, `SwiftJava`, `jextract`-for-Swift, and `ordo-one/package-benchmark` returns **empty** in all code/build files.

**Test corpus**: extensive C unit tests under `swift-java-wrapper/test/c/` using the **Unity** framework (`test_encode`, `test_decode`, `test_encode_roundtrip`, `test_color_modes`, `test_mobile_bridge[_comprehensive]`, `test_lab_color`, `test_kdtree_color`, `test_png_roundtrip`, `test_consensus`, …), plus Android instrumented tests and a calibration test-app.

**Color modes**: `CROSS_PLATFORM.md` reports **6/7 working** (4, 8, 16, 32, 64, 128); **256-color broken (malloc)**; Mode 0 / 2-color monochrome operational.

**Historical blocker**: `PHASE1-BLOCKER.md` (2026-01-18) — `generateJABCode()` was **not exported** from `libjabcode.a` (it lived only in the `jabcodeWriter` app), which blocked all mobile encoding. Resolved by extracting the orchestration into the library; `CROSS_PLATFORM.md` now shows **encode ✅ Complete** on both platforms.

**Branch relationship**: `swift-java-poc` did inherit the Panama wrapper (`panama-wrapper/` is present on **both** branches). The branches have since **diverged sharply** — `swift-java-poc` is now **310 commits ahead / 55 behind** `panama-poc` (HEAD `39de8df`, 2026-06-11).

**Assessment**: a real, well-tested cross-platform bridge with the correct native-interop approach for each OS. "Production-ready" is generous — it is mature PoC-grade (no release tag; mobile encode only recently unblocked).

---

## 3. Side-by-Side

| Dimension | Panama (FFM) | Swift-Java (mobile bridge) |
|---|---|---|
| What it is | Pure-Java wrapper via Foreign Function & Memory API | Cross-platform C bridge for iOS (Swift C interop) + Android (JNI) |
| Build | Maven | SwiftPM (iOS) + Gradle/NDK/CMake (Android) |
| External deps | None (FFM in JDK) | None on iOS; AGP/NDK on Android |
| Binding gen | `jextract` (automatic, from `jabcode.h`) | Hand-written JNI + Swift modulemap (manual) |
| Target | Server / desktop / cloud Java | Mobile (iOS + Android) |
| Maturity | Decoder Phase 9 complete; 25/27 ITs; JMH-benchmarked | Encode unblocked; broad C unit tests; untagged |
| Toolchain | JDK 21/23/25 + jextract | swift-tools 5.9; NDK r-/CMake 3.28; compileSdk 35 |

---

## 4. Downstream Consumer — JABCodeCOA-crypto (`jab-auth-framework`)

This is the project that actually *ships* the JABCode capability. Profiling the producer layers without it leaves the question "consumed how, by what?" unanswered — so it belongs in this report.

| Attribute | Value | Source |
|---|---|---|
| Identity | `org.nexus.jabauth:jab-auth-framework` `0.0.1-SNAPSHOT`; GitLab `business-ops/jab-auth-framework`, branch `main` | root `build.gradle`, `git remote` |
| Platform | Spring Boot 3.3.4, Gradle, **Java 23** (`JavaLanguageVersion.of(23)`), OWASP dependency-check 8.4.0 | root `build.gradle` |
| Modules (8) | `jab-auth-core`, `-pki`, `-jwt`, `-abe`, `-jabcode`, `-spring-integration`, `-cloud-adapters`, `-emulator` (+ `jab-auth-field-test-pwa`, a PWA, not a Gradle module) | `settings.gradle` |

### 4.1 How it consumes the Panama layer

`jab-auth-jabcode` is the single module that binds the native kernel. It does **not** call the JABCode C library directly — it depends on the **exact Panama artifact** profiled in §1:

```gradle
// jab-auth-jabcode/build.gradle
implementation files("$rootDir/libs/jabcode-panama-1.0.0-SNAPSHOT.jar")   // the §1 FFM wrapper
```

Two upstream artifacts from the sibling `practice/barcode/jabcode/` repo are **vendored as real files** (not symlinks, not `mavenLocal`) so fresh clones, CI runners, and Docker builds all work without a "publish locally first" step (`LIBJABCODE.md`):

| Artifact | Vendored at | Role |
|---|---|---|
| `jabcode-panama-1.0.0-SNAPSHOT.jar` | `libs/` | the FFM wrapper (`JABCodeEncoder` / `JABCodeDecoder`) |
| `libjabcode.so` (209,112 B, ELF64 x86-64) | `jab-auth-jabcode/src/main/resources/` | the native kernel, loaded via Panama FFM at runtime |

### 4.2 Supply-chain integrity gate (notable engineering)

`jab-auth-jabcode/build.gradle` registers `validateNativeLib`, wired into `processResources`, which:

1. **Parses the ELF header in pure Java** (no `file` utility in the `eclipse-temurin:23-jdk` builder image): asserts magic `\x7fELF`, 64-bit, little-endian, `e_type=ET_DYN`, `e_machine=0x3E` (x86-64).
2. **SHA-256-asserts both** the `.so` *and* the paired Panama JAR against `libjabcode.so.provenance`, failing the build on a stale/mismatched binary — "converts a silent month-old-binary drift into a loud build failure."

The provenance pin (the certified source of the vendored binary):

| Field | Value |
|---|---|
| `jabcode.repo.commit` | `e94f56d…` (branch `claude/ws-nc2-decode-consensus`) |
| `jabcode.decoder.commit` | `d486388…` ("decode genuine default-mode Nc=2 under the strict Part-II gate") |
| `libjabcode.so.sha256` | `c0e66393…` |
| `jabcode.verified` | **"Panama JVM/FFM roundtrip decodes 8/8 colour modes (nc0-nc7)"** |

`LIBJABCODE.md` documents two **retired anti-patterns** that previously broke builds: a `libs/libjabcode.so` symlink to an absolute path (broke every Docker build — target outside build context) and `COPY .m2/` in the Dockerfile (relied on a developer's local Maven cache). Both are now replaced by in-repo vendored real files. This is mature supply-chain hygiene, not PoC-grade.

### 4.3 What the framework does NOT consume

- **The Swift-Java mobile bridge is absent from the framework's code and build.** A grep for `swift-java` / `mobile_bridge` / `jabMobile` hits only planning docs in `.claude/worktrees/`, never a `build.gradle` or `.java`. The framework is **server-side Panama/FFM only**; the mobile bridge is a **parallel track** that ships separately (its own SwiftPM + Android Gradle). Any report language implying the framework is "mobile-ready" via swift-java is conflating two independent deliverables.

### 4.4 Secondary native dependency (for completeness)

`jab-auth-abe` carries a **second, unrelated native integration**: `native/rabe-kem/` is a **Rust crate** (the *Rabe* KEM, one of the three KEMs — JPBC, Local, Rabe) bound via **JNA** (`// JNA for native Rabe KEM binding (Option A)`), not Panama. So the framework uses **two distinct native-interop mechanisms**: Panama FFM for the JABCode kernel, JNA for the Rust Rabe KEM. Neither is JNI; neither is swift-java.

### 4.5 Post-audit update (git logs, 2026-06-13)

Two corrections from the recent commit history, both material to the claims above:

1. **The vendored native kernel is cross-branch.** The provenance commits (`e94f56d`, `d486388`) are contained **only in `swift-java-poc`**, not `panama-poc` (`git branch --contains`). So the framework composes its JABCode dependency from **two branches**: the C decoder core from the *swift-java-poc* lineage (the hot branch — 172 commits/30d, where the nc2 anti-fabrication decoder work lives) plus the FFM Java wrapper JAR from the *panama-poc* lineage (the slow wrapper/benchmark branch — ~9 commits/30d). The framework's own code calls it the *"swift-lineage libjabcode.so"*. §1's "panama" framing understates this — the *bindings* are panama, the *kernel* is swift-java-poc.

2. **COLOR_256 is resolved on the server path (today).** Framework commit `2bff4bd` (2026-06-13) changes the in-code status from *"COLOR_256 (Nc=7): library encoder rejects; fix scheduled for WS-3"* to *"COLOR_4..COLOR_256 (Nc=1..7): encode/decode fully working on the swift-lineage libjabcode.so"*, and adds `PanamaJabCodeServiceIntegrationTest.testColor256EncodeDecodeRoundTrip` (end-to-end encode→decode). The diff reframes the old "malloc bug": *"encode-256 was never a capability gap — the native encoder always produced 256-colour symbols and the swift-lineage decoder reads them back."* panama-poc corroborates the same day (`52ec88f` "measured 4-256 reality", `1a698cb` 256 encode benchmark). **Net:** the Panama/server path now spans the full 8 modes (nc0-nc7); the **mobile bridge's `CROSS_PLATFORM.md` "256 ❌ malloc" is stale** and should be reconciled (it derives from the same swift-java-poc lineage, so the doc — not the code — is the lag).

---

## 5. Ecosystem-Report Claims vs Repo Reality

Source: `2026-05-20.md` §"5. Swift-Java Interoperability" and the Branch Comparison Matrix (lines ~619-660), plus 2026-06-01 citations.

| # | Report claim | Repo reality | Verdict |
|---|---|---|---|
| 1 | Swift-Java is **"✅ Integrated (Apple Official Tools)"** | No `swiftlang/swift-java`, `JavaKit`, or jextract-for-Swift anywhere; hand-rolled C bridge + native interop | ❌ **Overstated / inaccurate** |
| 2 | **"SwiftJava macros: Simplified JNI authoring"** | JNI is hand-written in `jabcode_jni.c`; no macros | ❌ Inaccurate |
| 3 | **"Bidirectional bindings: `swift-java jextract` Swift→Java and Java→Swift"** | Absent. The repo's own `research/swift-java-poc/overview.md` lists this as **"Experimental"** | ❌ Overstated (experimental → reported as integrated) |
| 4 | **"Swift benchmarks: `package-benchmark` (ordo-one)"** | Not in `Package.swift` or anywhere | ❌ Inaccurate |
| 5 | Recommendation: **tag `swift-java-poc@17b06a5` as `v1.0-mobile-ready`** (due 2026-05-27) | Only tag in repo is `v1.0.0-phase1`; **no `v1.0-mobile-ready`**; HEAD moved to `39de8df` | ❌ **Not done** — yet 2026-06-01 report cites "swift-java-poc v1.0-mobile-ready" as fact |
| 6 | swift-java-poc **"17 commits ahead"** of panama-poc | Now **310 ahead / 55 behind** | ⚠️ Stale (true on 2026-05-20) |
| 7 | **"Java FFM bindings ✅ Inherited"** on swift-java-poc | `panama-wrapper/` present on both branches | ✅ Confirmed |
| 8 | **256-color malloc bug — avoid** | **Path-dependent (updated 2026-06-13):** `CROSS_PLATFORM.md` (mobile bridge) still marks 256 ❌ malloc, but the **Panama/server path now does full COLOR_256 encode+decode** — framework commit `2bff4bd` flips the status and adds `testColor256EncodeDecodeRoundTrip`; panama-poc `52ec88f`/`1a698cb` (today) reconcile "measured 4-256 reality" | ⚠️ **Was confirmed; now stale on the server path** — 256 works via Panama, mobile-bridge doc lagging |
| 9 | **iOS = Swift C interop; Android = JNI** | Exactly matches the bridge architecture | ✅ Confirmed |
| 10 | **LAB ΔE2000 color calibration** | `color_calibration.c` compiled into the bridge | ✅ Confirmed |
| 11 | **FFM mode "JDK 25+"** | Benchmarked on JDK 23.0.1; `pom` target 21 | ⚠️ Roughly right (FFM stable since JDK 22; 25 is conservative) |
| 12 | **"Mobile bridge API production-ready (iOS + Android)"** | Real bridge + tests, but untagged, encode only recently unblocked, panama `decode(byte[])` still stubbed | ⚠️ Partially confirmed — mature PoC, "production-ready" overstated |
| 13 | Color modes: panama **6** / swift-java **7** | `CROSS_PLATFORM` "6/7 (4-128)"; ecosystem INDEX "7/8 (2-128)"; framework provenance "8/8 (nc0-nc7)" | ⚠️ **Three different counts** across docs |
| 14 | Framework is "mobile-ready" / production via swift-java | Framework consumes **Panama only**; swift-java bridge absent from its build (§4.3) | ❌ Conflates two separate deliverables |
| 15 | (implicit) framework binds JABCode natively | Confirmed: vendored `jabcode-panama` JAR + provenance-pinned `libjabcode.so` via FFM, with `validateNativeLib` SHA-256 gate (§4.2) | ✅ Confirmed — and notably rigorous |
| 16 | FFM "JDK 25+" | Framework + `LIBJABCODE.md` converge on **JDK 23 runtime, jextract 25 for bindings** | ✅ Resolves the JDK ambiguity |

---

## 6. Why the Drift Happened

The single root cause for claims 1-4 is **name collision**: the branch `swift-java-poc` shares a name with Apple's `swiftlang/swift-java` project. The 2026-05-20 validation session appears to have described what Apple's `swift-java` *would* provide (FFM+JNI dual mode, jextract bidirectional bindings, `SwiftJava` macros, ordo-one benchmarks) rather than what the branch *contains* (a hand-rolled C bridge). The repo's own research notes are accurate — `overview.md` explicitly files Apple swift-java under **"Experimental"** — so the drift was introduced in the *report*, not the codebase. Claim 5 (the tag) is an ordinary unfulfilled-action-item-became-fact error, the same failure mode flagged in the OpenMCS project-name-collision memory. Claim 14 (the framework being "mobile-ready" via swift-java) is the same conflation viewed from the consumer side: the framework ships server-side Panama, the mobile bridge ships separately, and the report blurs the two.

---

## 7. Recommended Corrections

1. **Amend the ecosystem record.** In `2026-05-20.md` §5, relabel Swift-Java from "✅ Integrated (Apple Official Tools)" to *"Cross-platform C bridge (Swift C interop + hand-written JNI); Apple `swift-java` evaluated, status Experimental."* Drop the `SwiftJava macros`, `jextract` bidirectional, and ordo-one `package-benchmark` claims, or move them to a clearly-labelled "future option" line. (Owner: Research.)
2. **Resolve the `v1.0-mobile-ready` fiction.** Either create the tag on the intended commit or strike "v1.0-mobile-ready" from the 2026-06-01 report and any opportunity docs that lean on it as evidence of production-readiness. (Owner: Engineering.)
3. **Normalize the color-mode count and JDK-version messaging.** There are now **three** color-mode counts in circulation (`CROSS_PLATFORM` 6/7, ecosystem INDEX 7/8, framework provenance 8/8 nc0-nc7) — reconcile to one canonical statement, noting that the **server-side Panama path** asserts 8/8 (nc0-nc7) while the **mobile bridge** still lists 256 as malloc-blocked, so the two paths may genuinely differ. Adopt the framework's resolved JDK story (**JDK 23 runtime, jextract 25 for bindings**) across `pom.xml`, `README.md`, `jextract.sh`, and the ecosystem INDEX. (Owner: Engineering/Research.)
4. **Separate "framework" from "mobile" in opportunity docs.** Where reports cite mobile readiness as evidence for the framework (or vice-versa), split them: the `jab-auth-framework` is server-side Panama/FFM; the iOS/Android bridge is an independent track. (Owner: Research.)

None of these change the engineering posture — producer layers and the consuming framework are all genuine, and the framework's vendoring + provenance gate is notably rigorous. They tighten the *reporting* so downstream opportunity sizing (the iOS enterprise pilot, the mobile-decoder moat, the server-side cloud story) rests on what each artifact actually ships.

---

## 8. Evidence Index

- **Producer — Panama**: `panama-wrapper/pom.xml`, `jextract.sh`, `README.md`, `PHASE9_COMPLETE.md`, `baseline-benchmarks/2026-05-29-{encode,decode}-baseline.json`
- **Producer — Swift-Java bridge**: `swift-java-wrapper/CROSS_PLATFORM.md`, `PHASE1-BLOCKER.md`, `ios/Package.swift`, `android/library/build.gradle.kts`, `src/c/mobile_bridge.c`, `include/mobile_bridge.h`; `memory-bank/research/swift-java-poc/overview.md` (labels Apple swift-java "Experimental")
- **Consumer — framework** (`business-plans/JABCodeCOA-crypto`): root `build.gradle`, `settings.gradle`, `jab-auth-jabcode/build.gradle` (`validateNativeLib`), `jab-auth-jabcode/src/main/resources/libjabcode.so.provenance`, `libs/jabcode-panama-1.0.0-SNAPSHOT.jar`, `LIBJABCODE.md`, `jab-auth-abe/build.gradle` (JNA Rabe KEM), `native/rabe-kem/Cargo.toml`
- `git tag` (only `v1.0.0-phase1`); `git rev-list --left-right --count panama-poc...swift-java-poc` → `55  310`; framework `git remote` → GitLab `business-ops/jab-auth-framework` (branch `main`)
- Ecosystem: `business-plans/.../ecosystem-research/daily-reports/2026-05-20.md` (§5 + matrix), `2026-06-01.md` (v1.0-mobile-ready citations)

---

*Last Updated: 2026-06-13 — read-only audit, no source modified.*
