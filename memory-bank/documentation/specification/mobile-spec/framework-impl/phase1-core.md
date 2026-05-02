# Phase 1: :core Module - Foundation Layer

**Duration:** 1 week (5 working days)  
**Dependencies:** None  
**Status:** ⬜ Not Started

---

## Overview

The `:core` module provides foundational utilities used by all other modules:
- Secure storage abstraction
- Structured logging system
- Data validation utilities
- Network client wrapper

**Key Goal:** Create a stable, well-tested foundation with 80%+ coverage.

---

## Day 1-2: Project Setup & Secure Storage

### **Deliverables**
1. Gradle module configuration
2. Package structure
3. SecureStorage interface + implementations
4. 8 unit tests for storage

### **Implementation**

#### **1.1 Module Setup**

```kotlin
// :core/build.gradle.kts
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("jacoco")
}

android {
    namespace = "com.jabauth.core"
    compileSdk = 35
    
    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    // AndroidX
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.5.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    testImplementation("org.robolectric:robolectric:4.11.1")
    
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
}

// JaCoCo configuration
tasks.withType<Test> {
    configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")
    
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    
    sourceDirectories.setFrom(files("src/main/java"))
    classDirectories.setFrom(files("build/tmp/kotlin-classes/debug"))
    executionData.setFrom(files("build/jacoco/testDebugUnitTest.exec"))
}
```

#### **1.2 Package Structure**

```
:core/src/main/java/com/jabauth/core/
├── storage/
│   ├── SecureStorage.kt           // Interface
│   ├── SharedPreferencesStorage.kt
│   ├── EncryptedFileStorage.kt
│   └── StorageFactory.kt
├── logging/
│   ├── Logger.kt                  // Interface
│   ├── AndroidLogger.kt
│   └── FileLogger.kt
├── validation/
│   ├── CertificateValidator.kt
│   └── JWTValidator.kt
└── network/
    └── HttpClient.kt
```

#### **1.3 SecureStorage Interface**

```kotlin
// storage/SecureStorage.kt
package com.jabauth.core.storage

interface SecureStorage {
    /**
     * Store a key-value pair securely
     * @param key Storage key
     * @param value String value to store
     * @return true if successful
     */
    fun put(key: String, value: String): Boolean
    
    /**
     * Retrieve a value by key
     * @param key Storage key
     * @param defaultValue Value to return if key not found
     * @return Stored value or defaultValue
     */
    fun get(key: String, defaultValue: String? = null): String?
    
    /**
     * Remove a key-value pair
     * @param key Storage key
     * @return true if successful
     */
    fun remove(key: String): Boolean
    
    /**
     * Clear all stored data
     * @return true if successful
     */
    fun clear(): Boolean
    
    /**
     * Check if key exists
     * @param key Storage key
     * @return true if key exists
     */
    fun contains(key: String): Boolean
}
```

#### **1.4 Unit Tests (TDD Approach)**

Write tests **before** implementation:

