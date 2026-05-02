# JABAuth Diagnostic Framework - Modular Architecture

**Status**: 🏗️ Architecture Design  
**Created**: April 27, 2026  
**Purpose**: Transform android/testapp into modular diagnostic framework for JABAuth

---

## Executive Summary

Transform the single-purpose JABCode scanner app into a **modular diagnostic framework** that:
1. ✅ Tests all 7 JABAuth modules (PKI + JWT + ABE + JABCode + Spring + Core + Cloud)
2. ✅ Provides reusable components for custom client applications
3. ✅ Enables end-to-end authentication workflow debugging
4. ✅ Captures lessons learned for future development

---

## Current State Analysis

### Existing testapp Structure
```
android/testapp/
├── ScannerActivity         # Camera-based JABCode scanning
├── SettingsActivity        # App preferences
├── CalibrationActivity     # Color calibration for printing
├── ProfileManagerActivity  # Calibration profile management
├── CameraControlManager    # CameraX abstractions
├── AdaptiveCameraOptimizer # Auto-focus/exposure optimization
├── ImageQualityAnalyzer    # Pre-decode image checks
├── FeedbackManager         # Haptic/audio feedback
└── calibration/            # Printer color calibration
    ├── CalibrationProfile
    ├── CalibrationProfileManager
    ├── CalibrationActivity
    └── ProfileManagerActivity
```

**Limitations**:
- ❌ Single-purpose: JABCode scanning only
- ❌ No network capabilities (JABAuth API integration)
- ❌ No PKI/JWT/ABE testing
- ❌ Tightly coupled components
- ❌ No modular architecture for reuse

---

## Target Architecture: Modular Diagnostic Framework

### Multi-Module Gradle Project Structure

```
android/
├── settings.gradle.kts
├── build.gradle.kts
│
├── :core                       # Shared foundation
│   ├── models/                 # Data classes
│   ├── network/                # HTTP client abstraction
│   ├── storage/                # Local persistence
│   ├── logging/                # Diagnostic logging
│   └── utils/                  # Common utilities
│
├── :jabcode-sdk                # JABCode operations (extracted from testapp)
│   ├── scanner/                # Camera-based decoding
│   ├── encoder/                # JABCode generation
│   ├── calibration/            # Color calibration (moved from testapp)
│   ├── image/                  # Image processing
│   └── models/                 # JABCode-specific models
│
├── :jabauth-client             # JABAuth API client library
│   ├── api/                    # REST API interfaces
│   │   ├── CertificateApi      # PKI operations
│   │   ├── JwtApi              # Token operations
│   │   ├── JabCodeApi          # JABCode operations
│   │   └── HealthApi           # Health checks
│   ├── auth/                   # Authentication handlers
│   ├── interceptors/           # Network interceptors
│   └── models/                 # API request/response models
│
├── :diagnostic-engine          # Core diagnostic framework
│   ├── tests/                  # Diagnostic test suites
│   │   ├── PkiDiagnostics      # Certificate tests
│   │   ├── JwtDiagnostics      # Token lifecycle tests
│   │   ├── JabCodeDiagnostics  # Encode/decode tests
│   │   ├── E2EDiagnostics      # Full workflow tests
│   │   └── NetworkDiagnostics  # API connectivity tests
│   ├── reporters/              # Test result reporting
│   ├── monitors/               # Performance monitoring
│   └── analyzers/              # Issue detection
│
├── :ui-components              # Reusable UI widgets
│   ├── scanners/               # Camera scanner widget
│   ├── forms/                  # Input forms
│   ├── results/                # Result displays
│   ├── charts/                 # Performance charts
│   └── logs/                   # Log viewer
│
└── :diagnostic-app             # Main diagnostic application
    ├── MainActivity            # Navigation hub
    ├── features/               # Feature modules
    │   ├── scanner/            # JABCode scanning
    │   ├── certificate/        # PKI testing
    │   ├── token/              # JWT testing
    │   ├── e2e/                # End-to-end workflows
    │   ├── calibration/        # Calibration tools
    │   ├── settings/           # App settings
    │   └── reports/            # Diagnostic reports
    └── navigation/             # Navigation logic
```

---

## Module Specifications

### 1. `:core` - Foundation Module

**Purpose**: Shared utilities and base classes

