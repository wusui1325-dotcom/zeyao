package com.wusper.zeyao.data

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale
import kotlinx.coroutines.runBlocking

/**
 * Applies the in-app language on cold start and when the user changes it.
 * DataStore is the source of truth; a tiny sidecar pref is used in
 * [attachBaseContext] so the first frame is already in the chosen locale.
 */
object AppLocale {
    private const val PREF = "zeyao_locale"
    private const val KEY = "tag"

    fun current(context: Context): AppLanguage =
        AppLanguage.fromTag(
            context.applicationContext
                .getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getString(KEY, null),
        )

    fun persist(context: Context, language: AppLanguage) {
        context.applicationContext
            .getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, language.tag)
            .apply()
    }

    fun wrap(base: Context, language: AppLanguage = current(base)): Context {
        val locale = localeOf(language)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }

    /**
     * If the sidecar is empty (upgrade from an older build), copy the
     * language already stored in DataStore so activities start correctly.
     */
    fun hydrateFromSettings(context: Context, settings: SettingsRepository) {
        val prefs = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (prefs.contains(KEY)) return
        val language = runCatching {
            runBlocking { settings.current().language }
        }.getOrDefault(AppLanguage.TRADITIONAL)
        persist(context, language)
    }

    fun apply(context: Context, language: AppLanguage) {
        persist(context, language)
        wrap(context, language)
        if (Build.VERSION.SDK_INT >= 33) {
            runCatching {
                context.applicationContext.getSystemService(LocaleManager::class.java)
                    ?.applicationLocales = LocaleList.forLanguageTags(language.tag)
            }
        }
    }

    fun localeOf(language: AppLanguage): Locale = Locale.forLanguageTag(language.tag)
}
