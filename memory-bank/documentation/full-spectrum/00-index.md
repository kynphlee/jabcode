# JABCode Full-Spectrum Documentation
**A Deep Dive Technical Narrative**

Version: 3.0  
Last Updated: June 2026  
Coverage: Color Modes 0–256 (Nc 0–7), Panama FFM Wrapper, Round-Trip Reality Check

---

## 📚 Documentation Structure

This documentation provides both **user-facing** and **technical deep-dive** perspectives on the JABCode Panama wrapper implementation, recent critical fixes, and current capabilities.

### User Guides 🎨

Light, accessible guides for developers integrating JABCode:

- **[01-getting-started.md](01-getting-started.md)** - Quick start guide for encoding and decoding
- **[02-sample-gallery.md](02-sample-gallery.md)** - Visual tour of all supported color modes
- **[03-choosing-color-mode.md](03-choosing-color-mode.md)** - Which mode for your use case?

### Technical Chronicles ⚙️

Deep technical narratives for engineers working on JABCode internals:

- **[04-mask-metadata-saga.md](04-mask-metadata-saga.md)** - The hunt for the 64-color decoder bug
- **[05-encoder-memory-architecture.md](05-encoder-memory-architecture.md)** - Palette allocation and the 256-color mystery
- **[06-api-design-evolution.md](06-api-design-evolution.md)** - Config API and cascaded encoding limitations
- **[07-test-coverage-journey.md](07-test-coverage-journey.md)** - Achieving 75% instruction coverage across all modes

### Reference Material 📖

- **[08-color-mode-reference.md](08-color-mode-reference.md)** - Complete specifications for all 7 modes
- **[09-troubleshooting-guide.md](09-troubleshooting-guide.md)** - Common issues and solutions
- **[10-future-enhancements.md](10-future-enhancements.md)** - Roadmap and planned improvements
- **[11-iso-spec-conformance.md](11-iso-spec-conformance.md)** - Where we meet — and diverge from — ISO/IEC 23634:2022

---

## 🎯 Current Status (June 2026)

> 📌 **A word on "works" — read this first.** Earlier editions reported every mode
> from 4 to 128 colours as ✅ 100%. That was true of the *unit tests* in December
> 2025 — and it is still how those tests report. But a JAB Code lives or dies on a
> **round-trip**, and a green unit test is not the same as a barcode that survives
> `encode → PNG → decode`, let alone one a camera can read. Two more recent
> measurements — the 2026-05-29 JMH baseline and a June 2026 scan of the
> `reference-images/` corpus — tell a humbler story. This edition reports what the
> *round-trip* does, not what the test runner says. 🧭

### The three decode realities

The same symbol can pass on one path and fail on another. Always ask *which path*:

| Path | What it means | Where it's measured |
|------|---------------|---------------------|
| 🧪 **Unit test** | In-suite assertions, short fixed payloads | `panama-wrapper` tests |
| 🖼️ **Synthetic PNG round-trip** | `encodeToPNG` → file → `decodeFromFile` | JMH `DecodingBenchmark`, `baseline-benchmarks/` |
| 📷 **Camera / screen scan** | A real lens reading a printed or on-screen symbol | Android diagnostic app |

### ✅ What works — by colour mode and by path

| Nc | Colours | 🧪 Unit | 🖼️ PNG round-trip | 📷 Camera | Notes |
|----|---------|:------:|:-----------------:|:--------:|-------|
| 0 | 2 (mono) | ✅ | ✅ | ❌ 0% | Accepted since `bb91db7`; screen-scan parked at metadata stage |
| 1 | 4 | ✅ | ✅ | ✅ 93% | The one universally-reliable mode |
| 2 | 8 | ✅ | ✅ | ❌ 0% | PNG fine; camera collapses at Part-I metadata |
| 3 | 16 | ✅ | ❌ LDPC | ✅ 35–67% | 🧪 passes but 🖼️ **fails** — the contradiction this edition exists to fix |
| 4 | 32 | ✅ | ❌ LDPC | ✅ 35–67% | as above |
| 5 | 64 | ✅ | ❌ LDPC | ✅ 35–67% | "Fixed" via mask-metadata (ch04), yet fails PNG round-trip today |
| 6 | 128 | ✅ | ❌ LDPC | ✅ 35–67% | as above |
| 7 | 256 | ⛔ | ❌ LDPC | ❌ 17% | Encoder *historically* `malloc`-crashes — but see ⚠️ below |

