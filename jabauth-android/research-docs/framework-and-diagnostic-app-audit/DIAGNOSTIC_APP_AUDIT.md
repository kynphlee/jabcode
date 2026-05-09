# Diagnostic App Audit - Expert Analysis
**Date:** 2026-05-09  
**Auditor:** JARVIS (Agentic AI Assistant)  
**Scope:** `diagnostic-app` module (ScannerScreen, ScannerViewModel)

---

## Executive Summary

**Status:** ❌ **COMPLETELY INADEQUATE** - Current implementation is a toy demo, not a diagnostic tool

The diagnostic app bears **no resemblance** to the UI/UX wireframes specification. What should be a comprehensive Camera2 diagnostic platform with multiple screens, detailed telemetry, and robust error handling has been reduced to a single-screen JABCode scanner with minimal diagnostic value.

**Gap Analysis:**

| Specified Component | Implementation Status | Completeness |
|---------------------|----------------------|--------------|
| Dashboard Screen | ❌ Missing | 0% |
| Camera Detail Screen | ❌ Missing | 0% |
| Live Preview & Metadata | ⚠️ Partial (no metadata) | 15% |
| Error Log Screen | ❌ Missing | 0% |
| Capture Test Screen | ❌ Missing | 0% |
| Settings Screen | ❌ Missing | 0% |
| Error State Handling | ❌ Missing | 0% |
| Navigation Flow | ❌ Missing | 0% |
| Hardware Enumeration | ❌ Missing | 0% |
| Capability Display | ❌ Missing | 0% |
| Session Diagnostics | ❌ Missing | 0% |
| Performance Metrics | ❌ Missing | 0% |

**Overall Compliance:** 2% of specified requirements implemented

**Risk Level:** EXTREME - App provides no diagnostic value beyond basic JABCode scanning

---

## 1. Architecture Comparison

### Specified Architecture (from Wireframes)

```
┌─────────────────────────────────────────┐
│         Dashboard (Entry Point)          │
│  - Device summary (HW level, cameras)   │
│  - Camera cards with status badges      │
│  - Capability chips (RAW, ZSL, etc)     │
│  - Inline error warnings                │
└─────┬───────────────────────────────────┘
      │
      ├──► Camera Detail Screen
      │    - Full CameraCharacteristics dump
      │    - Sensor/Optics/3A grouped sections
      │    - Stream configuration tables
      │    - Advanced capabilities list
      │
      ├──► Live Preview & Metadata Screen
      │    - Real-time camera preview
      │    - Frame metadata display
      │    - Quality metrics (brightness, focus, contrast)
      │    - 3A state indicators
      │    - Exposure/ISO/Focus distance
      │
      ├──► Error Log Screen
      │    - Timestamped error entries
      │    - Error severity badges
      │    - StateCallback/CaptureCallback errors
      │    - Export functionality
      │
      ├──► Capture Test Screen
      │    - Stream configuration builder
      │    - Validation chips
      │    - Run test button
      │    - Test history cards
      │    - Latency measurement
      │
      └──► Settings Screen
           - Logging verbosity toggles
           - Export format selection
           - Background monitoring options
           - About/version info
```

### Actual Architecture

```
┌─────────────────────────────┐
│      ScannerScreen          │
│  - Camera preview (40% height)
│  - JABCode scan results (60% height)
│  - Scan counter             │
│  - No navigation            │
│  - No error details         │
│  - No hardware info         │
│  - No settings              │
└─────────────────────────────┘
```

**Architectural Gap:** 98% of specified screens and features missing.

---

## 2. Screen-by-Screen Analysis

### 2.1 Dashboard Screen (Specified)

**Purpose:** Entry point providing scannable summary of all cameras, hardware levels, and active errors.

**Required Components:**
1. Device Summary Card
   - Device model, Android version, API level
   - Total cameras detected
   - Overall status badge (OK/WARN/FAIL)
2. Camera Cards (one per detected camera)
   - Camera ID, facing direction, friendly name
   - Hardware level badge (FULL/LIMITED/LEGACY)
   - Max resolution
   - Availability status
   - Capability chips (RAW, ZSL, Manual Sensor, HDR)
   - Inline error banners for unavailable cameras
