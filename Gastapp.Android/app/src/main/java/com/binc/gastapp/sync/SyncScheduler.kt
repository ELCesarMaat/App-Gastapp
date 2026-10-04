package com.binc.gastapp.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lo que llaman los repositorios despues de cada escritura local: "hay algo pendiente
 * de subir". La escritura ya quedo en Room con isSynced = false; esto solo pide que se
 * sincronice cuando se pueda.
 */
interface SyncScheduler {
    /** Subir lo pendiente (despues de cada escritura). */
    fun requestSync()

    /** Al abrir la app: refrescar el token, subir y bajar los gastos de otros dispositivos. */
    fun requestFullSync() = requestSync()

    /** Al cerrar sesion: que no quede nada encolado. */
    fun cancelPending() = Unit
}

/**
 * Encola el trabajo unico [SyncWorker.UNIQUE_NAME] de WorkManager: espera a que haya red,
 * sobrevive a que se cierre la app y reintenta con backoff exponencial.
 *
 * Como se evita perder una escritura sin encolar de mas:
 *  - Mientras hay un trabajo esperando a empezar, las escrituras nuevas no encolan otro
 *    ([queued]): ese trabajo, al arrancar, va a leer todo lo pendiente.
 *  - En cuanto un trabajo arranca, [onWorkStarted] baja la bandera. Una escritura que
 *    llegue mientras corre encola UNO mas, encadenado despues (APPEND_OR_REPLACE), por
 *    si el que corre ya habia leido lo pendiente.
 *  - El retraso de 2 s junta una rafaga de escrituras en un solo envio.
 * Si el proceso muere, la bandera arranca en false y a lo mucho se encola uno de mas.
 */
@Singleton
class WorkManagerSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : SyncScheduler {

    private val queued = AtomicBoolean(false)

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    override fun requestSync() {
        if (queued.compareAndSet(false, true)) enqueue(full = false, delay = BurstDelay)
    }

    override fun requestFullSync() {
        queued.set(true)
        enqueue(full = true, delay = Duration.ZERO)
    }

    override fun cancelPending() {
        workManager.cancelUniqueWork(SyncWorker.UNIQUE_NAME)
        queued.set(false)
    }

    /** Lo llama el SyncWorker al empezar: lo que se escriba desde ahora necesita otro envio. */
    fun onWorkStarted() {
        queued.set(false)
    }

    private fun enqueue(full: Boolean, delay: Duration) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofSeconds(30))
            .setInitialDelay(delay)
            .setInputData(workDataOf(SyncWorker.KEY_FULL to full))
            .addTag(SyncWorker.UNIQUE_NAME)
            .build()
        workManager.enqueueUniqueWork(SyncWorker.UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    private companion object {
        val BurstDelay: Duration = Duration.ofSeconds(2)
    }
}
