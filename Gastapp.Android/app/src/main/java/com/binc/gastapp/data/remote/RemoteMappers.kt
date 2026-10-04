package com.binc.gastapp.data.remote

import com.binc.gastapp.core.remote.CategoryDto
import com.binc.gastapp.core.remote.CreditCardDto
import com.binc.gastapp.core.remote.IncomeTypeDto
import com.binc.gastapp.core.remote.SpendingDto
import com.binc.gastapp.core.remote.SubscriptionDto
import com.binc.gastapp.core.remote.UserDto
import com.binc.gastapp.core.remote.UserInfoDto
import com.binc.gastapp.data.local.CategoryEntity
import com.binc.gastapp.data.local.CreditCardEntity
import com.binc.gastapp.data.local.IncomeTypeEntity
import com.binc.gastapp.data.local.SpendingEntity
import com.binc.gastapp.data.local.SubscriptionEntity
import com.binc.gastapp.data.local.UserEntity
import com.binc.gastapp.domain.money.centsToMoney
import com.binc.gastapp.domain.money.toCents
import com.binc.gastapp.domain.time.toApiInstant
import com.binc.gastapp.domain.time.toLocalDateTime
import java.time.ZoneId

// Entidades de Room <-> DTO del API. Aqui se aplican las reglas de fechas del anexo C:
//  - La fecha de un gasto es hora local en el telefono e instante UTC en el API
//    (SpendingToApiUtc / SpendingFromApiToLocal de MAUI, en domain/time).
//  - FirstChargeDate, TrialEndDate y BirthDate son LocalDate en los dos lados: no se
//    convierten de zona.
//  - DeletedAt y LastChargeRegisteredAt son Instant en los dos lados.
//
// Al bajar, todo llega como sincronizado: es la copia del servidor. Las referencias a
// tarjetas y categorias se validan aparte (ver LocalDataWriter): una colgante truena la
// llave foranea.

// ------------------------------------------------------------------ Del API a Room

fun IncomeTypeDto.toEntity() = IncomeTypeEntity(incomeTypeId = incomeTypeId, incomeTypeName = incomeTypeName)

fun UserDto.toEntity() = UserEntity(
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
    isSynced = true,
)

fun CategoryDto.toEntity(userId: String) = CategoryEntity(
    categoryId = categoryId,
    userId = userId,
    categoryName = categoryName,
    isDefaultCategory = isDefaultCategory,
    isSynced = true,
    isDeleted = false,
)

fun CreditCardDto.toEntity(userId: String) = CreditCardEntity(
    creditCardId = creditCardId,
    userId = userId,
    cardName = cardName,
    bankName = bankName,
    lastFourDigits = lastFourDigits,
    cutOffDay = cutOffDay,
    paymentDay = paymentDay,
    creditLimitCents = creditLimit.toCents(),
    colorHex = colorHex,
    isSynced = true,
    isDeleted = isDeleted,
    deletedAt = deletedAt,
)

/** [creditCardId] y [categoryId] ya validados contra lo que existe localmente. */
fun SpendingDto.toEntity(userId: String, categoryId: String, creditCardId: String?, zone: ZoneId) = SpendingEntity(
    spendingId = spendingId,
    userId = userId,
    categoryId = categoryId,
    title = title,
    description = description,
    amountCents = amount.toCents(),
    date = date.toLocalDateTime(zone),
    isCreditCard = isCreditCard,
    creditCardId = creditCardId,
    paymentMethod = paymentMethod,
    isMsi = isMsi,
    totalInstallments = totalInstallments,
    currentInstallment = currentInstallment,
    parentSpendingId = parentSpendingId,
    installmentMonthlyAmountCents = installmentMonthlyAmount.toCents(),
    isSynced = true,
    isDeleted = isDeleted,
    deletedAt = deletedAt,
)

/** [creditCardId] y [categoryId] ya validados contra lo que existe localmente. */
fun SubscriptionDto.toEntity(userId: String, creditCardId: String?, categoryId: String?) = SubscriptionEntity(
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
    isSynced = true,
    isDeleted = isDeleted,
    deletedAt = deletedAt,
)

// ------------------------------------------------------------------ De Room al API

fun UserEntity.toInfoDto() = UserInfoDto(
    userId = userId,
    salary = salaryCents.centsToMoney(),
    name = name,
    birthDate = birthDate,
    percentSave = percentSave,
    incomeTypeId = incomeTypeId,
    firstPayDay = firstPayDay,
    secondPayDay = secondPayDay,
    weekPayDay = weekPayDay,
    isSynced = isSynced,
)

fun CategoryEntity.toDto() = CategoryDto(
    categoryId = categoryId,
    userId = userId,
    categoryName = categoryName,
    isDefaultCategory = isDefaultCategory,
    isSynced = isSynced,
)

fun CreditCardEntity.toDto() = CreditCardDto(
    creditCardId = creditCardId,
    userId = userId,
    cardName = cardName,
    bankName = bankName,
    lastFourDigits = lastFourDigits,
    cutOffDay = cutOffDay,
    paymentDay = paymentDay,
    creditLimit = creditLimitCents.centsToMoney(),
    colorHex = colorHex,
    isSynced = isSynced,
    isDeleted = isDeleted,
    deletedAt = deletedAt,
)

fun SpendingEntity.toDto(zone: ZoneId) = SpendingDto(
    spendingId = spendingId,
    categoryId = categoryId,
    userId = userId,
    title = title,
    description = description,
    amount = amountCents.centsToMoney(),
    isSynced = isSynced,
    isDeleted = isDeleted,
    deletedAt = deletedAt,
    date = date.toApiInstant(zone),
    isCreditCard = isCreditCard,
    creditCardId = creditCardId,
    paymentMethod = paymentMethod,
    isMsi = isMsi,
    totalInstallments = totalInstallments,
    currentInstallment = currentInstallment,
    parentSpendingId = parentSpendingId,
    installmentMonthlyAmount = installmentMonthlyAmountCents.centsToMoney(),
)

fun SubscriptionEntity.toDto() = SubscriptionDto(
    subscriptionId = subscriptionId,
    userId = userId,
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
    isSynced = isSynced,
    isDeleted = isDeleted,
    deletedAt = deletedAt,
)
