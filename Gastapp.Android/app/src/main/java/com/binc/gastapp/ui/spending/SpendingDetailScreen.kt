package com.binc.gastapp.ui.spending

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Splitscreen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binc.gastapp.R
import com.binc.gastapp.ui.category.categoryIcon
import com.binc.gastapp.ui.components.AppSnackbarHost
import com.binc.gastapp.ui.components.ListGroup
import com.binc.gastapp.ui.components.LocalAppMessages
import com.binc.gastapp.ui.components.ScreenMargin
import com.binc.gastapp.ui.components.SectionHeader
import com.binc.gastapp.ui.components.TonalIcon
import com.binc.gastapp.ui.components.TransparentListItemColors
import com.binc.gastapp.ui.components.appear
import com.binc.gastapp.ui.components.withExtra
import com.binc.gastapp.ui.format.categoryLabel
import com.binc.gastapp.ui.format.formatMoney
import com.binc.gastapp.ui.format.longDateWithYear
import com.binc.gastapp.ui.format.numericDate
import com.binc.gastapp.ui.format.rememberStrings
import com.binc.gastapp.ui.format.spendingDescription
import com.binc.gastapp.ui.format.timeText
import com.binc.gastapp.ui.theme.amountLarge

private data class DetailRow(val icon: ImageVector, val label: String, val value: String)

/**
 * Detalle del gasto (SpendingDetailPage de MAUI): pantalla apilada de solo lectura con
 * todos los datos y el boton Editar, que abre el formulario. Si el gasto se borra
 * (desde el formulario o desde otro lado), la pantalla se cierra sola.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpendingDetailScreen(
    onBack: () -> Unit,
    onSaved: (SpendingFormResult.Saved) -> Unit,
    viewModel: SpendingDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val messages = LocalAppMessages.current
    var editing by rememberSaveable { mutableStateOf(false) }
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()

    LaunchedEffect(state) { if (state is SpendingDetailState.Gone) onBack() }
    val spendingDeleted = stringResource(R.string.spending_deleted)

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.spending_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
                scrollBehavior = scroll,
            )
        },
        bottomBar = {
            if (state is SpendingDetailState.Shown) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Button(
                        onClick = { editing = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = ScreenMargin, vertical = 12.dp)
                            .heightIn(min = 52.dp),
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.edit))
                    }
                }
            }
        },
        snackbarHost = { AppSnackbarHost() },
    ) { padding ->
        val shown = state as? SpendingDetailState.Shown ?: return@Scaffold
        val detail = shown.detail
        val spending = detail.spending
        val description = spending.description?.takeIf { it.isNotBlank() }?.let { spendingDescription(it) } ?: " - "
        val strings = rememberStrings()
        val rows = buildList {
            add(DetailRow(Icons.Rounded.Payments, strings.get(R.string.detail_amount), formatMoney(spending.amount)))
            add(DetailRow(categoryIcon(detail.categoryName), strings.get(R.string.category), categoryLabel(strings, detail.categoryName)))
            add(DetailRow(Icons.Rounded.CreditCard, strings.get(R.string.payment_method), detail.paymentText))
            detail.cardText?.let { add(DetailRow(Icons.Rounded.CreditCard, strings.get(R.string.detail_card), it)) }
            detail.msiText?.let { add(DetailRow(Icons.Rounded.Splitscreen, strings.get(R.string.detail_msi), it)) }
            add(DetailRow(Icons.Rounded.CalendarToday, strings.get(R.string.detail_date), longDateWithYear(spending.date.toLocalDate())))
            add(DetailRow(Icons.Rounded.Schedule, strings.get(R.string.detail_time), timeText(spending.date.toLocalTime())))
            add(DetailRow(Icons.AutoMirrored.Rounded.Notes, strings.get(R.string.detail_description), description))
        }

        LazyColumn(
            contentPadding = padding.withExtra(top = 8.dp, bottom = 24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "cabecera") { DetailHeader(detail, Modifier.appear(0)) }
            if (detail.isCardPurchase) {
                item(key = "nota") { CardPurchaseNote(Modifier.appear(1)) }
            }
            item(key = "titulo") { SectionHeader(stringResource(R.string.detail_section), modifier = Modifier.appear(2)) }
            item(key = "datos") {
                ListGroup(rows, modifier = Modifier.appear(3)) { row ->
                    ListItem(
                        colors = TransparentListItemColors,
                        leadingContent = { TonalIcon(row.icon) },
                        overlineContent = { Text(row.label) },
                        headlineContent = { Text(row.value) },
                    )
                }
            }
        }
    }

    if (editing) {
        SpendingFormSheet(
            request = SpendingFormRequest.Edit(viewModel.spendingId),
            onDismiss = { editing = false },
            onResult = { result ->
                editing = false
                when (result) {
                    is SpendingFormResult.Saved -> onSaved(result)
                    // El aviso con "Deshacer" lo muestra la pantalla de abajo: esta se cierra.
                    is SpendingFormResult.Deleted -> messages.showUndo(spendingDeleted) { viewModel.restore(result.spendingId) }
                }
            },
        )
    }
}

@Composable
private fun DetailHeader(detail: SpendingDetail, modifier: Modifier = Modifier) {
    val spending = detail.spending
    Card(
        modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenMargin, vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TonalIcon(
                    if (detail.isCardPayment) Icons.Rounded.Payments else categoryIcon(detail.categoryName),
                    containerColor = MaterialTheme.colorScheme.primary,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    spending.title.ifBlank { categoryLabel(detail.categoryName) },
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text("-${formatMoney(spending.amount)}", style = amountLarge)
            Text(
                stringResource(
                    R.string.detail_header,
                    categoryLabel(detail.categoryName),
                    numericDate(spending.date.toLocalDate()),
                    timeText(spending.date.toLocalTime()),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun CardPurchaseNote(modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenMargin + 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.size(12.dp))
        Text(
            stringResource(R.string.detail_card_purchase_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
