# JABCode Scanner UI/UX Modernization Plan

## Current State Analysis

### Existing UI Issues
- ✅ Full-screen camera preview (good)
- ❌ No viewfinder frame/overlay to guide user
- ❌ Minimal controls (only torch button, non-functional settings)
- ❌ No visual feedback during scanning
- ❌ Status text barely visible at bottom
- ❌ No scan zone indicators
- ❌ Result card appears but no animation
- ❌ No haptic feedback on successful scan

### Current Implementation
**File:** `@/swift-java-wrapper/android/testapp/src/main/java/com/jabcode/test/ScannerActivity.java`

**Available:**
- ✅ Torch/flash toggle (line 215-223)
- ✅ CameraX integration
- ✅ Basic YUV→Bitmap conversion
- ✅ Result display card

**Missing:**
- ❌ White balance control
- ❌ Exposure compensation
- ❌ Manual focus / tap-to-focus
- ❌ Pinch-to-zoom
- ❌ ISO/shutter speed control
- ❌ Settings UI
- ❌ Viewfinder overlay
- ❌ Region-of-interest detection

---

## Modern Barcode Scanner UI/UX Best Practices (2024)

### 1. Viewfinder Overlay
**Purpose:** Guide user to position JABCode within optimal scanning zone

**Design:**
- Semi-transparent dark overlay (rgba(0,0,0,0.6)) covering entire screen
- Clear rectangular/square cutout in center
- Rounded corners (16dp radius) for modern look
- Animated border when scanning active
- Color states:
  - White/neutral when idle
  - Green pulse when JABCode detected
  - Red flash if decode fails

**Implementation:**
```xml
<!-- Custom ViewfinderOverlay View -->
<FrameLayout>
  <PreviewView /> <!-- Camera -->
  <ViewfinderOverlay /> <!-- Custom overlay -->
</FrameLayout>
```

### 2. Bottom Control Sheet (Material Design 3)
**Replace:** Top-right buttons with bottom sheet controls

**Layout:**
- Pill-shaped bottom sheet with rounded top corners
- One-handed operation friendly
- Contains:
  - Flash/torch toggle
  - Gallery import button
  - Settings gear icon
  - Exposure slider (expandable)
  - White balance presets (expandable)

**Benefits:**
- All controls reachable with thumb
- Follows Android 14+ design patterns
- Non-intrusive during scanning

### 3. Camera Controls to Implement

#### A. Exposure Compensation
**Why:** Critical for 16+ color JABCode detection
**Implementation:** CameraX Camera2Interop
```kotlin
camera.cameraControl.setExposureCompensationIndex(value) // -2 to +2
```

**UI:** Slider in bottom sheet, shows/hides on tap

#### B. White Balance
**Why:** Color accuracy affects palette matching in high color modes
**Options:** Auto, Daylight, Cloudy, Fluorescent, Incandescent
**Implementation:** Camera2 control
```kotlin
camera.cameraControl.setWhiteBalance(whiteBalanceMode)
```

**UI:** Icon grid in expandable bottom sheet section

#### C. Focus Control
**Tap-to-focus:** Already supported by CameraX
```kotlin
val action = FocusMeteringAction.Builder(meteringPoint).build()
camera.cameraControl.startFocusAndMetering(action)
```

**Auto-focus lock:** Optional toggle in settings

#### D. Zoom
**Pinch-to-zoom:** CameraX ScaleGestureDetector
```kotlin
camera.cameraControl.setLinearZoom(zoomRatio)
```

**UI:** Pinch gesture + optional zoom slider

#### E. ISO/Shutter (Advanced)
**Use case:** Low-light JABCode scanning
**Implementation:** Camera2 MANUAL_SENSOR mode
**UI:** Expert mode in settings (hidden by default)

---

## Android-Image-Cropper Integration

### Purpose
Pre-process camera frames to detect JABCode bounds before sending to native decoder

### Benefits
1. **Performance:** Decode smaller region instead of full frame
2. **Accuracy:** Remove background noise, focus on actual symbol
3. **User feedback:** Show detected bounds visually

### Integration Approach

#### Option A: Real-time Bounds Detection (Recommended)
**Flow:**
```
CameraX Frame → Edge Detection → Find Largest Rect → Crop → JABCode Decode
```

