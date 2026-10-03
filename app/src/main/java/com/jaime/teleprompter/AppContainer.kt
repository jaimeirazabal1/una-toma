package com.jaime.teleprompter

import android.content.Context
import com.jaime.teleprompter.camera.CameraRecorder
import com.jaime.teleprompter.data.ExportCoordinator
import com.jaime.teleprompter.data.MediaStoreSaver
import com.jaime.teleprompter.data.SettingsRepository
import com.jaime.teleprompter.data.VideoExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Inyección de dependencias manual: suficiente para este tamaño y sin costes de compilación (kapt/ksp). */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settingsRepository = SettingsRepository(appContext)

    val exportCoordinator = ExportCoordinator(
        scope = appScope,
        exporter = VideoExporter(appContext),
        saver = MediaStoreSaver(appContext),
        cacheDir = appContext.cacheDir,
    )

    fun newCameraRecorder() = CameraRecorder(appContext)
}
