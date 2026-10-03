package com.binc.gastapp.sync

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lo que llaman los repositorios despues de cada escritura local: "hay algo pendiente
 * de subir". La escritura ya quedo en Room con isSynced = false; esto solo pide que se
 * sincronice cuando se pueda.
 *
 * En la Fase 3 la implementacion encola el trabajo unico "sync" de WorkManager
 * (ExistingWorkPolicy.KEEP, con red y un retraso corto para juntar rafagas).
 */
interface SyncScheduler {
    fun requestSync()
}

/** Fase 2: todavia no hay red. Lo pendiente se queda marcado y se sube en la Fase 3. */
@Singleton
class NoOpSyncScheduler @Inject constructor() : SyncScheduler {
    override fun requestSync() = Unit
}