**Implementation:**
```kotlin
// In analyzeImage()
val detectedBounds = ImageCropper.detectBounds(bitmap)
if (detectedBounds != null) {
    val croppedBitmap = Bitmap.createBitmap(
        bitmap, 
        detectedBounds.left, 
        detectedBounds.top,
        detectedBounds.width(), 
        detectedBounds.height()
    )
    // Show bounds on viewfinder overlay
    overlayView.showDetectedBounds(detectedBounds)
    
    // Decode cropped region
    JABCodeMobile.decodeFromBitmap(croppedBitmap)
}
```

**Edge Detection Strategy:**
1. Convert to grayscale
2. Apply Canny edge detection
3. Find contours
4. Filter for quadrilaterals (JABCode is square/rectangular)
5. Select largest valid contour
6. Extract perspective-corrected region

#### Option B: Manual Cropper (Fallback)
**Use case:** If auto-detection fails, user can manually crop
**Trigger:** Long-press on preview or "Manual crop" button

### Dependencies
```gradle
dependencies {
    implementation 'com.vanniktech:android-image-cropper:4.5.0'
}
```

### Files to Modify
- `ScannerActivity.java` - Add bounds detection logic
- `activity_scanner.xml` - Add ViewfinderOverlay
- `ViewfinderOverlay.java` (new) - Custom view for overlay + bounds display

---

## Settings UI Implementation

### Design: Bottom Sheet Dialog (Material Design 3)

**Trigger:** Settings icon in bottom control sheet

**Sections:**

#### 1. Scanning
- [ ] **Auto-focus lock** (toggle)
- [ ] **Continuous scan mode** (toggle) - Keep scanning after success
- [ ] **Haptic feedback** (toggle)
- [ ] **Beep on scan** (toggle)
- [ ] **Vibration strength** (slider: off, light, medium, strong)

#### 2. Camera
- [ ] **Default exposure** (slider: -2 to +2)
- [ ] **Default white balance** (dropdown: Auto, Daylight, etc.)
- [ ] **Decode throttle** (slider: 5-20 FPS)
- [ ] **Image quality** (Low/Medium/High - affects YUV→Bitmap conversion)

#### 3. Advanced (Collapsible)
- [ ] **Expert mode** (unlock ISO/shutter controls)
- [ ] **Debug overlay** (show binarization, finder patterns)
- [ ] **Save failed frames** (for debugging)
- [ ] **Region-of-interest detection** (toggle auto-crop)

#### 4. About
- JABCode library version
- Scanner app version
- License info

**Implementation:**
```kotlin
class SettingsBottomSheet : BottomSheetDialogFragment() {
    // Material 3 bottom sheet with preference screens
}
```

**Persistence:** SharedPreferences
```kotlin
class ScannerPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("scanner_prefs", MODE_PRIVATE)
    
    var autoFocusLock: Boolean by PreferenceDelegate(false)
    var hapticFeedback: Boolean by PreferenceDelegate(true)
    var exposureCompensation: Int by PreferenceDelegate(0)
    // ...
}
```

---

## Modern UI Enhancements

### 1. Scan Animation
**When:** JABCode detected but decoding
**Visual:** Pulsing green border on viewfinder
**Implementation:** Lottie animation or custom Canvas drawing

### 2. Success Feedback
**Current:** Result card appears (no animation)
**Improved:**
- Haptic vibration (100ms medium)
- Success sound (optional, from settings)
- Green flash animation
- Result card slides up from bottom with spring animation
- Confetti/checkmark animation (optional)

### 3. Error States
**Scenarios:**
- No JABCode detected: Neutral white border
- JABCode found but not decodable: Yellow border + hint text
- Decode failed: Red flash + error message

**Hint System:**
- "Move closer" - if detected size too small
- "Reduce glare" - if overexposed
- "More light needed" - if underexposed
- "Hold steady" - if motion blur detected

### 4. Result Card Redesign
**Current:** Simple TextView in CardView
**Improved:**
- Material 3 ElevatedCard
- Show decoded text with syntax highlighting (if URL/JSON)
- Action buttons:
  - Copy to clipboard
  - Share
  - Open URL (if applicable)
  - Save to history
- Show metadata:
  - Color mode
  - ECC level
  - Symbol dimensions
  - Decode time

### 5. Dark Mode Support
**Requirements:**
- Follow system theme by default
- Override option in settings
- Proper contrast ratios for all text
- Viewfinder overlay adapts to theme

