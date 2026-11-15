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
 *
 * During [android.app.Application.attachBaseContext] the Application is not
 * yet attached, so [Context.getApplicationContext] is null. Prefs must be
 * read from the [base][Context] passed into attach, never from a stored
 * Application instance, and never via a default argument that ignores that
 * Context.
 */
object AppLocale {
    private const val PREF = "zeyao_locale"
    private const val KEY = "tag"

    /**
     * SharedPreferences must be reached through [base] itself when the
     * Application is still attaching. [Context.getApplicationContext] is
     * used later, once it is non-null.
     */
    private fun prefsContext(context: Context): Context =
        context.applicationContext ?: context

    /**
     * Any failure reading the sidecar (including a still-null context) falls
     * back to Traditional Chinese so process start cannot crash.
     */
    internal fun languageOrDefault(readTag: () -> String?): AppLanguage =
        runCatching { AppLanguage.fromTag(readTag()) }
            .getOrDefault(AppLanguage.TRADITIONAL)

    fun current(context: Context): AppLanguage =
        languageOrDefault {
            prefsContext(context)
                .getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getString(KEY, null)
        }

    fun persist(context: Context, language: AppLanguage) {
        runCatching {
            prefsContext(context)
                .getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY, language.tag)
                .apply()
        }
    }

    fun wrap(context: Context): Context = wrap(context, current(context))

    fun wrap(context: Context, language: AppLanguage): Context {
        val locale = localeOf(language)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLocales(LocaleList(locale))
        return context.createConfigurationContext(config)
    }

    /**
     * If the sidecar is empty (upgrade from an older build), copy the
     * language already stored in DataStore so activities start correctly.
     */
    fun hydrateFromSettings(context: Context, settings: SettingsRepository) {
        val prefs = runCatching {
            prefsContext(context).getSharedPreferences(PREF, Context.MODE_PRIVATE)
        }.getOrNull() ?: return
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
                context.applicationContext?.getSystemService(LocaleManager::class.java)
                    ?.applicationLocales = LocaleList.forLanguageTags(language.tag)
            }
        }
    }

    fun localeOf(language: AppLanguage): Locale = Locale.forLanguageTag(language.tag)
}
