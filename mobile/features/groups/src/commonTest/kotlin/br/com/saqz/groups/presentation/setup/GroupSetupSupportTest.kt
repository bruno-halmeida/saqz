package br.com.saqz.groups.presentation.setup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GroupSetupSupportTest {
    @Test
    fun `setup rules define the supported capacity and monthly due day`() {
        assertEquals(2..100, GroupSetupRules.capacityRange)
        assertEquals(12, GroupSetupRules.defaultCapacity)
        assertEquals(10, GroupSetupRules.defaultMonthlyDueDay)
    }

    @Test
    fun `only an organizer before setup success can edit`() {
        val editable = state(isOrganizer = true)
        assertTrue(editable.canEdit)
        assertFalse(state(isOrganizer = false).canEdit)
        assertFalse(state(isOrganizer = true, successGroupId = "group-1").canEdit)
    }

    private fun state(isOrganizer: Boolean, successGroupId: String? = null) = GroupSetupState(
        mode = GroupSetupMode.CREATE,
        form = newGroupDefaults(),
        commandKey = "command",
        isOrganizer = isOrganizer,
        successGroupId = successGroupId,
    )
}
