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

    /**
     * Aviso de corte o de pago de una tarjeta. [tag] (la tarjeta) + [notificationId] (el
     * tipo de aviso) lo identifican: el de una tarjeta no reemplaza al de otra.
     */
    fun showCardReminder(tag: String, notificationId: Int, title: String, text: String): Boolean

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
                    .setName(context.getString(R.string.channel_reminders))
                    .setDescription(context.getString(R.string.channel_reminders_description))
                    .setSound(sound, attributes)
                    .build(),
                NotificationChannelCompat.Builder(CARDS, NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName(context.getString(R.string.channel_cards))
                    .setDescription(context.getString(R.string.channel_cards_description))
                    .setSound(sound, attributes)
                    .build(),
                NotificationChannelCompat.Builder(WATCH, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(context.getString(R.string.channel_watch))
                    .setDescription(context.getString(R.string.channel_watch_description))
                    .setSound(sound, attributes)
                    .build(),
            ),
        )
    }
}

/**
 * Ids fijos, fuera de los rangos de los demas, como en MAUI: recordatorio 6100, prueba
 * 7100, reloj 7200 y tarjetas desde 8000 (8000 + tipo de aviso, con la tarjeta como tag;
 * las alarmas de tarjeta usan 8000-8099 como requestCode, que es otro espacio).
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
        context.getString(R.string.test_reminder_title),
        context.getString(R.string.test_reminder_text),
    )

    override fun showReminder(message: String): Boolean = show(
        NotificationIds.REMINDER,
        NotificationChannels.REMINDERS,
        context.getString(R.string.reminder_title),
        context.getString(R.string.reminder_text, message),
    )

    override fun showCardReminder(tag: String, notificationId: Int, title: String, text: String): Boolean =
        show(notificationId, NotificationChannels.CARDS, title, text, tag)

    override fun showWatchExpense(text: String): Boolean =
        show(NotificationIds.WATCH_EXPENSE, NotificationChannels.WATCH, context.getString(R.string.watch_expense_registered), text)

    // areNotificationsEnabled() ya da false sin el permiso POST_NOTIFICATIONS.
    @SuppressLint("MissingPermission")
    private fun show(id: Int, channel: String, title: String, text: String, tag: String? = null): Boolean {
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
        manager.notify(tag, id, notification)
        return true
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
