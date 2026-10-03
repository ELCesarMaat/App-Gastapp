package com.binc.gastapp.wo.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: ExpenseEntity)

    @Query("SELECT * FROM expenses WHERE synced = 0 AND failed = 0 ORDER BY occurredAt ASC")
    suspend fun getUnsynced(): List<ExpenseEntity>

    @Query("SELECT COUNT(*) FROM expenses WHERE synced = 0 AND failed = 0")
    suspend fun countPending(): Int

    @Query("SELECT COUNT(*) FROM expenses WHERE synced = 0 AND failed = 0")
    fun observePending(): Flow<Int>

    /** Los que todavia no llegan al servidor, para pintarlos en la lista del dia. */
    @Query("SELECT * FROM expenses WHERE synced = 0 AND failed = 0 ORDER BY occurredAt DESC")
    fun observeUnsynced(): Flow<List<ExpenseEntity>>

    @Query("UPDATE expenses SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("UPDATE expenses SET failed = 1 WHERE id = :id")
    suspend fun markFailed(id: String)

    /** Los que el servidor rechazo de forma permanente y ya nadie reintenta. */
    @Query("SELECT COUNT(*) FROM expenses WHERE synced = 0 AND failed = 1")
    fun observeFailed(): Flow<Int>

    /**
     * Los devuelve a la cola. Solo se llama cuando el usuario pide sincronizar a mano:
     * es una peticion explicita de reintentar, no un bucle automatico.
     */
    @Query("UPDATE expenses SET failed = 0, syncAttempts = 0 WHERE synced = 0 AND failed = 1")
    suspend fun resetFailed()

    @Query("UPDATE expenses SET syncAttempts = syncAttempts + 1 WHERE id = :id")
    suspend fun incrementAttempts(id: String)

    /** Limpia gastos ya sincronizados y viejos, para que la base del reloj no crezca. */
    @Query("DELETE FROM expenses WHERE synced = 1 AND occurredAt < :cutoff")
    suspend fun purgeSynced(cutoff: Long)

    @Query("DELETE FROM expenses")
    suspend fun clearAll()
}

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY position ASC")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories ORDER BY position ASC LIMIT 1")
    suspend fun getDefault(): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Query("DELETE FROM categories")
    suspend fun clear()
}

@Dao
interface SummaryDao {

    @Query("SELECT * FROM summary WHERE id = 0")
    suspend fun get(): SummaryEntity?

    @Query("SELECT * FROM summary WHERE id = 0")
    fun observe(): Flow<SummaryEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(summary: SummaryEntity)
}

@Dao
interface DaySpendingDao {

    @Query("SELECT * FROM day_spendings ORDER BY occurredAt DESC")
    fun observeAll(): Flow<List<DaySpendingEntity>>

    /**
     * Sustituye la cache entera. Va en una transaccion para que la lista nunca se
     * vea vacia a mitad del refresco.
     */
    @Transaction
    suspend fun replaceAll(items: List<DaySpendingEntity>) {
        clear()
        insertAll(items)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<DaySpendingEntity>)

    @Query("DELETE FROM day_spendings")
    suspend fun clear()
}
