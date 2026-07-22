package br.com.saqz.composeapp.di

import br.com.saqz.groups.data.GroupApi
import br.com.saqz.groups.data.GroupGateway
import br.com.saqz.groups.data.GroupPhotoApi
import br.com.saqz.groups.data.GroupPhotoGateway
import br.com.saqz.groups.data.GroupProfileGateway
import br.com.saqz.groups.data.RolesInvitesApi
import br.com.saqz.groups.data.RolesInvitesGateway
import br.com.saqz.groups.data.attendance.AttendanceApi
import br.com.saqz.groups.data.attendance.AttendanceGateway
import br.com.saqz.groups.data.attendance.share.AttendanceShareApi
import br.com.saqz.groups.data.attendance.share.AttendanceShareGateway
import br.com.saqz.groups.data.game.GameApi
import br.com.saqz.groups.data.game.GameGateway
import br.com.saqz.groups.port.DefaultGroupSystemTimeZonePort
import br.com.saqz.groups.port.GroupSystemTimeZonePort
import br.com.saqz.groups.presentation.DeferredInviteStateMachine
import br.com.saqz.groups.presentation.GroupAdministrationStateMachine
import br.com.saqz.groups.presentation.GroupSelectionIntent
import br.com.saqz.groups.presentation.GroupSelectionStateMachine
import br.com.saqz.groups.presentation.attendance.share.AttendanceLinkDestination
import br.com.saqz.groups.presentation.attendance.share.DeferredAttendanceLinkStateMachine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.dsl.module
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf

internal class AttendanceDestinationStore {
    private val mutableDestination = MutableStateFlow<AttendanceLinkDestination?>(null)
    val destination: StateFlow<AttendanceLinkDestination?> = mutableDestination.asStateFlow()

    fun publish(value: AttendanceLinkDestination) {
        mutableDestination.value = value
    }

    fun consume() {
        mutableDestination.value = null
    }
}

internal val groupsDataModule = module {
    singleOf(::GroupApi) {
        bind<GroupGateway>()
        bind<GroupProfileGateway>()
    }
    singleOf(::GroupPhotoApi) { bind<GroupPhotoGateway>() }
    singleOf(::RolesInvitesApi) { bind<RolesInvitesGateway>() }
    singleOf(::AttendanceShareApi) { bind<AttendanceShareGateway>() }
    singleOf(::GameApi) { bind<GameGateway>() }
    singleOf(::AttendanceApi) { bind<AttendanceGateway>() }
    singleOf(::DefaultGroupSystemTimeZonePort) { bind<GroupSystemTimeZonePort>() }
}

internal val groupsPresentationModule = module {
    singleOf(::GroupSelectionStateMachine)
    single {
        GroupAdministrationStateMachine(get(), get(), get()) { groupId ->
            get<GroupSelectionStateMachine>().onIntent(GroupSelectionIntent.Select(groupId))
        }
    }
    single {
        DeferredInviteStateMachine(get(), get(), get(), get()) { groupId ->
            get<GroupSelectionStateMachine>().onIntent(GroupSelectionIntent.Select(groupId))
        }
    }
    single { AttendanceDestinationStore() }
    single {
        DeferredAttendanceLinkStateMachine(get(), get(), get(), get()) { destination ->
            get<GroupSelectionStateMachine>().onIntent(GroupSelectionIntent.Select(destination.groupId))
            get<AttendanceDestinationStore>().publish(destination)
        }
    }
}
