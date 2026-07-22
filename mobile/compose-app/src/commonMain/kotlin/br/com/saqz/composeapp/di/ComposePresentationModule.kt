package br.com.saqz.composeapp.di

import br.com.saqz.composeapp.navigation.AccessRuntime
import br.com.saqz.composeapp.navigation.AccessViewModel
import br.com.saqz.composeapp.navigation.GroupsNavigationViewModel
import br.com.saqz.groups.data.GroupRoleDto
import br.com.saqz.groups.presentation.games.detail.GameDetailViewModel
import br.com.saqz.groups.presentation.setup.GroupCommandKeyFactory
import br.com.saqz.groups.presentation.setup.GroupSetupInput
import br.com.saqz.groups.presentation.setup.GroupSetupViewModel
import kotlinx.coroutines.CoroutineScope
import org.koin.core.parameter.parametersOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

internal data class GroupSetupViewModelParameters(
    val input: GroupSetupInput,
    val commandKeys: GroupCommandKeyFactory,
)

internal data class GameDetailViewModelParameters(
    val groupId: String,
    val gameId: String,
    val role: GroupRoleDto,
)

internal val composePresentationModule = module {
    factory { parameters ->
        AccessRuntime(
            auth = get(),
            localAccessState = get(),
            groupProfileGateway = get(),
            groupPhotoGateway = get(),
            sessionInvalidator = get(),
            roles = get(),
            authentication = get(),
            session = get(),
            selection = get(),
            administration = get(),
            invites = get(),
            attendanceLinks = get(),
            attendanceDestinations = get(),
            scope = parameters.get<CoroutineScope>(),
        )
    }
    viewModel {
        AccessViewModel { scope ->
            get<AccessRuntime> { parametersOf(scope) }
        }
    }
    viewModelOf(::GroupsNavigationViewModel)
    viewModel { parameters ->
        val input = parameters.get<GroupSetupViewModelParameters>()
        GroupSetupViewModel(
            input = input.input,
            gateway = get(),
            timeZones = get(),
            drafts = get(),
            commandKeys = input.commandKeys,
        )
    }
    viewModel { parameters ->
        val input = parameters.get<GameDetailViewModelParameters>()
        GameDetailViewModel(
            gateway = get(),
            groupId = input.groupId,
            gameId = input.gameId,
            role = input.role,
            attendanceGateway = get(),
            attendanceShareGateway = get(),
        )
    }
}
