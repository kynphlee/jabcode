# JABCode Full-Spectrum Documentation
**A Deep Dive Technical Narrative**

Version: 2.0  
Last Updated: January 2026  
Coverage: Color Modes 4-128, Panama FFM Wrapper, Critical Bug Fixes

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

---

## 🎯 Current Status (January 2026)

### ✅ What Works

| Feature | Status | Test Coverage | Notes |
|---------|--------|---------------|-------|
| 4-color encoding/decoding | ✅ Stable | 100% | Baseline mode |
| 8-color encoding/decoding | ✅ Stable | 100% | Standard mode |
| 16-color encoding/decoding | ✅ Stable | 100% | Enhanced palette |
| 32-color encoding/decoding | ✅ Stable | 100% | Extended range |
| 64-color encoding/decoding | ✅ **Fixed** | 100% | Adaptive palette, **mask metadata fix** |
| 128-color encoding/decoding | ✅ **Fixed** | 100% | Interpolation, **mask metadata fix** |
| Single-symbol codes | ✅ Stable | 100% | Fully functional |
| Sample generation | ✅ New | 100% | Self-describing samples |
| Panama FFM bindings | ✅ Stable | 75% instruction | Java 21+ Foreign Function & Memory API |

### ⚠️ Known Limitations

| Issue | Severity | Workaround | ETA |
|-------|----------|------------|-----|
| 256-color malloc crash | High | Skip mode | Investigating |
| Cascaded multi-symbol | Medium | API limitation | Q1 2026 |
| Symbol version config | Low | Not exposed in API | Q1 2026 |

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
