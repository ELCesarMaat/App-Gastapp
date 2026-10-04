package com.binc.gastapp.ui.update

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binc.gastapp.core.remote.AppLatestVersionDto
import com.binc.gastapp.update.AppUpdater
import com.binc.gastapp.update.UpdateCheck
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** El dialogo de actualizacion. [Hidden] = no se muestra nada. */
sealed interface UpdateUiState {
    data object Hidden : UpdateUiState
    data class Available(val latest: AppLatestVersionDto) : UpdateUiState

    /** Falta el permiso de instalar apps desconocidas: se explica y se manda a darlo. */
    data class NeedsPermission(val latest: AppLatestVersionDto) : UpdateUiState
    data class Downloading(val latest: AppLatestVersionDto, val progress: Float?) : UpdateUiState
    data class Failed(val latest: AppLatestVersionDto, val message: String) : UpdateUiState
}

/**
 * CheckForAppUpdate de App.xaml.cs. Vive en la actividad: lo usan la raiz de la app (el
 * dialogo y la revision al abrir) y Ajustes ("Buscar actualizaciones").
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updater: AppUpdater,
) : ViewModel() {

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Hidden)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private val _checking = MutableStateFlow(false)
    /** Para el indicador de Ajustes mientras se busca a mano. */
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    private var checkedOnStart = false
    private var download: Job? = null

    val versionText: String get() = updater.installedVersion.let { "Versión ${it.name} (${it.code})" }

    /** Al abrir la app, una vez por proceso. Si falla no se dice nada. */
    fun checkOnStart() {
        if (checkedOnStart) return
        checkedOnStart = true
        viewModelScope.launch {
            (updater.check() as? UpdateCheck.Available)?.let { offer(it.latest) }
        }
    }

    /** "Buscar actualizaciones" de Ajustes. [onMessage] recibe el resultado si no hay dialogo. */
    fun checkNow(onMessage: (String) -> Unit) {
        if (_checking.value) return
        _checking.value = true
        viewModelScope.launch {
            when (val result = updater.check()) {
                is UpdateCheck.Available -> offer(result.latest)
                UpdateCheck.UpToDate -> onMessage("Ya tienes la versión más reciente.")
                UpdateCheck.Failed -> onMessage("No se pudo buscar actualizaciones. Revisa tu conexión.")
            }
            _checking.value = false
        }
    }

    /** "Actualizar": sin permiso lo pide; con permiso baja el APK y abre el instalador. */
    fun update() {
        val latest = currentLatest() ?: return
        if (!updater.canInstall()) {
            _state.value = UpdateUiState.NeedsPermission(latest)
            return
        }
        if (download?.isActive == true) return
        _state.value = UpdateUiState.Downloading(latest, null)
        download = viewModelScope.launch {
            try {
                val apk = updater.download(latest) { progress -> _state.value = UpdateUiState.Downloading(latest, progress) }
                _state.value = UpdateUiState.Hidden
                updater.install(apk)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = UpdateUiState.Failed(latest, "No se pudo descargar la actualización. Intenta más tarde.")
            }
        }
    }

    fun installPermissionIntent(): Intent = updater.installPermissionIntent()

    /** Al volver de los ajustes del sistema: si ya dio el permiso, sigue solo. */
    fun onReturnedFromPermission() {
        if (_state.value is UpdateUiState.NeedsPermission && updater.canInstall()) update()
    }

    /** "Despues". Si estaba descargando, se cancela. */
    fun dismiss() {
        download?.cancel()
        _state.value = UpdateUiState.Hidden
    }

    private fun offer(latest: AppLatestVersionDto) {
        if (_state.value is UpdateUiState.Downloading) return
        _state.value = UpdateUiState.Available(latest)
    }

    private fun currentLatest(): AppLatestVersionDto? = when (val s = _state.value) {
        is UpdateUiState.Available -> s.latest
        is UpdateUiState.NeedsPermission -> s.latest
        is UpdateUiState.Failed -> s.latest
        is UpdateUiState.Downloading -> s.latest
        UpdateUiState.Hidden -> null
    }
}
