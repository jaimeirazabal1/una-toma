package com.jaime.teleprompter.domain

import android.net.Uri

enum class RecordingQuality { UHD_4K, FHD }

enum class Lens { FRONT, BACK }

/** VERTICAL: cuadro completo 9:16. HORIZONTAL: solo el recorte central 16:9. BOTH: 9:16 con la franja 16:9 resaltada. */
enum class PreviewMode { HORIZONTAL, VERTICAL, BOTH }

data class TeleprompterSettings(
    val speedDpPerSec: Float = DEFAULT_SPEED,
    val fontSizeSp: Float = DEFAULT_FONT_SIZE,
    val backgroundOpacity: Float = DEFAULT_OPACITY,
    /** Texto en espejo, para leer a través de un cristal de teleprompter. */
    val mirror: Boolean = false,
) {
    companion object {
        const val DEFAULT_SPEED = 45f
        const val DEFAULT_FONT_SIZE = 34f
        const val DEFAULT_OPACITY = 0.55f
        val SPEED_RANGE = 10f..250f
        val FONT_RANGE = 18f..72f
    }
}

/** Cómo se exporta el vertical: el original (4K, sin recomprimir) o 1080x1920 (más ligero). */
enum class VerticalOutput { ORIGINAL, FHD }

data class RecordingSettings(
    val quality: RecordingQuality = RecordingQuality.UHD_4K,
    val lens: Lens = Lens.FRONT,
    val countdownSeconds: Int = 3,
    val previewMode: PreviewMode = PreviewMode.BOTH,
    val filter: VideoFilter = VideoFilter.ORIGINAL,
    val filterIntensity: Float = 1f,
    val skinSmoothing: Float = 0f,
    /** Centro vertical de la franja 16:9 dentro del cuadro vertical (0 = arriba, 1 = abajo). */
    val horizontalFrameCenter: Float = HorizontalFrame.DEFAULT_CENTER,
    val verticalOutput: VerticalOutput = VerticalOutput.ORIGINAL,
)

data class UserSettings(
    val teleprompter: TeleprompterSettings = TeleprompterSettings(),
    val recording: RecordingSettings = RecordingSettings(),
)

enum class ExportStage { HORIZONTAL, VERTICAL }

sealed interface ExportState {
    data object Idle : ExportState
    /** [progress] null = indeterminado (p. ej. copia del original). */
    data class Running(val stage: ExportStage, val progress: Float?) : ExportState
    data class Done(val horizontal: Uri, val vertical: Uri) : ExportState
    data class Failed(val message: String) : ExportState
}
