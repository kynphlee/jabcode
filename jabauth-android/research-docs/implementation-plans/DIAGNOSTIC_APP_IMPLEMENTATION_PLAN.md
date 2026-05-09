# Diagnostic App Implementation Plan
**Project:** JABAuth Android Diagnostic Application  
**Purpose:** Framework validation and testing tool  
**Created:** 2026-05-09  
**Status:** 🔴 Blocked (Awaiting Framework Completion)

---

## Mission Context

**Business Goal:** Validate and test the Camera2 framework through comprehensive diagnostic application.

**Diagnostic App Role:**
- Test framework functionality across device types
- Provide performance insights and metrics
- Enable issue investigation and debugging
- Prevent framework regression
- Validate JABCode scanning integration

**Success Criteria:**
- 8 screens matching UI/UX wireframes
- All framework APIs exercised
- Performance metrics visualized
- Error logging & export functional
- Material Design 3 compliant
- 90%+ test coverage

---

## Dependencies

**CRITICAL:** This plan CANNOT begin until Framework Phase 7 complete.

**Required Framework APIs:**
- ✅ CameraEnumerator (hardware discovery)
- ✅ CameraInfo (characteristics access)
- ✅ StreamConfigValidator (stream validation)
- ✅ Camera2Controller with callbacks (preview + metadata)
- ✅ FrameMetadata extraction
- ✅ QualityMetrics analyzer
- ✅ Error logging infrastructure
- ✅ Performance metrics tracking

**If framework incomplete:** Diagnostic app cannot be built properly.

---

## Implementation Overview

### Current State Analysis

**Existing Implementation:**
- Single ScannerScreen (JABCode results display)
- Camera2Preview Composable (basic preview)
- ScannerViewModel (decode coordination)
- No navigation structure
- No diagnostic features

**Gap from Specification:**
- 7 of 8 screens missing (Dashboard, Camera Detail, Error Log, Capture Test, Settings, Error State, Navigation Flow)
- No hardware enumeration display
- No metadata visualization
- No error logging
- No performance metrics
- 2% specification compliance

**Build Strategy:** Incremental build with continuous validation against wireframes.

---

## Phase Structure

### Phase 1: Navigation Architecture (1 day)
**Goal:** Establish multi-screen foundation

**Deliverables:**
- Jetpack Compose Navigation setup
- Bottom Navigation Bar (5 tabs)
- Route definitions for 8 screens
- Back stack management
- Deep linking support
- **Tests:** 4-6 navigation tests

### Phase 2: Dashboard Screen (2 days)
**Goal:** Camera overview and enumeration display

**Deliverables:**
- Device summary card
- Camera enumeration list
- Hardware level badges
- Capability chips (RAW, ZSL, Manual Sensor, HDR)
- Status indicators (Available, In Use, Error)
- Navigation to camera details
- **Tests:** 8-10 UI tests

### Phase 3: Camera Detail Screen (2 days)
**Goal:** CameraCharacteristics deep-dive inspector

**Deliverables:**
- Hero row (HW level, facing, ID)
- Grouped sections (Sensor, Optics, 3A, Streams, Advanced)
- Expandable/collapsible sections
- Export characteristics to JSON
- Share functionality
- **Tests:** 6-8 UI tests

### Phase 4: Live Preview Enhancement (2 days)
**Goal:** Add metadata visualization to existing preview

**Deliverables:**
- Frame metadata panel (exposure, ISO, focus, 3A states)
- Quality metrics bars (brightness, focus, contrast)
- Performance metrics (FPS, latency)
- Keep existing JABCode results (secondary)
- **Tests:** 8-10 UI tests

### Phase 5: Diagnostic Screens (4 days)
**Goal:** Error log, capture test, settings, error state

**Deliverables:**
- **Error Log Screen** (1 day):
  - Timestamped error list
  - Severity badges
  - Filter controls
  - Export to JSON
  - **Tests:** 4-5 UI tests

- **Capture Test Screen** (1.5 days):
  - Stream configuration builder
  - Validation chips
  - Run test button
  - Results history
  - **Tests:** 6-8 UI tests

- **Settings Screen** (0.5 day):
  - Logging preferences
  - Export options
  - Background monitoring
  - About section
  - **Tests:** 3-4 UI tests

