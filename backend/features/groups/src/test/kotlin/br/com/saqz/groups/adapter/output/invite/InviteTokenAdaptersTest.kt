package br.com.saqz.groups.adapter.output.invite

import br.com.saqz.groups.adapter.output.crypto.JcaSecureTokenGenerator
import br.com.saqz.groups.adapter.output.link.BranchInviteLinkFactory
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
    private val bytes = ByteArray(32) { it.toByte() }
    private val code = InviteCode.from(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes))

    @Test
    fun `generator requests exactly 32 random bytes`() {
        var requestedSize = 0
        val generator = JcaSecureTokenGenerator { target ->
            requestedSize = target.size
            bytes.copyInto(target)
        }

        generator.generate()

        assertEquals(32, requestedSize)
    }

    @Test
    fun `generated code has the fixed 43 character length`() {
        val token = deterministicGenerator().generate()

        assertEquals(43, token.code.value.length)
    }

    @Test
    fun `generated code uses only the Base64URL alphabet`() {
        val token = deterministicGenerator().generate()

        assertTrue(token.code.value.matches(Regex("[A-Za-z0-9_-]+")))
    }

    @Test
    fun `generated code has no Base64 padding`() {
        val token = deterministicGenerator().generate()

        assertFalse('=' in token.code.value)
    }

    @Test
    fun `generated code decodes to the original 32 bytes`() {
        val token = deterministicGenerator().generate()

        assertContentEquals(bytes, Base64.getUrlDecoder().decode(token.code.value))
    }

    @Test
    fun `generated digest is SHA-256 of the raw code`() {
        val token = deterministicGenerator().generate()
        val expected = MessageDigest.getInstance("SHA-256")
            .digest(token.code.value.toByteArray(StandardCharsets.US_ASCII))

        assertContentEquals(expected, token.digest.toByteArray())
    }

    @Test
    fun `digest does not expose mutable internal bytes`() {
        val token = deterministicGenerator().generate()
        val digest = token.digest
        val exposed = digest.toByteArray()
        exposed.fill(0)

        assertFalse(digest.toByteArray().all { it == 0.toByte() })
        assertFalse(token.toString().contains(token.code.value))
    }

    @Test
    fun `ten thousand deterministic tokens do not collide`() {
        var sequence = 0
        val generator = JcaSecureTokenGenerator { target ->
            sequence += 1
            target.fill(0)
            target[28] = (sequence ushr 24).toByte()
            target[29] = (sequence ushr 16).toByte()
            target[30] = (sequence ushr 8).toByte()
            target[31] = sequence.toByte()
        }

        val codes = buildSet { repeat(10_000) { add(generator.generate().code.value) } }

        assertEquals(10_000, codes.size)
    }

    @Test
    fun `invite code rejects values outside the generated format`() {
        assertFailsWith<IllegalArgumentException> { InviteCode.from("not-a-32-byte-code") }
    }

    @Test
    fun `link factory rejects a non-HTTPS Branch domain`() {
        assertFailsWith<IllegalArgumentException> {
            BranchInviteLinkFactory(URI("http://join.saqz.app"))
        }
    }

    @Test
    fun `link factory rejects a Branch domain with mutable URL components`() {
        listOf(
            "https://user@join.saqz.app",
            "https://join.saqz.app/base",
            "https://join.saqz.app?campaign=secret",
            "https://join.saqz.app#fragment",
        ).forEach { configuredUrl ->
            assertFailsWith<IllegalArgumentException>(configuredUrl) {
                BranchInviteLinkFactory(URI(configuredUrl))
            }
        }
    }

    @Test
    fun `long link decodes to the exact Branch invite parameters`() {
        val link = BranchInviteLinkFactory(URI("https://join.saqz.app")).create(code)

        assertEquals("https", link.scheme)
        assertEquals("join.saqz.app", link.host)
        assertEquals("/", link.path)
        assertEquals(
            mapOf(
                "\$deeplink_path" to "invite/${code.value}",
                "saqz_invite" to code.value,
                "\$ios_nativelink" to "true",
            ),
            decodedQuery(link),
        )
        assertTrue(link.rawQuery.contains("%24deeplink_path=invite%2F"))
        assertTrue(link.rawQuery.contains("%24ios_nativelink=true"))
    }

    @Test
    fun `long link contains no identity group role or short-link metadata`() {
        val link = BranchInviteLinkFactory(URI("https://join.saqz.app")).create(code)
        val serialized = link.toASCIIString()

        listOf("groupId", "groupName", "email", "owner", "admin", "athlete", "alias", "campaign")
            .forEach { forbidden -> assertFalse(serialized.contains(forbidden, ignoreCase = true), forbidden) }
        assertEquals(setOf("\$deeplink_path", "saqz_invite", "\$ios_nativelink"), decodedQuery(link).keys)
    }

    private fun deterministicGenerator() = JcaSecureTokenGenerator { target -> bytes.copyInto(target) }

    private fun decodedQuery(uri: URI): Map<String, String> = uri.rawQuery
        .split('&')
        .associate { pair ->
            val (name, value) = pair.split('=', limit = 2)
            decode(name) to decode(value)
        }

    private fun decode(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8)
}
