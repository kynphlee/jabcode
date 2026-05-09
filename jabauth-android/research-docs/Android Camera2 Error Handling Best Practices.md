# Android Camera2 Error Handling Best Practices

**Author:** Manus AI

Developing a robust diagnostic application using the Android Camera2 API requires a comprehensive understanding of its error handling mechanisms. The asynchronous nature of the API, combined with the complexity of hardware interactions, means that errors can occur at multiple levels—from device connection to individual frame captures. This report synthesizes best practices for handling Camera2 API errors and exceptions, focusing on device-level errors, session-level failures, recovery strategies, and diagnostic logging.

## 1. Device-Level Error Handling

Device-level errors occur when the camera hardware or the camera service encounters a problem that prevents the camera from being opened or maintained. These are typically communicated through the `CameraDevice.StateCallback` [1].

### CameraDevice.StateCallback Errors

The `onError(CameraDevice camera, int error)` method is the primary mechanism for receiving fatal device errors. When this callback is invoked, the camera device is no longer usable, and any subsequent method calls will throw a `CameraAccessException` [1].

The following table outlines the specific error codes and the recommended handling strategies:

| Error Code | Description | Recommended Handling Strategy |
| :--- | :--- | :--- |
| `ERROR_CAMERA_IN_USE` (1) | The camera is already in use by a higher-priority client. | Close the camera device. Wait for the `CameraManager.AvailabilityCallback.onCameraAvailable` callback before attempting to reopen [1] [2]. |
| `ERROR_MAX_CAMERAS_IN_USE` (2) | The system-wide limit for open cameras has been reached. | Close the camera device. Prompt the user to close other camera applications, or wait and retry with exponential backoff [1]. |
| `ERROR_CAMERA_DISABLED` (3) | The camera could not be opened due to a device policy (e.g., enterprise management). | Close the camera device. Inform the user that the camera is disabled by policy and gracefully degrade functionality [1]. |
| `ERROR_CAMERA_DEVICE` (4) | The camera device has encountered a fatal hardware or firmware error. | Close the camera device. Attempt to reopen the camera. If the error persists, prompt the user to restart the device [1]. |
| `ERROR_CAMERA_SERVICE` (5) | The camera service has encountered a fatal error. | Close the `CameraDevice` and the `CameraManager`. Attempt to acquire all resources again from scratch. A device restart may be required [1]. |

### Handling Disconnections

The `onDisconnected(CameraDevice camera)` callback is invoked when the camera is no longer available, such as when a removable camera is physically disconnected or when a higher-priority application takes control [1].

> "You should clean up the camera with CameraDevice.close after this happens, as it is not recoverable until the camera can be opened again. For most use cases, this will be when the camera again becomes available." [1]

Applications should listen for the `CameraManager.AvailabilityCallback.onCameraAvailable` callback to know when it is safe to attempt reopening the camera [2].

## 2. Session-Level Error Handling

Session-level errors occur during the configuration or execution of a `CameraCaptureSession`.

### Configuration Failures

The `CameraCaptureSession.StateCallback.onConfigureFailed(CameraCaptureSession session)` method is called if the session cannot be configured [3]. This typically happens when the requested stream combination (e.g., resolutions, formats, or number of outputs) is not supported by the hardware level of the device.

**Best Practice:** Always query `CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP` to ensure the requested sizes and formats are supported before creating the session. If `onConfigureFailed` is called, the session is considered closed. The application should fall back to a simpler, guaranteed-supported stream combination (e.g., a single preview stream) and attempt to create a new session [3].

### Capture Failures

Errors during individual frame captures are reported through the `CameraCaptureSession.CaptureCallback.onCaptureFailed` method, which provides a `CaptureFailure` object [4].

The `CaptureFailure` object provides a reason code:
*   `REASON_ERROR` (0): The capture was dropped due to an error in the framework [5].
*   `REASON_FLUSHED` (1): The capture failed because the application called `abortCaptures()` [5].

