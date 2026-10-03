package com.binc.gastapp.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Upsert
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow

// Consultas de la base local. Las que alimentan pantallas devuelven Flow: Room vuelve a
// emitir cada vez que cambia una tabla que la consulta lee, y eso reemplaza a los
// mensajes SpendingChanged / DayChanged de MAUI.
//
// Siempre @Upsert y nunca @Insert(REPLACE): REPLACE borra la fila y la vuelve a crear,
// y con las llaves en cascada eso se llevaria los gastos de una categoria o de un usuario.
//
// Los rangos de fechas son [from, until): desde las 00:00 del primer dia hasta las 00:00
// del dia siguiente al ultimo. Las fechas se comparan como texto de ancho fijo (ver
// Converters.kt), por eso substr(date, 1, 10) es el dia.

/** Totales de un dia, para la tira de Resumen y el calendario de Explorar periodo. */
data class DayTotalRow(
    val day: LocalDate,
    /** Criterio del total del DIA: todo, compras con tarjeta incluidas (regla 16). */
    val totalCents: Long,
    /** Criterio del total del PERIODO: sin compras con tarjeta (regla 16). */
    val withoutCardPurchasesCents: Long,
    val spendingCount: Int,
)

/** Gasto de una categoria en un rango, sin compras con tarjeta (criterio del periodo). */
data class CategoryTotalRow(
    val categoryId: String,
    /** Null si la categoria ya no existe localmente. */
    val categoryName: String?,
    val totalCents: Long,
)

/** Una tarjeta con su saldo: compras menos abonos. Puede ser negativo si se pago de mas. */
data class CardWithBalanceRow(
    @Embedded val card: CreditCardEntity,
    val balanceCents: Long,
)

@Dao
interface IncomeTypeDao {
    @Upsert
    suspend fun upsertAll(incomeTypes: List<IncomeTypeEntity>)

    @Query("SELECT * FROM income_types ORDER BY incomeTypeId")
    fun observeAll(): Flow<List<IncomeTypeEntity>>

    @Query("SELECT * FROM income_types ORDER BY incomeTypeId")
    suspend fun getAll(): List<IncomeTypeEntity>
}

@Dao
interface UserDao {
    @Upsert
    suspend fun upsert(user: UserEntity)

    /** Solo hay un usuario: el de la sesion. */
    @Query("SELECT * FROM users LIMIT 1")
    fun observe(): Flow<UserEntity?>

    @Query("SELECT * FROM users LIMIT 1")
    suspend fun get(): UserEntity?

    @Query("SELECT * FROM users WHERE isSynced = 0 LIMIT 1")
    suspend fun pendingSync(): UserEntity?

    @Query("UPDATE users SET isSynced = 1 WHERE userId = :userId")
    suspend fun markSynced(userId: String)
}

@Dao
interface CategoryDao {
    @Upsert
    suspend fun upsert(category: CategoryEntity)

    @Upsert
    suspend fun upsertAll(categories: List<CategoryEntity>)

    /** La de por defecto primero y luego por nombre. */
    @Query("SELECT * FROM categories WHERE isDeleted = 0 ORDER BY isDefaultCategory DESC, categoryName COLLATE NOCASE")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE isDeleted = 0 ORDER BY isDefaultCategory DESC, categoryName COLLATE NOCASE")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE categoryId = :categoryId")
    suspend fun get(categoryId: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE userId = :userId AND isDeleted = 0")
    suspend fun getForUser(userId: String): List<CategoryEntity>

    @Query("UPDATE categories SET isDeleted = 1, isSynced = 0 WHERE categoryId = :categoryId")
    suspend fun markDeleted(categoryId: String): Int

    /** Altas y cambios por subir (las borradas van aparte: el API no recibe IsDeleted de categorias). */
    @Query("SELECT * FROM categories WHERE isSynced = 0 AND isDeleted = 0")
    suspend fun pendingSync(): List<CategoryEntity>

    /** Borradas que falta avisar al API con DeleteCategory. */
    @Query("SELECT * FROM categories WHERE isDeleted = 1")
    suspend fun pendingDeletion(): List<CategoryEntity>

    @Query("UPDATE categories SET isSynced = 1 WHERE categoryId IN (:categoryIds)")
    suspend fun markSynced(categoryIds: List<String>)

    /** Borrado definitivo, cuando el API ya confirmo DeleteCategory. */
    @Query("DELETE FROM categories WHERE categoryId = :categoryId")
    suspend fun delete(categoryId: String): Int
}

@Dao
interface SpendingDao {
    @Upsert
    suspend fun upsert(spending: SpendingEntity)

    @Upsert
    suspend fun upsertAll(spendings: List<SpendingEntity>)

