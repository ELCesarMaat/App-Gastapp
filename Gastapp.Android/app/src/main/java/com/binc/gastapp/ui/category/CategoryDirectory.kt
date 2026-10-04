package com.binc.gastapp.ui.category

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalGroceryStore
import androidx.compose.material.icons.rounded.LocalPharmacy
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import com.binc.gastapp.domain.model.Category
import com.binc.gastapp.domain.model.DEFAULT_CATEGORY_NAME
import com.binc.gastapp.domain.model.isDefaultCategoryName
import com.binc.gastapp.domain.spendings.CategoryTotal
import java.math.BigDecimal
import java.text.Normalizer
import java.util.Locale

/**
 * Las categorias como se muestran. Una cuenta puede tener DOS "Sin categoria" (MAUI la
 * duplicaba al registrarse, hallazgo 13). Decision del usuario: no se tocan los datos,
 * solo se muestran como una sola: un solo renglon en los resumenes, una sola opcion en
 * el formulario y, al abrirla, los gastos de las dos.
 */
class CategoryDirectory(categories: List<Category>) {
    private val byId = categories.associateBy { it.categoryId }

    /** Todas las que cuentan como "Sin categoria". */
    val defaultIds: Set<String> = categories
        .filter { it.isDefaultCategory || isDefaultCategoryName(it.categoryName) }
        .map { it.categoryId }
        .toSet()

    /** La que representa a todas: la marcada como por defecto, si hay; si no, la primera. */
    val primaryDefaultId: String? = categories.firstOrNull { it.isDefaultCategory }?.categoryId
        ?: categories.firstOrNull { it.categoryId in defaultIds }?.categoryId

    /** Para elegir: "Sin categoria" una sola vez, primero, y luego las demas. */
    val pickerCategories: List<Category> = categories.filter { it.categoryId !in defaultIds || it.categoryId == primaryDefaultId }
        .sortedWith(compareByDescending<Category> { it.categoryId == primaryDefaultId })

    fun isDefault(categoryId: String?): Boolean = categoryId == null || categoryId in defaultIds || categoryId !in byId

    /** El id con el que se muestra (una "Sin categoria" duplicada se ve como la principal). */
    fun displayId(categoryId: String?): String? = if (isDefault(categoryId)) primaryDefaultId ?: categoryId else categoryId

    fun nameOf(categoryId: String?): String =
        if (isDefault(categoryId)) DEFAULT_CATEGORY_NAME else byId[categoryId]?.categoryName ?: DEFAULT_CATEGORY_NAME

    /** Los ids cuyos gastos se ven al abrir esta categoria. */
    fun idsFor(categoryId: String): List<String> =
        if (categoryId in defaultIds) defaultIds.toList() else listOf(categoryId)

    /**
     * Junta los renglones de las "Sin categoria" (y de categorias que ya no existen en el
     * telefono, que el resumen tambien muestra como "Sin categoria") en uno solo.
     */
    fun mergeTotals(totals: List<CategoryTotal>): List<CategoryTotal> {
        val (defaults, others) = totals.partition { isDefault(it.categoryId) }
        if (defaults.isEmpty()) return totals
        val merged = CategoryTotal(
            categoryId = primaryDefaultId ?: defaults.first().categoryId,
            name = DEFAULT_CATEGORY_NAME,
            amount = defaults.fold(BigDecimal.ZERO) { acc, t -> acc + t.amount },
        )
        return (others + merged).sortedByDescending { it.amount }
    }
}

/** Icono de una categoria por su nombre. Las categorias son libres: se adivina por palabras. */
fun categoryIcon(name: String?): ImageVector {
    if (name.isNullOrBlank() || isDefaultCategoryName(name)) return Icons.AutoMirrored.Rounded.Label
    val plain = Normalizer.normalize(name, Normalizer.Form.NFD).replace(Regex("\\p{Mn}"), "").lowercase(Locale.ROOT)
    return IconRules.firstOrNull { (words, _) -> words.any { it in plain } }?.second ?: Icons.Rounded.Sell
}

private val IconRules: List<Pair<List<String>, ImageVector>> = listOf(
    // Palabras en espanol y en ingles (las categorias las escribe el usuario en su idioma).
    listOf("cafe", "starbucks", "coffee") to Icons.Rounded.LocalCafe,
    listOf("comida", "restaurant", "taco", "almuerzo", "cena", "desayuno", "antojo", "rapida", "food", "lunch", "dinner", "breakfast", "snack") to
        Icons.Rounded.Restaurant,
    listOf("super", "despensa", "mandado", "abarrote", "grocer") to Icons.Rounded.LocalGroceryStore,
    listOf("transporte", "uber", "didi", "gasolina", "taxi", "auto", "carro", "camion", "metro", "estacionamiento", "transport", "fuel", "parking") to
        Icons.Rounded.DirectionsCar,
    listOf("salud", "farmacia", "medic", "doctor", "dentista", "hospital", "health", "pharmac") to Icons.Rounded.LocalPharmacy,
    listOf("hogar", "casa", "renta", "luz", "agua", "gas", "mantenimiento", "home", "house", "rent", "utilit") to Icons.Rounded.Home,
    listOf("ocio", "cine", "entreten", "diversion", "fiesta", "salida", "bar", "movie", "entertain", "party") to Icons.Rounded.Movie,
    listOf("suscrip", "membresia", "streaming", "netflix", "spotify", "subscri", "membership") to Icons.Rounded.Subscriptions,
    listOf("ropa", "zapato", "calzado", "cloth", "shoe") to Icons.Rounded.Checkroom,
    listOf("escuela", "educa", "colegiatura", "curso", "libro", "school", "tuition", "course", "book") to Icons.Rounded.School,
    listOf("mascota", "perro", "gato", "veterinari", "dog") to Icons.Rounded.Pets,
    listOf("regalo", "gift") to Icons.Rounded.CardGiftcard,
    listOf("viaje", "vuelo", "hotel", "vacacion", "travel", "flight", "trip", "vacation") to Icons.Rounded.Flight,
    listOf("gym", "gimnasio", "deporte", "fitness", "sport") to Icons.Rounded.FitnessCenter,
    listOf("internet", "telefono", "celular", "servicio", "phone", "mobile") to Icons.Rounded.Wifi,
    listOf("tarjeta", "credito", "banco", "card", "credit", "bank") to Icons.Rounded.CreditCard,
)
