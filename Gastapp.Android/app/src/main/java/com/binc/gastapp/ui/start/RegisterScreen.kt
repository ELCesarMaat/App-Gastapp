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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binc.gastapp.R
import com.binc.gastapp.domain.model.IncomeTypes
import com.binc.gastapp.ui.components.IncomeTypeSelector
import com.binc.gastapp.ui.components.MonthDaySelector
import com.binc.gastapp.ui.components.ScreenMargin
import com.binc.gastapp.ui.components.WeekDaySelector
import com.binc.gastapp.ui.components.rememberSecondsUntil
import com.binc.gastapp.ui.format.amountPlaceholder
import com.binc.gastapp.ui.format.currencySymbol
import com.binc.gastapp.ui.format.dayMonthYear
import com.binc.gastapp.ui.format.filterAmountInput
import com.binc.gastapp.ui.format.rememberStrings
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
        state.welcomeName?.let { Toast.makeText(context, context.getString(R.string.welcome_name, it), Toast.LENGTH_SHORT).show() }
    }

    // Atras predictivo entre pasos: el paso se encoge y se recorre mientras el dedo arrastra.
    val pose = rememberStepBack(enabled = state.stepIndex > 0) { viewModel.previous() }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.step_of, state.stepIndex + 1, state.stepCount),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { if (!viewModel.previous()) onClose() }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
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
                            Text(stringResource(if (state.isLastStep) R.string.creating_account else R.string.one_moment))
                        } else {
                            Text(stringResource(if (state.isLastStep) R.string.create_account else R.string.continue_label))
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
                Text(stringResource(step.title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(step.description),
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
        label = { Text(stringResource(R.string.email)) },
        placeholder = { Text(stringResource(R.string.email_placeholder)) },
        singleLine = true,
        isError = state.emailError != null,
        supportingText = { Text(stringResource(state.emailError ?: R.string.email_hint)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.confirmEmail,
        onValueChange = viewModel::onConfirmEmailChange,
        label = { Text(stringResource(R.string.confirm_email)) },
        placeholder = { Text(stringResource(R.string.confirm_email_placeholder)) },
        singleLine = true,
        isError = state.confirmEmailError != null,
        supportingText = state.confirmEmailError?.let { { Text(stringResource(it)) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    PasswordField(
        value = state.password,
        onValueChange = viewModel::onPasswordChange,
        label = stringResource(R.string.password),
        hidden = state.passwordHidden,
        onToggle = viewModel::togglePasswordVisibility,
        error = state.passwordError?.let { stringResource(it) },
        supportingText = stringResource(R.string.password_hint),
        imeAction = ImeAction.Done,
        onDone = viewModel::next,
    )
}

@Composable
private fun EmailCodeStep(state: RegisterUiState, viewModel: RegisterViewModel) {
    Text(
        if (state.email.isBlank()) stringResource(R.string.code_sent_generic)
        else stringResource(R.string.code_sent_to, state.email.trim()),
        style = MaterialTheme.typography.bodyLarge,
    )
    VerificationCodeField(
        value = state.emailCode,
        onValueChange = viewModel::onEmailCodeChange,
        label = stringResource(R.string.six_digit_code),
        enabled = !state.busy,
        error = state.emailCodeError,
        onDone = viewModel::next,
    )
    AnimatedVisibility(state.busy) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.one_moment), style = MaterialTheme.typography.bodySmall)
        }
    }
    val secondsLeft = rememberSecondsUntil(state.resendAvailableAt)
    TextButton(onClick = viewModel::resendCode, enabled = secondsLeft <= 0 && !state.busy) {
        Text(if (secondsLeft > 0) stringResource(R.string.resend_code_in, secondsLeft) else stringResource(R.string.resend_code))
    }
    Text(
        stringResource(R.string.code_check_inbox),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun NameStep(state: RegisterUiState, viewModel: RegisterViewModel) {
    OutlinedTextField(
        value = state.name,
        onValueChange = viewModel::onNameChange,
        label = { Text(stringResource(R.string.your_name)) },
        placeholder = { Text(stringResource(R.string.your_name_placeholder)) },
        singleLine = true,
        isError = state.nameError != null,
        supportingText = { Text(stringResource(state.nameError ?: R.string.your_name_hint)) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthDateStep(state: RegisterUiState, viewModel: RegisterViewModel) {
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    val range = viewModel.birthDateRange
    val pickDescription = stringResource(R.string.pick_birth_date)

    // Campo de solo lectura que abre el selector; toda la fila responde al toque.
    Box {
        OutlinedTextField(
            value = state.birthDate?.let(::dayMonthYear).orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.birth_date)) },
            placeholder = { Text(stringResource(R.string.birth_date_placeholder)) },
            trailingIcon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            Modifier
                .matchParentSize()
                .semantics { contentDescription = pickDescription }
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
                ) { Text(stringResource(R.string.accept)) }
            },
            dismissButton = { TextButton(onClick = { pickerOpen = false }) { Text(stringResource(R.string.cancel)) } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun SalaryStep(state: RegisterUiState, viewModel: RegisterViewModel) {
    Text(
        stringResource(R.string.salary_step_intro),
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(stringResource(R.string.income_frequency), style = MaterialTheme.typography.titleSmall)
    IncomeTypeSelector(state.incomeTypeId, viewModel::onIncomeTypeChange)

    if (state.incomeTypeId == IncomeTypes.WEEKLY) {
        Text(stringResource(R.string.select_payday), style = MaterialTheme.typography.titleSmall)
        WeekDaySelector(state.weekPayDay, viewModel::onWeekPayDayChange)
    } else {
        Text(stringResource(R.string.select_paydays), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.paydays_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MonthDaySelector(state.monthPayDays, viewModel::onMonthPayDayToggle)
    }

    Spacer(Modifier.height(4.dp))
    OutlinedTextField(
        value = state.salaryText,
        onValueChange = { text -> filterAmountInput(text)?.let(viewModel::onSalaryChange) },
        label = { Text(stringResource(R.string.income_per_period_register)) },
        placeholder = { Text(amountPlaceholder) },
        prefix = { Text(currencySymbol) },
        singleLine = true,
        isError = state.salaryError != null,
        supportingText = state.salaryError?.let { { Text(stringResource(it)) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.percentSaveText,
        onValueChange = { text -> filterAmountInput(text, decimals = 2)?.let(viewModel::onPercentSaveChange) },
        label = { Text(stringResource(R.string.savings_percentage)) },
        placeholder = { Text("10") },
        suffix = { Text("%") },
        singleLine = true,
        isError = state.percentSaveError != null,
        supportingText = {
            Text(
                state.percentSaveError?.let { stringResource(it) }
                    ?: state.savingText(rememberStrings())
                    ?: stringResource(R.string.percent_save_hint),
            )
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
    if (state.askPasswordAgain) {
        PasswordField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = stringResource(R.string.password),
            hidden = state.passwordHidden,
            onToggle = viewModel::togglePasswordVisibility,
            error = state.passwordError?.let { stringResource(it) },
            supportingText = stringResource(R.string.password_again_hint),
            imeAction = ImeAction.Done,
        )
    }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
