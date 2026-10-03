package com.jaime.teleprompter.data

import com.jaime.teleprompter.domain.ExportStage
import com.jaime.teleprompter.domain.ExportState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Orquesta la exportación a los dos formatos. Vive a nivel de aplicación (no de pantalla)
 * para que una rotación o navegar atrás no interrumpa la exportación.
 *
 * - Vertical: el original (grabado en vertical), sin recomprimir: cero pérdida de calidad.
 * - Horizontal: recorte central 16:9 a 1920x1080.
 */
class ExportCoordinator(
    private val scope: CoroutineScope,
    private val exporter: VideoExporter,
    private val saver: MediaStoreSaver,
    private val cacheDir: File,
) {
    private val _state = MutableStateFlow<ExportState>(ExportState.Idle)
    val state: StateFlow<ExportState> = _state.asStateFlow()

    private var job: Job? = null

    fun start(source: File) {
        if (job?.isActive == true) return
        job = scope.launch {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val horizontalTemp = File(cacheDir, "horizontal_$stamp.mp4")
            try {
                _state.value = ExportState.Running(ExportStage.VERTICAL, 0f)
                val verticalUri = saver.saveVideo(source, "tp_${stamp}_vertical.mp4")

                _state.value = ExportState.Running(ExportStage.HORIZONTAL, 0f)
                exporter.exportHorizontal(source, horizontalTemp) { progress ->
                    _state.value = ExportState.Running(ExportStage.HORIZONTAL, progress)
                }
                val horizontalUri = saver.saveVideo(horizontalTemp, "tp_${stamp}_horizontal.mp4")

                source.delete()
                _state.value = ExportState.Done(horizontalUri, verticalUri)
            } catch (e: CancellationException) {
                _state.value = ExportState.Idle
                throw e
            } catch (e: Exception) {
                _state.value = ExportState.Failed(e.message ?: e.javaClass.simpleName)
            } finally {
                horizontalTemp.delete()
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    fun reset() {
        if (job?.isActive != true) _state.value = ExportState.Idle
    }
}
