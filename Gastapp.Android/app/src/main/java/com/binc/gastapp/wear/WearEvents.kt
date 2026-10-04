package com.binc.gastapp.wear

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Lo que paso con el reloj sin que el usuario tocara nada (DevicesChanged / WearDeviceLinked de MAUI). */
sealed interface WearEvent {
    /** Cambio la lista de dispositivos: Ajustes la recarga. */
    data object DevicesChanged : WearEvent

    /** El reloj se vinculo solo, por Bluetooth: el dialogo de vincular muestra el exito. */
    data class Linked(val deviceName: String) : WearEvent
}

/**
 * Avisos del servicio del reloj a las pantallas abiertas. Si no hay pantalla escuchando
 * se pierden, y no pasa nada: Ajustes recarga la lista al abrirse.
 */
@Singleton
class WearEvents @Inject constructor() {
    private val _events = MutableSharedFlow<WearEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<WearEvent> = _events.asSharedFlow()

    fun emit(event: WearEvent) {
        _events.tryEmit(event)
    }
}
