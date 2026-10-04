package com.binc.gastapp.notifications

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.binc.gastapp.data.local.DbTest
import com.binc.gastapp.data.prefs.InMemoryDataStore
import com.binc.gastapp.data.prefs.SettingsStore
import com.binc.gastapp.domain.cards.buildCardSummary
import com.binc.gastapp.domain.model.CreditCard
import com.binc.gastapp.domain.model.Spending
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

/** Lo que mostraria el telefono, en memoria. */
class RecordingNotifier(var enabled: Boolean = true) : AppNotifier {
    val reminders = mutableListOf<String>()
    /** Tag (la tarjeta), id, titulo y texto de cada aviso de tarjeta. */
    val cards = mutableListOf<CardNotification>()
    val watch = mutableListOf<String>()
    override fun areEnabled() = enabled
    override fun sendTest() = enabled
    override fun showReminder(message: String) = enabled.also { if (it) reminders += message }
    override fun showCardReminder(tag: String, notificationId: Int, title: String, text: String) =
        enabled.also { if (it) cards += CardNotification(tag, notificationId, title, text) }
    override fun showWatchExpense(text: String) = enabled.also { if (it) watch += text }
}

data class CardNotification(val tag: String, val id: Int, val title: String, val text: String)

class NotificationsTest : DbTest() {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val settingsStore = SettingsStore(InMemoryDataStore())
    private val notifier = RecordingNotifier()

    // ------------------------------------------------------------ canales

    @Test
    fun `los canales suenan con el cobro y los de versiones anteriores se borran`() {
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        manager.createNotificationChannel(android.app.NotificationChannel("recordatorios", "Viejo", android.app.NotificationManager.IMPORTANCE_DEFAULT))
        manager.createNotificationChannel(android.app.NotificationChannel("tarjetas_v2", "Viejo", android.app.NotificationManager.IMPORTANCE_HIGH))

        NotificationChannels.create(context)

        val ids = manager.notificationChannels.map { it.id }.toSet()
        assertEquals(setOf(NotificationChannels.REMINDERS, NotificationChannels.CARDS, NotificationChannels.WATCH), ids)
        manager.notificationChannels.forEach {
            assertTrue("${it.id} sin sonido propio", it.sound.toString().endsWith("/${com.binc.gastapp.R.raw.notificacion_moneda}"))
        }
    }

    // ------------------------------------------------------------ recordatorios

    private val alarmManager: AlarmManager get() = context.getSystemService(AlarmManager::class.java)
    private val alarms by lazy { AppAlarms(context) }
    private val reminderClock = MutableClock(clock.instant(), zone)
    private val reminderScheduler by lazy { AlarmReminderScheduler(alarms, settingsStore, users, notifier, strings, reminderClock) }

    private fun scheduledAlarms(action: String) =
        shadowOf(alarmManager).scheduledAlarms.filter { shadowOf(it.operation).savedIntent.action == action }

    private fun reminderAlarm() = scheduledAlarms(AppAlarms.ACTION_REMINDER).single()

    @Test
    fun `los recordatorios rotan los 6 mensajes de MAUI`() = runTest {
        seedBasics()
        repeat(7) { reminderScheduler.onAlarm() }
        assertEquals(7, notifier.reminders.size)
        assertEquals(ReminderMessages.map { strings.get(it) }, notifier.reminders.take(6))
        assertEquals("Despues del sexto vuelve al primero", strings.get(ReminderMessages[0]), notifier.reminders[6])
        assertEquals(ReminderMessages[1], reminderMessage(-5))
    }

