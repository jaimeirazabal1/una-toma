package com.jaime.teleprompter.ui.script

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jaime.teleprompter.AppContainer
import com.jaime.teleprompter.data.SettingsRepository
import com.jaime.teleprompter.domain.RecordingSettings
import com.jaime.teleprompter.domain.TeleprompterSettings
import com.jaime.teleprompter.domain.UserSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class ScriptViewModel(private val repository: SettingsRepository) : ViewModel() {

    /** Copia local editable del guion; se persiste con debounce para no escribir en cada tecla. */
    private val _script = MutableStateFlow<String?>(null)
    val script: StateFlow<String?> = _script

    val settings: StateFlow<UserSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    init {
        viewModelScope.launch {
            _script.value = repository.script.first()
            _script.drop(1).debounce(400).collect { text -> text?.let { repository.saveScript(it) } }
        }
    }

    fun onScriptChange(text: String) {
        _script.value = text
    }

    /** Fuerza el guardado inmediato (p. ej. al ir a grabar, antes de que venza el debounce). */
    fun flush(onDone: () -> Unit) {
        viewModelScope.launch {
            _script.value?.let { repository.saveScript(it) }
            onDone()
        }
    }

    fun updateTeleprompter(transform: (TeleprompterSettings) -> TeleprompterSettings) {
        viewModelScope.launch { repository.updateTeleprompter(transform) }
    }

    fun updateRecording(transform: (RecordingSettings) -> RecordingSettings) {
        viewModelScope.launch { repository.updateRecording(transform) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { ScriptViewModel(container.settingsRepository) }
        }
    }
}
