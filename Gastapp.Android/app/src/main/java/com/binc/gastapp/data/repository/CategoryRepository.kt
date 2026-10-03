package com.binc.gastapp.data.repository

import androidx.room.withTransaction
import com.binc.gastapp.data.local.CategoryEntity
import com.binc.gastapp.data.local.GastappDatabase
import com.binc.gastapp.data.local.toDomain
import com.binc.gastapp.domain.model.Category
import com.binc.gastapp.domain.model.DEFAULT_CATEGORY_NAME
import com.binc.gastapp.domain.model.isDefaultCategoryName
import com.binc.gastapp.sync.SyncScheduler
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Categorias del usuario. Port de la parte de categorias de SpendingService (MAUI). */
@Singleton
class CategoryRepository @Inject constructor(
    private val db: GastappDatabase,
    private val users: UserRepository,
    private val syncScheduler: SyncScheduler,
) {
    private val categoryDao = db.categoryDao()

    fun observeAll(): Flow<List<Category>> = categoryDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getAll(): List<Category> = categoryDao.getAll().map { it.toDomain() }

    suspend fun create(name: String): Category {
        val category = CategoryEntity(
            categoryId = UUID.randomUUID().toString(),
            userId = users.requireUserId(),
            categoryName = name.trim(),
            isDefaultCategory = false,
        )
        categoryDao.upsert(category)
        syncScheduler.requestSync()
        return category.toDomain()
    }

    /** Cuantos gastos vigentes tiene: se avisa antes de borrarla. */
    suspend fun countActiveSpendings(categoryId: String): Int = db.spendingDao().countActiveByCategory(categoryId)

    /** "Sin categoria" del usuario de la sesion; se crea si falta. */
    suspend fun ensureDefault(): Category = ensureDefaultFor(users.requireUserId()).toDomain()

    /**
     * Borra una categoria. Como en MAUI y en el API, sus gastos no se pierden: pasan a
     * "Sin categoria". La de por defecto no se puede borrar.
     *
     * Aqui solo se marca (isDeleted): la sincronizacion llama a DeleteCategory y, cuando
     * el API confirma, la borra de verdad. Asi un borrado sin red ya no se pierde.
     */
    suspend fun delete(categoryId: String): Boolean {
        val deleted = db.withTransaction {
            val category = categoryDao.get(categoryId)
            if (category == null || category.isDeleted) return@withTransaction false
            if (category.isDefaultCategory || isDefaultCategoryName(category.categoryName)) return@withTransaction false

            val fallback = ensureDefaultFor(category.userId)
            db.spendingDao().moveToCategory(categoryId, fallback.categoryId)
            db.subscriptionDao().clearCategory(categoryId)
            categoryDao.markDeleted(categoryId)
            true
        }
        if (deleted) syncScheduler.requestSync()
        return deleted
    }

    /**
     * EnsureDefaultCategoryForUser de MAUI: la marcada como por defecto; si no, la que se
     * llame "Sin categoria" (bases viejas sin la bandera), que se marca; si no, se crea.
     */
    internal suspend fun ensureDefaultFor(userId: String): CategoryEntity = db.withTransaction {
        val userCategories = categoryDao.getForUser(userId)
        userCategories.firstOrNull { it.isDefaultCategory }?.let { return@withTransaction it }

        val byName = userCategories.firstOrNull { isDefaultCategoryName(it.categoryName) }
        val result = byName?.copy(isDefaultCategory = true, categoryName = DEFAULT_CATEGORY_NAME, isSynced = false)
            ?: CategoryEntity(
                categoryId = UUID.randomUUID().toString(),
                userId = userId,
                categoryName = DEFAULT_CATEGORY_NAME,
                isDefaultCategory = true,
            )
        categoryDao.upsert(result)
        syncScheduler.requestSync()
        result
    }
}
