package com.binc.gastapp.data.local

import com.binc.gastapp.domain.model.Category
import com.binc.gastapp.domain.model.CreditCard
import com.binc.gastapp.domain.model.IncomeType
import com.binc.gastapp.domain.model.Spending
import com.binc.gastapp.domain.model.Subscription
import com.binc.gastapp.domain.model.User
import com.binc.gastapp.domain.money.centsToMoney
import com.binc.gastapp.domain.money.toCents
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

// Entidades de Room <-> modelos de :domain. Aqui se pasa de centavos (Long) a BigDecimal
// con escala 2 y de vuelta, y de Instant a hora local en lo poco que el dominio maneja
// como hora local (LastChargeRegisteredAt).
//
// Las banderas de sincronizacion (isSynced, deletedAt) no existen en el dominio: las
// ponen los repositorios al escribir.

fun IncomeTypeEntity.toDomain() = IncomeType(incomeTypeId = incomeTypeId, incomeTypeName = incomeTypeName)

fun IncomeType.toEntity() = IncomeTypeEntity(incomeTypeId = incomeTypeId, incomeTypeName = incomeTypeName)

fun UserEntity.toDomain() = User(
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

fun User.toEntity(isSynced: Boolean) = UserEntity(
    userId = userId,
    name = name,
    email = email,
    salaryCents = salary.toCents(),
    percentSave = percentSave,
    birthDate = birthDate,
    incomeTypeId = incomeTypeId,
    firstPayDay = firstPayDay,
    secondPayDay = secondPayDay,
    weekPayDay = weekPayDay,
    isSynced = isSynced,
)

fun CategoryEntity.toDomain() = Category(
    categoryId = categoryId,
    categoryName = categoryName,
    isDefaultCategory = isDefaultCategory,
)

fun SpendingEntity.toDomain() = Spending(
    spendingId = spendingId,
    amount = amountCents.centsToMoney(),
    date = date,
    userId = userId,
    categoryId = categoryId,
    title = title,
    description = description,
    isCreditCard = isCreditCard,
    creditCardId = creditCardId,
    paymentMethod = paymentMethod,
    isMsi = isMsi,
    totalInstallments = totalInstallments,
    currentInstallment = currentInstallment,
    parentSpendingId = parentSpendingId,
    installmentMonthlyAmount = installmentMonthlyAmountCents.centsToMoney(),
    isDeleted = isDeleted,
)

fun Spending.toEntity(isSynced: Boolean, deletedAt: Instant?) = SpendingEntity(
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
    isSynced = isSynced,
    isDeleted = isDeleted,
    deletedAt = deletedAt,
)

fun CreditCardEntity.toDomain() = CreditCard(
    creditCardId = creditCardId,
    cutOffDay = cutOffDay,
    paymentDay = paymentDay,
    userId = userId,
    cardName = cardName,
    bankName = bankName,
    lastFourDigits = lastFourDigits,
    creditLimit = creditLimitCents.centsToMoney(),
    colorHex = colorHex,
)

fun CreditCard.toEntity(isSynced: Boolean) = CreditCardEntity(
    creditCardId = creditCardId,
    userId = userId,
    cardName = cardName,
    bankName = bankName,
    lastFourDigits = lastFourDigits,
    cutOffDay = cutOffDay,
    paymentDay = paymentDay,
    creditLimitCents = creditLimit.toCents(),
    colorHex = colorHex,
    isSynced = isSynced,
)

fun SubscriptionEntity.toDomain(zone: ZoneId = ZoneId.systemDefault()) = Subscription(
    subscriptionId = subscriptionId,
    serviceName = serviceName,
    amount = amountCents.centsToMoney(),
    firstChargeDate = firstChargeDate,
    billingCycle = billingCycle,
    userId = userId,
    planName = planName,
    paymentMethod = paymentMethod,
    creditCardId = creditCardId,
    categoryId = categoryId,
    isActive = isActive,
    isTrial = isTrial,
    trialEndDate = trialEndDate,
    colorHex = colorHex,
    notes = notes,
    lastChargeRegisteredAt = lastChargeRegisteredAt?.let { LocalDateTime.ofInstant(it, zone) },
    isDeleted = isDeleted,
)

fun Subscription.toEntity(isSynced: Boolean, deletedAt: Instant?, zone: ZoneId = ZoneId.systemDefault()) = SubscriptionEntity(
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
    lastChargeRegisteredAt = lastChargeRegisteredAt?.atZone(zone)?.toInstant(),
    isSynced = isSynced,
    isDeleted = isDeleted,
    deletedAt = deletedAt,
)