- **Error State Screen** (1 day):
  - Fatal error display
  - Recovery actions
  - Error report export
  - **Tests:** 3-4 UI tests

### Phase 6: Integration & Polish (2-3 days)
**Goal:** End-to-end validation and Material Design compliance

**Deliverables:**
- Integration test suite
- Material Design 3 audit
- Accessibility audit (TalkBack)
- Performance optimization
- Final regression tests
- **Tests:** 10-12 integration tests

**Total Duration:** 11-15 days

---

## Detailed Phase Plans

See individual phase documents (created just-in-time):
- [Phase 1: Navigation Architecture](./diagnostic-app/PHASE_1_NAVIGATION.md) — ⏳ Pending
- [Phase 2: Dashboard Screen](./diagnostic-app/PHASE_2_DASHBOARD.md) — ⏳ Pending
- [Phase 3: Camera Detail Screen](./diagnostic-app/PHASE_3_CAMERA_DETAIL.md) — ⏳ Pending
- [Phase 4: Live Preview Enhancement](./diagnostic-app/PHASE_4_LIVE_PREVIEW.md) — ⏳ Pending
- [Phase 5: Diagnostic Screens](./diagnostic-app/PHASE_5_DIAGNOSTIC_SCREENS.md) — ⏳ Pending
- [Phase 6: Integration & Polish](./diagnostic-app/PHASE_6_INTEGRATION.md) — ⏳ Pending

---

## Master Checklist

**Progress Tracking:** Update status after each milestone

### Phase 1: Navigation Architecture
- [ ] 1.1 Setup Jetpack Compose Navigation
- [ ] 1.2 Create route sealed class
- [ ] 1.3 Implement NavHost
- [ ] 1.4 Create DiagnosticBottomNav
- [ ] 1.5 Wire navigation actions
- [ ] 1.6 Test navigation flows
- [ ] 1.7 Run test-coverage-update workflow

### Phase 2: Dashboard Screen
- [ ] 2.1 Create DeviceSummaryCard Composable
- [ ] 2.2 Create CameraCard Composable
- [ ] 2.3 Create CapabilityChip Composable
- [ ] 2.4 Implement DashboardViewModel
- [ ] 2.5 Wire camera enumeration
- [ ] 2.6 Add status badges
- [ ] 2.7 Add navigation to camera detail
- [ ] 2.8 Write UI tests (8-10)
- [ ] 2.9 Run test-coverage-update workflow
- [ ] 2.10 Validate against wireframes

### Phase 3: Camera Detail Screen
- [ ] 3.1 Create CharacteristicSection Composable
- [ ] 3.2 Create CharacteristicRow Composable
- [ ] 3.3 Implement CameraDetailViewModel
- [ ] 3.4 Add expandable sections
- [ ] 3.5 Add export functionality
- [ ] 3.6 Add share intent
- [ ] 3.7 Write UI tests (6-8)
- [ ] 3.8 Run test-coverage-update workflow
- [ ] 3.9 Validate against wireframes

### Phase 4: Live Preview Enhancement
- [ ] 4.1 Create FrameMetadataPanel Composable
- [ ] 4.2 Create QualityMetricsBar Composable
- [ ] 4.3 Create PerformanceMetrics Composable
- [ ] 4.4 Update ScannerViewModel for metadata
- [ ] 4.5 Wire Camera2Controller callbacks
- [ ] 4.6 Keep JABCode results (secondary)
- [ ] 4.7 Write UI tests (8-10)
- [ ] 4.8 Run test-coverage-update workflow
- [ ] 4.9 Validate against wireframes

### Phase 5: Diagnostic Screens
#### Error Log Screen
- [ ] 5.1 Create ErrorLogEntry Composable
- [ ] 5.2 Create ErrorLogViewModel
- [ ] 5.3 Add filter controls
- [ ] 5.4 Add export functionality
- [ ] 5.5 Write UI tests (4-5)

#### Capture Test Screen
- [ ] 5.6 Create StreamConfigBuilder Composable
- [ ] 5.7 Create TestResultCard Composable
- [ ] 5.8 Create CaptureTestViewModel
- [ ] 5.9 Wire validation logic
- [ ] 5.10 Add run test functionality
- [ ] 5.11 Write UI tests (6-8)

