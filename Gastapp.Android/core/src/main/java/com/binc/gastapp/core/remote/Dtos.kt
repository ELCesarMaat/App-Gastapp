package com.binc.gastapp.core.remote

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.Serializable

// Contratos del API: los mismos nombres que Gastapp.Models (C#), en el camelCase con el
// que los escribe ASP.NET Core. Solo estan los que usa la app nativa (anexo B del plan).
//
// Los campos que el API puede omitir o mandar en null llevan valor por defecto. Lo que
// sobra en las respuestas (navegaciones de EF, hashes...) se ignora.
//
// Tipos (ver Serializers.kt):
//  - Dinero: BigDecimal (BigDecimalSerializer), nunca Double.
//  - Instantes: Instant en UTC (ApiInstantSerializer).
//  - Fechas de calendario: LocalDate sin zona (CalendarDateSerializer).

// ------------------------------------------------------------------ Sesion

/** LoginModel / AuthenticateRequest. */
@Serializable
data class LoginRequest(val email: String, val password: String)

/** AllUserData: todo lo del usuario, lo que devuelve el login. */
@Serializable
data class AllUserDataDto(
    val user: UserDto,
    val categories: List<CategoryDto> = emptyList(),
    val spendings: List<SpendingDto> = emptyList(),
    val creditCards: List<CreditCardDto> = emptyList(),
    val subscriptions: List<SubscriptionDto> = emptyList(),
    val incomes: List<IncomeTypeDto> = emptyList(),
    val token: String,
    @Serializable(with = ApiInstantSerializer::class)
    val tokenExpiration: Instant? = null,
)

/**
 * User tal como sale del login. El API manda la entidad completa (con incomeType,
 * colecciones vacias y los campos del reset de contrasena): aqui solo se lee lo que el
 * telefono guarda.
 */
@Serializable
data class UserDto(
    val userId: String,
    @Serializable(with = BigDecimalSerializer::class)
    val salary: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val percentSave: BigDecimal = BigDecimal.ZERO,
    val name: String = "",
    val email: String? = null,
    /** Columna timestamptz, pero es fecha de calendario: se toma solo el dia. */
    @Serializable(with = CalendarDateSerializer::class)
    val birthDate: LocalDate,
    val incomeTypeId: Int,
    val firstPayDay: Int? = null,
    val secondPayDay: Int? = null,
    val weekPayDay: Int? = null,
    val isSynced: Boolean = false,
)

@Serializable
data class IncomeTypeDto(val incomeTypeId: Int, val incomeTypeName: String)

/** Token: respuesta de RefreshToken. */
@Serializable
data class TokenDto(
    val tokenValue: String,
    @Serializable(with = ApiInstantSerializer::class)
    val tokenExpiration: Instant? = null,
)

/** CreateUserModel. La contrasena solo viaja aqui y en el login. */
@Serializable
data class CreateUserRequest(
    val userId: String,
    @Serializable(with = BigDecimalSerializer::class)
    val salary: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val percentSave: BigDecimal,
    val name: String,
    val email: String,
    val password: String,
    /** Con Z: el API la guarda en timestamptz sin normalizar (ver CalendarDateUtcSerializer). */
    @Serializable(with = CalendarDateUtcSerializer::class)
    val birthDate: LocalDate,
    val incomeTypeId: Int,
    val firstPayDay: Int? = null,
    val secondPayDay: Int? = null,
    val weekPayDay: Int? = null,
)

@Serializable
data class CreateUserResponse(
    val userId: String,
    val token: String,
    @Serializable(with = ApiInstantSerializer::class)
    val tokenExpiration: Instant? = null,
)

/** PasswordReset/temporary responde { message }. */
@Serializable
data class TemporaryPasswordResponse(val message: String = "")

// ------------------------------------------------------------------ Datos

@Serializable
data class CategoryDto(
    val categoryId: String,
    val userId: String,
    val categoryName: String,
    val isDefaultCategory: Boolean = false,
    val isSynced: Boolean = false,
)

@Serializable
data class SpendingDto(
    val spendingId: String,
    val categoryId: String,
    val userId: String,
    val title: String = "",
    val description: String? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val amount: BigDecimal,
    val isSynced: Boolean = false,
    val isDeleted: Boolean = false,
    @Serializable(with = ApiInstantSerializer::class)
    val deletedAt: Instant? = null,
    /** Instante en UTC. En el telefono es hora local (SpendingToApiUtc / SpendingFromApiToLocal). */
    @Serializable(with = ApiInstantSerializer::class)
    val date: Instant,
    val isCreditCard: Boolean = false,
    val creditCardId: String? = null,
    val paymentMethod: String = "Cash",
    val isMsi: Boolean = false,
    val totalInstallments: Int = 1,
    val currentInstallment: Int = 1,
    val parentSpendingId: String? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val installmentMonthlyAmount: BigDecimal = BigDecimal.ZERO,
)

