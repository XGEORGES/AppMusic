package com.aura.music.core.license

import org.json.JSONObject
import java.math.BigInteger
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LicenseValidator @Inject constructor() {

    private val publicKey: PublicKey by lazy {
        val keyBytes = Base64.getDecoder().decode(PUBLIC_KEY_BASE64)
        val keySpec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("EC")
        keyFactory.generatePublic(keySpec)
    }

    /**
     * Valida la firma criptográfica y el payload del serial.
     * Retorna Result.success(LicensePayload) o Result.failure(Exception).
     */
    fun validateSerial(serial: String, expectedDeviceId: String): Result<LicensePayload> {
        return runCatching {
            val trimmed = serial.trim()
            val parts = trimmed.split(".")
            if (parts.size != 3 || !parts[0].equals("AURA", ignoreCase = true)) {
                throw IllegalArgumentException("Formato de serial inválido. Debe iniciar con AURA.")
            }

            val payloadEncoded = parts[1]
            val signatureEncoded = parts[2]

            val signatureBytes = base64UrlDecode(signatureEncoded)
            val dataToVerify = payloadEncoded.toByteArray(Charsets.UTF_8)

            // Verificar firma criptográfica ECDSA
            val isVerified = verifySignature(dataToVerify, signatureBytes)
            if (!isVerified) {
                throw SecurityException("Firma criptográfica inválida o serial adulterado.")
            }

            // Decodificar y parsear JSON del payload
            val payloadJsonString = String(base64UrlDecode(payloadEncoded), Charsets.UTF_8)
            val json = JSONObject(payloadJsonString)

            val dev = json.getString("dev").trim().uppercase()
            val days = json.getInt("days")
            val token = json.getString("token")
            val iat = json.optLong("iat", System.currentTimeMillis())

            if (dev != expectedDeviceId.trim().uppercase()) {
                throw IllegalArgumentException("Este serial pertenece a otro dispositivo ($dev).")
            }

            LicensePayload(
                dev = dev,
                days = days,
                token = token,
                iat = iat
            )
        }
    }

    private fun verifySignature(data: ByteArray, signatureBytes: ByteArray): Boolean {
        return try {
            // Si la firma viene en formato IEEE P1363 (64 bytes: r + s), convertirla a DER
            val derBytes = if (signatureBytes.size == 64) {
                p1363ToDer(signatureBytes)
            } else {
                signatureBytes
            }

            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(publicKey)
            verifier.update(data)
            verifier.verify(derBytes)
        } catch (e: Exception) {
            // Intento alternativo con P1363 directo si está soportado en la plataforma
            try {
                val altVerifier = Signature.getInstance("SHA256withECDSAinP1363Format")
                altVerifier.initVerify(publicKey)
                altVerifier.update(data)
                altVerifier.verify(signatureBytes)
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * Convierte una firma IEEE P1363 (64 bytes crudos r||s) a ASN.1 DER estándar
     */
    private fun p1363ToDer(p1363: ByteArray): ByteArray {
        if (p1363.size != 64) return p1363

        val rBytes = p1363.copyOfRange(0, 32)
        val sBytes = p1363.copyOfRange(32, 64)

        val r = BigInteger(1, rBytes).toByteArray()
        val s = BigInteger(1, sBytes).toByteArray()

        val seqLen = 4 + r.size + s.size
        val der = ByteArray(seqLen + 2)
        var offset = 0

        der[offset++] = 0x30
        der[offset++] = seqLen.toByte()

        der[offset++] = 0x02
        der[offset++] = r.size.toByte()
        System.arraycopy(r, 0, der, offset, r.size)
        offset += r.size

        der[offset++] = 0x02
        der[offset++] = s.size.toByte()
        System.arraycopy(s, 0, der, offset, s.size)

        return der
    }

    private fun base64UrlDecode(str: String): ByteArray {
        var base64 = str.replace('-', '+').replace('_', '/')
        while (base64.length % 4 != 0) {
            base64 += "="
        }
        return Base64.getDecoder().decode(base64)
    }

    companion object {
        /**
         * Clave pública maestra ECDSA P-256 (SPKI Base64)
         */
        const val PUBLIC_KEY_BASE64 =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEqO77LOCyaocXOntm1q+Cc9Ma5WnBlATuKyKnkUKMVc3ly368lCl5/YKnPlV3CfZ4UJNlkOndAuaCU5E5LeNBfQ=="
    }
}
