package br.com.saqz.groups.adapter.output.attendance.share

import br.com.saqz.groups.adapter.output.crypto.JcaAttendanceLinkTokenGenerator
import br.com.saqz.groups.adapter.output.link.BranchAttendanceLinkFactory
import br.com.saqz.groups.application.attendance.share.AttendanceLinkCode
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AttendanceSharePrimitivesTest {
    private val bytes = ByteArray(32) { it.toByte() }
    private val code = AttendanceLinkCode.from(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes))

    @Test
    fun `generator requests exactly 32 random bytes`() {
        var requestedSize = 0
        val generator = JcaAttendanceLinkTokenGenerator { target ->
            requestedSize = target.size
            bytes.copyInto(target)
        }

        generator.generate()

        assertEquals(32, requestedSize)
    }

    @Test
    fun `generated code has the fixed 43 character length`() {
        assertEquals(43, deterministicGenerator().generate().code.value.length)
    }

    @Test
    fun `generated code uses only the Base64URL alphabet`() {
        assertTrue(deterministicGenerator().generate().code.value.matches(Regex("[A-Za-z0-9_-]+")))
    }

    @Test
    fun `generated code has no Base64 padding`() {
        assertFalse('=' in deterministicGenerator().generate().code.value)
    }

    @Test
    fun `generated code decodes to the original 32 bytes`() {
        assertContentEquals(bytes, Base64.getUrlDecoder().decode(deterministicGenerator().generate().code.value))
    }

    @Test
    fun `generated digest is SHA-256 of the raw code`() {
        val token = deterministicGenerator().generate()
        val expected = MessageDigest.getInstance("SHA-256")
            .digest(token.code.value.toByteArray(StandardCharsets.US_ASCII))

        assertContentEquals(expected, token.digest.toByteArray())
    }

    @Test
    fun `digest and token diagnostics redact raw capability bytes`() {
        val token = deterministicGenerator().generate()
        val exposed = token.digest.toByteArray()
        exposed.fill(0)

        assertFalse(token.digest.toByteArray().all { it == 0.toByte() })
        assertFalse(token.code.toString().contains(token.code.value))
        assertFalse(token.digest.toString().contains(token.code.value))
        assertFalse(token.toString().contains(token.code.value))
    }

    @Test
    fun `attendance link code rejects values outside the generated format`() {
        assertFailsWith<IllegalArgumentException> { AttendanceLinkCode.from("not-a-32-byte-code") }
    }

    @Test
    fun `link factory rejects non-HTTPS or mutable Branch domain configuration`() {
        listOf(
            "http://join.saqz.app",
            "https://user@join.saqz.app",
            "https://join.saqz.app/base",
            "https://join.saqz.app?campaign=secret",
            "https://join.saqz.app#fragment",
            "https://join.saqz.app:8443",
        ).forEach { configuredUrl ->
            assertFailsWith<IllegalArgumentException>(configuredUrl) {
                BranchAttendanceLinkFactory(URI(configuredUrl))
            }
        }
    }

    @Test
    fun `long link decodes to the exact Branch attendance parameters`() {
        val link = BranchAttendanceLinkFactory(URI("https://join.saqz.app")).create(code)

        assertEquals("https", link.scheme)
        assertEquals("join.saqz.app", link.host)
        assertEquals("/", link.path)
        assertEquals(
            mapOf(
                "\$deeplink_path" to "attendance/${code.value}",
                "saqz_attendance" to code.value,
                "\$ios_nativelink" to "true",
            ),
            decodedQuery(link),
        )
        assertTrue(link.rawQuery.contains("%24deeplink_path=attendance%2F"))
        assertTrue(link.rawQuery.contains("saqz_attendance="))
    }

    @Test
    fun `long link contains no group game member contact attendance or finance metadata`() {
        val link = BranchAttendanceLinkFactory(URI("https://join.saqz.app")).create(code)
        val serialized = link.toASCIIString()
        val forbiddenSamples = listOf(
            UUID.randomUUID().toString(),
            "groupId",
            "gameId",
            "groupName",
            "gameTitle",
            "memberId",
            "email",
            "phone",
            "username",
            "confirmed",
            "waitlisted",
            "declined",
            "charge",
            "finance",
        )

        forbiddenSamples.forEach { forbidden ->
            assertFalse(serialized.contains(forbidden, ignoreCase = true), forbidden)
        }
        assertEquals(setOf("\$deeplink_path", "saqz_attendance", "\$ios_nativelink"), decodedQuery(link).keys)
    }

    private fun deterministicGenerator() = JcaAttendanceLinkTokenGenerator { target -> bytes.copyInto(target) }

    private fun decodedQuery(uri: URI): Map<String, String> = uri.rawQuery
        .split('&')
        .associate { pair ->
            val (name, value) = pair.split('=', limit = 2)
            decode(name) to decode(value)
        }

    private fun decode(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8)
}
