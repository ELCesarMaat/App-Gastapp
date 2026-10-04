package com.binc.gastapp.backup

import com.binc.gastapp.data.local.CategoryEntity
import com.binc.gastapp.data.local.CreditCardEntity
import com.binc.gastapp.data.local.IncomeTypeEntity
import com.binc.gastapp.data.local.SpendingEntity
import com.binc.gastapp.data.local.SubscriptionEntity
import com.binc.gastapp.data.local.UserEntity
import com.binc.gastapp.domain.money.centsToMoney
import com.binc.gastapp.domain.money.toCents
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

// Formato del respaldo JSON de la app nativa. Es propio y versionado: no es el de MAUI
// (aquel no traia suscripciones y guardaba las entidades de EF tal cual). Si algun dia
// cambia, se sube BACKUP_VERSION y se sigue sabiendo leer los anteriores.
//
// Tipos, pensados para que el archivo se pueda leer a ojo y no pierda nada:
//  - Dinero: texto con dos decimales ("1234.50"), exacto.
//  - Fecha de un gasto: hora local sin zona ("2026-10-03T14:05:00"), como en la base.
//  - Fechas de calendario (cumpleanos, primer cobro, fin de prueba): "2026-10-03".
//  - Instantes (borrado, ultimo cobro registrado): UTC con Z.

const val BACKUP_FORMAT = "gastapp-backup"
const val BACKUP_VERSION = 1

val BackupJson: Json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

@Serializable
data class BackupFile(
    val format: String = BACKUP_FORMAT,
    val version: Int = BACKUP_VERSION,
    @Serializable(with = InstantText::class) val exportedAt: Instant,
    val appVersion: String,
    val user: BackupUser,
    val incomeTypes: List<BackupIncomeType> = emptyList(),
    val categories: List<BackupCategory> = emptyList(),
    val creditCards: List<BackupCreditCard> = emptyList(),
    val subscriptions: List<BackupSubscription> = emptyList(),
    val spendings: List<BackupSpending> = emptyList(),
)

@Serializable
data class BackupUser(
    val userId: String,
    val name: String,
    val email: String? = null,
    @Serializable(with = MoneyText::class) val salary: BigDecimal,
    @Serializable(with = DecimalText::class) val percentSave: BigDecimal,
    @Serializable(with = LocalDateText::class) val birthDate: LocalDate,
    val incomeTypeId: Int,
    val firstPayDay: Int? = null,
    val secondPayDay: Int? = null,
    val weekPayDay: Int? = null,
)

@Serializable
data class BackupIncomeType(val incomeTypeId: Int, val incomeTypeName: String)

@Serializable
data class BackupCategory(
    val categoryId: String,
    val categoryName: String,
    val isDefaultCategory: Boolean = false,
)

/**
 * Las tarjetas borradas tambien viajan si algun gasto o suscripcion las sigue
 * referenciando: sin ellas, esas compras perderian su tarjeta al restaurar.
 */
@Serializable
data class BackupCreditCard(
    val creditCardId: String,
    val cardName: String,
    val bankName: String,
    val lastFourDigits: String? = null,
    val cutOffDay: Int,
    val paymentDay: Int,
    @Serializable(with = MoneyText::class) val creditLimit: BigDecimal = BigDecimal.ZERO,
    val colorHex: String = "#126E63",
    val isDeleted: Boolean = false,
    @Serializable(with = InstantText::class) val deletedAt: Instant? = null,
)

@Serializable
data class BackupSubscription(
    val subscriptionId: String,
    val serviceName: String,
    val planName: String? = null,
    @Serializable(with = MoneyText::class) val amount: BigDecimal,
    val billingCycle: String,
    @Serializable(with = LocalDateText::class) val firstChargeDate: LocalDate,
    val paymentMethod: String,
    val creditCardId: String? = null,
    val categoryId: String? = null,
    val isActive: Boolean = true,
    val isTrial: Boolean = false,
    @Serializable(with = LocalDateText::class) val trialEndDate: LocalDate? = null,
    val colorHex: String = "#7C3AED",
    val notes: String? = null,
    @Serializable(with = InstantText::class) val lastChargeRegisteredAt: Instant? = null,
)

@Serializable
data class BackupSpending(
    val spendingId: String,
    val categoryId: String,
    val title: String,
    val description: String? = null,
    @Serializable(with = MoneyText::class) val amount: BigDecimal,
    @Serializable(with = LocalDateTimeText::class) val date: LocalDateTime,
    val isCreditCard: Boolean = false,
    val creditCardId: String? = null,
    val paymentMethod: String,
    val isMsi: Boolean = false,
    val totalInstallments: Int = 1,
    val currentInstallment: Int = 1,
    val parentSpendingId: String? = null,
    @Serializable(with = MoneyText::class) val installmentMonthlyAmount: BigDecimal = BigDecimal.ZERO,
)

// ------------------------------------------------------------------ base -> respaldo

fun UserEntity.toBackup() = BackupUser(
    userId = userId,
    name = name,
    email = email,
    salary = salaryCents.centsToMoney(),
    percentSave = percentSave,
    birthDate = birthDate,
    incomeTypeId = incomeTypeId,
    firstPayDay = firstPayDay,
    secondPayDay = secondPayDay,
    weekPayDay = weekPayDay,
)

fun IncomeTypeEntity.toBackup() = BackupIncomeType(incomeTypeId, incomeTypeName)

fun CategoryEntity.toBackup() = BackupCategory(categoryId, categoryName, isDefaultCategory)

