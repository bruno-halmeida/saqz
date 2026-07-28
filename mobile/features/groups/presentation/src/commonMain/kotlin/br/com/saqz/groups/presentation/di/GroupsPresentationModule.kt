package br.com.saqz.groups.presentation.di

import br.com.saqz.groups.presentation.details.GroupDetailsState
import br.com.saqz.groups.presentation.details.GroupDetailsViewModel
import br.com.saqz.groups.presentation.list.GroupListState
import br.com.saqz.groups.presentation.list.GroupListViewModel
import br.com.saqz.groups.presentation.members.GroupMembersState
import br.com.saqz.groups.presentation.members.GroupMembersViewModel
import br.com.saqz.groups.presentation.schedule.GroupScheduleState
import br.com.saqz.groups.presentation.schedule.GroupScheduleViewModel
import br.com.saqz.groups.presentation.setup.GroupSetupState
import br.com.saqz.groups.presentation.setup.GroupSetupViewModel
import br.com.saqz.groups.presentation.ui.details.GroupDetailsPreviewData
import br.com.saqz.groups.presentation.ui.list.GroupListSamples
import br.com.saqz.groups.presentation.ui.members.previewMembersState
import br.com.saqz.groups.presentation.ui.schedule.previewScheduleState
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * O grafo das cinco telas de grupo. Mora aqui, e não em `:compose-app`, porque
 * `GroupScheduleViewModel` e `GroupScheduleState` são `internal` ao módulo — o app-shell
 * não consegue nomeá-los. Quem monta continua sendo só o `SaqzKoinBootstrap`, que carrega
 * este módulo (AGENTS.md §7).
 *
 * **Por que `viewModel { }` e não `viewModelOf(::X)` (VUL-72, achado da onda 2).** As cinco
 * ViewModels recebem o estado inicial no construtor, e três recebem também o argumento da
 * rota. `viewModelOf` resolveria cada parâmetro pelo grafo, e não existe — nem deve existir
 * — definição de `GroupSetupState` ou de `String` em Koin. Das duas saídas que o ticket
 * oferece, esta é a explícita: o estado inicial se decide **aqui**, num lugar só, e o
 * `parametersOf` da rota fica visível ao lado dele. Dar `= GroupXState()` de default em
 * cada construtor faria o `viewModelOf` compilar, mas espalharia por cinco arquivos a
 * decisão de com o que a tela abre — e é exatamente essa decisão que o gateway vai herdar.
 *
 * [sampleContent] responde à segunda metade do mesmo achado: quatro das cinco `State`
 * nascem com `isLoading = true`, e como nenhuma ViewModel carrega dado, **quem constrói é
 * quem tira o skeleton**. Registrar o estado cru deixaria as quatro telas em esqueleto
 * permanente. Ver [GroupsInitialState] para o que cada lado recebe e por quê.
 */
fun groupsPresentationModule(sampleContent: Boolean): Module = module {
    viewModel { GroupsInitialState.list(sampleContent).let(::GroupListViewModel) }
    // `params.get<SavedStateHandle>()` e não `get()`: quem entrega o handle é o
    // `AndroidParametersHolder` do Koin, a partir do `CreationExtras` do `NavEntry` — ele
    // nunca esteve no grafo. O `mode` vem do `parametersOf` que o `GroupSetupRoot` passa.
    viewModel { params -> GroupSetupViewModel(GroupSetupState(mode = params.get()), params.get()) }
    viewModel { params -> GroupDetailsViewModel(params.get(), GroupsInitialState.details(sampleContent)) }
    viewModel { params -> GroupMembersViewModel(params.get(), GroupsInitialState.members(sampleContent)) }
    viewModel { params -> GroupScheduleViewModel(params.get(), GroupsInitialState.schedule(sampleContent)) }
}

/**
 * Com o que cada tela abre enquanto não há gateway.
 *
 * **Prod: resolvido e vazio.** `isLoading = false` sem conteúdo é o estado honesto de um
 * app que não consulta nada — a lista cai no `2o` (primeiro acesso), e nenhuma tela mente
 * um carregamento que não acontece. O skeleton volta a existir no dia em que houver carga
 * de verdade, e aí ele nasce na ViewModel, não aqui.
 *
 * **Dev: o dado das previews.** O critério de pronto do VUL-72 é percorrer
 * lista → detalhe → membros → agenda no emulador, e com a lista vazia não há cartão para
 * tocar: a jornada seria inalcançável justamente no ticket que existe para torná-la
 * alcançável. Então o flavor dev abre com as mesmas cenas que as previews e as capturas
 * Roborazzi já usam — nenhuma amostra nova, nenhum caminho de código só de teste. A flag é
 * a mesma que já liga o catálogo do design system (`NetworkConfig.environment == Dev`,
 * VUL-51); não se inventou fonte de verdade.
 *
 * É `internal` para que `GroupsInitialStateTest` verifique a decisão direto, sem subir
 * container — quem sobe container é o `SaqzKoinModulesTest`, e só ele (AGENTS.md §7).
 *
 * ponytail: em dev todo `groupId` cai no mesmo grupo de amostra — não há de onde procurar
 * outro. Quando o `GroupGateway` entrar, este objeto some inteiro e cada ViewModel coleta
 * o seu `Flow` no `init`, com guarda de geração.
 */
internal object GroupsInitialState {
    fun list(sample: Boolean) =
        if (sample) GroupListSamples.filled else GroupListState(isLoading = false)

    // A cena de admin, não a de membro: é a visão que desenha o "Gerenciar", e é por ele
    // que o detalhe leva a Membros e a Agenda.
    fun details(sample: Boolean) =
        if (sample) GroupDetailsPreviewData.admin else GroupDetailsState(isLoading = false)

    fun members(sample: Boolean) =
        if (sample) previewMembersState else GroupMembersState(isLoading = false)

    fun schedule(sample: Boolean) =
        if (sample) previewScheduleState else GroupScheduleState(isLoading = false)
}
