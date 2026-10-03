package com.jaime.teleprompter.data

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.Effect
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbMatrix
import com.jaime.teleprompter.domain.FilterTimeline
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
 * Genera la versión horizontal (1920x1080) a partir del vídeo vertical original:
 * recorte central 16:9 + reescalado, re-codificado en H.264/AAC (compatible con YouTube).
 * Con una grabación 4K vertical (2160 px de ancho) el resultado es 1080p reales, sin escalar hacia arriba.
 */
@OptIn(UnstableApi::class)
class VideoExporter(private val context: Context) {

    /** Vertical con filtros: se re-codifica a la resolución original aplicando el filtro de cada tramo. */
    suspend fun exportVertical(input: File, output: File, filters: FilterTimeline, onProgress: (Float) -> Unit) =
        transcode(input, output, listOfNotNull(filterEffect(filters)), VERTICAL_BITRATE, onProgress)

    /** Horizontal: recorte central 16:9 a 1920x1080 y, si hay, filtros. */
    suspend fun exportHorizontal(input: File, output: File, filters: FilterTimeline, onProgress: (Float) -> Unit) {
        val crop = Presentation.createForWidthAndHeight(OUT_WIDTH, OUT_HEIGHT, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP)
        transcode(input, output, listOfNotNull(crop, filterEffect(filters)), OUT_BITRATE, onProgress)
    }

    private fun filterEffect(filters: FilterTimeline): Effect? =
        if (filters.isIdentity) null else TimelineRgbMatrix(filters)

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
        const val OUT_BITRATE = 16_000_000
        const val VERTICAL_BITRATE = 40_000_000
        const val PROGRESS_POLL_MS = 200L
    }
}
