package com.aura.music.core.license

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LicenseStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("aura_secure_license_meta", Context.MODE_PRIVATE)

    var expirationTimestamp: Long
        get() = prefs.getLong(KEY_EXPIRATION, 0L)
        set(value) = prefs.edit().putLong(KEY_EXPIRATION, value).apply()

    var isLifetime: Boolean
        get() = prefs.getBoolean(KEY_IS_LIFETIME, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_LIFETIME, value).apply()

    var lastVerifiedTimestamp: Long
        get() = prefs.getLong(KEY_LAST_VERIFIED_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_VERIFIED_TIME, value).apply()

    var lastOnlineSyncTimestamp: Long
        get() = prefs.getLong(KEY_LAST_ONLINE_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_ONLINE_SYNC, value).apply()

    fun isTokenUsed(token: String): Boolean {
        val tokens = getUsedTokens()
        return tokens.contains(token)
    }

    fun markTokenAsUsed(token: String) {
        val tokens = getUsedTokens().toMutableSet()
        tokens.add(token)
        saveUsedTokens(tokens)
    }

    private fun getUsedTokens(): Set<String> {
        val jsonString = prefs.getString(KEY_USED_TOKENS, null) ?: return emptySet()
        return try {
            val array = JSONArray(jsonString)
            val result = mutableSetOf<String>()
            for (i in 0 until array.length()) {
                result.add(array.getString(i))
            }
            result
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun saveUsedTokens(tokens: Set<String>) {
        val array = JSONArray()
        tokens.forEach { array.put(it) }
        prefs.edit().putString(KEY_USED_TOKENS, array.toString()).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_EXPIRATION = "lic_exp_ts"
        private const val KEY_IS_LIFETIME = "lic_is_lifetime"
        private const val KEY_USED_TOKENS = "lic_used_tokens"
        private const val KEY_LAST_VERIFIED_TIME = "lic_last_verified_ts"
        private const val KEY_LAST_ONLINE_SYNC = "lic_last_online_sync_ts"
    }
}