**Components**:
```kotlin
core/
├── network/
│   ├── ApiClient.kt            # HTTP client factory
│   ├── NetworkResult.kt        # Result wrapper
│   └── RetryPolicy.kt          # Retry logic
├── storage/
│   ├── PreferencesManager.kt   # SharedPreferences wrapper
│   ├── SecureStorage.kt        # Encrypted storage
│   └── CacheManager.kt         # Response caching
├── logging/
│   ├── DiagnosticLogger.kt     # Structured logging
│   ├── LogLevel.kt             # Log levels
│   └── LogExporter.kt          # Export logs to file
└── models/
    ├── Result.kt               # Generic result type
    ├── ApiError.kt             # Error models
    └── ServerConfig.kt         # Server configuration
```

**Key Classes**:
```java
// Network abstraction
public interface ApiClient {
    <T> NetworkResult<T> execute(ApiRequest request);
    void setBaseUrl(String baseUrl);
    void setBearerToken(String token);
}

// Result wrapper
public sealed class NetworkResult<T> {
    public static class Success<T> extends NetworkResult<T> {
        public final T data;
    }
    public static class Error<T> extends NetworkResult<T> {
        public final String message;
        public final int code;
    }
    public static class Loading<T> extends NetworkResult<T> {}
}

// Diagnostic logging
public class DiagnosticLogger {
    public void logTest(String testName, TestResult result);
    public void logApiCall(String endpoint, long duration, int statusCode);
    public void logError(String category, Throwable error);
    public File exportLogs();
}
```

**Dependencies**:
```kotlin
dependencies {
    api("com.squareup.okhttp3:okhttp:4.12.0")
    api("com.google.code.gson:gson:2.10.1")
    api("androidx.security:security-crypto:1.1.0-alpha06")
    
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
```

---

### 2. `:jabcode-sdk` - JABCode Operations

**Purpose**: Encapsulate JABCode encoding/decoding (extracted from testapp)

**Components**:
```kotlin
jabcode-sdk/
├── scanner/
│   ├── JABCodeScanner.kt       # Camera-based scanning
│   ├── ScannerConfig.kt        # Scanner configuration
│   └── ScanResult.kt           # Scan result model
├── encoder/
│   ├── JABCodeEncoder.kt       # Encode text to JABCode
│   ├── EncoderConfig.kt        # Color mode, ECC settings
│   └── EncodedImage.kt         # Bitmap + metadata
├── calibration/                # Moved from testapp
│   ├── CalibrationProfile.kt
│   ├── CalibrationManager.kt
│   └── ColorCorrection.kt
├── camera/                     # Camera utilities
│   ├── CameraController.kt     # CameraX wrapper
│   ├── FocusManager.kt         # Auto-focus
│   └── ExposureOptimizer.kt    # Exposure control
└── image/
    ├── ImageQualityAnalyzer.kt
    ├── BitmapProcessor.kt
    └── ColorSpaceConverter.kt
```

**Public API**:
```java
// High-level scanner API
public class JABCodeScanner {
    public JABCodeScanner(Context context, LifecycleOwner lifecycle);
    
    public void startScanning(PreviewView previewView, ScannerConfig config);
    public void stopScanning();
    public void setOnScanListener(OnScanListener listener);
    public void loadCalibrationProfile(CalibrationProfile profile);
}

public interface OnScanListener {
    void onScanSuccess(ScanResult result);
    void onScanFailure(String error);
}

// Encoder API
public class JABCodeEncoder {
    public static EncodedImage encode(String data, EncoderConfig config);
    public static Bitmap encodeToBitmap(String data, int colorMode, int eccLevel);
}

// Scan result
public class ScanResult {
    public final String data;              // Decoded text
    public final long scanDurationMs;       // Scan time
    public final int colorMode;             // Detected color mode
    public final float imageQualityScore;   // 0.0 - 1.0
    public final Bitmap sourceBitmap;       // Original image
}
```

**Dependencies**:
```kotlin
dependencies {
    implementation(project(":core"))
    implementation(project(":library"))  // Native JABCode wrapper
    
    api("androidx.camera:camera-core:1.4.0")
    api("androidx.camera:camera-camera2:1.4.0")
    api("androidx.camera:camera-lifecycle:1.4.0")
    api("androidx.camera:camera-view:1.4.0")
}
```

---

### 3. `:jabauth-client` - JABAuth API Client

**Purpose**: Type-safe REST API client for JABAuth framework

