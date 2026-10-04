package com.binc.gastapp.sync

import com.binc.gastapp.data.session.SessionState
import com.binc.gastapp.domain.model.Spending
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Push con SyncAllData, borrado de categorias, pull con GetSpendings y manejo de errores. */
class SyncEngineTest : SyncTest() {

    @Test
    fun `sube lo pendiente en un solo SyncAllData y lo marca como subido`() = runTest {
        seedBasics()
        signedIn()
        spendings.save(Spending("sp-new", BigDecimal("123.45"), LocalDateTime.of(2026, 10, 2, 12, 30), title = "Tacos", categoryId = "cat-food"))
        db.spendingDao().upsert(spending("sp-old", 5_000, at(today.minusDays(1), 9))) // ya subido: no viaja
        spendings.delete("sp-old")
        respond("true")

        val outcome = engine.sync(full = false)

        assertEquals(SyncOutcome.Done(pushed = 2, pulled = 0), outcome)
        val request = server.takeRequest()
        assertEquals("/api/Spendings/SyncAllData", request.path)
        assertEquals("Bearer tok", request.getHeader("Authorization"))
        val body = request.syncBody()
        assertNull("El usuario no cambio", body.user)
        assertEquals(setOf("sp-new", "sp-old"), body.spendings.map { it.spendingId }.toSet())

        val sent = body.spendings.single { it.spendingId == "sp-new" }
        assertEquals(BigDecimal("123.45"), sent.amount)
        // Hora local de Mexico (UTC-6) -> instante UTC (SpendingToApiUtc).
        assertEquals(Instant.parse("2026-10-02T18:30:00Z"), sent.date)
        assertEquals(userId, sent.userId)
        assertFalse(sent.isSynced)

        val deleted = body.spendings.single { it.spendingId == "sp-old" }
        assertTrue(deleted.isDeleted)
        assertEquals(clock.instant(), deleted.deletedAt)

        assertEquals(0, db.syncDao().pendingCounts().total)
        assertTrue(db.spendingDao().get("sp-new")!!.isSynced)
    }

    @Test
    fun `tarjetas suscripciones y perfil viajan con sus fechas bien`() = runTest {
        seedBasics()
        signedIn()
        db.creditCardDao().upsert(card("card-2").copy(isSynced = false))
        db.subscriptionDao().upsert(
            subscription("sub-1", creditCardId = "card-2", isSynced = false)
                .copy(trialEndDate = today.plusDays(10), lastChargeRegisteredAt = Instant.parse("2026-09-30T18:00:00Z")),
        )
        users.saveProfile(users.getUser()!!.copy(salary = BigDecimal("15000.00")))
        respond("true")

        engine.sync(full = false)

        val body = server.takeRequest().syncBody()
        assertEquals(BigDecimal("15000.00"), body.user!!.salary)
        assertEquals(java.time.LocalDate.of(1990, 1, 31), body.user!!.birthDate)
        assertEquals("card-2", body.creditCards.single().creditCardId)
        val sub = body.subscriptions.single()
        // Fechas de calendario tal cual; el instante, tal cual.
        assertEquals(java.time.LocalDate.of(2026, 1, 31), sub.firstChargeDate)
        assertEquals(today.plusDays(10), sub.trialEndDate)
        assertEquals(Instant.parse("2026-09-30T18:00:00Z"), sub.lastChargeRegisteredAt)
        assertEquals(0, db.syncDao().pendingCounts().total)
    }

