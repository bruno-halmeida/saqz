package br.com.saqz.groups.presentation.games.editor

import br.com.saqz.domain.GroupId
import br.com.saqz.groups.domain.game.GameVenue
import br.com.saqz.groups.domain.group.Group
import br.com.saqz.groups.domain.group.GroupFinanceDefaults
import br.com.saqz.groups.domain.group.GroupProfile
import br.com.saqz.groups.domain.group.GroupRegularSlot
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.domain.group.GroupTimeZone
import br.com.saqz.groups.domain.group.GroupVenue
import br.com.saqz.groups.domain.group.GroupWeekday
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The create-game route seeds its form from the group itself; a group without a profile
 * or finance defaults must still open an editable (empty) form instead of failing.
 */
class GameEditorDefaultsTest {

    @Test
    fun `a configured group seeds venue schedule capacity and fee defaults`() {
        val defaults = configuredGroup().toGameEditorDefaults()

        assertEquals("Vôlei da Quinta", defaults.title)
        assertEquals(GameVenue("venue-1", "Arena Central", "Rua Central 100", "Quadra 2"), defaults.venue)
        assertEquals("America/Sao_Paulo", defaults.zoneId)
        assertEquals(90, defaults.durationMinutes)
        assertEquals(24, defaults.capacity)
        assertEquals(180, defaults.confirmationLeadMinutes)
        assertEquals(2500, defaults.gameFeeCents)
    }

    @Test
    fun `a group without profile or finance defaults keeps every optional default empty`() {
        val defaults = Group(
            id = GroupId("group-1"),
            name = "Vôlei da Quinta",
            timeZone = GroupTimeZone("America/Sao_Paulo"),
            version = 1,
            role = GroupRole.OWNER,
        ).toGameEditorDefaults()

        assertEquals("Vôlei da Quinta", defaults.title)
        assertEquals("America/Sao_Paulo", defaults.zoneId)
        assertNull(defaults.venue)
        assertNull(defaults.durationMinutes)
        assertNull(defaults.capacity)
        assertNull(defaults.confirmationLeadMinutes)
        assertNull(defaults.gameFeeCents)
    }

    @Test
    fun `defaults produce an empty new-game draft form rather than a null one`() {
        val draft = GameEditorInput("group-1", configuredGroup().toGameEditorDefaults())
            .toGameEditorDraft("command-key")

        assertNull(draft.gameId)
        assertEquals("Vôlei da Quinta", draft.form.title)
        assertEquals("24", draft.form.capacity)
        assertEquals("", draft.form.localDate)
    }

    private fun configuredGroup() = Group(
        id = GroupId("group-1"),
        name = "Vôlei da Quinta",
        timeZone = GroupTimeZone("America/Sao_Paulo"),
        version = 1,
        role = GroupRole.OWNER,
        profile = GroupProfile(
            modality = null,
            composition = null,
            description = null,
            city = null,
            level = null,
            customLevel = null,
            playStyle = null,
            customPlayStyle = null,
            defaultVenue = GroupVenue("venue-1", "Arena Central", "Rua Central 100", "Quadra 2"),
            regularSlots = listOf(GroupRegularSlot(null, GroupWeekday.THURSDAY, "19:30", 90)),
            defaultCapacity = 24,
            defaultConfirmationLeadMinutes = 180,
        ),
        financeDefaults = GroupFinanceDefaults(
            defaultGameFeeCents = 2500,
            monthlyFeeCents = null,
            monthlyDueDay = null,
        ),
    )
}