**Components**:
```kotlin
jabauth-client/
├── api/
│   ├── CertificateApi.kt       # PKI operations
│   ├── JwtApi.kt               # Token operations
│   ├── JabCodeApi.kt           # JABCode generation/validation
│   ├── HealthApi.kt            # Server health checks
│   └── AbeApi.kt               # ABE encryption (future)
├── models/
│   ├── requests/               # API request models
│   │   ├── GenerateCertificateRequest.kt
│   │   ├── GenerateTokenRequest.kt
│   │   └── GenerateJabCodeRequest.kt
│   └── responses/              # API response models
│       ├── CertificateResponse.kt
│       ├── TokenResponse.kt
│       └── JabCodeResponse.kt
├── auth/
│   ├── BearerTokenInterceptor.kt
│   └── AuthManager.kt
└── JABAuthClient.kt            # Facade API
```

**API Interfaces**:
```java
// Certificate API
public interface CertificateApi {
    NetworkResult<CertificateResponse> generateCertificate(GenerateCertificateRequest request);
    NetworkResult<ValidationResult> validateCertificate(String certificateData);
    NetworkResult<Void> revokeCertificate(String certificateId);
}

// JWT API
public interface JwtApi {
    NetworkResult<TokenResponse> generateToken(GenerateTokenRequest request);
    NetworkResult<ValidationResult> validateToken(String token);
    NetworkResult<Void> blacklistToken(String token);
}

// JABCode API
public interface JabCodeApi {
    NetworkResult<JabCodeResponse> generateJabCode(GenerateJabCodeRequest request);
    NetworkResult<DecodeResult> decodeJabCode(byte[] imageData);
    NetworkResult<ValidationResult> validateJabCode(byte[] imageData);
}

// Facade client
public class JABAuthClient {
    public JABAuthClient(String baseUrl);
    
    public CertificateApi certificates();
    public JwtApi jwt();
    public JabCodeApi jabcode();
    public HealthApi health();
    
    public void setBearerToken(String token);
    public void clearToken();
}
```

**Request Models**:
```java
public class GenerateCertificateRequest {
    public String subject;           // e.g., "CN=Test User"
    public String keyAlgorithm;      // "RSA"
    public int keySize;              // 2048
    public int validityDays;         // 365
}

public class GenerateTokenRequest {
    public String subject;
    public String issuer;
    public Map<String, Object> claims;
    public int expirationMinutes;
}

public class GenerateJabCodeRequest {
    public String certificateId;
    public String tokenId;
    public byte[] data;
    public int colorMode;            // 1-6 (4, 8, 16, 32, 64, 128 colors)
    public int eccLevel;             // 0-9
}
```

**Dependencies**:
```kotlin
dependencies {
    implementation(project(":core"))
    
    api("com.squareup.retrofit2:retrofit:2.9.0")
    api("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
}
```

---

### 4. `:diagnostic-engine` - Testing Framework

**Purpose**: Automated diagnostic tests for JABAuth components

**Test Suites**:
```kotlin
diagnostic-engine/
├── tests/
│   ├── PkiDiagnostics.kt       # Certificate lifecycle tests
│   ├── JwtDiagnostics.kt       # Token generation/validation
│   ├── JabCodeDiagnostics.kt   # Encode/decode roundtrip
│   ├── E2EDiagnostics.kt       # Full authentication flow
│   └── PerformanceDiagnostics.kt # Latency/throughput tests
├── reporters/
│   ├── TestReporter.kt         # Test result formatting
│   ├── HtmlReporter.kt         # HTML report generation
│   └── JsonReporter.kt         # JSON export
├── monitors/
│   ├── LatencyMonitor.kt       # API call timing
│   ├── MemoryMonitor.kt        # Memory usage
│   └── BatteryMonitor.kt       # Power consumption
└── DiagnosticRunner.kt         # Test orchestration
```

