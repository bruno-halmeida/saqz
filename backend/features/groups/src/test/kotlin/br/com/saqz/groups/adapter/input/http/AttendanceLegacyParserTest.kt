package br.com.saqz.groups.adapter.input.http

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Apps 1.0.6 a 1.0.9 leem as respostas de presença sem conhecer `position`, `forced` e `positions` (DEB-102). */
class AttendanceLegacyParserTest {
    private val mapper = ObjectMapper()
    private val member = UUID.randomUUID()

    /** Leitor antigo: só os campos que existiam antes do DEB-102, como um parser que ignora o resto. */
    private fun oldFields(json: JsonNode, names: List<String>) = names.associateWith { json[it]?.takeUnless(JsonNode::isNull)?.asText() }

    @Test fun `old reader gets the same values for the old fields in a response with positions`() {
        val entry = AttendanceEntryResponse(member, "WAITLISTED", 2, 5, position = "PONTA", forced = false)
        val detail = AttendanceDetailResponse(
            entry, 4, 1, 3, 5, 0, 2, false,
            positions = listOf(PositionSummaryResponse("PONTA", 2, 2, 0, 2)),
        )
        val json = mapper.readTree(mapper.writeValueAsString(AttendanceMutationResponse(entry, null, 0, detail)))
        assertEquals(
            mapOf("memberId" to "$member", "status" to "WAITLISTED", "waitlistPosition" to "2", "version" to "5"),
            oldFields(json["attendance"], listOf("memberId", "status", "waitlistPosition", "version")),
        )
        assertEquals(
            mapOf("confirmedCount" to "4", "availableSpots" to "1", "waitlistCount" to "3", "capacity" to "5", "declinedCount" to "0", "pendingCount" to "2", "autoConfirmEnabled" to "false"),
            oldFields(json["detail"], listOf("confirmedCount", "availableSpots", "waitlistCount", "capacity", "declinedCount", "pendingCount", "autoConfirmEnabled")),
        )
        assertEquals(1, json["detail"]["positions"].size())
    }

    @Test fun `a game without positions has no summary and the same totals`() {
        val detail = AttendanceDetailResponse(null, 0, 2, 0, 2, 0, 1, false)
        assertNull(detail.positions)
        val json = mapper.readTree(mapper.writeValueAsString(detail))
        assertEquals(2, json["availableSpots"].asInt())
        assertEquals(true, json["positions"] == null || json["positions"].isNull)
        assertEquals(false, json["ownAttendance"]?.isObject ?: false)
    }

    @Test fun `old requests without position or force still deserialize`() {
        val self = mapper.readValue("""{"requestId":"${UUID.randomUUID()}","intent":"CONFIRM"}""", AttendanceSelfRequest::class.java)
        assertNull(self.position)
        val override = mapper.readValue("""{"requestId":"${UUID.randomUUID()}","memberId":"$member","intent":"CONFIRM","reason":"ok ok"}""", AttendanceOverrideRequest::class.java)
        assertNull(override.position); assertNull(override.force)
        val guest = mapper.readValue("""{"requestId":"${UUID.randomUUID()}","displayName":"Fulano"}""", GuestRequest::class.java)
        assertNull(guest.position)
    }
}
