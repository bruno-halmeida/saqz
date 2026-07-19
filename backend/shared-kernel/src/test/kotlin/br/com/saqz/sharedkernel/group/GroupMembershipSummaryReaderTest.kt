package br.com.saqz.sharedkernel.group

import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

class GroupMembershipSummaryReaderTest {
    @Test
    fun `reads bootstrap safe membership summaries for an actor`() {
        val userId = UUID.randomUUID()
        val summary = GroupMembershipSummary(UUID.randomUUID(), "Monday volleyball", "ADMIN")
        val reader = RecordingReader(listOf(summary))

        assertEquals(listOf(summary), reader.readFor(userId))
        assertEquals(listOf(userId), reader.requestedUsers)
    }

    @Test
    fun `summary retains only group bootstrap values`() {
        val groupId = UUID.randomUUID()
        val summary = GroupMembershipSummary(groupId, "Beach crew", "ATHLETE")

        assertEquals(groupId, summary.groupId)
        assertEquals("Beach crew", summary.groupName)
        assertEquals("ATHLETE", summary.role)
    }

    private class RecordingReader(
        private val summaries: List<GroupMembershipSummary>,
    ) : GroupMembershipSummaryReader {
        val requestedUsers = mutableListOf<UUID>()

        override fun readFor(userId: UUID): List<GroupMembershipSummary> {
            requestedUsers += userId
            return summaries
        }
    }
}
