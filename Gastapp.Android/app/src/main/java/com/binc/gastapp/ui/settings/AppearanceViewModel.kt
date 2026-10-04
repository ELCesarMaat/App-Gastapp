package com.binc.gastapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binc.gastapp.data.prefs.SettingsStore
import com.binc.gastapp.data.prefs.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Tema elegido en Ajustes. */
data class Appearance(val themeMode: ThemeMode, val dynamicColor: Boolean)

/**
 * Apariencia de la actividad. null mientras se lee DataStore: el splash se queda hasta
 * entonces para no pintar un instante con el tema equivocado.
 */
@HiltViewModel
class AppearanceViewModel @Inject constructor(settingsStore: SettingsStore) : ViewModel() {
    val appearance: StateFlow<Appearance?> = settingsStore.settings
        .map { Appearance(it.themeMode, it.dynamicColor) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
