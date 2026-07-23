package br.com.saqz.composeapp.navigation

import br.com.saqz.access.domain.session.AccessMembership
import br.com.saqz.access.domain.session.AccessMembershipRole
import br.com.saqz.access.domain.session.AccessSession
import br.com.saqz.access.domain.session.AccessUser
import br.com.saqz.domain.GroupId
import br.com.saqz.groups.domain.group.GroupRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AccessToGroupsMembershipMapperTest {
    @Test
    fun `owner translates to Groups owner`() {
        assertRole("OWNER", GroupRole.OWNER)
    }

    @Test
    fun `admin translates to Groups admin`() {
        assertRole("ADMIN", GroupRole.ADMIN)
    }

    @Test
    fun `athlete translates to Groups athlete`() {
        assertRole("ATHLETE", GroupRole.ATHLETE)
    }

    @Test
    fun `empty memberships produce a valid empty reconciliation`() {
        val result = assertIs<AccessToGroupsMemberships.Valid>(emptyList<AccessMembership>().toGroupSelectionMemberships())

        assertTrue(result.memberships.isEmpty())
    }

    @Test
    fun `multiple memberships preserve order identifiers names and translated roles`() {
        val source = listOf(
            membership("alpha", "Alpha", "OWNER"),
            membership("beta", "Beta", "ATHLETE"),
        )

        val result = assertIs<AccessToGroupsMemberships.Valid>(source.toGroupSelectionMemberships())

        assertEquals(listOf("alpha", "beta"), result.memberships.map { it.groupId })
        assertEquals(listOf("Alpha", "Beta"), result.memberships.map { it.groupName })
        assertEquals(listOf(GroupRole.OWNER, GroupRole.ATHLETE), result.memberships.map { it.role })
    }

    @Test
    fun `unknown role rejects the complete translation without a partial Groups model`() {
        val source = listOf(
            membership("alpha", "Alpha", "OWNER"),
            membership("beta", "Beta", "COACH"),
        )
        val session = AccessSession(AccessUser("user", null, "User"), source)

        val result = assertIs<AccessToGroupsMemberships.InvalidRole>(source.toGroupSelectionMemberships())

        assertEquals("COACH", result.value)
        assertTrue(session.toSafeGroupSelectionMemberships().isEmpty())
    }

    private fun assertRole(source: String, expected: GroupRole) {
        val result = assertIs<AccessToGroupsMemberships.Valid>(
            listOf(membership("group", "Group", source)).toGroupSelectionMemberships(),
        )

        assertEquals(expected, result.memberships.single().role)
    }

    private fun membership(id: String, name: String, role: String) = AccessMembership(
        groupId = GroupId(id),
        groupName = name,
        role = AccessMembershipRole(role),
    )
}
