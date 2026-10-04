package com.binc.gastapp.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binc.gastapp.R
import com.binc.gastapp.data.repository.CategoryRepository
import com.binc.gastapp.data.repository.CreditCardRepository
import com.binc.gastapp.data.repository.SpendingRepository
import com.binc.gastapp.data.repository.SubscriptionRepository
import com.binc.gastapp.data.repository.UserRepository
import com.binc.gastapp.domain.model.StatusLevel
import com.binc.gastapp.domain.money.sumOfMoney
import com.binc.gastapp.domain.subscriptions.totalMonthlyCost
import com.binc.gastapp.domain.subscriptions.upcomingCharges
import com.binc.gastapp.ui.category.CategoryDirectory
import com.binc.gastapp.ui.format.Strings
import com.binc.gastapp.ui.format.formatMoney
import com.binc.gastapp.ui.format.paymentStatus
import com.binc.gastapp.ui.format.whenText
import com.binc.gastapp.ui.spending.SpendingItem
import com.binc.gastapp.ui.spending.spendingItems
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Un dia de la tira del periodo, con lo gastado respecto al dia mas alto. */
data class StripDay(val date: LocalDate, val ratio: Float, val isFuture: Boolean)

/** Chip del acceso a Mis tarjetas: la tarjeta con deuda que vence primero. */
data class CardChip(val text: String, val level: StatusLevel)

data class SummaryUiState(
    val loaded: Boolean = false,
    val today: LocalDate,
    val period: ResolvedPeriod? = null,
    val periodTotal: BigDecimal = BigDecimal.ZERO,
    val days: List<StripDay> = emptyList(),
    val selectedDay: LocalDate = today,
    val dayTotal: BigDecimal = BigDecimal.ZERO,
    val dayItems: List<SpendingItem> = emptyList(),
    /** null: el texto de siempre ("Consulta cortes, fechas de pago y saldo diferido"). */
    val cardsSubtitle: String? = null,
    val cardChip: CardChip? = null,
    /** null: el texto de siempre ("Revisa cuánto pagas al mes y qué cobros vienen"). */
    val subscriptionsSubtitle: String? = null,
)

