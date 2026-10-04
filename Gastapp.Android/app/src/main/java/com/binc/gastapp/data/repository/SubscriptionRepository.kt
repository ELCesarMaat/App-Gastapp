package com.binc.gastapp.data.repository

import androidx.room.withTransaction
import com.binc.gastapp.data.local.GastappDatabase
import com.binc.gastapp.data.local.toDomain
import com.binc.gastapp.data.local.toEntity
import com.binc.gastapp.domain.model.MovementTexts
import com.binc.gastapp.domain.model.Spending
import com.binc.gastapp.domain.model.Subscription
import com.binc.gastapp.domain.subscriptions.chargeCategoryId
import com.binc.gastapp.domain.subscriptions.subscriptionCharge
import com.binc.gastapp.sync.SyncScheduler
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Suscripciones. Una suscripcion no mueve dinero por si sola: lo que lo mueve es el
 * gasto que crea [registerCharge].
 */
@Singleton
class SubscriptionRepository @Inject constructor(
    private val db: GastappDatabase,
    private val users: UserRepository,
    private val categories: CategoryRepository,
    private val spendings: SpendingRepository,
    private val syncScheduler: SyncScheduler,
    /** Titulo y descripcion del gasto de cada cobro, en el idioma del telefono. */
    private val texts: MovementTexts,
    private val clock: Clock,
) {
    private val subscriptionDao = db.subscriptionDao()

    fun observeAll(): Flow<List<Subscription>> =
        subscriptionDao.observeAll().map { list -> list.map { it.toDomain(clock.zone) } }

    suspend fun getAll(): List<Subscription> = subscriptionDao.getAll().map { it.toDomain(clock.zone) }

    suspend fun get(subscriptionId: String): Subscription? =
        subscriptionDao.get(subscriptionId)?.takeUnless { it.isDeleted }?.toDomain(clock.zone)

    /** Alta o edicion. Sin usuario se toma el de la sesion. */
    suspend fun save(subscription: Subscription): Subscription {
        val toSave = subscription.copy(userId = subscription.userId.ifBlank { users.requireUserId() })
        subscriptionDao.upsert(toSave.toEntity(isSynced = false, deletedAt = null, zone = clock.zone))
        syncScheduler.requestSync()
        return toSave
    }

    suspend fun delete(subscriptionId: String): Boolean {
        val changed = subscriptionDao.markDeleted(subscriptionId, clock.instant()) > 0
        if (changed) syncScheduler.requestSync()
        return changed
    }

    /** Pausar (deja de contar en los totales y de avisar) o reanudar. */
    suspend fun setActive(subscriptionId: String, isActive: Boolean): Boolean {
        val changed = subscriptionDao.setActive(subscriptionId, isActive) > 0
        if (changed) syncScheduler.requestSync()
        return changed
    }

    /**
     * RegisterCharge de SubscriptionsViewModel (MAUI): crea el gasto del cobro (a la
     * tarjeta si se paga con tarjeta) y anota el cobro en la suscripcion, las dos cosas
     * en una transaccion. No revisa si el cobro del periodo ya estaba registrado: eso lo
     * avisa la UI sin bloquear (regla 6).
     */
    suspend fun registerCharge(subscriptionId: String, amountCharged: BigDecimal): Spending? {
        val spending = db.withTransaction {
            val subscription = get(subscriptionId) ?: return@withTransaction null
            val categoryId = chargeCategoryId(subscription, categories.getAll()) ?: categories.ensureDefault().categoryId
            val planned = subscriptionCharge(subscription, amountCharged, LocalDateTime.now(clock), texts)
            val spending = spendings.addPlanned(planned, categoryId, subscription.userId)
            subscriptionDao.markChargeRegistered(subscriptionId, spending.date.atZone(clock.zone).toInstant())
            spending
        }
        if (spending != null) syncScheduler.requestSync()
        return spending
    }
}
