package com.jabauth.jabcode.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Size
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for CameraEnumerator
 *
 * Uses mocked CameraManager to test enumeration logic.
 *
 * Runs under Robolectric: on a bare JVM (JDK 23) the Mockito inline
 * mock-maker cannot instrument the Android framework classes this test
 * mocks (Context, CameraManager, CameraCharacteristics) — every test failed
 * with "Mockito cannot mock this class: class android.content.Context".
 * Robolectric supplies instrumented, mockable versions of those classes and
 * a real `android.util.Size` constructor for the output-size stubs below.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CameraEnumeratorTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockCameraManager: CameraManager

    @Mock
    private lateinit var mockBackCameraCharacteristics: CameraCharacteristics

    @Mock
    private lateinit var mockFrontCameraCharacteristics: CameraCharacteristics

    private lateinit var enumerator: CameraEnumerator

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        `when`(mockContext.getSystemService(Context.CAMERA_SERVICE)).thenReturn(mockCameraManager)
        enumerator = CameraEnumerator(mockContext)
    }

    @Test
    fun getAllCameras_returnsEmptyList_whenNoCamerasAvailable() {
        `when`(mockCameraManager.cameraIdList).thenReturn(arrayOf())

        val cameras = enumerator.getAllCameras()

        assertTrue(cameras.isEmpty())
    }

    @Test
    fun getAllCameras_returnsCameraList_whenCamerasAvailable() {
        `when`(mockCameraManager.cameraIdList).thenReturn(arrayOf("0", "1"))
        setupBackCamera("0")
        setupFrontCamera("1")

        val cameras = enumerator.getAllCameras()

        assertEquals(2, cameras.size)
        assertEquals("0", cameras[0].cameraId)
        assertEquals("1", cameras[1].cameraId)
    }

    @Test
    fun getAllCameras_skipsInaccessibleCamera() {
        `when`(mockCameraManager.cameraIdList).thenReturn(arrayOf("0", "1"))
        setupBackCamera("0")
        `when`(mockCameraManager.getCameraCharacteristics("1"))
            .thenThrow(RuntimeException("Camera not accessible"))

        val cameras = enumerator.getAllCameras()

        assertEquals(1, cameras.size)
        assertEquals("0", cameras[0].cameraId)
    }

    @Test
    fun findCameraByFacing_returnsBackCamera_whenExists() {
        `when`(mockCameraManager.cameraIdList).thenReturn(arrayOf("0", "1"))
        setupBackCamera("0")
        setupFrontCamera("1")

        val backCamera = enumerator.findCameraByFacing(CameraCharacteristics.LENS_FACING_BACK)

        assertNotNull(backCamera)
        assertEquals("0", backCamera?.cameraId)
        assertEquals(CameraCharacteristics.LENS_FACING_BACK, backCamera?.facing)
    }

    @Test
    fun findCameraByFacing_returnsFrontCamera_whenExists() {
        `when`(mockCameraManager.cameraIdList).thenReturn(arrayOf("0", "1"))
        setupBackCamera("0")
        setupFrontCamera("1")

        val frontCamera = enumerator.findCameraByFacing(CameraCharacteristics.LENS_FACING_FRONT)

        assertNotNull(frontCamera)
        assertEquals("1", frontCamera?.cameraId)
        assertEquals(CameraCharacteristics.LENS_FACING_FRONT, frontCamera?.facing)
    }

    @Test
    fun findCameraByFacing_returnsNull_whenNotFound() {
        `when`(mockCameraManager.cameraIdList).thenReturn(arrayOf("0"))
        setupBackCamera("0")

        val frontCamera = enumerator.findCameraByFacing(CameraCharacteristics.LENS_FACING_FRONT)

        assertNull(frontCamera)
    }

    @Test
    fun findCameraByFacing_returnsHighestLevel_whenMultipleCamerasMatch() {
        `when`(mockCameraManager.cameraIdList).thenReturn(arrayOf("0", "1"))
        // Both back cameras, but different hardware levels
        setupBackCameraWithLevel("0", CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED)
        setupBackCameraWithLevel("1", CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL)

        val backCamera = enumerator.findCameraByFacing(CameraCharacteristics.LENS_FACING_BACK)

        assertNotNull(backCamera)
        assertEquals("1", backCamera?.cameraId) // Should return FULL level camera
        assertEquals(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL, backCamera?.hardwareLevel)
    }

    @Test
    fun findCamerasWithCapability_returnsMatchingCameras() {
        `when`(mockCameraManager.cameraIdList).thenReturn(arrayOf("0", "1"))
        setupBackCamera("0") // Has RAW capability
        setupFrontCamera("1") // No RAW capability

        val rawCameras = enumerator.findCamerasWithCapability(
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW
        )

        assertEquals(1, rawCameras.size)
        assertEquals("0", rawCameras[0].cameraId)
    }

    @Test
    fun findCamerasWithCapability_returnsEmptyList_whenNoMatch() {
        `when`(mockCameraManager.cameraIdList).thenReturn(arrayOf("0"))
        setupFrontCamera("0") // No RAW capability

        val rawCameras = enumerator.findCamerasWithCapability(
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW
        )

        assertTrue(rawCameras.isEmpty())
    }

    private fun setupBackCamera(cameraId: String) {
        setupBackCameraWithLevel(cameraId, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL)
    }

    private fun setupBackCameraWithLevel(cameraId: String, hardwareLevel: Int) {
        val characteristics = if (cameraId == "0") mockBackCameraCharacteristics else mock(CameraCharacteristics::class.java)
        `when`(mockCameraManager.getCameraCharacteristics(cameraId)).thenReturn(characteristics)
        `when`(characteristics.get(CameraCharacteristics.LENS_FACING))
            .thenReturn(CameraCharacteristics.LENS_FACING_BACK)
        `when`(characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL))
            .thenReturn(hardwareLevel)
        `when`(characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES))
            .thenReturn(intArrayOf(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE,
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW
            ))

        val streamConfigMap = mock(android.hardware.camera2.params.StreamConfigurationMap::class.java)
        `when`(characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP))
            .thenReturn(streamConfigMap)
        `when`(streamConfigMap.getOutputSizes(android.graphics.ImageFormat.JPEG))
            .thenReturn(arrayOf(Size(1920, 1080), Size(1280, 720)))
    }

    private fun setupFrontCamera(cameraId: String) {
        val characteristics = if (cameraId == "1") mockFrontCameraCharacteristics else mock(CameraCharacteristics::class.java)
        `when`(mockCameraManager.getCameraCharacteristics(cameraId)).thenReturn(characteristics)
        `when`(characteristics.get(CameraCharacteristics.LENS_FACING))
            .thenReturn(CameraCharacteristics.LENS_FACING_FRONT)
        `when`(characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL))
            .thenReturn(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED)
        `when`(characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES))
            .thenReturn(intArrayOf(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE
            ))

        val streamConfigMap = mock(android.hardware.camera2.params.StreamConfigurationMap::class.java)
        `when`(characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP))
            .thenReturn(streamConfigMap)
        `when`(streamConfigMap.getOutputSizes(android.graphics.ImageFormat.JPEG))
            .thenReturn(arrayOf(Size(1280, 720)))
    }
}