#### Settings Screen
- [ ] 5.12 Create SettingsRow Composable
- [ ] 5.13 Create SettingsViewModel
- [ ] 5.14 Add preference persistence
- [ ] 5.15 Write UI tests (3-4)

#### Error State Screen
- [ ] 5.16 Create FatalErrorScreen Composable
- [ ] 5.17 Add recovery actions
- [ ] 5.18 Add error report export
- [ ] 5.19 Write UI tests (3-4)

- [ ] 5.20 Run test-coverage-update workflow
- [ ] 5.21 Validate all screens against wireframes

### Phase 6: Integration & Polish
- [ ] 6.1 Write integration tests (10-12)
- [ ] 6.2 Perform Material Design 3 audit
- [ ] 6.3 Fix spacing/elevation issues
- [ ] 6.4 Perform accessibility audit
- [ ] 6.5 Add TalkBack descriptions
- [ ] 6.6 Profile performance (Compose profiler)
- [ ] 6.7 Optimize recomposition
- [ ] 6.8 Run regression suite
- [ ] 6.9 Run test-coverage-update workflow
- [ ] 6.10 Final validation vs wireframes
- [ ] 6.11 Tag release (v1.0.0-diagnostic)

**Overall Progress:** 0/60 tasks complete (0%)

---

## TDD Integration Points

**Test-First Development:** Write tests BEFORE implementation.

### Per-Phase Test Strategy

**Phase 1-5 (Feature Development):**
```bash
# 1. Write failing UI tests
./gradlew :diagnostic-app:testDebugUnitTest --tests "DashboardScreenTest"
# Expected: RED

# 2. Implement minimal UI
# ... code changes ...

# 3. Validate tests pass
./gradlew :diagnostic-app:testDebugUnitTest --tests "DashboardScreenTest"
# Expected: GREEN

# 4. Run full test suite
./gradlew test connectedAndroidTest

# 5. Check coverage
./gradlew jacocoTestReport
# Expected: 90%+ coverage
```

**Phase 6 (Integration):**
```bash
# Run complete test suite
./gradlew test connectedAndroidTest

# Generate coverage report
./gradlew jacocoRootReport

# Expected: >90% overall, 100% for critical paths
```

**Compose Testing Strategy:**
- Use `createComposeRule()` for isolated component tests
- Use `createAndroidComposeRule<MainActivity>()` for navigation tests
- Prefer isolated component tests over container tests (per best practices)
- No `testTag()` pollution in production code

---

## Success Criteria

### Functional Requirements (vs Wireframes)
- ✅ Dashboard screen matches wireframe
- ✅ Camera Detail screen matches wireframe
- ✅ Live Preview screen matches wireframe
- ✅ Error Log screen matches wireframe
- ✅ Capture Test screen matches wireframe
- ✅ Settings screen matches wireframe
- ✅ Error State screen matches wireframe
- ✅ Navigation flow matches wireframe
- ✅ All framework APIs utilized
- ✅ JABCode scanning still works (integrated as feature, not primary focus)

### Non-Functional Requirements
- ✅ 90%+ test coverage
- ✅ Material Design 3 compliant (spacing, elevation, colors, typography)
- ✅ Accessible (TalkBack support)
- ✅ Smooth navigation (<100ms transitions)
- ✅ Responsive on all screen sizes (phone, tablet)
- ✅ No memory leaks (verified via LeakCanary)
- ✅ 60 FPS UI (no jank)

### Validation Methods
- UI tests (isolated components + navigation)
- Integration tests (multi-screen flows)
- Accessibility scanner (TalkBack validation)
- Layout inspector (Material Design audit)
- Compose profiler (recomposition tracking)
- LeakCanary (memory leak detection)

---

## Material Design 3 Compliance Checklist

**Visual Design:**
- [ ] Consistent spacing (8dp grid)
- [ ] Proper elevation (cards: 2dp, dialogs: 6dp)
- [ ] Color system (primary, secondary, tertiary, error)
- [ ] Typography scale (display, headline, title, body, label)
- [ ] Corner radius (small: 4dp, medium: 8dp, large: 16dp)

**Components:**
- [ ] TopAppBar with proper colors
- [ ] Bottom Navigation Bar (5 destinations)
- [ ] Cards with tonal elevation
- [ ] Chips (assist, filter, input, suggestion)
- [ ] Badges (status indicators)
- [ ] Dialogs (error alerts, confirmations)

