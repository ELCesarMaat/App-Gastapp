package com.binc.gastapp.startup

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.binc.gastapp.data.local.LocalPurge
import com.binc.gastapp.data.prefs.SettingsStore
import com.binc.gastapp.data.repository.CreditCardRepository
import com.binc.gastapp.data.repository.UserRepository
import com.binc.gastapp.di.ApplicationScope
import com.binc.gastapp.notifications.CardReminderPlanner
import com.binc.gastapp.notifications.CardReminderScheduler
import com.binc.gastapp.notifications.ReminderScheduler
import com.binc.gastapp.wear.WearPublisher
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Lo que App.OnStart hacia en MAUI y no es una pantalla. Lo arranca GastappApplication
 * en cada proceso (tambien cuando WorkManager o el reloj despiertan la app): todo aqui es
 * barato e idempotente.
 *
 *  - Recordatorios: se programan o se quitan cada vez que cambian los ajustes o la sesion.
 *  - Avisos de tarjeta: se recalculan al cambiar tarjetas, gastos o el dia.
 *  - Reloj: publica el dia y las categorias cuando cambian.
 *  - Purga de lo borrado hace mas de 30 dias, y el mantenimiento diario para cuando la app
 *    no se abre (que tambien deja programados los avisos del mes siguiente).
 *
 * Refrescar el token, subir y bajar lo pide MainActivity al abrirse (no tiene caso en un
 * proceso de fondo) y la busqueda de actualizacion es de la UI (pregunta al usuario).
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Singleton
class StartupCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val dayClock: DayClock,
    private val settingsStore: SettingsStore,
    private val users: UserRepository,
    private val cards: CreditCardRepository,
    private val reminderScheduler: ReminderScheduler,
    private val cardPlanner: CardReminderPlanner,
    private val cardScheduler: CardReminderScheduler,
    private val wearPublisher: WearPublisher,
    private val purge: LocalPurge,
) {
    private var started = false

    fun start() {
        if (started) return
        started = true

        launchSafely("cambio de dia") { dayClock.runMidnightTicks() }

        launchSafely("recordatorios") {
            combine(settingsStore.settings, users.observeUser().map { it != null }) { settings, hasAccount ->
                (settings.remindersEnabled && hasAccount) to settings.reminderFrequencyHours
            }.distinctUntilChanged().collect { (active, hours) -> reminderScheduler.apply(active, hours) }
        }

        launchSafely("avisos de tarjeta") {
            dayClock.today
                .flatMapLatest { cards.observeSummaries(it) }
                // Un pago o un alta con varias MSI son varias escrituras seguidas.
                .debounce(1_000)
                .map(cardPlanner::plan)
                .distinctUntilChanged()
                .collect { cardScheduler.reconcile(it) }
        }

        launchSafely("reloj") { wearPublisher.run() }

        launchSafely("purga") { purge.purge() }

        launchSafely("mantenimiento diario") {
            val request = PeriodicWorkRequestBuilder<MaintenanceWorker>(Duration.ofDays(1)).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(MaintenanceWorker.UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }

    /** Si una tarea falla, se registra y las demas siguen (el alcance es SupervisorJob). */
    private fun launchSafely(name: String, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Fallo la tarea de arranque '$name': ${e.message}", e)
            }
        }
    }

    private companion object {
        const val TAG = "GastappArranque"
    }
}

/**
 * Una vez al dia, aunque la app no se abra: purga lo borrado hace mas de 30 dias y deja
 * programados los avisos de tarjeta (cuando pasa un corte, el siguiente se programa aqui).
 */
@HiltWorker
class MaintenanceWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val purge: LocalPurge,
    private val cardPlanner: CardReminderPlanner,
    private val cardScheduler: CardReminderScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        purge.purge()
        cardScheduler.reconcile(cardPlanner.currentPlan())
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "maintenance"
    }
}
