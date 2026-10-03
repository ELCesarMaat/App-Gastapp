package com.binc.gastapp.demo.datos

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalGroceryStore
import androidx.compose.material.icons.rounded.LocalPharmacy
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.Random

// Datos de muestra. Todo es inventado y esta fijo en una fecha para que las capturas
// salgan iguales siempre. En la app real los montos irian en BigDecimal.

val LocaleMx: Locale = Locale.Builder().setLanguage("es").setRegion("MX").build()

val Hoy: LocalDate = LocalDate.of(2026, 10, 2)

/** Primer dia de la semana segun la configuracion regional (domingo en es-MX). */
val primerDiaSemana: java.time.DayOfWeek = WeekFields.of(LocaleMx).firstDayOfWeek

enum class Categoria(val nombre: String, val icono: ImageVector) {
    Comida("Comida", Icons.Rounded.Restaurant),
    Despensa("Despensa", Icons.Rounded.LocalGroceryStore),
    Transporte("Transporte", Icons.Rounded.DirectionsCar),
    Cafe("Café", Icons.Rounded.LocalCafe),
    Salud("Salud", Icons.Rounded.LocalPharmacy),
    Hogar("Hogar", Icons.Rounded.Home),
    Ocio("Ocio", Icons.Rounded.Movie),
    Suscripciones("Suscripciones", Icons.Rounded.Subscriptions),
}

enum class Metodo(val etiqueta: String) {
    Efectivo("Efectivo"),
    Debito("Débito"),
    Transferencia("Transf."),
    Credito("Crédito"),
}

data class Gasto(
    val id: Long,
    val fecha: LocalDate,
    val titulo: String,
    val categoria: Categoria,
    val monto: Double,
    val hora: LocalTime,
    val metodo: Metodo,
    val tarjeta: String? = null,
    val descripcion: String = "",
)

// ------------------------------------------------------------------ Periodos

/** Rango de fechas, inclusivo en los dos extremos. */
data class Rango(val inicio: LocalDate, val fin: LocalDate) {
    val dias: Int get() = ChronoUnit.DAYS.between(inicio, fin).toInt() + 1

    fun contiene(fecha: LocalDate): Boolean = !fecha.isBefore(inicio) && !fecha.isAfter(fin)

    fun fechas(): List<LocalDate> = (0 until dias).map { inicio.plusDays(it.toLong()) }

    fun esQuincena(): Boolean = this == quincenaDe(inicio)

    /** Periodo anterior: la quincena previa, o un rango del mismo largo justo antes. */
    fun anterior(): Rango =
        if (esQuincena()) quincenaDe(inicio.minusDays(1))
        else Rango(inicio.minusDays(dias.toLong()), inicio.minusDays(1))

    fun siguiente(): Rango =
        if (esQuincena()) quincenaDe(fin.plusDays(1))
        else Rango(fin.plusDays(1), fin.plusDays(dias.toLong()))
}

fun quincenaDe(fecha: LocalDate): Rango =
    if (fecha.dayOfMonth <= 15) Rango(fecha.withDayOfMonth(1), fecha.withDayOfMonth(15))
    else Rango(fecha.withDayOfMonth(16), fecha.with(TemporalAdjusters.lastDayOfMonth()))

/** Atajos de la pantalla Explorar periodo. */
enum class Atajo(val etiqueta: String) {
    EstaSemana("Esta semana"),
    QuincenaActual("Quincena actual"),
    QuincenaPasada("Quincena pasada"),
    EsteMes("Este mes"),
    MesPasado("Mes pasado"),
    Ultimos30("Últimos 30 días");

    fun rango(): Rango = when (this) {
        EstaSemana -> {
            val inicio = Hoy.with(TemporalAdjusters.previousOrSame(primerDiaSemana))
            Rango(inicio, inicio.plusDays(6))
        }
        QuincenaActual -> quincenaDe(Hoy)
        QuincenaPasada -> quincenaDe(quincenaDe(Hoy).inicio.minusDays(1))
        EsteMes -> Rango(Hoy.withDayOfMonth(1), Hoy.with(TemporalAdjusters.lastDayOfMonth()))
        MesPasado -> {
            val mes = Hoy.minusMonths(1)
            Rango(mes.withDayOfMonth(1), mes.with(TemporalAdjusters.lastDayOfMonth()))
        }
        Ultimos30 -> Rango(Hoy.minusDays(29), Hoy)
    }
}

