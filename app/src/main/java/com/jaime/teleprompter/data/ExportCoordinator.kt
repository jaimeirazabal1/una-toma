package com.jaime.teleprompter.data

import com.jaime.teleprompter.domain.ExportJob
import com.jaime.teleprompter.domain.ExportStage
import com.jaime.teleprompter.domain.ExportState
import com.jaime.teleprompter.domain.VerticalOutput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ejecuta una exportación y publica su estado. Lo invoca [ExportWorker] (WorkManager), así que la
 * exportación sigue aunque la app pase a segundo plano; la UI solo observa [state].
 *
 * - Vertical: el original sin recomprimir si no hay nada que aplicar; si se pidió 1080x1920 o hay
 *   filtros/suavizado, se re-codifica.
 * - Horizontal: franja 16:9 a 1920x1080.
 */
class ExportCoordinator(
    private val exporter: VideoExporter,
    private val saver: MediaStoreSaver,
    private val cacheDir: File,
) {
    private val _state = MutableStateFlow<ExportState>(ExportState.Idle)
    val state: StateFlow<ExportState> = _state.asStateFlow()

    /** La UI lo llama al encolar el trabajo para no mostrar "vacío" hasta que el worker arranque. */
    fun markQueued() {
        _state.value = ExportState.Running(ExportStage.VERTICAL, null)
    }

    fun reset() {
        if (_state.value !is ExportState.Running) _state.value = ExportState.Idle
    }

    /** @return true si terminó bien. Relanza la cancelación para que WorkManager la respete. */
    suspend fun run(job: ExportJob): Boolean {
        val source = File(job.sourcePath)
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val verticalTemp = File(cacheDir, "vertical_$stamp.mp4")
        val horizontalTemp = File(cacheDir, "horizontal_$stamp.mp4")
        return try {
            if (!source.exists()) error("No se encontró la grabación")

            val verticalNeedsEncoding = job.verticalOutput == VerticalOutput.FHD || !job.filters.isIdentity
            val verticalUri = if (verticalNeedsEncoding) {
                _state.value = ExportState.Running(ExportStage.VERTICAL, 0f)
                exporter.exportVertical(source, verticalTemp, job) { progress ->
                    _state.value = ExportState.Running(ExportStage.VERTICAL, progress)
                }
                saver.saveVideo(verticalTemp, "tp_${stamp}_vertical.mp4")
            } else {
                _state.value = ExportState.Running(ExportStage.VERTICAL, null)
                saver.saveVideo(source, "tp_${stamp}_vertical.mp4")
            }

            _state.value = ExportState.Running(ExportStage.HORIZONTAL, 0f)
            exporter.exportHorizontal(source, horizontalTemp, job) { progress ->
                _state.value = ExportState.Running(ExportStage.HORIZONTAL, progress)
            }
            val horizontalUri = saver.saveVideo(horizontalTemp, "tp_${stamp}_horizontal.mp4")

            source.delete()
            _state.value = ExportState.Done(horizontalUri, verticalUri)
            true
        } catch (e: CancellationException) {
            _state.value = ExportState.Idle
            throw e
        } catch (e: Exception) {
            _state.value = ExportState.Failed(e.message ?: e.javaClass.simpleName)
            false
        } finally {
            verticalTemp.delete()
            horizontalTemp.delete()
        }
    }
}
