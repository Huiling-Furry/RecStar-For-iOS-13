package io

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import ui.model.AppContext
import ui.model.androidNativeContext
import kotlin.coroutines.resume

actual class PermissionChecker actual constructor(private val appContext: AppContext) {
    private val activity: Activity get() = appContext.androidNativeContext as Activity

    private var hasRequestedPermission = false
    private var pendingContinuation: CancellableContinuation<Boolean>? = null

    private val permissionLauncher: ActivityResultLauncher<String> =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasRequestedPermission = true
            pendingContinuation?.let { continuation ->
                pendingContinuation = null
                if (continuation.isActive) {
                    continuation.resume(granted)
                }
            }
        }

    actual suspend fun checkAndRequestRecordingPermission(): Boolean {
        if (ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }

        if (pendingContinuation != null) {
            return false
        }

        hasRequestedPermission = true
        return suspendCancellableCoroutine { continuation ->
            pendingContinuation = continuation
            continuation.invokeOnCancellation {
                if (pendingContinuation === continuation) {
                    pendingContinuation = null
                }
            }
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    actual fun checkRecordingPermissionIgnored(): Boolean {
        val granted = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted || !hasRequestedPermission) {
            return false
        }
        return !ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.RECORD_AUDIO,
        )
    }
}