3. Bottom Navigation Bar
   - Overview, Cameras, Preview, Errors, Settings tabs
4. Top App Bar with menu and filter chips

**Actual Implementation:** ❌ **DOES NOT EXIST**

**Impact:** Users cannot:
- See available cameras at a glance
- Identify hardware support levels
- Diagnose camera availability issues
- Navigate to detailed diagnostics

---

### 2.2 Camera Detail Screen (Specified)

**Purpose:** Deep-dive into a single camera's CameraCharacteristics for debugging.

**Required Components:**
1. Hero Row: Hardware Level, Facing, Camera ID (large badges)
2. Grouped Sections (collapsible):
   - **Sensor:** Size, active array, frame duration, sensitivity range, exposure range, orientation
   - **Optics:** Focal lengths, apertures, OIS, optical zoom ratio
   - **3A Capabilities:** AF modes, AE modes, AWB modes, flash, AE lock
   - **Stream Configurations:** Max sizes per format (JPEG, YUV, RAW, PRIVATE), HFR support
   - **Advanced Capabilities:** Full list of REQUEST_AVAILABLE_CAPABILITIES flags
3. Export Button (share characteristics JSON)

**Actual Implementation:** ❌ **DOES NOT EXIST**

**Impact:** Users cannot:
- Diagnose hardware limitations
- Verify supported formats/sizes
- Export camera specs for bug reports
- Compare devices

---

### 2.3 Live Preview & Metadata Screen (Specified)

**Purpose:** Real-time camera preview with frame-by-frame metadata display.

**Required Components:**
1. Camera Preview (landscape preview surface)
2. Metadata Panel:
   - Frame number, timestamp
   - Exposure time, ISO sensitivity
   - Focus distance, AF state
   - AE state, AWB state
   - Lens state (moving/stationary)
3. Quality Metrics:
   - Brightness bar (0-100%)
   - Focus score bar (0-100%)
   - Contrast bar (0-100%)
4. 3A State Indicators:
   - AF: Scanning/Focused/NotFocused badges
   - AE: Searching/Converged/FlashRequired badges
   - AWB: Searching/Converged badges

**Actual Implementation:** ⚠️ **15% COMPLETE**

```kotlin
// Current: ScannerScreen.kt
Camera2Preview(
    onFrameAvailable = { reader -> viewModel.analyzeFrame(reader) },
    modifier = Modifier.fillMaxWidth().weight(0.4f)
)

// Diagnostic results panel shows:
// - JABCode scan results (color mode, decode time, hex dump)
// - Scan counter
// - Waiting state with spinner
```

**Missing:**
- ❌ Frame metadata display (exposure, ISO, focus)
- ❌ Quality metrics visualization
- ❌ 3A state tracking
- ❌ Performance metrics (FPS, latency)
- ❌ Frame number/timestamp display

**Impact:** Users cannot:
- Diagnose focus issues
- Verify exposure/ISO values
- Track 3A convergence
- Measure frame rate
- Debug metadata anomalies

---

### 2.4 Error Log Screen (Specified)

**Purpose:** Centralized log of all Camera2 errors with filtering and export.

**Required Components:**
1. Error List (reverse chronological):
   - Timestamp
   - Severity badge (ERROR/WARN/INFO)
   - Error source (StateCallback/CaptureCallback/Session)
   - Error code and human-readable message
   - Camera ID
   - Expandable details panel
2. Filter Controls:
   - By severity (ERROR, WARN, INFO)
   - By camera ID
   - By time range
3. Export Button:
   - JSON format
   - Include full CameraCharacteristics
   - Share via intent

**Actual Implementation:** ❌ **DOES NOT EXIST**

**Current Error Handling:**
```kotlin
// ScannerViewModel.kt
onDecodeFailure = { error ->
    _scanError.value = error  // Just overwrites previous error
}

// ScannerScreen.kt
scanError?.let { error ->
    Card(/* error card */) {
        Text(text = error)  // No timestamp, no severity, no details
    }
}
```

