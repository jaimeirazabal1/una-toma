package com.jaime.teleprompter.data

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.jaime.teleprompter.data.ExportWorker.Companion.toData
import com.jaime.teleprompter.domain.ExportJob

/** Encola y cancela exportaciones en WorkManager. */
class ExportScheduler(context: Context, private val coordinator: ExportCoordinator) {

    private val workManager = WorkManager.getInstance(context)

    fun enqueue(job: ExportJob) {
        coordinator.markQueued()
        val request = OneTimeWorkRequestBuilder<ExportWorker>()
            .setInputData(job.toData())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        // APPEND_OR_REPLACE: si hubiera una exportación en curso, la nueva espera su turno.
        workManager.enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    fun cancel() {
        workManager.cancelUniqueWork(UNIQUE_NAME)
    }

    private companion object {
        const val UNIQUE_NAME = "export"
    }
}
