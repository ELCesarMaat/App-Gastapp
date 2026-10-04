package com.binc.gastapp.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * El trabajo unico "sync" de WorkManager. Solo corre con red (condicion CONNECTED) y la
 * logica esta en [SyncEngine].
 *
 * Sin red, timeout o 5xx: reintenta con backoff exponencial, hasta [MAX_ATTEMPTS]
 * intentos; despues se rinde sin perder nada (lo pendiente sigue marcado y sube con la
 * siguiente escritura o al abrir la app). Un 400 no se reintenta: mandaria lo mismo.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val engine: SyncEngine,
    private val scheduler: WorkManagerSyncScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        scheduler.onWorkStarted()
        return when (engine.sync(full = inputData.getBoolean(KEY_FULL, false))) {
            is SyncOutcome.Done, SyncOutcome.NoSession, SyncOutcome.Unauthorized -> Result.success()
            is SyncOutcome.Retry -> if (runAttemptCount + 1 < MAX_ATTEMPTS) Result.retry() else Result.success()
            is SyncOutcome.Failed -> Result.failure()
        }
    }

    companion object {
        const val UNIQUE_NAME = "sync"
        const val KEY_FULL = "full"
        const val MAX_ATTEMPTS = 6
    }
}
