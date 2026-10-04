package com.binc.gastapp.data.repository

import androidx.room.withTransaction
import com.binc.gastapp.data.local.GastappDatabase
import com.binc.gastapp.data.local.SpendingEntity
import com.binc.gastapp.data.local.toDomain
import com.binc.gastapp.data.local.toEntity
import com.binc.gastapp.domain.model.DEFAULT_CATEGORY_NAME
import com.binc.gastapp.domain.model.PlannedSpending
import com.binc.gastapp.domain.model.Spending
import com.binc.gastapp.domain.money.centsToMoney
import com.binc.gastapp.domain.spendings.CategoryTotal
import com.binc.gastapp.sync.SyncScheduler
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Totales de un dia que tiene gastos. */
data class DayTotal(
    val day: LocalDate,
    /** Todo lo del dia, compras con tarjeta incluidas (el total del dia de MAUI, regla 16). */
    val total: BigDecimal,
    /**
     * Lo que cuenta en los totales: sin compras con tarjeta. Desde la Fase 4 es el
     * criterio del dia y del periodo (decision del usuario, anexo F).
     */
    val totalWithoutCardPurchases: BigDecimal,
    val spendingCount: Int,
)

/**
 * Gastos (y pagos a tarjeta, que tambien son gastos). Se escribe primero en Room con
 * isSynced = false y luego se avisa al [SyncScheduler]; la UI nunca espera a la red.
 *
 * Los rangos son de dias completos: de las 00:00 del primer dia a las 00:00 del dia
 * siguiente al ultimo. Es el criterio de periodTotal en :domain, que corrige a MAUI
 * (alla el total del calendario dejaba fuera el ultimo dia).
 */
@Singleton
class SpendingRepository @Inject constructor(
    private val db: GastappDatabase,
    private val users: UserRepository,
    private val categories: CategoryRepository,
    private val syncScheduler: SyncScheduler,
    private val clock: Clock,
) {
    private val spendingDao = db.spendingDao()

    fun observe(spendingId: String): Flow<Spending?> = spendingDao.observe(spendingId).map { it?.toDomain() }

    suspend fun get(spendingId: String): Spending? = spendingDao.get(spendingId)?.toDomain()

    /** Gastos de un dia, en orden de hora. */
    fun observeDay(day: LocalDate): Flow<List<Spending>> = observeRange(day, day)

    fun observeRange(firstDay: LocalDate, lastDay: LocalDate): Flow<List<Spending>> =
        spendingDao.observeBetween(firstDay.atStartOfDay(), lastDay.plusDays(1).atStartOfDay()).map(::toDomainList)

    /** Solo los dias con gastos; los que no aparecen van en cero. */
    fun observeDailyTotals(firstDay: LocalDate, lastDay: LocalDate): Flow<List<DayTotal>> =
        spendingDao.observeDailyTotals(firstDay.atStartOfDay(), lastDay.plusDays(1).atStartOfDay()).map { rows ->
            rows.map {
                DayTotal(
                    day = it.day,
                    total = it.totalCents.centsToMoney(),
                    totalWithoutCardPurchases = it.withoutCardPurchasesCents.centsToMoney(),
                    spendingCount = it.spendingCount,
                )
            }
        }

    /** Lo mismo que categoryTotalsByPeriod de :domain, pero sumado en SQL. */
    fun observeCategoryTotals(firstDay: LocalDate, lastDay: LocalDate): Flow<List<CategoryTotal>> =
        spendingDao.observeCategoryTotals(firstDay.atStartOfDay(), lastDay.plusDays(1).atStartOfDay()).map { rows ->
            rows.map { CategoryTotal(it.categoryId, it.categoryName ?: DEFAULT_CATEGORY_NAME, it.totalCents.centsToMoney()) }
        }

    /** Gastos de una o varias categorias en el rango, sin compras con tarjeta (criterio del periodo). */
    fun observeCategorySpendings(categoryIds: List<String>, firstDay: LocalDate, lastDay: LocalDate): Flow<List<Spending>> =
        spendingDao.observeByCategoryBetween(categoryIds, firstDay.atStartOfDay(), lastDay.plusDays(1).atStartOfDay())
            .map(::toDomainList)

    /** Dia del gasto vigente mas viejo; null si no hay gastos. */
    fun observeFirstDay(): Flow<LocalDate?> = spendingDao.observeFirstDate().map { it?.toLocalDate() }

    /** Compras y abonos vigentes de todas las tarjetas: la entrada de domain/cards. */
    fun observeCardMovements(): Flow<List<Spending>> = spendingDao.observeCardMovements().map(::toDomainList)

    /**
     * Alta o edicion de un gasto. Sin usuario se toma el de la sesion y sin categoria
     * va a "Sin categoria".
     */
    suspend fun save(spending: Spending): Spending {
        val saved = db.withTransaction {
            val userId = spending.userId.ifBlank { users.requireUserId() }
            val categoryId = spending.categoryId.ifBlank { categories.ensureDefaultFor(userId).categoryId }
            val toSave = spending.copy(userId = userId, categoryId = categoryId)
            val deletedAt = if (toSave.isDeleted) spendingDao.get(toSave.spendingId)?.deletedAt ?: clock.instant() else null
            spendingDao.upsert(toSave.toEntity(isSynced = false, deletedAt = deletedAt))
            toSave
        }
        syncScheduler.requestSync()
        return saved
    }

    /**
     * Guarda un gasto que propuso el dominio: pago a tarjeta, ajuste de saldo, cobro de
     * suscripcion, saldo inicial de una tarjeta en uso...
     */
    suspend fun addPlanned(planned: PlannedSpending, categoryId: String?, userId: String? = null): Spending = save(
        Spending(
            spendingId = UUID.randomUUID().toString(),
            amount = planned.amount,
            date = planned.date,
            userId = userId.orEmpty(),
            categoryId = categoryId.orEmpty(),
            title = planned.title,
            description = planned.description,
            isCreditCard = planned.isCreditCard,
            creditCardId = planned.creditCardId,
            paymentMethod = planned.paymentMethod,
            isMsi = planned.isMsi,
            totalInstallments = planned.totalInstallments,
            currentInstallment = planned.currentInstallment,
            installmentMonthlyAmount = planned.installmentMonthlyAmount,
        ),
    )

    /** Borrado logico: se marca y se sube; la purga lo quita a los 30 dias. */
    suspend fun delete(spendingId: String): Boolean {
        val changed = spendingDao.markDeleted(spendingId, clock.instant()) > 0
        if (changed) syncScheduler.requestSync()
        return changed
    }

    /** "Deshacer" del Snackbar despues de borrar. */
    suspend fun restore(spendingId: String): Boolean {
        val changed = spendingDao.restore(spendingId) > 0
        if (changed) syncScheduler.requestSync()
        return changed
    }

    private fun toDomainList(list: List<SpendingEntity>): List<Spending> = list.map { it.toDomain() }
}