fun etiquetaDe(rango: Rango): String =
    Atajo.entries.firstOrNull { it.rango() == rango }?.etiqueta
        ?: if (rango.esQuincena()) "Quincena" else "Periodo personalizado"

const val presupuestoPeriodo = 9000.0

// ------------------------------------------------------------------- Gastos

private fun h(hora: Int, minuto: Int) = LocalTime.of(hora, minuto)

/** Los ultimos cinco dias van a mano para que los totales de las capturas no cambien. */
private val gastosRecientes = listOf(
    Gasto(1, Hoy, "Spotify Familiar", Categoria.Suscripciones, 199.00, h(19, 0), Metodo.Credito, "BBVA Azul"),
    Gasto(2, Hoy, "Súper Chedraui", Categoria.Despensa, 612.00, h(18, 40), Metodo.Debito),
    Gasto(3, Hoy, "Comida con el equipo", Categoria.Comida, 320.00, h(14, 15), Metodo.Credito, "Nu"),
    Gasto(4, Hoy, "Café y pan", Categoria.Cafe, 74.50, h(9, 30), Metodo.Efectivo),
    Gasto(5, Hoy, "Uber al trabajo", Categoria.Transporte, 86.00, h(8, 12), Metodo.Credito, "BBVA Azul"),
    Gasto(6, Hoy.minusDays(1), "Gasolina", Categoria.Transporte, 850.00, h(20, 5), Metodo.Credito, "BBVA Azul"),
    Gasto(7, Hoy.minusDays(1), "Farmacia", Categoria.Salud, 226.70, h(13, 20), Metodo.Debito),
    Gasto(8, Hoy.minusDays(1), "Café con Laura", Categoria.Cafe, 172.00, h(10, 45), Metodo.Efectivo),
    Gasto(9, Hoy.minusDays(2), "Comida corrida", Categoria.Comida, 420.00, h(15, 10), Metodo.Efectivo),
    Gasto(10, Hoy.minusDays(2), "Despensa quincenal", Categoria.Despensa, 1890.00, h(12, 30), Metodo.Credito, "Nu"),
    Gasto(11, Hoy.minusDays(3), "Cena", Categoria.Comida, 300.00, h(21, 15), Metodo.Efectivo),
    Gasto(12, Hoy.minusDays(3), "Uber", Categoria.Transporte, 180.00, h(19, 40), Metodo.Credito, "BBVA Azul"),
    Gasto(13, Hoy.minusDays(3), "Luz CFE", Categoria.Hogar, 640.40, h(9, 0), Metodo.Transferencia),
    Gasto(14, Hoy.minusDays(4), "Palomitas y refresco", Categoria.Ocio, 135.00, h(18, 5), Metodo.Efectivo),
    Gasto(15, Hoy.minusDays(4), "Cine", Categoria.Ocio, 310.00, h(18, 0), Metodo.Debito),
    Gasto(16, Hoy.minusDays(4), "Súper", Categoria.Despensa, 400.00, h(11, 30), Metodo.Debito),
)

private class Plantilla(
    val titulo: String,
    val categoria: Categoria,
    val minimo: Int,
    val maximo: Int,
    val metodo: Metodo,
    val tarjeta: String? = null,
)

private val plantillas = listOf(
    Plantilla("Uber", Categoria.Transporte, 70, 220, Metodo.Credito, "BBVA Azul"),
    Plantilla("Gasolina", Categoria.Transporte, 600, 950, Metodo.Credito, "BBVA Azul"),
    Plantilla("Café", Categoria.Cafe, 55, 120, Metodo.Efectivo),
    Plantilla("Comida corrida", Categoria.Comida, 120, 260, Metodo.Efectivo),
    Plantilla("Tacos", Categoria.Comida, 90, 220, Metodo.Efectivo),
    Plantilla("Súper", Categoria.Despensa, 250, 900, Metodo.Debito),
    Plantilla("Oxxo", Categoria.Despensa, 40, 160, Metodo.Efectivo),
    Plantilla("Farmacia", Categoria.Salud, 120, 480, Metodo.Debito),
    Plantilla("Cine", Categoria.Ocio, 180, 400, Metodo.Debito),
    Plantilla("Cena", Categoria.Comida, 250, 650, Metodo.Credito, "Nu"),
    Plantilla("Mercado Libre", Categoria.Hogar, 200, 1200, Metodo.Credito, "Nu"),
)

