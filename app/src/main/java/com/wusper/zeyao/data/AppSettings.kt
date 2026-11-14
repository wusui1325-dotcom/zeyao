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
    /** When true, HTTPS decryption only applies to known news domains (whitelist mode). */
    val httpsDecryptNewsOnly: Boolean = true,
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
    /** UI language. Default Traditional Chinese. */
    val language: AppLanguage = AppLanguage.TRADITIONAL,
    /** Preferred summary length. */
    val summaryLength: SummaryLength = SummaryLength.NORMAL,
    /** When on, regenerate/shorter/longer will ask the model to search before summarizing. */
    val searchNewsEnabled: Boolean = false,
    /**
     * Experimental. Screenshots every second (max 3) and, when a page is intercepted,
     * sends the previous screenshot to the cheapest model to recover a title or link.
     */
    val imageRecognitionEnabled: Boolean = false,
    /** Intercept every classifiable page, not only news. */
    val interceptAllPages: Boolean = false,
    /** When intercept-all is on, ask before opening 擇要. Off = always summarize. */
    val askBeforeSummary: Boolean = false,
    /** Add anti-fraud guidance and require risk percentages at the start. */
    val antiFraudEnabled: Boolean = false,
    /** Extract facts and search each one before summarizing. */
    val authenticityCheckEnabled: Boolean = false,
    /** Preferred temperature for summary generation. */
    val temperature: Double = 1.0,
    /** Preferred max tokens for summary. */
    val maxTokens: Int = 1200,
    /** Comma-separated fallback models for the current provider. Empty = use built-in list. */
    val fallbackModels: String = "",
    /** Local anonymous intercept counter for simple stats. */
    val interceptCount: Int = 0,
    /** Enable biometric unlock for API key access. */
    val biometricUnlockEnabled: Boolean = false,
    /** High contrast mode for accessibility. */
    val highContrastEnabled: Boolean = false,
)

enum class SummaryLength { SHORT, NORMAL, LONG }

enum class AppLanguage(val tag: String, val nativeName: String) {
    SIMPLIFIED("zh-CN", "简体中文"),
    TRADITIONAL("zh-TW", "繁體中文"),
    ENGLISH("en", "English"),
    ;

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag == tag } ?: TRADITIONAL
    }
}

enum class ThemeStyle { FOLLOW_SYSTEM, DARK, LIGHT, HIGH_CONTRAST }

enum class TypographyStyle { EDITORIAL, COMPACT }

enum class EnterButtonPosition {
    LEFT,
    RIGHT,
    BOTTOM_CENTER,
    ENTER_LEFT,
    ENTER_RIGHT,
}