    @Test
    fun `sin cuenta, apagados o sin permiso no se muestra nada`() = runTest {
        reminderScheduler.onAlarm()
        assertTrue("Sin cuenta", notifier.reminders.isEmpty())
        assertTrue("Sin cuenta no se pone la siguiente", scheduledAlarms(AppAlarms.ACTION_REMINDER).isEmpty())

        seedBasics()
        settingsStore.setReminders(enabled = false, frequencyHours = 4)
        reminderScheduler.onAlarm()
        assertTrue("Apagados", notifier.reminders.isEmpty())

        settingsStore.setReminders(enabled = true, frequencyHours = 4)
        notifier.enabled = false
        reminderScheduler.onAlarm()
        assertTrue("Sin permiso", notifier.reminders.isEmpty())
        assertEquals("Sin permiso no se gasta el turno del mensaje", 0, settingsStore.takeReminderIndex())
        assertEquals("Sin permiso la serie sigue", 1, scheduledAlarms(AppAlarms.ACTION_REMINDER).size)
    }

    @Test
    fun `se programa una sola vez por frecuencia y se quita al apagarlos`() = runTest {
        val start = reminderClock.instant()
        reminderScheduler.apply(active = true, frequencyHours = 4)
        assertEquals(start.plus(Duration.ofHours(4)).toEpochMilli(), reminderAlarm().triggerAtMs)

        // Abrir la app otra vez (una hora despues) no reinicia la cuenta.
        reminderClock.advance(Duration.ofHours(1))
        reminderScheduler.apply(active = true, frequencyHours = 4)
        assertEquals(start.plus(Duration.ofHours(4)).toEpochMilli(), reminderAlarm().triggerAtMs)

        reminderScheduler.apply(active = true, frequencyHours = 12)
        assertEquals(reminderClock.instant().plus(Duration.ofHours(12)).toEpochMilli(), reminderAlarm().triggerAtMs)

        reminderScheduler.apply(active = false, frequencyHours = 12)
        assertTrue(scheduledAlarms(AppAlarms.ACTION_REMINDER).isEmpty())
        assertEquals(null, settingsStore.reminderSchedule())
    }

    @Test
    fun `la alarma despierta al telefono en reposo y es exacta si hay permiso`() = runTest {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        reminderScheduler.apply(active = true, frequencyHours = 4)
        assertTrue(reminderAlarm().isAllowWhileIdle)
        assertEquals(ShadowAlarmManager.WINDOW_EXACT, reminderAlarm().windowLengthMs)

        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        reminderScheduler.restore(active = true, frequencyHours = 4)
        assertTrue("Sin permiso de exactas, igual sale en reposo", reminderAlarm().isAllowWhileIdle)
    }

    @Test
    fun `al sonar tarde la serie no se corre, y tras reiniciar sigue sin repetir los perdidos`() = runTest {
        seedBasics()
        val start = reminderClock.instant()
        reminderScheduler.apply(active = true, frequencyHours = 4)

        // Sono 20 minutos tarde: la siguiente es a las 8 horas del inicio, no a las 4h20.
        reminderClock.advance(Duration.ofHours(4).plusMinutes(20))
        reminderScheduler.onAlarm()
        assertEquals(1, notifier.reminders.size)
        assertEquals(start.plus(Duration.ofHours(8)).toEpochMilli(), reminderAlarm().triggerAtMs)

        // Telefono apagado de las 7h a las 13h: reiniciar borra la alarma; no se recuperan
        // las de las 8h y las 12h, sigue en la de las 16h.
        reminderClock.advance(Duration.ofHours(9).minusMinutes(20))
        alarms.cancel(NotificationIds.REMINDER, AppAlarms.ACTION_REMINDER)
        reminderScheduler.apply(active = true, frequencyHours = 4)
        assertEquals(start.plus(Duration.ofHours(16)).toEpochMilli(), reminderAlarm().triggerAtMs)
        assertEquals(1, notifier.reminders.size)
    }

    @Test
    fun `siguiente recordatorio de la serie`() {
        val anchor = Instant.parse("2026-10-02T12:00:00Z")
        assertEquals(anchor, nextReminderAt(anchor, 4, anchor.minusSeconds(1)))
        assertEquals(anchor.plus(Duration.ofHours(4)), nextReminderAt(anchor, 4, anchor))
        assertEquals(anchor.plus(Duration.ofHours(12)), nextReminderAt(anchor, 4, anchor.plus(Duration.ofHours(9))))
        // El reloj se atraso tres dias: no se espera tres dias, sale a las 4 horas de ahora.
        val earlier = anchor.minus(Duration.ofDays(3))
        assertEquals(earlier.plus(Duration.ofHours(4)), nextReminderAt(anchor, 4, earlier))
    }

