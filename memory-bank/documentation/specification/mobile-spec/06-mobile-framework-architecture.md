# JABAuth Mobile Framework Architecture Specification

**Version:** 2.0.0  
**Date:** 2026-05-02  
**Status:** ✅ Active Specification  
**Supersedes:** Legacy mobile-spec (v1.x - Native library only)

---

## Executive Summary

This specification defines the **JABAuth Mobile Framework**—a modular Android architecture that complements the server-side JABAuth framework for building authentication applications using visual JABCode communication.

### Key Principles

1. **Visual Communication** - All authentication data exchange happens via JABCode (screen-to-camera, bi-directional)
2. **Local Validation** - Cryptographic operations (PKI, JWT, ABE) execute on-device without network calls
3. **Modular Design** - Shared framework supports both diagnostic tools and custom applications
4. **JABAuth Alignment** - Module structure mirrors server-side framework (7 modules → 7 mobile modules)

---

## Framework Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                  APPLICATION LAYER                          │
├─────────────────────────────────────────────────────────────┤
│  :diagnostic-app            :custom-apps                    │
│  - Live testing             - Prescription auth             │
│  - Performance monitoring   - Contract signing              │
│  - Bug reporting            - Device provisioning           │
│  - Issue diagnostics        - Document verification         │
└─────────────────┬───────────────────────────────────────────┘
                  ↓
┌─────────────────────────────────────────────────────────────┐
│              MOBILE FRAMEWORK MODULES (7)                   │
├─────────────────────────────────────────────────────────────┤
│  Module 1: :core                                            │
│  - Data models, validation, storage, logging                │
├─────────────────────────────────────────────────────────────┤
│  Module 2: :jabcode-sdk                                     │
│  - Native JABCode encode/decode (C library)                 │
│  - Visual protocol handler (layer extraction)               │
│  - Camera integration (CameraX)                             │
│  - Calibration system                                       │
├─────────────────────────────────────────────────────────────┤
│  Module 3: :auth-client                                     │
│  - CertificateValidator (X.509, chain verification)         │
│  - JwtValidator (signature, expiration, claims)             │
│  - AbeDecryptor (policy-based encryption)                   │
│  - Local cryptographic operations (NO network)              │
├─────────────────────────────────────────────────────────────┤
│  Module 4: :diagnostic-engine                               │
│  - Automated test suites                                    │
│  - Live performance benchmarks (Microbenchmark)             │
│  - Issue detection & reporting                              │
│  - Workflow validation tests                                │
├─────────────────────────────────────────────────────────────┤
│  Module 5: :ui-components                                   │
│  - JABCodeScannerView (reusable camera widget)              │
│  - AuthenticationResultCard (validation display)            │
│  - ResponseJabCodeView (response display)                   │
│  - Material Design 3 themed components                      │
├─────────────────────────────────────────────────────────────┤
│  Module 6: :android-integration                             │
│  - Services (background authentication)                     │
│  - ContentProviders (data sharing)                          │
│  - BroadcastReceivers (event system)                        │
│  - Intent-based APIs (other apps)                           │
├─────────────────────────────────────────────────────────────┤
│  Module 7: :storage-adapters                                │
│  - AndroidKeystore (secure key storage)                     │
│  - EncryptedSharedPreferences (secure prefs)                │
│  - LRU cache (performance optimization)                     │
│  - Optional diagnostic telemetry (diagnostic app only)      │
└─────────────────────────────────────────────────────────────┘
```

---

## Module 1: :core - Foundation Layer

**Purpose:** Shared utilities, models, and abstractions used by all other modules

### Components

#### 1.1 Data Models

```kotlin
// Core authentication models
data class AuthenticationPayload(
    val token: String,              // JWT from server JABCode
    val metadata: PayloadMetadata,  // Resource info, timestamps
    val context: PayloadContext?    // Optional additional data
)

data class ValidationResult(
    val isValid: Boolean,
    val subject: String,
    val validUntil: Instant,
    val errors: List<ValidationError>
)

data class CertificateInfo(
    val certificateId: String,
    val subjectDN: String,
    val issuerDN: String,
    val validFrom: Instant,
    val validUntil: Instant
)
```

#### 1.2 Storage Abstractions

```kotlin
interface SecureStorage {
    fun storeSecurely(key: String, value: ByteArray)
    fun retrieveSecurely(key: String): ByteArray?
    fun delete(key: String)
}

