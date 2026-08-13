package com.rotiropi.pos_erpnext.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * A user-facing message decided outside a composable.
 *
 * ViewModels have no `Context`, and a `String` chosen inside one is frozen in whatever
 * language was current when the state was built. Carrying the resource id and its
 * arguments instead defers the lookup to the composable that renders it, so the message
 * follows the selected interface language.
 *
 * [Raw] exists because the same fields also carry text this app does not own — ERPNext
 * validation messages and error codes. Those are passed through verbatim rather than
 * translated, and having one type for both keeps the state contracts single-valued.
 */
sealed interface UiText {
    data class Resource(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Raw(val value: String) : UiText
}

fun uiText(@StringRes id: Int, vararg args: Any): UiText.Resource =
    UiText.Resource(id, args.toList())

@Composable
fun UiText.resolve(): String = when (this) {
    is UiText.Resource -> stringResource(id, *args.toTypedArray())
    is UiText.Raw -> value
}

/** For the XML surfaces and any other non-composable caller that already holds a Context. */
fun UiText.resolve(context: android.content.Context): String = when (this) {
    is UiText.Resource -> context.getString(id, *args.toTypedArray())
    is UiText.Raw -> value
}