    @Query("SELECT * FROM spendings WHERE spendingId = :spendingId")
    suspend fun get(spendingId: String): SpendingEntity?

    @Query("SELECT * FROM spendings WHERE spendingId = :spendingId")
    fun observe(spendingId: String): Flow<SpendingEntity?>

    /** Gastos vigentes del rango, en orden de hora (GetSpendingListByDateAsync). */
    @Query("SELECT * FROM spendings WHERE isDeleted = 0 AND date >= :from AND date < :until ORDER BY date")
    fun observeBetween(from: LocalDateTime, until: LocalDateTime): Flow<List<SpendingEntity>>

    /** Un renglon por cada dia del rango que tiene gastos (los demas no aparecen). */
    @Query(
        """
        SELECT substr(date, 1, 10) AS day,
               SUM(amountCents) AS totalCents,
               SUM(CASE WHEN isCreditCard = 0 THEN amountCents ELSE 0 END) AS withoutCardPurchasesCents,
               COUNT(*) AS spendingCount
        FROM spendings
        WHERE isDeleted = 0 AND date >= :from AND date < :until
        GROUP BY day
        ORDER BY day
        """,
    )
    fun observeDailyTotals(from: LocalDateTime, until: LocalDateTime): Flow<List<DayTotalRow>>

    /** GetCategoryResumeByPeriod: por categoria, de mayor a menor, sin compras con tarjeta. */
    @Query(
        """
        SELECT s.categoryId AS categoryId, c.categoryName AS categoryName, SUM(s.amountCents) AS totalCents
        FROM spendings s
        LEFT JOIN categories c ON c.categoryId = s.categoryId
        WHERE s.isDeleted = 0 AND s.isCreditCard = 0 AND s.date >= :from AND s.date < :until
        GROUP BY s.categoryId
        ORDER BY totalCents DESC
        """,
    )
    fun observeCategoryTotals(from: LocalDateTime, until: LocalDateTime): Flow<List<CategoryTotalRow>>

    /** Detalle de categoria: sus gastos del rango, del mas reciente al mas viejo. */
    @Query(
        """
        SELECT * FROM spendings
        WHERE isDeleted = 0 AND categoryId = :categoryId AND date >= :from AND date < :until
        ORDER BY date DESC
        """,
    )
    fun observeByCategoryBetween(categoryId: String, from: LocalDateTime, until: LocalDateTime): Flow<List<SpendingEntity>>

    /**
     * Todo lo vigente que toca una tarjeta (compras y abonos). Es la entrada de las
     * funciones de domain/cards: deuda, ciclo, fecha limite y MSI.
     */
    @Query("SELECT * FROM spendings WHERE isDeleted = 0 AND creditCardId IS NOT NULL")
    fun observeCardMovements(): Flow<List<SpendingEntity>>

    @Query("SELECT COUNT(*) FROM spendings WHERE isDeleted = 0 AND categoryId = :categoryId")
    suspend fun countActiveByCategory(categoryId: String): Int

    /** Al borrar una categoria sus gastos (borrados incluidos) pasan a otra, como en MAUI y el API. */
    @Query("UPDATE spendings SET categoryId = :toCategoryId, isSynced = 0 WHERE categoryId = :fromCategoryId")
    suspend fun moveToCategory(fromCategoryId: String, toCategoryId: String): Int

    @Query("UPDATE spendings SET isDeleted = 1, deletedAt = :deletedAt, isSynced = 0 WHERE spendingId = :spendingId AND isDeleted = 0")
    suspend fun markDeleted(spendingId: String, deletedAt: Instant): Int

    /** Deshacer un borrado. */
    @Query("UPDATE spendings SET isDeleted = 0, deletedAt = NULL, isSynced = 0 WHERE spendingId = :spendingId AND isDeleted = 1")
    suspend fun restore(spendingId: String): Int

    @Query("SELECT * FROM spendings WHERE isSynced = 0")
    suspend fun pendingSync(): List<SpendingEntity>

    @Query("UPDATE spendings SET isSynced = 1 WHERE spendingId IN (:spendingIds)")
    suspend fun markSynced(spendingIds: List<String>)

    /** PurgeDeletedLocal: solo lo ya sincronizado, para no perder un borrado que el servidor no conoce. */
    @Query("DELETE FROM spendings WHERE isDeleted = 1 AND isSynced = 1 AND deletedAt IS NOT NULL AND deletedAt < :cutoff")
    suspend fun purgeDeleted(cutoff: Instant): Int
}

@Dao
interface CreditCardDao {
    @Upsert
    suspend fun upsert(card: CreditCardEntity)

    @Upsert
    suspend fun upsertAll(cards: List<CreditCardEntity>)

    @Query("SELECT * FROM credit_cards WHERE creditCardId = :creditCardId")
    suspend fun get(creditCardId: String): CreditCardEntity?