**Diagnostic Tests**:
```java
// PKI diagnostics
public class PkiDiagnostics extends DiagnosticTest {
    @Test
    public TestResult testCertificateGeneration() {
        // 1. Generate RSA-2048 certificate
        // 2. Validate certificate structure
        // 3. Check expiration date
        // 4. Verify signature
    }
    
    @Test
    public TestResult testCertificateRevocation() {
        // 1. Generate certificate
        // 2. Revoke certificate
        // 3. Verify revocation status
    }
}

// JWT diagnostics
public class JwtDiagnostics extends DiagnosticTest {
    @Test
    public TestResult testTokenGeneration() {
        // 1. Generate JWT with custom claims
        // 2. Decode JWT header/payload
        // 3. Verify signature
        // 4. Check expiration
    }
    
    @Test
    public TestResult testTokenValidation() {
        // 1. Generate valid token
        // 2. Validate token
        // 3. Attempt validation with expired token
        // 4. Attempt validation with tampered token
    }
}

// JABCode diagnostics
public class JabCodeDiagnostics extends DiagnosticTest {
    @Test
    public TestResult testEncodeDecodeRoundtrip() {
        // 1. Encode test data (all color modes)
        // 2. Decode generated JABCode
        // 3. Verify data integrity
        // 4. Measure encode/decode time
    }
    
    @Test
    public TestResult testColorModeSupport() {
        // Test modes: 4, 8, 16, 32, 64, 128 colors
        // Verify each mode encodes/decodes successfully
    }
}

// End-to-end workflow
public class E2EDiagnostics extends DiagnosticTest {
    @Test
    public TestResult testFullAuthenticationFlow() {
        // 1. Generate certificate (PKI)
        // 2. Generate JWT token (with cert ID)
        // 3. Generate JABCode (with cert + token)
        // 4. Scan JABCode with camera
        // 5. Decode JABCode to extract cert + token
        // 6. Validate certificate
        // 7. Validate token
        // 8. Verify authentication success
    }
    
    @Test
    public TestResult testMobileToServerFlow() {
        // 1. Encode auth data locally (mobile)
        // 2. Generate JABCode bitmap
        // 3. Upload JABCode to server API
        // 4. Server decodes and validates
        // 5. Verify server authentication result
    }
}
```

**Test Reporting**:
```java
public class DiagnosticRunner {
    public DiagnosticReport runAll();
    public DiagnosticReport runSuite(Class<? extends DiagnosticTest> suite);
    public void setReporter(TestReporter reporter);
}

public class DiagnosticReport {
    public int totalTests;
    public int passed;
    public int failed;
    public long totalDurationMs;
    public List<TestResult> results;
    
    public String toHtml();
    public String toJson();
    public void exportToFile(File file);
}
```

**Dependencies**:
```kotlin
dependencies {
    implementation(project(":core"))
    implementation(project(":jabcode-sdk"))
    implementation(project(":jabauth-client"))
    
    api("junit:junit:4.13.2")
    implementation("org.json:json:20231013")
}
```

---

### 5. `:ui-components` - Reusable UI Widgets

**Purpose**: Shareable UI components for custom apps

**Components**:
```kotlin
ui-components/
├── scanners/
│   ├── JABCodeScannerView.kt   # Drop-in scanner widget
│   └── ScanResultCard.kt       # Result display
├── forms/
│   ├── CertificateForm.kt      # Certificate request form
│   ├── TokenForm.kt            # Token request form
│   └── ServerConfigForm.kt     # Server settings form
├── results/
│   ├── TestResultCard.kt       # Diagnostic test result
│   ├── ApiResponseViewer.kt    # JSON response viewer
│   └── ErrorDisplay.kt         # Error message display
├── charts/
│   ├── LatencyChart.kt         # API latency graph
│   └── SuccessRateChart.kt     # Test success rate
└── logs/
    ├── LogViewer.kt            # Scrollable log display
    └── LogFilterControls.kt    # Filter by level/category
```

**Widget Examples**:
```java
// Scanner widget (drop-in)
public class JABCodeScannerView extends FrameLayout {
    public JABCodeScannerView(Context context, AttributeSet attrs);
    
    public void startScanning();
    public void stopScanning();
    public void setOnScanListener(OnScanListener listener);
    public void setScannerConfig(ScannerConfig config);
}

// Usage in XML
<com.jabcode.ui.scanners.JABCodeScannerView
    android:id="@+id/scannerView"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    app:enableTorch="true"
    app:scannerMode="continuous" />

// Test result card
public class TestResultCard extends MaterialCardView {
    public void setTestResult(TestResult result);
    public void setOnRetryClickListener(OnClickListener listener);
}
```

**Dependencies**:
```kotlin
dependencies {
    implementation(project(":core"))
    implementation(project(":jabcode-sdk"))
    
    api("com.google.android.material:material:1.12.0")
    api("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
}
```

---

### 6. `:diagnostic-app` - Main Application

**Purpose**: User-facing diagnostic application

