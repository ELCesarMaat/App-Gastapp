package com.binc.gastapp.notifications

import com.binc.gastapp.data.prefs.ReminderSchedule
import com.binc.gastapp.data.prefs.SettingsStore
import com.binc.gastapp.data.repository.UserRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
 * La primera hora de la serie [anchor] + k * [hours] (k >= 0) que cae despues de [now].
 * Si se perdio uno (telefono apagado), no se repite: se salta al siguiente. Nunca queda a
 * mas de [hours] de [now]: si el reloj se atraso, la serie empieza de nuevo desde ahora.
 */
fun nextReminderAt(anchor: Instant, hours: Int, now: Instant): Instant {
    val step = Duration.ofHours(hours.toLong())
    if (anchor.isAfter(now)) return minOf(anchor, now.plus(step))
    val passed = Duration.between(anchor, now).toMillis() / step.toMillis() + 1
    return anchor.plus(step.multipliedBy(passed))
}

/**
 * Un recordatorio cada N horas (1 a 24) con una alarma que, al sonar, pone la siguiente
 * (ver [AppAlarms]: con WorkManager Android los retrasaba hasta abrir la app). La hora
 * de la siguiente se guarda en SettingsStore para seguir la cuenta tras reiniciar.
 *
 * Diferencias con MAUI, a proposito:
 *  - MAUI reprogramaba en cada arranque y el primer aviso llegaba 5 minutos despues de
 *    abrir la app. Aqui solo se reprograma si cambio la frecuencia, y el primero llega a
 *    las N horas.
 *  - MAUI usaba 6 ids (uno por mensaje) y se podian apilar 6 avisos; aqui el nuevo
 *    reemplaza al anterior.
 */
@Singleton
class AlarmReminderScheduler @Inject constructor(
    private val alarms: AppAlarms,
    private val settingsStore: SettingsStore,
    private val users: UserRepository,
    private val notifier: AppNotifier,
    private val clock: Clock,
) : ReminderScheduler {

    // StartupCoordinator y la alarma que acaba de sonar pueden llegar a la vez al arrancar el proceso.
    private val mutex = Mutex()

    override suspend fun apply(active: Boolean, frequencyHours: Int) = schedule(active, frequencyHours, keepIfSet = true)

    /** Como [apply], pero vuelve a poner la alarma aunque siga puesta (cambio la hora o el permiso). */
    suspend fun restore(active: Boolean, frequencyHours: Int) = schedule(active, frequencyHours, keepIfSet = false)

    /** Sono la alarma: muestra el recordatorio que toca y pone el siguiente. */
    suspend fun onAlarm() = mutex.withLock {
        alarms.cancel(NotificationIds.REMINDER, AppAlarms.ACTION_REMINDER)
        val settings = settingsStore.settings.first()
        // Pudo quedar puesta justo antes de apagarlos o de cerrar sesion.
        if (!settings.remindersEnabled || users.getUser() == null) {
            settingsStore.setReminderSchedule(null)
            return@withLock
        }
        if (notifier.areEnabled()) notifier.showReminder(reminderMessage(settingsStore.takeReminderIndex()))
        val hours = settings.reminderFrequencyHours.coerceIn(1, 24)
        val current = settingsStore.reminderSchedule()
        val now = clock.instant()
        // Se cuenta desde la hora que tocaba, no desde ahora: si sono tarde, la serie no se corre.
        val anchor = if (current?.hours == hours) current.nextAt else now
        set(nextReminderAt(anchor, hours, now), hours)
    }

    private suspend fun schedule(active: Boolean, frequencyHours: Int, keepIfSet: Boolean) = mutex.withLock {
        if (!active) {
            alarms.cancel(NotificationIds.REMINDER, AppAlarms.ACTION_REMINDER)
            settingsStore.setReminderSchedule(null)
            return@withLock
        }
        val hours = frequencyHours.coerceIn(1, 24)
        val current = settingsStore.reminderSchedule()
        // Ya esta puesta con esa frecuencia: volver a ponerla reiniciaria la cuenta.
        if (keepIfSet && current?.hours == hours && alarms.isSet(NotificationIds.REMINDER, AppAlarms.ACTION_REMINDER)) {
            return@withLock
        }
        val now = clock.instant()
        val next = if (current?.hours == hours) {
            // Misma frecuencia pero se perdio la alarma (reinicio): sigue la serie.
            nextReminderAt(current.nextAt, hours, now)
        } else {
            now.plus(Duration.ofHours(hours.toLong()))
        }
        set(next, hours)
    }

    private suspend fun set(at: Instant, hours: Int) {
        settingsStore.setReminderSchedule(ReminderSchedule(at, hours))
        alarms.set(NotificationIds.REMINDER, AppAlarms.ACTION_REMINDER, at)
    }
}