```kotlin
// :core/src/test/java/com/jabauth/core/storage/SharedPreferencesStorageTest.kt
package com.jabauth.core.storage

import android.content.Context
import android.content.SharedPreferences
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.kotlin.*
import org.mockito.junit.MockitoJUnitRunner
import org.junit.Assert.*

@RunWith(MockitoJUnitRunner::class)
class SharedPreferencesStorageTest {
    
    @Mock
    private lateinit var context: Context
    
    @Mock
    private lateinit var sharedPreferences: SharedPreferences
    
    @Mock
    private lateinit var editor: SharedPreferences.Editor
    
    private lateinit var storage: SharedPreferencesStorage
    
    @Before
    fun setup() {
        whenever(context.getSharedPreferences(any(), any())).thenReturn(sharedPreferences)
        whenever(sharedPreferences.edit()).thenReturn(editor)
        whenever(editor.putString(any(), any())).thenReturn(editor)
        whenever(editor.remove(any())).thenReturn(editor)
        whenever(editor.clear()).thenReturn(editor)
        whenever(editor.commit()).thenReturn(true)
        
        storage = SharedPreferencesStorage(context)
    }
    
    @Test
    fun `put stores value successfully`() {
        // Given
        val key = "test_key"
        val value = "test_value"
        
        // When
        val result = storage.put(key, value)
        
        // Then
        assertTrue(result)
        verify(editor).putString(key, value)
        verify(editor).commit()
    }
    
    @Test
    fun `get retrieves stored value`() {
        // Given
        val key = "test_key"
        val value = "test_value"
        whenever(sharedPreferences.getString(key, null)).thenReturn(value)
        
        // When
        val result = storage.get(key)
        
        // Then
        assertEquals(value, result)
    }
    
    @Test
    fun `get returns default value when key not found`() {
        // Given
        val key = "missing_key"
        val defaultValue = "default"
        whenever(sharedPreferences.getString(key, defaultValue)).thenReturn(defaultValue)
        
        // When
        val result = storage.get(key, defaultValue)
        
        // Then
        assertEquals(defaultValue, result)
    }
    
    @Test
    fun `remove deletes key successfully`() {
        // Given
        val key = "test_key"
        
        // When
        val result = storage.remove(key)
        
        // Then
        assertTrue(result)
        verify(editor).remove(key)
        verify(editor).commit()
    }
    
    @Test
    fun `clear removes all data`() {
        // When
        val result = storage.clear()
        
        // Then
        assertTrue(result)
        verify(editor).clear()
        verify(editor).commit()
    }
    
    @Test
    fun `contains returns true when key exists`() {
        // Given
        val key = "test_key"
        whenever(sharedPreferences.contains(key)).thenReturn(true)
        
        // When
        val result = storage.contains(key)
        
        // Then
        assertTrue(result)
    }
    
    @Test
    fun `contains returns false when key does not exist`() {
        // Given
        val key = "missing_key"
        whenever(sharedPreferences.contains(key)).thenReturn(false)
        
        // When
        val result = storage.contains(key)
        
        // Then
        assertFalse(result)
    }
    
    @Test
    fun `put handles commit failure gracefully`() {
        // Given
        whenever(editor.commit()).thenReturn(false)
        
        // When
        val result = storage.put("key", "value")
        
        // Then
        assertFalse(result)
    }
}
```

#### **1.5 Implementation**

After tests are written, implement to pass tests:

```kotlin
// storage/SharedPreferencesStorage.kt
package com.jabauth.core.storage

import android.content.Context
import android.content.SharedPreferences

class SharedPreferencesStorage(
    context: Context,
    private val prefsName: String = "jabauth_secure_storage"
) : SecureStorage {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        prefsName,
        Context.MODE_PRIVATE
    )
    
    override fun put(key: String, value: String): Boolean {
        return try {
            prefs.edit().putString(key, value).commit()
        } catch (e: Exception) {
            false
        }
    }
    
    override fun get(key: String, defaultValue: String?): String? {
        return try {
            prefs.getString(key, defaultValue)
        } catch (e: Exception) {
            defaultValue
        }
    }
    
    override fun remove(key: String): Boolean {
        return try {
            prefs.edit().remove(key).commit()
        } catch (e: Exception) {
            false
        }
    }
    
    override fun clear(): Boolean {
        return try {
            prefs.edit().clear().commit()
        } catch (e: Exception) {
            false
        }
    }
    
    override fun contains(key: String): Boolean {
        return prefs.contains(key)
    }
}
```

### **Day 1-2 Completion Checklist**
- [ ] Module created and builds successfully
- [ ] 8 unit tests written for SharedPreferencesStorage
- [ ] All tests pass
- [ ] Coverage ≥ 80% for storage package
- [ ] Code reviewed for quality

---

## Day 3: Logging System

### **Deliverables**
1. Logger interface
2. AndroidLogger implementation
3. FileLogger implementation
4. 5 unit tests

### **Implementation**

#### **2.1 Logger Interface**

```kotlin
// logging/Logger.kt
package com.jabauth.core.logging

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

interface Logger {
    fun debug(tag: String, message: String, throwable: Throwable? = null)
    fun info(tag: String, message: String, throwable: Throwable? = null)
    fun warn(tag: String, message: String, throwable: Throwable? = null)
    fun error(tag: String, message: String, throwable: Throwable? = null)
    fun setMinLevel(level: LogLevel)
}
```

