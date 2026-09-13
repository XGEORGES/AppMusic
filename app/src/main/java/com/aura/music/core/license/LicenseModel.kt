package com.aura.music.core.license

data class LicensePayload(
    val dev: String,
    val days: Int,
    val token: String,
    val iat: Long
)

sealed interface LicenseStatus {
    /** La aplicación nunca ha sido activada */
    data object Unlicensed : LicenseStatus

    /** Licencia activa y válida */
    data class Active(
        val daysRemaining: Int,
        val expiresAt: Long,
        val isLifetime: Boolean
    ) : LicenseStatus

    /** Faltan 3 días o menos para que caduque (alerta preventiva) */
    data class ExpiringSoon(
        val daysRemaining: Int,
        val expiresAt: Long
    ) : LicenseStatus

    /** La licencia ha caducado */
    data class Expired(
        val expiredAt: Long
    ) : LicenseStatus

    /** Han pasado más de 5 días sin conectarse a internet */
    data object OfflineSyncRequired : LicenseStatus

    /** Se detectó que el reloj del sistema fue alterado hacia el pasado */
    data object ClockTampered : LicenseStatus
}

sealed interface RedeemResult {
    data class Success(
        val daysAdded: Int,
        val isLifetime: Boolean,
        val totalDaysRemaining: Int
    ) : RedeemResult

    data class Error(
        val message: String
    ) : RedeemResult
}
