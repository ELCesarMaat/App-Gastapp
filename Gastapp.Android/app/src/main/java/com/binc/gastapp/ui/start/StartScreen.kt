package com.binc.gastapp.ui.start

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binc.gastapp.BuildConfig
import com.binc.gastapp.R
import com.binc.gastapp.ui.components.ScreenMargin
import com.binc.gastapp.ui.components.appear
import com.binc.gastapp.ui.components.scaleOnPress
import com.binc.gastapp.ui.legal.LegalDocumentId
import com.binc.gastapp.ui.theme.Baloo
import kotlinx.coroutines.launch

/**
 * Pantalla de inicio (StartPage de MAUI): iniciar sesion en una hoja, crear cuenta,
 * olvide mi contrasena y privacidad y legal.
 *
 * @param message aviso arriba de los botones (sesion vencida o revocada).
 * @param openLogin abre la hoja de inicio de sesion al entrar, con [loginEmail] escrito.
 * @param pendingLoginEmail correo con el que se acaba de cambiar la contrasena: abre la
 *   hoja con ese correo y se avisa con [onPendingLoginConsumed].
 * @param onOpenLegal abre Privacidad y legal; con un documento, directo en ese documento.
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
    onOpenLegal: (LegalDocumentId?) -> Unit,
    onClose: (() -> Unit)?,
    onUseSample: (() -> Unit)?,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sheetOpen by rememberSaveable { mutableStateOf(openLogin) }
    // Se salio de la hoja para leer un documento legal: al regresar se vuelve a abrir.
    var reopenSheet by rememberSaveable { mutableStateOf(false) }

    // Con el resume y no al recomponer: durante el atras predictivo esta pantalla ya se
    // dibuja debajo, y la hoja (una ventana aparte) taparia la que se esta cerrando.
    LifecycleResumeEffect(Unit) {
        if (reopenSheet) {
            reopenSheet = false
            sheetOpen = true
        }
        onPauseOrDispose { }
    }

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
                PrivacyNoticeLink(onClick = { onOpenLegal(null) }, modifier = Modifier.appear(6))
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
            // La agarradera se dibuja dentro del encabezado, sobre el degradado.
            dragHandle = null,
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
                onOpenLegal = { document ->
                    reopenSheet = true
                    close()
                    onOpenLegal(document)
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

/** Lleva a Privacidad y legal (el aviso y los terminos, dentro de la app). */
@Composable
fun PrivacyNoticeLink(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onClick,
        modifier = modifier.semantics {
            role = Role.Button
            contentDescription = "Abrir privacidad y legal"
        },
    ) {
        Text(
            "Aviso de privacidad y Términos",
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
    onOpenLegal: (LegalDocumentId) -> Unit,
) {
    val fieldShape = RoundedCornerShape(16.dp)
    // El texto del error se recuerda para que no se vacie mientras la tarjeta se cierra.
    var lastError by remember { mutableStateOf("") }
    if (state.error != null) lastError = state.error
    val loginInteraction = remember { MutableInteractionSource() }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .navigationBarsPadding(),
    ) {
        LoginSheetHeader()
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenMargin + 8.dp)
                .padding(top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.email,
                onValueChange = onEmailChange,
                label = { Text("Correo electrónico") },
                placeholder = { Text("tuemail@dominio.com") },
                leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null) },
                singleLine = true,
                enabled = !state.busy,
                shape = fieldShape,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                    autoCorrectEnabled = false,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .appear(2),
            )
            Column(Modifier.appear(3)) {
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
                    leadingIcon = Icons.Outlined.Lock,
                    shape = fieldShape,
                )
                TextButton(
                    onClick = onForgotPassword,
                    enabled = !state.busy,
                    modifier = Modifier.align(Alignment.End),
                ) { Text("¿Olvidaste tu contraseña?") }
            }
            AnimatedVisibility(
                visible = state.error != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                LoginErrorCard(lastError)
            }
            Button(
                onClick = onLogin,
                enabled = !state.busy,
                shape = fieldShape,
                interactionSource = loginInteraction,
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .scaleOnPress(loginInteraction)
                    .appear(4),
            ) {
                AnimatedContent(targetState = state.busy, label = "boton de entrar") { busy ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (busy) {
                            CircularProgressIndicator(
                                Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.size(12.dp))
                            Text("Entrando…", style = MaterialTheme.typography.titleMedium)
                        } else {
                            Text("Iniciar sesión", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.size(8.dp))
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = state.busy,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                ServerWakeHint()
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .appear(5),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    "¿Aún no tienes cuenta?",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
            }
            OutlinedButton(
                onClick = onRegister,
                enabled = !state.busy,
                shape = fieldShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .appear(5),
            ) {
                Icon(Icons.Outlined.PersonAddAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text("Crear una cuenta nueva")
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .appear(6),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.VerifiedUser,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    "Conexión segura · tus datos se respaldan en la nube",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            LegalFooter(onOpenLegal, Modifier.appear(6))
        }
    }
}

/**
 * Pie legal de la hoja: los terminos y el aviso de privacidad como enlaces dentro del
 * texto (abren su documento en la app) y la version instalada.
 */
@Composable
private fun LegalFooter(onOpenLegal: (LegalDocumentId) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val linkStyles = TextLinkStyles(
        style = SpanStyle(color = colors.primary, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline),
    )
    fun link(document: LegalDocumentId) = LinkAnnotation.Clickable(document.name, linkStyles) { onOpenLegal(document) }
    val text = buildAnnotatedString {
        append("Al iniciar sesión aceptas los ")
        withLink(link(LegalDocumentId.Terms)) { append("Términos y condiciones") }
        append(" y el ")
        withLink(link(LegalDocumentId.Privacy)) { append("Aviso de privacidad") }
        append(" de Gastapp.")
    }
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            "Gastapp ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = colors.outline,
        )
    }
}

/**
 * Encabezado de la hoja: degradado de marca, la agarradera,
 * el logo con un halo que respira y el saludo.
 */
@Composable
private fun LoginSheetHeader() {
    val colors = MaterialTheme.colorScheme
    val halo = rememberInfiniteTransition(label = "halo")
    val haloScale by halo.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "escala del halo",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(colors.primaryContainer.copy(alpha = 0.7f), Color.Transparent))),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenMargin + 8.dp)
                .padding(top = 12.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(width = 32.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(colors.onSurfaceVariant.copy(alpha = 0.4f)),
            )
            Spacer(Modifier.height(20.dp))
            Box(Modifier.appear(0), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(92.dp)
                        .graphicsLayer {
                            scaleX = haloScale
                            scaleY = haloScale
                        }
                        .clip(CircleShape)
                        .background(colors.primary.copy(alpha = 0.12f)),
                )
                Box(
                    Modifier
                        .size(72.dp)
                        .shadow(6.dp, CircleShape)
                        .clip(CircleShape)
                        .background(colorResource(R.color.ic_launcher_background)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.mipmap.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.size(108.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "¡Bienvenido de vuelta!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.appear(1),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Inicia sesión para seguir con tus finanzas",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.appear(1),
            )
        }
    }
}

@Composable
private fun LoginErrorCard(message: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Render apaga el API cuando no se usa; el primer login puede tardar. */
@Composable
private fun ServerWakeHint() {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("El servidor puede tardar un minuto en despertar.", style = MaterialTheme.typography.bodySmall)
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
    leadingIcon: ImageVector? = null,
    shape: Shape = OutlinedTextFieldDefaults.shape,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null) } },
        singleLine = true,
        enabled = enabled,
        shape = shape,
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