interface CacheStorage {
    fun <T> cache(key: String, value: T, ttlSeconds: Long)
    fun <T> retrieve(key: String): T?
    fun invalidate(key: String)
}
```

#### 1.3 Validation Utilities

```kotlin
object ValidationUtils {
    fun validateX509Certificate(cert: X509Certificate): ValidationResult
    fun validateJwtStructure(token: String): Boolean
    fun validateResourceId(id: String): Boolean
    fun validateHash(hash: String, algorithm: String): Boolean
}
```

#### 1.4 Logging Framework

```kotlin
interface DiagnosticLogger {
    fun logPerformance(operation: String, durationMs: Long)
    fun logValidationFailure(reason: String, context: Map<String, Any>)
    fun logScanEvent(colorMode: Int, success: Boolean)
    fun exportLogs(): File // For bug reports
}
```

**Dependencies:** None (foundation module)

---

## Module 2: :jabcode-sdk - Visual Communication Layer

**Purpose:** JABCode encoding/decoding and visual protocol implementation

### Components

#### 2.1 Native C Library (Existing)

```c
// From existing mobile-spec
jab_encode* jabEncodeCreate(jab_byte* rgba, int width, int height, 
                            char* data, int length, int color_mode);
jab_data* jabDecode(jab_byte* rgba, int width, int height);
```

**Features:**
- ✅ Color modes 0-6 supported (4, 8, 16, 32, 64, 128 colors)
- ✅ LDPC error correction with matrix caching
- ✅ ARM NEON optimizations
- ❌ 256-color mode excluded (malloc corruption)

#### 2.2 Visual Protocol Handler

```kotlin
class JABCodeProtocol {
    /**
     * Decode authentication data from scanned JABCode
     * 
     * Multi-layer extraction:
     * - Layer 0: JWT token (encrypted, high ECC)
     * - Layer 1: Certificate ID or resource metadata (medium ECC)
     * - Layer 2: Additional claims or context (low ECC)
     */
    fun decodeAuthenticationData(bitmap: Bitmap): AuthenticationPayload {
        val layers = jabCodeNative.decodeMultiLayer(bitmap)
        
        return AuthenticationPayload(
            token = layers[0].content,
            metadata = parseMetadata(layers[1].content),
            context = layers.getOrNull(2)?.let { parseContext(it.content) }
        )
    }
    
    /**
     * Generate JABCode containing authentication response
     */
    fun generateResponseCode(
        response: AuthenticationResponse,
        colorMode: Int = 8,
        eccLevel: Int = 3
    ): Bitmap {
        return jabCodeNative.generateMultiLayerCode(
            layers = listOf(
                Layer(0, response.toJwtToken(), ErrorCorrection.HIGH),
                Layer(1, response.metadata.toJson(), ErrorCorrection.MEDIUM)
            ),
            colorMode = colorMode,
            eccLevel = eccLevel
        )
    }
}
```

#### 2.3 Camera Integration (CameraX)

```kotlin
class JABCodeScanner(private val protocol: JABCodeProtocol) {
    
