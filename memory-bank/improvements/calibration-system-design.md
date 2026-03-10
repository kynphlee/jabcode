# JABCode Color Calibration System Design

**Based on:** Bugert et al. 2024 - Color Calibration for Multicolored Barcodes  
**Target:** Android/iOS mobile scanner  
**Status:** Design Complete, Implementation Pending  
**Created:** 2026-01-29

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    Calibration Workflow                      │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  1. Generate Test Pattern                                    │
│     ↓                                                         │
│     [TestPatternGenerator] → 77×77 JABCode w/ 4096 colors   │
│     ↓                                                         │
│     Export high-res PNG                                      │
│                                                               │
│  2. User Prints Pattern (external printer)                   │
│                                                               │
│  3. Capture with Camera                                      │
│     ↓                                                         │
│     [ScannerActivity] → Detect JABCode                       │
│     ↓                                                         │
│     [CalibrationAnalyzer] → Sample 4096 modules              │
│                                                               │
│  4. Analyze Color Distances                                  │
│     ↓                                                         │
│     [ColorDistanceCalculator] → Euclidean distances          │
│     ↓                                                         │
│     [ColorOptimizer] → Find optimal 5 colors (R,G,B,W,M)    │
│                                                               │
│  5. Generate Calibration Profile                             │
│     ↓                                                         │
│     [CalibrationProfile] → JSON with color mapping           │
│     ↓                                                         │
│     Save to SharedPreferences / File                         │
│                                                               │
│  6. Apply During Encoding                                    │
│     ↓                                                         │
│     [ColorRemapper] → Map standard → calibrated colors       │
│     ↓                                                         │
│     Encode JABCode with remapped palette                     │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

---

## Data Structures

### 1. Calibration Profile (JSON)

```json
{
  "version": "1.0",
  "created": "2026-01-29T22:50:00Z",
  "printer": {
    "model": "Canon Pixma iP4000",
    "type": "inkjet",
    "user_notes": "Home printer, photo paper quality"
  },
  "camera": {
    "device": "Google Pixel 5",
    "captureDate": "2026-01-29T22:45:00Z"
  },
  "colorMapping": {
    "red":     {"standard": [255,   0,   0], "calibrated": [240,  10,   5]},
    "green":   {"standard": [  0, 255,   0], "calibrated": [ 15, 235,  12]},
    "blue":    {"standard": [  0,   0, 255], "calibrated": [  8,   5, 240]},
    "white":   {"standard": [255, 255, 255], "calibrated": [248, 250, 245]},
    "magenta": {"standard": [255,   0, 255], "calibrated": [235,  15, 230]}
  },
  "fixedColors": {
    "black":   [0,   0,   0],
    "yellow":  [255, 255, 0],
    "cyan":    [0, 255, 255]
  },
  "quality": {
    "averageColorDistance": 185.7,
    "redMagentaSeparation": 303.06,
    "minSeparation": 73.37
  },
  "testPattern": {
    "moduleCount": 5929,
    "sampleCount": 4096,
    "quantization": 16
  }
}
```

### 2. Test Pattern Metadata

```c
typedef struct {
    jab_int32 version;           // 15 (77×77 modules)
    jab_int32 module_count;      // 5929 total modules
    jab_int32 data_modules;      // 4096 color samples
    jab_int32 quantization;      // 16 steps per RGB channel
    jab_byte* color_samples;     // 4096×3 RGB values (R,G,B for each)
} jab_calibration_pattern;
```

### 3. Color Distance Matrix

```c
typedef struct {
    jab_float distances[8][8];   // Pairwise distances for 8 colors
    jab_int32 sample_count;       // Samples per color
    jab_float min_separation;     // Minimum distance found
    jab_float avg_separation;     // Average distance
} jab_color_distance_matrix;
```

### 4. Calibration Result

```c
typedef struct {
    jab_byte standard_colors[8][3];    // Original RGB values
    jab_byte calibrated_colors[8][3];  // Mapped RGB values
    jab_float quality_score;            // 0-100, higher is better
    jab_int32 success;                  // 1=success, 0=failure
    char error_message[256];            // If failure
} jab_calibration_result;
```

---

## Component Specifications

### Component 1: Test Pattern Generator

**File:** `CalibrationPatternGenerator.java`

**API:**
```java
public class CalibrationPatternGenerator {
    public static Bitmap generateTestPattern(int quantization) {
        // Generate 77×77 JABCode with 4096 color samples
        // quantization = 16 (steps per RGB channel: 0, 17, 34, ..., 255)
    }
    
    public static void exportTestPattern(Bitmap pattern, String outputPath) {
        // Export high-resolution PNG (1540×1540 pixels = 20px per module)
    }
}
```

**Implementation Details:**
- Create JABCode version 15 (77×77 modules)
- Preserve finder patterns (Black, Yellow, Cyan)
- Fill data region with 4,096 color samples
- Quantize RGB space: 16³ = 4,096 colors
- Step size: 256 / 16 = 17 (values: 0, 17, 34, 51, ..., 255)

### Component 2: Calibration Analyzer

