package br.com.ryuukossei.gymprogress.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Campo numérico com botões − e +. Ao tocar no campo, todo o valor fica selecionado,
 * para digitar o novo número direto (aceita vírgula ou ponto como decimal).
 */
@Composable
fun NumberStepper(
    value: Double,
    onValueChange: (Double) -> Unit,
    step: Double,
    decimal: Boolean,
    modifier: Modifier = Modifier,
) {
    val format = { v: Double -> if (decimal) formatNumber(v) else v.toLong().toString() }
    var field by remember { mutableStateOf(TextFieldValue(format(value))) }
    var focused by remember { mutableStateOf(false) }
    // Logo após ganhar o foco, o toque tenta posicionar o cursor; ignoramos essa
    // primeira mudança só de seleção para manter o valor inteiro selecionado.
    var keepSelection by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // Mantém o texto alinhado ao valor externo (ex.: botões −/+ ou valor restaurado).
    LaunchedEffect(value) {
        if (parse(field.text, decimal) != value) {
            val text = format(value)
            field = TextFieldValue(text, selection = if (focused) TextRange(0, text.length) else TextRange(text.length))
        }
    }

    fun change(delta: Double) {
        val next = BigDecimal.valueOf(value).add(BigDecimal.valueOf(delta))
            .setScale(2, RoundingMode.HALF_UP).toDouble()
            .coerceAtLeast(0.0)
        onValueChange(next)
    }

    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(10.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { change(-step) }, modifier = Modifier.size(40.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text("−", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            BasicTextField(
                value = field,
                onValueChange = { newValue ->
                    if (keepSelection && newValue.text == field.text) {
                        keepSelection = false
                        return@BasicTextField
                    }
                    keepSelection = false
                    val cleaned = newValue.text.filter { it.isDigit() || (decimal && (it == ',' || it == '.')) }
                    field = newValue.copy(text = cleaned)
                    parse(cleaned, decimal)?.let(onValueChange)
                },
                singleLine = true,
                textStyle = TextStyle(
                    color = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier.onFocusChanged { state ->
                    if (state.isFocused && !focused) {
                        field = field.copy(selection = TextRange(0, field.text.length))
                        keepSelection = true
                    }
                    if (!state.isFocused && focused) {
                        // Ao sair do campo, mostra o valor válido atual (ex.: campo deixado vazio).
                        field = TextFieldValue(format(value))
                    }
                    focused = state.isFocused
                },
            )
        }
        TextButton(onClick = { change(step) }, modifier = Modifier.size(40.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text("+", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun parse(text: String, decimal: Boolean): Double? {
    if (text.isBlank()) return null
    return if (decimal) text.replace(',', '.').toDoubleOrNull() else text.toLongOrNull()?.toDouble()
}