    @Test
    fun `sin nada pendiente no se llama al API`() = runTest {
        seedBasics()
        signedIn()

        assertEquals(SyncOutcome.Done(0, 0), engine.sync(full = false))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `lo que se edita mientras se sube se queda pendiente`() = runTest {
        seedBasics()
        signedIn()
        spendings.save(Spending("sp-1", BigDecimal("100.00"), at(today, 9), title = "Antes", categoryId = "cat-food"))

        // El usuario edita el gasto justo mientras el servidor procesa el envio.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                runBlocking {
                    val current = db.spendingDao().get("sp-1")!!
                    db.spendingDao().upsert(current.copy(amountCents = 25_000, title = "Despues"))
                }
                return MockResponse().setBody("true")
            }
        }

        engine.sync(full = false)

        val local = db.spendingDao().get("sp-1")!!
        assertEquals(25_000L, local.amountCents)
        assertFalse("La version nueva no ha subido", local.isSynced)
        assertEquals(1, db.syncDao().pendingCounts().activeSpendings)
    }

    @Test
    fun `un 401 cierra la sesion y manda a iniciar sesion`() = runTest {
        seedBasics()
        signedIn()
        spendings.save(Spending("sp-1", BigDecimal("10.00"), at(today, 9), categoryId = "cat-food"))
        respond("", code = 401)

        assertEquals(SyncOutcome.Unauthorized, engine.sync(full = false))

        assertNull(sessionStore.get())
        assertTrue(guard.revoked.value)
        assertTrue(sessions.state.first() is SessionState.Revoked)
        // Los datos locales no se tocan.
        assertNotNull(db.spendingDao().get("sp-1"))
        assertEquals(1, db.syncDao().pendingCounts().activeSpendings)
    }

    @Test
    fun `un 500 o un timeout no expulsan y se reintenta`() = runTest {
        seedBasics()
        signedIn()
        spendings.save(Spending("sp-1", BigDecimal("10.00"), at(today, 9), categoryId = "cat-food"))
        respond("Ocurrió un error al sincronizar los gastos.", code = 500)
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        assertTrue(engine.sync(full = false) is SyncOutcome.Retry)
        assertTrue(engine.sync(full = false) is SyncOutcome.Retry)

        assertEquals("tok", sessionStore.get()!!.token)
        assertFalse(guard.revoked.value)
        assertTrue(sessions.state.first() is SessionState.Active)
        assertEquals(1, db.syncDao().pendingCounts().activeSpendings)
    }

    @Test
    fun `con el token vencido no se llama al API ni se expulsa`() = runTest {
        seedBasics()
        sessionStore.save("viejo", clock.instant().minus(Duration.ofHours(1)))
        spendings.save(Spending("sp-1", BigDecimal("10.00"), at(today, 9), categoryId = "cat-food"))

        assertEquals(SyncOutcome.NoSession, engine.sync(full = true))

        assertEquals(0, server.requestCount)
        assertTrue(sessions.state.first() is SessionState.Expired)
    }

    @Test
    fun `la completa refresca sube borra categorias y baja en ese orden`() = runTest {
        seedBasics()
        signedIn("viejo")
        db.spendingDao().upsert(spending("sp-food", 4_000, at(today, 8)))
        categories.delete("cat-food") // sus gastos pasan a "Sin categoria"
        respond(fixture("refresh_token.json"))
        respond("true")
        respond("true")
        respond(fixture("get_spendings.json"))

        val outcome = engine.sync(full = true)

        assertEquals(SyncOutcome.Done(pushed = 1, pulled = 2), outcome)
        val calls = requests()
        assertEquals(
            listOf(
                "/api/User/RefreshToken",
                "/api/Spendings/SyncAllData",
                "/api/Spendings/DeleteCategory?categoryId=cat-food",
                "/api/Spendings/GetSpendings",
            ),
            calls.map { it.path },
        )
        assertEquals("Bearer viejo", calls[0].getHeader("Authorization"))
        // Despues del refresh ya se usa el token nuevo.
        assertTrue(calls.drop(1).all { it.getHeader("Authorization") == "Bearer eyJ.token.nuevo" })
        assertEquals("cat-default", calls[1].syncBody().spendings.single().categoryId)

        assertEquals("eyJ.token.nuevo", sessionStore.get()!!.token)
        assertEquals(Instant.parse("2026-11-01T18:30:00.123Z"), sessionStore.get()!!.expiresAt)
        assertNull("Confirmado por el API: se borra de verdad", db.categoryDao().get("cat-food"))

        // El gasto del reloj llego, en hora local y como sincronizado.
        val watch = db.spendingDao().get("sp-reloj")!!
        assertEquals(LocalDateTime.of(2026, 10, 2, 10, 5), watch.date)
        assertEquals("cat-default", watch.categoryId)
        assertEquals(userId, watch.userId)
        assertTrue(watch.isSynced)
        assertEquals(0, db.syncDao().pendingCounts().total)
    }

    @Test
    fun `el pull no sobrescribe un gasto local ni revive uno borrado aqui`() = runTest {
        seedBasics()
        signedIn()
        // sp-tacos existe aqui con otro monto y sin subir; sp-reloj se borro aqui.
        db.spendingDao().upsert(spending("sp-tacos", 1_00, at(today, 14), isSynced = false))
        db.spendingDao().upsert(spending("sp-reloj", 4_500, at(today, 10), isDeleted = true, isSynced = false, deletedAt = clock.instant()))
        respond(fixture("refresh_token.json"))
        respond("true")
        respond(fixture("get_spendings.json"))

        val outcome = engine.sync(full = true)

        assertEquals(SyncOutcome.Done(pushed = 2, pulled = 0), outcome)
        assertEquals(100L, db.spendingDao().get("sp-tacos")!!.amountCents)
        assertTrue(db.spendingDao().get("sp-reloj")!!.isDeleted)
    }

    @Test
    fun `DeleteCategory 404 la borra aqui y 400 la restaura`() = runTest {
        seedBasics()
        signedIn()
        db.categoryDao().upsert(com.binc.gastapp.data.local.CategoryEntity("cat-2", userId, "Otra", isSynced = true))
        categories.delete("cat-food")
        categories.delete("cat-2")
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path!!.endsWith("categoryId=cat-food") -> MockResponse().setResponseCode(404).setBody("Categoría no encontrada.")
                request.path!!.endsWith("categoryId=cat-2") -> MockResponse().setResponseCode(400).setBody("No puedes eliminar la categoría predeterminada.")
                else -> MockResponse().setBody("true")
            }
        }

        engine.sync(full = false)

        assertNull(db.categoryDao().get("cat-food"))
        val restored = db.categoryDao().get("cat-2")!!
        assertFalse(restored.isDeleted)
        assertTrue(restored.isSynced)
    }

    @Test
    fun `un 400 al subir no se reintenta pero el pull sigue`() = runTest {
        seedBasics()
        signedIn()
        spendings.save(Spending("sp-1", BigDecimal("10.00"), at(today, 9), categoryId = "cat-food"))
        respond(fixture("refresh_token.json"))
        respond("Los gastos no pertenecen al usuario autenticado.", code = 400)
        respond(fixture("get_spendings.json"))

        val outcome = engine.sync(full = true)

        assertTrue(outcome is SyncOutcome.Failed)
        assertTrue((outcome as SyncOutcome.Failed).reason.contains("no pertenecen"))
        assertNotNull("El pull corrio", db.spendingDao().get("sp-reloj"))
        assertEquals(1, db.syncDao().pendingCounts().activeSpendings)
        assertEquals(outcome.reason, engine.runState.value.lastError)
    }
}