**Missing:**
- ❌ Error history (only latest error shown)
- ❌ Timestamps
- ❌ Severity classification
- ❌ Error code mapping
- ❌ Camera-specific errors
- ❌ Export functionality
- ❌ Filtering

**Impact:** Users cannot:
- Review error history
- Identify error patterns
- Export logs for debugging
- Distinguish critical vs minor errors
- Track which camera caused error

---

### 2.5 Capture Test Screen (Specified)

**Purpose:** Test arbitrary stream configurations and measure session performance.

**Required Components:**
1. Stream Configuration Builder:
   - Output 1: Format dropdown (JPEG, YUV, RAW, PRIVATE), Size dropdown
   - Output 2: Format dropdown, Size dropdown
   - Output 3: Format dropdown, Size dropdown
   - Use Case dropdowns (PREVIEW, STILL_CAPTURE, VIDEO_RECORD)
2. Validation Chips:
   - "Combination Valid ✔" (green) or "Unsupported ✗" (red)
   - "Sizes Supported ✔"
3. Run Test Button (primary CTA)
4. Test Results History:
   - Test run number
   - Status badge (PASSED/PARTIAL/FAILED)
   - Session config result (onConfigured/onConfigureFailed)
   - Capture result (onCaptureCompleted/onCaptureFailed)
   - Frame number, latency, JPEG size
   - Failure reason (if applicable)

**Actual Implementation:** ❌ **DOES NOT EXIST**

**Impact:** Users cannot:
- Test custom stream combinations
- Validate hardware support
- Measure capture latency
- Debug session configuration failures
- Compare performance across configs

---

### 2.6 Settings Screen (Specified)

**Purpose:** Configure diagnostic logging and export preferences.

**Required Components:**
1. Logging Section:
   - Verbose Logging toggle
   - Log Capture Failures toggle
   - Log Partial Results toggle
   - Log Retention dropdown (1/7/30 days)
2. Export Section:
   - Export Format dropdown (JSON/CSV/TXT)
   - Include CameraCharacteristics toggle
3. Background Monitoring Section:
   - Monitor Camera Availability toggle
   - Notify on Camera Error toggle
4. About Section:
   - App version
   - Camera2 API level
   - Build date

**Actual Implementation:** ❌ **DOES NOT EXIST**

**Impact:** Users cannot:
- Control logging verbosity
- Configure export format
- Enable background monitoring
- Check app version

---

### 2.7 Error State Screen (Specified)

**Purpose:** Dedicated full-screen error state for fatal Camera2 errors.

**Required Components:**
1. Red App Bar (visual signal of error state)
2. Error Icon (large, centered)
3. Error Code Display:
   - Symbolic name (ERROR_CAMERA_SERVICE)
   - Numeric code (5)
4. Plain-language description
5. Diagnostic Details Table:
   - Camera ID
   - Source (StateCallback.onError)
   - Timestamp
   - Recovery attempts counter
   - Recovery status
6. Recovery Actions:
   - Primary: "Retry Camera Open" button
   - Secondary: "Export Error Report" button
7. Guidance Text (e.g., "restart device if persists")

**Actual Implementation:** ❌ **DOES NOT EXIST**

**Current Error Display:**
```kotlin
scanError?.let { error ->
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "⚠ Error", ...)
            Text(text = error, ...)  // Just the string
        }
    }
}
```

**Missing:**
- ❌ Full-screen error state
- ❌ Error code classification
- ❌ Recovery attempt tracking
- ❌ Export functionality
- ❌ User guidance
- ❌ Retry mechanism

**Impact:** Fatal errors (ERROR_CAMERA_SERVICE, ERROR_CAMERA_DEVICE) have no dedicated recovery flow.

---

### 2.8 Navigation Flow (Specified)

**Purpose:** Structured navigation between diagnostic screens.

**Required Navigation Paths:**
```
Dashboard → Camera Detail → back
Dashboard → Live Preview → back
Dashboard → Error Log → Error Detail → back
Dashboard → Capture Test → back
Dashboard → Settings → back

Any screen → Bottom Nav → Overview/Cameras/Preview/Errors/Settings
```

**Actual Implementation:** ❌ **DOES NOT EXIST**

