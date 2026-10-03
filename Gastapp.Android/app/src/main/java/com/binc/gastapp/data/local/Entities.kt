package com.binc.gastapp.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.binc.gastapp.domain.model.BillingCycles
import com.binc.gastapp.domain.model.PaymentMethods
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

// Tablas de la base local. Los campos son los de Gastapp.Models (MAUI) y las llaves
// foraneas las de OnModelCreating en Gastapp/Data/GastappDbContext.cs:
//  - Borrar un usuario borra todo lo suyo en cascada.
//  - Borrar una categoria arrastra sus gastos (pero CategoryRepository los pasa antes
//    a "Sin categoria", igual que MAUI y el API).
//  - Un gasto no deja borrar la tarjeta que referencia (NO ACTION, como MAUI): la purga
//    solo quita tarjetas que ya nadie usa.
//  - Borrar una tarjeta o una categoria NO borra la suscripcion: se anula la referencia.
//
// El dinero va en centavos (Long): las sumas en SQL quedan exactas. En dominio y UI es
// BigDecimal con escala 2 (ver Mappers.kt).
//
// Ojo con las referencias colgantes (regla 12 del anexo C): insertar un registro que
// apunta a una tarjeta o categoria que no existe localmente truena la llave foranea y
// revierte la transaccion completa. Quien inserte datos del servidor (Fase 3) tiene que
// anular esas referencias antes.

@Entity(
    tableName = "income_types",
)
data class IncomeTypeEntity(
    @PrimaryKey val incomeTypeId: Int,
    val incomeTypeName: String,
)

/** El usuario de la sesion. `passWordHash` y los datos de reset de contrasena no se guardan en el telefono. */
@Entity(
    tableName = "users",
    foreignKeys = [
        ForeignKey(
            entity = IncomeTypeEntity::class,
            parentColumns = ["incomeTypeId"],
            childColumns = ["incomeTypeId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("incomeTypeId")],
)
data class UserEntity(
    @PrimaryKey val userId: String,
    val name: String,
    val email: String?,
    val salaryCents: Long,
    /** Porcentaje de ahorro (15 = 15 %), hasta 4 decimales. No es dinero: va como texto exacto. */
    val percentSave: BigDecimal,
    /** Fecha de calendario: nunca pasa por conversion de zona. */
    val birthDate: LocalDate,
    val incomeTypeId: Int,
    /** En modo semanal es el dia de la semana con la convencion de .NET (0 = domingo). */
    val firstPayDay: Int?,
    val secondPayDay: Int?,
    /** Existe en el API pero no se usa: el dia semanal va en [firstPayDay] (regla 10). */
    val weekPayDay: Int?,
    val isSynced: Boolean = false,
)

@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["userId"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("userId")],
)
data class CategoryEntity(
    @PrimaryKey val categoryId: String,
    val userId: String,
    val categoryName: String,
    val isDefaultCategory: Boolean = false,
    val isSynced: Boolean = false,
    /**
     * Nuevo respecto a MAUI: alla borrar una categoria sin red se perdia, porque
     * DeleteCategory se llamaba en el momento y no se reintentaba. Aqui se marca y la
     * sincronizacion llama a DeleteCategory; al confirmarse, se borra de verdad.
     */
    val isDeleted: Boolean = false,
)

@Entity(
    tableName = "credit_cards",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["userId"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("userId")],
)
data class CreditCardEntity(
    @PrimaryKey val creditCardId: String,
    val userId: String,
    val cardName: String,
    val bankName: String,
    val lastFourDigits: String?,
    /** Solo el dia del mes (1-31). No hay fechas de corte ni de pago guardadas. */
    val cutOffDay: Int,
    val paymentDay: Int,
    val creditLimitCents: Long = 0,
    val colorHex: String = "#126E63",
    val isSynced: Boolean = false,
    val isDeleted: Boolean = false,
    val deletedAt: Instant? = null,
)

/**
 * Un gasto. Tambien los pagos a tarjeta: un abono es un gasto con [isCreditCard] = false
 * y [creditCardId] lleno (regla 3 del anexo C).
 */
@Entity(
    tableName = "spendings",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["userId"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["categoryId"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CreditCardEntity::class,
            parentColumns = ["creditCardId"],
            childColumns = ["creditCardId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [Index("userId"), Index("categoryId"), Index("creditCardId"), Index("date")],
)
data class SpendingEntity(
    @PrimaryKey val spendingId: String,
    val userId: String,
    val categoryId: String,
    val title: String,
    val description: String?,
    val amountCents: Long,
    /** Hora local del telefono. Al API viaja en UTC (regla 9). */
    val date: LocalDateTime,
    val isCreditCard: Boolean = false,
    val creditCardId: String? = null,
    val paymentMethod: String = PaymentMethods.CASH,
    val isMsi: Boolean = false,
    val totalInstallments: Int = 1,
    val currentInstallment: Int = 1,
    val parentSpendingId: String? = null,
    val installmentMonthlyAmountCents: Long = 0,
    val isSynced: Boolean = false,
    val isDeleted: Boolean = false,
    val deletedAt: Instant? = null,
)

/**
 * Suscripcion. [firstChargeDate] es el ancla de todos los cobros y, junto con
 * [trialEndDate], es fecha de calendario: sin zona horaria (regla 8).
 */
@Entity(
    tableName = "subscriptions",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["userId"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CreditCardEntity::class,
            parentColumns = ["creditCardId"],
            childColumns = ["creditCardId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["categoryId"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("userId"), Index("creditCardId"), Index("categoryId")],
)
data class SubscriptionEntity(
    @PrimaryKey val subscriptionId: String,
    val userId: String,
    val serviceName: String,
    val planName: String? = null,
    val amountCents: Long,
    val billingCycle: String = BillingCycles.MONTHLY,
    val firstChargeDate: LocalDate,
    val paymentMethod: String = PaymentMethods.CASH,
    val creditCardId: String? = null,
    val categoryId: String? = null,
    val isActive: Boolean = true,
    val isTrial: Boolean = false,
    val trialEndDate: LocalDate? = null,
    val colorHex: String = "#7C3AED",
    val notes: String? = null,
    /** Instante: viaja en UTC (regla 9). */
    val lastChargeRegisteredAt: Instant? = null,
    val isSynced: Boolean = false,
    val isDeleted: Boolean = false,
    val deletedAt: Instant? = null,
)