    fun startScanning(
        lifecycleOwner: LifecycleOwner,
        onAuthDataScanned: (AuthenticationPayload) -> Unit
    ) {
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview,
            imageAnalysis.apply {
                setAnalyzer(executor) { imageProxy ->
                    analyzeFrame(imageProxy, onAuthDataScanned)
                }
            }
        )
    }
    
    private fun analyzeFrame(
        imageProxy: ImageProxy,
        callback: (AuthenticationPayload) -> Unit
    ) {
        // Throttle to 10 fps
        if (System.currentTimeMillis() - lastScanTime < 100) return
        
        val bitmap = imageProxy.toBitmap()
        
        // Quality pre-checks (from existing testapp)
        if (!ImageQualityAnalyzer.isSufficientQuality(bitmap)) return
        
        try {
            val payload = protocol.decodeAuthenticationData(bitmap)
            callback(payload)
            lastScanTime = System.currentTimeMillis()
        } catch (e: DecodeException) {
            // Log but continue scanning
        }
    }
}
```

#### 2.4 Calibration System (Existing)

From current testapp:
- `CalibrationProfile` (254 LOC) - Printer-specific color correction
- `CalibrationProfileManager` (192 LOC) - Profile storage/loading
- `CalibrationAnalyzer` (249 LOC) - Color deviation analysis

**Dependencies:** :core

---

## Module 3: :auth-client - Authentication Logic Layer

**Purpose:** Local cryptographic validation (PKI, JWT, ABE)

### Components

#### 3.1 Certificate Validator

```kotlin
class CertificateValidator(
    private val trustedCAs: List<X509Certificate>
) {
    /**
     * Validate X.509 certificate from scanned JABCode
     */
    fun validateCertificate(certPem: String): ValidationResult {
        val cert = parseCertificate(certPem)
        
        val errors = mutableListOf<ValidationError>()
        
        // Check expiration
        if (!checkExpiration(cert)) {
            errors.add(ValidationError.EXPIRED)
        }
        
        // Check signature against trusted CAs
        if (!checkSignature(cert, trustedCAs)) {
            errors.add(ValidationError.UNTRUSTED_ISSUER)
        }
        
        // Check revocation (optional, requires CRL/OCSP)
        if (revocationCheckEnabled && !checkRevocation(cert)) {
            errors.add(ValidationError.REVOKED)
        }
        
        return ValidationResult(
            isValid = errors.isEmpty(),
            subject = cert.subjectDN.name,
            validUntil = cert.notAfter.toInstant(),
            errors = errors
        )
    }
}
```

#### 3.2 JWT Validator

```kotlin
class JwtValidator(
    private val publicKeys: Map<String, PublicKey>
) {
    /**
     * Validate JWT token from scanned JABCode
     */
    fun validateToken(tokenString: String): TokenValidationResult {
        val token = parseJwt(tokenString)
        
        val errors = mutableListOf<ValidationError>()
        
        // Check signature
        val publicKey = publicKeys[token.keyId] 
            ?: return TokenValidationResult.error(ValidationError.UNKNOWN_KEY)
        
        if (!verifySignature(token, publicKey)) {
            errors.add(ValidationError.INVALID_SIGNATURE)
        }
        
        // Check expiration
        if (token.expiresAt < Instant.now()) {
            errors.add(ValidationError.EXPIRED)
        }
        
        // Check custom claims
        if (!validateCustomClaims(token.claims)) {
            errors.add(ValidationError.INVALID_CLAIMS)
        }
        
        return TokenValidationResult(
            isValid = errors.isEmpty(),
            subject = token.subject,
            claims = token.claims,
            expiresAt = token.expiresAt,
            errors = errors
        )
    }
}
```

#### 3.3 ABE Decryptor

```kotlin
class AbeDecryptor(
    private val policyEngine: PolicyEngine
) {
    /**
     * Decrypt ABE-encrypted data using user attributes
     */
    fun decrypt(
        encryptedData: ByteArray,
        policy: AccessPolicy,
        userAttributes: UserAttributes
    ): DecryptionResult {
        // Evaluate policy against user attributes
        if (!policyEngine.evaluate(policy, userAttributes)) {
            return DecryptionResult.accessDenied(
                reason = "Attributes do not satisfy policy: ${policy.expression}"
            )
        }
        
        // Decrypt using attribute-based private key
        val decrypted = try {
            abeService.decrypt(encryptedData, userAttributes.privateKey)
        } catch (e: DecryptionException) {
            return DecryptionResult.error(e.message)
        }
        
        return DecryptionResult.success(decrypted)
    }
}
```

**Dependencies:** :core

**NOTE:** This module performs **local validation only**. No network calls to backend servers.

---

## Module 4: :diagnostic-engine - Testing & Monitoring Framework

**Purpose:** Automated testing, live performance monitoring, and issue reporting

### Components

#### 4.1 Automated Diagnostic Tests

```kotlin
abstract class DiagnosticTest {
    abstract val name: String
    abstract val category: TestCategory
    abstract suspend fun execute(): TestResult
}

// Example: JABCode scan test
class JabCodeScanDiagnostic : DiagnosticTest() {
    override val name = "JABCode Scan & Decode"
    override val category = TestCategory.JABCODE
    