**Current Navigation:**
```kotlin
// MainActivity.kt presumably shows ScannerScreen directly
// No NavController, no bottom navigation, no back stack
```

**Impact:** Users cannot:
- Navigate between diagnostic views
- Return to overview after deep-dive
- Access settings
- View error log while testing

---

## 3. Diagnostic Feature Gaps

### 3.1 Hardware Enumeration

**Specified:**
- Enumerate all cameras (front, back, external)
- Display hardware level for each (FULL, LIMITED, LEGACY, LEVEL_3, EXTERNAL)
- Show facing direction, max resolution
- List capabilities (RAW, ZSL, Manual Sensor, etc.)

**Actual:**
```kotlin
// Camera2Preview.kt:142
val cameraId = manager.cameraIdList[0]  // Hardcoded first camera
```

**Gap:** Zero hardware enumeration. Users don't know what cameras exist on their device.

---

### 3.2 Metadata Display

**Specified:**
- Frame-by-frame metadata from TotalCaptureResult:
  - Exposure time, ISO, focus distance
  - AF/AE/AWB states
  - Lens state, flash state
  - Frame number, timestamp

**Actual:**
```kotlin
// Camera2Preview.kt:239
session.setRepeatingRequest(
    requestBuilder.build(),
    null,  // ← NO CAPTURE CALLBACK
    backgroundHandler
)
```

**Gap:** Zero metadata extraction. Framework doesn't expose CaptureCallback, diagnostic app has no way to access metadata.

---

### 3.3 Quality Metrics

**Specified:**
- Brightness (0-100% bar)
- Focus score (0-100% bar)
- Contrast (0-100% bar)
- Color-coded feedback (red/yellow/green)

**Actual:**
```kotlin
// ScannerViewModel.kt:26-40
private val analyzer = Camera2JABCodeAnalyzer(
    decoder = decoder,
    options = DecodeOptions(
        timeout = 200L,
        analyzeIntervalMs = 500L
    ),
    onDecodeSuccess = { ... },
    onDecodeFailure = { ... }
    // onQualityUpdate parameter not used
)
```

**Gap:** Quality analyzer exists in framework but not wired to UI. No visual feedback on image quality.

---

### 3.4 Error Logging

**Specified:**
- Timestamped error entries
- Severity classification (ERROR/WARN/INFO)
- Error source tracking (StateCallback, CaptureCallback, Session)
- Error code interpretation
- Export to JSON with full context

**Actual:**
```kotlin
// ScannerViewModel.kt:37-39
onDecodeFailure = { error ->
    _scanError.value = error  // Overwrites previous
}
```

**Gap:**
- No error history (only latest)
- No timestamps
- No severity levels
- No error code mapping
- No export

---

### 3.5 Performance Tracking

**Specified:**
- Frame rate measurement
- Capture latency (request → result time)
- Frame drop detection (frame number gaps)
- Session stability metrics

**Actual:**
```kotlin
// Zero performance tracking in app or framework
```

**Gap:** Cannot measure or display performance metrics.

---

### 3.6 Stream Configuration Testing

**Specified:**
- Visual stream builder (3 outputs)
- Format/size dropdowns per output
- Real-time validation (supported/unsupported)
- Run test button
- Results display (success/failure, latency)

**Actual:**
```kotlin
// Hardcoded in Camera2Preview.kt:
// - 1 TextureView (PRIV)
// - 1 ImageReader (YUV_420_888, 1280x720)
// No user configuration possible
```

**Gap:** Zero stream testing capability. Cannot validate arbitrary configurations.

---

## 4. UI/UX Violations

### 4.1 Material Design 3 Compliance

**Specified:**
- TopAppBar with proper colors
- Bottom Navigation Bar (5 tabs)
- Card-based layout with elevation
- Chip components for capabilities/status
- Proper spacing (8dp, 16dp, 24dp grid)
- Color-coded badges (green=ok, yellow=warn, red=error)

**Actual:**
```kotlin
Scaffold(
    topBar = {
        TopAppBar(
            title = { 
                Column {
                    Text("JABCode Diagnostic")
                    Text("Scans: $scanCount", ...)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
    }
) { ... }
```

