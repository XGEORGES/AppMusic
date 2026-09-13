package com.aura.music.core.license

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class LicenseRepository @Inject constructor(
    private val deviceIdProvider: DeviceIdProvider,
    private val validator: LicenseValidator,
    private val storage: LicenseStorage
) {
    private val _licenseStatus = MutableStateFlow<LicenseStatus>(LicenseStatus.Unlicensed)
    val licenseStatus: StateFlow<LicenseStatus> = _licenseStatus.asStateFlow()

    init {
        refreshStatus()
    }

    /**
     * Retorna el ID de dispositivo único actual (ej: A8F4-992B-7C10)
     */
    fun getDeviceId(): String = deviceIdProvider.deviceId

    /**
     * Evalúa y actualiza el estado actual de la licencia
     */
    fun refreshStatus(): LicenseStatus {
        val now = System.currentTimeMillis()
        val lastVerified = storage.lastVerifiedTimestamp

        // 1. Anti-Tampering: Si el reloj del sistema es inferior al último tiempo verificado (> 5 min de desfase)
        if (lastVerified > 0 && now < (lastVerified - CLOCK_SKEW_TOLERANCE_MS)) {
            val status = LicenseStatus.ClockTampered
            _licenseStatus.value = status
            return status
        }

        // Si el tiempo avanza normalmente, registrar el nuevo pico
        if (now > lastVerified) {
            storage.lastVerifiedTimestamp = now
        }

        // 2. Si no hay licencia configurada
        if (!storage.isLifetime && storage.expirationTimestamp <= 0L) {
            val status = LicenseStatus.Unlicensed
            _licenseStatus.value = status
            return status
        }

        // 3. Tolerancia Offline de 5 días
        val lastSync = storage.lastOnlineSyncTimestamp
        if (lastSync > 0L && (now - lastSync) > OFFLINE_GRACE_PERIOD_MS) {
            val status = LicenseStatus.OfflineSyncRequired
            _licenseStatus.value = status
            return status
        }

        // 4. Si es Licencia Vitalicia
        if (storage.isLifetime) {
            val status = LicenseStatus.Active(
                daysRemaining = 9999,
                expiresAt = -1L,
                isLifetime = true
            )
            _licenseStatus.value = status
            return status
        }

        // 5. Verificar fecha de expiración
        val exp = storage.expirationTimestamp
        if (now >= exp) {
            val status = LicenseStatus.Expired(expiredAt = exp)
            _licenseStatus.value = status
            return status
        }

        // 6. Calcular días restantes
        val remainingMillis = exp - now
        val daysRemaining = (remainingMillis / ONE_DAY_MS).toInt().coerceAtLeast(0)

        val status = if (daysRemaining <= EXPIRING_SOON_THRESHOLD_DAYS) {
            LicenseStatus.ExpiringSoon(daysRemaining = daysRemaining, expiresAt = exp)
        } else {
            LicenseStatus.Active(daysRemaining = daysRemaining, expiresAt = exp, isLifetime = false)
        }

        _licenseStatus.value = status
        return status
    }

    /**
     * Canjea un serial y acumula los días a la suscripción actual
     */
    fun redeemSerial(serial: String): RedeemResult {
        val validationResult = validator.validateSerial(serial, getDeviceId())
        if (validationResult.isFailure) {
            val err = validationResult.exceptionOrNull()?.message ?: "Serial inválido o incorrecto."
            return RedeemResult.Error(err)
        }

        val payload = validationResult.getOrThrow()

        // Verificar si este token ya fue canjeado en este dispositivo
        if (storage.isTokenUsed(payload.token)) {
            return RedeemResult.Error("Este serial ya fue canjeado previamente.")
        }

        // Marcar token como consumido
        storage.markTokenAsUsed(payload.token)

        val now = System.currentTimeMillis()
        if (storage.lastOnlineSyncTimestamp == 0L) {
            storage.lastOnlineSyncTimestamp = now
        }
        if (storage.lastVerifiedTimestamp < now) {
            storage.lastVerifiedTimestamp = now
        }

        if (payload.days == -1) {
            // Licencia Vitalicia
            storage.isLifetime = true
            refreshStatus()
            return RedeemResult.Success(
                daysAdded = -1,
                isLifetime = true,
                totalDaysRemaining = 9999
            )
        }

        // Licencia con días: acumular sobre la expiración actual o sobre 'ahora'
        val baseTime = max(storage.expirationTimestamp, now)
        val addedMillis = payload.days.toLong() * ONE_DAY_MS
        val newExpiration = baseTime + addedMillis

        storage.expirationTimestamp = newExpiration
        refreshStatus()

        val totalRemainingDays = ((newExpiration - now) / ONE_DAY_MS).toInt().coerceAtLeast(1)

        return RedeemResult.Success(
            daysAdded = payload.days,
            isLifetime = false,
            totalDaysRemaining = totalRemainingDays
        )
    }

    /**
     * Notificado por el NetworkTimeInterceptor cada vez que se recibe un Date header HTTP de Google/YouTube
     */
    fun updateNetworkTime(serverTimeMillis: Long) {
        storage.lastOnlineSyncTimestamp = serverTimeMillis
        if (serverTimeMillis > storage.lastVerifiedTimestamp) {
            storage.lastVerifiedTimestamp = serverTimeMillis
        }
        refreshStatus()
    }

    companion object {
        private const val ONE_DAY_MS = 24 * 60 * 60 * 1000L
        private const val OFFLINE_GRACE_PERIOD_MS = 5 * ONE_DAY_MS // 5 días de tolerancia sin internet
        private const val CLOCK_SKEW_TOLERANCE_MS = 5 * 60 * 1000L // 5 minutos de margen por desfase
        private const val EXPIRING_SOON_THRESHOLD_DAYS = 3 // Aviso cuando queden 3 días o menos
    }
}
