package com.rotiropi.pos_erpnext.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class PosLanguageTest {

    @Test
    fun unset_and_unknown_values_fall_back_to_indonesian() {
        assertEquals(PosLanguage.INDONESIAN, PosLanguage.parse(null))
        assertEquals(PosLanguage.INDONESIAN, PosLanguage.parse(""))
        assertEquals(PosLanguage.INDONESIAN, PosLanguage.parse("JAVANESE"))
        assertEquals(PosLanguage.INDONESIAN, PosLanguage.DEFAULT)
    }

    @Test
    fun stored_names_round_trip() {
        PosLanguage.entries.forEach { language ->
            assertEquals(language, PosLanguage.parse(language.name))
        }
    }

    @Test
    fun language_tags_resolve_including_the_legacy_indonesian_spelling() {
        assertEquals(PosLanguage.ENGLISH, PosLanguage.fromLanguageTags("en"))
        assertEquals(PosLanguage.ENGLISH, PosLanguage.fromLanguageTags("en-US"))
        assertEquals(PosLanguage.INDONESIAN, PosLanguage.fromLanguageTags("id-ID"))
        // Java's legacy Locale rewrites "id" to "in".
        assertEquals(PosLanguage.INDONESIAN, PosLanguage.fromLanguageTags("in-ID"))
        assertEquals(PosLanguage.ENGLISH, PosLanguage.fromLanguageTags("en-GB,id-ID"))
        assertEquals(PosLanguage.INDONESIAN, PosLanguage.fromLanguageTags(""))
        assertEquals(PosLanguage.INDONESIAN, PosLanguage.fromLanguageTags(null))
        assertEquals(PosLanguage.INDONESIAN, PosLanguage.fromLanguageTags("fr-FR"))
    }

    @Test
    fun tags_are_bcp_47_language_codes_not_enum_names() {
        assertEquals("id", PosLanguage.INDONESIAN.tag)
        assertEquals("en", PosLanguage.ENGLISH.tag)
    }

    @Test
    fun unknown_stored_language_reads_as_indonesian() {
        assertEquals(PosLanguage.INDONESIAN, ThemePreferences.parseLanguage(null))
        assertEquals(PosLanguage.INDONESIAN, ThemePreferences.parseLanguage("JAVANESE"))
        assertEquals(PosLanguage.ENGLISH, ThemePreferences.parseLanguage("ENGLISH"))
    }
}