**Issues:**
- ✅ TopAppBar exists
- ❌ No Bottom Navigation Bar
- ⚠️ Cards used but missing elevation/proper structure
- ❌ No chip components for capabilities
- ❌ Inconsistent spacing
- ⚠️ Partial color-coding (green success, red error, but no yellow warn)

**Compliance:** 40%

---

### 4.2 Information Architecture

**Specified:** Multi-screen navigation with clear hierarchy (Dashboard → Details)

**Actual:** Single flat screen with no navigation

**Violation:** Users cannot drill down from overview to details. All information cramped into one scrolling view.

---

### 4.3 Scan Results Display

**Specified (Live Preview & Metadata Screen):**
- Metadata panel with sensor readings
- Quality metrics bars
- 3A state indicators

**Actual:**
```kotlin
// Diagnostic results panel shows JABCode-specific data:
DiagnosticRow("Color Mode", result.colorMode.toString())
DiagnosticRow("Decode Time", "${result.decodeTimeMs}ms")
DiagnosticRow("Position", "${result.position.width()}×${result.position.height()}px")
DiagnosticRow("Data Size", "${result.data.size} bytes")

// + Decoded data card
// + Hex dump card
```

**Issue:** App shows JABCode diagnostic data (which is fine for a barcode scanner) but **zero Camera2 diagnostic data** (which is the stated purpose of a "Camera2 Diagnostic App").

**Correct Implementation:**
```kotlin
// Camera2 diagnostics FIRST:
DiagnosticRow("Frame Number", metadata.frameNumber.toString())
DiagnosticRow("Exposure Time", "${metadata.exposureTimeNs / 1_000_000}ms")
DiagnosticRow("ISO", metadata.sensitivity.toString())
DiagnosticRow("Focus Distance", "${metadata.focusDistance}m")
DiagnosticRow("AF State", afStateToString(metadata.afState))
DiagnosticRow("AE State", aeStateToString(metadata.aeState))

QualityMetricsBar("Brightness", qualityMetrics.brightness)
QualityMetricsBar("Focus", qualityMetrics.focus)
QualityMetricsBar("Contrast", qualityMetrics.contrast)

// JABCode results SECOND (if detected):
if (jabcodeResult != null) {
    // ... existing JABCode display
}
```

---

## 5. Functional Requirements vs Implementation

| Functional Requirement | Specified | Implemented | Gap |
|------------------------|-----------|-------------|-----|
| Multi-camera enumeration | ✓ | ✗ | 100% |
| Hardware level display | ✓ | ✗ | 100% |
| Capability chips | ✓ | ✗ | 100% |
| CameraCharacteristics inspector | ✓ | ✗ | 100% |
| Stream configuration builder | ✓ | ✗ | 100% |
| Capture test runner | ✓ | ✗ | 100% |
| Frame metadata display | ✓ | ✗ | 100% |
| Quality metrics visualization | ✓ | ✗ | 100% |
| 3A state tracking | ✓ | ✗ | 100% |
| Error log with history | ✓ | ✗ | 100% |
| Error export (JSON) | ✓ | ✗ | 100% |
| Settings screen | ✓ | ✗ | 100% |
| Background monitoring | ✓ | ✗ | 100% |
| Bottom navigation | ✓ | ✗ | 100% |
| Multi-screen architecture | ✓ | ✗ | 100% |
| Camera preview | ✓ | ✓ | 0% |
| Basic error display | ✓ | ⚠️ | 70% |
| JABCode scanning | ✗ (not specified) | ✓ | N/A |

**Overall Feature Completeness:** **2% of specified requirements**

---

## 6. Code Quality Issues

### 6.1 ViewModel Responsibilities

**File:** `ScannerViewModel.kt`

```kotlin
class ScannerViewModel : ViewModel() {
    private val decoder = JABCodeDecoderImpl()  // Hardcoded dependency
    
    private val analyzer = Camera2JABCodeAnalyzer(
        decoder = decoder,
        options = DecodeOptions(timeout = 200L, analyzeIntervalMs = 500L),
        onDecodeSuccess = { result -> /* ... */ },
        onDecodeFailure = { error -> /* ... */ }
    )
    
    fun analyzeFrame(reader: ImageReader) {
        analyzer.analyze(reader)
    }
}
```