> 💡 **The "8-colour seam."** The cliff sits exactly between Nc=2 (8 colours) and
> Nc=3 (16). That is no coincidence: it is the same boundary where the decoder
> switches from fast squared-RGB colour matching (≤8) to perceptual-LAB plus
> interpolated palettes (>8). Every >8-colour bug in this handbook lives on the far
> side of that seam — you will meet it again in ch04 and ch08.

> ⚠️ **The 256-colour story is no longer "always crashes."** A `jabcode_256.png`
> sits in `reference-images/` (so *something* encoded it), and a recent server-side
> sweep encoded 256 on the current `.so`. Treat the `malloc` crash as
> *intermittent / build-dependent* and **re-verify on your build**. See
> [05-encoder-memory-architecture.md](05-encoder-memory-architecture.md).

### Beyond colour modes

| Capability | Status | Notes |
|------------|--------|-------|
| Single-symbol codes | ✅ Stable | Fully functional |
| Sample generation | ✅ Stable | Self-describing samples |
| Panama FFM bindings | ✅ Stable | Java 21+ FFM; ~75% instruction coverage |
| `JABCodeEncoder.VERBOSE` gate | ✅ New (`23a6570`) | Silences per-encode stderr in tight loops |

### ⚠️ Known limitations

| Issue | Severity | Reality | Tracking |
|-------|----------|---------|----------|
| **>8-colour PNG round-trip fails (LDPC)** | High | Nc≥3 don't survive `encode → PNG → decode` | Register entry pending |
| 256-colour encoder `malloc` | Med–High | Intermittent; re-verify per build | ch05 |
| Camera scan: Nc 0/2/7 fail | High (product) | Metadata-stage — *distinct* from the LDPC wall | Screen-scan track |
| Decoder per-module file logging | Med (perf) | 81 un-gated `fprintf` to `/tmp/…` every decode | Gating task queued |
| Cascaded multi-symbol | Medium | API limitation | — |

### 🔧 Recent Critical Fixes

1. **Mask Metadata Synchronization** (Dec 2025)
   - Fixed encoder writing wrong `mask_type` to metadata for 64/128-color modes
   - Root cause: Safety check prevented metadata updates for `color_number > 8`
   - Impact: LDPC decoding failures eliminated across all working modes
   - Details: [04-mask-metadata-saga.md](04-mask-metadata-saga.md)

2. **Encoder Palette Buffer Overflow** (Dec 2025)
   - Fixed palette allocation from 1 palette to 4 palettes (COLOR_PALETTE_NUMBER)
   - Prevented buffer overflow in higher color modes
   - Details: [05-encoder-memory-architecture.md](05-encoder-memory-architecture.md)

3. **Test Coverage Achievement** (Dec 2025)
   - Reached 75% instruction coverage with 170 passing tests
   - All color modes 4-128 validated with round-trip encoding
   - Details: [07-test-coverage-journey.md](07-test-coverage-journey.md)

---

## 🚀 Quick Navigation

**New to JABCode?** Start with [01-getting-started.md](01-getting-started.md)

**Want to see it in action?** Check out [02-sample-gallery.md](02-sample-gallery.md)

**Debugging encoding issues?** Jump to [09-troubleshooting-guide.md](09-troubleshooting-guide.md)

**Working on internals?** Read [04-mask-metadata-saga.md](04-mask-metadata-saga.md) for a masterclass in C/Java debugging

**Planning integration?** Review [03-choosing-color-mode.md](03-choosing-color-mode.md) for mode selection guidance

---

## 📊 Test Results Summary

```
Total Tests: 170
Passing: 170 (100%)
Instruction Coverage: 75%
Branch Coverage: 68%
Line Coverage: 79%

Color Mode Breakdown:
├─ 4-color:   11/11 tests ✅
├─ 8-color:   13/13 tests ✅
├─ 16-color:  12/12 tests ✅
├─ 32-color:  12/12 tests ✅
├─ 64-color:  11/11 tests ✅ (FIXED)
├─ 128-color: 13/13 tests ✅ (FIXED)
└─ 256-color: EXCLUDED (malloc corruption)
```

---

## 🎓 Learning Path

1. **Beginner**: Read user guides (01-03) → Try sample code → Explore sample gallery
2. **Intermediate**: Dive into color mode reference (08) → Study API design (06)
3. **Advanced**: Read technical sagas (04-05) → Contribute to enhancements (10)

---

**Philosophy**: JABCode is complex. These docs make it accessible while respecting that complexity. Enjoy the journey! 🎨🔍
