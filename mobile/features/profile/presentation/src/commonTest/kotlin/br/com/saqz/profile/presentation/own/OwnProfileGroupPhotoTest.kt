package br.com.saqz.profile.presentation.own

import br.com.saqz.domain.GroupId
import br.com.saqz.profile.domain.AthleteMembership
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OwnProfileGroupPhotoTest {
    private fun membership(version: Long?) = AthleteMembership(
        groupId = GroupId("g-1"),
        groupName = "Quadra Fechada",
        role = "ATHLETE",
        position = null,
        membershipType = "AVULSO",
        active = true,
        groupPhotoVersion = version,
    )

    @Test
    fun `group with a photo uses the same url as the group list`() {
        assertEquals("/api/groups/g-1/photo?v=4", membership(4).toOwnProfileGroupUi().photoUrl)
    }

    @Test
    fun `group without a photo stays on the initials`() {
        assertNull(membership(null).toOwnProfileGroupUi().photoUrl)
    }
}
