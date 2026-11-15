package com.wusper.zeyao.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.util.concurrent.ConcurrentHashMap

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
    private val memory = ConcurrentHashMap<String, String>()

    private fun read(name: String): String? {
        memory[name]?.let { return it.takeIf { value -> value.isNotEmpty() } }
        val value = prefs.getString(name, null)?.takeIf { it.isNotBlank() }
        memory[name] = value ?: ""
        return value
    }

    private fun write(name: String, value: String?) {
        if (value.isNullOrEmpty()) {
            memory[name] = ""
            prefs.edit().remove(name).apply()
        } else {
            memory[name] = value
            prefs.edit().putString(name, value).apply()
        }
    }

    fun getApiKey(providerId: String): String? = read(keyName(providerId))

    fun setApiKey(providerId: String, key: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) {
            clearApiKey(providerId)
        } else {
            write(keyName(providerId), trimmed)
        }
    }

    fun clearApiKey(providerId: String) {
        write(keyName(providerId), null)
    }

    /** Role-specific key without falling back to the shared provider key. */
    fun getRoleApiKey(providerId: String, roleId: String): String? =
        read(roleKeyName(providerId, roleId))

    /**
     * Key to use for a role: the role override if set, otherwise the shared
     * provider key. Returns null when both are blank.
     */
    fun getApiKeyForRole(providerId: String, roleId: String): String? =
        ApiKeyResolver.resolve(getRoleApiKey(providerId, roleId), getApiKey(providerId))

    fun setRoleApiKey(providerId: String, roleId: String, key: String) {
        val trimmed = key.trim()
        write(roleKeyName(providerId, roleId), trimmed.ifEmpty { null })
    }

    fun clearRoleApiKey(providerId: String, roleId: String) {
        write(roleKeyName(providerId, roleId), null)
    }

    private fun keyName(providerId: String): String = "api_key_$providerId"

    private fun roleKeyName(providerId: String, roleId: String): String =
        "api_key_${providerId}_$roleId"

    private companion object {
        const val FILE_NAME = "zeyao_secrets"
    }
}
