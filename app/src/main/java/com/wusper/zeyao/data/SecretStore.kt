package com.wusper.zeyao.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * API keys live only in EncryptedSharedPreferences (Android Keystore-backed).
 * Never log, print, or write keys to source, gradle, or debug files.
 */
class SecretStore(context: Context) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        FILE_NAME,
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context.applicationContext,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun getApiKey(providerId: String): String? {
        val value = prefs.getString(keyName(providerId), null)
        return value?.takeIf { it.isNotBlank() }
    }

    fun setApiKey(providerId: String, key: String) {
        prefs.edit().putString(keyName(providerId), key.trim()).apply()
    }

    fun clearApiKey(providerId: String) {
        prefs.edit().remove(keyName(providerId)).apply()
    }

    private fun keyName(providerId: String): String = "api_key_$providerId"

    private companion object {
        const val FILE_NAME = "zeyao_secrets"
    }
}
