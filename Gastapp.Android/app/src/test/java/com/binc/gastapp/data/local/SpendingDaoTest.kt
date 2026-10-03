package com.binc.gastapp.data.local

import app.cash.turbine.test
import java.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpendingDaoTest : DbTest() {
    private val dao get() = db.spendingDao()

    @Test
    fun guardaYLeeIgual() = runTest {
        seedBasics()
        val original = spending("s1", 12_345, at(today, 14, 15, 30, 500_000_000), isCreditCard = true, creditCardId = "card-1")
            .copy(description = "Con descripcion", isMsi = true, totalInstallments = 6, installmentMonthlyAmountCents = 2_058)
        dao.upsert(original)
        assertEquals(original, dao.get("s1"))
    }

    @Test
    fun losDiasSonCompletosYNoIncluyenElSiguiente() = runTest {
        seedBasics()
        dao.upsertAll(
            listOf(
                spending("antes", 100, at(today.minusDays(1), 23, 59, 59, 999_999_999)),
                spending("inicio", 200, at(today, 0, 0)),
                spending("fin", 300, at(today, 23, 59, 59, 999_999_999)),
                spending("despues", 400, at(today.plusDays(1), 0, 0)),
            ),
        )
        val delDia = dao.observeBetween(today.atStartOfDay(), today.plusDays(1).atStartOfDay()).first()
        assertEquals(listOf("inicio", "fin"), delDia.map { it.spendingId })
    }

    @Test
    fun losBorradosNoAparecenYDeshacerLosRegresa() = runTest {
        seedBasics()
        dao.upsertAll(listOf(spending("s1", 100, at(today, 10)), spending("s2", 200, at(today, 11))))

        dao.observeBetween(today.atStartOfDay(), today.plusDays(1).atStartOfDay()).test {
            assertEquals(2, awaitItem().size)

            assertEquals(1, dao.markDeleted("s1", clock.instant()))
            assertEquals(listOf("s2"), awaitItem().map { it.spendingId })
            val borrado = dao.get("s1")!!
            assertTrue(borrado.isDeleted)
            assertEquals(clock.instant(), borrado.deletedAt)
            assertEquals(false, borrado.isSynced)

            assertEquals(1, dao.restore("s1"))
            assertEquals(2, awaitItem().size)
            assertNull(dao.get("s1")!!.deletedAt)
        }
    }

    @Test
    fun totalesPorDiaConLosDosCriterios() = runTest {
        seedBasics()
        dao.upsertAll(
            listOf(
                // 0.10 + 0.20 en centavos: 0.30 exacto, sin errores de punto flotante.
                spending("a", 10, at(today, 9)),
                spending("b", 20, at(today, 10)),
                spending("compra", 50_000, at(today, 12), isCreditCard = true, creditCardId = "card-1"),
                spending("abono", 30_000, at(today, 13), isCreditCard = false, creditCardId = "card-1"),
                spending("ayer", 999, at(today.minusDays(1), 20)),
                spending("borrado", 777, at(today, 15), isDeleted = true),
            ),
        )
        val dias = dao.observeDailyTotals(today.minusDays(1).atStartOfDay(), today.plusDays(1).atStartOfDay()).first()

        assertEquals(listOf(today.minusDays(1), today), dias.map { it.day })
        val hoy = dias.last()
        // Total del dia: todo, compras con tarjeta incluidas.
        assertEquals(80_030L, hoy.totalCents)
        // Criterio del periodo: sin la compra con tarjeta (el abono si cuenta).
        assertEquals(30_030L, hoy.withoutCardPurchasesCents)
        assertEquals(4, hoy.spendingCount)
    }

    @Test
    fun totalesPorCategoriaSinComprasConTarjeta() = runTest {
        seedBasics()
        dao.upsertAll(
            listOf(
                spending("a", 10_000, at(today, 9), categoryId = "cat-food"),
                spending("b", 5_000, at(today, 10), categoryId = "cat-default"),
                spending("c", 90_000, at(today, 11), categoryId = "cat-default", isCreditCard = true, creditCardId = "card-1"),
                spending("d", 2_500, at(today, 12), categoryId = "cat-food"),
            ),
        )
        val totales = dao.observeCategoryTotals(today.atStartOfDay(), today.plusDays(1).atStartOfDay()).first()
        assertEquals(
            listOf(CategoryTotalRow("cat-food", "Comida", 12_500), CategoryTotalRow("cat-default", "Sin categoria", 5_000)),
            totales,
        )
    }

    @Test
    fun movimientosDeTarjetaYConteoPorCategoria() = runTest {
        seedBasics()
        dao.upsertAll(
            listOf(
                spending("compra", 100, at(today, 9), isCreditCard = true, creditCardId = "card-1"),
                spending("abono", 50, at(today, 10), creditCardId = "card-1"),
                spending("efectivo", 70, at(today, 11)),
                spending("compraBorrada", 80, at(today, 12), isCreditCard = true, creditCardId = "card-1", isDeleted = true),
            ),
        )
        assertEquals(setOf("compra", "abono"), dao.observeCardMovements().first().map { it.spendingId }.toSet())
        assertEquals(3, dao.countActiveByCategory("cat-food"))
    }

    @Test
    fun pendientesYMarcarSincronizados() = runTest {
        seedBasics()
        dao.upsertAll(listOf(spending("s1", 1, at(today, 9), isSynced = false), spending("s2", 2, at(today, 9))))
        assertEquals(listOf("s1"), dao.pendingSync().map { it.spendingId })
        dao.markSynced(listOf("s1"))
        assertTrue(dao.pendingSync().isEmpty())
    }

    @Test
    fun purgaSoloLoSincronizadoConMasDe30Dias() = runTest {
        seedBasics()
        val hace31 = clock.instant().minus(Duration.ofDays(31))
        val hace29 = clock.instant().minus(Duration.ofDays(29))
        dao.upsertAll(
            listOf(
                spending("viejo", 1, at(today, 9), isDeleted = true, deletedAt = hace31),
                spending("reciente", 1, at(today, 9), isDeleted = true, deletedAt = hace29),
                spending("sinSubir", 1, at(today, 9), isDeleted = true, deletedAt = hace31, isSynced = false),
                spending("vivo", 1, at(today, 9)),
            ),
        )
        val resultado = LocalPurge(db, clock).purge()
        assertEquals(1, resultado.spendings)
        assertNull(dao.get("viejo"))
        assertEquals(setOf("reciente", "sinSubir", "vivo"), listOf("reciente", "sinSubir", "vivo").filter { dao.get(it) != null }.toSet())
    }
}