@Serializable
data class CreditCardDto(
    val creditCardId: String,
    val userId: String,
    val cardName: String,
    val bankName: String = "",
    val lastFourDigits: String? = null,
    val cutOffDay: Int,
    val paymentDay: Int,
    @Serializable(with = BigDecimalSerializer::class)
    val creditLimit: BigDecimal = BigDecimal.ZERO,
    val colorHex: String = "#126E63",
    val isSynced: Boolean = false,
    val isDeleted: Boolean = false,
    @Serializable(with = ApiInstantSerializer::class)
    val deletedAt: Instant? = null,
)

/**
 * SubscriptionDto. Ojo: [firstChargeDate] y [trialEndDate] son fechas de calendario y
 * viajan tal cual; [lastChargeRegisteredAt] y [deletedAt] son instantes en UTC.
 */
@Serializable
data class SubscriptionDto(
    val subscriptionId: String,
    val userId: String,
    val serviceName: String,
    val planName: String? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val amount: BigDecimal,
    val billingCycle: String = "Monthly",
    @Serializable(with = CalendarDateSerializer::class)
    val firstChargeDate: LocalDate,
    val paymentMethod: String = "Cash",
    val creditCardId: String? = null,
    val categoryId: String? = null,
    val isActive: Boolean = true,
    val isTrial: Boolean = false,
    @Serializable(with = CalendarDateSerializer::class)
    val trialEndDate: LocalDate? = null,
    val colorHex: String = "#7C3AED",
    val notes: String? = null,
    @Serializable(with = ApiInstantSerializer::class)
    val lastChargeRegisteredAt: Instant? = null,
    val isSynced: Boolean = false,
    val isDeleted: Boolean = false,
    @Serializable(with = ApiInstantSerializer::class)
    val deletedAt: Instant? = null,
)

/** UserInfoDto: el perfil que sube SyncAllData. */
@Serializable
data class UserInfoDto(
    val userId: String,
    @Serializable(with = BigDecimalSerializer::class)
    val salary: BigDecimal,
    val name: String,
    /** Sin zona: SyncAllData la marca como UTC tal cual (SpecifyKind), sin moverla. */
    @Serializable(with = CalendarDateSerializer::class)
    val birthDate: LocalDate,
    @Serializable(with = BigDecimalSerializer::class)
    val percentSave: BigDecimal,
    val incomeTypeId: Int,
    val firstPayDay: Int? = null,
    val secondPayDay: Int? = null,
    val weekPayDay: Int? = null,
    val isSynced: Boolean = false,
)

/**
 * SyncDataDto: todo lo pendiente en un solo envio. El API hace upsert de cada cosa con
 * isSynced = false y aplica isDeleted/deletedAt en gastos, tarjetas y suscripciones (las
 * categorias borradas van aparte, con DeleteCategory).
 */
@Serializable
data class SyncDataDto(
    val user: UserInfoDto? = null,
    val categories: List<CategoryDto> = emptyList(),
    val spendings: List<SpendingDto> = emptyList(),
    val creditCards: List<CreditCardDto> = emptyList(),
    val subscriptions: List<SubscriptionDto> = emptyList(),
) {
    val isEmpty: Boolean
        get() = user == null && categories.isEmpty() && spendings.isEmpty() && creditCards.isEmpty() && subscriptions.isEmpty()
}

// ------------------------------------------------------------------ Dispositivos y version

@Serializable
data class LinkDeviceRequest(val userCode: String)

@Serializable
data class LinkDeviceResponse(val deviceName: String, val platform: String)

@Serializable
data class RevokeDeviceRequest(val deviceId: String)

@Serializable
data class DeviceDto(
    val deviceId: String,
    val name: String,
    val platform: String,
    @Serializable(with = ApiInstantSerializer::class)
    val createdAt: Instant,
    @Serializable(with = ApiInstantSerializer::class)
    val lastSeenAt: Instant? = null,
)

@Serializable
data class AppLatestVersionDto(
    val versionCode: Int,
    val versionName: String = "",
    val apkUrl: String = "",
    val releaseNotes: String = "",
    @Serializable(with = ApiInstantSerializer::class)
    val publishedAt: Instant? = null,
)
