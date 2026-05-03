# JABAuth Framework - Migration Guide

**Version:** 1.0.0  
**Last Updated:** 2026-05-03  
**Target Audience:** Developers migrating from monolithic JABAuth app to modular framework

---

## Overview

This guide helps you migrate from the monolithic JABAuth Android application to the new modular framework architecture. The framework provides:

- **Testability:** All components are interface-based and mockable
- **Maintainability:** Clear module boundaries and separation of concerns
- **Reusability:** Core utilities shared across multiple apps
- **Security:** Encrypted storage and secure-by-default APIs

---

## Architecture Changes

### **Before: Monolithic Structure**
```
jabauth-app/
├── activities/
│   ├── ScannerActivity.java
│   ├── ResultActivity.java
│   └── SettingsActivity.java
├── utils/
│   ├── PreferenceHelper.java
│   ├── LogHelper.java
│   └── CertificateHelper.java
└── native/
    └── JABCodeWrapper.java
```

### **After: Modular Framework**
```
jabauth-android/
├── framework/
│   ├── core/                    # Foundation utilities
│   ├── jabcode-sdk/             # JABCode wrapper
│   ├── jabauth-client/          # Authentication logic
│   ├── diagnostic-engine/       # Diagnostic system
│   └── ui-components/           # Reusable UI
└── apps/
    ├── diagnostic-app/          # Diagnostic application
    └── production-app/          # End-user app (future)
```

---

## Migration Steps

### **1. Secure Storage Migration**

#### **Before (Monolithic)**
```java
// ScannerActivity.java
SharedPreferences prefs = getSharedPreferences("jabauth_prefs", MODE_PRIVATE);
prefs.edit().putString("last_scan", scanData).apply();
String lastScan = prefs.getString("last_scan", null);
```

**Issues:**
- ❌ No encryption (sensitive data exposed)
- ❌ Not testable (requires Android Context)
- ❌ No error handling
- ❌ Magic strings ("jabauth_prefs", "last_scan")

#### **After (Framework)**
```kotlin
// ScannerViewModel.kt
class ScannerViewModel(
    private val storage: SecureStorage
) : ViewModel() {
    
    companion object {
        private const val KEY_LAST_SCAN = "last_scan"
    }
    
    fun saveLastScan(scanData: String) {
        storage.putString(KEY_LAST_SCAN, scanData)
    }
    
    fun getLastScan(): String? {
        return storage.getString(KEY_LAST_SCAN)
    }
}
```

**Benefits:**
- ✅ **Encrypted:** Uses EncryptedSharedPreferences
- ✅ **Testable:** Mock `SecureStorage` in tests
- ✅ **Type-safe:** No magic strings in business logic
- ✅ **Error handling:** Built-in validation

#### **Testing Example**
```kotlin
@Test
fun `saveLastScan stores data successfully`() {
    val mockStorage = mock<SecureStorage>()
    val viewModel = ScannerViewModel(mockStorage)
    
    viewModel.saveLastScan("test_data")
    
    verify(mockStorage).putString("last_scan", "test_data")
}
```

---

### **2. Logging Migration**

#### **Before (Monolithic)**
```java
// LogHelper.java
public class LogHelper {
    private static final String TAG = "JABAuth";
    
    public static void logScan(String data) {
        Log.d(TAG, "Scan complete: " + data);
    }
    
    public static void logError(String message, Exception e) {
        Log.e(TAG, message, e);
    }
}

// Usage
LogHelper.logScan(scanData);
LogHelper.logError("Scan failed", exception);
```

**Issues:**
- ❌ Global state (static methods)
- ❌ No structured logging
- ❌ Cannot filter by component
- ❌ Not testable

#### **After (Framework)**
```kotlin
// ScannerViewModel.kt
class ScannerViewModel(
    private val logger: Logger
) : ViewModel() {
    
    private val scanLogger = logger.withTag("Scanner")
    
    fun processScan(data: String) {
        scanLogger.info("Processing scan", mapOf(
            "dataLength" to data.length,
            "timestamp" to System.currentTimeMillis()
        ))
        
        try {
            // Process scan
            scanLogger.debug("Scan successful", mapOf(
                "result" to result
            ))
        } catch (e: Exception) {
            scanLogger.error("Scan processing failed", e, mapOf(
                "dataLength" to data.length
            ))
        }
    }
}
```

**Benefits:**
- ✅ **Structured:** Key-value metadata for log aggregation
- ✅ **Tagged:** Easy filtering by component
- ✅ **Testable:** Mock logger in tests
- ✅ **Performance:** `isDebugEnabled()` check

#### **Testing Example**
```kotlin
@Test
fun `processScan logs success with metadata`() {
    val mockLogger = TestLoggerImpl()
    val viewModel = ScannerViewModel(mockLogger.withTag("Scanner"))
    
    viewModel.processScan("test_data")
    
    val logs = mockLogger.getLogs()
    assertTrue(logs.any { it.message.contains("Processing scan") })
}
```

