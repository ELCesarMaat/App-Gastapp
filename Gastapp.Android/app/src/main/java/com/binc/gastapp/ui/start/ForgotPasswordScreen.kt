package com.binc.gastapp.ui.start

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binc.gastapp.R
import com.binc.gastapp.ui.components.ScreenMargin
import com.binc.gastapp.ui.components.rememberSecondsUntil
import com.binc.gastapp.ui.navigation.rememberStepBack
import com.binc.gastapp.ui.navigation.stepBackPose
import com.binc.gastapp.ui.theme.LocalStatusColors

/**
 * Recuperar contrasena (ForgetPasswordPage de MAUI). Al terminar, "Ir a iniciar sesion"
 * regresa al inicio con la hoja de login abierta y el correo escrito.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(
    onClose: () -> Unit,
    onGoToLogin: (email: String) -> Unit,
    viewModel: ForgotPasswordViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    val pose = rememberStepBack(enabled = state.step == ResetStep.Code || state.step == ResetStep.NewPassword) {
        viewModel.previous()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recover_password)) },
                navigationIcon = {
                    IconButton(onClick = { if (!viewModel.previous()) onClose() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenMargin, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.padding(horizontal = 4.dp)) {
                Text(stringResource(R.string.secure_reset), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.secure_reset_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedContent(
                targetState = state.step,
                transitionSpec = {
                    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (slideInHorizontally(tween(320)) { direction * it / 5 } + fadeIn(tween(220, delayMillis = 60))) togetherWith
                        (slideOutHorizontally(tween(260)) { -direction * it / 5 } + fadeOut(tween(120)))
                },
                label = "paso",
                modifier = Modifier.stepBackPose(pose),
            ) { step ->
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        when (step) {
                            ResetStep.Email -> EmailStep(state, viewModel)
                            ResetStep.Code -> CodeStep(state, viewModel)
                            ResetStep.NewPassword -> NewPasswordStep(state, viewModel)
                            ResetStep.Done -> DoneStep(onGoToLogin = { onGoToLogin(state.email.trim()) })
                        }
                        AnimatedVisibility(state.error != null) {
                            Text(state.error.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepHeader(title: String, description: String) {
    Text(title, style = MaterialTheme.typography.titleLarge)
    Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun BusyButton(text: String, busy: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !busy,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(12.dp))
            Text(stringResource(R.string.one_moment))
        } else {
            Text(text)
        }
    }
}

@Composable
private fun EmailStep(state: ForgotPasswordUiState, viewModel: ForgotPasswordViewModel) {
    StepHeader(stringResource(R.string.enter_your_email), stringResource(R.string.enter_your_email_hint))
    OutlinedTextField(
        value = state.email,
        onValueChange = viewModel::onEmailChange,
        label = { Text(stringResource(R.string.email)) },
        placeholder = { Text(stringResource(R.string.email_placeholder)) },
        singleLine = true,
        enabled = !state.busy,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { viewModel.sendCode() }),
        modifier = Modifier.fillMaxWidth(),
    )
    BusyButton(stringResource(R.string.send_verification_code), state.busy, viewModel::sendCode)
}

@Composable
private fun ColumnScope.CodeStep(state: ForgotPasswordUiState, viewModel: ForgotPasswordViewModel) {
    StepHeader(stringResource(R.string.verify_identity), stringResource(R.string.verify_identity_hint))
    VerificationCodeField(
        value = state.code,
        onValueChange = viewModel::onCodeChange,
        label = stringResource(R.string.verification_code),
        enabled = !state.busy,
        onDone = viewModel::verifyCode,
    )
    BusyButton(stringResource(R.string.verify_code), state.busy, viewModel::verifyCode)
    val secondsLeft = rememberSecondsUntil(state.resendAvailableAt)
    TextButton(
        onClick = viewModel::sendCode,
        enabled = secondsLeft <= 0 && !state.busy,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    ) {
        Text(if (secondsLeft > 0) stringResource(R.string.resend_code_in, secondsLeft) else stringResource(R.string.resend_code))
    }
}

@Composable
private fun NewPasswordStep(state: ForgotPasswordUiState, viewModel: ForgotPasswordViewModel) {
    StepHeader(stringResource(R.string.new_password), stringResource(R.string.new_password_hint))
    PasswordField(
        value = state.newPassword,
        onValueChange = viewModel::onNewPasswordChange,
        label = stringResource(R.string.new_password),
        placeholder = stringResource(R.string.min_6_characters),
        hidden = state.passwordHidden,
        onToggle = viewModel::togglePasswordVisibility,
        enabled = !state.busy,
    )
    PasswordField(
        value = state.confirmPassword,
        onValueChange = viewModel::onConfirmPasswordChange,
        label = stringResource(R.string.confirm_password),
        placeholder = stringResource(R.string.repeat_password),
        hidden = state.passwordHidden,
        onToggle = viewModel::togglePasswordVisibility,
        enabled = !state.busy,
        imeAction = ImeAction.Done,
        onDone = viewModel::resetPassword,
    )
    BusyButton(stringResource(R.string.change_password), state.busy, viewModel::resetPassword)
}

@Composable
private fun DoneStep(onGoToLogin: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedVisibility(true, enter = scaleIn() + fadeIn()) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = LocalStatusColors.current.ok.strong,
                modifier = Modifier.size(64.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.password_reset_done), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            stringResource(R.string.password_reset_done_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Row {
            Button(onClick = onGoToLogin, modifier = Modifier.heightIn(min = 52.dp)) { Text(stringResource(R.string.go_to_sign_in)) }
        }
    }
}