**Best Practice:** For diagnostic applications, log the `frameNumber`, `sequenceId`, and `reason` from the `CaptureFailure`. If `REASON_ERROR` occurs frequently, it may indicate a hardware issue or an unstable stream configuration. If `wasImageCaptured()` returns true, some buffers may still be available, but the metadata is lost [5].

### Sequence Aborts

The `onCaptureSequenceAborted` callback indicates that a sequence of captures was terminated before completion, usually due to `stopRepeating()` or `abortCaptures()` [4]. This is generally an expected state transition rather than a fatal error, but diagnostic apps should track these to ensure the state machine is operating correctly.

## 3. Recovery Strategies and Retry Logic

When a recoverable error occurs, applications should implement robust retry mechanisms to restore functionality without crashing.

### Exponential Backoff

For transient errors or when waiting for resources to become available (e.g., `ERROR_MAX_CAMERAS_IN_USE`), implement an exponential backoff retry strategy. Attempt to reopen the camera after a short delay (e.g., 500ms), and double the delay for subsequent attempts up to a maximum threshold.

### Availability Callbacks

Instead of blindly retrying, the most robust recovery strategy relies on `CameraManager.AvailabilityCallback` [2].

1.  Register an `AvailabilityCallback` using `CameraManager.registerAvailabilityCallback()`.
2.  When `onError` or `onDisconnected` occurs, close the camera and transition the app to a "waiting" state.
3.  When `onCameraAvailable(String cameraId)` is invoked for the target camera, initiate the camera open sequence again [2].

For logical multi-cameras, also monitor `onPhysicalCameraAvailable` and `onPhysicalCameraUnavailable` to handle dynamic changes in physical sensor availability without tearing down the entire logical session [2].

## 4. Diagnostic Logging and Telemetry

A diagnostic application must capture detailed context when errors occur to facilitate debugging.

### Structured Error Telemetry

When logging Camera2 errors, include the following structured data:
*   **Camera ID:** The ID of the failing camera.
*   **Hardware Level:** The `INFO_SUPPORTED_HARDWARE_LEVEL` of the device.
*   **Error Code/Reason:** The specific integer code from `StateCallback` or `CaptureFailure`.
*   **Session State:** Whether the error occurred during opening, configuration, or active capture.
*   **Stream Configuration:** The formats and resolutions that were active or requested.

### Exception Handling

The Camera2 API frequently throws specific exceptions that must be caught:
*   `CameraAccessException`: Thrown when the camera device has been disconnected or has encountered a fatal error during a method call. Catch this around all `CameraDevice` and `CameraCaptureSession` interactions.
*   `IllegalStateException`: Thrown when methods are called on a closed `CameraDevice` or `CameraCaptureSession`. Ensure state variables (e.g., `isCameraClosed`) are checked before invoking methods, and synchronize access to camera objects.
*   `IllegalArgumentException`: Thrown when invalid parameters (e.g., unsupported surfaces) are passed to the API. Validate all inputs against `CameraCharacteristics` [3].

## Conclusion

Handling Camera2 API errors effectively requires a defensive programming approach. By correctly interpreting `StateCallback` error codes, gracefully handling `CaptureFailure` events, utilizing `AvailabilityCallback` for recovery, and implementing structured diagnostic logging, developers can build resilient diagnostic applications that provide clear feedback and maintain stability even in the face of hardware or framework instability.

## References

[1] Android Developers. "CameraDevice.StateCallback API reference." https://developer.android.com/reference/android/hardware/camera2/CameraDevice.StateCallback
[2] Android Developers. "CameraManager.AvailabilityCallback API reference." https://developer.android.com/reference/android/hardware/camera2/CameraManager.AvailabilityCallback
[3] Android Developers. "CameraCaptureSession.StateCallback API reference." https://developer.android.com/reference/android/hardware/camera2/CameraCaptureSession.StateCallback
[4] Android Developers. "CameraCaptureSession.CaptureCallback API reference." https://developer.android.com/reference/android/hardware/camera2/CameraCaptureSession.CaptureCallback
[5] Android Developers. "CaptureFailure API reference." https://developer.android.com/reference/android/hardware/camera2/CaptureFailure
