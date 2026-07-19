package br.com.saqz.groups.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GroupRoleTest {
    @Test
    fun `owner is synthesized while only admin and athlete are persistable`() {
        assertEquals(setOf(PersistedMembershipRole.ADMIN, PersistedMembershipRole.ATHLETE), PersistedMembershipRole.entries.toSet())
        assertEquals(GroupRole.OWNER, GroupRole.resolve(isOwner = true, persistedRole = null))
        assertEquals(GroupRole.ADMIN, GroupRole.resolve(isOwner = false, PersistedMembershipRole.ADMIN))
        assertEquals(GroupRole.ATHLETE, GroupRole.resolve(isOwner = false, PersistedMembershipRole.ATHLETE))
        assertNull(GroupRole.resolve(isOwner = false, persistedRole = null))
    }
}
