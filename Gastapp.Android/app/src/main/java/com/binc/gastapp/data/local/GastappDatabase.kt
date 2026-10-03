package com.binc.gastapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Base local de la app nativa. Esquema nuevo: no se importa nada de MAUI (la app se
 * instala desde cero y los datos se bajan al iniciar sesion).
 *
 * Cada cambio de esquema sube [version] y lleva su Migration: el JSON de cada version
 * queda en app/schemas/ para poder probarlas.
 */
@Database(
    entities = [
        IncomeTypeEntity::class,
        UserEntity::class,
        CategoryEntity::class,
        CreditCardEntity::class,
        SpendingEntity::class,
        SubscriptionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class GastappDatabase : RoomDatabase() {
    abstract fun incomeTypeDao(): IncomeTypeDao
    abstract fun userDao(): UserDao
    abstract fun categoryDao(): CategoryDao
    abstract fun creditCardDao(): CreditCardDao
    abstract fun spendingDao(): SpendingDao
    abstract fun subscriptionDao(): SubscriptionDao

    companion object {
        /**
         * Distinto de gastapp.db (la base de MAUI) a proposito: si alguien instalara
         * encima de MAUI sin desinstalar, Room no intentaria abrir esa base y tronar.
         */
        const val NAME = "gastapp_room.db"
    }
}
