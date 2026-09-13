package com.aura.music.core.license

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LicenseValidatorTest {

    private lateinit var validator: LicenseValidator

    @Before
    fun setup() {
        validator = LicenseValidator()
    }

    @Test
    fun testValidSerialAcceptsCorrectDevice() {
        val deviceId = "TEST-1234-5678"
        val validSerial = "AURA.eyJkZXYiOiJURVNULTEyMzQtNTY3OCIsImRheXMiOjMwLCJ0b2tlbiI6IjQ3YjUwMmQ3LTk4MGQtNDAzMC1hOTFmLTRhZWVlMjQ5Yzg3ZCIsImlhdCI6MTc4OTI1ODkwMTUxN30.MEUCIQD7t7n3-pRjIZsD8p_az7wZLRlozJZMNYAR9Z1mT3dNjwIgcUBOwSJgADmTftJ9C6b8PqsLazdTy-xFv41Hc4-kujI"

        val result = validator.validateSerial(validSerial, deviceId)
        if (result.isFailure) {
            result.exceptionOrNull()?.printStackTrace()
            org.junit.Assert.fail("Fallo con error: ${result.exceptionOrNull()?.message}")
        }
        assertTrue("La validación de un serial legítimo debe ser exitosa", result.isSuccess)

        val payload = result.getOrNull()
        assertNotNull(payload)
        assertEquals(deviceId, payload?.dev)
        assertEquals(30, payload?.days)
        assertEquals("47b502d7-980d-4030-a91f-4aeee249c87d", payload?.token)
    }

    @Test
    fun testRejectSerialWhenDeviceDoesNotMatch() {
        val differentDeviceId = "OTHER-9999-0000"
        val serialForTestDevice = "AURA.eyJkZXYiOiJURVNULTEyMzQtNTY3OCIsImRheXMiOjMwLCJ0b2tlbiI6IjQ3YjUwMmQ3LTk4MGQtNDAzMC1hOTFmLTRhZWVlMjQ5Yzg3ZCIsImlhdCI6MTc4OTI1ODkwMTUxN30.MEUCIQD7t7n3-pRjIZsD8p_az7wZLRlozJZMNYAR9Z1mT3dNjwIgcUBOwSJgADmTftJ9C6b8PqsLazdTy-xFv41Hc4-kujI"

        val result = validator.validateSerial(serialForTestDevice, differentDeviceId)
        assertTrue("Debe rechazar la licencia si pertenece a otro dispositivo", result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message?.contains("pertenece a otro dispositivo") == true
        )
    }

    @Test
    fun testRejectTamperedSerial() {
        val deviceId = "TEST-1234-5678"
        // Alteramos el último carácter de la firma
        val tamperedSerial = "AURA.eyJkZXYiOiJURVNULTEyMzQtNTY3OCIsImRheXMiOjMwLCJ0b2tlbiI6IjQ3YjUwMmQ3LTk4MGQtNDAzMC1hOTFmLTRhZWVlMjQ5Yzg3ZCIsImlhdCI6MTc4OTI1ODkwMTUxN30.MEUCIQD7t7n3-pRjIZsD8p_az7wZLRlozJZMNYAR9Z1mT3dNjwIgcUBOwSJgADmTftJ9C6b8PqsLazdTy-xFv41Hc4-kujX"

        val result = validator.validateSerial(tamperedSerial, deviceId)
        assertTrue("Cualquier alteración en la firma debe invalidar el serial", result.isFailure)
    }

    @Test
    fun testRejectMalformedSerial() {
        val deviceId = "TEST-1234-5678"
        val malformed1 = "INVALID-SERIAL-STRING"
        val malformed2 = "AURA.soloUnaParte"

        assertTrue(validator.validateSerial(malformed1, deviceId).isFailure)
        assertTrue(validator.validateSerial(malformed2, deviceId).isFailure)
    }
}
