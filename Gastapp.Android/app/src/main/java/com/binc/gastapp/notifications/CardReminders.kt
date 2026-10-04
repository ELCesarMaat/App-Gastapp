package com.binc.gastapp.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.binc.gastapp.data.repository.CreditCardRepository
import com.binc.gastapp.data.repository.UserRepository
import com.binc.gastapp.domain.cards.CardSummary
import com.binc.gastapp.ui.format.MexicoLocale
import com.binc.gastapp.ui.format.formatMoney
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** Un aviso de tarjeta por mostrar a la hora [at]. */
data class CardReminder(
    val notificationId: Int,
    val title: String,
    val text: String,
    val at: LocalDateTime,
)

/** "05 de octubre", el "dd 'de' MMMM" de MAUI. */
private val ReminderDateFormat = DateTimeFormatter.ofPattern("dd 'de' MMMM", MexicoLocale)

private val CutOffTime: LocalTime = LocalTime.of(9, 0)
private val PaymentWarningTime: LocalTime = LocalTime.of(9, 0)
private val PaymentDayTime: LocalTime = LocalTime.of(8, 30)

/** Hasta 100 ids (8000-8099), como en MAUI: 33 tarjetas con sus tres avisos. */
private const val MaxCardReminders = 100

/**
 * ScheduleCreditCardRemindersAsync de MAUI, como funcion pura. Por cada tarjeta:
 *  - Corte: 2 dias antes a las 9:00.
 *  - Fecha limite: 3 dias antes a las 9:00, con el saldo si hay deuda.
 *  - Dia de pago: a las 8:30, solo si hay deuda.
 * Lo que ya paso no se programa. Los resumenes son solo de tarjetas vigentes (las
 * borradas no llegan). La fecha limite es la del resumen, que ya salta al mes
 * siguiente cuando el corte quedo pagado: no se recuerda un pago ya hecho.
 */
fun planCardReminders(summaries: List<CardSummary>, now: LocalDateTime): List<CardReminder> {
    val reminders = mutableListOf<CardReminder>()
    for (summary in summaries) {
        val card = summary.card
        val hasDebt = summary.totalDebt.signum() > 0

        val cutOffAt = summary.nextCutOffDate.minusDays(2).atTime(CutOffTime)
        if (cutOffAt.isAfter(now)) {
            reminders += CardReminder(
                notificationId = 0,
                title = "Próximo corte: ${card.cardName}",
                text = "Tu tarjeta ${card.bankName} corta el ${reminderDate(summary.nextCutOffDate)}. " +
                    "Revisa tus compras para cerrar tu ciclo.",
                at = cutOffAt,
            )
        }

        val warningAt = summary.nextPaymentDueDate.minusDays(3).atTime(PaymentWarningTime)
        if (warningAt.isAfter(now)) {
            val amount = if (hasDebt) " Saldo a pagar: ${formatMoney(summary.totalDebt)}" else ""
            reminders += CardReminder(
                notificationId = 0,
                title = "Fecha límite de pago: ${card.cardName}",
                text = "Tu pago vence el ${reminderDate(summary.nextPaymentDueDate)}.$amount " +
                    "Paga a tiempo para no generar intereses.",
                at = warningAt,
            )
        }

        val paymentDayAt = summary.nextPaymentDueDate.atTime(PaymentDayTime)
        if (paymentDayAt.isAfter(now) && hasDebt) {
            reminders += CardReminder(
                notificationId = 0,
                title = "¡Hoy vence tu tarjeta ${card.cardName}!",
                text = "Hoy es la fecha límite de pago para ${card.bankName}. " +
                    "Saldo pendiente: ${formatMoney(summary.totalDebt)}.",
                at = paymentDayAt,
            )
        }
    }
    return reminders.take(MaxCardReminders).mapIndexed { i, r -> r.copy(notificationId = NotificationIds.CARD_BASE + i) }
}

private fun reminderDate(date: LocalDate): String = date.format(ReminderDateFormat)

/** Los avisos que tocan ahora mismo, con las tarjetas y gastos de Room. */
@Singleton
class CardReminderPlanner @Inject constructor(
    private val cards: CreditCardRepository,
    private val clock: Clock,
) {
    suspend fun currentPlan(): List<CardReminder> {
        val now = LocalDateTime.now(clock)
        return planCardReminders(cards.observeSummaries(now.toLocalDate()).first(), now)
    }

    fun plan(summaries: List<CardSummary>): List<CardReminder> = planCardReminders(summaries, LocalDateTime.now(clock))
}

/** Deja programados exactamente estos avisos y quita los demas. */
interface CardReminderScheduler {
    suspend fun reconcile(reminders: List<CardReminder>)
}

/**
 * Un trabajo de una sola vez por aviso, con el retraso calculado. No es exacto al minuto
 * (Doze lo puede retrasar un poco), y para un aviso de "faltan 3 dias" no hace falta. Si
 * algun dia se exige, se cambia por AlarmManager.setAndAllowWhileIdle.
 */
@Singleton
class WorkManagerCardReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) : CardReminderScheduler {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    override suspend fun reconcile(reminders: List<CardReminder>) {
        workManager.cancelAllWorkByTag(TAG)
        val now = LocalDateTime.now(clock)
        for (reminder in reminders) {
            val delay = Duration.between(now, reminder.at)
            if (delay.isNegative) continue
            val request = OneTimeWorkRequestBuilder<CardReminderWorker>()
                .setInitialDelay(delay)
                .setInputData(
                    workDataOf(
                        CardReminderWorker.KEY_ID to reminder.notificationId,
                        CardReminderWorker.KEY_TITLE to reminder.title,
                        CardReminderWorker.KEY_TEXT to reminder.text,
                    ),
                )
                .addTag(TAG)
                .build()
            workManager.enqueueUniqueWork("$TAG-${reminder.notificationId}", ExistingWorkPolicy.REPLACE, request)
        }
    }

    companion object {
        const val TAG = "card_reminder"
    }
}

@HiltWorker
class CardReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val users: UserRepository,
    private val notifier: AppNotifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Se cerro la sesion despues de programarlo.
        if (users.getUser() == null) return Result.success()
        val title = inputData.getString(KEY_TITLE) ?: return Result.success()
        val text = inputData.getString(KEY_TEXT).orEmpty()
        notifier.showCardReminder(inputData.getInt(KEY_ID, NotificationIds.CARD_BASE), title, text)
        return Result.success()
    }

    companion object {
        const val KEY_ID = "id"
        const val KEY_TITLE = "title"
        const val KEY_TEXT = "text"
    }
}
