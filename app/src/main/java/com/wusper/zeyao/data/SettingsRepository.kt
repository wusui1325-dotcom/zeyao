package com.wusper.zeyao.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Calendar

private val Context.settingsStore by preferencesDataStore(name = "zeyao_settings")

class SettingsRepository(private val context: Context) {
    val settings: Flow<AppSettings> = context.settingsStore.data
        .map { it.toSettings() }
        .distinctUntilChanged()

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsStore.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[ONBOARDED] = next.onboarded
            prefs[PROVIDER] = next.providerId
            prefs[INTERCEPT] = next.interceptEnabled
            prefs[PERSISTENT] = next.persistentEnabled
            prefs[SCHEDULE] = next.scheduleEnabled
            prefs[SCHED_START] = next.scheduleStartMinutes
            prefs[SCHED_END] = next.scheduleEndMinutes
            prefs[SCHED_DAYS] = next.scheduleDays
            prefs[TARGETS] = next.targetPackages.sorted().joinToString("\n")
            prefs[ACCENT] = next.accentColor
            prefs[THEME] = next.themeStyle.name
            prefs[TYPE] = next.typographyStyle.name
            prefs[ENTER_POS] = next.enterButtonPosition.name
            prefs[CONTINUE] = next.continueOnExit
        }
    }

    suspend fun setOnboarded(value: Boolean) = update { it.copy(onboarded = value) }
    suspend fun setProvider(id: String) = update { it.copy(providerId = id) }
    suspend fun setInterceptEnabled(value: Boolean) = update { it.copy(interceptEnabled = value) }
    suspend fun setPersistentEnabled(value: Boolean) = update { it.copy(persistentEnabled = value) }

    /**
     * Whether intercept should fire right now, given stored settings.
     * Does not check permissions.
     */
    fun isInterceptArmed(s: AppSettings, now: Calendar = Calendar.getInstance()): Boolean {
        if (!s.interceptEnabled) return false
        if (!s.scheduleEnabled) return true
        val dayBit = 1 shl now.get(Calendar.DAY_OF_WEEK) - 1 // Sunday=1 -> bit 0
        if (s.scheduleDays and dayBit == 0) return false
        val minutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val start = s.scheduleStartMinutes.coerceIn(0, 24 * 60)
        val end = s.scheduleEndMinutes.coerceIn(0, 24 * 60)
        return if (start == end) {
            true
        } else if (start < end) {
            minutes in start until end
        } else {
            minutes >= start || minutes < end
        }
    }

    fun isAppTargeted(s: AppSettings, packageName: String?): Boolean {
        if (s.targetPackages.isEmpty()) return true
        if (packageName.isNullOrBlank()) return true
        return packageName in s.targetPackages
    }

    private fun Preferences.toSettings(): AppSettings = AppSettings(
        onboarded = this[ONBOARDED] ?: false,
        providerId = this[PROVIDER] ?: "deepseek",
        interceptEnabled = this[INTERCEPT] ?: false,
        persistentEnabled = this[PERSISTENT] ?: false,
        scheduleEnabled = this[SCHEDULE] ?: false,
        scheduleStartMinutes = this[SCHED_START] ?: 8 * 60,
        scheduleEndMinutes = this[SCHED_END] ?: 22 * 60,
        scheduleDays = this[SCHED_DAYS] ?: 0b1111111,
        targetPackages = this[TARGETS].orEmpty().split('\n').filter { it.isNotBlank() }.toSet(),
        accentColor = this[ACCENT] ?: 0xFFB33A2BL,
        themeStyle = runCatching { ThemeStyle.valueOf(this[THEME] ?: "") }.getOrDefault(ThemeStyle.FOLLOW_SYSTEM),
        typographyStyle = runCatching { TypographyStyle.valueOf(this[TYPE] ?: "") }.getOrDefault(TypographyStyle.EDITORIAL),
        enterButtonPosition = runCatching {
            EnterButtonPosition.valueOf(this[ENTER_POS] ?: "")
        }.getOrDefault(EnterButtonPosition.ENTER_RIGHT),
        continueOnExit = this[CONTINUE] ?: true,
    )

    private companion object {
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val PROVIDER = stringPreferencesKey("provider")
        val INTERCEPT = booleanPreferencesKey("intercept")
        val PERSISTENT = booleanPreferencesKey("persistent")
        val SCHEDULE = booleanPreferencesKey("schedule")
        val SCHED_START = intPreferencesKey("sched_start")
        val SCHED_END = intPreferencesKey("sched_end")
        val SCHED_DAYS = intPreferencesKey("sched_days")
        val TARGETS = stringPreferencesKey("targets")
        val ACCENT = longPreferencesKey("accent")
        val THEME = stringPreferencesKey("theme")
        val TYPE = stringPreferencesKey("type")
        val ENTER_POS = stringPreferencesKey("enter_pos")
        val CONTINUE = booleanPreferencesKey("continue_on_exit")
    }
}
