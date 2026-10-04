package com.binc.gastapp.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * DataStore en memoria. Lo que se prueba es la logica de los stores (llaves, valores
 * por defecto, caducidad); la persistencia es de la libreria. Ademas su almacenamiento
 * por archivo no sirve en la JVM de Windows: renombra el .tmp encima del archivo y
 * Windows no lo deja (en Android si funciona).
 */
class InMemoryDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}
