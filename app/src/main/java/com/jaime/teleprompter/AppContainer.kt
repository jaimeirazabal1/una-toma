package com.jaime.teleprompter

import android.content.Context
import com.jaime.teleprompter.camera.CameraRecorder
import com.jaime.teleprompter.data.ExportCoordinator
import com.jaime.teleprompter.data.MediaStoreSaver
import com.jaime.teleprompter.data.SettingsRepository
import com.jaime.teleprompter.data.VideoExporter
import com.jaime.teleprompter.data.ExportNotifications
import com.jaime.teleprompter.data.ExportScheduler
import java.io.File
import java.util.concurrent.TimeUnit

/** Inyección de dependencias manual: suficiente para este tamaño y sin costes de compilación (kapt/ksp). */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val settingsRepository = SettingsRepository(appContext)

    val exportCoordinator = ExportCoordinator(
        exporter = VideoExporter(appContext),
        saver = MediaStoreSaver(appContext),
        cacheDir = appContext.cacheDir,
    )

    val exportScheduler = ExportScheduler(appContext, exportCoordinator)

    init {
        ExportNotifications(appContext).createChannel()
        pruneOldRecordings()
    }

    /** Borra grabaciones huérfanas (exportaciones fallidas) con más de una semana. */
    private fun pruneOldRecordings() {
        val limit = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        File(appContext.filesDir, "recordings").listFiles()?.filter { it.lastModified() < limit }?.forEach { it.delete() }
    }

    fun newCameraRecorder() = CameraRecorder(appContext)
}
