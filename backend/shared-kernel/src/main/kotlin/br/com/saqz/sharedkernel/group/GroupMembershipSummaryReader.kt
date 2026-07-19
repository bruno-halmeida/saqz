package br.com.saqz.sharedkernel.group

import java.util.UUID

data class GroupMembershipSummary(
    val groupId: UUID,
    val groupName: String,
    val role: String,
)

interface GroupMembershipSummaryReader {
    fun readFor(userId: UUID): List<GroupMembershipSummary>
}
