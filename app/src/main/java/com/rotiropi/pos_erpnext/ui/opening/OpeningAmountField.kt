package com.rotiropi.pos_erpnext.ui.opening

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.rotiropi.pos_erpnext.ui.theme.PosDimensions

internal fun normalizeOpeningAmountInput(input: String): String? {
    if (input.isEmpty()) return ""
    if (input.any { it != '.' && it != ',' && !it.isDigit() }) return null
    if (input.count { it == ',' } > 1) return null

    val commaIndex = input.indexOf(',')
    if (commaIndex >= 0) {
        val integer = input.substring(0, commaIndex)
        val fraction = input.substring(commaIndex + 1)
        if (fraction.any { !it.isDigit() }) return null
        if (integer.contains('.')) {
            if (!isValidGroupedInteger(integer)) return null
            return integer.filter { it != '.' } + "," + fraction
        }
        if (integer.isEmpty() || integer.any { !it.isDigit() }) return null
        return integer + "," + fraction
    }

    val dotCount = input.count { it == '.' }
    if (dotCount == 0) return input.takeIf { it.all(Char::isDigit) }
    if (dotCount == 1) {
        val integer = input.substringBefore('.')
        val fraction = input.substringAfter('.')
        if (integer.isEmpty() || integer.any { !it.isDigit() } || fraction.any { !it.isDigit() }) return null
        return if (isGroupedInteger(input)) integer + fraction else input
    }

    return input.takeIf { isValidGroupedInteger(input) }?.filter { it != '.' }
}

internal fun formatOpeningAmount(raw: String): String {
    if (raw.isEmpty()) return ""
    val separatorIndex = raw.indexOfFirst { it == '.' || it == ',' }
    val integer = if (separatorIndex >= 0) raw.substring(0, separatorIndex) else raw
    val fraction = if (separatorIndex >= 0) raw.substring(separatorIndex + 1) else null
    if (integer.isEmpty() || integer.any { !it.isDigit() }) return raw

    val grouped = buildString {
        integer.forEachIndexed { index, digit ->
            if (index > 0 && (integer.length - index) % 3 == 0) append('.')
            append(digit)
        }
    }
    return if (fraction != null) "$grouped,$fraction" else grouped
}

internal fun openingAmountOffsetMapping(raw: String): OffsetMapping {
    val separatorIndex = raw.indexOfFirst { it == '.' || it == ',' }
    val integerLength = if (separatorIndex >= 0) separatorIndex else raw.length
    val rawToTransformed = IntArray(raw.length + 1)
    val transformed = StringBuilder()
    rawToTransformed[0] = 0

    raw.substring(0, integerLength).forEachIndexed { index, digit ->
        transformed.append(digit)
        rawToTransformed[index + 1] = transformed.length
        if (index + 1 < integerLength && (integerLength - index - 1) % 3 == 0) {
            transformed.append('.')
        }
    }
    if (separatorIndex >= 0) {
        transformed.append(',')
        rawToTransformed[separatorIndex + 1] = transformed.length
        raw.substring(separatorIndex + 1).forEachIndexed { index, digit ->
            transformed.append(digit)
            rawToTransformed[separatorIndex + index + 2] = transformed.length
        }
    }

    return object : OffsetMapping {
        override fun originalToTransformed(offset: Int): Int =
            rawToTransformed[offset.coerceIn(0, raw.length)]

        override fun transformedToOriginal(offset: Int): Int {
            val target = offset.coerceIn(0, transformed.length)
            var original = 0
            for (candidate in rawToTransformed.indices) {
                if (rawToTransformed[candidate] > target) break
                original = candidate
            }
            return original
        }
    }
}

