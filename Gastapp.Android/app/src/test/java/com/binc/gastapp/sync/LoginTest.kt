package com.binc.gastapp.sync

import com.binc.gastapp.data.local.CategoryEntity
import com.binc.gastapp.data.local.IncomeTypeEntity
import com.binc.gastapp.data.local.UserEntity
import com.binc.gastapp.data.prefs.RegisterDraft
import com.binc.gastapp.data.session.SessionResult
import com.binc.gastapp.data.session.SessionState
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Login (AddUserData de MAUI), registro y cerrar sesion, con el login.json real del API. */
class LoginTest : SyncTest() {

    private val serverUserId = "8d3c0f2e-1a5b-4c7d-9e0f-123456789abc"

    @Test
    fun `el login baja todo y lo guarda como sincronizado`() = runTest {
        respond(fixture("login.json"))

        assertEquals(SessionResult.Ok, sessions.login("  ana@gastapp.dev ", "secreta"))

        // Token y expiracion (de tokenExpiration, ya no de DateTime.ToString()).
        val session = sessionStore.get()!!
        assertEquals("eyJ.token.login", session.token)
        // SessionStore la guarda en milisegundos.
        assertEquals(Instant.parse("2026-11-01T18:30:00.123Z"), session.expiresAt)
        assertEquals(SessionState.Active("Ana Prueba"), sessions.state.first())
        assertTrue("""El correo va sin espacios""", server.takeRequest().body.readUtf8().contains("\"ana@gastapp.dev\""))

        val user = db.userDao().get()!!
        assertEquals(serverUserId, user.userId)
        assertEquals(1_250_000L, user.salaryCents)
        assertEquals(BigDecimal("15.5"), user.percentSave)
        // Fecha de calendario: no se corre al 14 de junio por la zona de Mexico.
        assertEquals(LocalDate.of(1995, 6, 15), user.birthDate)
        assertTrue(user.isSynced)

        assertEquals(3, db.incomeTypeDao().getAll().size)
        assertEquals(listOf("cat-default", "cat-comida"), db.categoryDao().getAll().map { it.categoryId })

        // UTC del API -> hora local (Mexico, UTC-6).
        val tacos = db.spendingDao().get("sp-tacos")!!
        assertEquals(LocalDateTime.of(2026, 10, 2, 14, 15, 33, 123_456_000), tacos.date)
        assertEquals(12_345L, tacos.amountCents)
        assertTrue(tacos.isSynced)

        val msi = db.spendingDao().get("sp-msi")!!
        assertEquals("card-oro", msi.creditCardId)
        assertEquals(100_000L, msi.installmentMonthlyAmountCents)

        // Lo borrado tambien baja (con su fecha), para que la purga lo quite a su tiempo.
        val deleted = db.spendingDao().get("sp-borrado")!!
        assertTrue(deleted.isDeleted)
        assertEquals(Instant.parse("2026-10-01T12:00:00.5Z"), deleted.deletedAt)
        assertTrue(db.creditCardDao().get("card-vieja")!!.isDeleted)

        // Referencias colgantes (regla 12): se conservan los registros, sin la referencia.
        val dangling = db.spendingDao().get("sp-colgante")!!
        assertNull(dangling.creditCardId)
        assertEquals("cat-default", dangling.categoryId)
        val trial = db.subscriptionDao().get("sub-prueba")!!
        assertNull(trial.creditCardId)
        assertNull(trial.categoryId)
        assertEquals(LocalDate.of(2026, 2, 28), trial.firstChargeDate)
        assertEquals(LocalDate.of(2026, 10, 15), trial.trialEndDate)

        val netflix = db.subscriptionDao().get("sub-netflix")!!
        assertEquals("card-oro", netflix.creditCardId)
        assertEquals(Instant.parse("2026-09-30T18:00:00Z"), netflix.lastChargeRegisteredAt)

        // Nada quedo pendiente de subir.
        assertEquals(0, db.syncDao().pendingCounts().total)
    }

    @Test
    fun `la base de otra cuenta se descarta entera`() = runTest {
        seedBasics()
        db.spendingDao().upsert(spending("de-otro", 1_000, at(today, 9), isSynced = false))
        respond(fixture("login.json"))

        sessions.login("ana@gastapp.dev", "secreta")

        assertNull(db.spendingDao().get("de-otro"))
        assertNull(db.categoryDao().get("cat-food"))
        assertEquals(serverUserId, db.userDao().get()!!.userId)
    }