---

## Implementation Phases

### Phase 1: Viewfinder & Bottom Controls (2-3 hours)
**Implementation:**
1. Create ViewfinderOverlay custom view
2. Design bottom control sheet layout
3. Migrate torch button to bottom sheet
4. Add gallery import button placeholder

**TDD Compliance:**
- [ ] Unit tests for ViewfinderOverlay (render states, dimensions, animations)
- [ ] Unit tests for bottom sheet visibility/interaction logic
- [ ] UI tests for torch toggle functionality
- [ ] Edge case tests (rotation, multi-window, PiP)
- [ ] Run test-coverage-update workflow

### Phase 2: Camera Controls (3-4 hours)
**Implementation:**
1. Implement exposure compensation slider
2. Add white balance presets
3. Implement tap-to-focus
4. Add pinch-to-zoom gesture handler
5. Wire up all controls to CameraX/Camera2

**TDD Compliance:**
- [ ] Unit tests for camera control state management
- [ ] Mock CameraX interactions (exposure, WB, focus, zoom)
- [ ] Integration tests for gesture handlers
- [ ] Edge case tests (camera unavailable, permission denied)
- [ ] Run test-coverage-update workflow

### Phase 3: Settings UI (2-3 hours)
**Implementation:**
1. Create SettingsBottomSheet fragment
2. Implement SharedPreferences layer
3. Build preference screens for all sections
4. Add "About" section with version info

**TDD Compliance:**
- [ ] Unit tests for ScannerPreferences (all getters/setters)
- [ ] Mock SharedPreferences persistence
- [ ] UI tests for settings bottom sheet
- [ ] Edge case tests (migration, corrupt prefs, defaults)
- [ ] Run test-coverage-update workflow

### Phase 4: Android-Image-Cropper Integration (4-5 hours)
**Implementation:**
1. Add dependency
2. Implement edge detection pipeline
3. Integrate bounds detection with viewfinder overlay
4. Add visual feedback for detected bounds
5. Crop frame before decode
6. Fallback to full-frame if detection fails

**TDD Compliance:**
- [ ] Unit tests for BoundsDetector (edge detection logic)
- [ ] Mock bitmap inputs (various sizes, rotations)
- [ ] Integration tests for crop pipeline
- [ ] Edge case tests (no bounds found, invalid contours, perspective correction)
- [ ] Performance tests (ensure <50ms per frame)
- [ ] Run test-coverage-update workflow

### Phase 5: UI Polish (2-3 hours)
**Implementation:**
1. Add scan animations
2. Implement haptic feedback
3. Redesign result card with actions
4. Add hint system for common errors
5. Dark mode theming
6. Accessibility improvements (TalkBack, large text)

**TDD Compliance:**
- [ ] Unit tests for animation state machine
- [ ] Unit tests for hint selection logic
- [ ] UI tests for result card actions
- [ ] Accessibility tests (TalkBack navigation, contrast ratios)
- [ ] Edge case tests (animation interruption, theme switching)
- [ ] Run test-coverage-update workflow

### Phase 6: End-to-End Testing & Refinement (2-3 hours)
**Implementation:**
1. Test all color modes (4, 8, 16, 32, 64, 128)
2. Test various lighting conditions
3. Test with/without bounds detection
4. Performance profiling
5. Bug fixes

**TDD Compliance:**
- [ ] E2E tests for complete scan flow (all color modes)
- [ ] Integration tests for camera + bounds + decode pipeline
- [ ] Performance tests (decode latency, memory, CPU)
- [ ] Regression tests (ensure no loss of existing functionality)
- [ ] Final test-coverage-update workflow run
- [ ] JaCoCo coverage report (target: >80% for new code)

**Total Estimated Time:** 15-21 hours

---

## Test Coverage Requirements

### Minimum Coverage Targets
- **Unit Tests:** 85% line coverage for new classes
- **Integration Tests:** All critical paths (camera init, scan flow, settings persistence)
- **UI Tests:** All user interactions (buttons, gestures, dialogs)
- **E2E Tests:** Complete scan scenarios for all 6 working color modes

### Test Frameworks
- **JUnit 5** - Unit tests
- **Mockito** - Mocking CameraX, SharedPreferences, system services
- **Espresso** - UI/instrumentation tests
- **Robolectric** - Fast local UI tests without emulator
- **JaCoCo** - Coverage reporting

