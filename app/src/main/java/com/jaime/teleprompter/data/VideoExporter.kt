package com.jaime.teleprompter.data

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import android.media.MediaMetadataRetriever
import androidx.media3.common.Effect
import androidx.media3.effect.Crop
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbMatrix
import com.jaime.teleprompter.domain.ExportJob
import com.jaime.teleprompter.domain.FilterTimeline
import com.jaime.teleprompter.domain.HorizontalFrame
import com.jaime.teleprompter.domain.VerticalOutput
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Genera las versiones de salida a partir del video vertical grabado: la horizontal (recorte 16:9
 * reposicionable a 1920x1080) y, si hace falta, la vertical re-codificada (1080x1920 y/o con filtros).
 * Con una grabación 4K vertical (2160 px de ancho) el horizontal sale en 1080p reales, sin escalar hacia arriba.
 */
@OptIn(UnstableApi::class)
class VideoExporter(private val context: Context) {

    /**
     * Vertical: se re-codifica solo si hace falta (bajar a 1080x1920 o aplicar filtros/suavizado).
     * Sin nada de eso el coordinador copia el original sin tocarlo.
     */
    suspend fun exportVertical(input: File, output: File, job: ExportJob, onProgress: (Float) -> Unit) {
        val fhd = job.verticalOutput == VerticalOutput.FHD
        val effects = buildList {
            if (fhd) add(Presentation.createForWidthAndHeight(FHD_WIDTH, FHD_HEIGHT, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP))
            addAll(lookEffects(job.filters))
        }
        transcode(input, output, effects, if (fhd) FHD_BITRATE else ORIGINAL_BITRATE, onProgress)
    }

    /** Horizontal: franja 16:9 elegida por el usuario, a 1920x1080, más filtros y suavizado. */
    suspend fun exportHorizontal(input: File, output: File, job: ExportJob, onProgress: (Float) -> Unit) {
        val (width, height) = probeUprightSize(input)
        val band = HorizontalFrame.bandFraction(width, height)
        val (bottom, top) = HorizontalFrame.cropNdc(job.horizontalFrameCenter, band)
        val effects = buildList {
            add(Crop(-1f, 1f, bottom, top))
            add(Presentation.createForWidthAndHeight(OUT_WIDTH, OUT_HEIGHT, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP))
            addAll(lookEffects(job.filters))
        }
        transcode(input, output, effects, HORIZONTAL_BITRATE, onProgress)
    }

    /** Suavizado primero (necesita el color original para detectar piel) y luego el filtro de color. */
    private fun lookEffects(filters: FilterTimeline): List<Effect> = buildList {
        if (filters.hasSmoothing) add(SkinSmoothingEffect(filters))
        if (filters.hasColor) add(TimelineRgbMatrix(filters))
    }

    /** Tamaño del video ya orientado (tiene en cuenta los metadatos de rotación). */
    private fun probeUprightSize(file: File): Pair<Int, Int> {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            fun meta(key: Int) = retriever.extractMetadata(key)?.toIntOrNull() ?: 0
            val w = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val h = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val rotation = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            return if (rotation == 90 || rotation == 270) h to w else w to h
        } finally {
            retriever.release()
        }
    }

    /** Matriz de color que cambia con el tiempo según la línea de tiempo de filtros. */
    private class TimelineRgbMatrix(private val timeline: FilterTimeline) : RgbMatrix {
        override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray =
            timeline.selectionAt(presentationTimeUs / 1_000).affine().toMedia3Matrix()
    }

    private suspend fun transcode(
        input: File,
        output: File,
        videoEffects: List<Effect>,
        bitrate: Int,
        onProgress: (Float) -> Unit,
    ) {
        // Transformer exige un hilo con Looper: usamos Main (el trabajo pesado va en el codec de hardware).
        withContext(Dispatchers.Main.immediate) {
            coroutineScope {
                var activeTransformer: Transformer? = null
                val progressHolder = ProgressHolder()
                val progressJob = launch {
                    while (isActive) {
                        val transformer = activeTransformer
                        if (transformer != null &&
                            transformer.getProgress(progressHolder) != Transformer.PROGRESS_STATE_NOT_STARTED
                        ) {
                            onProgress(progressHolder.progress / 100f)
                        }
                        delay(PROGRESS_POLL_MS)
                    }
                }
                try {
                    suspendCancellableCoroutine { continuation ->
                        val transformer = Transformer.Builder(context)
                            .setVideoMimeType(MimeTypes.VIDEO_H264)
                            .setAudioMimeType(MimeTypes.AUDIO_AAC)
                            .setEncoderFactory(
                                DefaultEncoderFactory.Builder(context)
                                    .setRequestedVideoEncoderSettings(
                                        VideoEncoderSettings.Builder().setBitrate(bitrate).build(),
                                    )
                                    .build(),
                            )
                            .addListener(object : Transformer.Listener {
                                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                    if (continuation.isActive) continuation.resume(Unit)
                                }

                                override fun onError(
                                    composition: Composition,
                                    exportResult: ExportResult,
                                    exportException: ExportException,
                                ) {
                                    if (continuation.isActive) continuation.resumeWithException(exportException)
                                }
                            })
                            .build()
                        activeTransformer = transformer

                        val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(input)))
                            .setEffects(Effects(emptyList(), videoEffects))
                            .build()

                        continuation.invokeOnCancellation {
                            transformer.cancel()
                            output.delete()
                        }
                        transformer.start(item, output.absolutePath)
                    }
                } finally {
                    // Sin esto coroutineScope esperaría eternamente al job de progreso.
                    progressJob.cancel()
                }
            }
        }
    }

    private companion object {
        const val OUT_WIDTH = 1920
        const val OUT_HEIGHT = 1080
        const val FHD_WIDTH = 1080
        const val FHD_HEIGHT = 1920
        const val HORIZONTAL_BITRATE = 16_000_000
        const val FHD_BITRATE = 16_000_000
        const val ORIGINAL_BITRATE = 40_000_000
        const val PROGRESS_POLL_MS = 200L
    }
}