    @Test
    fun `volver a entrar a la misma cuenta conserva lo pendiente de subir`() = runTest {
        // Sesion vencida con cambios sin subir: un gasto nuevo y una edicion de uno del servidor.
        db.incomeTypeDao().upsertAll(listOf(IncomeTypeEntity(2, "Quincenal")))
        db.userDao().upsert(
            UserEntity(serverUserId, "Ana", "ana@gastapp.dev", 1_000_000, BigDecimal("10"), LocalDate.of(1995, 6, 15), 2, 1, 16, null, isSynced = true),
        )
        db.categoryDao().upsertAll(
            listOf(
                CategoryEntity("cat-default", serverUserId, "Sin categoria", isDefaultCategory = true, isSynced = true),
                CategoryEntity("cat-nueva", serverUserId, "Nueva sin subir", isSynced = false),
            ),
        )
        db.spendingDao().upsert(spending("sp-nuevo", 7_700, at(today, 10), categoryId = "cat-nueva", isSynced = false).copy(userId = serverUserId))
        db.spendingDao().upsert(spending("sp-tacos", 99_900, at(today, 14), categoryId = "cat-default", isSynced = false).copy(userId = serverUserId))
        respond(fixture("login.json"))

        assertEquals(SessionResult.Ok, sessions.login("ana@gastapp.dev", "secreta"))

        assertEquals(7_700L, db.spendingDao().get("sp-nuevo")!!.amountCents)
        assertEquals("cat-nueva", db.spendingDao().get("sp-nuevo")!!.categoryId)
        // La edicion local gana sobre lo que bajo y sigue pendiente.
        val tacos = db.spendingDao().get("sp-tacos")!!
        assertEquals(99_900L, tacos.amountCents)
        assertFalse(tacos.isSynced)
        // Lo demas si se actualizo con el servidor.
        assertEquals(1_250_000L, db.userDao().get()!!.salaryCents)
        assertEquals(2, db.syncDao().pendingCounts().activeSpendings)
        assertEquals(1, db.syncDao().pendingCounts().categories)
        assertTrue("Se pidio subir lo pendiente", scheduler.requests > 0)
    }

    @Test
    fun `los errores del login dan los mensajes de MAUI`() = runTest {
        respond("Contraseña incorrecta. Verifica tu contraseña o usa la opción 'Olvidé mi contraseña'.", code = 400)
        respond("", code = 503)
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        assertEquals(
            SessionResult.Failed("Contraseña incorrecta. Verifica tu contraseña o usa la opción 'Olvidé mi contraseña'."),
            sessions.login("ana@gastapp.dev", "mala"),
        )
        assertEquals(SessionResult.Failed("Error del servidor: 503. Intenta más tarde."), sessions.login("ana@gastapp.dev", "x"))
        assertEquals(
            SessionResult.Failed("Error de conexión. Verifica tu conexión a internet e intenta de nuevo."),
            sessions.login("ana@gastapp.dev", "x"),
        )
        assertNull(sessionStore.get())
        assertEquals(SessionState.LoggedOut, sessions.state.first())
    }

    @Test
    fun `crear la cuenta entra con el login y no duplica Sin categoria`() = runTest {
        draftStore.save(RegisterDraft(step = 3, email = "ana@gastapp.dev"))
        respond(fixture("create_user_response.json"))
        respond(fixture("login.json"))

        val result = sessions.createAccount(
            com.binc.gastapp.data.session.NewAccount(
                name = "Ana Prueba",
                email = "ana@gastapp.dev",
                password = "secreta",
                birthDate = LocalDate.of(1995, 6, 15),
                salary = BigDecimal("12500.00"),
                percentSave = BigDecimal("15.5"),
                incomeTypeId = 2,
                firstPayDay = 1,
                secondPayDay = 16,
            ),
        )

        assertEquals(SessionResult.Ok, result)
        val create = server.takeRequest()
        assertEquals("/api/User/CreateUser", create.path)
        assertTrue(create.body.readUtf8().contains("\"birthDate\":\"1995-06-15T00:00:00Z\""))
        assertEquals("/api/User/Login", server.takeRequest().path)
        assertEquals(1, db.categoryDao().getAll().count { it.isDefaultCategory })
        assertNull("El borrador sobra con la cuenta creada", draftStore.load())
    }

    @Test
    fun `cerrar sesion borra Room el token y el borrador`() = runTest {
        respond(fixture("login.json"))
        sessions.login("ana@gastapp.dev", "secreta")
        draftStore.save(RegisterDraft(step = 1, email = "otra@gastapp.dev"))

        sessions.logout()

        assertNull(db.userDao().get())
        assertEquals(0, db.spendingDao().allIds().size)
        assertEquals(0, db.incomeTypeDao().getAll().size)
        assertNull(sessionStore.get())
        assertNull(draftStore.load())
        assertEquals(SessionState.LoggedOut, sessions.state.first())
    }
}
