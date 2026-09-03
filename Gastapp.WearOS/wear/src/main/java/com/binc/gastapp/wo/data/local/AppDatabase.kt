package com.binc.gastapp.wo.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ExpenseEntity::class,
        CategoryEntity::class,
        SummaryEntity::class,
        DaySpendingEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao
    abstract fun categoryDao(): CategoryDao
    abstract fun summaryDao(): SummaryDao
    abstract fun daySpendingDao(): DaySpendingDao

    companion object {
        @Volatile
        private var instancia: AppDatabase? = null

        /**
         * Solo agrega la cache de gastos del dia. Se escribe a mano en vez de dejar
         * que Room recree la base, porque un borron se llevaria los gastos pendientes
         * de subir, que son el unico dato que no se puede perder.
         */
        private val MIGRACION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `day_spendings` (" +
                        "`spendingId` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`categoryName` TEXT, " +
                        "`amount` REAL NOT NULL, " +
                        "`occurredAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`spendingId`))"
                )
            }
        }

        fun get(context: Context): AppDatabase =
            instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gastapp-wear.db"
                )
                    .addMigrations(MIGRACION_1_2)
                    // Red de seguridad para saltos de version sin migracion escrita.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instancia = it }
            }
    }
}
