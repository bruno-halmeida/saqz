package br.com.saqz.groups.presentation.di

import androidx.lifecycle.SavedStateHandle
import br.com.saqz.groups.presentation.details.GroupDetailsViewModel
import br.com.saqz.groups.presentation.list.GroupListViewModel
import br.com.saqz.groups.presentation.members.GroupMembersViewModel
import br.com.saqz.groups.presentation.schedule.GroupScheduleViewModel
import br.com.saqz.groups.presentation.setup.GroupSetupMode
import br.com.saqz.groups.presentation.setup.GroupSetupState
import br.com.saqz.groups.presentation.setup.GroupSetupViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * VUL-72 — com o que cada tela abre, verificado **sem container Koin**: o AGENTS.md §7 dá
 * essa exceção a um único teste, o `SaqzKoinModulesTest`, e é lá que a resolução do grafo
 * é exercida. Aqui as ViewModels são construídas direto, com o mesmo [GroupsInitialState]
 * que o `groupsPresentationModule` passa a elas.
 *
 * O `SavedStateHandle` do formulário se instancia direto, sem mock (AGENTS.md §10).
 */
class GroupsInitialStateTest {

    /**
     * Nenhuma das cinco pode abrir presa no skeleton: quatro `State` nascem com
     * `isLoading = true` e **nenhuma ViewModel carrega dado** neste projeto, então quem
     * constrói é quem tira o esqueleto. Vale para os dois lados da flag.
     */
    @Test
    fun noScreenOpensStuckOnTheSkeleton() {
        listOf(true, false).forEach { sample ->
            assertFalse(GroupsInitialState.list(sample).isLoading, "lista/$sample")
            assertFalse(GroupsInitialState.details(sample).isLoading, "detalhe/$sample")
            assertFalse(GroupsInitialState.members(sample).isLoading, "membros/$sample")
            assertFalse(GroupsInitialState.schedule(sample).isLoading, "agenda/$sample")
            // O formulário já nascia resolvido; a asserção existe para que uma mudança de
            // default no `GroupSetupState` não passe silenciosa.
            assertFalse(GroupSetupState(mode = GroupSetupMode.Create).isLoading, "formulário/$sample")
        }
    }

    /**
     * Em dev a jornada precisa ser percorrível: sem cartão na lista não há como abrir o
     * detalhe, e o detalhe precisa da visão de admin para levar a Membros e a Agenda.
     */
    @Test
    fun devOpensWithContentTheJourneyCanBeWalkedOn() {
        assertTrue(GroupsInitialState.list(sample = true).groups.isNotEmpty())
        assertTrue(GroupsInitialState.details(sample = true).isAdmin)
        assertTrue(GroupsInitialState.members(sample = true).admins.isNotEmpty())
        assertTrue(GroupsInitialState.schedule(sample = true).upcoming.isNotEmpty())
    }

    /** Em prod o estado chega resolvido e vazio — sem gateway, é o que existe de verdade. */
    @Test
    fun prodOpensResolvedAndEmpty() {
        val list = GroupsInitialState.list(sample = false)
        assertTrue(list.groups.isEmpty())
        assertTrue(list.isEmpty)
        assertEquals(0, GroupsInitialState.members(sample = false).totalCount)
        assertTrue(GroupsInitialState.schedule(sample = false).upcoming.isEmpty())
    }

    /**
     * As cinco aceitam o que o `groupsPresentationModule` entrega — o argumento da rota
     * mais o estado inicial. É a mesma chamada das definições, sem o Koin no meio.
     */
    @Test
    fun everyViewModelTakesItsRouteArgumentAndInitialState() {
        assertFalse(GroupListViewModel(GroupsInitialState.list(true)).state.value.isLoading)
        assertFalse(
            GroupDetailsViewModel("ceret", GroupsInitialState.details(true)).state.value.isLoading,
        )
        assertFalse(
            GroupMembersViewModel("ceret", GroupsInitialState.members(true)).state.value.isLoading,
        )
        assertEquals(
            "ceret",
            GroupScheduleViewModel("ceret", GroupsInitialState.schedule(true)).groupId,
        )
        val editing = GroupSetupViewModel(
            GroupSetupState(mode = GroupSetupMode.Edit("ceret")),
            SavedStateHandle(),
        )
        assertTrue(editing.state.value.isEditing)
    }
}
