package com.binc.gastapp.backup

import com.binc.gastapp.data.local.DbTest
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRepositoryTest : DbTest() {

    private val backups by lazy { BackupRepository(db, categories, scheduler, clock) }

    /** Gastos (uno con tarjeta y uno de una tarjeta ya borrada), una suscripcion y dos tarjetas. */
    private suspend fun seedAccount() {
        seedBasics()
        db.creditCardDao().upsert(card("card-old").copy(isDeleted = true, deletedAt = clock.instant()))
        db.spendingDao().upsertAll(
            listOf(
                spending("s1", 12_345, LocalDateTime.of(2026, 9, 30, 13, 15, 7)),
                spending("s2", 50_000, LocalDateTime.of(2026, 9, 28, 9, 0), isCreditCard = true, creditCardId = "card-1"),
                spending("s3", 9_900, LocalDateTime.of(2026, 8, 1, 9, 0), isCreditCard = true, creditCardId = "card-old"),
                spending("borrado", 100, LocalDateTime.of(2026, 9, 1, 9, 0), isDeleted = true, deletedAt = clock.instant()),
            ),
        )
        db.subscriptionDao().upsert(
            subscription("sub-1", creditCardId = "card-1", categoryId = "cat-food").copy(
                firstChargeDate = LocalDate.of(2026, 1, 31),
                isTrial = true,
                trialEndDate = LocalDate.of(2026, 2, 14),
                notes = "Plan familiar",
                lastChargeRegisteredAt = clock.instant(),
            ),
        )
    }

    private suspend fun exportText(): String = ByteArrayOutputStream().also { backups.export(it) }.toString(Charsets.UTF_8)

    private suspend fun read(text: String) = backups.read(text.byteInputStream())

    @Test
    fun `el respaldo es JSON versionado, con suscripciones y sin lo borrado`() = runTest {
        seedAccount()
        val text = exportText()
        assertTrue(text.contains("\"format\": \"gastapp-backup\""))
        assertTrue(text.contains("\"version\": 1"))
        assertTrue(text.contains("\"amount\": \"123.45\""))
        assertTrue("Hora local sin zona", text.contains("\"date\": \"2026-09-30T13:15:07\""))
        assertTrue("Fecha de calendario", text.contains("\"firstChargeDate\": \"2026-01-31\""))
        assertTrue(text.contains("\"serviceName\": \"Servicio sub-1\""))
        assertFalse(text.contains("\"borrado\""))
        assertTrue("La tarjeta borrada viaja porque s3 la usa", text.contains("\"card-old\""))

        val preview = (read(text) as BackupReadResult.Ok).preview
        assertEquals(BackupCounts(spendings = 3, creditCards = 1, subscriptions = 1, categories = 2), preview.counts)
        assertEquals(clock.instant(), preview.exportedAt)
    }

    @Test
    fun `exportar y restaurar deja los mismos datos y conserva lo registrado despues`() = runTest {
        seedAccount()
        val before = snapshot()
        val text = exportText()

        // Despues del respaldo: se edita, se borra y se agrega.
        db.spendingDao().upsert(db.spendingDao().get("s1")!!.copy(amountCents = 1, isSynced = true))
        spendings.delete("s2")
        subscriptions.delete("sub-1")
        db.userDao().upsert(db.userDao().get()!!.copy(name = "Otro nombre", salaryCents = 1))
        db.spendingDao().upsert(spending("nuevo", 777, LocalDateTime.of(2026, 10, 2, 8, 0)))
        scheduler.requests = 0

        val counts = backups.restore((read(text) as BackupReadResult.Ok).preview)
        assertEquals(3, counts.spendings)

        assertEquals("Lo del respaldo vuelve a como estaba", before, snapshot().copy(spendings = snapshot().spendings - "nuevo"))
        assertTrue("Lo registrado despues se queda", db.spendingDao().get("nuevo") != null)

        // Todo lo restaurado queda pendiente de subir, para que la nube tambien lo recupere.
        assertFalse(db.spendingDao().get("s1")!!.isSynced)
        assertFalse(db.subscriptionDao().get("sub-1")!!.isSynced)
        assertFalse(db.userDao().get()!!.isSynced)
        assertTrue(scheduler.requests > 0)
        assertTrue("La tarjeta borrada sigue borrada", db.creditCardDao().get("card-old")!!.isDeleted)
    }

    @Test
    fun `una categoria borrada vuelve con sus gastos`() = runTest {
        seedAccount()
        val text = exportText()
        categories.delete("cat-food")
        assertEquals("cat-default", db.spendingDao().get("s1")!!.categoryId)

        backups.restore((read(text) as BackupReadResult.Ok).preview)
        assertFalse(db.categoryDao().get("cat-food")!!.isDeleted)
        assertEquals("cat-food", db.spendingDao().get("s1")!!.categoryId)
        assertEquals("cat-food", db.subscriptionDao().get("sub-1")!!.categoryId)
    }

    @Test
    fun `no se restaura un respaldo de otra cuenta ni algo que no es respaldo`() = runTest {
        seedAccount()
        val text = exportText()

        val otherAccount = text.replace("\"userId\": \"user-1\"", "\"userId\": \"otra\"")
        assertEquals(
            BackupReadResult.Invalid("Este respaldo es de otra cuenta (prueba@example.com). Inicia sesión con esa cuenta para restaurarlo."),
            read(otherAccount),
        )
        assertEquals(BackupReadResult.Invalid("El archivo no es un respaldo de Gastapp."), read("{\"hola\": 1}"))
        assertEquals(BackupReadResult.Invalid("El archivo no es un respaldo de Gastapp."), read("no es json"))
        assertEquals(
            BackupReadResult.Invalid("El archivo no es un respaldo de Gastapp."),
            read(text.replace("\"format\": \"gastapp-backup\"", "\"format\": \"otra-app\"")),
        )
        assertEquals(
            BackupReadResult.Invalid("Este respaldo es de una versión más nueva de Gastapp. Actualiza la app para restaurarlo."),
            read(text.replace("\"version\": 1", "\"version\": 2")),
        )
        assertEquals(
            BackupReadResult.Invalid("El archivo no es un respaldo de Gastapp."),
            read(text.replace("\"amount\": \"123.45\"", "\"amount\": \"mucho\"")),
        )
    }

    /** Lo que importa comparar, sin las banderas de sincronizacion. */
    private data class Snapshot(
        val user: Any,
        val categories: List<Any>,
        val cards: List<Any>,
        val subscriptions: List<Any>,
        val spendings: Map<String, Any>,
    )

    private suspend fun snapshot() = Snapshot(
        user = db.userDao().get()!!.copy(isSynced = false),
        categories = db.categoryDao().getAll().map { it.copy(isSynced = false) },
        cards = db.creditCardDao().getAllIncludingDeleted().map { it.copy(isSynced = false) },
        subscriptions = db.subscriptionDao().getAll().map { it.copy(isSynced = false) },
        spendings = db.spendingDao().getAllActive().associate { it.spendingId to it.copy(isSynced = false) },
    )
}
