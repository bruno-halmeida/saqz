package br.com.saqz.groups.domain.membership

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.ValidationDetails
import br.com.saqz.groups.domain.group.GroupRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GroupMembershipTest {
    @Test
    fun `membership preserves owner role and identity`() {
        assertEquals(GroupRole.OWNER, membership(GroupRole.OWNER).role)
    }

    @Test
    fun `membership preserves admin role`() {
        assertEquals(GroupRole.ADMIN, membership(GroupRole.ADMIN).role)
    }

    @Test
    fun `membership preserves athlete role`() {
        assertEquals(GroupRole.ATHLETE, membership(GroupRole.ATHLETE).role)
    }

    @Test
    fun `assignable roles exclude owner`() {
        assertEquals(
            listOf(AssignableGroupRole.ADMIN, AssignableGroupRole.ATHLETE),
            AssignableGroupRole.entries,
        )
    }

    @Test
    fun `role change preserves group user and target role`() {
        val command = ChangeMembershipRoleCommand(GroupId("group-1"), "user-2", AssignableGroupRole.ADMIN)

        assertEquals(
            Triple(GroupId("group-1"), "user-2", AssignableGroupRole.ADMIN),
            Triple(command.groupId, command.userId, command.role),
        )
    }

    @Test
    fun `invite URL preserves exact safe value`() {
        assertEquals("https://saqz.app/invite/safe", GroupInviteUrl("https://saqz.app/invite/safe").value)
    }

    @Test
    fun `redeemed membership preserves group and role`() {
        val result = RedeemedMembership(GroupId("group-1"), GroupRole.ADMIN)

        assertEquals(GroupId("group-1") to GroupRole.ADMIN, result.groupId to result.role)
    }

    @Test
    fun `invalid or expired remains a feature outcome`() {
        assertEquals(GroupMembershipError.InvalidOrExpired, GroupMembershipError.InvalidOrExpired)
    }

    @Test
    fun `attempt limit preserves retry delay`() {
        assertEquals(45, GroupMembershipError.AttemptLimit(45).retryAfterSeconds)
    }

    @Test
    fun `attempt limit preserves missing retry delay`() {
        assertNull(GroupMembershipError.AttemptLimit(null).retryAfterSeconds)
    }

    @Test
    fun `validation preserves safe global and field messages`() {
        val details = ValidationDetails(
            globalMessages = listOf("Revise o convite"),
            fieldMessages = mapOf("code" to listOf("inválido")),
        )

        assertEquals(details, GroupMembershipError.Validation(details).details)
    }

    @Test
    fun `forbidden preserves shared data category`() {
        assertEquals(DataError.Forbidden, GroupMembershipError.DataFailure(DataError.Forbidden).error)
    }

    @Test
    fun `not found preserves shared data category`() {
        assertEquals(DataError.NotFound, GroupMembershipError.DataFailure(DataError.NotFound).error)
    }

    @Test
    fun `data failure preserves transport independent category`() {
        assertEquals(DataError.Connectivity, GroupMembershipError.DataFailure(DataError.Connectivity).error)
    }

    private fun membership(role: GroupRole) = GroupMembership(
        userId = "user-1",
        displayName = "Ana",
        role = role,
    )
}
