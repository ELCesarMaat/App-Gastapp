package com.binc.gastapp.core.remote

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lee un fixture de src/test/resources/contratos/. */
internal fun fixture(name: String): String =
    requireNotNull(ContractTest::class.java.classLoader!!.getResource("contratos/$name")) { "Falta el fixture $name" }
        .readText()

/**
 * Pruebas de contrato contra los fixtures que genera tools/Gastapp.Contratos con los
 * tipos reales de Gastapp.Models y la configuracion JSON de ASP.NET Core. Si el API
 * cambia un DTO, se regeneran los fixtures y estas pruebas dicen que se rompio.
 */
class ContractTest {

    private val tokenExpiration = Instant.parse("2026-11-01T18:30:00.123456Z")

    @Test
    fun `login trae todo lo del usuario`() {
        val data = ApiJson.decodeFromString<AllUserDataDto>(fixture("login.json"))

        assertEquals("eyJ.token.login", data.token)
        // Sale como DateTime Local (con desfase): es el mismo instante.
        assertEquals(tokenExpiration, data.tokenExpiration)

        with(data.user) {
            assertEquals("8d3c0f2e-1a5b-4c7d-9e0f-123456789abc", userId)
            assertEquals(BigDecimal("12500.00"), salary)
            assertEquals(BigDecimal("15.5"), percentSave)
            assertEquals(LocalDate.of(1995, 6, 15), birthDate)
            assertEquals(2, incomeTypeId)
            assertEquals(1, firstPayDay)
            assertEquals(16, secondPayDay)
            assertNull(weekPayDay)
        }

        assertEquals(listOf("cat-default", "cat-comida"), data.categories.map { it.categoryId })
        assertTrue(data.categories.first().isDefaultCategory)
        assertEquals(3, data.incomes.size)

        val tacos = data.spendings.single { it.spendingId == "sp-tacos" }
        assertEquals(BigDecimal("123.45"), tacos.amount)
        assertEquals(Instant.parse("2026-10-02T20:15:33.123456Z"), tacos.date)

        val msi = data.spendings.single { it.spendingId == "sp-msi" }
        assertTrue(msi.isMsi)
        assertEquals(3, msi.totalInstallments)
        assertEquals(BigDecimal("1000.00"), msi.installmentMonthlyAmount)
        assertEquals("card-oro", msi.creditCardId)

        val deleted = data.spendings.single { it.spendingId == "sp-borrado" }
        assertTrue(deleted.isDeleted)
        assertEquals(Instant.parse("2026-10-01T12:00:00.5Z"), deleted.deletedAt)
        assertEquals(BigDecimal("0.10"), deleted.amount)

        val oldCard = data.creditCards.single { it.creditCardId == "card-vieja" }
        assertTrue(oldCard.isDeleted)
        assertNull(oldCard.lastFourDigits)

        val netflix = data.subscriptions.single { it.subscriptionId == "sub-netflix" }
        assertEquals(LocalDate.of(2026, 1, 31), netflix.firstChargeDate)
        assertNull(netflix.trialEndDate)
        assertEquals(Instant.parse("2026-09-30T18:00:00Z"), netflix.lastChargeRegisteredAt)

        val trial = data.subscriptions.single { it.subscriptionId == "sub-prueba" }
        assertEquals(LocalDate.of(2026, 2, 28), trial.firstChargeDate)
        assertEquals(LocalDate.of(2026, 10, 15), trial.trialEndDate)
        assertEquals("Yearly", trial.billingCycle)
        assertEquals(BigDecimal("49.90"), trial.amount)
    }

    @Test
    fun `refresh token y alta de usuario`() {
        val token = ApiJson.decodeFromString<TokenDto>(fixture("refresh_token.json"))
        assertEquals("eyJ.token.nuevo", token.tokenValue)
        assertEquals(tokenExpiration, token.tokenExpiration)

        val created = ApiJson.decodeFromString<CreateUserResponse>(fixture("create_user_response.json"))
        assertEquals("eyJ.token.registro", created.token)
        assertEquals(tokenExpiration, created.tokenExpiration)
    }

    @Test
    fun `gastos del servidor, incluido uno del reloj`() {
        val spendings = ApiJson.decodeFromString<List<SpendingDto>>(fixture("get_spendings.json"))
        val watch = spendings.single { it.spendingId == "sp-reloj" }
        assertEquals(BigDecimal("45"), watch.amount)
        assertEquals(Instant.parse("2026-10-02T16:05:00Z"), watch.date)
        assertTrue(watch.isSynced)
        assertFalse(watch.isCreditCard)
    }

