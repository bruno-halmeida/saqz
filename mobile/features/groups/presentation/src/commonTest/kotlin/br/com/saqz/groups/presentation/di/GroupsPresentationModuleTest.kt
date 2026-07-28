package br.com.saqz.groups.presentation.di

import androidx.lifecycle.SavedStateHandle
import br.com.saqz.groups.presentation.details.GroupDetailsViewModel
import br.com.saqz.groups.presentation.list.GroupListViewModel
import br.com.saqz.groups.presentation.members.GroupMembersViewModel
import br.com.saqz.groups.presentation.schedule.GroupScheduleViewModel
import br.com.saqz.groups.presentation.setup.GroupSetupMode
import br.com.saqz.groups.presentation.setup.GroupSetupViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication

/**
 * VUL-72 — a costura que o projeto de dados vai herdar, verificada dos dois lados.
 *
 * Mora aqui, e não em `SaqzKoinModulesTest`, porque `GroupScheduleViewModel` é `internal`
 * ao módulo: o `:compose-app` não consegue nomeá-la. Lá o mesmo grafo é exercitado junto
 * das outras definições do app, com as quatro que atravessam a fronteira pública.
 *
 * O `SavedStateHandle` entra pelo `parametersOf` porque em app quem o entrega é o
 * `AndroidParametersHolder` do Koin, a partir do `CreationExtras` do `NavEntry` — sem
 * `ViewModelStoreOwner` num teste puro, não há de onde ele vir.
 */
class GroupsPresentationModuleTest {

    @Test
    fun resolvesTheFiveScreensWithTheArgumentsTheRoutePasses() {
        val app = koinApplication { modules(groupsPresentationModule(sampleContent = true)) }
        val koin = app.koin

        koin.get<GroupListViewModel>()
        koin.get<GroupSetupViewModel> { parametersOf(GroupSetupMode.Create, SavedStateHandle()) }
        koin.get<GroupDetailsViewModel> { parametersOf("ceret") }
        koin.get<GroupMembersViewModel> { parametersOf("ceret") }
        assertEquals("ceret", koin.get<GroupScheduleViewModel> { parametersOf("ceret") }.groupId)

        app.close()
    }

    /** O `mode` do `2i` chega pela rota, e é ele que faz a tela abrir em modo de edição. */
    @Test
    fun setupOpensInEditModeWhenTheRouteCarriesAGroupId() {
        val app = koinApplication { modules(groupsPresentationModule(sampleContent = true)) }
        val koin = app.koin

        val editing = koin.get<GroupSetupViewModel> {
            parametersOf(GroupSetupMode.Edit("ceret"), SavedStateHandle())
        }
        assertTrue(editing.state.value.isEditing)
        assertEquals(GroupSetupMode.Edit("ceret"), editing.state.value.mode)

        app.close()
    }

    /**
     * Nenhuma das cinco pode abrir presa no skeleton: quatro `State` nascem com
     * `isLoading = true` e **nenhuma ViewModel carrega dado** neste projeto, então quem
     * constrói é quem tira o esqueleto. Vale para os dois lados da flag.
     */
    @Test
    fun noScreenOpensStuckOnTheSkeleton() {
        listOf(true, false).forEach { sampleContent ->
            val app = koinApplication { modules(groupsPresentationModule(sampleContent)) }
            val koin = app.koin

            assertFalse(koin.get<GroupListViewModel>().state.value.isLoading, "lista/$sampleContent")
            assertFalse(
                koin.get<GroupSetupViewModel> {
                    parametersOf(GroupSetupMode.Create, SavedStateHandle())
                }.state.value.isLoading,
                "formulário/$sampleContent",
            )
            assertFalse(
                koin.get<GroupDetailsViewModel> { parametersOf("ceret") }.state.value.isLoading,
                "detalhe/$sampleContent",
            )
            assertFalse(
                koin.get<GroupMembersViewModel> { parametersOf("ceret") }.state.value.isLoading,
                "membros/$sampleContent",
            )
            assertFalse(
                koin.get<GroupScheduleViewModel> { parametersOf("ceret") }.state.value.isLoading,
                "agenda/$sampleContent",
            )

            app.close()
        }
    }

    /**
     * Em dev a jornada precisa ser percorrível: sem cartão na lista não há como abrir o
     * detalhe, e o detalhe precisa da visão de admin para levar a Membros e a Agenda.
     */
    @Test
    fun devOpensWithContentTheJourneyCanBeWalkedOn() {
        val app = koinApplication { modules(groupsPresentationModule(sampleContent = true)) }
        val koin = app.koin

        assertTrue(koin.get<GroupListViewModel>().state.value.groups.isNotEmpty())
        assertTrue(koin.get<GroupDetailsViewModel> { parametersOf("ceret") }.state.value.isAdmin)

        app.close()
    }

    /** Em prod o estado chega resolvido e vazio — sem gateway, é o que existe de verdade. */
    @Test
    fun prodOpensResolvedAndEmpty() {
        val app = koinApplication { modules(groupsPresentationModule(sampleContent = false)) }
        val koin = app.koin

        val list = koin.get<GroupListViewModel>().state.value
        assertTrue(list.groups.isEmpty())
        assertTrue(list.isEmpty)
        assertEquals(0, koin.get<GroupMembersViewModel> { parametersOf("ceret") }.state.value.totalCount)

        app.close()
    }
}
