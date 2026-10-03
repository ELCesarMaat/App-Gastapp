package com.binc.gastapp.domain.paridad

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.fail
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime

// Lectura de los fixtures que genera tools/Gastapp.Paridad a partir del codigo de la
// app MAUI. Cada prueba de paridad recorre un archivo y compara caso por caso.

object Fixtures {
    // Devuelve JsonElement (no JsonObject) para que fixture["campo"] use el get de abajo,
    // que no es nulable, en vez del get de Map.
    fun load(name: String): JsonElement {
        val stream = Fixtures::class.java.getResourceAsStream("/paridad/$name")
            ?: error("Falta el fixture $name. Generalo con: dotnet run --project tools/Gastapp.Paridad")
        return Json.parseToJsonElement(stream.bufferedReader().use { it.readText() })
    }
}

operator fun JsonElement.get(key: String): JsonElement = jsonObject.getValue(key)

operator fun JsonElement.get(index: Int): JsonElement = jsonArray[index]

val JsonElement.list: JsonArray get() = jsonArray

val JsonElement.text: String get() = jsonPrimitive.content

val JsonElement.number: Int get() = jsonPrimitive.int

val JsonElement.flag: Boolean
    get() = jsonPrimitive.content.let { it == "1" || it == "true" }

val JsonElement.bool: Boolean get() = jsonPrimitive.boolean

val JsonElement.decimal: BigDecimal get() = BigDecimal(jsonPrimitive.content)

val JsonElement.double: Double get() = jsonPrimitive.content.toDouble()

val JsonElement.date: LocalDate get() = LocalDate.parse(jsonPrimitive.content)

val JsonElement.dateTime: LocalDateTime get() = LocalDateTime.parse(jsonPrimitive.content)

val JsonElement.isNull: Boolean get() = this is JsonNull

val JsonElement.intOrNull: Int? get() = if (isNull) null else number

val JsonElement.dateOrNull: LocalDate? get() = if (isNull) null else date

val JsonElement.dateTimeOrNull: LocalDateTime? get() = if (isNull) null else dateTime

/**
 * Junta las diferencias de todos los casos y falla al final con las primeras, para
 * ver de un vistazo si es un solo caso raro o un error de fondo.
 */
class Comparison(private val name: String) {
    private val failures = mutableListOf<String>()
    private var cases = 0

    fun case(block: () -> Unit) {
        cases++
        block()
    }

    fun equal(context: String, expected: Any?, actual: Any?) {
        if (expected != actual) failures += "$context -> MAUI: $expected | Kotlin: $actual"
    }

    /** Montos exactos (sumas, restas): mismo valor sin importar la escala. */
    fun equalMoney(context: String, expected: BigDecimal, actual: BigDecimal) {
        if (expected.compareTo(actual) != 0) failures += "$context -> MAUI: $expected | Kotlin: $actual"
    }

    /**
     * Resultados de una division: .NET guarda 28-29 digitos y aqui hay 34, asi que se
     * comparan redondeados a [scale] decimales.
     */
    fun equalDivision(context: String, expected: BigDecimal, actual: BigDecimal, scale: Int = 10) {
        val a = expected.setScale(scale, RoundingMode.HALF_EVEN)
        val b = actual.setScale(scale, RoundingMode.HALF_EVEN)
        if (a.compareTo(b) != 0) failures += "$context -> MAUI: $expected | Kotlin: $actual"
    }

    fun equalDouble(context: String, expected: Double, actual: Double, tolerance: Double = 1e-9) {
        if (Math.abs(expected - actual) > tolerance) failures += "$context -> MAUI: $expected | Kotlin: $actual"
    }

    fun verify() {
        if (cases == 0) fail("$name: el fixture no trajo casos")
        if (failures.isNotEmpty()) {
            fail(
                "$name: ${failures.size} diferencias en $cases casos. Primeras:\n" +
                    failures.take(20).joinToString("\n")
            )
        }
        println("$name: $cases casos iguales a MAUI")
    }
}
