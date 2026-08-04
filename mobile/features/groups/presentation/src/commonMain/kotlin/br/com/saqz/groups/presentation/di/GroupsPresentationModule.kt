package br.com.saqz.groups.presentation.di

import br.com.saqz.groups.domain.athlete.AthleteGateway
import br.com.saqz.groups.domain.game.GameGateway
import br.com.saqz.groups.domain.group.GroupGateway
import br.com.saqz.groups.domain.group.GroupProfileGateway
import br.com.saqz.groups.domain.membership.GroupMembershipGateway
import br.com.saqz.groups.domain.photo.GroupPhotoEncoderPort
import br.com.saqz.groups.domain.photo.GroupPhotoGateway
import br.com.saqz.groups.domain.photo.GroupPhotoPreviewPort
import br.com.saqz.groups.domain.photo.GroupPhotoSelectionPort
import br.com.saqz.groups.port.GroupSystemTimeZonePort
import br.com.saqz.groups.port.GroupNowPort
import br.com.saqz.groups.presentation.details.GroupDetailsViewModel
import br.com.saqz.groups.presentation.finance.overview.FinanceOverviewViewModel
import br.com.saqz.groups.presentation.gamedetail.GameDetailViewModel
import br.com.saqz.groups.presentation.gameeditor.GameEditorViewModel
import br.com.saqz.groups.presentation.list.GroupListViewModel
import br.com.saqz.groups.presentation.members.GroupMembersViewModel
import br.com.saqz.groups.presentation.newentry.NewEntryViewModel
import br.com.saqz.groups.presentation.photo.GroupPhotoViewModel
import br.com.saqz.groups.presentation.schedule.GroupScheduleViewModel
import br.com.saqz.groups.presentation.setup.GroupSetupMode
import br.com.saqz.groups.presentation.setup.GroupSetupState
import br.com.saqz.groups.presentation.setup.GroupSetupViewModel
import br.com.saqz.groups.presentation.statement.StatementViewModel
import br.com.saqz.groups.presentation.ui.finance.groupcash.GroupCashboxViewModel
import org.koin.core.parameter.ParametersHolder
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Grafo das cinco telas: toda carga real começa na própria ViewModel.
 *
 * `viewModel { }`, e não `viewModelOf`, é intencional. Os argumentos de rota (`groupId` e
 * `GroupSetupMode`) e o `SavedStateHandle` chegam por `parametersOf`; não são dependências
 * globais. O estado é skeleton para as leituras remotas, e cada ViewModel remove-o quando
 * a resposta chega. Conteúdo de amostra pertence apenas às previews e às capturas.
 */
fun groupsPresentationModule(): Module = module {
    viewModel { GroupListViewModel(get(), get(), get()) }
    viewModel { FinanceOverviewViewModel(get(), get()) }
    viewModel { params ->
        val mode = params.get<GroupSetupMode>()
        GroupSetupViewModel(
            initialState = GroupSetupState(
                mode = mode,
                isLoading = mode is GroupSetupMode.Edit,
            ),
            savedState = params.get(),
            groupGateway = get(),
            profileGateway = get(),
            timeZonePort = get<GroupSystemTimeZonePort>(),
        )
    }
    viewModel { params -> StatementViewModel(params.get(), get()) }
    viewModel { params -> NewEntryViewModel(params.get(), params.get(), get(), get<GroupNowPort>()) }
    viewModel {
        params ->
        GroupDetailsViewModel(params.get(), get(), get(), get(), get(), get(), get(), get<GroupNowPort>())
    }
    viewModel { params -> GroupCashboxViewModel(params.get(), get(), get(), get(), get(), get<GroupNowPort>()) }
    viewModel { params -> GroupMembersViewModel(params.get(), get(), get(), get()) }
    viewModel { params -> GroupScheduleViewModel(params.get(), get(), get()) }
    viewModel { params ->
        val (groupId, gameId) = gameEditorRouteArguments(params)
        GameEditorViewModel(groupId, gameId, params.get(), get(), get())
    }
    viewModel { params -> GameDetailViewModel(params.get(), params.get(), get(), get(), get(), get()) }
    viewModel {
        GroupPhotoViewModel(
            profileGateway = get(),
            photoGateway = get<GroupPhotoGateway>(),
            selection = get<GroupPhotoSelectionPort>(),
            encoder = get<GroupPhotoEncoderPort>(),
            previews = get<GroupPhotoPreviewPort>(),
        )
    }
}

internal fun gameEditorRouteArguments(params: ParametersHolder): Pair<String, String?> {
    val groupId: String = params[0]
    val gameId: String? = params[1]
    return groupId to gameId
}
