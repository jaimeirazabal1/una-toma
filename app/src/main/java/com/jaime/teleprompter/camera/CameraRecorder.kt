package com.jaime.teleprompter.camera

import android.content.Context
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.jaime.teleprompter.domain.Lens
import com.jaime.teleprompter.domain.RecordingQuality
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface RecordingEvent {
    /** La grabación empezó de verdad (el archivo ya recibe frames). */
    data object Started : RecordingEvent
    data class Progress(val elapsedMs: Long) : RecordingEvent
    data class Finished(val file: File) : RecordingEvent
    data class Failed(val code: Int) : RecordingEvent
}

/**
 * Encapsula CameraX. Se graba en vertical (9:16, el teléfono en portrait) a máxima resolución;
 * la versión horizontal se recorta del centro en la exportación.
 */
class CameraRecorder(private val context: Context) {

    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    val isRecording: Boolean get() = recording != null

    suspend fun bind(
        owner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        lens: Lens,
        quality: RecordingQuality,
    ) {
        val provider = awaitProvider()

        val preview = Preview.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(
                        AspectRatioStrategy(AspectRatio.RATIO_16_9, AspectRatioStrategy.FALLBACK_RULE_AUTO),
                    )
                    .build(),
            )
            .build()
            .also { it.setSurfaceProvider(surfaceProvider) }

        val recorder = Recorder.Builder()
            .setAspectRatio(AspectRatio.RATIO_16_9)
            .setQualitySelector(qualitySelector(quality))
            .build()
        val capture = VideoCapture.withOutput(recorder)

        val selector = when (lens) {
            Lens.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
            Lens.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
        }

        provider.unbindAll()
        provider.bindToLifecycle(owner, selector, preview, capture)
        videoCapture = capture
    }

    /** [displayRotation]: `Surface.ROTATION_*` actual, para que el archivo salga con la orientación correcta. */
    fun start(output: File, displayRotation: Int, onEvent: (RecordingEvent) -> Unit) {
        val capture = videoCapture ?: return
        if (recording != null) return
        capture.targetRotation = displayRotation
        output.parentFile?.mkdirs()

        recording = capture.output
            .prepareRecording(context, FileOutputOptions.Builder(output).build())
            .withAudioEnabled()
            .start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> onEvent(RecordingEvent.Started)
                    is VideoRecordEvent.Status ->
                        onEvent(RecordingEvent.Progress(event.recordingStats.recordedDurationNanos / 1_000_000))
                    is VideoRecordEvent.Finalize -> {
                        recording = null
                        if (event.hasError()) {
                            output.delete()
                            onEvent(RecordingEvent.Failed(event.error))
                        } else {
                            onEvent(RecordingEvent.Finished(output))
                        }
                    }
                    else -> Unit
                }
            }
    }

    fun stop() {
        recording?.stop()
    }

    fun newOutputFile(): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(context.cacheDir, "recordings/rec_$stamp.mp4")
    }

    /**
     * No usamos `ProcessCameraProvider.awaitInstance`: al cancelarse la corrutina cancela el future
     * compartido del singleton y las siguientes llamadas fallan con "Task was cancelled".
     */
    private suspend fun awaitProvider(): ProcessCameraProvider = suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                try {
                    continuation.resume(future.get())
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    private fun qualitySelector(quality: RecordingQuality): QualitySelector {
        val order = when (quality) {
            RecordingQuality.UHD_4K -> listOf(Quality.UHD, Quality.FHD, Quality.HD)
            RecordingQuality.FHD -> listOf(Quality.FHD, Quality.HD)
        }
        return QualitySelector.fromOrderedList(order, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))
    }
}
