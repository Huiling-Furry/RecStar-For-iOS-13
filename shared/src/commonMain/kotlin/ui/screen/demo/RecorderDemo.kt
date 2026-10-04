package ui.screen.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import audio.AudioRecorder
import audio.AudioRecorderProvider
import io.File
import io.LocalFileInteractor
import io.LocalPermissionChecker
import io.Paths
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import repository.LocalAppPreferenceRepository
import ui.common.ErrorNotifier
import ui.common.LocalAlertDialogController
import ui.common.requestConfirm
import ui.string.Strings
import ui.string.stringStatic
import ui.model.LocalAppContext
import ui.model.Screen

object RecorderDemoScreen : Screen {
    @Composable
    override fun getTitle(): String = "Recorder Demo"

    @Composable
    override fun Content() = RecorderDemo()
}

@Composable
private fun RecorderDemo() {
    var isRecording by remember { mutableStateOf(false) }
    var isRequestedRecording by remember { mutableStateOf(false) }
    val listener = remember {
        object : AudioRecorder.Listener {
            override fun onStarted() {
                isRecording = true
            }

            override fun onStopped() {
                isRecording = false
            }
        }
    }
    val context = LocalAppContext.current
    val fileInteractor = LocalFileInteractor.current
    val permissionChecker = LocalPermissionChecker.current
    val alertDialogController = LocalAlertDialogController.current
    val appPreferenceRepository = LocalAppPreferenceRepository.current
    val errorNotifier = remember(context, alertDialogController, fileInteractor) {
        ErrorNotifier(
            context = context,
            alertDialogController = alertDialogController,
            fileInteractor = fileInteractor,
            onFatalError = {},
        )
    }
    val recorder = remember {
        AudioRecorderProvider(listener, context, errorNotifier, appPreferenceRepository).get()
    }
    val coroutineScope = rememberCoroutineScope()
    var isRequestingPermission by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = "Is recording: $isRecording")
            Button(
                enabled = !isRequestingPermission && isRecording == isRequestedRecording,
                onClick = {
                    if (isRequestedRecording) {
                        isRequestedRecording = false
                        recorder.stop()
                        return@Button
                    }
                    isRequestingPermission = true
                    coroutineScope.launch {
                        try {
                            if (permissionChecker.checkRecordingPermissionIgnored()) {
                                alertDialogController.requestConfirm(
                                    title = stringStatic(Strings.AlertNeedManualPermissionGrantTitle),
                                    message = stringStatic(Strings.AlertNeedManualPermissionGrantMessage),
                                )
                                return@launch
                            }
                            if (permissionChecker.checkAndRequestRecordingPermission()) {
                                isRequestedRecording = true
                                recorder.start(getOutputFile())
                            }
                        } finally {
                            isRequestingPermission = false
                        }
                    }
                },
            ) {
                Text(text = if (isRecording) "Stop recording" else "Start recording")
            }
            Button(onClick = { fileInteractor.requestOpenFolder(Paths.contentRoot) }) {
                Text(text = "Show output directory")
            }
        }
    }
}

private fun getOutputFile(): File {
    val outputDir = Paths.contentRoot
    val dateTimeSuffix = Clock.System.now().epochSeconds.toString()
    return outputDir.resolve("record-test-$dateTimeSuffix.wav")
}
