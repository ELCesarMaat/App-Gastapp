package com.binc.gastapp.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Settings
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.binc.gastapp.R

/** Pestanas de la barra inferior, en el orden de MAUI. */
enum class TopLevelTab(@StringRes val title: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    Summary(R.string.tab_summary, Icons.Outlined.CalendarMonth, Icons.Rounded.CalendarMonth),
    Savings(R.string.tab_savings, Icons.Outlined.Savings, Icons.Rounded.Savings),
    Profile(R.string.tab_profile, Icons.Outlined.Person, Icons.Rounded.Person),
    Settings(R.string.tab_settings, Icons.Outlined.Settings, Icons.Rounded.Settings),
}
