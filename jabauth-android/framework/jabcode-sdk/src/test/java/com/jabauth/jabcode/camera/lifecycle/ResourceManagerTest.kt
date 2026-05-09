package com.jabauth.jabcode.camera.lifecycle

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ResourceManager
 * 
 * Tests resource tracking and cleanup coordination
 */
class ResourceManagerTest {
    
    private lateinit var manager: ResourceManager
    private lateinit var cleanupCallbacks: MutableList<String>
    
    @Before
    fun setup() {
        manager = ResourceManager()
        cleanupCallbacks = mutableListOf()
    }
    
    @Test
    fun manager_tracksRegisteredResources() {
        val resource1 = object : ManagedResource {
            override val resourceId = "camera1"
            override fun release() {
                cleanupCallbacks.add("camera1")
            }
        }
        
        manager.register(resource1)
        
        assertEquals(1, manager.getResourceCount())
        assertTrue(manager.hasResource(resource1.resourceId))
    }
    
    @Test
    fun manager_releasesAllResources() {
        val resource1 = object : ManagedResource {
            override val resourceId = "camera1"
            override fun release() {
                cleanupCallbacks.add("camera1")
            }
        }
        
        val resource2 = object : ManagedResource {
            override val resourceId = "imageReader1"
            override fun release() {
                cleanupCallbacks.add("imageReader1")
            }
        }
        
        manager.register(resource1)
        manager.register(resource2)
        
        manager.releaseAll()
        
        assertEquals(2, cleanupCallbacks.size)
        assertTrue(cleanupCallbacks.contains("camera1"))
        assertTrue(cleanupCallbacks.contains("imageReader1"))
        assertEquals(0, manager.getResourceCount())
    }
    
    @Test
    fun manager_releasesSingleResource() {
        val resource1 = object : ManagedResource {
            override val resourceId = "camera1"
            override fun release() {
                cleanupCallbacks.add("camera1")
            }
        }
        
        val resource2 = object : ManagedResource {
            override val resourceId = "imageReader1"
            override fun release() {
                cleanupCallbacks.add("imageReader1")
            }
        }
        
        manager.register(resource1)
        manager.register(resource2)
        
        manager.release("camera1")
        
        assertEquals(1, cleanupCallbacks.size)
        assertEquals("camera1", cleanupCallbacks[0])
        assertEquals(1, manager.getResourceCount())
        assertFalse(manager.hasResource("camera1"))
        assertTrue(manager.hasResource("imageReader1"))
    }
    
    @Test
    fun manager_ignoresDuplicateRegistration() {
        val resource = object : ManagedResource {
            override val resourceId = "camera1"
            override fun release() {
                cleanupCallbacks.add("camera1")
            }
        }
        
        manager.register(resource)
        manager.register(resource) // Duplicate
        
        assertEquals(1, manager.getResourceCount())
        
        manager.releaseAll()
        
        // Should only be called once
        assertEquals(1, cleanupCallbacks.size)
    }
    
    @Test
    fun manager_handlesReleaseOfNonExistentResource() {
        // Should not throw
        manager.release("nonexistent")
        
        assertEquals(0, manager.getResourceCount())
    }
}
