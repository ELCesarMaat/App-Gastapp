package com.binc.gastapp

import android.app.Application
import android.content.res.Configuration as AndroidConfiguration
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.binc.gastapp.notifications.NotificationChannels
import com.binc.gastapp.startup.DayClock
import com.binc.gastapp.startup.StartupCoordinator
import com.binc.gastapp.ui.format.AppLocale
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Punto de entrada de Hilt. Tambien configura WorkManager para que sus trabajos reciban
 * dependencias de Hilt; por eso el inicializador automatico de WorkManager esta quitado
 * en el manifiesto.
 *
 * Aqui arranca lo que corre sin pantalla (StartupCoordinator: recordatorios, avisos de
 * tarjeta, reloj, purga). El arranque con sesion (refrescar, subir y bajar) lo pide
 * MainActivity al abrirse, no aqui: este onCreate tambien corre cuando WorkManager o el
 * reloj despiertan la app en segundo plano, y ahi no tiene caso llamar al API.
 */
@HiltAndroidApp
class GastappApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var startup: StartupCoordinator

    @Inject
    lateinit var dayClock: DayClock

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        AppLocale.update(this)
        NotificationChannels.create(this)
        // Al volver de segundo plano se revisa si cambio el dia (DayChangedMessage de MAUI).
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_START) dayClock.refresh() },
        )
        startup.start()
    }

    /** Cambio de idioma o de region en el telefono: montos, fechas y canales en el nuevo. */
    override fun onConfigurationChanged(newConfig: AndroidConfiguration) {
        super.onConfigurationChanged(newConfig)
        AppLocale.update(this)
        NotificationChannels.create(this)
    }
}