**File:** `CalibrationAnalyzer.java`

**API:**
```java
public class CalibrationAnalyzer {
    public CalibrationResult analyzeCapturedPattern(Bitmap capturedImage) {
        // 1. Detect JABCode in captured image
        // 2. Sample all 4,096 color modules
        // 3. Calculate color distances for 8 standard colors
        // 4. Find optimal calibrated colors
        // 5. Generate calibration profile
    }
    
    private float calculateEuclideanDistance(int[] rgb1, int[] rgb2) {
        // √((r2-r1)² + (g2-g1)² + (b2-b1)²)
    }
    
    private int[] findOptimalColor(int[] standardRGB, List<int[]> samples) {
        // From samples, find RGB closest to standard color's corner
    }
}
```

**Algorithm (from Bugert paper):**
1. For each standard color (Red, Green, Blue, White, Magenta):
   - Look at corresponding corner of RGB cube
   - Find sampled color closest to that corner
   - Map standard → sampled color
2. Keep Black, Yellow, Cyan unchanged (finder patterns)
3. Calculate quality metrics (separation distances)

### Component 3: Profile Storage

**File:** `CalibrationProfileManager.java`

**API:**
```java
public class CalibrationProfileManager {
    public void saveProfile(CalibrationProfile profile) {
        // Save to SharedPreferences or File
    }
    
    public CalibrationProfile loadProfile(String profileId) {
        // Load from storage
    }
    
    public List<CalibrationProfile> listProfiles() {
        // List all saved profiles
    }
    
    public void deleteProfile(String profileId) {
        // Remove profile
    }
}
```

**Storage Location:**
- Android: `{app_data}/calibration_profiles/{printer_model}.json`
- iOS: `{documents}/calibration_profiles/{printer_model}.json`

### Component 4: Color Remapper (Native C)

**File:** `color_calibration.c`

**API:**
```c
// Load calibration profile
jab_int32 jabLoadCalibrationProfile(const char* profile_json);

// Remap color using active profile
void jabRemapColor(jab_byte* rgb_in, jab_byte* rgb_out);

// Apply calibration during encoding
void jabApplyCalibration(jab_encode* enc);

// Free calibration data
void jabFreeCalibration();
```

**Integration Point:**
- Called in `encoder.c` after palette generation
- Remaps colors before placing modules
- No change to decoder (reads embedded palette)

### Component 5: Calibration UI (Android)

**Files:**
- `CalibrationActivity.java` - Main workflow
- `activity_calibration.xml` - Layout
- `CalibrationCameraFragment.java` - Camera capture

**Workflow Screens:**

1. **Welcome Screen**
   - Explanation of calibration
   - "Generate Test Pattern" button
   
2. **Test Pattern Screen**
   - Display test pattern
   - "Share" button (export PNG)
   - "Print this pattern with your printer"
   
3. **Capture Screen**
   - Camera preview
   - "Scan Printed Pattern" button
   - Real-time detection feedback
   
4. **Analysis Screen**
   - Progress indicator
   - "Analyzing colors..."
   - Show color distance results
   
5. **Results Screen**
   - Quality score
   - Before/After color comparison
   - "Save Profile" button
   - "Test with Real JABCode" button

---

## Implementation Phases

### Phase 1: Core Components (Week 1)

**Tasks:**
1. ✅ Design architecture (this document)
2. Create `CalibrationProfile` data class
3. Implement `CalibrationPatternGenerator`
4. Write unit tests for test pattern generation

**Deliverables:**
- Generate valid 77×77 test pattern
- Export high-res PNG
- Verify with desktop decoder (should fail decode, but detect)

### Phase 2: Analysis Engine (Week 2)

**Tasks:**
1. Implement `CalibrationAnalyzer`
2. Color distance calculations
3. Optimal color finder algorithm
4. Profile generation logic

**Deliverables:**
- Analyze captured test pattern
- Generate calibration profile JSON
- Unit tests with synthetic data

### Phase 3: Native Integration (Week 3)

**Tasks:**
1. Create `color_calibration.c` in native library
2. JNI bindings for profile loading
3. Color remapping in encoder
4. Integration tests

**Deliverables:**
- Native color remapping works
- Encoded JABCode uses calibrated colors
- Roundtrip test: encode (calibrated) → decode

### Phase 4: UI Implementation (Week 4)

**Tasks:**
1. Create `CalibrationActivity`
2. Implement 5-screen workflow
3. Camera integration for capture
4. Profile management UI

**Deliverables:**
- Complete calibration workflow
- Save/load profiles
- User can calibrate a printer

### Phase 5: Polish & Test (Week 5)

**Tasks:**
1. Real-world testing with printed codes
2. Multi-printer validation
3. Error handling and edge cases
4. Documentation

**Deliverables:**
- Production-ready calibration system
- User guide
- Validated with 2+ printer types

---

## Test Strategy

### Unit Tests

**TestPatternGenerator:**
```java
@Test
public void testGeneratesCorrectModuleCount() {
    // 77×77 = 5929 modules
}

@Test
public void testQuantizationSteps() {
    // Verify 16 steps per channel
}

@Test
public void testFinderPatternsPreserved() {
    // Black, Yellow, Cyan unchanged
}
```