    @Test
    fun `version, dispositivos y contrasena temporal`() {
        val version = ApiJson.decodeFromString<AppLatestVersionDto>(fixture("latest_version.json"))
        assertEquals(200, version.versionCode)
        assertEquals("2.0.0", version.versionName)
        assertEquals(Instant.parse("2026-10-20T17:00:00Z"), version.publishedAt)

        val devices = ApiJson.decodeFromString<List<DeviceDto>>(fixture("devices.json"))
        assertEquals(Instant.parse("2026-10-02T09:00:00.25Z"), devices[0].lastSeenAt)
        assertNull(devices[1].lastSeenAt)

        val temporary = ApiJson.decodeFromString<TemporaryPasswordResponse>(fixture("temporary_password.json"))
        assertTrue(temporary.message.startsWith("Se ha enviado una contraseña"))
    }

    /**
     * Lo que manda la app. Los mismos archivos los lee `tools/Gastapp.Contratos verificar`
     * con System.Text.Json y comprueba montos exactos y el Kind de cada fecha: si esta
     * prueba pasa, C# los entiende.
     */
    @Test
    fun `SyncAllData sale con la forma que espera el API`() {
        val body = SyncDataDto(
            user = UserInfoDto(
                userId = "u-1",
                salary = BigDecimal("12500.00"),
                name = "Ana",
                birthDate = LocalDate.of(1995, 6, 15),
                percentSave = BigDecimal("15.5"),
                incomeTypeId = 2,
                firstPayDay = 1,
                secondPayDay = 16,
            ),
            categories = listOf(CategoryDto("cat-1", "u-1", "Comida")),
            spendings = listOf(
                SpendingDto(
                    spendingId = "sp-1",
                    categoryId = "cat-1",
                    userId = "u-1",
                    title = "Tacos",
                    amount = BigDecimal("123.45"),
                    date = Instant.parse("2026-10-02T20:15:00Z"),
                    isCreditCard = true,
                    creditCardId = "card-1",
                    paymentMethod = "CreditCard",
                    isMsi = true,
                    totalInstallments = 3,
                    installmentMonthlyAmount = BigDecimal("41.15"),
                ),
                SpendingDto(
                    spendingId = "sp-2",
                    categoryId = "cat-1",
                    userId = "u-1",
                    title = "Cafe",
                    description = "Sin azucar",
                    amount = BigDecimal("0.10"),
                    isDeleted = true,
                    deletedAt = Instant.parse("2026-10-01T12:00:00Z"),
                    date = Instant.parse("2026-09-30T14:00:00Z"),
                    installmentMonthlyAmount = BigDecimal("0.00"),
                ),
            ),
            creditCards = listOf(
                CreditCardDto(
                    creditCardId = "card-1",
                    userId = "u-1",
                    cardName = "Oro",
                    bankName = "Banco Uno",
                    lastFourDigits = "1234",
                    cutOffDay = 10,
                    paymentDay = 30,
                    creditLimit = BigDecimal("20000.00"),
                ),
            ),
            subscriptions = listOf(
                SubscriptionDto(
                    subscriptionId = "sub-1",
                    userId = "u-1",
                    serviceName = "Netflix",
                    planName = "Estandar",
                    amount = BigDecimal("219.00"),
                    firstChargeDate = LocalDate.of(2026, 1, 31),
                    paymentMethod = "CreditCard",
                    creditCardId = "card-1",
                    categoryId = "cat-1",
                    trialEndDate = LocalDate.of(2026, 10, 15),
                    colorHex = "#C62828",
                    lastChargeRegisteredAt = Instant.parse("2026-09-30T18:00:00Z"),
                ),
            ),
        )

        assertSameJson(fixture("sync_all_data_request.json"), ApiJson.encodeToString(SyncDataDto.serializer(), body))
    }

    @Test
    fun `CreateUser manda la fecha de nacimiento en UTC`() {
        val body = CreateUserRequest(
            userId = "u-1",
            salary = BigDecimal("12500.00"),
            percentSave = BigDecimal("15.5"),
            name = "Ana",
            email = "ana@gastapp.dev",
            password = "secreta-de-prueba",
            birthDate = LocalDate.of(1995, 6, 15),
            incomeTypeId = 2,
            firstPayDay = 1,
            secondPayDay = 16,
        )

        assertSameJson(fixture("create_user_request.json"), ApiJson.encodeToString(CreateUserRequest.serializer(), body))
    }

    /** Mismo arbol JSON (el orden de las llaves no importa; los numeros, con su escala). */
    private fun assertSameJson(expected: String, actual: String) {
        assertEquals(Json.parseToJsonElement(expected), Json.parseToJsonElement(actual))
    }
}
