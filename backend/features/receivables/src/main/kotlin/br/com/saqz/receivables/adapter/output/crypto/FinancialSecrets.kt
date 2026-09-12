package br.com.saqz.receivables.adapter.output.crypto

import java.nio.charset.StandardCharsets.UTF_8
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Keys are supplied by deployment secrets, never loaded from financial tables. */
class FinancialSecrets(
    private val activeKeyId: String,
    keys: Map<String, ByteArray>,
    identityLookupKey: ByteArray,
) {
    private val keys = keys.mapValues { (_, bytes) ->
        require(bytes.size == 32) { "Financial encryption requires a 256-bit key" }
        SecretKeySpec(bytes.copyOf(), "AES")
    }
    private val lookupKey = SecretKeySpec(identityLookupKey.copyOf(), "HmacSHA256")
    private val random = SecureRandom()

    init {
        require(activeKeyId.matches(Regex("[A-Za-z0-9_-]{1,64}")))
        require(this.keys.containsKey(activeKeyId))
        require(identityLookupKey.size >= 32)
    }

    fun encrypt(accountId: UUID, purpose: String, plaintext: String): String {
        val nonce = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keys.getValue(activeKeyId), GCMParameterSpec(128, nonce))
        cipher.updateAAD(context(accountId, purpose, activeKeyId))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(UTF_8))
        return "v1.$activeKeyId.${encode(nonce + ciphertext)}"
    }

    fun decrypt(accountId: UUID, purpose: String, envelope: String): String {
        try {
            val parts = envelope.split('.')
            require(parts.size == 3 && parts[0] == "v1")
            val packed = Base64.getUrlDecoder().decode(parts[2])
            require(packed.size >= 28)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, keys.getValue(parts[1]), GCMParameterSpec(128, packed.copyOfRange(0, 12)))
            cipher.updateAAD(context(accountId, purpose, parts[1]))
            return String(cipher.doFinal(packed.copyOfRange(12, packed.size)), UTF_8)
        } catch (_: Exception) {
            // Neither ciphertext, key identifier supplied by an attacker, nor provider data is diagnostic output.
            throw IllegalStateException("Financial secret could not be authenticated")
        }
    }

    fun legalIdentityDigest(normalizedCpfCnpj: String): String {
        require(normalizedCpfCnpj.matches(Regex("[0-9]{11}|[0-9]{14}"))) { "Invalid legal identity" }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(lookupKey)
        return encode(mac.doFinal("receivables:legal-identity:$normalizedCpfCnpj".toByteArray(UTF_8)))
    }

    private fun context(accountId: UUID, purpose: String, keyId: String): ByteArray {
        require(purpose.matches(Regex("[a-z-]{1,64}")))
        return "receivables:v1:$keyId:$accountId:$purpose".toByteArray(UTF_8)
    }
    private fun encode(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
