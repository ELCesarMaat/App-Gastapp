package com.binc.gastapp.ui.start

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binc.gastapp.domain.model.IncomeTypes
import com.binc.gastapp.ui.components.IncomeTypeSelector
import com.binc.gastapp.ui.components.MonthDaySelector
import com.binc.gastapp.ui.components.ScreenMargin
import com.binc.gastapp.ui.components.WeekDaySelector
import com.binc.gastapp.ui.components.rememberSecondsUntil
import com.binc.gastapp.ui.format.MexicoLocale
import com.binc.gastapp.ui.format.filterAmountInput
import com.binc.gastapp.ui.navigation.rememberStepBack
import com.binc.gastapp.ui.navigation.stepBackPose
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Asistente de registro (WizardRegister de MAUI): barra de progreso, un paso a la vez y
 * atras predictivo entre pasos. En el primer paso, atras cierra la pantalla (el
 * borrador se queda guardado).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onClose: () -> Unit,
    onOpenLegal: () -> Unit,
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }
    LaunchedEffect(state.welcomeName) {
        state.welcomeName?.let { Toast.makeText(context, "Bienvenido $it", Toast.LENGTH_SHORT).show() }
    }

    // Atras predictivo entre pasos: el paso se encoge y se recorre mientras el dedo arrastra.
    val pose = rememberStepBack(enabled = state.stepIndex > 0) { viewModel.previous() }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            "Paso ${state.stepIndex + 1} de ${state.stepCount}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { if (!viewModel.previous()) onClose() }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Regresar")
                        }
                    },
                )
                val progress by animateFloatAsState(state.progress, tween(400), label = "progreso")
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScreenMargin),
                )
            }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(
                    Modifier
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = ScreenMargin, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Button(
                        onClick = viewModel::next,
                        enabled = state.canContinue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                    ) {
                        if (state.busy) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(12.dp))
                            Text(if (state.isLastStep) "Creando cuenta…" else "Un momento…")
                        } else {
                            Text(if (state.isLastStep) "Crear cuenta" else "Continuar")
                        }
                    }
                    PrivacyNoticeLink(onClick = onOpenLegal)
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        AnimatedContent(
            targetState = state.step,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                val direction = if (forward) 1 else -1
                (slideInHorizontally(tween(320)) { direction * it / 5 } + fadeIn(tween(220, delayMillis = 60))) togetherWith
                    (slideOutHorizontally(tween(260)) { -direction * it / 5 } + fadeOut(tween(120)))
            },
            label = "paso",
            modifier = Modifier
                .padding(padding)
                .stepBackPose(pose),
        ) { step ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ScreenMargin + 4.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(step.title, style = MaterialTheme.typography.headlineSmall)
                Text(
                    step.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                when (step) {
                    RegisterStep.Account -> AccountStep(state, viewModel)
                    RegisterStep.EmailCode -> EmailCodeStep(state, viewModel)
                    RegisterStep.Name -> NameStep(state, viewModel)
                    RegisterStep.BirthDate -> BirthDateStep(state, viewModel)
                    RegisterStep.Salary -> SalaryStep(state, viewModel)
                }
            }
        }
    }
}