fun CreditCardEntity.toBackup() = BackupCreditCard(
    creditCardId = creditCardId,
    cardName = cardName,
    bankName = bankName,
    lastFourDigits = lastFourDigits,
    cutOffDay = cutOffDay,
    paymentDay = paymentDay,
    creditLimit = creditLimitCents.centsToMoney(),
    colorHex = colorHex,
    isDeleted = isDeleted,
    deletedAt = deletedAt,
)

fun SubscriptionEntity.toBackup() = BackupSubscription(
    subscriptionId = subscriptionId,
    serviceName = serviceName,
    planName = planName,
    amount = amountCents.centsToMoney(),
    billingCycle = billingCycle,
    firstChargeDate = firstChargeDate,
    paymentMethod = paymentMethod,
    creditCardId = creditCardId,
    categoryId = categoryId,
    isActive = isActive,
    isTrial = isTrial,
    trialEndDate = trialEndDate,
    colorHex = colorHex,
    notes = notes,
    lastChargeRegisteredAt = lastChargeRegisteredAt,
)

fun SpendingEntity.toBackup() = BackupSpending(
    spendingId = spendingId,
    categoryId = categoryId,
    title = title,
    description = description,
    amount = amountCents.centsToMoney(),
    date = date,
    isCreditCard = isCreditCard,
    creditCardId = creditCardId,
    paymentMethod = paymentMethod,
    isMsi = isMsi,
    totalInstallments = totalInstallments,
    currentInstallment = currentInstallment,
    parentSpendingId = parentSpendingId,
    installmentMonthlyAmount = installmentMonthlyAmountCents.centsToMoney(),
)

// ------------------------------------------------------------------ respaldo -> base
// Todo vuelve como pendiente de subir (isSynced = false): restaurar tambien deja la nube
// como estaba en el respaldo.

fun BackupIncomeType.toEntity() = IncomeTypeEntity(incomeTypeId, incomeTypeName)

fun BackupCategory.toEntity(userId: String) = CategoryEntity(
    categoryId = categoryId,
    userId = userId,
    categoryName = categoryName,
    isDefaultCategory = isDefaultCategory,
    isSynced = false,
    isDeleted = false,
)

fun BackupCreditCard.toEntity(userId: String) = CreditCardEntity(
    creditCardId = creditCardId,
    userId = userId,
    cardName = cardName,
    bankName = bankName,
    lastFourDigits = lastFourDigits,
    cutOffDay = cutOffDay,
    paymentDay = paymentDay,
    creditLimitCents = creditLimit.toCents(),
    colorHex = colorHex,
    isSynced = false,
    isDeleted = isDeleted,
    deletedAt = if (isDeleted) deletedAt else null,
)

fun BackupSubscription.toEntity(userId: String, creditCardId: String?, categoryId: String?) = SubscriptionEntity(
    subscriptionId = subscriptionId,
    userId = userId,
    serviceName = serviceName,
    planName = planName,
    amountCents = amount.toCents(),
    billingCycle = billingCycle,
    firstChargeDate = firstChargeDate,
    paymentMethod = paymentMethod,
    creditCardId = creditCardId,
    categoryId = categoryId,
    isActive = isActive,
    isTrial = isTrial,
    trialEndDate = trialEndDate,
    colorHex = colorHex,
    notes = notes,
    lastChargeRegisteredAt = lastChargeRegisteredAt,
    isSynced = false,
    isDeleted = false,
    deletedAt = null,
)

fun BackupSpending.toEntity(userId: String, categoryId: String, creditCardId: String?) = SpendingEntity(
    spendingId = spendingId,
    userId = userId,
    categoryId = categoryId,
    title = title,
    description = description,
    amountCents = amount.toCents(),
    date = date,
    isCreditCard = isCreditCard,
    creditCardId = creditCardId,
    paymentMethod = paymentMethod,
    isMsi = isMsi,
    totalInstallments = totalInstallments,
    currentInstallment = currentInstallment,
    parentSpendingId = parentSpendingId,
    installmentMonthlyAmountCents = installmentMonthlyAmount.toCents(),
    isSynced = false,
    isDeleted = false,
    deletedAt = null,
)

// ------------------------------------------------------------------ serializadores

private abstract class TextSerializer<T : Any>(name: String) : KSerializer<T> {
    override val descriptor = PrimitiveSerialDescriptor("com.binc.gastapp.backup.$name", PrimitiveKind.STRING)
    abstract fun format(value: T): String
    abstract fun parse(text: String): T
    override fun serialize(encoder: Encoder, value: T) = encoder.encodeString(format(value))
    override fun deserialize(decoder: Decoder): T = parse(decoder.decodeString())
}

private object MoneyText : TextSerializer<BigDecimal>("Money") {
    override fun format(value: BigDecimal): String = value.setScale(2, java.math.RoundingMode.HALF_EVEN).toPlainString()
    override fun parse(text: String): BigDecimal = BigDecimal(text.trim())
}

private object DecimalText : TextSerializer<BigDecimal>("Decimal") {
    override fun format(value: BigDecimal): String = value.toPlainString()
    override fun parse(text: String): BigDecimal = BigDecimal(text.trim())
}

private object LocalDateText : TextSerializer<LocalDate>("LocalDate") {
    override fun format(value: LocalDate): String = value.toString()
    override fun parse(text: String): LocalDate = LocalDate.parse(text.trim())
}

private object LocalDateTimeText : TextSerializer<LocalDateTime>("LocalDateTime") {
    override fun format(value: LocalDateTime): String = value.toString()
    override fun parse(text: String): LocalDateTime = LocalDateTime.parse(text.trim())
}

private object InstantText : TextSerializer<Instant>("Instant") {
    override fun format(value: Instant): String = value.toString()
    override fun parse(text: String): Instant = Instant.parse(text.trim())
}
