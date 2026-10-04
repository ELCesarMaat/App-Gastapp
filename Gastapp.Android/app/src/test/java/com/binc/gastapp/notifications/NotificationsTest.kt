package com.binc.gastapp.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.binc.gastapp.data.local.DbTest
import com.binc.gastapp.data.prefs.InMemoryDataStore
import com.binc.gastapp.data.prefs.SettingsStore
import com.binc.gastapp.domain.cards.buildCardSummary
import com.binc.gastapp.domain.model.CreditCard
import com.binc.gastapp.domain.model.Spending
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Lo que mostraria el telefono, en memoria. */
class RecordingNotifier(var enabled: Boolean = true) : AppNotifier {
    val reminders = mutableListOf<String>()
    val cards = mutableListOf<Triple<Int, String, String>>()
    val watch = mutableListOf<String>()
    override fun areEnabled() = enabled
    override fun sendTest() = enabled
    override fun showReminder(message: String) = enabled.also { if (it) reminders += message }
    override fun showCardReminder(notificationId: Int, title: String, text: String) =
        enabled.also { if (it) cards += Triple(notificationId, title, text) }
    override fun showWatchExpense(text: String) = enabled.also { if (it) watch += text }
}

class NotificationsTest : DbTest() {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val settingsStore = SettingsStore(InMemoryDataStore())
    private val notifier = RecordingNotifier()

    @Before
    fun testWorkManager() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
    }

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

    private fun reminderWorker() = TestListenableWorkerBuilder<ReminderWorker>(context)
        .setWorkerFactory(
            object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                    ReminderWorker(appContext, workerParameters, settingsStore, users, notifier)
            },
        )
        .build()

    @Test
    fun `los recordatorios rotan los 6 mensajes de MAUI`() = runTest {
        seedBasics()
        repeat(7) { assertEquals(ListenableWorker.Result.success(), reminderWorker().doWork()) }
        assertEquals(7, notifier.reminders.size)
        assertEquals(ReminderMessages, notifier.reminders.take(6))
        assertEquals("Despues del sexto vuelve al primero", ReminderMessages[0], notifier.reminders[6])
        assertEquals(ReminderMessages[1], reminderMessage(-5))
    }

    @Test
    fun `sin cuenta, apagados o sin permiso no se muestra nada`() = runTest {
        reminderWorker().doWork()
        assertTrue("Sin cuenta", notifier.reminders.isEmpty())

        seedBasics()
        settingsStore.setReminders(enabled = false, frequencyHours = 4)
        reminderWorker().doWork()
        assertTrue("Apagados", notifier.reminders.isEmpty())

        settingsStore.setReminders(enabled = true, frequencyHours = 4)
        notifier.enabled = false
        reminderWorker().doWork()
        assertTrue("Sin permiso", notifier.reminders.isEmpty())
        assertEquals("Sin permiso no se gasta el turno del mensaje", 0, settingsStore.takeReminderIndex())
    }

    @Test
    fun `se programa una sola vez por frecuencia y se quita al apagarlos`() = runTest {
        val scheduler = WorkManagerReminderScheduler(context)
        val workManager = WorkManager.getInstance(context)
        suspend fun current() = workManager.getWorkInfosForUniqueWorkFlow(WorkManagerReminderScheduler.UNIQUE_NAME).first()
            .filterNot { it.state.isFinished }

        scheduler.apply(active = true, frequencyHours = 4)
        val first = current().single()
        assertTrue(WorkManagerReminderScheduler.frequencyTag(4) in first.tags)

        // Abrir la app otra vez no reinicia la cuenta.
        scheduler.apply(active = true, frequencyHours = 4)
        assertEquals(first.id, current().single().id)

        scheduler.apply(active = true, frequencyHours = 12)
        val changed = current().single()
        assertNotEquals(first.id, changed.id)
        assertTrue(WorkManagerReminderScheduler.frequencyTag(12) in changed.tags)

        scheduler.apply(active = false, frequencyHours = 12)
        assertTrue(current().isEmpty())
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
        val plan = planCardReminders(listOf(summary), today.atTime(12, 0))

        assertEquals(3, plan.size)
        assertEquals(listOf(8000, 8001, 8002), plan.map { it.notificationId })

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
        val plan = planCardReminders(listOf(summary), today.atTime(12, 0))
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
        val plan = planCardReminders(listOf(buildCardSummary(card, spendings, today)), today.atTime(12, 0))
        assertTrue(plan.none { it.text.contains("30 de octubre") })
        assertTrue(plan.any { it.title.startsWith("Fecha límite") && it.text.contains("30 de noviembre") })
        assertTrue("Sin deuda no hay aviso del dia de pago", plan.none { it.title.startsWith("¡Hoy vence") })
    }

    @Test
    fun `el planificador lee las tarjetas de Room y el programador deja solo esos avisos`() = runTest {
        seedBasics()
        db.spendingDao().upsertAll(listOf(spending("p1", 50_000, LocalDateTime.of(2026, 9, 28, 12, 0), isCreditCard = true, creditCardId = "card-1")))
        // Hoy 2 oct: corte el 5 (aviso el 3) y pago el 25.
        val plan = CardReminderPlanner(cards, clock).currentPlan()
        assertEquals(3, plan.size)

        val scheduler = WorkManagerCardReminderScheduler(context, clock)
        val workManager = WorkManager.getInstance(context)
        suspend fun scheduled() = workManager.getWorkInfosByTagFlow(WorkManagerCardReminderScheduler.TAG).first()
            .filter { it.state == WorkInfo.State.ENQUEUED }

        scheduler.reconcile(plan)
        assertEquals(3, scheduled().size)
        scheduler.reconcile(plan.take(1))
        assertEquals(1, scheduled().size)
        scheduler.reconcile(emptyList())
        assertTrue(scheduled().isEmpty())
    }

    @Test
    fun `el aviso de tarjeta se muestra solo si sigue habiendo cuenta`() = runTest {
        fun worker() = TestListenableWorkerBuilder<CardReminderWorker>(context)
            .setInputData(
                androidx.work.workDataOf(
                    CardReminderWorker.KEY_ID to 8001,
                    CardReminderWorker.KEY_TITLE to "Próximo corte: Oro",
                    CardReminderWorker.KEY_TEXT to "Tu tarjeta corta el 10 de octubre.",
                ),
            )
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                        CardReminderWorker(appContext, workerParameters, users, notifier)
                },
            )
            .build()

        worker().doWork()
        assertTrue("Se cerro la sesion", notifier.cards.isEmpty())
        seedBasics()
        worker().doWork()
        assertEquals(listOf(Triple(8001, "Próximo corte: Oro", "Tu tarjeta corta el 10 de octubre.")), notifier.cards)
    }
}

/** El formato de moneda de Java usa espacios raros segun la version. */
private fun String.normalizeSpaces() = replace(' ', ' ').replace(' ', ' ')
