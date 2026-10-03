package com.jaime.teleprompter.ui.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jaime.teleprompter.AppContainer
import com.jaime.teleprompter.data.ExportCoordinator
import com.jaime.teleprompter.domain.ExportState
import kotlinx.coroutines.flow.StateFlow

class ExportViewModel(private val coordinator: ExportCoordinator) : ViewModel() {
    val state: StateFlow<ExportState> = coordinator.state

    fun cancel() = coordinator.cancel()
    fun reset() = coordinator.reset()

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { ExportViewModel(container.exportCoordinator) }
        }
    }
}
