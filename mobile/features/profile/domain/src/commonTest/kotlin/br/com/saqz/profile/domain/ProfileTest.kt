package br.com.saqz.profile.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProfileTest {
    @Test
    fun `phone visibility exposes the contract values`() {
        assertEquals(
            listOf(PhoneVisibility.EVERYONE, PhoneVisibility.ADMINS, PhoneVisibility.NOBODY),
            PhoneVisibility.entries,
        )
    }

    @Test
    fun `stats preserve a missing attendance rate`() {
        val stats = ProfileStats(games = 0, attendanceRate = null, groups = 2)

        assertEquals(null, stats.attendanceRate)
        assertEquals(0, stats.games)
        assertEquals(2, stats.groups)
    }

    @Test
    fun `profile update can distinguish clearing a nullable field from leaving it unchanged`() {
        val request = UpdateSessionProfileRequest(
            nickname = UpdateField.Set(null),
            city = UpdateField.Unchanged,
        )

        assertEquals(UpdateField.Set(null), request.nickname)
        assertIs<UpdateField.Unchanged>(request.city)
    }
}
