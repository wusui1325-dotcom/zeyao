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
    /** Optional local HTTPS decryption (requires the user-installed 擇要 CA). */
    val httpsDecryptEnabled: Boolean = false,
    /**
     * Floating-window click intercept (default off).
     * When on, a small overlay lets the user arm the next link click
     * in apps such as WeChat; the accessibility service then extracts
     * the URL and shows 擇要 before navigation.
     */
    val clickInterceptEnabled: Boolean = false,
    /** When click intercept is armed, whether to try to consume (block) the original click. Default off. */
    val clickInterceptConsume: Boolean = false,
    /** Same-site cooldown in seconds. Default 10 minutes. */
    val siteCooldownSeconds: Int = 600,
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
