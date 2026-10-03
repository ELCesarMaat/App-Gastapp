package com.binc.gastapp.data.repository

import androidx.room.withTransaction
import com.binc.gastapp.data.local.GastappDatabase
import com.binc.gastapp.data.local.toDomain
import com.binc.gastapp.data.local.toEntity
import com.binc.gastapp.domain.model.CreditCard
import com.binc.gastapp.domain.model.PlannedSpending
import com.binc.gastapp.domain.money.centsToMoney
import com.binc.gastapp.sync.SyncScheduler
import java.math.BigDecimal
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Una tarjeta con su saldo (compras menos abonos). */
data class CardWithDebt(val card: CreditCard, val balance: BigDecimal) {
    /** La deuda nunca es negativa, igual que pendingAmount de :domain. */
    val debt: BigDecimal get() = balance.max(BigDecimal.ZERO)
}

/**
 * Tarjetas de credito. Las fechas de corte y de pago no se guardan: salen de los dias
 * del mes con domain/cards, alimentado con [SpendingRepository.observeCardMovements].
 */
@Singleton
class CreditCardRepository @Inject constructor(
    private val db: GastappDatabase,
    private val users: UserRepository,
    private val spendings: SpendingRepository,
    private val syncScheduler: SyncScheduler,
    private val clock: Clock,
) {
    private val cardDao = db.creditCardDao()

    fun observeCards(): Flow<List<CreditCard>> = cardDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getCards(): List<CreditCard> = cardDao.getAll().map { it.toDomain() }

    /** Tarjetas vigentes con su deuda, sumada en SQL. */
    fun observeCardsWithDebt(): Flow<List<CardWithDebt>> = cardDao.observeWithBalance().map { rows ->
        rows.map { CardWithDebt(it.card.toDomain(), it.balanceCents.centsToMoney()) }
    }

    suspend fun get(creditCardId: String): CreditCard? = cardDao.get(creditCardId)?.takeUnless { it.isDeleted }?.toDomain()

    /** Alta o edicion. Sin usuario se toma el de la sesion. */
    suspend fun save(card: CreditCard): CreditCard {
        val toSave = card.copy(userId = card.userId.ifBlank { users.requireUserId() })
        cardDao.upsert(toSave.toEntity(isSynced = false))
        syncScheduler.requestSync()
        return toSave
    }

    /**
     * Alta de una tarjeta que ya se venia usando: la tarjeta y sus movimientos iniciales
     * (saldo de contado y MSI previas, de inUseCardMovements en :domain) en una sola
     * transaccion, para que nunca quede la tarjeta sin su saldo.
     */
    suspend fun createWithMovements(card: CreditCard, movements: List<PlannedSpending>, categoryId: String?): CreditCard =
        db.withTransaction {
            val saved = save(card)
            movements.forEach { spendings.addPlanned(it, categoryId, saved.userId) }
            saved
        }

    /**
     * Borrado logico, como MAUI. Sus gastos y sus suscripciones se quedan como estan; la
     * purga solo la borra de verdad cuando ya nada la referencia.
     */
    suspend fun delete(creditCardId: String): Boolean {
        val changed = cardDao.markDeleted(creditCardId, clock.instant()) > 0
        if (changed) syncScheduler.requestSync()
        return changed
    }
}
