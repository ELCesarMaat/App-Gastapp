package com.binc.gastapp.data.repository

import com.binc.gastapp.data.local.GastappDatabase
import com.binc.gastapp.data.local.toDomain
import com.binc.gastapp.data.local.toEntity
import com.binc.gastapp.domain.model.IncomeType
import com.binc.gastapp.domain.model.User
import com.binc.gastapp.sync.SyncScheduler
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** El usuario de la sesion y el catalogo de frecuencias de ingreso. */
@Singleton
class UserRepository @Inject constructor(
    db: GastappDatabase,
    private val syncScheduler: SyncScheduler,
) {
    private val userDao = db.userDao()
    private val incomeTypeDao = db.incomeTypeDao()

    fun observeUser(): Flow<User?> = userDao.observe().map { it?.toDomain() }

    suspend fun getUser(): User? = userDao.get()?.toDomain()

    fun observeIncomeTypes(): Flow<List<IncomeType>> = incomeTypeDao.observeAll().map { list -> list.map { it.toDomain() } }

    /** Perfil editado (frecuencia y dias de pago, sueldo, meta de ahorro). Viaja en SyncAllData. */
    suspend fun saveProfile(user: User) {
        userDao.upsert(user.toEntity(isSynced = false))
        syncScheduler.requestSync()
    }

    /** Las escrituras de los demas repositorios son siempre del usuario de la sesion. */
    internal suspend fun requireUserId(): String =
        userDao.get()?.userId ?: throw IllegalStateException("No hay usuario en la base local: falta iniciar sesion")
}