**Features**:
```kotlin
diagnostic-app/
├── features/
│   ├── scanner/                # JABCode scanning
│   │   ├── ScannerFragment.kt
│   │   └── ScannerViewModel.kt
│   ├── certificate/            # PKI testing
│   │   ├── CertificateFragment.kt
│   │   ├── CertificateViewModel.kt
│   │   └── CertificateDetailsFragment.kt
│   ├── token/                  # JWT testing
│   │   ├── TokenFragment.kt
│   │   ├── TokenViewModel.kt
│   │   └── TokenDetailsFragment.kt
│   ├── e2e/                    # End-to-end workflows
│   │   ├── E2EFragment.kt
│   │   ├── E2EViewModel.kt
│   │   └── WorkflowStepAdapter.kt
│   ├── diagnostics/            # Run diagnostic tests
│   │   ├── DiagnosticsFragment.kt
│   │   ├── DiagnosticsViewModel.kt
│   │   └── TestSuiteAdapter.kt
│   ├── calibration/            # Calibration tools
│   │   ├── CalibrationFragment.kt
│   │   └── ProfileManagerFragment.kt
│   ├── settings/               # App settings
│   │   ├── SettingsFragment.kt
│   │   └── ServerConfigFragment.kt
│   └── reports/                # Diagnostic reports
│       ├── ReportsFragment.kt
│       ├── ReportDetailsFragment.kt
│       └── ReportExporter.kt
├── navigation/
│   ├── MainNavGraph.kt         # Navigation graph
│   └── NavDestinations.kt      # Destination constants
└── MainActivity.kt             # Navigation host
```

**Navigation Structure**:
```
MainActivity (Navigation Drawer)
├── Home Dashboard
├── Scanner
│   ├── Live Scan
│   └── Gallery Decode
├── Certificates
│   ├── Generate Certificate
│   ├── Validate Certificate
│   └── Certificate History
├── Tokens
│   ├── Generate Token
│   ├── Validate Token
│   └── Token Inspector
├── JABCode
│   ├── Encode Data
│   ├── Decode Image
│   └── Roundtrip Test
├── E2E Workflows
│   ├── Full Authentication
│   ├── Mobile to Server
│   └── Custom Workflow
├── Diagnostics
│   ├── Run All Tests
│   ├── PKI Tests
│   ├── JWT Tests
│   ├── JABCode Tests
│   └── Performance Tests
├── Calibration
│   ├── Create Profile
│   ├── Manage Profiles
│   └── Calibration Guide
├── Reports
│   ├── Test Reports
│   ├── Performance Metrics
│   └── Export Data
└── Settings
    ├── Server Configuration
    ├── Scanner Settings
    ├── Logging Settings
    └── About
```

**Key Screens**:

**1. Scanner Screen**
```java
public class ScannerFragment extends Fragment {
    private JABCodeScannerView scannerView;
    private ScannerViewModel viewModel;
    
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        // Setup scanner
        scannerView.setOnScanListener(result -> {
            // Display scan result
            // Optionally trigger server validation
            viewModel.processScanResult(result);
        });
    }
}
```

**2. Certificate Test Screen**
```java
public class CertificateFragment extends Fragment {
    private CertificateViewModel viewModel;
    
    public void onGenerateCertificate() {
        GenerateCertificateRequest request = new GenerateCertificateRequest();
        request.subject = "CN=" + binding.subjectInput.getText();
        request.keySize = 2048;
        request.validityDays = 365;
        
        viewModel.generateCertificate(request).observe(this, result -> {
            if (result instanceof NetworkResult.Success) {
                // Show certificate details
                showCertificateDetails(result.data);
            } else if (result instanceof NetworkResult.Error) {
                // Show error
                showError(result.message);
            }
        });
    }
}
```

**3. E2E Workflow Screen**
```java
public class E2EFragment extends Fragment {
    // Displays step-by-step authentication flow
    // 1. Generate Certificate → ✓
    // 2. Generate Token      → ✓
    // 3. Generate JABCode    → ✓
    // 4. Scan JABCode        → In Progress...
    // 5. Validate Auth       → Pending
}
```

**4. Diagnostics Screen**
```java
public class DiagnosticsFragment extends Fragment {
    public void runAllTests() {
        DiagnosticRunner runner = new DiagnosticRunner();
        runner.setReporter(new HtmlReporter());
        
        DiagnosticReport report = runner.runAll();
        
        // Display results
        binding.totalTests.setText(String.valueOf(report.totalTests));
        binding.passedTests.setText(String.valueOf(report.passed));
        binding.failedTests.setText(String.valueOf(report.failed));
        
        // Show detailed results
        adapter.setResults(report.results);
    }
}
```