**Issues:**
1. **Hardcoded dependencies** - No dependency injection
2. **Single responsibility violation** - ViewModel handles both UI state AND camera analysis
3. **No testability** - Cannot inject mock decoder or analyzer
4. **Tight coupling** - ViewModel directly dependent on Camera2JABCodeAnalyzer
5. **Missing diagnostic state** - No tracking of:
   - Frame metadata
   - Quality metrics
   - Camera errors
   - Performance metrics

**Required Refactoring:**
```kotlin
class ScannerViewModel @Inject constructor(
    private val camera2Controller: Camera2Controller,
    private val jabcodeDecoder: JABCodeDecoder
) : ViewModel() {
    
    // Camera2 diagnostic state
    private val _frameMetadata = MutableStateFlow<FrameMetadata?>(null)
    val frameMetadata = _frameMetadata.asStateFlow()
    
    private val _qualityMetrics = MutableStateFlow<QualityMetrics?>(null)
    val qualityMetrics = _qualityMetrics.asStateFlow()
    
    private val _cameraErrors = MutableStateFlow<List<CameraError>>(emptyList())
    val cameraErrors = _cameraErrors.asStateFlow()
    
    // JABCode state (secondary)
    private val _scanResult = MutableStateFlow<DecodeResult?>(null)
    val scanResult = _scanResult.asStateFlow()
    
    init {
        camera2Controller.setCallbacks(object : Camera2Callbacks {
            override fun onMetadata(metadata: FrameMetadata) {
                _frameMetadata.value = metadata
            }
            
            override fun onQuality(metrics: QualityMetrics) {
                _qualityMetrics.value = metrics
            }
            
            override fun onError(error: CameraError) {
                _cameraErrors.value = _cameraErrors.value + error
            }
            
            override fun onFrameAvailable(image: Image) {
                // Decode JABCode from frame
                viewModelScope.launch(Dispatchers.Default) {
                    val bitmap = CameraUtils.imageToBitmap(image)
                    bitmap?.let {
                        val result = jabcodeDecoder.decode(it, DecodeOptions())
                        _scanResult.value = result
                        it.recycle()
                    }
                }
            }
        })
    }
}
```

---

### 6.2 Composable Structure

**File:** `ScannerScreen.kt:67-207`

Single monolithic Column containing:
- Preview (40% height)
- Scrollable results panel (60% height)
  - Waiting state OR
  - Success state with multiple cards OR
  - Error state

**Issues:**
1. **No component extraction** - 200+ line composable should be broken into:
   - `CameraPreviewSection()`
   - `MetadataPanel()`
   - `QualityMetricsPanel()`
   - `JABCodeResultsPanel()`
   - `ErrorPanel()`
2. **Hardcoded layout weights** - 40/60 split not configurable
3. **Missing loading states** - No skeleton UI while camera initializes
4. **No empty states** - No guidance when no JABCode detected

**Required Refactoring:**
```kotlin
@Composable
fun ScannerScreen(viewModel: ScannerViewModel = viewModel()) {
    val frameMetadata by viewModel.frameMetadata.collectAsState()
    val qualityMetrics by viewModel.qualityMetrics.collectAsState()
    val jabcodeResult by viewModel.scanResult.collectAsState()
    val cameraErrors by viewModel.cameraErrors.collectAsState()
    
    Scaffold(
        topBar = { DiagnosticTopAppBar() },
        bottomBar = { DiagnosticBottomNav() }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Camera preview section
            CameraPreviewSection(
                modifier = Modifier.weight(0.4f)
            )
            
            // Diagnostic panels section
            DiagnosticResultsSection(
                frameMetadata = frameMetadata,
                qualityMetrics = qualityMetrics,
                jabcodeResult = jabcodeResult,
                errors = cameraErrors,
                modifier = Modifier.weight(0.6f)
            )
        }
    }
}

@Composable
private fun DiagnosticResultsSection(
    frameMetadata: FrameMetadata?,
    qualityMetrics: QualityMetrics?,
    jabcodeResult: DecodeResult?,
    errors: List<CameraError>,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier) {
        // Camera2 metadata FIRST
        item { FrameMetadataCard(frameMetadata) }
        item { QualityMetricsCard(qualityMetrics) }
        
        // JABCode results SECOND (if present)
        if (jabcodeResult != null) {
            item { JABCodeResultCard(jabcodeResult) }
        }
        
        // Errors LAST
        items(errors) { error ->
            CameraErrorCard(error)
        }
    }
}
```

