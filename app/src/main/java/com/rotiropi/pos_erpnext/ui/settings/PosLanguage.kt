package com.rotiropi.pos_erpnext.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * The interface languages a cashier can choose. Indonesian is the default and the
 * fallback: `values/strings.xml` holds Indonesian, so any locale without a better
 * match resolves to it, and the app opens in Indonesian even on an English device.
 *
 * [tag] is a BCP 47 language tag, not the enum name, because that is what
 * [LocaleListCompat.forLanguageTags] consumes.
 *
 * [label] is deliberately a literal in its own language rather than a string
 * resource. A cashier looking at an interface in a language they cannot read must
 * still be able to find their own language in this list, which a translated label
 * would prevent.
 */
enum class PosLanguage(val tag: String, val label: String) {
    INDONESIAN("id", "Bahasa Indonesia"),
    ENGLISH("en", "English"),
    ;

    companion object {
        val DEFAULT = INDONESIAN

        fun parse(value: String?): PosLanguage =
            entries.firstOrNull { it.name == value } ?: DEFAULT

        /** The language whose [tag] leads the given tag list, or the default if none does. */
        fun fromLanguageTags(tags: String?): PosLanguage {
            val first = tags?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
                ?: return DEFAULT
            val language = first.substringBefore('-').lowercase()
            // Java's legacy Locale rewrites "id" to "in", so both spellings reach here.
            val normalized = if (language == "in") "id" else language
            return entries.firstOrNull { it.tag == normalized } ?: DEFAULT
        }
    }
}

/**
 * Applies [language] as the per-app locale. On API 33 and above the platform stores it;
 * below that, AppCompat's `AppLocalesMetadataHolderService` with `autoStoreLocales` does,
 * which is why `minSdk 23` needs the AndroidX path rather than the platform API.
 */
fun applyPosLanguage(language: PosLanguage) {
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
}
