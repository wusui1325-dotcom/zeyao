package com.wusper.zeyao.data

/**
 * Persisted user settings (never includes API keys).
 */
data class AppSettings(
    val onboarded: Boolean = false,
    val providerId: String = "deepseek",
    val interceptEnabled: Boolean = false,
    val persistentEnabled: Boolean = false,
    val scheduleEnabled: Boolean = false,
    val scheduleStartMinutes: Int = 8 * 60,
    val scheduleEndMinutes: Int = 22 * 60,
    /** Bitmask Sunday=1, Monday=2, ... Saturday=64. Default all days. */
    val scheduleDays: Int = 0b1111111,
    val targetPackages: Set<String> = emptySet(),
    val accentColor: Long = 0xFFB33A2BL,
    val themeStyle: ThemeStyle = ThemeStyle.FOLLOW_SYSTEM,
    val typographyStyle: TypographyStyle = TypographyStyle.EDITORIAL,
    val enterButtonPosition: EnterButtonPosition = EnterButtonPosition.ENTER_RIGHT,
    val continueOnExit: Boolean = true,
    /** Package of the real browser used after 進入 or for non-news links. */
    val handoffBrowserPackage: String = "",
)

enum class ThemeStyle { FOLLOW_SYSTEM, DARK, LIGHT }

enum class TypographyStyle { EDITORIAL, COMPACT }

enum class EnterButtonPosition {
    LEFT,
    RIGHT,
    BOTTOM_CENTER,
    ENTER_LEFT,
    ENTER_RIGHT,
}
