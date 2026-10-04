package com.binc.gastapp.notifications

import android.content.Intent
import android.util.Log
import com.binc.gastapp.data.repository.CreditCardRepository
import com.binc.gastapp.data.repository.UserRepository
import com.binc.gastapp.domain.cards.CardSummary
import com.binc.gastapp.ui.format.MexicoLocale
import com.binc.gastapp.ui.format.formatMoney
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** Los tres avisos de una tarjeta; el orden da el id de la notificacion (8000, 8001, 8002). */
enum class CardReminderKind { CUT_OFF, PAYMENT_WARNING, PAYMENT_DAY }

/**
 * Un aviso de tarjeta por mostrar a la hora [at]. [requestCode] es el de su alarma (8000 +
 * su lugar en el plan); la notificacion se identifica aparte, por tarjeta y tipo
 * ([notificationTag], [notificationId]), para que el aviso de una tarjeta nunca reemplace
 * en la bandeja al de otra.
 */
data class CardReminder(
    val requestCode: Int,
    val cardId: String,
    val kind: CardReminderKind,
    val title: String,
    val text: String,
    val at: LocalDateTime,
) {
    val notificationTag: String get() = cardId
    val notificationId: Int get() = NotificationIds.CARD_BASE + kind.ordinal

    /** Mismo aviso (tarjeta, tipo y hora), aunque el texto haya cambiado. */
    fun isSameAs(other: CardReminder): Boolean = cardId == other.cardId && kind == other.kind && at == other.at
}

/** "05 de octubre", el "dd 'de' MMMM" de MAUI. */
private val ReminderDateFormat = DateTimeFormatter.ofPattern("dd 'de' MMMM", MexicoLocale)

private val CutOffTime: LocalTime = LocalTime.of(9, 0)
private val PaymentWarningTime: LocalTime = LocalTime.of(9, 0)
private val PaymentDayTime: LocalTime = LocalTime.of(8, 30)

/** Hasta 100 ids (8000-8099), como en MAUI: 33 tarjetas con sus tres avisos. */
internal const val MaxCardReminders = 100

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
                requestCode = 0,
                cardId = card.creditCardId,
                kind = CardReminderKind.CUT_OFF,
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
                requestCode = 0,
                cardId = card.creditCardId,
                kind = CardReminderKind.PAYMENT_WARNING,
                title = "Fecha límite de pago: ${card.cardName}",
                text = "Tu pago vence el ${reminderDate(summary.nextPaymentDueDate)}.$amount " +
                    "Paga a tiempo para no generar intereses.",
                at = warningAt,
            )
        }

        val paymentDayAt = summary.nextPaymentDueDate.atTime(PaymentDayTime)
        if (paymentDayAt.isAfter(now) && hasDebt) {
            reminders += CardReminder(
                requestCode = 0,
                cardId = card.creditCardId,
                kind = CardReminderKind.PAYMENT_DAY,
                title = "¡Hoy vence tu tarjeta ${card.cardName}!",
                text = "Hoy es la fecha límite de pago para ${card.bankName}. " +
                    "Saldo pendiente: ${formatMoney(summary.totalDebt)}.",
                at = paymentDayAt,
            )
        }
    }
    return reminders.take(MaxCardReminders).mapIndexed { i, r -> r.copy(requestCode = NotificationIds.CARD_BASE + i) }
}

private fun reminderDate(date: LocalDate): String = date.format(ReminderDateFormat)

/** Los avisos que tocan ahora mismo, con las tarjetas y gastos de Room. */
@Singleton
class CardReminderPlanner @Inject constructor(
    private val cards: CreditCardRepository,
    private val clock: Clock,
) {
    suspend fun currentPlan(): List<CardReminder> = planAt(LocalDateTime.now(clock))

    /** El plan como se veria en [now], con los datos de hoy. */
    suspend fun planAt(now: LocalDateTime): List<CardReminder> =
        planCardReminders(cards.observeSummaries(now.toLocalDate()).first(), now)

    fun plan(summaries: List<CardSummary>): List<CardReminder> = planCardReminders(summaries, LocalDateTime.now(clock))
}

/** Deja programados exactamente estos avisos y quita los demas. */
interface CardReminderScheduler {
    suspend fun reconcile(reminders: List<CardReminder>)
}