/**
 * Resumen (SummaryViewModel de MAUI): navegador de periodos, tira con todos los dias,
 * total del dia, accesos a tarjetas y suscripciones y los gastos del dia.
 *
 * Decision del usuario (anexo F): el total del dia sigue el criterio del periodo, asi
 * que las compras con tarjeta se listan pero no suman; cuenta el pago a la tarjeta.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SummaryViewModel @Inject constructor(
    users: UserRepository,
    categories: CategoryRepository,
    private val spendings: SpendingRepository,
    private val cards: CreditCardRepository,
    subscriptions: SubscriptionRepository,
    private val strings: Strings,
    private val clock: Clock,
) : ViewModel() {

    private val today = MutableStateFlow(LocalDate.now(clock))
    private val selection = MutableStateFlow<PeriodSelection>(PeriodSelection.Paid(0))
    private val requestedDay = MutableStateFlow<LocalDate?>(null)
    private val user = users.observeUser()

    private val directory = categories.observeAll().map(::CategoryDirectory)
    private val cardList = cards.observeCards()

    /**
     * Periodo, dia elegido, tira y gastos del dia salen de una sola cadena: asi la
     * pantalla nunca ve el periodo nuevo con los montos del anterior.
     */
    private val periodPart: Flow<PeriodPart> = combine(selection, user, today, requestedDay) { s, u, t, requested ->
        val period = resolvePeriod(s, u, t)
        val day = if (requested != null && requested in period && !requested.isAfter(t)) requested else period.defaultDay(t)
        Triple(period, day, t)
    }.distinctUntilChanged().flatMapLatest { (period, day, t) ->
        combine(
            spendings.observeDailyTotals(period.start, period.end),
            spendings.observeDay(day),
            directory,
            cardList,
        ) { totals, list, dir, cardsNow ->
            val byDay = totals.associate { it.day to it.totalWithoutCardPurchases }
            val max = byDay.values.maxOrNull()?.takeIf { it.signum() > 0 }
            PeriodPart(
                period = period,
                days = period.days().map { date ->
                    StripDay(
                        date = date,
                        ratio = if (max == null) 0f else (byDay[date] ?: BigDecimal.ZERO).toFloat() / max.toFloat(),
                        isFuture = date.isAfter(t),
                    )
                },
                total = byDay.values.sumOfMoney { it },
                day = day,
                items = spendingItems(list, dir, cardsNow),
            )
        }
    }

    private val cardInfo: Flow<Pair<String?, CardChip?>> = today.flatMapLatest { cards.observeSummaries(it) }.map { summaries ->
        if (summaries.isEmpty()) return@map strings.get(R.string.cards_subtitle_empty) to null
        val urgent = summaries.filter { it.totalDebt.signum() > 0 }.minByOrNull { it.daysUntilPayment }
        val chip = urgent?.let {
            CardChip(
                text = strings.get(R.string.card_chip, it.card.cardName, it.paymentStatus(strings).lowercaseFirst()),
                level = it.paymentLevel,
            )
        }
        val subtitle = strings.get(if (urgent == null) R.string.cards_subtitle_no_pending else R.string.cards_subtitle_with_pending)
        subtitle to chip
    }

    private val subscriptionsSubtitle: Flow<String?> = combine(subscriptions.observeAll(), today) { list, t ->
        if (list.isEmpty()) return@combine null
        val monthly = formatMoney(totalMonthlyCost(list, t))
        val next = upcomingCharges(list, t, daysAhead = 45).firstOrNull()
        if (next == null) strings.get(R.string.subscriptions_subtitle_monthly, monthly)
        else strings.get(R.string.subscriptions_subtitle_next, monthly, next.serviceName, next.whenText(strings).lowercaseFirst())
    }

    val state: StateFlow<SummaryUiState> = combine(
        periodPart,
        cardInfo,
        subscriptionsSubtitle,
        today,
    ) { part, cardsNow, subs, t ->
        SummaryUiState(
            loaded = true,
            today = t,
            period = part.period,
            periodTotal = part.total,
            days = part.days,
            selectedDay = part.day,
            dayTotal = part.items.filterNot { it.isCardPurchase }.sumOfMoney { it.spending.amount },
            dayItems = part.items,
            cardsSubtitle = cardsNow.first,
            cardChip = cardsNow.second,
            subscriptionsSubtitle = subs,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SummaryUiState(today = today.value))

    private data class PeriodPart(
        val period: ResolvedPeriod,
        val days: List<StripDay>,
        val total: BigDecimal,
        val day: LocalDate,
        val items: List<SpendingItem>,
    )

    // ------------------------------------------------------------ acciones

    fun selectDay(date: LocalDate) {
        if (!date.isAfter(today.value)) requestedDay.value = date
    }

    fun previousPeriod() {
        val current = state.value.period ?: return
        selection.value = current.previous()
        requestedDay.value = null
    }

    fun nextPeriod() {
        val current = state.value.period ?: return
        selection.value = current.next() ?: return
        requestedDay.value = null
    }

    /** SetTodayDate de MAUI: regresa al periodo actual y a hoy. */
    fun goToToday() {
        selection.value = PeriodSelection.Paid(0)
        requestedDay.value = today.value
    }

    /** Rango elegido en Explorar periodo. */
    fun applyRange(start: LocalDate, end: LocalDate) {
        viewModelScope.launch {
            selection.value = selectionForRange(start, end, currentUser(), today.value)
            requestedDay.value = null
        }
    }

    /** Despues de guardar un gasto se muestra su dia, cambiando de periodo si hace falta. */
    fun showDay(date: LocalDate) {
        viewModelScope.launch {
            val current = state.value.period
            if (current == null || date !in current) {
                selection.value = paidSelectionContaining(date, currentUser(), today.value)
            }
            requestedDay.value = date
        }
    }

    /** Al volver a la app: si cambio el dia, el periodo actual ya es otro (DayChangedMessage). */
    fun refreshToday() {
        val now = LocalDate.now(clock)
        if (now == today.value) return
        val wasOnToday = requestedDay.value == null || requestedDay.value == today.value
        today.value = now
        if (wasOnToday) requestedDay.value = null
    }

    fun deleteSpending(spendingId: String, onDeleted: () -> Unit) {
        viewModelScope.launch { if (spendings.delete(spendingId)) onDeleted() }
    }

    fun restoreSpending(spendingId: String) {
        viewModelScope.launch { spendings.restore(spendingId) }
    }

    private val userState = user.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun currentUser() = userState.value
}

private fun String.lowercaseFirst(): String = replaceFirstChar { it.lowercase() }
