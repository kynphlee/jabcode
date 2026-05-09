package com.jabauth.jabcode.camera.lifecycle

/**
 * Manages camera-related resources and coordinates cleanup
 * 
 * Tracks registered resources and ensures proper release
 */
class ResourceManager {
    
    private val resources = mutableMapOf<String, ManagedResource>()
    
    /**
     * Register a resource for management
     * 
     * @param resource Resource to track
     */
    fun register(resource: ManagedResource) {
        resources[resource.resourceId] = resource
    }
    
    /**
     * Release a specific resource by ID
     * 
     * @param resourceId ID of resource to release
     */
    fun release(resourceId: String) {
        resources.remove(resourceId)?.release()
    }
    
    /**
     * Release all managed resources
     */
    fun releaseAll() {
        resources.values.forEach { it.release() }
        resources.clear()
    }
    
    /**
     * Get current number of managed resources
     * 
     * @return Resource count
     */
    fun getResourceCount(): Int = resources.size
    
    /**
     * Check if a resource is registered
     * 
     * @param resourceId Resource ID to check
     * @return true if resource is registered
     */
    fun hasResource(resourceId: String): Boolean = resources.containsKey(resourceId)
}