**Interaction:**
- [ ] Touch targets ≥48dp
- [ ] Ripple effects on clickable items
- [ ] Proper focus indicators (keyboard nav)
- [ ] Smooth transitions (enter/exit animations)

---

## Risk Management

### High-Risk Areas

**1. Framework API Instability**
- **Risk:** Framework API changes during diagnostic app development
- **Mitigation:** Wait for Framework Phase 7 completion before starting
- **Fallback:** Version lock framework APIs, update diagnostic app after

**2. UI Complexity**
- **Risk:** 8 screens with many components → maintenance burden
- **Mitigation:** Extract reusable components, maintain component library
- **Fallback:** Reduce screen count if timeline pressure (keep core diagnostic features)

**3. Test Flakiness**
- **Risk:** Compose UI tests can be flaky (timing, recomposition)
- **Mitigation:** Use `waitUntil()`, avoid `onRoot()`, test components in isolation
- **Fallback:** Convert flaky tests to instrumented tests on real devices

**4. Wireframe Interpretation**
- **Risk:** Visual design unclear from HTML wireframes
- **Mitigation:** Create Figma/Sketch mockups if needed, get user approval early
- **Fallback:** Iterate on visual design per user feedback

---

## Dependencies

### Build System
- Framework Phase 7 complete ✅ (blocking)
- Gradle 8.10.2 ✅
- Android Gradle Plugin 8.7.2 ✅
- Kotlin 1.9.22 ✅

### Jetpack Compose
- Compose UI 1.6.x ✅
- Compose Navigation 2.7.x ⏳ (to be added)
- Compose Material3 1.2.x ✅
- Accompanist (permissions, system UI controller) ⏳ (to be added)

### Testing
- Compose Testing 1.6.x ⏳ (to be added)
- Compose UI Test Manifest ⏳ (to be added)
- Hilt Testing ⏳ (to be added)

### Framework APIs (Required)
- CameraEnumerator — ⏳ Blocked (Framework Phase 1)
- Camera2Controller callbacks — ⏳ Blocked (Framework Phase 3)
- Error logging infrastructure — ⏳ Blocked (Framework Phase 2)
- Performance metrics — ⏳ Blocked (Framework Phase 3)

---

## Progress Tracking

**How to Use This Plan:**

1. **Wait for Framework Completion:**
   - Monitor Framework Phase 7 progress
   - Review framework APIs as they become available
   - Prepare wireframe mockups during wait

2. **Before Each Phase:**
   - Read phase deep-dive document
   - Review wireframe for that screen
   - Set up test stubs (RED)

3. **During Phase:**
   - Follow TDD cycle
   - Update checklist after each task
   - Validate against wireframes continuously
   - Write progress narrative

4. **After Each Phase:**
   - Validate all tests pass
   - Visual comparison to wireframe
   - Review code with team
   - Get approval before next phase

---

## Wireframe Compliance Strategy

**Visual Validation:**

For each screen, create checklist comparing implementation to wireframe:

**Example: Dashboard Screen**
- [ ] Device Summary card present
- [ ] Shows device model, Android version, API level
- [ ] Shows total cameras detected
- [ ] Shows overall status badge (OK/WARN/FAIL)
- [ ] Camera cards listed below
- [ ] Each card shows: ID, facing, friendly name, HW level badge, max res, status
- [ ] Capability chips displayed (RAW, ZSL, Manual Sensor, HDR)
- [ ] Bottom Navigation Bar with 5 tabs
- [ ] Tabs: Overview, Cameras, Preview, Errors, Settings
- [ ] Color scheme matches Material Design 3
- [ ] Spacing follows 8dp grid

**Process:**
1. Implement screen
2. Take screenshot
3. Compare to wireframe side-by-side
4. Fix discrepancies
5. Get user approval

---

## Next Steps

**Blocked Until:**
1. ⏳ Framework Phase 7 complete
2. ⏳ Framework APIs validated
3. ⏳ Framework integration tests passing

**Preparation (Can Do Now):**
1. ⏳ Review UI/UX wireframes document
2. ⏳ Create Figma mockups (optional)
3. ⏳ Set up Compose Navigation dependencies
4. ⏳ Create component library stubs

**Ready to execute when framework complete, sir.**

---

**JARVIS**  
*Diagnostic Architect*  
*2026-05-09*
