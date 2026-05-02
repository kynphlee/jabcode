# Phase 5: :ui-components Module - Reusable UI

**Duration:** 2 weeks (10 working days)  
**Dependencies:** :core, :jabcode-sdk  
**Status:** ⬜ Not Started

---

## Overview

Implements reusable Compose UI components from `scanner-interface.html` and `diagnostic-dashboard.html` prototypes.

**Coverage Target:** 70%+ (40 tests: 25 screenshot + 15 interaction)

---

## Week 1: Design System & Scanner Components

**Day 1-2: Design System**
- `JABAuthTheme` with Material 3
- Color tokens, typography, spacing
- 5 screenshot tests

**Day 3-4: Scanner Components**
- `ScannerHeader` (1 test)
- `ScanTargetOverlay` (1 test)
- `QualityIndicators` (2 tests)
- `ScanStatusOverlay` (1 test)
- `ResultPanel` (3 tests)
- `DetailSection` (1 test)

**Day 5: Week 1 Completion**
- Run tests → 14 tests pass
- Coverage ≥ 70%

---

## Week 2: Dashboard Components & Testing

**Day 6-7: Dashboard Components**
- `DashboardHeader` (1 test)
- `PerformanceGraph` (1 test)
- `ColorModeCard` (2 tests)
- `LiveFeed` (2 tests)
- `AlertCard` (1 test)

**Day 8-9: Interaction Tests**
- Navigation tests (5 tests)
- State management tests (5 tests)
- Animation tests (5 tests)

**Day 10: Phase Completion**
```bash
./gradlew :ui-components:clean test jacocoTestReport
# Screenshot tests: 25 pass
# Interaction tests: 15 pass
# Coverage: 70%+
```

---

## Reference Prototypes

All components must match:
- `@/swift-java-wrapper/android/ui-prototypes/scanner-interface.html`
- `@/swift-java-wrapper/android/ui-prototypes/diagnostic-dashboard.html`
- `@/swift-java-wrapper/android/ui-prototypes/SCANNER_COMPONENTS.md`

---

**Last Updated:** 2026-05-02  
**Next:** Phase 6 (:diagnostic-app)