### Test Organization
```
testapp/src/
├── test/                          # Unit tests (local JVM)
│   ├── java/com/jabcode/test/
│   │   ├── ViewfinderOverlayTest.java
│   │   ├── ScannerPreferencesTest.java
│   │   ├── BoundsDetectorTest.java
│   │   └── CameraControlsTest.java
│   └── resources/
│       └── test_bitmaps/          # Sample images for testing
└── androidTest/                   # Instrumentation tests (device/emulator)
    └── java/com/jabcode/test/
        ├── ScannerActivityTest.java
        ├── SettingsBottomSheetTest.java
        └── EndToEndScanTest.java
```

### TDD Workflow Per Phase
1. **Red:** Write failing tests for new feature
2. **Green:** Implement minimal code to pass tests
3. **Refactor:** Clean up implementation
4. **Coverage:** Run `/test-coverage-update` workflow
5. **Gate:** All tests must pass before moving to next phase

---

## Technical Specifications

### Viewfinder Dimensions
- **Aspect Ratio:** 1:1 (square, matching typical JABCode symbols)
- **Size:** 70% of screen width (max 300dp)
- **Position:** Centered vertically, slight offset upward (1/3 from top)

### Performance Targets
- **Frame Processing:** 10 FPS (current throttle)
- **With Bounds Detection:** 8 FPS minimum
- **Edge Detection:** <50ms per frame
- **Total Decode Latency:** <200ms (including crop + native decode)

### Accessibility
- TalkBack announcements for scan results
- High contrast mode for viewfinder
- Larger touch targets (48dp minimum)
- Voice commands (optional: "Scan", "Toggle flash")

---

## Files to Create/Modify

### New Files
- `ViewfinderOverlay.java` - Custom viewfinder overlay view
- `SettingsBottomSheet.java` - Settings dialog fragment
- `ScannerPreferences.java` - Settings persistence layer
- `BoundsDetector.java` - Edge detection for JABCode bounds
- `res/layout/bottom_control_sheet.xml` - Bottom controls layout
- `res/layout/bottom_sheet_settings.xml` - Settings UI
- `res/layout/item_result_card.xml` - Enhanced result card
- `res/anim/*.xml` - Animation resources

### Modified Files
- `ScannerActivity.java` - Add camera controls, bounds detection
- `activity_scanner.xml` - Add ViewfinderOverlay, bottom sheet
- `build.gradle` - Add android-image-cropper dependency
- `res/values/colors.xml` - Material 3 color scheme
- `res/values/themes.xml` - Dark mode support

### Dependencies to Add
```gradle
dependencies {
    // Android-Image-Cropper
    implementation 'com.vanniktech:android-image-cropper:4.5.0'
    
    // Material Design 3
    implementation 'com.google.android.material:material:1.11.0'
    
    // Preferences
    implementation 'androidx.preference:preference-ktx:1.2.1'
    
    // Lottie (for animations)
    implementation 'com.airbnb.android:lottie:6.3.0'
}
```

---

## Success Metrics

### Functional
- ✅ All camera controls working (exposure, WB, focus, zoom)
- ✅ Settings persist across app restarts
- ✅ Bounds detection improves decode accuracy
- ✅ All 6 working color modes decode successfully

### UX
- ✅ User can scan JABCode within 3 seconds
- ✅ Clear visual feedback for all states
- ✅ One-handed operation possible
- ✅ Accessible to TalkBack users

### Performance
- ✅ 60 FPS camera preview (no jank)
- ✅ <200ms total decode latency
- ✅ <5% CPU usage when idle (preview only)
- ✅ <150MB memory footprint

---

## Notes

- Android-Image-Cropper uses Canny edge detection + contour finding
- For JABCode, we can optimize by looking for square/rectangular shapes only
- Finder pattern detection can inform bounds detection (look for cyan/yellow corners)
- Luminance-based binarization should improve edge detection accuracy
- Consider caching edge detection results across frames (temporal filtering)

## References
- Material Design 3 Guidelines: https://m3.material.io
- CameraX Documentation: https://developer.android.com/training/camerax
- Android-Image-Cropper: https://github.com/CanHub/Android-Image-Cropper
- Scanbot SDK Best Practices: https://scanbot.io/techblog/
