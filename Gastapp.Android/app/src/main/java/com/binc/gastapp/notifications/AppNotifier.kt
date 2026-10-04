package com.binc.gastapp.notifications

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.binc.gastapp.MainActivity
import com.binc.gastapp.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Notificaciones del telefono. Por ahora solo lo que usa Ajustes: saber si estan
 * permitidas y mandar la de prueba. Los recordatorios periodicos y los avisos de
 * tarjeta llegan en la Fase 5.1, con sus canales ("Tarjetas" y "Reloj").
 */
interface AppNotifier {
    /** El permiso (Android 13+) y el interruptor de la app en el sistema. */
    fun areEnabled(): Boolean

    /** SendTestNotificationAsync de MAUI. false si las notificaciones estan apagadas. */
    fun sendTest(): Boolean
}

@Singleton
class AndroidAppNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppNotifier {

    private val manager = NotificationManagerCompat.from(context)

    override fun areEnabled(): Boolean = manager.areNotificationsEnabled()

    // areNotificationsEnabled() ya da false sin el permiso POST_NOTIFICATIONS.
    @SuppressLint("MissingPermission")
    override fun sendTest(): Boolean {
        if (!areEnabled()) return false
        ensureChannels()
        val notification = NotificationCompat.Builder(context, RemindersChannel)
            .setSmallIcon(R.mipmap.ic_launcher_monochrome)
            .setContentTitle("Prueba de recordatorio")
            .setContentText("Este es un recordatorio de prueba. Si hiciste un gasto, regístralo en Gastapp.")
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()
        manager.notify(TestNotificationId, notification)
        return true
    }

    private fun ensureChannels() {
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(RemindersChannel, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Recordatorios")
                .setDescription("Avisos para que registres tus gastos del día")
                .build(),
        )
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val RemindersChannel = "recordatorios"
        const val TestNotificationId = 7100
    }
}