/**
 * Una alarma por aviso (ver [AppAlarms]: con WorkManager Android los retrasaba hasta abrir
 * la app). El requestCode es el id de la notificacion: reconciliar quita todas las del
 * rango que ya no estan en el plan.
 */
@Singleton
class AlarmCardReminderScheduler @Inject constructor(
    private val alarms: AppAlarms,
    private val clock: Clock,
) : CardReminderScheduler {

    override suspend fun reconcile(reminders: List<CardReminder>) {
        val now = LocalDateTime.now(clock)
        val wanted = reminders.filter { it.at.isAfter(now) }.associateBy { it.requestCode }
        for (code in NotificationIds.CARD_BASE until NotificationIds.CARD_BASE + MaxCardReminders) {
            if (code !in wanted) alarms.cancel(code, AppAlarms.ACTION_CARD_REMINDER)
        }
        for (reminder in wanted.values) {
            alarms.set(reminder.requestCode, AppAlarms.ACTION_CARD_REMINDER, reminder.at.atZone(clock.zone).toInstant()) {
                putReminder(reminder)
            }
        }
    }

    companion object {
        private const val EXTRA_REQUEST_CODE = "requestCode"
        private const val EXTRA_CARD_ID = "cardId"
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_AT = "at"

        fun Intent.putReminder(reminder: CardReminder): Intent = this
            .putExtra(EXTRA_REQUEST_CODE, reminder.requestCode)
            .putExtra(EXTRA_CARD_ID, reminder.cardId)
            .putExtra(EXTRA_KIND, reminder.kind.name)
            .putExtra(EXTRA_TITLE, reminder.title)
            .putExtra(EXTRA_TEXT, reminder.text)
            .putExtra(EXTRA_AT, reminder.at.toString())

        /** El aviso que lleva la alarma, o null si le faltan datos. */
        fun Intent.cardReminder(): CardReminder? {
            val cardId = getStringExtra(EXTRA_CARD_ID) ?: return null
            val kind = getStringExtra(EXTRA_KIND)?.let { name -> CardReminderKind.entries.firstOrNull { it.name == name } } ?: return null
            val at = getStringExtra(EXTRA_AT)?.let(LocalDateTime::parse) ?: return null
            return CardReminder(
                requestCode = getIntExtra(EXTRA_REQUEST_CODE, NotificationIds.CARD_BASE),
                cardId = cardId,
                kind = kind,
                title = getStringExtra(EXTRA_TITLE) ?: return null,
                text = getStringExtra(EXTRA_TEXT).orEmpty(),
                at = at,
            )
        }
    }
}

/**
 * Sono la alarma de un aviso de tarjeta. Antes de mostrarlo se vuelve a calcular con los
 * datos de este momento: si ya se pago o se borro la tarjeta no se avisa, y el saldo sale
 * actualizado (el texto de la alarma es de cuando se programo). Despues se reprograma todo,
 * por si el proceso no vive para hacerlo StartupCoordinator.
 */
@Singleton
class CardReminderFirer @Inject constructor(
    private val users: UserRepository,
    private val planner: CardReminderPlanner,
    private val scheduler: CardReminderScheduler,
    private val notifier: AppNotifier,
) {
    suspend fun show(intent: Intent) {
        // Se cerro la sesion despues de programarlo.
        if (users.getUser() == null) return
        val scheduled = with(AlarmCardReminderScheduler) { intent.cardReminder() } ?: return
        val current = try {
            // Un segundo antes de su hora, el aviso todavia esta en el plan si sigue aplicando.
            planner.planAt(scheduled.at.minusSeconds(1)).firstOrNull { it.isSameAs(scheduled) }
        } catch (e: Exception) {
            // Mejor el texto de cuando se programo que no avisar.
            Log.w(TAG, "No se pudo recalcular el aviso de tarjeta: ${e.message}", e)
            scheduled
        }
        if (current != null) {
            notifier.showCardReminder(current.notificationTag, current.notificationId, current.title, current.text)
        }
        scheduler.reconcile(planner.currentPlan())
    }

    private companion object {
        const val TAG = "GastappAlarmas"
    }
}