/** Historial "realista" generado con semilla fija: siempre sale igual. */
private fun historialGenerado(desde: LocalDate, hasta: LocalDate, primerId: Long): List<Gasto> {
    val azar = Random(2026)
    var id = primerId
    val resultado = mutableListOf<Gasto>()
    var fecha = desde
    while (!fecha.isAfter(hasta)) {
        repeat(azar.nextInt(5)) {
            val p = plantillas[azar.nextInt(plantillas.size)]
            val monto = (p.minimo + azar.nextInt(p.maximo - p.minimo + 1)).toDouble() +
                if (azar.nextBoolean()) 0.5 else 0.0
            val hora = LocalTime.of(8 + azar.nextInt(14), azar.nextInt(60))
            resultado += Gasto(id++, fecha, p.titulo, p.categoria, monto, hora, p.metodo, p.tarjeta)
        }
        fecha = fecha.plusDays(1)
    }
    return resultado
}

/**
 * Estado compartido del demo. En la app real esto seria Room + un repositorio; aqui
 * basta una lista observable para que editar o borrar se refleje en todas las pantallas.
 */
object RepositorioDemo {
    val gastos: SnapshotStateList<Gasto> = mutableStateListOf<Gasto>().apply {
        addAll(gastosRecientes)
        addAll(historialGenerado(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 27), 1000))
    }

    private var siguienteId = 100_000L

    fun nuevoId(): Long = siguienteId++

    fun guardar(gasto: Gasto) {
        val indice = gastos.indexOfFirst { it.id == gasto.id }
        if (indice >= 0) gastos[indice] = gasto else gastos.add(gasto)
    }

    fun eliminar(gasto: Gasto) {
        gastos.removeAll { it.id == gasto.id }
    }
}

fun List<Gasto>.delDia(fecha: LocalDate): List<Gasto> =
    filter { it.fecha == fecha }.sortedByDescending { it.hora }

fun List<Gasto>.enRango(rango: Rango): List<Gasto> = filter { rango.contiene(it.fecha) }

fun List<Gasto>.totalesPorDia(): Map<LocalDate, Double> =
    groupBy { it.fecha }.mapValues { (_, lista) -> lista.sumOf { it.monto } }

// ---------------------------------------------------------------- Tarjetas

enum class Estado { Vencido, Pronto, AlCorriente }

data class CompraMsi(
    val titulo: String,
    val categoria: Categoria,
    val total: Double,
    val mensualidad: Double,
    val mensualidadActual: Int,
    val plazo: Int,
)

data class Tarjeta(
    val etiqueta: String,
    val banco: String,
    val producto: String,
    val ultimos4: String,
    val color: Color,
    val limite: Double,
    val deuda: Double,
    val pagoSinIntereses: Double,
    val proximoCorte: LocalDate,
    val limitePago: LocalDate,
    val estadoPago: Estado,
    val textoPago: String,
    val msi: List<CompraMsi> = emptyList(),
) {
    val disponible: Double get() = limite - deuda
    val usoPorcentaje: Float get() = (deuda / limite).toFloat()
    val deudaFuturaMsi: Double get() = deuda - pagoSinIntereses
}

