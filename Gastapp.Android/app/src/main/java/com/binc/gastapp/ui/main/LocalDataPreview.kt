package com.binc.gastapp.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.binc.gastapp.BuildConfig
import com.binc.gastapp.data.local.DevSampleData
import com.binc.gastapp.data.repository.CreditCardRepository
import com.binc.gastapp.data.repository.SpendingRepository
import com.binc.gastapp.data.repository.SubscriptionRepository
import com.binc.gastapp.data.repository.UserRepository
import com.binc.gastapp.domain.cards.buildCardSummary
import com.binc.gastapp.domain.periods.periodBounds
import com.binc.gastapp.domain.subscriptions.totalMonthlyCost
import com.binc.gastapp.domain.money.sumOfMoney
import com.binc.gastapp.ui.components.ScreenMargin
import com.binc.gastapp.ui.components.appear
import com.binc.gastapp.ui.format.MexicoLocale
import com.binc.gastapp.ui.format.formatMoney
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Provisional de la Fase 2: muestra en Resumen lo que hay en Room, calculado con los
// repositorios y :domain, para comprobar la capa de datos en el telefono. Se borra
// cuando llegue el Resumen real (Fase 4.2).

data class LocalDataPreviewState(
    val userName: String? = null,
    val periodText: String = "",
    val todayTotal: BigDecimal = BigDecimal.ZERO,
    val todayCount: Int = 0,
    val periodTotal: BigDecimal = BigDecimal.ZERO,
    val topCategories: List<Pair<String, BigDecimal>> = emptyList(),
    val cards: List<String> = emptyList(),
    val subscriptionCount: Int = 0,
    val subscriptionsMonthly: BigDecimal = BigDecimal.ZERO,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LocalDataPreviewViewModel @Inject constructor(
    users: UserRepository,
    spendings: SpendingRepository,
    cards: CreditCardRepository,
    subscriptions: SubscriptionRepository,
    private val sampleData: DevSampleData,
    private val clock: Clock,
) : ViewModel() {

    private val shortDate = DateTimeFormatter.ofPattern("d MMM", MexicoLocale)

    val state: StateFlow<LocalDataPreviewState> = users.observeUser().flatMapLatest { user ->
        if (user == null) return@flatMapLatest flowOf(LocalDataPreviewState())
        val today = LocalDate.now(clock)
        val period = periodBounds(user.incomeTypeId, user.firstPayDay, user.secondPayDay, today)

        combine(
            spendings.observeDailyTotals(period.start, period.end),
            spendings.observeCategoryTotals(period.start, period.end),
            cards.observeCards(),
            spendings.observeCardMovements(),
            subscriptions.observeAll(),
        ) { days, categories, cardList, movements, subs ->
            val todayRow = days.firstOrNull { it.day == today }
            LocalDataPreviewState(
                userName = user.name,
                periodText = "${period.start.format(shortDate)} – ${period.end.format(shortDate)}",
                todayTotal = todayRow?.total ?: BigDecimal.ZERO,
                todayCount = todayRow?.spendingCount ?: 0,
                periodTotal = days.sumOfMoney { it.totalWithoutCardPurchases },
                topCategories = categories.take(3).map { it.name to it.amount },
                cards = cardList.map { card ->
                    val summary = buildCardSummary(card, movements, today)
                    "${card.cardName}: debe ${formatMoney(summary.totalDebt)}, paga el ${summary.nextPaymentDueDate.format(shortDate)}"
                },
                subscriptionCount = subs.size,
                subscriptionsMonthly = totalMonthlyCost(subs, today),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalDataPreviewState())

    fun loadSample() {
        viewModelScope.launch { sampleData.load() }
    }

    fun clear() {
        viewModelScope.launch { sampleData.clear() }
    }
}

@Composable
fun LocalDataPreviewCard(modifier: Modifier = Modifier, viewModel: LocalDataPreviewViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenMargin),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Datos locales (Room)", style = MaterialTheme.typography.titleMedium)
            if (state.userName == null) {
                Text(
                    "La base está vacía. Se llena al iniciar sesión (Fase 3).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text("${state.userName} · periodo ${state.periodText}", style = MaterialTheme.typography.bodyMedium)
                Text("Hoy: ${formatMoney(state.todayTotal)} en ${state.todayCount} gastos", style = MaterialTheme.typography.bodyMedium)
                Text("Periodo (sin compras con tarjeta): ${formatMoney(state.periodTotal)}", style = MaterialTheme.typography.bodyMedium)
                state.topCategories.forEach { (name, amount) ->
                    Text("· $name: ${formatMoney(amount)}", style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                state.cards.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text(
                    "${state.subscriptionCount} suscripciones · ${formatMoney(state.subscriptionsMonthly)} al mes",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (BuildConfig.DEBUG) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = viewModel::loadSample) { Text("Cargar muestra") }
                    OutlinedButton(onClick = viewModel::clear) { Text("Vaciar") }
                }
            }
        }
    }
}

/** Para colocarla en una LazyColumn con la misma animacion de entrada que lo demas. */
@Composable
fun LocalDataPreviewItem(index: Int) = LocalDataPreviewCard(Modifier.appear(index))
