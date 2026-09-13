package com.aura.music.core.license

import android.content.Context
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceIdProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("aura_device_meta", Context.MODE_PRIVATE)

    val deviceId: String by lazy {
        getOrCreateDeviceId()
    }

    private fun getOrCreateDeviceId(): String {
        // Verificar si ya tenemos uno cacheado
        val cached = prefs.getString(KEY_DEVICE_ID, null)
        if (!cached.isNullOrBlank()) {
            return cached
        }

        // Obtener ANDROID_ID
        var rawId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        if (rawId.isNullOrBlank() || rawId == "9774d56d682e549c") {
            // Fallback a UUID persistente
            rawId = UUID.randomUUID().toString()
        }

        // Hashear a SHA-256 para uniformidad y tomar 12 caracteres formateados
        val hash = sha256(rawId)
        val formatted = "${hash.substring(0, 4)}-${hash.substring(4, 8)}-${hash.substring(8, 12)}".uppercase()

        prefs.edit().putString(KEY_DEVICE_ID, formatted).apply()
        return formatted
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val KEY_DEVICE_ID = "cached_device_id"
    }
}