internal fun normalizeOpeningAmountEdit(
    current: TextFieldValue,
    proposed: TextFieldValue,
): TextFieldValue? {
    val normalized = normalizeOpeningAmountInput(proposed.text) ?: return null
    val zeroReplacement = if (isZeroAmount(current.text) && normalized.any(Char::isDigit) &&
        normalized.any { it != '0' }
    ) {
        val separatorIndex = normalized.indexOfFirst { it == '.' || it == ',' }
        val integer = if (separatorIndex >= 0) normalized.substring(0, separatorIndex) else normalized
        val fraction = if (separatorIndex >= 0) normalized.substring(separatorIndex) else ""
        integer.trimStart('0').ifEmpty { "0" } + fraction
    } else {
        normalized
    }
    val selection = if (isGroupedInput(proposed.text)) {
        TextRange(
            removeGroupingBefore(proposed.text, proposed.selection.start).coerceIn(0, zeroReplacement.length),
            removeGroupingBefore(proposed.text, proposed.selection.end).coerceIn(0, zeroReplacement.length),
        )
    } else {
        TextRange(
            proposed.selection.start.coerceIn(0, zeroReplacement.length),
            proposed.selection.end.coerceIn(0, zeroReplacement.length),
        )
    }
    return TextFieldValue(zeroReplacement, selection)
}

@Composable
internal fun OpeningAmountField(
    value: String,
    label: String,
    enabled: Boolean,
    isError: Boolean,
    supportingText: String?,
    editableStateDescription: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    var fieldValue by remember { mutableStateOf(initialFieldValue(value)) }
    var lastExternalValue by remember { mutableStateOf(value) }
    var zeroSuggestion by remember { mutableStateOf(isZeroAmount(value)) }

    LaunchedEffect(value) {
        if (value != lastExternalValue) {
            fieldValue = initialFieldValue(value)
            lastExternalValue = value
            zeroSuggestion = isZeroAmount(value)
        }
    }

    OutlinedTextField(
        value = fieldValue,
        onValueChange = { proposed ->
            val normalized = normalizeOpeningAmountEdit(fieldValue, proposed) ?: return@OutlinedTextField
            fieldValue = normalized
            lastExternalValue = normalized.text
            zeroSuggestion = false
            onValueChange(normalized.text)
        },
        label = { Text(label) },
        enabled = enabled,
        isError = isError,
        supportingText = supportingText?.let { message -> { Text(message) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
        visualTransformation = OpeningAmountVisualTransformation,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = PosDimensions.touchTarget)
            .testTag("opening-amount-$label")
            .onFocusChanged { focusState ->
                if (focusState.isFocused && zeroSuggestion && fieldValue.text == "0") {
                    fieldValue = fieldValue.copy(selection = TextRange(0, fieldValue.text.length))
                }
            }
            .semantics {
                editableStateDescription?.let { stateDescription = it }
            },
    )
}

private val OpeningAmountVisualTransformation = VisualTransformation { text ->
    TransformedText(
        text = androidx.compose.ui.text.AnnotatedString(formatOpeningAmount(text.text)),
        offsetMapping = openingAmountOffsetMapping(text.text),
    )
}

private fun initialFieldValue(value: String): TextFieldValue =
    if (isZeroAmount(value)) TextFieldValue("0") else TextFieldValue(value, TextRange(value.length))

private fun isZeroAmount(value: String): Boolean =
    value.isNotEmpty() && value.filter { it.isDigit() }.all { it == '0' }

private fun isGroupedInput(input: String): Boolean {
    val integer = input.substringBefore(',')
    return integer.contains('.') && isValidGroupedInteger(integer)
}

private fun isGroupedInteger(input: String): Boolean =
    input.substringBefore('.').length in 1..3 && input.substringAfter('.').length == 3 &&
        input.substringBefore('.').all(Char::isDigit) && input.substringAfter('.').all(Char::isDigit)

private fun isValidGroupedInteger(input: String): Boolean {
    val groups = input.split('.')
    return groups.firstOrNull()?.length in 1..3 &&
        groups.drop(1).all { it.length == 3 } &&
        groups.all { it.all(Char::isDigit) }
}

private fun removeGroupingBefore(input: String, offset: Int): Int =
    input.substring(0, offset.coerceIn(0, input.length)).count { it != '.' }