**CalibrationAnalyzer:**
```java
@Test
public void testColorDistanceCalculation() {
    // Euclidean distance formula
}

@Test
public void testOptimalColorFinding() {
    // Closest to RGB cube corner
}
```

### Integration Tests

**End-to-End Calibration:**
```java
@Test
public void testFullCalibrationWorkflow() {
    // 1. Generate pattern
    // 2. Simulate capture (use pre-captured image)
    // 3. Analyze
    // 4. Save profile
    // 5. Encode with profile
    // 6. Verify colors remapped
}
```

### Real-World Validation

**Printers to Test:**
- Canon Pixma iP4000 (inkjet) - baseline from paper
- HP LaserJet (laser)
- Brother MFC (multifunction)
- Epson EcoTank (ink tank)

**Success Criteria:**
- Red/Magenta separation > 150 units (daylight)
- Min separation > 50 units (all color pairs)
- 16-color codes decodable after calibration

---

## API Documentation

### Public API (Kotlin/Java)

```kotlin
// Generate calibration test pattern
val generator = CalibrationPatternGenerator()
val testPattern: Bitmap = generator.generate()
generator.export(testPattern, "/sdcard/jabcode_calibration.png")

// Analyze captured pattern
val analyzer = CalibrationAnalyzer()
val result: CalibrationResult = analyzer.analyze(capturedBitmap)

// Save profile
val manager = CalibrationProfileManager(context)
manager.save(result.profile)

// Use profile during encoding
val encoder = JABCodeEncoder()
encoder.setCalibrationProfile(result.profile)
val encoded = encoder.encode("Hello World", colorMode = 16)
```

### Native API (C)

```c
// Load calibration from JSON
jab_int32 jabLoadCalibrationProfile(const char* json);

// Apply to encoder
jabApplyCalibration(encoder);

// Encode with calibration
jab_encode* enc = jabEncode(...);
jab_bitmap* bmp = generateJABCode(enc, ...);
```

---

## Error Handling

### Error Codes

```java
public enum CalibrationError {
    SUCCESS(0),
    PATTERN_NOT_DETECTED(1),
    INSUFFICIENT_SAMPLES(2),
    POOR_SEPARATION(3),
    INVALID_PROFILE(4),
    STORAGE_FAILED(5)
}
```

### Validation Rules

**Test Pattern Validation:**
- Must detect as valid JABCode structure
- Must have 4,096 data modules
- Finder patterns must be intact

**Captured Pattern Validation:**
- Must detect JABCode in image
- Must sample all 4,096 modules
- Must have sufficient contrast

**Profile Quality Validation:**
- Min separation ≥ 30 units (warning)
- Min separation ≥ 50 units (acceptable)
- Min separation ≥ 100 units (excellent)

---

## User Experience Flow

### Calibration Setup (First Time)

1. **Entry Point:** Settings → "Printer Calibration"
2. **Intro:** "Improve 16+ color scanning accuracy"
3. **Generate:** Tap "Create Test Pattern"
4. **Share:** Export PNG, print with target printer
5. **Capture:** Scan printed pattern
6. **Results:** View quality metrics
7. **Save:** Name profile (e.g., "Home Canon Printer")
8. **Test:** Encode sample 16-color code

### Using Calibration (Daily Use)

1. **Encode Mode:** Select calibration profile from dropdown
2. **Auto-Apply:** Colors automatically remapped
3. **Visual Indicator:** Badge shows "Calibrated" on generated code
4. **Scan Test:** User prints, scans to verify

---

## Future Enhancements

### Phase 2 Features (Post-MVP)

1. **Cloud Profile Sharing**
   - Upload/download profiles by printer model
   - Community-contributed calibrations
   
2. **Auto-Detect Printer**
   - Parse print metadata from captured image
   - Suggest matching profile
   
3. **Lighting Compensation**
   - Capture under multiple lighting conditions
   - Interpolate optimal colors
   
4. **YUV Color Space**
   - Analyze in YUV instead of RGB
   - Reduce conversion artifacts

### Research Directions

1. **Machine Learning Color Correction**
   - Train neural network on print-capture pairs
   - Predict optimal colors without test pattern
   
2. **Real-Time Calibration**
   - Detect drift during scanning
   - Apply corrections dynamically

---

## References

**Primary:**
- Bugert et al. (2024) - Color Calibration for Multicolored Barcodes
- ISO/IEC 23634:2022 - JAB Code specification

**Implementation:**
- `@/src/jabcode/encoder.c` - Native encoder
- `@/swift-java-wrapper/android/testapp/.../ScannerActivity.java` - Scanner app
- `@/memory-bank/documentation/specification/audit-bugert-2024-color-calibration.md` - Audit report

---

**Status:** Design Complete ✅  
**Next:** Begin Phase 1 implementation  
**Estimated Effort:** 5 weeks (1 week per phase)  
**Priority:** High (enables 16+ color camera scanning)
