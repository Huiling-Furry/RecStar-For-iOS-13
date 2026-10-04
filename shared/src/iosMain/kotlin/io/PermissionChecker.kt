package io

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusDenied
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVAuthorizationStatusRestricted
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import ui.model.AppContext
import kotlin.coroutines.resume

actual class PermissionChecker actual constructor(appContext: AppContext) {
    actual suspend fun checkAndRequestRecordingPermission(): Boolean {
        return when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeAudio)) {
            AVAuthorizationStatusAuthorized -> true
            AVAuthorizationStatusNotDetermined -> {
                suspendCancellableCoroutine { continuation ->
                    AVCaptureDevice.requestAccessForMediaType(AVMediaTypeAudio) { granted ->
                        if (continuation.isActive) {
                            continuation.resume(granted)
                        }
                    }
                }
            }
            AVAuthorizationStatusDenied,
            AVAuthorizationStatusRestricted,
            -> false
            else -> false
        }
    }

    actual fun checkRecordingPermissionIgnored(): Boolean {
        val authStatus = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeAudio)
        return authStatus == AVAuthorizationStatusDenied || authStatus == AVAuthorizationStatusRestricted
    }
}
