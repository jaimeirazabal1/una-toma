package com.jaime.teleprompter.ui.record

import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jaime.teleprompter.AppContainer
import com.jaime.teleprompter.R
import com.jaime.teleprompter.camera.CameraRecorder
import com.jaime.teleprompter.camera.RecordingEvent
import com.jaime.teleprompter.data.ExportCoordinator
import com.jaime.teleprompter.data.SettingsRepository
import com.jaime.teleprompter.domain.PreviewMode
import com.jaime.teleprompter.domain.RecordingSettings
import com.jaime.teleprompter.domain.TeleprompterSettings
import com.jaime.teleprompter.domain.UserSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class RecordPhase { IDLE, COUNTDOWN, RECORDING }

data class RecordUiState(
    val phase: RecordPhase = RecordPhase.IDLE,
    val countdown: Int = 0,
    val elapsedMs: Long = 0,
    val errorRes: Int? = null,
    val errorCode: Int? = null,
)

class RecordViewModel(
    private val settingsRepository: SettingsRepository,
    private val recorder: CameraRecorder,
    private val exports: ExportCoordinator,
) : ViewModel() {

    /** null hasta que DataStore emite: evita enlazar la cámara con valores por defecto y reenlazar. */
    val settings: StateFlow<UserSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val script: StateFlow<String> = settingsRepository.script
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val _ui = MutableStateFlow(RecordUiState())
    val ui: StateFlow<RecordUiState> = _ui.asStateFlow()

    private val _finished = Channel<Unit>(Channel.BUFFERED)
    val finished = _finished.receiveAsFlow()

    private var countdownJob: Job? = null

    suspend fun bindCamera(
        owner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        recording: RecordingSettings,
    ) {
        try {
            recorder.bind(owner, surfaceProvider, recording.lens, recording.quality)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _ui.value = _ui.value.copy(errorRes = R.string.error_camera, errorCode = null)
        }
    }

    fun onRecordClick(displayRotation: Int) {
        when (_ui.value.phase) {
            RecordPhase.IDLE -> startCountdown(displayRotation)
            RecordPhase.COUNTDOWN -> {
                countdownJob?.cancel()
                _ui.value = RecordUiState()
            }
            RecordPhase.RECORDING -> recorder.stop()
        }
    }

    fun updateTeleprompter(transform: (TeleprompterSettings) -> TeleprompterSettings) {
        viewModelScope.launch { settingsRepository.updateTeleprompter(transform) }
    }

    fun setPreviewMode(mode: PreviewMode) {
        viewModelScope.launch { settingsRepository.updateRecording { it.copy(previewMode = mode) } }
    }

    private fun startCountdown(displayRotation: Int) {
        val seconds = settings.value?.recording?.countdownSeconds ?: 0
        countdownJob = viewModelScope.launch {
            for (remaining in seconds downTo 1) {
                _ui.value = RecordUiState(phase = RecordPhase.COUNTDOWN, countdown = remaining)
                delay(1_000)
            }
            _ui.value = RecordUiState(phase = RecordPhase.RECORDING)
            recorder.start(recorder.newOutputFile(), displayRotation, ::onRecordingEvent)
        }
    }

    private fun onRecordingEvent(event: RecordingEvent) {
        when (event) {
            is RecordingEvent.Progress -> _ui.value = _ui.value.copy(elapsedMs = event.elapsedMs)
            is RecordingEvent.Finished -> {
                _ui.value = RecordUiState()
                exports.start(event.file)
                _finished.trySend(Unit)
            }
            is RecordingEvent.Failed -> _ui.value =
                RecordUiState(errorRes = R.string.error_recording, errorCode = event.code)
        }
    }

    override fun onCleared() {
        if (recorder.isRecording) recorder.stop()
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                RecordViewModel(
                    container.settingsRepository,
                    container.newCameraRecorder(),
                    container.exportCoordinator,
                )
            }
        }
    }
}