val tarjetas = listOf(
    Tarjeta(
        etiqueta = "BBVA Azul",
        banco = "BBVA",
        producto = "Azul",
        ultimos4 = "4821",
        color = Color(0xFF1E4FA3),
        limite = 40_000.0,
        deuda = 8_420.35,
        pagoSinIntereses = 5_310.20,
        proximoCorte = LocalDate.of(2026, 10, 20),
        limitePago = LocalDate.of(2026, 10, 5),
        estadoPago = Estado.Pronto,
        textoPago = "Vence en 3 días",
        msi = listOf(
            CompraMsi("Pantalla Samsung 55\"", Categoria.Hogar, 6_999.00, 583.25, 7, 12),
            CompraMsi("Audífonos Sony", Categoria.Ocio, 1_163.40, 193.90, 5, 6),
        ),
    ),
    Tarjeta(
        etiqueta = "Nu",
        banco = "Nu",
        producto = "Tarjeta de crédito",
        ultimos4 = "0937",
        color = Color(0xFF7C3AED),
        limite = 25_000.0,
        deuda = 2_150.00,
        pagoSinIntereses = 0.0,
        proximoCorte = LocalDate.of(2026, 10, 25),
        // Ya se cubrio el corte, asi que la fecha limite salto al mes siguiente
        // (mismo criterio que CalculateCycleDatesAsync en la app actual).
        limitePago = LocalDate.of(2026, 11, 5),
        estadoPago = Estado.AlCorriente,
        textoPago = "Pagada este ciclo",
    ),
    Tarjeta(
        etiqueta = "Banamex Costco",
        banco = "Banamex",
        producto = "Costco",
        ultimos4 = "5512",
        color = Color(0xFF3F4A5A),
        limite = 20_000.0,
        deuda = 4_200.00,
        pagoSinIntereses = 4_200.00,
        proximoCorte = LocalDate.of(2026, 10, 8),
        limitePago = LocalDate.of(2026, 10, 1),
        estadoPago = Estado.Vencido,
        textoPago = "Venció ayer",
    ),
)

// ----------------------------------------------------------- Suscripciones

enum class EstadoSuscripcion { Activa, Prueba, Pausada }

data class Suscripcion(
    val servicio: String,
    val plan: String,
    val monto: Double,
    val color: Color,
    val proximoCobro: LocalDate?,
    val estado: EstadoSuscripcion,
    val pagaCon: String,
    val cobradaEsteCiclo: Boolean = false,
)

val suscripciones = listOf(
    Suscripcion("Netflix", "Estándar", 299.00, Color(0xFFE50914), LocalDate.of(2026, 10, 3), EstadoSuscripcion.Activa, "Crédito · BBVA Azul"),
    Suscripcion("Spotify", "Familiar", 199.00, Color(0xFF1DB954), LocalDate.of(2026, 11, 2), EstadoSuscripcion.Activa, "Crédito · BBVA Azul", cobradaEsteCiclo = true),
    Suscripcion("iCloud+", "200 GB", 49.00, Color(0xFF3B82F6), LocalDate.of(2026, 10, 14), EstadoSuscripcion.Activa, "Débito"),
    Suscripcion("Smart Fit", "Black", 489.00, Color(0xFF1F2937), LocalDate.of(2026, 10, 20), EstadoSuscripcion.Activa, "Crédito · Nu"),
    Suscripcion("ChatGPT", "Plus", 399.00, Color(0xFF10A37F), LocalDate.of(2026, 10, 7), EstadoSuscripcion.Prueba, "Crédito · Nu"),
    Suscripcion("Disney+", "Premium", 219.00, Color(0xFF113CCF), null, EstadoSuscripcion.Pausada, "Débito"),
)

data class CobroProximo(val fecha: LocalDate, val servicio: String, val monto: Double, val origen: String)

val cobrosProximos = listOf(
    CobroProximo(LocalDate.of(2026, 10, 3), "Netflix", 299.00, "Crédito · BBVA Azul"),
    CobroProximo(LocalDate.of(2026, 10, 7), "ChatGPT · fin de la prueba", 399.00, "Crédito · Nu"),
    CobroProximo(LocalDate.of(2026, 10, 14), "iCloud+", 49.00, "Débito"),
    CobroProximo(LocalDate.of(2026, 10, 20), "Smart Fit", 489.00, "Crédito · Nu"),
    CobroProximo(LocalDate.of(2026, 11, 2), "Spotify", 199.00, "Crédito · BBVA Azul"),
    CobroProximo(LocalDate.of(2026, 11, 3), "Netflix", 299.00, "Crédito · BBVA Azul"),
)
