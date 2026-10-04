package com.binc.gastapp.ui.start

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binc.gastapp.BuildConfig
import com.binc.gastapp.R
import com.binc.gastapp.ui.components.ScreenMargin
import com.binc.gastapp.ui.components.appear
import com.binc.gastapp.ui.theme.Baloo
import kotlinx.coroutines.launch

/** El mismo aviso de privacidad que abre MAUI. */
const val PrivacyNoticeUrl = "https://www.privacypolicies.com/live/063d06df-a5ce-42a4-9513-86839a3aa87d"

/**
 * Pantalla de inicio (StartPage de MAUI): iniciar sesion en una hoja, crear cuenta,
 * olvide mi contrasena y aviso de privacidad.
 *
 * @param message aviso arriba de los botones (sesion vencida o revocada).
 * @param openLogin abre la hoja de inicio de sesion al entrar, con [loginEmail] escrito.
 * @param pendingLoginEmail correo con el que se acaba de cambiar la contrasena: abre la
 *   hoja con ese correo y se avisa con [onPendingLoginConsumed].
 * @param onClose si no es null, se puede volver a la app sin iniciar sesion (sesion vencida).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartScreen(
    message: String?,
    openLogin: Boolean,
    loginEmail: String,
    pendingLoginEmail: String?,
    onPendingLoginConsumed: () -> Unit,
    onRegister: () -> Unit,
    onForgotPassword: () -> Unit,
    onClose: (() -> Unit)?,
    onUseSample: (() -> Unit)?,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sheetOpen by rememberSaveable { mutableStateOf(openLogin) }
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(loginEmail) { viewModel.prefillEmail(loginEmail) }
    // Al volver de "Recuperar contrasena" con la contrasena nueva, se abre la hoja.
    LaunchedEffect(pendingLoginEmail) {
        if (pendingLoginEmail != null) {
            viewModel.useEmail(pendingLoginEmail)
            sheetOpen = true
            onPendingLoginConsumed()
        }
    }

    if (onClose != null) BackHandler(onBack = onClose)

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ScreenMargin + 8.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(1f))
                AppLogo(Modifier.appear(0))
                Spacer(Modifier.height(20.dp))
                Text(
                    "Gastapp",
                    style = MaterialTheme.typography.displayMedium.copy(fontFamily = Baloo),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.appear(1),
                )
                Text(
                    "Tus gastos, tarjetas y ahorros en un solo lugar.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.appear(2),
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.height(32.dp))

                if (message != null) {
                    NoticeCard(message, Modifier.appear(3))
                    Spacer(Modifier.height(16.dp))
                }

                Button(
                    onClick = { sheetOpen = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .appear(3),
                ) { Text("Iniciar sesión") }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onRegister,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .appear(4),
                ) { Text("Crear cuenta") }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onForgotPassword, modifier = Modifier.appear(5)) { Text("Olvidé mi contraseña") }
                if (onClose != null) {
                    TextButton(onClick = onClose, modifier = Modifier.appear(5)) { Text("Seguir sin sesión") }
                }
                if (BuildConfig.DEBUG && onUseSample != null) {
                    TextButton(onClick = onUseSample, modifier = Modifier.appear(6)) { Text("Entrar con datos de muestra (debug)") }
                }
                PrivacyNoticeLink(onClick = { uriHandler.openUri(PrivacyNoticeUrl) }, modifier = Modifier.appear(6))
            }

            if (onClose != null) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .systemBarsPadding()
                        .padding(8.dp),
                ) { Icon(Icons.Rounded.Close, contentDescription = "Seguir sin sesión") }
            }
        }
    }

    if (sheetOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        val close: () -> Unit = {
            scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) sheetOpen = false }
        }
        ModalBottomSheet(
            onDismissRequest = { if (!state.busy) sheetOpen = false },
            sheetState = sheetState,
        ) {
            LoginSheetContent(
                state = state,
                onEmailChange = viewModel::onEmailChange,
                onPasswordChange = viewModel::onPasswordChange,
                onTogglePassword = viewModel::togglePasswordVisibility,
                onLogin = viewModel::login,
                onForgotPassword = {
                    close()
                    onForgotPassword()
                },
                onRegister = {
                    close()
                    onRegister()
                },
            )
        }
    }
}

@Composable
private fun AppLogo(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(120.dp)
            .clip(CircleShape)
            .background(colorResource(R.color.ic_launcher_background)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(180.dp),
        )
    }
}

@Composable
private fun NoticeCard(message: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.Info, contentDescription = null)
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** "Consulta nuestro Aviso de privacidad", como en MAUI. */
@Composable
fun PrivacyNoticeLink(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onClick,
        modifier = modifier.semantics {
            role = Role.Button
            contentDescription = "Abrir el aviso de privacidad"
        },
    ) {
        Text(
            "Consulta nuestro Aviso de privacidad",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoginSheetContent(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onLogin: () -> Unit,
    onForgotPassword: () -> Unit,
    onRegister: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = ScreenMargin + 8.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Bienvenido de vuelta", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Inicia sesión para seguir con tus finanzas",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = { Text("Correo electrónico") },
            placeholder = { Text("tuemail@dominio.com") },
            singleLine = true,
            enabled = !state.busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        PasswordField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = "Contraseña",
            hidden = state.passwordHidden,
            onToggle = onTogglePassword,
            enabled = !state.busy,
            imeAction = ImeAction.Done,
            onDone = onLogin,
            placeholder = "Tu contraseña",
        )
        AnimatedVisibility(state.error != null) {
            Text(state.error.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        Button(
            onClick = onLogin,
            enabled = !state.busy,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
        ) {
            if (state.busy) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.size(12.dp))
                Text("Entrando…")
            } else {
                Text("Iniciar sesión")
            }
        }
        AnimatedVisibility(state.busy) {
            Text(
                "El servidor puede tardar un minuto en despertar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        TextButton(onClick = onForgotPassword, enabled = !state.busy, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("¿Olvidaste tu contraseña?")
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("¿Aún no tienes cuenta?", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onRegister, enabled = !state.busy) { Text("Créala aquí") }
        }
    }
}

/** Campo de contrasena con el boton de mostrar u ocultar. */
@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    hidden: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    error: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        enabled = enabled,
        isError = error != null,
        supportingText = (error ?: supportingText)?.let { { Text(it) } },
        visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        trailingIcon = {
            IconButton(onClick = onToggle) {
                Icon(
                    if (hidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                    contentDescription = if (hidden) "Mostrar contraseña" else "Ocultar contraseña",
                )
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}
