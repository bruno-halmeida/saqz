package br.com.saqz.access.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertSame

class GroupAccessPolicyTest {
    private val policy = GroupAccessPolicy()

    @Test
    fun `owner can read group`() = assertAllowed(GroupRole.OWNER, GroupAction.READ_GROUP)

    @Test
    fun `admin can read group`() = assertAllowed(GroupRole.ADMIN, GroupAction.READ_GROUP)

    @Test
    fun `athlete can read group`() = assertAllowed(GroupRole.ATHLETE, GroupAction.READ_GROUP)

    @Test
    fun `non member reads group as not found`() = assertNotFound(GroupAction.READ_GROUP)

    @Test
    fun `owner can update settings`() = assertAllowed(GroupRole.OWNER, GroupAction.UPDATE_SETTINGS)

    @Test
    fun `admin can update settings`() = assertAllowed(GroupRole.ADMIN, GroupAction.UPDATE_SETTINGS)

    @Test
    fun `athlete cannot update settings`() = assertForbidden(GroupRole.ATHLETE, GroupAction.UPDATE_SETTINGS)

    @Test
    fun `non member updates settings as not found`() = assertNotFound(GroupAction.UPDATE_SETTINGS)

    @Test
    fun `owner can manage invite`() = assertAllowed(GroupRole.OWNER, GroupAction.MANAGE_INVITE)

    @Test
    fun `admin can manage invite`() = assertAllowed(GroupRole.ADMIN, GroupAction.MANAGE_INVITE)

    @Test
    fun `athlete cannot manage invite`() = assertForbidden(GroupRole.ATHLETE, GroupAction.MANAGE_INVITE)

    @Test
    fun `non member manages invite as not found`() = assertNotFound(GroupAction.MANAGE_INVITE)

    @Test
    fun `owner can manage roles`() = assertAllowed(GroupRole.OWNER, GroupAction.MANAGE_ROLES)

    @Test
    fun `admin cannot manage roles`() = assertForbidden(GroupRole.ADMIN, GroupAction.MANAGE_ROLES)

    @Test
    fun `athlete cannot manage roles`() = assertForbidden(GroupRole.ATHLETE, GroupAction.MANAGE_ROLES)

    @Test
    fun `non member manages roles as not found`() = assertNotFound(GroupAction.MANAGE_ROLES)

    private fun assertAllowed(role: GroupRole, action: GroupAction) {
        assertSame(GroupAccessDecision.Allowed, policy.authorize(role, action))
    }

    private fun assertForbidden(role: GroupRole, action: GroupAction) {
        assertSame(GroupAccessDecision.Forbidden, policy.authorize(role, action))
    }

    private fun assertNotFound(action: GroupAction) {
        assertSame(GroupAccessDecision.GroupNotFound, policy.authorize(role = null, action))
    }
}
