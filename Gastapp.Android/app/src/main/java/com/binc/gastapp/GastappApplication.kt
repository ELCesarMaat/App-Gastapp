package com.binc.gastapp

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Punto de entrada de Hilt. Tambien configura WorkManager para que sus trabajos
 * (SyncWorker) reciban dependencias de Hilt; por eso el inicializador automatico de
 * WorkManager esta quitado en el manifiesto.
 *
 * El arranque con sesion (refrescar, subir y bajar) lo pide MainActivity al abrirse,
 * no aqui: este onCreate tambien corre cuando WorkManager despierta la app en segundo
 * plano, y ahi no tiene caso.
 */
@HiltAndroidApp
class GastappApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
