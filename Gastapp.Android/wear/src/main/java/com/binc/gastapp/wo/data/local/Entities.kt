package com.binc.gastapp.wo.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class ExpenseEntity(
    /**
     * UUID generado en el reloj. Es la clave de idempotencia del servidor:
     * NUNCA se regenera al reintentar, o se duplicaria el gasto.
     */
    @PrimaryKey val id: String,
    val amount: Double,
    val title: String,
    val categoryId: String?,
    /** Epoch millis en UTC. */
    val occurredAt: Long,
    val rawInput: String,
    /** El parser no encontro monto: el servidor marcara el titulo como "Revisar: ...". */
    val needsReview: Boolean = false,
    val synced: Boolean = false,
    val syncAttempts: Int = 0,
    /** Rechazado de forma permanente por el servidor. Se muestra al usuario. */
    val failed: Boolean = false
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val categoryId: String,
    val categoryName: String,
    val isDefaultCategory: Boolean,
    /** Posicion que devolvio el servidor; conserva su orden estable. */
    val position: Int
)

/**
 * Copia local de los gastos del dia que devuelve el servidor (los del reloj y los
 * del telefono). Es cache pura: se vacia y se vuelve a llenar en cada refresco.
 * Existe para que la lista aparezca al instante y siga viendose sin red.
 */
@Entity(tableName = "day_spendings")
data class DaySpendingEntity(
    @PrimaryKey val spendingId: String,
    val title: String,
    val categoryName: String?,
    val amount: Double,
    /** Epoch millis en UTC. */
    val occurredAt: Long
)

/**
 * Cache del resumen para el tile. Una sola fila (id = 0): el tile jamas debe
 * hacer red, solo leer de aqui.
 */
@Entity(tableName = "summary")
data class SummaryEntity(
    @PrimaryKey val id: Int = 0,
    val total: Double,
    val count: Int,
    val updatedAt: Long
)
