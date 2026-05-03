package com.jabauth.core.storage

/**
 * Interface for secure encrypted storage
 * 
 * Provides encrypted key-value storage for sensitive data using
 * Android EncryptedSharedPreferences with AES256-GCM encryption.
 */
interface SecureStorage {
    
    /**
     * Store a string value
     * @param key Storage key
     * @param value String value to store
     */
    fun putString(key: String, value: String)
    
    /**
     * Retrieve a string value
     * @param key Storage key
     * @param defaultValue Optional default if key doesn't exist
     * @return Stored value or default/null
     */
    fun getString(key: String, defaultValue: String? = null): String?
    
    /**
     * Store an integer value
     * @param key Storage key
     * @param value Integer value to store
     */
    fun putInt(key: String, value: Int)
    
    /**
     * Retrieve an integer value
     * @param key Storage key
     * @param defaultValue Default if key doesn't exist
     * @return Stored value or default
     */
    fun getInt(key: String, defaultValue: Int): Int
    
    /**
     * Store a boolean value
     * @param key Storage key
     * @param value Boolean value to store
     */
    fun putBoolean(key: String, value: Boolean)
    
    /**
     * Retrieve a boolean value
     * @param key Storage key
     * @param defaultValue Default if key doesn't exist
     * @return Stored value or default
     */
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    
    /**
     * Remove a key-value pair
     * @param key Key to remove
     */
    fun remove(key: String)
    
    /**
     * Clear all stored data
     */
    fun clear()
    
    /**
     * Check if key exists
     * @param key Key to check
     * @return True if key exists
     */
    fun contains(key: String): Boolean
}
