package com.larkplayer.core.common

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/** Applies the selected app locale without restarting the process. */
object AppLocaleController {
    private const val DEFAULT_TAG = "es"

    /** Rejects blank/malformed tags before they reach the platform locale APIs. */
    fun normalizeTag(tag: String): String {
        val trimmed = tag.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() || it == '!' }) return DEFAULT_TAG
        val locale = runCatching { Locale.forLanguageTag(trimmed) }.getOrNull() ?: return DEFAULT_TAG
        if (locale.language.length < 2 || locale.language.any { !it.isLetter() }) return DEFAULT_TAG
        return locale.toLanguageTag()
    }

    /**
     * Android 13+ has per-app locales and propagates the change to every
     * activity immediately. Older versions update the resource configuration
     * and recreate the current activity.
     */
    fun apply(context: Context, tag: String) {
        val normalized = normalizeTag(tag)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val manager = context.getSystemService(LocaleManager::class.java) ?: return
            runCatching { manager.applicationLocales = LocaleList.forLanguageTags(normalized) }
            return
        }
        val locale = runCatching { Locale.forLanguageTag(normalized) }.getOrNull() ?: return
        Locale.setDefault(locale)
        val resources = context.resources
        val configuration = resources.configuration
        configuration.setLocale(locale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(configuration, resources.displayMetrics)
        (context as? Activity)?.let { runCatching { it.recreate() } }
    }
}
