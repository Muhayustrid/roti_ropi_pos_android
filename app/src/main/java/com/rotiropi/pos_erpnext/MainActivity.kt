package com.rotiropi.pos_erpnext

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.rotiropi.pos_erpnext.databinding.Task4RootBinding
import com.rotiropi.pos_erpnext.ui.Task4RootHost
import com.rotiropi.pos_erpnext.ui.posRequestedOrientation
import com.rotiropi.pos_erpnext.ui.settings.PosLanguage
import com.rotiropi.pos_erpnext.ui.settings.ThemePreferences
import com.rotiropi.pos_erpnext.ui.settings.applyPosLanguage

/**
 * An [AppCompatActivity] rather than a `ComponentActivity` because per-app language
 * selection on API 32 and below resolves locales through an AppCompat context. The
 * theme is already `Theme.Material3.DayNight.NoActionBar`, so this is a base-class
 * change, not a restyle.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var binding: Task4RootBinding
    private lateinit var task4RootHost: Task4RootHost

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = posRequestedOrientation(resources)
        applyStoredLanguage()
        enableEdgeToEdge()
        binding = Task4RootBinding.inflate(layoutInflater)
        setContentView(binding.root)
        task4RootHost = Task4RootHost(
            activity = this,
            application = application as MobilePosApplication,
            binding = binding,
        )
    }

    /**
     * With no locale applied, resource resolution would follow the device: an English
     * device would open in English. Indonesian is the product default regardless of the
     * device, so the stored choice is applied explicitly, including on first launch when
     * nothing has been applied yet.
     */
    private fun applyStoredLanguage() {
        val stored = ThemePreferences.from(this).read().language
        val applied = AppCompatDelegate.getApplicationLocales()
        if (applied.isEmpty || PosLanguage.fromLanguageTags(applied.toLanguageTags()) != stored) {
            applyPosLanguage(stored)
        }
    }
}