    // ------------------------------------------------------------ avisos de tarjeta

    private val card = CreditCard(creditCardId = "c1", cutOffDay = 10, paymentDay = 30, cardName = "Oro", bankName = "BBVA")

    private fun purchase(id: String, amount: String, date: LocalDateTime) =
        Spending(spendingId = id, amount = BigDecimal(amount), date = date, isCreditCard = true, creditCardId = "c1")

    private fun payment(id: String, amount: String, date: LocalDateTime) =
        Spending(spendingId = id, amount = BigDecimal(amount), date = date, isCreditCard = false, creditCardId = "c1")

    @Test
    fun `corte 2 dias antes, limite 3 dias antes con saldo y dia de pago si hay deuda`() {
        // Hoy 2 oct: corte el 10 y pago el 30 de octubre.
        val today = LocalDate.of(2026, 10, 2)
        val summary = buildCardSummary(card, listOf(purchase("p1", "1500", LocalDateTime.of(2026, 9, 20, 12, 0))), today)
        val plan = planCardReminders(listOf(summary), today.atTime(12, 0), strings)

        assertEquals(3, plan.size)
        assertEquals(listOf(8000, 8001, 8002), plan.map { it.requestCode })
        assertEquals(listOf(CardReminderKind.CUT_OFF, CardReminderKind.PAYMENT_WARNING, CardReminderKind.PAYMENT_DAY), plan.map { it.kind })

        assertEquals(LocalDateTime.of(2026, 10, 8, 9, 0), plan[0].at)
        assertEquals("Próximo corte: Oro", plan[0].title)
        assertEquals("Tu tarjeta BBVA corta el 10 de octubre. Revisa tus compras para cerrar tu ciclo.", plan[0].text)

        assertEquals(LocalDateTime.of(2026, 10, 27, 9, 0), plan[1].at)
        assertEquals("Fecha límite de pago: Oro", plan[1].title)
        assertEquals(
            "Tu pago vence el 30 de octubre. Saldo a pagar: $1,500.00 Paga a tiempo para no generar intereses.",
            plan[1].text.normalizeSpaces(),
        )

        assertEquals(LocalDateTime.of(2026, 10, 30, 8, 30), plan[2].at)
        assertEquals("¡Hoy vence tu tarjeta Oro!", plan[2].title)
        assertEquals("Hoy es la fecha límite de pago para BBVA. Saldo pendiente: $1,500.00.", plan[2].text.normalizeSpaces())
    }

    @Test
    fun `sin deuda no hay aviso del dia de pago ni saldo, y lo que ya paso no se programa`() {
        val today = LocalDate.of(2026, 10, 9)
        val summary = buildCardSummary(card, emptyList(), today)
        val plan = planCardReminders(listOf(summary), today.atTime(12, 0), strings)
        // El 8 a las 9:00 (corte) ya paso; solo queda la fecha limite del 30.
        assertEquals(1, plan.size)
        assertEquals("Tu pago vence el 30 de octubre. Paga a tiempo para no generar intereses.", plan.single().text)
    }

    @Test
    fun `un corte ya pagado no se recuerda (lee la fecha limite ajustada)`() {
        // Hoy 15 oct: el corte del 10 ya paso. Con la compra de septiembre pagada completa,
        // la fecha limite salta del 30 de octubre al 30 de noviembre.
        val today = LocalDate.of(2026, 10, 15)
        val spendings = listOf(
            purchase("p1", "800", LocalDateTime.of(2026, 9, 20, 12, 0)),
            payment("a1", "800", LocalDateTime.of(2026, 10, 12, 12, 0)),
        )
        val plan = planCardReminders(listOf(buildCardSummary(card, spendings, today)), today.atTime(12, 0), strings)
        assertTrue(plan.none { it.text.contains("30 de octubre") })
        assertTrue(plan.any { it.title.startsWith("Fecha límite") && it.text.contains("30 de noviembre") })
        assertTrue("Sin deuda no hay aviso del dia de pago", plan.none { it.title.startsWith("¡Hoy vence") })
    }