---

## 7. Missing Critical Screens

### 7.1 Dashboard Screen Template

```kotlin
@Composable
fun DashboardScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Camera2 Diagnostics") })
        },
        bottomBar = {
            DiagnosticBottomNav(navController, selectedTab = 0)
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            // Device summary card
            item {
                DeviceSummaryCard(
                    deviceModel = Build.MODEL,
                    androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                    camera2Support = true,
                    totalCameras = cameraManager.cameraIdList.size,
                    status = calculateOverallStatus()
                )
            }
            
            // Camera cards
            items(cameraList) { camera ->
                CameraCard(
                    cameraId = camera.id,
                    facing = camera.facing,
                    hardwareLevel = camera.hwLevel,
                    maxResolution = camera.maxRes,
                    status = camera.status,
                    capabilities = camera.capabilities,
                    onClick = { navController.navigate("camera_detail/${camera.id}") }
                )
            }
        }
    }
}
```

### 7.2 Camera Detail Screen Template

```kotlin
@Composable
fun CameraDetailScreen(cameraId: String, navController: NavController) {
    val characteristics = remember { getCameraCharacteristics(cameraId) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Camera $cameraId Details") },
                navigationIcon = { BackButton(navController) },
                actions = { ExportButton(characteristics) }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            // Hero row
            item {
                Row(Modifier.fillMaxWidth().padding(16.dp)) {
                    HardwareLevelBadge(characteristics.hwLevel)
                    Spacer(Modifier.width(8.dp))
                    FacingBadge(characteristics.facing)
                    Spacer(Modifier.width(8.dp))
                    CameraIdBadge(cameraId)
                }
            }
            
            // Sensor section
            item {
                ExpandableSection(title = "SENSOR") {
                    CharacteristicRow("Sensor Size", "${characteristics.sensorWidth} × ${characteristics.sensorHeight} mm")
                    CharacteristicRow("Active Array", "${characteristics.activeWidth} × ${characteristics.activeHeight} px")
                    CharacteristicRow("Max Frame Duration", "${characteristics.maxFrameDuration} ms")
                    CharacteristicRow("Sensitivity Range", "${characteristics.isoMin} – ${characteristics.isoMax} ISO")
                    CharacteristicRow("Exposure Range", "${characteristics.expMin} µs – ${characteristics.expMax} s")
                    CharacteristicRow("Orientation", "${characteristics.orientation}°")
                }
            }
            
            // Optics section
            item { OpticsSection(characteristics) }
            
            // 3A section
            item { ThreeASection(characteristics) }
            
            // Stream configurations section
            item { StreamConfigSection(characteristics) }
            
            // Advanced capabilities section
            item { AdvancedCapabilitiesSection(characteristics) }
        }
    }
}
```

### 7.3 Error Log Screen Template

```kotlin
@Composable
fun ErrorLogScreen(viewModel: ErrorLogViewModel) {
    val errors by viewModel.errors.collectAsState()
    val filter by viewModel.filter.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Error Log") },
                actions = {
                    FilterButton(filter) { viewModel.setFilter(it) }
                    ExportButton { viewModel.exportErrors() }
                }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            items(errors) { error ->
                ErrorLogEntry(
                    timestamp = error.timestamp,
                    severity = error.severity,
                    source = error.source,
                    errorCode = error.code,
                    message = error.message,
                    cameraId = error.cameraId,
                    expanded = error.id == viewModel.expandedErrorId.value,
                    onClick = { viewModel.toggleExpanded(error.id) }
                )
            }
        }
    }
}

@Composable
private fun ErrorLogEntry(
    timestamp: Long,
    severity: ErrorSeverity,
    source: String,
    errorCode: Int,
    message: String,
    cameraId: String,
    expanded: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SeverityBadge(severity)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatTimestamp(timestamp),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Camera $cameraId",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            Spacer(Modifier.height(8.dp))
            
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )
            
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Divider()
                Spacer(Modifier.height(8.dp))
                
                DetailRow("Source", source)
                DetailRow("Error Code", errorCode.toString())
                DetailRow("Timestamp", formatFullTimestamp(timestamp))
            }
        }
    }
}
```