    override suspend fun execute(): TestResult {
        val testPayload = createTestPayload()
        val jabCode = protocol.generateResponseCode(testPayload)
        
        val scanned = protocol.decodeAuthenticationData(jabCode)
        
        return if (scanned.token == testPayload.token) {
            TestResult.success("Roundtrip successful")
        } else {
            TestResult.failure("Token mismatch", 
                expected = testPayload.token,
                actual = scanned.token
            )
        }
    }
}
```

#### 4.2 Live Performance Benchmarks (Microbenchmark)

```kotlin
/**
 * Live performance testing using androidx.benchmark.Microbenchmark
 * 
 * Measures:
 * - Encode latency per color mode
 * - Decode latency per color mode
 * - Memory allocation
 * - Thermal throttling
 */
@RunWith(AndroidJUnit4::class)
class JABCodePerformanceBenchmark {
    
    @get:Rule
    val benchmarkRule = BenchmarkRule()
    
    @Test
    fun benchmarkEncode4Color() {
        val data = "Test prescription data"
        
        benchmarkRule.measureRepeated {
            jabCodeNative.encode(
                data = data,
                colorMode = 4,
                eccLevel = 3
            )
        }
    }
    
    @Test
    fun benchmarkDecode8Color() {
        val testBitmap = loadTestFixture("test_8color_25x25.rgba")
        
        benchmarkRule.measureRepeated {
            jabCodeNative.decode(testBitmap)
        }
    }
    
    // Benchmark all color modes (4, 8, 16, 32, 64, 128)
    @Test
    fun benchmarkAllColorModes() {
        listOf(4, 8, 16, 32, 64, 128).forEach { colorMode ->
            val result = benchmarkRule.measureRepeated {
                jabCodeNative.encode("test", colorMode, 3)
            }
            
            // Export metrics for reporting
            DiagnosticLogger.logPerformance(
                operation = "encode_color_$colorMode",
                durationMs = result.medianMs,
                metadata = mapOf(
                    "allocations" = result.allocationCount,
                    "device_model" = Build.MODEL
                )
            )
        }
    }
}
```

#### 4.3 Issue Detection & Reporting

```kotlin
class IssueDetector {
    /**
     * Detect common authentication failures
     */
    fun detectIssues(
        scanHistory: List<ScanAttempt>,
        validationResults: List<ValidationResult>
    ): List<DetectedIssue> {
        val issues = mutableListOf<DetectedIssue>()
        
        // Pattern: High scan failure rate
        val failureRate = scanHistory.count { !it.success } / scanHistory.size.toFloat()
        if (failureRate > 0.5) {
            issues.add(DetectedIssue(
                severity = IssueSeverity.HIGH,
                category = IssueCategory.SCAN_FAILURE,
                description = "Scan failure rate: ${(failureRate * 100).toInt()}%",
                recommendation = "Check camera focus, lighting, or use 4-color mode"
            ))
        }
        
        // Pattern: Certificate expiration
        val expiredCerts = validationResults.count { 
            it.errors.contains(ValidationError.EXPIRED)
        }
        if (expiredCerts > 0) {
            issues.add(DetectedIssue(
                severity = IssueSeverity.MEDIUM,
                category = IssueCategory.CERTIFICATE,
                description = "$expiredCerts expired certificates detected",
                recommendation = "Contact certificate authority for renewal"
            ))
        }
        
        return issues
    }
}
```

#### 4.4 Diagnostic Report Generator

```kotlin
class DiagnosticReportGenerator(
    private val logger: DiagnosticLogger,
    private val issueDetector: IssueDetector
) {
    /**
     * Generate comprehensive diagnostic report
     * 
     * For diagnostic app: Internal troubleshooting
     * For custom apps: User-facing bug reports
     */
    fun generateReport(
        includePerformanceMetrics: Boolean = true,
        includeLogs: Boolean = true,
        includeDeviceInfo: Boolean = true
    ): DiagnosticReport {
        return DiagnosticReport(
            timestamp = Instant.now(),
            deviceInfo = if (includeDeviceInfo) collectDeviceInfo() else null,
            performanceMetrics = if (includePerformanceMetrics) {
                collectPerformanceMetrics()
            } else null,
            detectedIssues = issueDetector.detectIssues(
                scanHistory = logger.getScanHistory(),
                validationResults = logger.getValidationHistory()
            ),
            logs = if (includeLogs) logger.exportLogs() else null
        )
    }
    
    /**
     * Export report for sharing
     */
    fun exportReport(report: DiagnosticReport, format: ReportFormat): File {
        return when (format) {
            ReportFormat.JSON -> exportAsJson(report)
            ReportFormat.HTML -> exportAsHtml(report)
            ReportFormat.PDF -> exportAsPdf(report)
        }
    }
}
```

**Dependencies:** :core, :jabcode-sdk, :auth-client

**Key Features:**
- ✅ Live benchmarking using androidx.benchmark
- ✅ Automated issue detection
- ✅ Export reports (JSON, HTML, PDF)
- ✅ Performance metrics collection
- ✅ Color mode comparison

---

## Module 5: :ui-components - Reusable UI Widgets

**Purpose:** Drop-in Material Design 3 components for authentication workflows

### Components

#### 5.1 JABCode Scanner View

```kotlin
class JABCodeScannerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    
    private val scanner = JABCodeScanner(JABCodeProtocol())
    private var onAuthDataScanned: ((AuthenticationPayload) -> Unit)? = null
    
    fun setOnAuthenticationScannedListener(
        listener: (AuthenticationPayload) -> Unit
    ) {
        this.onAuthDataScanned = listener
    }
    
    fun startScanning(lifecycleOwner: LifecycleOwner) {
        scanner.startScanning(lifecycleOwner) { payload ->
            onAuthDataScanned?.invoke(payload)
        }
    }
    
    fun stopScanning() {
        scanner.stopScanning()
    }
}
```

**Usage in XML:**
```xml
<com.jabauth.mobile.ui.JABCodeScannerView
    android:id="@+id/scannerView"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

