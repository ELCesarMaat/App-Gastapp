package com.binc.gastapp.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binc.gastapp.R

/** El dialogo "Nueva versión disponible" y su descarga. No pinta nada si no hay actualizacion. */
@Composable
fun UpdatePrompt(viewModel: UpdateViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Volvio de dar (o no) el permiso de instalar apps desconocidas.
    LifecycleResumeEffect(state is UpdateUiState.NeedsPermission) {
        viewModel.onReturnedFromPermission()
        onPauseOrDispose { }
    }

    when (val s = state) {
        UpdateUiState.Hidden -> Unit

        is UpdateUiState.Available -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            icon = { Icon(Icons.Rounded.SystemUpdate, contentDescription = null) },
            title = { Text(stringResource(R.string.update_available_title)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                ) {
                    Text(stringResource(R.string.update_available_text, s.latest.versionName))
                    s.latest.releaseNotes.trim().takeIf { it.isNotEmpty() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::update) { Text(stringResource(R.string.update)) } },
            dismissButton = { TextButton(onClick = viewModel::dismiss) { Text(stringResource(R.string.later)) } },
        )

        is UpdateUiState.NeedsPermission -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            icon = { Icon(Icons.Rounded.SystemUpdate, contentDescription = null) },
            title = { Text(stringResource(R.string.update_permission_title)) },
            text = {
                Text(stringResource(R.string.update_permission_text))
            },
            confirmButton = {
                TextButton(onClick = { context.startActivity(viewModel.installPermissionIntent()) }) { Text(stringResource(R.string.open_settings)) }
            },
            dismissButton = { TextButton(onClick = viewModel::dismiss) { Text(stringResource(R.string.later)) } },
        )

        is UpdateUiState.Downloading -> AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnClickOutside = false),
            icon = { Icon(Icons.Rounded.SystemUpdate, contentDescription = null) },
            title = { Text(stringResource(R.string.update_downloading)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val progress = s.progress
                    if (progress == null) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(stringResource(R.string.update_version_name, s.latest.versionName))
                    } else {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        Text(stringResource(R.string.update_version_progress, s.latest.versionName, (progress * 100).toInt()))
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = viewModel::dismiss) { Text(stringResource(R.string.cancel)) } },
        )

        is UpdateUiState.Failed -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            icon = { Icon(Icons.Rounded.ErrorOutline, contentDescription = null) },
            title = { Text(stringResource(R.string.update_failed_title)) },
            text = { Text(s.message) },
            confirmButton = { TextButton(onClick = viewModel::update) { Text(stringResource(R.string.retry)) } },
            dismissButton = { TextButton(onClick = viewModel::dismiss) { Text(stringResource(R.string.close)) } },
        )
    }
}
