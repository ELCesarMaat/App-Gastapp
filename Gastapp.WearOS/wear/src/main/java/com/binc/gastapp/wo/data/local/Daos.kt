package com.binc.gastapp.wo.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Query("UPDATE expenses SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("UPDATE expenses SET failed = 1 WHERE id = :id")
    suspend fun markFailed(id: String)

    @Query("UPDATE expenses SET syncAttempts = syncAttempts + 1 WHERE id = :id")
    suspend fun incrementAttempts(id: String)

    /** Limpia gastos ya sincronizados y viejos, para que la base del reloj no crezca. */
    @Query("DELETE FROM expenses WHERE synced = 1 AND occurredAt < :cutoff")
    suspend fun purgeSynced(cutoff: Long)
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(summary: SummaryEntity)
}
