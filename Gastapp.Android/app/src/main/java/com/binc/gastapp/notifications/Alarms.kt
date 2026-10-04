package com.binc.gastapp.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.binc.gastapp.data.prefs.SettingsStore
import com.binc.gastapp.data.repository.UserRepository
import com.binc.gastapp.di.ApplicationScope
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Avisos a una hora fija (recordatorios y tarjetas) con AlarmManager.
 *
 * Hasta la 2.0.1 iban con WorkManager y Android los retrasaba: un trabajo de WorkManager
 * es diferible, y con el telefono en reposo (Doze) o con la app poco usada (cubetas
 * "rare"/"restricted" de App Standby) se juntaba para despues. Muchas veces corria hasta
 * que el usuario abria la app y todos los avisos atrasados llegaban de golpe. Una alarma
 * "allow while idle" si despierta al telefono a su hora.
 *
 * Exacta si hay permiso (USE_EXACT_ALARM en Android 13+, SCHEDULE_EXACT_ALARM en el 12,
 * los dos se conceden solos). Si no, setAndAllowWhileIdle: puede llegar un rato tarde,
 * pero llega aunque la app no se abra.
 *
 * Las alarmas se borran al reiniciar el telefono, al actualizar la app (instalar fuerza
 * la detencion) y al forzar la detencion a mano: [RescheduleReceiver] las vuelve a poner
 * en los dos primeros casos, y StartupCoordinator en cada arranque. Tras forzar la
 * detencion a mano Android no avisa: vuelven al abrir la app.
 */
@Singleton
class AppAlarms @Inject constructor(@ApplicationContext private val context: Context) {

    private val manager: AlarmManager get() = context.getSystemService(AlarmManager::class.java)

    /** Pone (o reemplaza) la alarma [requestCode]; [fill] agrega los extras del aviso. */
    fun set(requestCode: Int, action: String, at: Instant, fill: Intent.() -> Unit = {}) {
        val operation = pendingIntent(requestCode, action, PendingIntent.FLAG_UPDATE_CURRENT, fill) ?: return
        val millis = at.toEpochMilli()
        if (manager.canScheduleExactAlarms()) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, operation)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, operation)
        }
    }

    /**
     * Quita la alarma y su PendingIntent. Tambien se llama al sonar, para que [isSet]
     * signifique "hay una alarma pendiente".
     */
    fun cancel(requestCode: Int, action: String) {
        val existing = pendingIntent(requestCode, action, PendingIntent.FLAG_NO_CREATE) ?: return
        manager.cancel(existing)
        existing.cancel()
    }

    /** Si la alarma sigue puesta. Al reiniciar o forzar la detencion se pierde el PendingIntent. */
    fun isSet(requestCode: Int, action: String): Boolean =
        pendingIntent(requestCode, action, PendingIntent.FLAG_NO_CREATE) != null

    // El PendingIntent se identifica por la accion y el requestCode (los extras no cuentan).
    private fun pendingIntent(requestCode: Int, action: String, flags: Int, fill: Intent.() -> Unit = {}): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, AlarmReceiver::class.java).setAction(action).apply(fill),
            flags or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        const val ACTION_REMINDER = "com.binc.gastapp.action.REMINDER"
        const val ACTION_CARD_REMINDER = "com.binc.gastapp.action.CARD_REMINDER"
    }
}

/** Lo que necesitan los receptores; no se inyectan con @AndroidEntryPoint para no depender del onReceive generado. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AlarmEntryPoint {
    @ApplicationScope
    fun scope(): CoroutineScope
    fun reminderScheduler(): AlarmReminderScheduler
    fun cardPlanner(): CardReminderPlanner
    fun cardScheduler(): CardReminderScheduler
    fun cardReminderFirer(): CardReminderFirer
    fun settingsStore(): SettingsStore
    fun users(): UserRepository
}

/** Corre [block] fuera del hilo principal sin que Android mate el proceso antes de terminar. */
private fun BroadcastReceiver.runAsync(context: Context, name: String, block: suspend AlarmEntryPoint.() -> Unit) {
    val deps = EntryPointAccessors.fromApplication(context, AlarmEntryPoint::class.java)
    val pending = goAsync()
    deps.scope().launch {
        try {
            deps.block()
        } catch (e: Exception) {
            Log.w("GastappAlarmas", "Fallo '$name': ${e.message}", e)
        } finally {
            pending.finish()
        }
    }
}

/** Sono una alarma de aviso. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AppAlarms.ACTION_REMINDER -> runAsync(context, "recordatorio") { reminderScheduler().onAlarm() }
            AppAlarms.ACTION_CARD_REMINDER -> runAsync(context, "aviso de tarjeta") { cardReminderFirer().show(intent) }
        }
    }
}

/**
 * Vuelve a poner las alarmas cuando el sistema las borra o las deja mal: al reiniciar, al
 * actualizar la app, al cambiar la hora o la zona (los avisos de tarjeta son a una hora
 * local) y al cambiar el permiso de alarmas exactas.
 */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runAsync(context, "reprogramar (${intent.action})") {
            val settings = settingsStore().settings.first()
            reminderScheduler().restore(settings.remindersEnabled && users().getUser() != null, settings.reminderFrequencyHours)
            cardScheduler().reconcile(cardPlanner().currentPlan())
        }
    }
}
