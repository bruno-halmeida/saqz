package br.com.saqz.groups.adapter.output.invite

import br.com.saqz.groups.adapter.output.crypto.JcaSecureTokenGenerator
import br.com.saqz.groups.adapter.output.link.PublicInviteLinkFactory
import br.com.saqz.groups.application.invite.InviteCode
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InviteTokenAdaptersTest {
    private val code = InviteCode.from("44DF8SA8")
    private val legacy = InviteCode.from(Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { it.toByte() }))

    @Test
    fun `generated code has 8 characters from the unambiguous alphabet`() {
        var sequence = 0
        val generator = JcaSecureTokenGenerator { target -> target.indices.forEach { target[it] = (sequence++ * 7).toByte() } }

        repeat(200) {
            val token = generator.generate()
            assertEquals(8, token.code.value.length)
            assertTrue(token.code.isShort)
            assertTrue(token.code.value.all { it in InviteCode.SHORT_ALPHABET })
        }
    }

    @Test
    fun `alphabet has no ambiguous characters`() {
        assertEquals(31, InviteCode.SHORT_ALPHABET.length)
        "01OIL".forEach { assertFalse(it in InviteCode.SHORT_ALPHABET, it.toString()) }
    }

    @Test
    fun `generator discards the out-of-range sample instead of biasing the draw`() {
        // 0x1F (31) está fora do alfabeto de 31 símbolos: é descartado; os demais viram 'A'..
        var call = 0
        val generator = JcaSecureTokenGenerator { target ->
            call += 1
            if (call == 1) {
                target.fill(0x1F)
                target[0] = 0
            } else {
                target.fill(1)
            }
        }

        val token = generator.generate()

        assertEquals("2".repeat(1) + "3".repeat(7), token.code.value)
    }

    @Test
    fun `generated digest is SHA-256 of the canonical code`() {
        val token = JcaSecureTokenGenerator { target -> target.fill(2) }.generate()
        val expected = MessageDigest.getInstance("SHA-256")
            .digest(token.code.value.toByteArray(StandardCharsets.US_ASCII))

        assertContentEquals(expected, token.digest.toByteArray())
        assertFalse(token.toString().contains(token.code.value))
    }

    @Test
    fun `thousands of random codes do not collide`() {
        val generator = JcaSecureTokenGenerator()

        val codes = buildSet { repeat(10_000) { add(generator.generate().code.value) } }

        assertEquals(10_000, codes.size)
    }

    @Test
    fun `short code is accepted in lowercase with or without hyphen or spaces`() {
        listOf("44DF-8SA8", "44df-8sa8", "44DF8SA8", "44df8sa8", " 44DF 8SA8 ", "44-DF-8S-A8").forEach {
            assertEquals("44DF8SA8", InviteCode.from(it).value, it)
        }
        assertEquals("44DF-8SA8", InviteCode.from("44df8sa8").display)
    }

    @Test
    fun `short code rejects ambiguous characters and wrong sizes`() {
        listOf("44DF-8SA0", "44DF-8SAO", "44DF-8SA1", "44DF-8SAI", "44DF-8SAL", "44DF-8SA", "44DF-8SA88", "").forEach {
            assertFailsWith<IllegalArgumentException>(it) { InviteCode.from(it) }
        }
    }

    @Test
    fun `legacy long token is kept as is and keeps its case`() {
        assertEquals(43, legacy.value.length)
        assertFalse(legacy.isShort)
        assertEquals(legacy.value, InviteCode.from(" ${legacy.value} ").value)
    }

    @Test
    fun `invite code rejects values outside both formats`() {
        assertFailsWith<IllegalArgumentException> { InviteCode.from("not-a-32-byte-code") }
    }

    @Test
    fun `link factory rejects a non-HTTPS links domain`() {
        assertFailsWith<IllegalArgumentException> {
            PublicInviteLinkFactory(URI("http://join.saqz.app"))
        }
    }

    @Test
    fun `link factory rejects a links domain with mutable URL components`() {
        listOf(
            "https://user@join.saqz.app",
            "https://join.saqz.app/base",
            "https://join.saqz.app?campaign=secret",
            "https://join.saqz.app#fragment",
        ).forEach { configuredUrl ->
            assertFailsWith<IllegalArgumentException>(configuredUrl) {
                PublicInviteLinkFactory(URI(configuredUrl))
            }
        }
    }

    @Test
    fun `link carries the short code without hyphen and nothing else`() {
        val link = PublicInviteLinkFactory(URI("https://links.saqz.app")).create(code)

        assertEquals("https://links.saqz.app/?saqz_invite=44DF8SA8", link.toASCIIString())
        assertEquals(mapOf("saqz_invite" to "44DF8SA8"), decodedQuery(link))
    }

    @Test
    fun `link contains no identity group role or short-link metadata`() {
        val link = PublicInviteLinkFactory(URI("https://join.saqz.app")).create(code)
        val serialized = link.toASCIIString()

        listOf("groupId", "groupName", "email", "owner", "admin", "athlete", "alias", "campaign")
            .forEach { forbidden -> assertFalse(serialized.contains(forbidden, ignoreCase = true), forbidden) }
        assertEquals(setOf("saqz_invite"), decodedQuery(link).keys)
    }

    private fun decodedQuery(uri: URI): Map<String, String> = uri.rawQuery
        .split('&')
        .associate { pair ->
            val (name, value) = pair.split('=', limit = 2)
            decode(name) to decode(value)
        }

    private fun decode(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8)
}