**Dependencies**:
```kotlin
dependencies {
    implementation(project(":core"))
    implementation(project(":jabcode-sdk"))
    implementation(project(":jabauth-client"))
    implementation(project(":diagnostic-engine"))
    implementation(project(":ui-components"))
    
    // Android
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    
    // Material Design
    implementation("com.google.android.material:material:1.12.0")
}
```

---

## Refactoring Strategy

### Phase 1: Extract Modules (Week 1)

**Goal**: Split testapp into reusable modules

**Steps**:
1. ✅ Create multi-module project structure
2. ✅ Extract `:core` module
   - Move `ApiClient` interfaces (create new)
   - Move logging utilities (create new)
   - Move storage utilities (create new)
3. ✅ Extract `:jabcode-sdk` module
   - Move `JABCodeMobile` wrapper
   - Move `CameraControlManager`
   - Move `AdaptiveCameraOptimizer`
   - Move `ImageQualityAnalyzer`
   - Move `calibration/` package
4. ✅ Verify builds independently

**Deliverable**: 3 modules (core, jabcode-sdk, testapp) building successfully

---

### Phase 2: Create API Client (Week 2)

**Goal**: Implement JABAuth REST API client

**Steps**:
1. ✅ Create `:jabauth-client` module
2. ✅ Define API interfaces
   - `CertificateApi`
   - `JwtApi`
   - `JabCodeApi`
3. ✅ Implement request/response models
4. ✅ Add Retrofit integration
5. ✅ Write unit tests (mock server)

**Deliverable**: Working API client with 90%+ test coverage

---

### Phase 3: Build Diagnostic Engine (Week 3)

**Goal**: Create automated test framework

**Steps**:
1. ✅ Create `:diagnostic-engine` module
2. ✅ Implement test suites
   - `PkiDiagnostics` (5 tests)
   - `JwtDiagnostics` (6 tests)
   - `JabCodeDiagnostics` (7 tests)
   - `E2EDiagnostics` (3 tests)
3. ✅ Implement test reporters
4. ✅ Add performance monitors
5. ✅ Write integration tests

**Deliverable**: 21 diagnostic tests running successfully

---

### Phase 4: Create UI Components (Week 4)

**Goal**: Build reusable UI library

**Steps**:
1. ✅ Create `:ui-components` module
2. ✅ Implement scanner widget
3. ✅ Implement form components
4. ✅ Implement result displays
5. ✅ Create sample app demonstrating widgets

**Deliverable**: UI component library with showcase app

---

### Phase 5: Build Diagnostic App (Week 5-6)

**Goal**: Assemble final diagnostic application

**Steps**:
1. ✅ Create `:diagnostic-app` module
2. ✅ Implement Navigation Drawer
3. ✅ Implement feature screens
   - Scanner (reuse existing)
   - Certificate testing
   - Token testing
   - E2E workflows
   - Diagnostics dashboard
4. ✅ Integrate all modules
5. ✅ User acceptance testing

**Deliverable**: Fully functional diagnostic app

---

### Phase 6: Documentation & Lessons (Week 7)

**Goal**: Capture knowledge for custom app development

**Steps**:
1. ✅ Document each module's API
2. ✅ Create integration guides
3. ✅ Write "Lessons Learned" document
4. ✅ Create sample custom app templates
5. ✅ Record demo videos

**Deliverable**: Complete developer documentation

---

## Custom App Templates

### Template 1: Simple Scanner App
```
custom-scanner-app/
└── Uses:
    ├── :jabcode-sdk (scanner only)
    └── :ui-components (JABCodeScannerView)
```

### Template 2: Authentication Client
```
auth-client-app/
└── Uses:
    ├── :core (network, storage)
    ├── :jabcode-sdk (scanner)
    ├── :jabauth-client (API client)
    └── :ui-components (forms, results)
```

### Template 3: Full Diagnostic Clone
```
custom-diagnostic-app/
└── Uses:
    ├── All 6 modules
    └── Custom features added
```

---

## Testing Strategy

### Unit Tests (80% coverage target)
- `:core` → Network, storage, logging
- `:jabcode-sdk` → Encoder, decoder, calibration
- `:jabauth-client` → API calls (mocked)
- `:diagnostic-engine` → Test suites
- `:ui-components` → Widget behavior