#### 5.2 Authentication Result Card

```kotlin
class AuthenticationResultCard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : MaterialCardView(context, attrs) {
    
    fun showValidationResult(result: ValidationResult) {
        binding.statusIcon.setImageResource(
            if (result.isValid) R.drawable.ic_check_circle_24
            else R.drawable.ic_error_24
        )
        
        binding.statusText.text = if (result.isValid) {
            "Valid until ${formatDate(result.validUntil)}"
        } else {
            "Validation failed: ${result.errors.joinToString()}"
        }
        
        binding.subjectText.text = result.subject
        
        setCardBackgroundColor(
            if (result.isValid) 
                context.getColor(R.color.success_light)
            else 
                context.getColor(R.color.error_light)
        )
    }
}
```

#### 5.3 Response JABCode Display

```kotlin
class ResponseJabCodeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {
    
    private val protocol = JABCodeProtocol()
    
    fun displayResponse(
        response: AuthenticationResponse,
        colorMode: Int = 8
    ) {
        val jabCode = protocol.generateResponseCode(response, colorMode)
        setImageBitmap(jabCode)
        
        // Maximize screen brightness for better scanning
        (context as? Activity)?.window?.attributes?.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        }
    }
}
```

**Dependencies:** :core, :jabcode-sdk, :auth-client

---

## Module 6: :android-integration - Platform Integration

**Purpose:** Android framework integration patterns (Services, ContentProviders, Intents)

### Components

#### 6.1 Authentication Service

```kotlin
/**
 * Background authentication service
 * 
 * Enables other apps to request validation without UI
 */
class AuthenticationService : Service() {
    
    private val validator = JwtValidator(loadPublicKeys())
    
    override fun onBind(intent: Intent): IBinder {
        return AuthenticationBinder()
    }
    
    inner class AuthenticationBinder : Binder() {
        fun validateToken(token: String): ValidationResult {
            return validator.validateToken(token)
        }
        
        fun validateCertificate(certPem: String): ValidationResult {
            return certificateValidator.validateCertificate(certPem)
        }
    }
}
```

#### 6.2 Content Provider

```kotlin
/**
 * Share validation history with other apps
 * 
 * URI: content://com.jabauth.provider/validations
 */
class AuthenticationProvider : ContentProvider() {
    
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        return when (uriMatcher.match(uri)) {
            VALIDATIONS -> queryValidationHistory()
            VALIDATION_BY_ID -> queryValidationById(uri.lastPathSegment)
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
    }
}
```

#### 6.3 Intent-based API

```kotlin
object AuthenticationIntents {
    
    const val ACTION_VALIDATE_TOKEN = "com.jabauth.VALIDATE_TOKEN"
    const val ACTION_SCAN_JABCODE = "com.jabauth.SCAN_JABCODE"
    const val EXTRA_TOKEN = "token"
    const val EXTRA_RESULT = "result"
    
    /**
     * Request token validation from diagnostic/custom app
     */
    fun createValidationIntent(token: String): Intent {
        return Intent(ACTION_VALIDATE_TOKEN).apply {
            putExtra(EXTRA_TOKEN, token)
        }
    }
}
```

