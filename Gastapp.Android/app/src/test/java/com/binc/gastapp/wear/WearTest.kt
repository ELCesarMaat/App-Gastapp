package com.binc.gastapp.wear

import com.binc.gastapp.core.wear.WearExpensePayload
import com.binc.gastapp.core.wear.WearJson
import com.binc.gastapp.core.wear.WearPaths
import com.binc.gastapp.core.wear.WearTodayPayload
import com.binc.gastapp.data.repository.DeviceRepository
import com.binc.gastapp.domain.model.Category
import com.binc.gastapp.domain.model.PaymentMethods
import com.binc.gastapp.domain.model.Spending
import com.binc.gastapp.startup.DayClock
import com.binc.gastapp.sync.SyncTest
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** El lado del telefono de la Data Layer: gastos que llegan del reloj, vinculacion y lo que se publica. */
class WearTest : SyncTest() {

    private val importer by lazy { WatchExpenseImporter(db, users, spendings, clock) }
    private val events = WearEvents()
    private val channel = FakeWearChannel()
    private val pairing by lazy { WatchPairing(DeviceRepository(api, guard, channel), events) }

    private fun payload(
        id: String = "w-1",
        amount: Double = 85.0,
        title: String = "Café",
        categoryId: String? = "cat-food",
        occurredAt: String = "2026-10-02T15:30:00Z",
    ) = WearExpensePayload(id, amount, title, categoryId, "Agregado desde mi reloj", occurredAt)

    // ------------------------------------------------------------ gastos del reloj

    @Test
    fun `el gasto del reloj entra a Room con su id, en hora local, en efectivo y pendiente de subir`() = runTest {
        seedBasics()
        assertEquals(WatchExpenseResult.Imported, importer.import(payload(amount = 85.5)))

        val saved = db.spendingDao().get("w-1")!!
        assertEquals(8_550, saved.amountCents)
        assertEquals("15:30 UTC son las 9:30 en Ciudad de Mexico", LocalDateTime.of(2026, 10, 2, 9, 30), saved.date)
        assertEquals("cat-food", saved.categoryId)
        assertEquals(PaymentMethods.CASH, saved.paymentMethod)
        assertEquals(false, saved.isCreditCard)
        assertEquals("Agregado desde mi reloj", saved.description)
        assertEquals(false, saved.isSynced)
        assertEquals(1, scheduler.requests)
    }

    @Test
    fun `categoria desconocida va a Sin categoria y no se duplica ni revive`() = runTest {
        seedBasics()
        importer.import(payload(categoryId = "no-existe"))
        assertEquals("cat-default", db.spendingDao().get("w-1")!!.categoryId)

        assertEquals(WatchExpenseResult.AlreadyThere, importer.import(payload(amount = 999.0)))
        assertEquals(8_500, db.spendingDao().get("w-1")!!.amountCents)

        spendings.delete("w-1")
        assertEquals("Un gasto borrado aqui no revive", WatchExpenseResult.AlreadyThere, importer.import(payload()))
        assertTrue(db.spendingDao().get("w-1")!!.isDeleted)
    }

    @Test
    fun `sin cuenta en el telefono no se inserta`() = runTest {
        assertEquals(WatchExpenseResult.NoAccount, importer.import(payload()))
        assertNull(db.spendingDao().get("w-1"))
    }

    @Test
    fun `texto de la notificacion como en MAUI`() {
        assertEquals("$85 · Café", watchExpenseText(payload(amount = 85.0)).normalizeSpaces())
        assertEquals("$85.50 · Café", watchExpenseText(payload(amount = 85.5)).normalizeSpaces())
        assertEquals("$1,200", watchExpenseText(payload(amount = 1200.0, title = "  ")).normalizeSpaces())
    }

    // ------------------------------------------------------------ vinculacion automatica

    /**
     * El limite de 55 s de WatchPairing correria con el reloj virtual de runTest y venceria
     * en cuanto la prueba esperara a MockWebServer: se corre en tiempo real.
     */
    private suspend fun pair(code: String) = withContext(Dispatchers.IO) { pairing.pair(code) }