---

### **3. Certificate Validation Migration**

#### **Before (Monolithic)**
```java
// CertificateHelper.java
public static boolean validateCertificate(byte[] certBytes) {
    try {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) cf.generateCertificate(
            new ByteArrayInputStream(certBytes)
        );
        cert.checkValidity();
        return true;
    } catch (Exception e) {
        Log.e("JABAuth", "Certificate validation failed", e);
        return false;
    }
}
```

**Issues:**
- ❌ Boolean return (no error details)
- ❌ Logs directly (tight coupling)
- ❌ Not reusable (static method)
- ❌ No DN extraction

#### **After (Framework)**
```kotlin
// AuthenticationViewModel.kt
class AuthenticationViewModel(
    private val certValidator: CertificateValidator,
    private val logger: Logger
) : ViewModel() {
    
    fun validateCertificate(certBytes: ByteArray): ValidationResult {
        val result = certValidator.validateFormat(certBytes)
        
        if (!result.isValid) {
            logger.error("Certificate validation failed", mapOf(
                "error" to result.errorMessage,
                "code" to result.errorCode
            ))
            return result
        }
        
        val cert = certValidator.parseCertificate(certBytes)
        
        if (!certValidator.isNotExpired(cert)) {
            logger.warn("Certificate expired", mapOf(
                "subject" to certValidator.getSubjectDN(cert),
                "issuer" to certValidator.getIssuerDN(cert)
            ))
            return ValidationResult.failure("Certificate expired", "CERT_EXPIRED")
        }
        
        logger.info("Certificate validated", mapOf(
            "subject" to certValidator.getSubjectDN(cert),
            "isSelfSigned" to certValidator.isSelfSigned(cert)
        ))
        
        return ValidationResult.success()
    }
}
```

**Benefits:**
- ✅ **Rich errors:** Machine-readable error codes
- ✅ **Metadata extraction:** DN, issuer, self-signed status
- ✅ **Testable:** Mock validator
- ✅ **Composable:** Combine with JWT validation

---

### **4. JWT Validation Migration**

#### **Before (Monolithic)**
```java
// TokenHelper.java
public static boolean isValidToken(String token) {
    String[] parts = token.split("\\.");
    if (parts.length != 3) return false;
    
    try {
        String payload = new String(Base64.decode(parts[1], Base64.DEFAULT));
        JSONObject json = new JSONObject(payload);
        long exp = json.getLong("exp");
        return System.currentTimeMillis() / 1000 < exp;
    } catch (Exception e) {
        return false;
    }
}
```

**Issues:**
- ❌ Boolean return (no error details)
- ❌ No claims extraction
- ❌ Limited validation (only expiration)

#### **After (Framework)**
```kotlin
// AuthenticationViewModel.kt
fun validateJWT(token: String): ValidationResult {
    val result = jwtValidator.validateFormat(token)
    if (!result.isValid) {
        logger.error("JWT format invalid", mapOf(
            "error" to result.errorMessage,
            "code" to result.errorCode
        ))
        return result
    }
    
    val claims = jwtValidator.extractClaims(token)
    
    // Check expiration
    if (jwtValidator.isExpired(claims)) {
        logger.warn("JWT expired", mapOf(
            "issuer" to jwtValidator.getIssuer(claims),
            "subject" to jwtValidator.getSubject(claims)
        ))
        return ValidationResult.failure("JWT expired", "JWT_EXPIRED")
    }
    
    // Validate required claims
    val requiredClaims = listOf("sub", "iss", "exp", "aud")
    val claimsResult = jwtValidator.hasRequiredClaims(claims, requiredClaims)
    if (!claimsResult.isValid) {
        logger.error("Missing required JWT claims", mapOf(
            "error" to claimsResult.errorMessage
        ))
        return claimsResult
    }
    
    logger.info("JWT validated", mapOf(
        "issuer" to jwtValidator.getIssuer(claims),
        "subject" to jwtValidator.getSubject(claims)
    ))
    
    return ValidationResult.success()
}
```

**Benefits:**
- ✅ **Comprehensive validation:** Format + claims + expiration
- ✅ **Claims extraction:** Access issuer, subject, custom claims
- ✅ **Required claims:** Enforce mandatory fields
- ✅ **Testable:** Mock validator

---

## Dependency Injection Setup

### **Hilt Configuration**

```kotlin
// Module: framework/core/src/main/java/com/jabauth/core/di/CoreModule.kt
@Module
@InstallIn(SingletonComponent::class)
object CoreModule {
    
    @Provides
    @Singleton
    fun provideSecureStorage(
        @ApplicationContext context: Context
    ): SecureStorage {
        return SecureStorageImpl(context)
    }
    
    @Provides
    @Singleton
    fun provideLogger(): Logger {
        return if (BuildConfig.DEBUG) {
            LoggerImpl.createDebugLogger()
        } else {
            LoggerImpl.createProductionLogger()
        }
    }
    
    @Provides
    @Singleton
    fun provideCertificateValidator(): CertificateValidator {
        return CertificateValidatorImpl()
    }
    
    @Provides
    @Singleton
    fun provideJWTValidator(): JWTValidator {
        return JWTValidatorImpl()
    }
}
```