    @Test
    fun `el planificador lee las tarjetas de Room y el programador deja solo esos avisos`() = runTest {
        seedBasics()
        db.spendingDao().upsertAll(listOf(spending("p1", 50_000, LocalDateTime.of(2026, 9, 28, 12, 0), isCreditCard = true, creditCardId = "card-1")))
        // Hoy 2 oct: corte el 5 (aviso el 3) y pago el 25.
        val plan = CardReminderPlanner(cards, strings, clock).currentPlan()
        assertEquals(3, plan.size)

        val scheduler = AlarmCardReminderScheduler(alarms, clock)
        scheduler.reconcile(plan)
        val scheduled = scheduledAlarms(AppAlarms.ACTION_CARD_REMINDER)
        assertEquals(3, scheduled.size)
        assertTrue(scheduled.all { it.isAllowWhileIdle })
        assertEquals(
            plan.map { it.at.atZone(zone).toInstant().toEpochMilli() }.sorted(),
            scheduled.map { it.triggerAtMs }.sorted(),
        )
        val first = scheduled.single { it.triggerAtMs == plan[0].at.atZone(zone).toInstant().toEpochMilli() }
        assertEquals(plan[0], with(AlarmCardReminderScheduler) { shadowOf(first.operation).savedIntent.cardReminder() })

        scheduler.reconcile(plan.take(1))
        assertEquals(1, scheduledAlarms(AppAlarms.ACTION_CARD_REMINDER).size)
        scheduler.reconcile(emptyList())
        assertTrue(scheduledAlarms(AppAlarms.ACTION_CARD_REMINDER).isEmpty())
    }

    // ------------------------------------------------------------ al sonar un aviso de tarjeta

    private val cardScheduler by lazy { AlarmCardReminderScheduler(alarms, clock) }
    private val firer by lazy { CardReminderFirer(users, CardReminderPlanner(cards, strings, clock), cardScheduler, notifier) }

    /** La alarma de [kind] tal como la dejo programada el plan de hoy (2 oct, mediodia). */
    private suspend fun alarmFor(kind: CardReminderKind, cardId: String = "card-1"): Intent {
        val reminder = CardReminderPlanner(cards, strings, clock).currentPlan().single { it.cardId == cardId && it.kind == kind }
        return with(AlarmCardReminderScheduler) { Intent(AppAlarms.ACTION_CARD_REMINDER).putReminder(reminder) }
    }

    /** Tarjeta card-1 (corte el 5, pago el 25) con $500 comprados el 28 de septiembre. */
    private suspend fun seedDebt() {
        seedBasics()
        db.spendingDao().upsertAll(listOf(spending("p1", 50_000, LocalDateTime.of(2026, 9, 28, 12, 0), isCreditCard = true, creditCardId = "card-1")))
    }

    @Test
    fun `los tres avisos de tarjeta llegan a su hora`() = runTest {
        seedDebt()
        val cutOff = alarmFor(CardReminderKind.CUT_OFF)
        val warning = alarmFor(CardReminderKind.PAYMENT_WARNING)
        val paymentDay = alarmFor(CardReminderKind.PAYMENT_DAY)

        firer.show(cutOff)
        firer.show(warning)
        firer.show(paymentDay)

        assertEquals(
            listOf(
                CardNotification("card-1", 8000, "Próximo corte: Tarjeta card-1", "Tu tarjeta Banco corta el 05 de octubre. Revisa tus compras para cerrar tu ciclo."),
                CardNotification("card-1", 8001, "Fecha límite de pago: Tarjeta card-1", "Tu pago vence el 25 de octubre. Saldo a pagar: $500.00 Paga a tiempo para no generar intereses."),
                CardNotification("card-1", 8002, "¡Hoy vence tu tarjeta Tarjeta card-1!", "Hoy es la fecha límite de pago para Banco. Saldo pendiente: $500.00."),
            ),
            notifier.cards.map { it.copy(text = it.text.normalizeSpaces()) },
        )
    }

