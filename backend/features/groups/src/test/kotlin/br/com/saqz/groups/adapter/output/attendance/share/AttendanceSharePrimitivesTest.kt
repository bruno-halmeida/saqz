package br.com.saqz.groups.adapter.output.attendance.share

import br.com.saqz.groups.adapter.output.link.PublicAttendanceLinkFactory
import br.com.saqz.groups.application.attendance.share.AttendanceLinkCode
import org.junit.jupiter.api.Test
import java.net.URI
import java.util.Base64
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class AttendanceSharePrimitivesTest {
    private val bytes = ByteArray(32) { it.toByte() }
    private val code = AttendanceLinkCode.from(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes))

    @Test
    fun `attendance link code rejects values outside the generated format`() {
        assertFailsWith<IllegalArgumentException> { AttendanceLinkCode.from("not-a-32-byte-code") }
    }

    @Test
    fun `link factory rejects non-HTTPS or mutable links domain configuration`() {
        listOf(
            "http://join.saqz.app",
            "https://user@join.saqz.app",
            "https://join.saqz.app/base",
            "https://join.saqz.app?campaign=secret",
            "https://join.saqz.app#fragment",
            "https://join.saqz.app:8443",
        ).forEach { configuredUrl ->
            assertFailsWith<IllegalArgumentException>(configuredUrl) {
                PublicAttendanceLinkFactory(URI(configuredUrl))
            }
        }
    }

    @Test
    fun `long link decodes to the exact public attendance parameters`() {
        val link = PublicAttendanceLinkFactory(URI("https://join.saqz.app")).confirm(code)

        assertEquals("https", link.scheme)
        assertEquals("join.saqz.app", link.host)
        assertEquals("/attendance/${code.value}", link.path)
        assertNull(link.rawQuery)
    }

    @Test
    fun `decline link preserves the attendance path and marks only the intent`() {
        val link = PublicAttendanceLinkFactory(URI("https://join.saqz.app")).decline(code)

        assertEquals("/attendance/${code.value}", link.path)
        assertEquals("saqz_intent=decline", link.rawQuery)
    }

    @Test
    fun `long link contains no group game member contact attendance or finance metadata`() {
        val factory = PublicAttendanceLinkFactory(URI("https://join.saqz.app"))
        val confirm = factory.confirm(code)
        val decline = factory.decline(code)
        assertNull(confirm.rawQuery)
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
        listOf(confirm, decline).map(URI::toASCIIString).forEach { serialized ->
            forbiddenSamples.forEach { forbidden ->
                assertFalse(serialized.contains(forbidden, ignoreCase = true), "$forbidden in $serialized")
            }
        }
    }
}
