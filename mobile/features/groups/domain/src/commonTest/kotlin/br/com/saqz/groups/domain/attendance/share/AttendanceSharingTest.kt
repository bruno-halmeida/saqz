package br.com.saqz.groups.domain.attendance.share

import br.com.saqz.domain.GroupId
import kotlin.test.Test
import kotlin.test.assertEquals

class AttendanceSharingTest {
    @Test
    fun `destination preserves group and game identity`() {
        val destination = AttendanceLinkDestination(GroupId("group"), "game")
        assertEquals("group", destination.groupId.value)
        assertEquals("game", destination.gameId)
    }

    @Test
    fun `attempt limit preserves optional retry delay`() {
        assertEquals(30, AttendanceSharingError.AttemptLimit(30).retryAfterSeconds)
        assertEquals(null, AttendanceSharingError.AttemptLimit(null).retryAfterSeconds)
    }
}
