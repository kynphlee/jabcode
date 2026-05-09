package com.jabauth.jabcode.camera.lifecycle

/**
 * Interface for resources that can be managed and released
 */
interface ManagedResource {
    /**
     * Unique identifier for this resource
     */
    val resourceId: String
    
    /**
     * Release/cleanup this resource
     */
    fun release()
}
