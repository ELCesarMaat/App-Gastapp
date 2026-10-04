package com.binc.gastapp.wear

import com.binc.gastapp.core.remote.formatApiInstant
import com.binc.gastapp.core.wear.DeviceCategoryDto
import com.binc.gastapp.core.wear.DeviceDaySpendingDto
import com.binc.gastapp.core.wear.WearJson
import com.binc.gastapp.core.wear.WearPaths
import com.binc.gastapp.core.wear.WearTodayPayload
import com.binc.gastapp.data.repository.CategoryRepository
import com.binc.gastapp.data.repository.SpendingRepository
import com.binc.gastapp.data.repository.UserRepository
import com.binc.gastapp.domain.model.Category
import com.binc.gastapp.domain.model.Spending
import com.binc.gastapp.startup.DayClock
import java.math.BigDecimal
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer

/**
 * El dia de hoy tal como lo pinta el reloj (PushTodayAsync de MAUI).
 *
 * Suma TODO lo del dia, compras con tarjeta incluidas: es lo mismo que calcula el API en
 * GET /Device/Summary, que el reloj tambien consulta. Si aqui se quitaran las compras con
 * tarjeta, el total del reloj cambiaria segun de donde llegara el dato.
 */
fun buildTodayPayload(spendings: List<Spending>, categories: List<Category>, zone: ZoneId): WearTodayPayload {
    val names = categories.associate { it.categoryId to it.categoryName }
    val active = spendings.filterNot { it.isDeleted }
    return WearTodayPayload(
        total = active.fold(BigDecimal.ZERO) { acc, s -> acc + s.amount }.toDouble(),
        count = active.size,
        spendings = active.sortedByDescending { it.date }.map {
            DeviceDaySpendingDto(
                spendingId = it.spendingId,
                title = it.title,
                categoryName = names[it.categoryId],
                amount = it.amount.toDouble(),
                // El reloj lo pasa a su hora local; en UTC un cambio de zona no descoloca la lista.
                occurredAt = formatApiInstant(it.date.atZone(zone).toInstant()),
            )
        },
    )
}

fun buildCategoriesPayload(categories: List<Category>): List<DeviceCategoryDto> =
    categories.map { DeviceCategoryDto(it.categoryId, it.categoryName, it.isDefaultCategory) }

/**
 * Mantiene al dia los DataItems del reloj (/gastapp/today y /gastapp/categories) mientras
 * vive el proceso, observando Room: cualquier gasto que se registre, edite o borre, o el
 * cambio de dia, se publica solo. Reemplaza al StartWatching manual de MAUI.
 *
 * Sin cuenta en el telefono no publica nada: mandar listas vacias le borraria las
 * categorias a un reloj que sigue vinculado a la cuenta.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Singleton
class WearPublisher @Inject constructor(
    private val users: UserRepository,
    private val spendings: SpendingRepository,
    private val categories: CategoryRepository,
    private val dayClock: DayClock,
    private val channel: WearChannel,
    private val clock: Clock,
) {
    suspend fun run() {
        users.observeUser().map { it != null }.distinctUntilChanged().collectLatest { hasAccount ->
            if (!hasAccount) return@collectLatest
            coroutineScope {
                launch {
                    dayClock.today
                        .flatMapLatest { day ->
                            combine(spendings.observeDay(day), categories.observeAll()) { list, cats ->
                                WearJson.encodeToString(WearTodayPayload.serializer(), buildTodayPayload(list, cats, clock.zone))
                            }
                        }
                        .distinctUntilChanged()
                        // Una rafaga (alta de una tarjeta con sus MSI, un login) sale en un envio.
                        .debounce(DebounceMillis)
                        .collect { channel.putData(WearPaths.TODAY, it) }
                }
                launch {
                    categories.observeAll()
                        .map { WearJson.encodeToString(CategoriesSerializer, buildCategoriesPayload(it)) }
                        .distinctUntilChanged()
                        .debounce(DebounceMillis)
                        .collect { channel.putData(WearPaths.CATEGORIES, it) }
                }
            }
        }
    }

    private companion object {
        const val DebounceMillis = 500L
        val CategoriesSerializer = ListSerializer(DeviceCategoryDto.serializer())
    }
}
