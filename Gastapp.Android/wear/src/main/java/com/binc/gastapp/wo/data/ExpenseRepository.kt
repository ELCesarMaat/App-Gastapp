package com.binc.gastapp.wo.data

import com.binc.gastapp.wo.data.local.CategoryDao
import com.binc.gastapp.wo.data.local.CategoryEntity
import com.binc.gastapp.wo.data.local.DaySpendingDao
import com.binc.gastapp.wo.data.local.DaySpendingEntity
import com.binc.gastapp.wo.data.local.ExpenseDao
import com.binc.gastapp.wo.data.local.ExpenseEntity
import com.binc.gastapp.wo.data.local.SummaryDao
import com.binc.gastapp.wo.data.local.SummaryEntity
import com.binc.gastapp.wo.data.remote.DeviceCategoryDto
import com.binc.gastapp.wo.data.remote.DeviceDaySpendingDto
import com.binc.gastapp.wo.data.remote.DeviceExpenseDto
import com.binc.gastapp.wo.data.remote.WearTodayPayload
import com.binc.gastapp.wo.data.remote.GastappApi
import com.binc.gastapp.wo.domain.CategoryMatcher
import com.binc.gastapp.wo.domain.ParsedExpense
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.TimeUnit

class ExpenseRepository(
    private val api: GastappApi,
    private val expenseDao: ExpenseDao,
    private val categoryDao: CategoryDao,
    private val summaryDao: SummaryDao,
    private val daySpendingDao: DaySpendingDao
) {

    /** Maximo de gastos por peticion que acepta el servidor. */
    private val tamanoLote = 50

    /** Mas de esto no cabe en la pantalla del reloj ni vale la pena descargarlo. */
    private val topeGastosDelDia = 50

    /**
     * Guarda el gasto en local y devuelve la categoria elegida, para poder mostrarla
     * en la confirmacion. No toca la red: de eso se encarga el SyncWorker.
     */
    suspend fun saveLocally(parsed: ParsedExpense): Pair<ExpenseEntity, CategoryEntity?> = withContext(Dispatchers.IO) {
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
        expense to categoria
    }

    /**
     * Sube los pendientes. Lanza excepcion si la red falla, para que el worker
     * decida entre reintentar y rendirse.
     */
    suspend fun pushPending(): Int = withContext(Dispatchers.IO) {
        val pendientes = expenseDao.getUnsynced()
        if (pendientes.isEmpty()) return@withContext 0

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
        subidos
    }

    suspend fun refreshCategories() = withContext(Dispatchers.IO) {
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

    /**
     * Baja los gastos del dia ya consolidados en el servidor (los del reloj y los del
     * telefono) y reemplaza la cache local.
     */
    suspend fun refreshDayExpenses() = withContext(Dispatchers.IO) {
        val remotos = api.dayExpenses(
            period = "today",
            tzOffsetMinutes = desfaseHorarioMinutos(),
            limit = topeGastosDelDia
        )

        daySpendingDao.replaceAll(remotos.map { it.toEntity() })
    }

    /**
     * Aplica el estado del dia que empujo el telefono por Bluetooth.
     *
     * Escribe exactamente lo mismo que refreshDayExpenses y refreshSummary, pero sin
     * tocar la red: el telefono ya hizo ese trabajo. Si el reloj estaba lejos, la Data
     * Layer entrega esto al reconectar.
     */
    suspend fun applyPushedToday(payload: WearTodayPayload) = withContext(Dispatchers.IO) {
        daySpendingDao.replaceAll(payload.spendings.map { it.toEntity() })

        summaryDao.save(
            SummaryEntity(
                total = payload.total,
                count = payload.count,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /** Aplica las categorias que empujo el telefono. */
    suspend fun applyPushedCategories(remotas: List<DeviceCategoryDto>) = withContext(Dispatchers.IO) {
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

    suspend fun refreshSummary() = withContext(Dispatchers.IO) {
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

    fun observeSummary(): Flow<SummaryEntity?> = summaryDao.observe()

    fun observeDaySpendings(): Flow<List<DaySpendingEntity>> = daySpendingDao.observeAll()

    fun observeUnsynced(): Flow<List<ExpenseEntity>> = expenseDao.observeUnsynced()

    fun observeFailed(): Flow<Int> = expenseDao.observeFailed()

    /** Devuelve a la cola los gastos que se habian dado por perdidos. */
    suspend fun retryFailed() = expenseDao.resetFailed()

    /**
     * Borra todo lo local. Se llama al desvincular: hasta los gastos pendientes son de
     * la cuenta que se esta soltando, y dejarlos ahi los subiria a la siguiente cuenta
     * que se vincule en este reloj.
     */
    suspend fun clearAllLocalData() = withContext(Dispatchers.IO) {
        expenseDao.clearAll()
        daySpendingDao.clear()
        categoryDao.clear()
        summaryDao.save(SummaryEntity(total = 0.0, count = 0, updatedAt = 0L))
    }

    /**
     * Desfase local en minutos. El servidor lo usa para que "hoy" sea el dia del
     * usuario y no el dia UTC.
     */
    private fun desfaseHorarioMinutos(): Int {
        val calendario = Calendar.getInstance()
        val offsetMillis = calendario.get(Calendar.ZONE_OFFSET) + calendario.get(Calendar.DST_OFFSET)
        return (offsetMillis / 60000)
    }

    private fun DeviceDaySpendingDto.toEntity() = DaySpendingEntity(
        spendingId = spendingId,
        title = title,
        categoryName = categoryName,
        amount = amount,
        occurredAt = parsearInstante(occurredAt)
    )

    /**
     * El API serializa DateTime, y System.Text.Json solo escribe la Z cuando el valor
     * venia con Kind=Utc; si no, llega sin zona. Se normaliza con una comparacion de
     * texto en vez de encadenar parseos: la version anterior lanzaba dos excepciones
     * por cada gasto, y con 50 gastos eso son 100 stack traces en el hilo principal,
     * que es lo que hacia que la lista fuera a tirones.
     */
    private fun parsearInstante(iso: String): Long {
        val texto = iso.trim()
        val normalizado = if (traeZona(texto)) texto else texto + "Z"

        return runCatching { OffsetDateTime.parse(normalizado).toInstant().toEpochMilli() }
            .getOrElse { System.currentTimeMillis() }
    }

    private fun traeZona(texto: String): Boolean {
        if (texto.endsWith("Z", ignoreCase = true)) return true

        // Un desfase (+05:00, -06:00) siempre va despues de la hora, nunca en la fecha.
        val hora = texto.indexOf('T')
        if (hora < 0) return false

        return texto.indexOf('+', hora) > 0 || texto.indexOf('-', hora) > 0
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