@Composable
private fun AccountStep(state: RegisterUiState, viewModel: RegisterViewModel) {
    OutlinedTextField(
        value = state.email,
        onValueChange = viewModel::onEmailChange,
        label = { Text("Correo electrónico") },
        placeholder = { Text("nombre@correo.com") },
        singleLine = true,
        isError = state.emailError != null,
        supportingText = { Text(state.emailError ?: "Usa un correo que revises con frecuencia.") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.confirmEmail,
        onValueChange = viewModel::onConfirmEmailChange,
        label = { Text("Confirma tu correo electrónico") },
        placeholder = { Text("Repite tu correo") },
        singleLine = true,
        isError = state.confirmEmailError != null,
        supportingText = state.confirmEmailError?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    PasswordField(
        value = state.password,
        onValueChange = viewModel::onPasswordChange,
        label = "Contraseña",
        hidden = state.passwordHidden,
        onToggle = viewModel::togglePasswordVisibility,
        error = state.passwordError,
        supportingText = PasswordHint,
        imeAction = ImeAction.Done,
        onDone = viewModel::next,
    )
}

private const val PasswordHint = "De 6 a 20 caracteres, con una mayúscula, un número y un carácter especial."

@Composable
private fun EmailCodeStep(state: RegisterUiState, viewModel: RegisterViewModel) {
    Text(
        if (state.email.isBlank()) "Te enviamos un código a tu correo."
        else "Te enviamos un código de 6 dígitos a ${state.email.trim()}.",
        style = MaterialTheme.typography.bodyLarge,
    )
    VerificationCodeField(
        value = state.emailCode,
        onValueChange = viewModel::onEmailCodeChange,
        label = "Código de 6 dígitos",
        enabled = !state.busy,
        error = state.emailCodeError,
        onDone = viewModel::next,
    )
    AnimatedVisibility(state.busy) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Text("Un momento…", style = MaterialTheme.typography.bodySmall)
        }
    }
    val secondsLeft = rememberSecondsUntil(state.resendAvailableAt)
    TextButton(onClick = viewModel::resendCode, enabled = secondsLeft <= 0 && !state.busy) {
        Text(if (secondsLeft > 0) "Reenviar código en ${secondsLeft}s" else "Reenviar código")
    }
    Text(
        "Revisa tu bandeja de entrada y la carpeta de spam. El código expira en 15 minutos.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun NameStep(state: RegisterUiState, viewModel: RegisterViewModel) {
    OutlinedTextField(
        value = state.name,
        onValueChange = viewModel::onNameChange,
        label = { Text("Tu nombre") },
        placeholder = { Text("¿Cómo quieres que te llamemos?") },
        singleLine = true,
        isError = state.nameError != null,
        supportingText = { Text(state.nameError ?: "Este nombre aparecerá en tu perfil y mensajes principales.") },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
}

private val LongDate = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", MexicoLocale)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthDateStep(state: RegisterUiState, viewModel: RegisterViewModel) {
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    val range = viewModel.birthDateRange

    // Campo de solo lectura que abre el selector; toda la fila responde al toque.
    Box {
        OutlinedTextField(
            value = state.birthDate?.format(LongDate).orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Fecha de nacimiento") },
            placeholder = { Text("Selecciona día, mes y año.") },
            trailingIcon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            Modifier
                .matchParentSize()
                .semantics { contentDescription = "Elegir fecha de nacimiento" }
                .clickable { pickerOpen = true },
        )
    }

    if (pickerOpen) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.birthDate?.toUtcMillis(),
            initialDisplayedMonthMillis = (state.birthDate ?: range.endInclusive.withDayOfYear(1)).toUtcMillis(),
            yearRange = range.start.year..range.endInclusive.year,
            initialDisplayMode = DisplayMode.Input,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis.toLocalDate() in range
                override fun isSelectableYear(year: Int): Boolean = year in range.start.year..range.endInclusive.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickerOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { viewModel.onBirthDateChange(it.toLocalDate()) }
                        pickerOpen = false
                    },
                    enabled = pickerState.selectedDateMillis != null,
                ) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { pickerOpen = false }) { Text("Cancelar") } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun SalaryStep(state: RegisterUiState, viewModel: RegisterViewModel) {
    Text(
        "Define tu frecuencia de ingreso, el día de pago y cuánto quieres ahorrar.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Text("Frecuencia de ingreso", style = MaterialTheme.typography.titleSmall)
    IncomeTypeSelector(state.incomeTypeId, viewModel::onIncomeTypeChange)

    if (state.incomeTypeId == IncomeTypes.WEEKLY) {
        Text("Selecciona el día de pago", style = MaterialTheme.typography.titleSmall)
        WeekDaySelector(state.weekPayDay, viewModel::onWeekPayDayChange)
    } else {
        Text("Selecciona día(s) de pago", style = MaterialTheme.typography.titleSmall)
        Text(
            "Quincenal: hasta 2 días. Mensual: solo 1 día.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MonthDaySelector(state.monthPayDays, viewModel::onMonthPayDayToggle)
    }

    Spacer(Modifier.height(4.dp))
    OutlinedTextField(
        value = state.salaryText,
        onValueChange = { text -> filterAmountInput(text)?.let(viewModel::onSalaryChange) },
        label = { Text("Tus ingresos por período") },
        placeholder = { Text("0.00") },
        prefix = { Text("$") },
        singleLine = true,
        isError = state.salaryError != null,
        supportingText = state.salaryError?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.percentSaveText,
        onValueChange = { text -> filterAmountInput(text)?.let(viewModel::onPercentSaveChange) },
        label = { Text("Porcentaje de ahorro") },
        placeholder = { Text("10") },
        suffix = { Text("%") },
        singleLine = true,
        isError = state.percentSaveError != null,
        supportingText = { Text(state.percentSaveError ?: state.savingText ?: "Puedes cambiarlo después en tu perfil.") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
    if (state.askPasswordAgain) {
        PasswordField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = "Contraseña",
            hidden = state.passwordHidden,
            onToggle = viewModel::togglePasswordVisibility,
            error = state.passwordError,
            supportingText = "Por seguridad no guardamos tu contraseña: escríbela otra vez.",
            imeAction = ImeAction.Done,
        )
    }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
