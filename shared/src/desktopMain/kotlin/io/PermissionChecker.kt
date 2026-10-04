package io

import ui.model.AppContext

actual class PermissionChecker actual constructor(appContext: AppContext) {
    actual suspend fun checkAndRequestRecordingPermission(): Boolean = true

    actual fun checkRecordingPermissionIgnored(): Boolean = false
}
