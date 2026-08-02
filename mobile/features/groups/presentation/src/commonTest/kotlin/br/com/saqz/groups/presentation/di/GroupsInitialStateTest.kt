package br.com.saqz.groups.presentation.di

import br.com.saqz.groups.presentation.details.GroupDetailsState
import br.com.saqz.groups.presentation.list.GroupListState
import br.com.saqz.groups.presentation.members.GroupMembersState
import br.com.saqz.groups.presentation.schedule.GroupScheduleState
import br.com.saqz.groups.presentation.setup.GroupSetupMode
import br.com.saqz.groups.presentation.setup.GroupSetupState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
}
