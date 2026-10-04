package com.binc.gastapp.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.binc.gastapp.data.prefs.SettingsStore
import com.binc.gastapp.data.repository.UserRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** Los 6 mensajes de ReminderNotificationService (MAUI), en el mismo orden. */
val ReminderMessages = listOf(
    "Tip de ahorro: guarda al menos el 10% de cualquier ingreso extra.",
    "Tip rápido: revisar tus gastos 2 minutos al día evita fugas de dinero.",
    "Idea útil: separa tus gastos fijos de los variables para ajustar mejor tu presupuesto.",
    "Recordatorio: los pequeños gastos diarios también cuentan; regístralos para ver el impacto real.",
    "Tip práctico: antes de comprar algo, espera 24 horas y decide con calma.",
    "Tip inteligente: define un tope semanal para gastos hormiga y respétalo.",
)

/** El mensaje que toca en el turno [index] (rotan). */
fun reminderMessage(index: Int): String = ReminderMessages[Math.floorMod(index, ReminderMessages.size)]

/**
 * Programa o quita los recordatorios periodicos. Lo llama StartupCoordinator cada vez que
 * cambian los ajustes o la sesion.
 */
interface ReminderScheduler {
    /** [active] = recordatorios encendidos y hay una cuenta en el telefono. */
    suspend fun apply(active: Boolean, frequencyHours: Int)
}

/**
 * Un trabajo periodico de WorkManager cada N horas (1 a 24). Sobrevive a cerrar la app
 * y a reiniciar el telefono.
 *
 * Diferencias con MAUI, a proposito:
 *  - MAUI reprogramaba en cada arranque y el primer aviso llegaba 5 minutos despues de
 *    abrir la app. Aqui solo se reprograma si cambio la frecuencia, y el primero llega a
 *    las N horas.
 *  - MAUI usaba 6 ids (uno por mensaje) y se podian apilar 6 avisos; aqui el nuevo
 *    reemplaza al anterior.
 */
@Singleton
class WorkManagerReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReminderScheduler {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    override suspend fun apply(active: Boolean, frequencyHours: Int) {
        if (!active) {
            workManager.cancelUniqueWork(UNIQUE_NAME)
            return
        }
        val hours = frequencyHours.coerceIn(1, 24)
        val tag = frequencyTag(hours)
        // Ya esta programado con esa frecuencia: reencolarlo reiniciaria la cuenta.
        val current = workManager.getWorkInfosForUniqueWorkFlow(UNIQUE_NAME).first()
        if (current.any { !it.state.isFinished && tag in it.tags }) return

        val request = PeriodicWorkRequestBuilder<ReminderWorker>(Duration.ofHours(hours.toLong()))
            .setInitialDelay(Duration.ofHours(hours.toLong()))
            .addTag(tag)
            .build()
        workManager.enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request)
    }

    companion object {
        const val UNIQUE_NAME = "reminders"
        fun frequencyTag(hours: Int) = "reminders_every_${hours}h"
    }
}

/** Muestra el recordatorio que toca, si siguen encendidos y hay cuenta. */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val settingsStore: SettingsStore,
    private val users: UserRepository,
    private val notifier: AppNotifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Pudo quedar encolado justo antes de apagarlos o de cerrar sesion.
        if (!settingsStore.settings.first().remindersEnabled || users.getUser() == null) return Result.success()
        if (!notifier.areEnabled()) return Result.success()
        notifier.showReminder(reminderMessage(settingsStore.takeReminderIndex()))
        return Result.success()
    }
}
