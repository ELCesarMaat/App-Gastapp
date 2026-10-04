package com.binc.gastapp.ui.start

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * Segundos entre un codigo y el siguiente (registro y recuperar contrasena). El API
 * rechaza otro antes de este tiempo (CodeRequestLimiter), y el que ya se mando sigue
 * sirviendo, asi que la app tampoco lo ofrece.
 */
const val CodeResendCooldownSeconds = 60L

private val CodeTextStyle = TextStyle(fontSize = 24.sp, letterSpacing = 8.sp, textAlign = TextAlign.Center)

/**
 * Campo del codigo de 6 digitos que llega por correo. El ejemplo "000000" lleva el mismo
 * estilo que lo tecleado (grande, espaciado y centrado): con el estilo por defecto quedaba
 * chico y a la izquierda mientras el cursor estaba al centro.
 */
@Composable
fun VerificationCodeField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    error: String? = null,
    onDone: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text("000000", style = CodeTextStyle, modifier = Modifier.fillMaxWidth()) },
        singleLine = true,
        enabled = enabled,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        textStyle = CodeTextStyle,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier.fillMaxWidth(),
    )
}
