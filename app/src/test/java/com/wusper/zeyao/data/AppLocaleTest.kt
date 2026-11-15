package com.wusper.zeyao.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLocaleTest {
    @Test
    fun languageOrDefault_usesStoredTag() {
        assertEquals(AppLanguage.ENGLISH, AppLocale.languageOrDefault { "en" })
        assertEquals(AppLanguage.SIMPLIFIED, AppLocale.languageOrDefault { "zh-CN" })
        assertEquals(AppLanguage.TRADITIONAL, AppLocale.languageOrDefault { "zh-TW" })
    }

    @Test
    fun languageOrDefault_fallsBackWhenMissingOrUnknown() {
        assertEquals(AppLanguage.TRADITIONAL, AppLocale.languageOrDefault { null })
        assertEquals(AppLanguage.TRADITIONAL, AppLocale.languageOrDefault { "xx" })
        assertEquals(AppLanguage.TRADITIONAL, AppLocale.languageOrDefault { "" })
    }

    @Test
    fun languageOrDefault_failsOpenWhenPrefsContextIsUnavailable() {
        assertEquals(
            AppLanguage.TRADITIONAL,
            AppLocale.languageOrDefault {
                throw NullPointerException(
                    "Attempt to invoke virtual method " +
                        "'android.content.SharedPreferences " +
                        "android.content.Context.getSharedPreferences" +
                        "(java.lang.String, int)' on a null object reference",
                )
            },
        )
    }
}
