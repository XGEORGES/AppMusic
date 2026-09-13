package com.aura.music.core.license

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LicenseRepositoryTest {

    private val deviceIdProvider: DeviceIdProvider = mockk()
    private val validator: LicenseValidator = mockk()
    private val storage: LicenseStorage = mockk(relaxed = true)

    private val testDeviceId = "TEST-1234-5678"

    private lateinit var repository: LicenseRepository

    @Before
    fun setup() {
        every { deviceIdProvider.deviceId } returns testDeviceId
        every { storage.lastVerifiedTimestamp } returns 0L
        every { storage.lastOnlineSyncTimestamp } returns 0L
        every { storage.isLifetime } returns false
        every { storage.expirationTimestamp } returns 0L

        repository = LicenseRepository(deviceIdProvider, validator, storage)
    }

    @Test
    fun testInitialStatusIsUnlicensedWhenNoData() {
        val status = repository.refreshStatus()
        assertTrue("Sin datos de licencia debe ser Unlicensed", status is LicenseStatus.Unlicensed)
    }

    @Test
    fun testRedeemSuccessAccumulatesDays() {
        val serial = "AURA.payload.sig"
        val payload = LicensePayload(
            dev = testDeviceId,
            days = 30,
            token = "token-uuid-1",
            iat = System.currentTimeMillis()
        )

        every { validator.validateSerial(serial, testDeviceId) } returns Result.success(payload)
        every { storage.isTokenUsed("token-uuid-1") } returns false

        val result = repository.redeemSerial(serial)
        assertTrue("Canje exitoso", result is RedeemResult.Success)

        val success = result as RedeemResult.Success
        assertEquals(30, success.daysAdded)
        assertEquals(false, success.isLifetime)

        // Verificar que el token se marcó como usado
        verify { storage.markTokenAsUsed("token-uuid-1") }
    }

    @Test
    fun testRejectAlreadyUsedToken() {
        val serial = "AURA.payload.sig"
        val payload = LicensePayload(
            dev = testDeviceId,
            days = 30,
            token = "token-already-used",
            iat = System.currentTimeMillis()
        )

        every { validator.validateSerial(serial, testDeviceId) } returns Result.success(payload)
        every { storage.isTokenUsed("token-already-used") } returns true

        val result = repository.redeemSerial(serial)
        assertTrue("Debe rechazar token usado", result is RedeemResult.Error)
        assertEquals("Este serial ya fue canjeado previamente.", (result as RedeemResult.Error).message)
    }

    @Test
    fun testRedeemLifetimeLicense() {
        val serial = "AURA.lifetime.sig"
        val payload = LicensePayload(
            dev = testDeviceId,
            days = -1,
            token = "token-lifetime",
            iat = System.currentTimeMillis()
        )

        every { validator.validateSerial(serial, testDeviceId) } returns Result.success(payload)
        every { storage.isTokenUsed("token-lifetime") } returns false

        val result = repository.redeemSerial(serial)
        assertTrue(result is RedeemResult.Success)
        val success = result as RedeemResult.Success
        assertTrue(success.isLifetime)

        verify { storage.isLifetime = true }
    }

    @Test
    fun testClockTamperedDetection() {
        val futureTime = System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 10 // 10 días en el futuro
        every { storage.lastVerifiedTimestamp } returns futureTime

        val status = repository.refreshStatus()
        assertTrue("Debe detectar retroceso de reloj", status is LicenseStatus.ClockTampered)
    }

    @Test
    fun testOfflineSyncRequiredAfter5Days() {
        val sixDaysAgo = System.currentTimeMillis() - (6L * 24 * 60 * 60 * 1000)
        every { storage.lastOnlineSyncTimestamp } returns sixDaysAgo
        every { storage.expirationTimestamp } returns System.currentTimeMillis() + (20L * 24 * 60 * 60 * 1000)

        val status = repository.refreshStatus()
        assertTrue("Debe exigir sincronización tras 5 días sin internet", status is LicenseStatus.OfflineSyncRequired)
    }
}
