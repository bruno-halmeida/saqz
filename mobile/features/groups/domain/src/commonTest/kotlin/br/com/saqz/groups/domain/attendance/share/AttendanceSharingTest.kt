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

    @Test
    fun `snapshot preserves privacy safe rendered values`() {
        val person = AttendanceSharePerson("Ana", 1)
        val snapshot = AttendanceShareSnapshot("Jogo", "2026-07-22T20:00:00Z", "UTC", "Quadra", 12, listOf(person), emptyList(), emptyList())
        assertEquals(person, snapshot.confirmed.single())
        assertEquals(12, snapshot.capacity)
    }
}