    @Test
    fun `al sonar se avisa con el saldo de ese momento`() = runTest {
        seedDebt()
        val paymentDay = alarmFor(CardReminderKind.PAYMENT_DAY)
        // Despues de programarlo se compraron otros $100.
        db.spendingDao().upsertAll(listOf(spending("p2", 10_000, LocalDateTime.of(2026, 10, 10, 12, 0), isCreditCard = true, creditCardId = "card-1")))

        firer.show(paymentDay)
        assertEquals("Hoy es la fecha límite de pago para Banco. Saldo pendiente: $600.00.", notifier.cards.single().text.normalizeSpaces())
    }

    @Test
    fun `un vencimiento ya pagado o de una tarjeta borrada no se avisa`() = runTest {
        seedDebt()
        val paymentDay = alarmFor(CardReminderKind.PAYMENT_DAY)
        val cutOff = alarmFor(CardReminderKind.CUT_OFF)
        // Se pago el corte completo el 20: la fecha limite salta a noviembre.
        db.spendingDao().upsertAll(listOf(spending("a1", 50_000, LocalDateTime.of(2026, 10, 20, 12, 0), creditCardId = "card-1")))
        firer.show(paymentDay)
        assertTrue("Ya pagado", notifier.cards.isEmpty())

        db.creditCardDao().upsert(card("card-1").copy(isDeleted = true))
        firer.show(cutOff)
        assertTrue("Tarjeta borrada", notifier.cards.isEmpty())
    }

    @Test
    fun `el aviso de una tarjeta no reemplaza al de otra`() = runTest {
        seedDebt()
        db.creditCardDao().upsert(card("card-2"))
        firer.show(alarmFor(CardReminderKind.CUT_OFF, "card-1"))
        firer.show(alarmFor(CardReminderKind.CUT_OFF, "card-2"))
        val shown = notifier.cards.map { it.tag to it.id }
        assertEquals(listOf("card-1" to 8000, "card-2" to 8000), shown)
        assertEquals("Tag + id distintos: son dos notificaciones", 2, shown.toSet().size)
    }

    @Test
    fun `al sonar se vuelven a programar los avisos`() = runTest {
        seedDebt()
        val cutOff = alarmFor(CardReminderKind.CUT_OFF)
        assertTrue(scheduledAlarms(AppAlarms.ACTION_CARD_REMINDER).isEmpty())
        firer.show(cutOff)
        assertEquals(3, scheduledAlarms(AppAlarms.ACTION_CARD_REMINDER).size)
    }

    @Test
    fun `el aviso de tarjeta se muestra solo si sigue habiendo cuenta`() = runTest {
        val reminder = CardReminder(8000, "card-1", CardReminderKind.CUT_OFF, "Próximo corte", "", LocalDateTime.of(2026, 10, 3, 9, 0))
        firer.show(with(AlarmCardReminderScheduler) { Intent(AppAlarms.ACTION_CARD_REMINDER).putReminder(reminder) })
        assertTrue("Se cerro la sesion", notifier.cards.isEmpty())
    }
}

/** Un Clock que las pruebas pueden adelantar. */
class MutableClock(private var now: Instant, private val zone: ZoneId) : Clock() {
    fun advance(by: Duration) {
        now = now.plus(by)
    }
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)
    override fun instant(): Instant = now
}

/** El formato de moneda de Java usa espacios raros segun la version. */
private fun String.normalizeSpaces() = replace(' ', ' ').replace(' ', ' ')
