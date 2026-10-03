package com.binc.gastapp.data.local

import androidx.room.withTransaction
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PurgeDeletedLocal de MAUI: borra en definitivo lo marcado como borrado hace mas de
 * [RETENTION_DAYS] dias. Solo toca filas ya sincronizadas, para no perder un borrado
 * que el servidor todavia no conoce. En la Fase 5.5 lo corre un PurgeWorker.
 */
@Singleton
class LocalPurge @Inject constructor(
    private val db: GastappDatabase,
    private val clock: Clock,
) {
    data class Result(val spendings: Int, val subscriptions: Int, val cards: Int) {
        val total: Int get() = spendings + subscriptions + cards
    }

    suspend fun purge(): Result {
        val cutoff: Instant = clock.instant().minus(Duration.ofDays(RETENTION_DAYS))
        return db.withTransaction {
            // El orden importa: los gastos y las suscripciones primero, porque una
            // tarjeta solo se puede borrar cuando ya nada la referencia.
            val spendings = db.spendingDao().purgeDeleted(cutoff)
            val subscriptions = db.subscriptionDao().purgeDeleted(cutoff)
            val cards = db.creditCardDao().purgeDeleted(cutoff)
            Result(spendings, subscriptions, cards)
        }
    }

    companion object {
        const val RETENTION_DAYS = 30L
    }
}
