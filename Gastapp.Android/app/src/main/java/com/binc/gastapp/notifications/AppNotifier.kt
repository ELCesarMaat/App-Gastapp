package com.binc.gastapp.notifications

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.binc.gastapp.MainActivity
import com.binc.gastapp.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Notificaciones del telefono (ReminderNotificationService de MAUI y los avisos del
 * reloj). Cada metodo devuelve false si no se pudo mostrar: sin permiso (Android 13+) o
 * con las notificaciones de la app apagadas en el sistema.
 */
interface AppNotifier {
    /** El permiso (Android 13+) y el interruptor de la app en el sistema. */
    fun areEnabled(): Boolean

    /** SendTestNotificationAsync de MAUI. */
    fun sendTest(): Boolean

    /** Recordatorio periodico de registrar gastos ("Gastapp te acompaña"). */
    fun showReminder(message: String): Boolean

    /** Aviso de corte o de pago de una tarjeta. */
    fun showCardReminder(notificationId: Int, title: String, text: String): Boolean

    /** Gasto capturado en el reloj ("$85 · Café"). Siempre el mismo id: el nuevo reemplaza al anterior. */
    fun showWatchExpense(text: String): Boolean
}

/**
 * Los tres canales de la app; se crean al arrancar (GastappApplication). Todos suenan con
 * el "cobro" de res/raw/notificacion_moneda.wav (lo genera tools/sonidos/moneda.py).
 *
 * Android no deja cambiar el sonido de un canal que ya existe: por eso los ids llevan
 * version. Si cambia el sonido, se sube [VERSION] y los canales viejos se borran solos
 * (el usuario pierde lo que hubiera ajustado en ellos, como la importancia).
 */
object NotificationChannels {
    private const val VERSION = 3

    const val REMINDERS = "recordatorios_v$VERSION"
    const val CARDS = "tarjetas_v$VERSION"
    const val WATCH = "reloj_v$VERSION"

    /**
     * Los de versiones anteriores: la 1 no llevaba sufijo ni sonido propio y la 2 sonaba
     * con la primera moneda, que al usuario no le gusto.
     */
    private val Obsolete = listOf("recordatorios", "tarjetas", "reloj") +
        (2 until VERSION).flatMap { listOf("recordatorios_v$it", "tarjetas_v$it", "reloj_v$it") }

    fun create(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        Obsolete.forEach(manager::deleteNotificationChannel)
        val sound = Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(context.packageName)
            .appendPath(R.raw.notificacion_moneda.toString())
            .build()
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        manager.createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(REMINDERS, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName("Recordatorios")
                    .setDescription("Avisos para que registres tus gastos del día")
                    .setSound(sound, attributes)
                    .build(),
                NotificationChannelCompat.Builder(CARDS, NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName("Tarjetas")
                    .setDescription("Fechas de corte y de pago de tus tarjetas de crédito")
                    .setSound(sound, attributes)
                    .build(),
                NotificationChannelCompat.Builder(WATCH, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName("Reloj")
                    .setDescription("Gastos que registras desde tu reloj")
                    .setSound(sound, attributes)
                    .build(),
            ),
        )
    }
}

/**
 * Ids fijos, fuera de los rangos de los demas, como en MAUI: recordatorio 6100, prueba
 * 7100, reloj 7200 y tarjetas desde 8000.
 */
object NotificationIds {
    const val REMINDER = 6100
    const val TEST = 7100
    const val WATCH_EXPENSE = 7200
    const val CARD_BASE = 8000
}

@Singleton
class AndroidAppNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppNotifier {

    private val manager = NotificationManagerCompat.from(context)

    override fun areEnabled(): Boolean = manager.areNotificationsEnabled()

    override fun sendTest(): Boolean = show(
        NotificationIds.TEST,
        NotificationChannels.REMINDERS,
        "Prueba de recordatorio",
        "Este es un recordatorio de prueba. Si hiciste un gasto, regístralo en Gastapp.",
    )

    override fun showReminder(message: String): Boolean = show(
        NotificationIds.REMINDER,
        NotificationChannels.REMINDERS,
        "Gastapp te acompaña",
        "$message Recuerda registrar tus gastos de hoy.",
    )

    override fun showCardReminder(notificationId: Int, title: String, text: String): Boolean =
        show(notificationId, NotificationChannels.CARDS, title, text)

    override fun showWatchExpense(text: String): Boolean =
        show(NotificationIds.WATCH_EXPENSE, NotificationChannels.WATCH, "Gasto desde el reloj registrado", text)

    // areNotificationsEnabled() ya da false sin el permiso POST_NOTIFICATIONS.
    @SuppressLint("MissingPermission")
    private fun show(id: Int, channel: String, title: String, text: String): Boolean {
        if (!areEnabled()) return false
        // Por si se llama antes de que GastappApplication los cree (es idempotente).
        NotificationChannels.create(context)
        val notification: Notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.mipmap.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()
        manager.notify(id, notification)
        return true
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
