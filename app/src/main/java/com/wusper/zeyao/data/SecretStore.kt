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
        val trimmed = key.trim()
        if (trimmed.isEmpty()) {
            clearApiKey(providerId)
        } else {
            prefs.edit().putString(keyName(providerId), trimmed).apply()
        }
    }

    fun clearApiKey(providerId: String) {
        prefs.edit().remove(keyName(providerId)).apply()
    }

    /** Role-specific key without falling back to the shared provider key. */
    fun getRoleApiKey(providerId: String, roleId: String): String? {
        val value = prefs.getString(roleKeyName(providerId, roleId), null)
        return value?.takeIf { it.isNotBlank() }
    }

    /**
     * Key to use for a role: the role override if set, otherwise the shared
     * provider key. Returns null when both are blank.
     */
    fun getApiKeyForRole(providerId: String, roleId: String): String? =
        ApiKeyResolver.resolve(getRoleApiKey(providerId, roleId), getApiKey(providerId))

    fun setRoleApiKey(providerId: String, roleId: String, key: String) {
        val name = roleKeyName(providerId, roleId)
        val trimmed = key.trim()
        if (trimmed.isEmpty()) {
            prefs.edit().remove(name).apply()
        } else {
            prefs.edit().putString(name, trimmed).apply()
        }
    }

    fun clearRoleApiKey(providerId: String, roleId: String) {
        prefs.edit().remove(roleKeyName(providerId, roleId)).apply()
    }

    private fun keyName(providerId: String): String = "api_key_$providerId"

    private fun roleKeyName(providerId: String, roleId: String): String =
        "api_key_${providerId}_$roleId"

    private companion object {
        const val FILE_NAME = "zeyao_secrets"
    }
}
