package br.com.saqz.composeapp.di

import br.com.saqz.composeapp.SaqzAppDependencies
import br.com.saqz.composeapp.navigation.AccessViewModel
import br.com.saqz.composeapp.navigation.GroupsNavigationViewModel
import br.com.saqz.groups.data.GroupProfileGateway
import br.com.saqz.groups.data.GroupRoleDto
import br.com.saqz.groups.data.attendance.AttendanceGateway
import br.com.saqz.groups.data.game.GameGateway
import br.com.saqz.groups.port.GroupDraftStorePort
import br.com.saqz.groups.port.GroupSystemTimeZonePort
import br.com.saqz.groups.presentation.games.detail.GameDetailViewModel
import br.com.saqz.groups.presentation.setup.GroupCommandKeyFactory
import br.com.saqz.groups.presentation.setup.GroupSetupInput
import br.com.saqz.groups.presentation.setup.GroupSetupViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

internal data class GroupSetupViewModelParameters(
    val input: GroupSetupInput,
    val gateway: GroupProfileGateway,
    val timeZones: GroupSystemTimeZonePort,
    val drafts: GroupDraftStorePort,
    val commandKeys: GroupCommandKeyFactory,
)

internal data class GameDetailViewModelParameters(
    val gateway: GameGateway,
    val groupId: String,
    val gameId: String,
    val role: GroupRoleDto,
    val attendanceGateway: AttendanceGateway,
)

internal val composePresentationModule = module {
    viewModel { parameters -> AccessViewModel(parameters.get<SaqzAppDependencies>()) }
    viewModelOf(::GroupsNavigationViewModel)
    viewModel { parameters ->
        val input = parameters.get<GroupSetupViewModelParameters>()
        GroupSetupViewModel(
            input = input.input,
            gateway = input.gateway,
            timeZones = input.timeZones,
            drafts = input.drafts,
            commandKeys = input.commandKeys,
        )
    }
    viewModel { parameters ->
        val input = parameters.get<GameDetailViewModelParameters>()
        GameDetailViewModel(
            gateway = input.gateway,
            groupId = input.groupId,
            gameId = input.gameId,
            role = input.role,
            attendanceGateway = input.attendanceGateway,
        )
    }
}
