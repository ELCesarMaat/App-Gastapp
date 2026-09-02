package com.binc.gastapp.wo.data

import com.binc.gastapp.wo.data.local.CategoryDao
import com.binc.gastapp.wo.data.local.CategoryEntity
import com.binc.gastapp.wo.data.local.ExpenseDao
import com.binc.gastapp.wo.data.local.ExpenseEntity
import com.binc.gastapp.wo.data.local.SummaryDao
import com.binc.gastapp.wo.data.local.SummaryEntity
import com.binc.gastapp.wo.data.remote.DeviceExpenseDto
import com.binc.gastapp.wo.data.remote.GastappApi
import com.binc.gastapp.wo.domain.CategoryMatcher
import com.binc.gastapp.wo.domain.ParsedExpense
import java.time.Instant
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.TimeUnit

class ExpenseRepository(
    private val api: GastappApi,
    private val expenseDao: ExpenseDao,
    private val categoryDao: CategoryDao,
    private val summaryDao: SummaryDao
) {

    /** Maximo de gastos por peticion que acepta el servidor. */
    private val tamanoLote = 50

    /**
     * Guarda el gasto en local y devuelve la categoria elegida, para poder mostrarla
     * en la confirmacion. No toca la red: de eso se encarga el SyncWorker.
     */
    suspend fun saveLocally(parsed: ParsedExpense): Pair<ExpenseEntity, CategoryEntity?> {
        val categorias = categoryDao.getAll()
        val categoriaId = CategoryMatcher.match(parsed.rawInput, categorias)
        val categoria = categorias.firstOrNull { it.categoryId == categoriaId }

        val expense = ExpenseEntity(
            id = UUID.randomUUID().toString(),
            amount = parsed.amount,
            title = parsed.title,
            categoryId = categoriaId,
            occurredAt = System.currentTimeMillis(),
            rawInput = parsed.rawInput,
            needsReview = parsed.needsReview
        )

        expenseDao.insert(expense)
        return expense to categoria
    }

    /**
     * Sube los pendientes. Lanza excepcion si la red falla, para que el worker
     * decida entre reintentar y rendirse.
     */
    suspend fun pushPending(): Int {
        val pendientes = expenseDao.getUnsynced()
        if (pendientes.isEmpty()) return 0

        var subidos = 0
        pendientes.chunked(tamanoLote).forEach { lote ->
            val resultado = api.createExpenses(lote.map { it.toDto() })

            // created true o false dan igual: ambos significan que ya esta en el servidor.
            resultado.results.forEach { r ->
                expenseDao.markSynced(r.spendingId)
                subidos++
            }
        }

        // Los ya sincronizados de mas de una semana no le sirven a nadie en el reloj.
        expenseDao.purgeSynced(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7))
        return subidos
    }

    suspend fun refreshCategories() {
        val remotas = api.categories()
        categoryDao.clear()
        categoryDao.insertAll(
            remotas.mapIndexed { indice, dto ->
                CategoryEntity(
                    categoryId = dto.categoryId,
                    categoryName = dto.categoryName,
                    isDefaultCategory = dto.isDefaultCategory,
                    position = indice
                )
            }
        )
    }

    suspend fun refreshSummary() {
        val resumen = api.summary(period = "today", tzOffsetMinutes = desfaseHorarioMinutos())
        summaryDao.save(
            SummaryEntity(
                total = resumen.total,
                count = resumen.count,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun cachedSummary(): SummaryEntity? = summaryDao.get()

    suspend fun pendingCount(): Int = expenseDao.countPending()

    /**
     * Desfase local en minutos. El servidor lo usa para que "hoy" sea el dia del
     * usuario y no el dia UTC.
     */
    private fun desfaseHorarioMinutos(): Int {
        val calendario = Calendar.getInstance()
        val offsetMillis = calendario.get(Calendar.ZONE_OFFSET) + calendario.get(Calendar.DST_OFFSET)
        return (offsetMillis / 60000)
    }

    private fun ExpenseEntity.toDto() = DeviceExpenseDto(
        spendingId = id,
        amount = amount,
        title = title,
        categoryId = categoryId,
        occurredAt = Instant.ofEpochMilli(occurredAt).toString(),
        rawInput = rawInput,
        needsReview = needsReview
    )
}