### Integration Tests
- `:jabauth-client` + real server
- `:diagnostic-engine` + real JABAuth API
- End-to-end workflows

### UI Tests
- `:diagnostic-app` → Espresso tests
- Navigation flows
- Form submissions

---

## Performance Targets

| Metric | Target | Notes |
|--------|--------|-------|
| JABCode Scan Time | <500ms | Camera to decode |
| API Call Latency | <200ms | Local network |
| Certificate Generation | <1s | Server-side |
| Token Generation | <100ms | Server-side |
| JABCode Generation | <300ms | Server-side |
| App Launch Time | <2s | Cold start |
| Memory Usage | <150MB | Normal operation |

---

## Deployment Strategy

### Diagnostic App Distribution
1. **Internal Testing**: TestFlight / Internal Testing track
2. **Beta Release**: Closed beta with JABAuth developers
3. **Production**: Open source GitHub release

### Module Distribution
1. **Maven Local**: Development
2. **JitPack**: Open source distribution
3. **Private Maven**: Enterprise distribution

---

## Success Criteria

### Functional Requirements
- ✅ All 6 modules build independently
- ✅ All diagnostic tests pass (21 tests)
- ✅ E2E authentication flow works
- ✅ App runs on Android 7.0+ (API 24+)
- ✅ Supports all 6 JABCode color modes

### Non-Functional Requirements
- ✅ 80%+ code coverage
- ✅ Zero memory leaks
- ✅ <2s app launch time
- ✅ All API docs complete
- ✅ Sample custom app templates ready

---

## Risk Mitigation

| Risk | Impact | Mitigation |
|------|--------|------------|
| API changes in JABAuth | High | Version pinning, changelog monitoring |
| Native library crashes | High | Error boundaries, crash reporting |
| Network unreliability | Medium | Retry logic, offline mode |
| Camera permission denial | Medium | Graceful degradation, file upload |
| Device fragmentation | Medium | Min SDK 24, extensive testing |

---

## Next Steps (Immediate)

1. **Create multi-module structure** (1 hour)
   ```bash
   cd android/
   # Create settings.gradle.kts with module includes
   # Create module directories
   ```

2. **Extract `:core` module** (4 hours)
   - Create network abstractions
   - Create storage abstractions
   - Create logging framework

3. **Extract `:jabcode-sdk`** (8 hours)
   - Move scanner components
   - Move calibration
   - Create public API
   - Write tests

4. **Spike `:jabauth-client`** (4 hours)
   - Implement 1 API (Certificates)
   - Test against real JABAuth server
   - Validate approach

---

## Appendix: File Migration Map

### From testapp → :jabcode-sdk
```
testapp/ScannerActivity.java              → jabcode-sdk/scanner/JABCodeScanner.kt
testapp/CameraControlManager.java         → jabcode-sdk/camera/CameraController.kt
testapp/AdaptiveCameraOptimizer.java      → jabcode-sdk/camera/FocusManager.kt
testapp/ImageQualityAnalyzer.java         → jabcode-sdk/image/ImageQualityAnalyzer.kt
testapp/calibration/*                     → jabcode-sdk/calibration/*
testapp/FeedbackManager.java              → jabcode-sdk/feedback/FeedbackManager.kt
testapp/ViewfinderOverlay.java            → jabcode-sdk/ui/ViewfinderOverlay.kt
```

### New Files in :jabauth-client
```
jabauth-client/api/CertificateApi.kt      → NEW
jabauth-client/api/JwtApi.kt              → NEW
jabauth-client/api/JabCodeApi.kt          → NEW
jabauth-client/models/requests/*          → NEW
jabauth-client/models/responses/*         → NEW
jabauth-client/JABAuthClient.kt           → NEW
```

### New Files in :diagnostic-engine
```
diagnostic-engine/tests/PkiDiagnostics.kt → NEW
diagnostic-engine/tests/JwtDiagnostics.kt → NEW
diagnostic-engine/tests/JabCodeDiagnostics.kt → NEW
diagnostic-engine/tests/E2EDiagnostics.kt → NEW
diagnostic-engine/reporters/HtmlReporter.kt → NEW
diagnostic-engine/DiagnosticRunner.kt     → NEW
```

---

**Ready to proceed with Phase 1 implementation?**