#### **2.2 Unit Tests**

```kotlin
// :core/src/test/java/com/jabauth/core/logging/AndroidLoggerTest.kt
package com.jabauth.core.logging

import android.util.Log
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
class AndroidLoggerTest {
    
    private lateinit var logger: AndroidLogger
    
    @Before
    fun setup() {
        ShadowLog.reset()
        logger = AndroidLogger()
    }
    
    @Test
    fun `debug logs at DEBUG level`() {
        // When
        logger.debug("TEST_TAG", "Debug message")
        
        // Then
        val logs = ShadowLog.getLogsForTag("TEST_TAG")
        assertTrue(logs.any { it.msg == "Debug message" && it.type == Log.DEBUG })
    }
    
    @Test
    fun `info logs at INFO level`() {
        // When
        logger.info("TEST_TAG", "Info message")
        
        // Then
        val logs = ShadowLog.getLogsForTag("TEST_TAG")
        assertTrue(logs.any { it.msg == "Info message" && it.type == Log.INFO })
    }
    
    @Test
    fun `warn logs at WARN level`() {
        // When
        logger.warn("TEST_TAG", "Warning message")
        
        // Then
        val logs = ShadowLog.getLogsForTag("TEST_TAG")
        assertTrue(logs.any { it.msg == "Warning message" && it.type == Log.WARN })
    }
    
    @Test
    fun `error logs at ERROR level`() {
        // When
        logger.error("TEST_TAG", "Error message")
        
        // Then
        val logs = ShadowLog.getLogsForTag("TEST_TAG")
        assertTrue(logs.any { it.msg == "Error message" && it.type == Log.ERROR })
    }
    
    @Test
    fun `setMinLevel filters lower priority logs`() {
        // Given
        logger.setMinLevel(LogLevel.ERROR)
        
        // When
        logger.debug("TEST_TAG", "Debug message")
        logger.info("TEST_TAG", "Info message")
        logger.warn("TEST_TAG", "Warning message")
        logger.error("TEST_TAG", "Error message")
        
        // Then
        val logs = ShadowLog.getLogsForTag("TEST_TAG")
        assertEquals(1, logs.size)
        assertEquals("Error message", logs.first().msg)
    }
}
```

### **Day 3 Completion Checklist**
- [ ] Logger interface defined
- [ ] AndroidLogger implemented
- [ ] 5 unit tests pass
- [ ] Coverage ≥ 80% for logging package

---

## Day 4: Data Validation

### **Deliverables**
1. CertificateValidator interface + implementation
2. JWTValidator interface + implementation
3. 12 unit tests (6 cert + 6 JWT)

### **Implementation**

#### **3.1 CertificateValidator**

```kotlin
// validation/CertificateValidator.kt
package com.jabauth.core.validation

import java.security.cert.X509Certificate

interface CertificateValidator {
    /**
     * Validate X.509 certificate format and basic properties
     * @param certificate DER-encoded certificate bytes
     * @return ValidationResult with success/failure and error message
     */
    fun validateFormat(certificate: ByteArray): ValidationResult
    
    /**
     * Check if certificate is expired
     * @param certificate X509Certificate to check
     * @return true if valid (not expired)
     */
    fun isNotExpired(certificate: X509Certificate): Boolean
    
    /**
     * Verify certificate signature
     * @param certificate Certificate to verify
     * @param issuerCertificate Issuer's certificate
     * @return true if signature is valid
     */
    fun verifySignature(certificate: X509Certificate, issuerCertificate: X509Certificate): Boolean
}

data class ValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
)
```

#### **3.2 Unit Tests**

