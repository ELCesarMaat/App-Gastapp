package com.binc.gastapp.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binc.gastapp.R
import com.binc.gastapp.backup.BackupCounts
import com.binc.gastapp.backup.BackupPreview
import com.binc.gastapp.backup.BackupReadResult
import com.binc.gastapp.backup.BackupRepository
import com.binc.gastapp.ui.format.Strings
import com.binc.gastapp.ui.format.dayMonthYear
import com.binc.gastapp.ui.format.timeText
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Un respaldo leido que espera la confirmacion del usuario. */
data class RestorePrompt(val preview: BackupPreview, val exportedText: String, val counts: BackupCounts)

data class BackupUiState(
    val busy: Boolean = false,
    val restore: RestorePrompt? = null,
)

/**
 * Exportar y restaurar respaldos desde Ajustes. Los archivos los elige el usuario con el
 * selector del sistema (CreateDocument / OpenDocument): la app no necesita permisos de
 * almacenamiento y el respaldo puede ir a Drive, a Descargas o a donde quiera.
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backups: BackupRepository,
    private val strings: Strings,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    /** "gastapp-respaldo-2026-10-03.json" */
    fun suggestedFileName(): String = strings.get(R.string.backup_file_name, LocalDate.now(clock).toString())

    fun export(uri: Uri, onMessage: (String) -> Unit) = work(onMessage) {
        val counts = withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri, "wt")?.use { backups.export(it) }
        } ?: return@work strings.get(R.string.backup_create_failed)
        strings.get(R.string.backup_saved, countsText(strings, counts))
    }

    /** Lee el archivo elegido; si es valido, pide confirmacion ([BackupUiState.restore]). */
    fun open(uri: Uri, onMessage: (String) -> Unit) = work(onMessage) {
        val result = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { backups.read(it) }
        } ?: return@work strings.get(R.string.backup_open_failed)
        when (result) {
            is BackupReadResult.Invalid -> result.message
            is BackupReadResult.Ok -> {
                val exported = result.preview.exportedAt.atZone(clock.zone)
                _state.update {
                    it.copy(
                        restore = RestorePrompt(
                            preview = result.preview,
                            exportedText = "${dayMonthYear(exported.toLocalDate())}, ${timeText(exported.toLocalTime())}",
                            counts = result.preview.counts,
                        ),
                    )
                }
                null
            }
        }
    }

    fun confirmRestore(onMessage: (String) -> Unit) {
        val prompt = _state.value.restore ?: return
        _state.update { it.copy(restore = null) }
        work(onMessage) { strings.get(R.string.backup_restored, countsText(strings, backups.restore(prompt.preview))) }
    }

    fun cancelRestore() {
        _state.update { it.copy(restore = null) }
    }

    /** Corre [block] con el indicador puesto; su texto (si hay) se le muestra al usuario. */
    private fun work(onMessage: (String) -> Unit, block: suspend () -> String?) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val message = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                strings.get(R.string.backup_failed, e.message ?: strings.get(R.string.unexpected_error))
            } finally {
                _state.update { it.copy(busy = false) }
            }
            message?.let(onMessage)
        }
    }
}

/** "120 gastos, 2 tarjetas y 3 suscripciones" */
internal fun countsText(strings: Strings, counts: BackupCounts): String = strings.get(
    R.string.backup_counts,
    strings.plural(R.plurals.count_spendings, counts.spendings),
    strings.plural(R.plurals.count_cards, counts.creditCards),
    strings.plural(R.plurals.count_subscriptions, counts.subscriptions),
)
