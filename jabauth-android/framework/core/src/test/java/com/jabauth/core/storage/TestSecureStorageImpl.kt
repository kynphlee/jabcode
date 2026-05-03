package com.jabauth.core.storage

import android.content.Context
import android.content.SharedPreferences

/**
 * Test implementation of SecureStorage for unit testing
 * 
 * Uses regular SharedPreferences instead of EncryptedSharedPreferences
 * since Robolectric doesn't support Android KeyStore operations.
 * 
 * Production code uses SecureStorageImpl with actual encryption.
 */
class TestSecureStorageImpl(context: Context) : SecureStorage {
    
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        "test_secure_prefs",
        Context.MODE_PRIVATE
    )
    
    override fun putString(key: String, value: String) {
        sharedPreferences.edit().putString(key, value).apply()
    }
    
    override fun getString(key: String, defaultValue: String?): String? {
        return sharedPreferences.getString(key, defaultValue)
    }
    
    override fun putInt(key: String, value: Int) {
        sharedPreferences.edit().putInt(key, value).apply()
    }
    
    override fun getInt(key: String, defaultValue: Int): Int {
        return sharedPreferences.getInt(key, defaultValue)
    }
    
    override fun putBoolean(key: String, value: Boolean) {
        sharedPreferences.edit().putBoolean(key, value).apply()
    }
    
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return sharedPreferences.getBoolean(key, defaultValue)
    }
    
    override fun remove(key: String) {
        sharedPreferences.edit().remove(key).apply()
    }
    
    override fun clear() {
        sharedPreferences.edit().clear().apply()
    }
    
    override fun contains(key: String): Boolean {
        return sharedPreferences.contains(key)
    }
}
