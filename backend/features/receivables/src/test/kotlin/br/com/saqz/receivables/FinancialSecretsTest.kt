package br.com.saqz.receivables

import br.com.saqz.receivables.adapter.output.crypto.FinancialSecrets
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class FinancialSecretsTest {
    private val key = ByteArray(32) { it.toByte() }
    private val secrets = FinancialSecrets("key1", mapOf("key1" to key), key.reversedArray())
    private val account = UUID.randomUUID()

    @Test
    fun `credentials are randomized authenticated and bound to account and purpose`() {
        val first = secrets.encrypt(account, "provider-key", "secret-api-key")
        val second = secrets.encrypt(account, "provider-key", "secret-api-key")
        assertFalse(first.contains("secret-api-key"))
        assertNotEquals(first, second)
        assertEquals("secret-api-key", secrets.decrypt(account, "provider-key", first))
        assertFailsWith<IllegalStateException> { secrets.decrypt(UUID.randomUUID(), "provider-key", first) }
        assertFailsWith<IllegalStateException> { secrets.decrypt(account, "legal-data", first) }
        val parts = first.split('.').toMutableList()
        val packed = java.util.Base64.getUrlDecoder().decode(parts[2])
        packed[15] = (packed[15].toInt() xor 1).toByte()
        parts[2] = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(packed)
        assertEquals("Financial secret could not be authenticated", assertFailsWith<IllegalStateException> {
            secrets.decrypt(account, "provider-key", parts.joinToString("."))
        }.message)
    }

    @Test
    fun `key rotation reads previous envelopes but missing keys fail without exposing secrets`() {
        val old = secrets.encrypt(account, "provider-key", "secret-api-key")
        val rotated = FinancialSecrets("key2", mapOf("key1" to key, "key2" to key.reversedArray()), key)
        assertEquals("secret-api-key", rotated.decrypt(account, "provider-key", old))
        val missing = FinancialSecrets("key2", mapOf("key2" to key.reversedArray()), key)
        val failure = assertFailsWith<IllegalStateException> { missing.decrypt(account, "provider-key", old) }
        assertFalse(failure.toString().contains("secret-api-key"))
        assertEquals(null, failure.cause)
    }

    @Test
    fun `legal identity lookup is stable keyed and distinct from plaintext`() {
        val cpf = "12345678901"
        assertEquals(secrets.legalIdentityDigest(cpf), secrets.legalIdentityDigest(cpf))
        assertFalse(secrets.legalIdentityDigest(cpf).contains(cpf))
        assertNotEquals(secrets.legalIdentityDigest(cpf), secrets.legalIdentityDigest("12345678902"))
        assertNotEquals(secrets.legalIdentityDigest(cpf),
            FinancialSecrets("key1", mapOf("key1" to key), key).legalIdentityDigest(cpf))
    }
}
