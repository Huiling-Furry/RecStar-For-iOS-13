package io

import androidx.compose.runtime.staticCompositionLocalOf
import ui.model.AppContext

/**
 * A helper class to check and request permissions.
 */
expect class PermissionChecker(appContext: AppContext) {
    /**
     * Checks if the app has permission to record audio and requests it if necessary.
     *
     * This function completes only after the operating system returns the permission result. A successful first-time
     * request can therefore continue the current recording action without requiring a second user interaction.
     */
    suspend fun checkAndRequestRecordingPermission(): Boolean

    /**
     * Checks if the OS has ignored the app's request to record audio. If this returns true, it means the user has
     * denied the permission more some times, or has checked the "Don't ask again" option. In this case, the app should
     * show a dialog explaining why it needs the permission and how to enable it manually.
     */
    fun checkRecordingPermissionIgnored(): Boolean
}

val LocalPermissionChecker = staticCompositionLocalOf<PermissionChecker> {
    error("No PermissionChecker provided")
}
