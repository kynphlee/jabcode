package com.jabauth.core.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Test suite for SecureStorage implementation
 * 
 * Tests key-value storage contract and behavior.
 * Uses TestSecureStorageImpl for unit tests (plain SharedPreferences).
 * Production uses SecureStorageImpl with EncryptedSharedPreferences.
 * Coverage Target: 80%+
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecureStorageTest {
    
    private lateinit var context: Context
    private lateinit var storage: SecureStorage
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        storage = TestSecureStorageImpl(context)
    }
    
    @Test
    fun `putString stores value successfully`() {
        // Arrange
        val key = "test_key"
        val value = "test_value"
        
        // Act
        storage.putString(key, value)
        
        // Assert
        val retrieved = storage.getString(key)
        assertThat(retrieved).isEqualTo(value)
    }
    
    @Test
    fun `getString returns null for non-existent key`() {
        // Arrange
        val key = "non_existent_key"
        
        // Act
        val result = storage.getString(key)
        
        // Assert
        assertThat(result).isNull()
    }
    
    @Test
    fun `getString with default returns default for non-existent key`() {
        // Arrange
        val key = "non_existent_key"
        val default = "default_value"
        
        // Act
        val result = storage.getString(key, default)
        
        // Assert
        assertThat(result).isEqualTo(default)
    }
    
    @Test
    fun `putInt stores and retrieves integer`() {
        // Arrange
        val key = "int_key"
        val value = 42
        
        // Act
        storage.putInt(key, value)
        
        // Assert
        val retrieved = storage.getInt(key, 0)
        assertThat(retrieved).isEqualTo(value)
    }
    
    @Test
    fun `putBoolean stores and retrieves boolean`() {
        // Arrange
        val key = "bool_key"
        val value = true
        
        // Act
        storage.putBoolean(key, value)
        
        // Assert
        val retrieved = storage.getBoolean(key, false)
        assertThat(retrieved).isEqualTo(value)
    }
    
    @Test
    fun `remove deletes key-value pair`() {
        // Arrange
        val key = "key_to_remove"
        val value = "value"
        storage.putString(key, value)
        
        // Act
        storage.remove(key)
        
        // Assert
        val retrieved = storage.getString(key)
        assertThat(retrieved).isNull()
    }
    
    @Test
    fun `clear removes all key-value pairs`() {
        // Arrange
        storage.putString("key1", "value1")
        storage.putString("key2", "value2")
        storage.putInt("key3", 123)
        
        // Act
        storage.clear()
        
        // Assert
        assertThat(storage.getString("key1")).isNull()
        assertThat(storage.getString("key2")).isNull()
        assertThat(storage.getInt("key3", -1)).isEqualTo(-1)
    }
    
    @Test
    fun `contains returns true for existing key`() {
        // Arrange
        val key = "existing_key"
        storage.putString(key, "value")
        
        // Act
        val result = storage.contains(key)
        
        // Assert
        assertThat(result).isTrue()
    }
    
    @Test
    fun `contains returns false for non-existent key`() {
        // Arrange
        val key = "non_existent_key"
        
        // Act
        val result = storage.contains(key)
        
        // Assert
        assertThat(result).isFalse()
    }
    
    @Test
    fun `putString with empty value stores successfully`() {
        // Arrange
        val key = "empty_key"
        val value = ""
        
        // Act
        storage.putString(key, value)
        
        // Assert
        val retrieved = storage.getString(key)
        assertThat(retrieved).isEqualTo(value)
    }
    
    @Test
    fun `multiple operations maintain data integrity`() {
        // Arrange & Act
        storage.putString("str_key", "string_value")
        storage.putInt("int_key", 100)
        storage.putBoolean("bool_key", true)
        storage.putString("str_key2", "another_string")
        storage.remove("int_key")
        
        // Assert
        assertThat(storage.getString("str_key")).isEqualTo("string_value")
        assertThat(storage.getInt("int_key", -1)).isEqualTo(-1)
        assertThat(storage.getBoolean("bool_key", false)).isTrue()
        assertThat(storage.getString("str_key2")).isEqualTo("another_string")
    }
}