**Dependencies:** :core, :jabcode-sdk, :auth-client

---

## Module 7: :storage-adapters - Secure Storage & Telemetry

**Purpose:** Secure local storage with optional diagnostic telemetry

### Components

#### 7.1 Android Keystore Adapter

```kotlin
/**
 * Secure key storage using hardware-backed Android Keystore
 * 
 * Keys NEVER leave the device
 */
class AndroidKeystoreAdapter : SecureStorageAdapter {
    
    override fun storePrivateKey(alias: String, key: PrivateKey) {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        
        val entry = KeyStore.PrivateKeyEntry(key, null)
        keyStore.setEntry(
            alias,
            entry,
            KeyProtection.Builder(KeyProperties.PURPOSE_SIGN)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationValidityDurationSeconds(30)
                .build()
        )
    }
    
    override fun getPrivateKey(alias: String): PrivateKey? {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        return keyStore.getKey(alias, null) as? PrivateKey
    }
}
```

#### 7.2 LRU Cache Adapter

```kotlin
/**
 * Local performance optimization cache
 */
class LruCacheAdapter : CacheAdapter {
    
    private val certCache = LruCache<String, X509Certificate>(50)
    private val tokenCache = LruCache<String, JwtToken>(100)
    
    override fun cacheCertificate(
        certId: String,
        cert: X509Certificate,
        ttlSeconds: Long
    ) {
        certCache.put(certId, cert)
        
        // Auto-evict after TTL
        handler.postDelayed({
            certCache.remove(certId)
        }, ttlSeconds * 1000)
    }
}
```

#### 7.3 Optional Diagnostic Telemetry

```kotlin
/**
 * DIAGNOSTIC APP ONLY - Anonymous performance metrics
 * 
 * NOT included in custom apps by default
 */
class FirebaseTelemetryAdapter : DiagnosticTelemetry {
    
    override fun logScanLatency(
        colorMode: Int,
        durationMs: Long,
        success: Boolean
    ) {
        firebaseAnalytics.logEvent("scan_performance", bundleOf(
            "color_mode" to colorMode,
            "duration_ms" to durationMs,
            "success" to success,
            "device_model" to Build.MODEL,
            "os_version" to Build.VERSION.SDK_INT
        ))
    }
    
    override fun logValidationFailure(
        errorType: ValidationError,
        context: Map<String, Any>
    ) {
        firebaseAnalytics.logEvent("validation_failure", bundleOf(
            "error_type" to errorType.name,
            "context" to context.toJson()
        ))
    }
}
```

**Dependencies:** :core

**Security Principle:** All cryptographic keys stay on-device. Telemetry is opt-in and anonymous.

---

## Bug Reporting Support

### For Custom Applications

Custom applications built with this framework inherit comprehensive bug reporting capabilities:

#### Bug Report Structure

```kotlin
data class BugReport(
    val reportId: String,
    val timestamp: Instant,
    val appVersion: String,
    
    // Device context
    val deviceInfo: DeviceInfo,
    val osVersion: String,
    val availableMemory: Long,
    
    // Application state
    val lastScanAttempts: List<ScanAttempt>,
    val lastValidationResults: List<ValidationResult>,
    val activeColorMode: Int,
    val calibrationProfileActive: Boolean,
    
    // Performance metrics
    val averageScanLatency: Double,
    val averageDecodeLatency: Double,
    val scanSuccessRate: Float,
    
    // Detected issues
    val detectedIssues: List<DetectedIssue>,
    
    // Logs (optional, user consent required)
    val diagnosticLogs: File?
)
```

#### Bug Report Generation