```kotlin
// :core/src/test/java/com/jabauth/core/validation/CertificateValidatorTest.kt
package com.jabauth.core.validation

import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.security.cert.X509Certificate
import java.security.cert.CertificateFactory
import java.io.ByteArrayInputStream

class CertificateValidatorTest {
    
    private lateinit var validator: CertificateValidatorImpl
    
    @Before
    fun setup() {
        validator = CertificateValidatorImpl()
    }
    
    @Test
    fun `validateFormat accepts valid X509 certificate`() {
        // Given
        val validCert = createTestCertificate()
        
        // When
        val result = validator.validateFormat(validCert.encoded)
        
        // Then
        assertTrue(result.isValid)
        assertNull(result.errorMessage)
    }
    
    @Test
    fun `validateFormat rejects invalid certificate data`() {
        // Given
        val invalidData = "not a certificate".toByteArray()
        
        // When
        val result = validator.validateFormat(invalidData)
        
        // Then
        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
    }
    
    @Test
    fun `isNotExpired returns true for valid certificate`() {
        // Given
        val validCert = createTestCertificate()
        
        // When
        val result = validator.isNotExpired(validCert)
        
        // Then
        assertTrue(result)
    }
    
    // ... 3 more certificate tests
    
    private fun createTestCertificate(): X509Certificate {
        // Test certificate generation logic
        // (Use pre-generated test cert or create self-signed)
    }
}
```

### **Day 4 Completion Checklist**
- [ ] CertificateValidator interface + impl
- [ ] JWTValidator interface + impl
- [ ] 12 unit tests pass
- [ ] Coverage ≥ 80% for validation package

---

## Day 5: Phase Completion & Testing

### **Final Tasks**

#### **5.1 Run Test Coverage Workflow**

```bash
# Clean build
./gradlew :core:clean

# Run all tests
./gradlew :core:testDebugUnitTest

# Generate coverage report
./gradlew :core:jacocoTestReport

# View report
open core/build/reports/jacoco/test/html/index.html
```

**Expected Output:**
```
Package Coverage:
- com.jabauth.core.storage   → 85%
- com.jabauth.core.logging   → 82%
- com.jabauth.core.validation → 81%
Overall: 83% ✅
```

#### **5.2 Fix Test Failures**

If coverage < 80%:
1. Identify untested code paths
2. Add missing test cases
3. Re-run coverage
4. Repeat until ≥ 80%

#### **5.3 Documentation**

```kotlin
// Example KDoc
/**
 * Secure storage abstraction for JABAuth framework.
 * 
 * Provides encrypted storage of sensitive data using Android's
 * EncryptedSharedPreferences or EncryptedFile depending on data size.
 * 
 * Usage:
 * ```kotlin
 * val storage = StorageFactory.create(context)
 * storage.put("api_key", "secret_key_value")
 * val value = storage.get("api_key")
 * ```
 * 
 * @see SharedPreferencesStorage for small key-value pairs
 * @see EncryptedFileStorage for large data (>1KB)
 */
interface SecureStorage { ... }
```

#### **5.4 Migration Guide**

Create `MIGRATION.md`:

```markdown
# Migrating from Monolithic App to :core Module

## Before (Monolithic)
```kotlin
// ScannerActivity.java
SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
prefs.edit().putString("last_scan", data).apply();
```

## After (Using :core)
```kotlin
// ScannerViewModel.kt
class ScannerViewModel(
    private val storage: SecureStorage
) : ViewModel() {
    fun saveLastScan(data: String) {
        storage.put("last_scan", data)
    }
}
```

## Benefits
- ✅ Testable (can mock SecureStorage)
- ✅ Secure (encrypted by default)
- ✅ Consistent error handling
- ✅ Type-safe APIs
```

### **Day 5 Completion Checklist**
- [ ] All 25 tests pass
- [ ] Coverage ≥ 80%
- [ ] KDoc complete for public APIs
- [ ] Migration guide written
- [ ] Git tag: `v1.0.0-phase1`

---

## Success Criteria

**Code Quality:**
- ✅ 25 unit tests pass
- ✅ 80%+ code coverage
- ✅ Zero critical bugs
- ✅ All public APIs documented

**Functionality:**
- ✅ SecureStorage works on Android 7.0+
- ✅ Logger writes to logcat and file
- ✅ Validators handle malformed input gracefully

**Performance:**
- ✅ Storage operations < 10ms
- ✅ Logging overhead < 1ms per call

---

## Next Phase

**Phase 2: :jabcode-sdk Module**
- Depends on `:core` for logging and storage
- Wraps native JABCode library
- Adds calibration and performance tracking

---

**Last Updated:** 2026-05-02  
**Status:** ⬜ Ready to Start
