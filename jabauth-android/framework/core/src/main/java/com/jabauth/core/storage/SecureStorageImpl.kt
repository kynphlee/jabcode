package com.jabauth.core.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Implementation of SecureStorage using Android EncryptedSharedPreferences
 * 
 * Uses AES256-GCM encryption for values and encrypted keys.
 * Backed by Android Keystore for master key protection.
 */
class SecureStorageImpl(context: Context) : SecureStorage {
    
    private val sharedPreferences: SharedPreferences
    
    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        sharedPreferences = EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
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
    
    companion object {
        private const val PREFS_FILE_NAME = "jabauth_secure_prefs"
    }
}