```kotlin
class BugReportManager(
    private val diagnosticLogger: DiagnosticLogger,
    private val reportGenerator: DiagnosticReportGenerator
) {
    /**
     * Generate bug report for custom applications
     * 
     * Privacy-preserving: No authentication data, only metadata
     */
    fun generateBugReport(
        includePerformanceData: Boolean = true,
        includeLogs: Boolean = false // Requires user consent
    ): BugReport {
        return BugReport(
            reportId = UUID.randomUUID().toString(),
            timestamp = Instant.now(),
            appVersion = BuildConfig.VERSION_NAME,
            deviceInfo = collectDeviceInfo(),
            osVersion = Build.VERSION.RELEASE,
            availableMemory = Runtime.getRuntime().freeMemory(),
            lastScanAttempts = diagnosticLogger.getRecentScans(limit = 10),
            lastValidationResults = diagnosticLogger.getRecentValidations(limit = 10),
            activeColorMode = PreferenceManager.getColorMode(),
            calibrationProfileActive = CalibrationManager.hasActiveProfile(),
            averageScanLatency = if (includePerformanceData) {
                diagnosticLogger.getAverageScanLatency()
            } else 0.0,
            detectedIssues = IssueDetector.detectIssues(
                diagnosticLogger.getScanHistory(),
                diagnosticLogger.getValidationHistory()
            ),
            diagnosticLogs = if (includeLogs) {
                diagnosticLogger.exportLogs()
            } else null
        )
    }
    
    /**
     * Export bug report for sharing with support team
     */
    fun exportBugReport(report: BugReport): File {
        val json = Gson().toJson(report)
        val file = File(context.cacheDir, "bugreport_${report.reportId}.json")
        file.writeText(json)
        return file
    }
}
```

#### Integration with Android Bug Report System

```kotlin
/**
 * Integrate with Android's built-in bug reporting
 */
class SystemBugReportIntegration {
    
    fun attachFrameworkDiagnostics() {
        // Add custom diagnostics to Android bug report
        val diagnosticReport = reportGenerator.generateReport(
            includePerformanceMetrics = true,
            includeLogs = true,
            includeDeviceInfo = true
        )
        
        // Write to external storage for adb bugreport inclusion
        val reportFile = exportBugReport(diagnosticReport)
        
        Log.i("BugReport", "Framework diagnostics: ${reportFile.absolutePath}")
    }
    
    fun triggerBugReport() {
        // Programmatically trigger Android bug report
        // (requires android.permission.DUMP)
        val intent = Intent("com.android.internal.intent.action.BUGREPORT_REQUESTED")
        context.sendBroadcast(intent)
    }
}
```

### For Diagnostic Application

The diagnostic app has enhanced reporting capabilities:

```kotlin
class DiagnosticAppReportManager : BugReportManager() {
    
    /**
     * Generate comprehensive diagnostic report
     * 
     * Includes all framework modules + live benchmark results
     */
    fun generateDiagnosticReport(): DiagnosticReport {
        return DiagnosticReport(
            bugReport = generateBugReport(
                includePerformanceData = true,
                includeLogs = true
            ),
            
            // Live benchmark results
            benchmarkResults = runLiveBenchmarks(),
            
            // All color mode performance
            colorModeMetrics = (4..128 step 2).map { colorMode ->
                ColorModeMetric(
                    colorMode = colorMode,
                    encodeLatency = benchmarkEncode(colorMode),
                    decodeLatency = benchmarkDecode(colorMode),
                    successRate = calculateSuccessRate(colorMode)
                )
            },
            
            // Framework module health
            moduleHealth = listOf(
                ModuleHealth(":core", checkCoreModule()),
                ModuleHealth(":jabcode-sdk", checkJabCodeModule()),
                ModuleHealth(":auth-client", checkAuthModule()),
                ModuleHealth(":diagnostic-engine", checkDiagnosticModule()),
                ModuleHealth(":ui-components", checkUiModule()),
                ModuleHealth(":android-integration", checkIntegrationModule()),
                ModuleHealth(":storage-adapters", checkStorageModule())
            )
        )
    }
}
```

---

## Implementation Guidance

### Modularization Strategy (Android Best Practices)

Based on Android Developer Guidelines:

1. **Separation of Concerns** - Each module has single, well-defined responsibility
2. **Unidirectional Dependencies** - Higher-level modules depend on lower-level modules only
3. **Loose Coupling** - Modules interact via interfaces, not implementations
4. **High Cohesion** - Related functionality grouped together

### Module Dependency Graph

```
:diagnostic-app ─────────────────┐
                                 ↓
:custom-apps ────────────────────┼───→ :ui-components
                                 │         ↓
                                 ├───→ :android-integration
                                 │         ↓
                                 ├───→ :diagnostic-engine
                                 │         ↓
                                 ├───→ :auth-client
                                 │         ↓
                                 ├───→ :jabcode-sdk
                                 │         ↓
                                 └───→ :storage-adapters
                                           ↓
                                       :core (foundation)
```

