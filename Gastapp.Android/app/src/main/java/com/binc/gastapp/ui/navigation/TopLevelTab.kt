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
import androidx.compose.ui.graphics.vector.ImageVector

/** Pestanas de la barra inferior, en el orden de MAUI. */
enum class TopLevelTab(val title: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Summary("Resumen", Icons.Outlined.CalendarMonth, Icons.Rounded.CalendarMonth),
    Savings("Ahorros", Icons.Outlined.Savings, Icons.Rounded.Savings),
    Profile("Perfil", Icons.Outlined.Person, Icons.Rounded.Person),
    Settings("Ajustes", Icons.Outlined.Settings, Icons.Rounded.Settings),
}
