package com.jaime.teleprompter.data

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.jaime.teleprompter.TeleprompterApp
import com.jaime.teleprompter.domain.ExportJob
import com.jaime.teleprompter.domain.ExportState
import com.jaime.teleprompter.domain.FilterTimeline
import com.jaime.teleprompter.domain.VerticalOutput
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Exporta en segundo plano como servicio en primer plano (con notificación de progreso):
 * sobrevive a minimizar la app y a que el sistema mate el proceso (WorkManager lo reintenta).
 */
class ExportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val container get() = (applicationContext as TeleprompterApp).container
    private val notifications = ExportNotifications(applicationContext)

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(null)

    override suspend fun doWork(): Result {
        val job = inputData.toExportJob() ?: return Result.failure()
        setForeground(foregroundInfo(null))

        val coordinator = container.exportCoordinator
        val success = coroutineScope {
            // Mantiene la notificación al día con el progreso.
            val progressJob = launch {
                var lastShown = -1
                coordinator.state.collect { state ->
                    val progress = (state as? ExportState.Running)?.progress
                    val percent = ((progress ?: 0f) * 100).toInt() / 5 * 5
                    if (state is ExportState.Running && percent != lastShown) {
                        lastShown = percent
                        setForeground(foregroundInfo(progress))
                    }
                }
            }
            try {
                coordinator.run(job)
            } finally {
                progressJob.cancel()
            }
        }
        notifications.done(success)
        return if (success) Result.success() else Result.failure()
    }

    private fun foregroundInfo(progress: Float?) = ForegroundInfo(
        ExportNotifications.PROGRESS_ID,
        notifications.progress(progress),
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
    )

    companion object {
        private const val KEY_SOURCE = "source"
        private const val KEY_CENTER = "center"
        private const val KEY_VERTICAL = "vertical"
        private const val KEY_AT = "tl_at"
        private const val KEY_FILTER = "tl_filter"
        private const val KEY_INTENSITY = "tl_intensity"
        private const val KEY_SMOOTHING = "tl_smoothing"

        fun ExportJob.toData(): Data {
            val packed = filters.pack()
            return Data.Builder()
                .putString(KEY_SOURCE, sourcePath)
                .putFloat(KEY_CENTER, horizontalFrameCenter)
                .putString(KEY_VERTICAL, verticalOutput.name)
                .putLongArray(KEY_AT, packed.atMs)
                .putIntArray(KEY_FILTER, packed.filters)
                .putFloatArray(KEY_INTENSITY, packed.intensities)
                .putFloatArray(KEY_SMOOTHING, packed.smoothing)
                .build()
        }

        fun Data.toExportJob(): ExportJob? {
            val source = getString(KEY_SOURCE) ?: return null
            val filters = FilterTimeline.unpack(
                FilterTimeline.Packed(
                    atMs = getLongArray(KEY_AT) ?: LongArray(0),
                    filters = getIntArray(KEY_FILTER) ?: IntArray(0),
                    intensities = getFloatArray(KEY_INTENSITY) ?: FloatArray(0),
                    smoothing = getFloatArray(KEY_SMOOTHING) ?: FloatArray(0),
                ),
            )
            return ExportJob(
                sourcePath = source,
                filters = filters,
                horizontalFrameCenter = getFloat(KEY_CENTER, 0.5f),
                verticalOutput = VerticalOutput.entries.firstOrNull { it.name == getString(KEY_VERTICAL) }
                    ?: VerticalOutput.ORIGINAL,
            )
        }
    }
}