    @Query("SELECT * FROM credit_cards WHERE isDeleted = 0 ORDER BY cardName")
    fun observeAll(): Flow<List<CreditCardEntity>>

    @Query("SELECT * FROM credit_cards WHERE isDeleted = 0 ORDER BY cardName")
    suspend fun getAll(): List<CreditCardEntity>

    /** Tarjetas vigentes con su saldo (GetPendingAmountForCardAsync sin el tope en cero). */
    @Query(
        """
        SELECT cc.*,
               COALESCE(SUM(CASE WHEN s.isCreditCard = 1 THEN s.amountCents ELSE -s.amountCents END), 0) AS balanceCents
        FROM credit_cards cc
        LEFT JOIN spendings s ON s.creditCardId = cc.creditCardId AND s.isDeleted = 0
        WHERE cc.isDeleted = 0
        GROUP BY cc.creditCardId
        ORDER BY cc.cardName
        """,
    )
    fun observeWithBalance(): Flow<List<CardWithBalanceRow>>

    @Query("UPDATE credit_cards SET isDeleted = 1, deletedAt = :deletedAt, isSynced = 0 WHERE creditCardId = :creditCardId AND isDeleted = 0")
    suspend fun markDeleted(creditCardId: String, deletedAt: Instant): Int

    @Query("SELECT * FROM credit_cards WHERE isSynced = 0")
    suspend fun pendingSync(): List<CreditCardEntity>

    @Query("UPDATE credit_cards SET isSynced = 1 WHERE creditCardId IN (:creditCardIds)")
    suspend fun markSynced(creditCardIds: List<String>)

    /** Solo tarjetas que ya ningun gasto ni suscripcion referencia (la llave no deja otra cosa). */
    @Query(
        """
        DELETE FROM credit_cards
        WHERE isDeleted = 1 AND isSynced = 1 AND deletedAt IS NOT NULL AND deletedAt < :cutoff
          AND NOT EXISTS (SELECT 1 FROM spendings s WHERE s.creditCardId = credit_cards.creditCardId)
          AND NOT EXISTS (SELECT 1 FROM subscriptions su WHERE su.creditCardId = credit_cards.creditCardId)
        """,
    )
    suspend fun purgeDeleted(cutoff: Instant): Int
}

@Dao
interface SubscriptionDao {
    @Upsert
    suspend fun upsert(subscription: SubscriptionEntity)

    @Upsert
    suspend fun upsertAll(subscriptions: List<SubscriptionEntity>)

    @Query("SELECT * FROM subscriptions WHERE subscriptionId = :subscriptionId")
    suspend fun get(subscriptionId: String): SubscriptionEntity?

    /** Activas primero (GetAllAsync de MAUI); el orden fino lo da la pantalla. */
    @Query("SELECT * FROM subscriptions WHERE isDeleted = 0 ORDER BY isActive DESC, serviceName COLLATE NOCASE")
    fun observeAll(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE isDeleted = 0 ORDER BY isActive DESC, serviceName COLLATE NOCASE")
    suspend fun getAll(): List<SubscriptionEntity>

    @Query("UPDATE subscriptions SET isDeleted = 1, deletedAt = :deletedAt, isSynced = 0 WHERE subscriptionId = :subscriptionId AND isDeleted = 0")
    suspend fun markDeleted(subscriptionId: String, deletedAt: Instant): Int

    @Query("UPDATE subscriptions SET isActive = :isActive, isSynced = 0 WHERE subscriptionId = :subscriptionId AND isDeleted = 0")
    suspend fun setActive(subscriptionId: String, isActive: Boolean): Int

    @Query("UPDATE subscriptions SET lastChargeRegisteredAt = :chargedAt, isSynced = 0 WHERE subscriptionId = :subscriptionId AND isDeleted = 0")
    suspend fun markChargeRegistered(subscriptionId: String, chargedAt: Instant): Int

    /** Lo que hace el SET NULL de la llave, pero al momento de borrar la categoria (que solo se marca). */
    @Query("UPDATE subscriptions SET categoryId = NULL, isSynced = 0 WHERE categoryId = :categoryId")
    suspend fun clearCategory(categoryId: String): Int

    @Query("SELECT * FROM subscriptions WHERE isSynced = 0")
    suspend fun pendingSync(): List<SubscriptionEntity>

    @Query("UPDATE subscriptions SET isSynced = 1 WHERE subscriptionId IN (:subscriptionIds)")
    suspend fun markSynced(subscriptionIds: List<String>)

    @Query("DELETE FROM subscriptions WHERE isDeleted = 1 AND isSynced = 1 AND deletedAt IS NOT NULL AND deletedAt < :cutoff")
    suspend fun purgeDeleted(cutoff: Instant): Int
}
