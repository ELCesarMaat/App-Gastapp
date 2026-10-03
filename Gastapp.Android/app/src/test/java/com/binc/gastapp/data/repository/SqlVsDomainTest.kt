package com.binc.gastapp.data.repository

import com.binc.gastapp.data.local.CategoryEntity
import com.binc.gastapp.data.local.CreditCardEntity
import com.binc.gastapp.data.local.DbTest
import com.binc.gastapp.data.local.toDomain
import com.binc.gastapp.domain.cards.pendingAmount
import com.binc.gastapp.domain.spendings.categoryTotalsByPeriod
import com.binc.gastapp.domain.spendings.dayTotal
import com.binc.gastapp.domain.spendings.periodTotal
import com.binc.gastapp.domain.money.sumOfMoney
import java.math.BigDecimal
import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Los totales que suma SQLite (para la tira, Explorar periodo, categorias y tarjetas)
 * tienen que dar exactamente lo mismo que las funciones de :domain, que son las que
 * tienen paridad con MAUI. Si alguien cambia un criterio de un lado, esto truena.
 */
class SqlVsDomainTest : DbTest() {

    @Test
    fun totalesDeSqlIgualesALosDelDominio() = runTest {
        seedBasics()
        val random = Random(20261002)
        val categoryIds = listOf("cat-default", "cat-food") + (1..4).map { "cat-$it" }
        db.categoryDao().upsertAll(categoryIds.drop(2).map { CategoryEntity(it, userId, "Categoria $it", isSynced = true) })
        val cardIds = listOf("card-1", "card-2", "card-3")
        db.creditCardDao().upsertAll(cardIds.drop(1).map { CreditCardEntity(it, userId, it, "Banco", null, 10, 30) })

        val first = today.minusDays(45)
        val rows = (0 until 600).map { i ->
            val card = if (random.nextInt(3) == 0) cardIds.random(random) else null
            spending(
                id = "s$i",
                // Montos en centavos con cifras raras: 0.01 a 25,000.00.
                cents = random.nextLong(1, 2_500_000),
                date = first.plusDays(random.nextLong(0, 46))
                    .atTime(random.nextInt(24), random.nextInt(60), random.nextInt(60), random.nextInt(1_000_000_000)),
                categoryId = categoryIds.random(random),
                isCreditCard = card != null && random.nextInt(4) != 0,
                creditCardId = card,
                isDeleted = random.nextInt(10) == 0,
            )
        }
        db.spendingDao().upsertAll(rows)
        val domain = rows.map { it.toDomain() }

        // Total por dia (con compras con tarjeta) y del periodo (sin ellas).
        val days = spendings.observeDailyTotals(first, today).first().associateBy { it.day }
        generateSequence(first) { it.plusDays(1) }.takeWhile { it <= today }.forEach { day ->
            val sql = days[day]?.total ?: BigDecimal.ZERO
            assertEquals("dia $day", 0, dayTotal(domain, day).compareTo(sql))
        }
        for (offset in listOf(0L, 7L, 15L, 30L)) {
            val from = first.plusDays(offset)
            val sqlPeriod = spendings.observeDailyTotals(from, today).first().sumOfMoney { it.totalWithoutCardPurchases }
            assertEquals("periodo desde $from", periodTotal(domain, from, today).compareTo(sqlPeriod), 0)
        }

        // Por categoria (se compara como mapa: los empates no tienen orden fijo).
        val names = db.categoryDao().getAll().associate { it.categoryId to it.categoryName }
        val expected = categoryTotalsByPeriod(domain, names, first, today).associate { it.categoryId to it.amount }
        val actual = spendings.observeCategoryTotals(first, today).first().associate { it.categoryId to it.amount }
        assertEquals(expected, actual)

        // Deuda de cada tarjeta.
        val debts = cards.observeCardsWithDebt().first().associate { it.card.creditCardId to it.debt }
        cardIds.forEach { id -> assertEquals("tarjeta $id", 0, pendingAmount(id, domain).compareTo(debts.getValue(id))) }
    }
}