    @Test
    fun `vincula con el codigo que manda el reloj y avisa a Ajustes`() = runTest {
        seedBasics()
        signedIn()
        val received = mutableListOf<WearEvent>()
        val listening = backgroundScope.launch { events.events.collect { received += it } }
        yield()

        respond("""{"deviceName":"Pixel Watch 4","platform":"wearos"}""")
        assertEquals(WearPaths.PAIR_OK, pair("K7M-2QX"))
        assertTrue(server.takeRequest().body.readUtf8().contains("\"userCode\":\"K7M-2QX\""))
        assertEquals(listOf(WearEvent.Linked("Pixel Watch 4"), WearEvent.DevicesChanged), received)
        listening.cancel()
    }

    @Test
    fun `motivos cortos para el reloj`() = runTest {
        seedBasics()
        assertEquals("Inicia sesión en el teléfono", pair("K7M-2QX"))
        assertEquals("Código vacío", pair(" "))

        signedIn()
        respond("\"Codigo no valido o expirado.\"", code = 400)
        assertEquals("Código no válido o expirado", pair("K7M-2QX"))
        respond("\"Demasiados intentos.\"", code = 429)
        assertEquals("Demasiados intentos", pair("K7M-2QX"))
        respond("", code = 503)
        assertEquals("Error del servidor (503)", pair("K7M-2QX"))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        assertEquals("Sin conexión", pair("K7M-2QX"))
    }

    // ------------------------------------------------------------ lo que se publica

    @Test
    fun `el dia para el reloj suma todo, del mas nuevo al mas viejo y en UTC`() {
        val zone = ZoneId.of("America/Mexico_City")
        val list = listOf(
            Spending("a", BigDecimal("100.00"), LocalDateTime.of(2026, 10, 2, 9, 0), categoryId = "cat-food", title = "Desayuno"),
            Spending("b", BigDecimal("250.50"), LocalDateTime.of(2026, 10, 2, 13, 0), categoryId = "x", title = "Tenis", isCreditCard = true),
            Spending("c", BigDecimal("40.00"), LocalDateTime.of(2026, 10, 2, 11, 0), title = "Borrado", isDeleted = true),
        )
        val payload = buildTodayPayload(list, listOf(Category("cat-food", "Comida")), zone)
        assertEquals("Las compras con tarjeta cuentan, como en GET /Device/Summary", 350.5, payload.total, 0.001)
        assertEquals(2, payload.count)
        assertEquals(listOf("b", "a"), payload.spendings.map { it.spendingId })
        assertEquals("2026-10-02T19:00:00Z", payload.spendings[0].occurredAt)
        assertNull(payload.spendings[0].categoryName)
        assertEquals("Comida", payload.spendings[1].categoryName)
    }

    @Test
    fun `publica el dia y las categorias al cambiar Room, y nada sin cuenta`() = runTest {
        val publisher = WearPublisher(users, spendings, categories, DayClock(clock), channel, clock)
        val job = backgroundScope.launch { publisher.run() }

        // Sin cuenta no sale nada (le borraria las categorias al reloj).
        testScheduler.advanceTimeBy(2_000)
        assertTrue(channel.data.isEmpty())

        seedBasics()
        db.spendingDao().upsertAll(listOf(spending("hoy", 12_000, at(today, 9))))
        val today = awaitData(WearPaths.TODAY) { it.count == 1 }
        assertEquals(120.0, today.total, 0.001)
        assertTrue(channel.data.any { it.first == WearPaths.CATEGORIES && it.second.contains("\"categoryName\":\"Comida\"") })
        job.cancel()
    }

    /** Espera a que el canal reciba un /gastapp/today que cumpla la condicion. */
    private suspend fun kotlinx.coroutines.test.TestScope.awaitData(path: String, predicate: (WearTodayPayload) -> Boolean): WearTodayPayload {
        repeat(200) {
            channel.data.lastOrNull { it.first == path }
                ?.let { WearJson.decodeFromString(WearTodayPayload.serializer(), it.second) }
                ?.takeIf(predicate)
                ?.let { return it }
            testScheduler.advanceTimeBy(600)
            Thread.sleep(10)
        }
        throw AssertionError("No llego $path: ${channel.data}")
    }

    private fun at(day: java.time.LocalDate, hour: Int) = day.atTime(hour, 0)
}

private fun String.normalizeSpaces() = replace(' ', ' ').replace(' ', ' ')
