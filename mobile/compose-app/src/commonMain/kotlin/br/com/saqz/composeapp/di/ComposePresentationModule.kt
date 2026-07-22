package br.com.saqz.composeapp.di

import br.com.saqz.composeapp.navigation.AccessOrchestrator
import br.com.saqz.composeapp.navigation.AccessViewModel
import br.com.saqz.composeapp.navigation.GroupsNavigationViewModel
import br.com.saqz.composeapp.navigation.RequestIdGenerator
import br.com.saqz.composeapp.navigation.UuidV4RequestIdGenerator
import br.com.saqz.groups.data.GroupRoleDto
import br.com.saqz.groups.presentation.games.detail.GameDetailViewModel
import br.com.saqz.groups.presentation.InviteToolStateMachine
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
    val testScope: CoroutineScope? = null,
)

internal data class GameDetailViewModelParameters(
    val groupId: String,
    val gameId: String,
    val role: GroupRoleDto,
    val testScope: CoroutineScope? = null,
)

internal val composePresentationModule = module {
    single<RequestIdGenerator> { UuidV4RequestIdGenerator() }
    factory { parameters ->
        InviteToolStateMachine(
            roles = get(),
            groupId = { get<br.com.saqz.groups.presentation.GroupAdministrationStateMachine>().state.value.group?.group?.id },
            scope = parameters.get<CoroutineScope>(),
        )
    }
    factory { parameters ->
        AccessOrchestrator(
            auth = get(),
            localAccessState = get(),
            groupProfileGateway = get(),
            groupPhotoGateway = get(),
            sessionInvalidator = get(),
            authentication = get(),
            session = get(),
            selection = get(),
            administration = get(),
            inviteTools = get { parametersOf(parameters.get<CoroutineScope>()) },
            invites = get(),
            attendanceLinks = get(),
            attendanceDestinations = get(),
            requestIds = get(),
            scope = parameters.get<CoroutineScope>(),
        )
    }
    viewModel {
        AccessViewModel { scope ->
            get<AccessOrchestrator> { parametersOf(scope) }
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
            testScope = input.testScope,
        )
    }
    viewModel { parameters ->
        val input = parameters.get<GameDetailViewModelParameters>()
        GameDetailViewModel(
            gateway = get(),
            groupId = input.groupId,
            gameId = input.gameId,
            role = input.role,
            testScope = input.testScope,
            attendanceGateway = get(),
            attendanceShareGateway = get(),
        )
    }
}
