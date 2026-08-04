package br.com.saqz.groups.presentation.di

import br.com.saqz.groups.presentation.details.GroupDetailsState
import br.com.saqz.groups.presentation.list.GroupListState
import br.com.saqz.groups.presentation.members.GroupMembersState
import br.com.saqz.groups.presentation.schedule.GroupScheduleState
import br.com.saqz.groups.presentation.setup.GroupSetupMode
import br.com.saqz.groups.presentation.setup.GroupSetupState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.koin.core.parameter.parametersOf

/**
 * O grafo agora entrega ViewModels que iniciam a própria carga. O estado inicial real é
 * skeleton para leituras remotas; a amostra continua somente nas previews/screenshot tests.
 */
class GroupsInitialStateTest {
    @Test
    fun `remote screens start in the loading state`() {
        assertTrue(GroupListState().isLoading)
        assertTrue(GroupDetailsState().isLoading)
        assertTrue(GroupMembersState().isLoading)
        assertTrue(GroupScheduleState().isLoading)
    }

    @Test
    fun `create form starts ready while edit form starts loading`() {
        assertFalse(GroupSetupState(GroupSetupMode.Create).isLoading)
        assertTrue(GroupSetupState(GroupSetupMode.Edit("group-1"), isLoading = true).isLoading)
    }

    @Test
    fun `game editor keeps nullable route id by position`() {
        val create = gameEditorRouteArguments(parametersOf("group-1", null))
        val edit = gameEditorRouteArguments(parametersOf("group-1", "game-1"))

        assertEquals("group-1", create.first)
        assertNull(create.second)
        assertEquals("group-1", edit.first)
        assertEquals("game-1", edit.second)
    }
}
