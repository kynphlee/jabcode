package com.jabauth.jabcode.camera.lifecycle

/**
 * Camera lifecycle states mapped to Android Activity lifecycle
 * 
 * @property canOpenCamera Whether camera can be opened in this state
 * @property requiresCleanup Whether resources should be cleaned up
 * @property isActive Whether this is an active state
 */
enum class CameraLifecycleState(
    val canOpenCamera: Boolean,
    val requiresCleanup: Boolean,
    val isActive: Boolean
) {
    /** Activity created, camera not yet initialized */
    CREATED(canOpenCamera = false, requiresCleanup = false, isActive = false),
    
    /** Activity started, can initialize camera resources */
    STARTED(canOpenCamera = false, requiresCleanup = false, isActive = true),
    
    /** Activity resumed, camera can be opened and streaming */
    RESUMED(canOpenCamera = true, requiresCleanup = false, isActive = true),
    
    /** Activity paused, camera should be closed */
    PAUSED(canOpenCamera = false, requiresCleanup = true, isActive = false),
    
    /** Activity stopped, all resources should be released */
    STOPPED(canOpenCamera = false, requiresCleanup = true, isActive = false),
    
    /** Activity destroyed, final cleanup */
    DESTROYED(canOpenCamera = false, requiresCleanup = true, isActive = false)
}