### Build Configuration

```kotlin
// settings.gradle.kts
include(
    ":core",
    ":jabcode-sdk",
    ":auth-client",
    ":diagnostic-engine",
    ":ui-components",
    ":android-integration",
    ":storage-adapters",
    ":diagnostic-app",
    ":sample-prescription-app",
    ":sample-contract-app"
)
```

---

## Success Criteria

### Functional Requirements

- ✅ All 7 modules compile and pass unit tests
- ✅ Diagnostic app can scan and validate JABCodes
- ✅ Custom apps can be built using framework modules
- ✅ Bug reports export successfully (JSON, HTML, PDF)
- ✅ Live benchmarks run without crashes

### Performance Requirements

- ✅ Encode 100 chars: <50ms (all color modes)
- ✅ Decode clean code: <80ms (all color modes)
- ✅ Memory: <5MB peak usage
- ✅ Battery: <1% per 100 scans

### Quality Requirements

- ✅ Test coverage >80% (C layer), >90% (platform layers)
- ✅ Zero memory leaks (verified with LeakCanary)
- ✅ Thread-safe operations
- ✅ Crash-free on target devices

---

## Migration from Legacy Spec

### What Changed

| Legacy (v1.x) | New (v2.0) | Rationale |
|---------------|------------|-----------|
| Native library only | 7-module framework | Support custom apps + diagnostics |
| No authentication logic | PKI, JWT, ABE modules | Align with JABAuth framework |
| No diagnostic tools | Live benchmarks + reporting | Enable issue diagnosis |
| Monolithic testapp | Modular architecture | Reusability and extensibility |

### What Stayed the Same

- ✅ Native C library implementation (01-native-compilation.md)
- ✅ Performance targets (02-mobile-optimizations.md)
- ✅ Test methodology (03-tdd-benchmarks.md)
- ✅ Color mode support (4-128 colors)

---

## UI/UX Design Prototypes

High-fidelity web prototypes are available for visual design reference:

**Location:** `@/swift-java-wrapper/android/ui-prototypes/`

**Available Prototypes:**
1. **diagnostic-dashboard.html** - Interactive prototype of diagnostic app UI
   - Aesthetic: "Precision Instrumentation"
   - Features: Live metrics, performance graphs, color mode cards, issue detection
   - Typography: IBM Plex Mono + Archivo
   - Color: Dark theme with electric cyan/neon green accents

2. **scanner-interface.html** - Reusable scanning UI for Module 5 (:ui-components)
   - Aesthetic: "Precision Targeting"
   - Features: Camera viewfinder, corner guides, quality indicators, result panel
   - Context variants: Healthcare (cyan), Legal (gold), IoT (purple)
   - Interactive: Auto-detect simulation, torch toggle, sliding result panel

3. **DESIGN_SYSTEM.md** - Complete design system specification
   - Color palette with Material 3 mapping
   - Typography scale (10 styles)
   - Component specifications (cards, badges, feed items)
   - Motion principles with Compose implementation examples
   - Accessibility guidelines (WCAG AA/AAA)

4. **SCANNER_COMPONENTS.md** - Scanner component specification
   - 7 reusable Composables (header, target overlay, quality indicators, etc.)
   - Context-aware theming for different app types
   - Complete Jetpack Compose implementation examples
   - Performance optimization patterns

5. **README.md** - Translation guide (Web → Android)
   - Design token mapping
   - Component implementation examples
   - Animation conversion patterns
   - Responsive layout strategies

**Usage:** Open prototypes in browser for interactive demos, then reference component specs for Android implementation details.

---

## References

1. **Android Modularization Guide** - https://developer.android.com/topic/modularization
2. **androidx.benchmark Documentation** - https://developer.android.com/topic/performance/benchmarking
3. **Android Bug Reporting** - https://developer.android.com/studio/debug/bug-report
4. **JABAuth Framework** - `/projects/business-plans/JABCodeCOA-crypto/FRAMEWORK-GENERALIZATION.md`
5. **Legacy Mobile Spec** - `00-index.md`, `01-native-compilation.md`, `02-mobile-optimizations.md`
6. **UI/UX Prototypes** - `@/swift-java-wrapper/android/ui-prototypes/` (NEW)

---

**Last Updated:** 2026-05-02  
**Status:** ✅ Active Specification  
**Next Review:** 2026-06-01
