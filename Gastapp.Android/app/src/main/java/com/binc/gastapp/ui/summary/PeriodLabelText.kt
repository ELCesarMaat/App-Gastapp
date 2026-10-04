package com.binc.gastapp.ui.summary

import androidx.compose.runtime.Composable
import com.binc.gastapp.R
import com.binc.gastapp.domain.model.IncomeTypes
import com.binc.gastapp.ui.format.Strings
import com.binc.gastapp.ui.format.rememberStrings

/** "Quincena actual", "Semana anterior", "Hace 2 meses", "Este mes", "Periodo personalizado". */
fun PeriodLabel.text(strings: Strings): String = when (this) {
    is PeriodLabel.Paid -> {
        val (current, previous, ago) = when (incomeTypeId) {
            IncomeTypes.WEEKLY -> Triple(R.string.period_current_week, R.string.period_previous_week, R.plurals.period_weeks_ago)
            IncomeTypes.BIWEEKLY -> Triple(R.string.period_current_half_month, R.string.period_previous_half_month, R.plurals.period_half_months_ago)
            else -> Triple(R.string.period_current_month, R.string.period_previous_month, R.plurals.period_months_ago)
        }
        when (offset) {
            0 -> strings.get(current)
            1 -> strings.get(previous)
            else -> strings.plural(ago, offset)
        }
    }
    is PeriodLabel.Preset -> strings.get(shortcut.label)
    PeriodLabel.Custom -> strings.get(R.string.period_custom)
}

@Composable
fun PeriodLabel.text(): String = text(rememberStrings())
