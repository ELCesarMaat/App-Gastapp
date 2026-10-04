package com.binc.gastapp.ui.format

import androidx.test.core.app.ApplicationProvider
import com.binc.gastapp.R
import com.binc.gastapp.data.remote.translateServerMessage
import com.binc.gastapp.domain.model.IncomeTypes
import com.binc.gastapp.ui.summary.PeriodLabel
import com.binc.gastapp.ui.summary.text
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La app en otro idioma y otra region: textos en ingles, montos con la moneda de la
 * region del telefono y fechas en el orden de cada idioma. Las demas pruebas corren en
 * es-MX (robolectric.properties) y ya cubren el espanol.
 */
@RunWith(RobolectricTestRunner::class)
class LocaleFormatTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val strings by lazy { ResourceStrings(context) }
    private val date = LocalDate.of(2026, 10, 2)

    private fun use(tag: String, currency: String) = AppLocale.set(Locale.forLanguageTag(tag), Currency.getInstance(currency))

    @After
    fun backToMexico() = use("es-MX", "MXN")

    @Test
    fun `mexico conserva los formatos de siempre`() {
        assertEquals("$1,234.56", formatMoney(BigDecimal("1234.56")))
        assertEquals("Viernes 2 de octubre", longDate(date))
        assertEquals("2 oct", shortDate(date))
        assertEquals("05/oct", dayMonth(LocalDate.of(2026, 10, 5)))
        assertEquals("02/10/2026", numericDate(date))
        assertEquals("1 – 15 oct", shortRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15)))
        assertEquals("16 al 30 de septiembre", longRange(LocalDate.of(2026, 9, 16), LocalDate.of(2026, 9, 30)))
    }

    @Test
    fun `estados unidos en ingles con dolares`() {
        use("en-US", "USD")
        assertEquals("$1,234.56", formatMoney(BigDecimal("1234.56")))
        assertEquals("$1,235", formatMoneyWhole(BigDecimal("1234.56")))
        assertEquals("Friday, October 2", longDate(date))
        assertEquals("Oct 2", shortDate(date))
        assertEquals("10/02/2026", numericDate(date))
        assertEquals("Oct 1 – 15", shortRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15)))
        assertEquals("September 16 – 30", longRange(LocalDate.of(2026, 9, 16), LocalDate.of(2026, 9, 30)))
        assertEquals("September 3 – October 2", longRange(LocalDate.of(2026, 9, 3), date))
    }

    @Test
    fun `espana usa euros y coma decimal`() {
        use("es-ES", "EUR")
        assertEquals("1234,56 €", formatMoney(BigDecimal("1234.56")).replace(".", ""))
        assertEquals(',', AppLocale.decimalSeparator)
        // Los campos de monto aceptan coma o punto como decimal y lo muestran con coma.
        assertEquals("12,5", filterAmountInput("12,5"))
        assertEquals("12,5", filterAmountInput("12.5"))
        assertEquals(null, filterAmountInput("12,555"))
        assertEquals(BigDecimal("1250.5"), parseAmountInput("1.250,5"))
        assertEquals("1250,5", BigDecimal("1250.50").toInputText())
        assertEquals("02/10/2026", numericDate(date))
    }

    @Test
    fun `mexico sigue quitando la coma de miles al teclear`() {
        assertEquals("1250", filterAmountInput("1,250"))
        assertEquals("12.50", filterAmountInput("12.50"))
        assertEquals(BigDecimal("12500.50"), parseAmountInput("$12,500.50"))
    }

    @Test
    fun `monedas sin centavos`() {
        use("ja-JP", "JPY")
        assertEquals(0, AppLocale.currencyDigits)
        assertEquals(null, filterAmountInput("12.5"))
        assertEquals("1250", filterAmountInput("1250"))
    }

    @Test
    @Config(qualifiers = "en-rUS")
    fun `los textos salen en ingles`() {
        assertEquals("en", strings.get(R.string.locale_language))
        assertEquals("Overview", strings.get(R.string.tab_summary))
        assertEquals("In 1 day", strings.plural(R.plurals.in_days, 1))
        assertEquals("In 5 days", strings.plural(R.plurals.in_days, 5))
        assertEquals("Current pay period", PeriodLabel.Paid(IncomeTypes.BIWEEKLY, 0).text(strings))
        assertEquals("2 months ago", PeriodLabel.Paid(IncomeTypes.MONTHLY, 2).text(strings))
        assertEquals("Uncategorized", categoryLabel(strings, "Sin categoria"))
        assertEquals("Comida", categoryLabel(strings, "Comida"))
    }

    @Test
    @Config(qualifiers = "fr-rFR")
    fun `otro idioma cae en ingles`() {
        assertEquals("en", strings.get(R.string.locale_language))
        assertEquals("Savings", strings.get(R.string.tab_savings))
    }

    @Test
    @Config(qualifiers = "en-rUS")
    fun `los errores conocidos del API se traducen`() {
        assertEquals("That email is already in use", translateServerMessage(strings, "Email en uso"))
        assertEquals("Wait 45 seconds to request another code.", translateServerMessage(strings, "Espera 45 segundos para pedir otro código."))
        assertEquals(
            "You've requested several codes. Try again in 12 minutes.",
            translateServerMessage(strings, "Ya pediste varios códigos. Intenta de nuevo en 12 minutos."),
        )
        // Uno que no se conoce se muestra tal cual.
        assertEquals("Algo nuevo del servidor", translateServerMessage(strings, "Algo nuevo del servidor"))
    }

    @Test
    fun `en espanol los mensajes del API quedan igual`() {
        assertEquals("Email en uso", translateServerMessage(strings, "Email en uso"))
        assertEquals("Espera 45 segundos para pedir otro código.", translateServerMessage(strings, "Espera 45 segundos para pedir otro código."))
    }
}