### **ViewModel Injection**

```kotlin
@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val storage: SecureStorage,
    private val logger: Logger,
    private val certValidator: CertificateValidator,
    private val jwtValidator: JWTValidator
) : ViewModel() {
    // Use injected dependencies
}
```

---

## Testing Strategy

### **Unit Tests (Fast, No Android)**
```kotlin
class ScannerViewModelTest {
    
    private lateinit var mockStorage: SecureStorage
    private lateinit var mockLogger: Logger
    private lateinit var viewModel: ScannerViewModel
    
    @Before
    fun setup() {
        mockStorage = TestSecureStorageImpl(ApplicationProvider.getApplicationContext())
        mockLogger = TestLoggerImpl()
        viewModel = ScannerViewModel(mockStorage, mockLogger)
    }
    
    @Test
    fun `saveLastScan stores data successfully`() {
        viewModel.saveLastScan("test_data")
        
        val stored = mockStorage.getString("last_scan")
        assertEquals("test_data", stored)
    }
}
```

### **Instrumented Tests (Real Android)**
```kotlin
@HiltAndroidTest
class SecureStorageInstrumentedTest {
    
    @get:Rule
    val hiltRule = HiltAndroidRule(this)
    
    @Inject
    lateinit var storage: SecureStorage
    
    @Test
    fun testEncryption() {
        // Test with real EncryptedSharedPreferences
        storage.putString("secret_key", "secret_value")
        val retrieved = storage.getString("secret_key")
        assertEquals("secret_value", retrieved)
    }
}
```

---

## Migration Checklist

### **Phase 1: Framework Core**
- [x] Replace `SharedPreferences` with `SecureStorage`
- [x] Replace direct logging with `Logger` interface
- [x] Replace certificate validation with `CertificateValidator`
- [x] Replace JWT parsing with `JWTValidator`
- [x] Set up Hilt dependency injection
- [ ] Update ViewModels to use injected dependencies
- [ ] Write unit tests for all ViewModels
- [ ] Write instrumented tests for storage

### **Phase 2: JABCode SDK**
- [ ] Migrate native JNI calls to `:jabcode-sdk` module
- [ ] Add calibration and performance tracking
- [ ] Write unit tests for SDK wrapper

### **Phase 3: Authentication Client**
- [ ] Extract authentication logic to `:jabauth-client`
- [ ] Implement PKI certificate chain validation
- [ ] Implement JWT signature verification
- [ ] Write integration tests

### **Phase 4: UI Migration**
- [ ] Migrate activities to `:ui-components` module
- [ ] Create reusable composables
- [ ] Implement Material 3 design system

---

## Common Pitfalls

### **1. Forgetting to Inject Dependencies**
❌ **Wrong:**
```kotlin
class MyViewModel : ViewModel() {
    private val storage = SecureStorageImpl(context) // Where does context come from?
}
```

✅ **Correct:**
```kotlin
@HiltViewModel
class MyViewModel @Inject constructor(
    private val storage: SecureStorage
) : ViewModel()
```

### **2. Testing with Production Implementations**
❌ **Wrong:**
```kotlin
@Test
fun test() {
    val storage = SecureStorageImpl(context) // Requires Android KeyStore
    // Test will fail in unit tests
}
```

✅ **Correct:**
```kotlin
@Test
fun test() {
    val storage = TestSecureStorageImpl(context) // Works in unit tests
    // Test passes
}
```

### **3. Not Using Structured Logging**
❌ **Wrong:**
```kotlin
logger.info("User logged in, id=123, role=admin")
```

✅ **Correct:**
```kotlin
logger.info("User logged in", mapOf(
    "userId" to 123,
    "role" to "admin"
))
```

---

## Performance Considerations

### **Secure Storage**
- Operations are ~5ms (encrypted) vs ~1ms (plain SharedPreferences)
- Acceptable for infrequent operations (login, settings)
- Avoid in tight loops or high-frequency updates

### **Logging**
- Use `logger.isDebugEnabled()` before expensive string construction
- Structured metadata has minimal overhead (~0.1ms)
- Production builds disable debug logs automatically

### **Validation**
- Certificate parsing: ~10-50ms (acceptable for auth flow)
- JWT validation: ~1-5ms (very fast, no signature verification yet)

---

## Support & Resources

- **Documentation:** `jabauth-android/docs/`
- **Examples:** `jabauth-android/examples/`
- **API Reference:** `jabauth-android/build/dokka/`
- **Issues:** GitHub Issues

---

**Migration Support:** Open an issue with tag `migration` for help with specific use cases.

**Version:** 1.0.0  
**Last Updated:** 2026-05-03