---

## 8. Testing Strategy Violations

### Specified Testing (per Best Practices docs):

1. **Unit Tests:**
   - ViewModel state management
   - Error code interpretation
   - Stream validation logic
   - Characteristic parsing

2. **Instrumented Tests:**
   - Camera enumeration on real device
   - Session configuration
   - Capture callback behavior
   - Error recovery flows

**Actual Testing:**
```
diagnostic-app/src/androidTest/ → EMPTY
diagnostic-app/src/test/ → EMPTY
```

**Gap:** ZERO tests for diagnostic app.

---

## 9. Recommendations

### Phase 1: Foundation (3-4 days)

1. **Create proper navigation structure**
   - Implement NavController with bottom navigation
   - Create Dashboard, Camera Detail, Live Preview, Error Log, Settings destinations
   - Set up navigation graph

2. **Implement Dashboard Screen**
   - Device summary card
   - Camera enumeration and display
   - Hardware level badges
   - Capability chips

3. **Implement Camera Detail Screen**
   - CameraCharacteristics inspector
   - Grouped sections (Sensor, Optics, 3A, Streams)
   - Export functionality

### Phase 2: Diagnostic Core (4-5 days)

4. **Enhance Live Preview Screen**
   - Add frame metadata display (exposure, ISO, focus)
   - Add quality metrics visualization
   - Add 3A state indicators
   - Keep JABCode results as secondary feature

5. **Implement Error Log Screen**
   - Structured error logging with timestamps
   - Severity classification
   - Error history list
   - Export to JSON

6. **Implement Capture Test Screen**
   - Stream configuration builder
   - Validation logic
   - Test runner
   - Results history

### Phase 3: Polish (2-3 days)

7. **Implement Settings Screen**
   - Logging preferences
   - Export options
   - Background monitoring toggles

8. **Add Error State Handling**
   - Fatal error screen
   - Recovery flows
   - Error report export

9. **Material Design 3 compliance audit**
   - Consistent spacing
   - Proper elevation
   - Color system adherence
   - Typography hierarchy

### Phase 4: Testing (2-3 days)

10. **Write comprehensive tests**
    - ViewModel unit tests
    - Navigation tests
    - Error handling tests
    - Instrumented hardware tests

**Total Estimated Effort:** 11-15 days for experienced Android developer

---

## 10. Conclusion

The current diagnostic app implementation is **not a Camera2 diagnostic tool**—it's a JABCode scanner with minimal diagnostic value. It implements roughly **2% of the specified requirements** from the UI/UX wireframes document.

**Critical Missing Components:**
- ❌ Multi-screen navigation (Dashboard, Camera Detail, Error Log, Capture Test, Settings)
- ❌ Hardware enumeration and capability display
- ❌ Frame metadata extraction and display
- ❌ Quality metrics visualization
- ❌ 3A state tracking
- ❌ Error logging infrastructure
- ❌ Stream configuration testing
- ❌ Performance metrics
- ❌ Export functionality
- ❌ Settings/preferences

**What Exists:**
- ✅ Camera preview (from framework, with flaws)
- ✅ JABCode scanning (works but not the primary purpose)
- ⚠️ Basic error display (incomplete)

**Recommendation:** This app requires a **complete rebuild** following the wireframes specification. The current implementation should be treated as a proof-of-concept for JABCode integration, not as a foundation for the diagnostic app.

**Priority Actions:**
1. Review and internalize the UI/UX wireframes specification
2. Build proper navigation structure FIRST
3. Implement Dashboard as entry point
4. Add diagnostic screens one-by-one per spec
5. Treat JABCode scanning as ONE feature among many diagnostic capabilities

The diagnostic app should help users **understand their camera hardware**, not just scan barcodes.
