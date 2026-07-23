package br.com.saqz.composeapp.di

import br.com.saqz.groups.data.photo.KtorGroupPhotoGateway
import br.com.saqz.groups.domain.photo.GroupPhotoGateway
import br.com.saqz.groups.data.group.KtorGroupGateway
import br.com.saqz.groups.data.membership.KtorGroupMembershipGateway
import br.com.saqz.groups.data.attendance.share.KtorAttendanceSharingGateway
import br.com.saqz.groups.data.attendance.KtorAttendanceGateway
import br.com.saqz.groups.domain.attendance.AttendanceGateway
import br.com.saqz.groups.domain.attendance.share.AttendanceSharingGateway
import br.com.saqz.groups.domain.group.GroupGateway
import br.com.saqz.groups.domain.group.GroupProfileGateway
import br.com.saqz.groups.domain.membership.GroupMembershipGateway
import br.com.saqz.groups.data.game.KtorGameGateway
import br.com.saqz.groups.domain.game.GameGateway
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
    single { KtorGroupGateway(get()) }
    single<GroupGateway> { get<KtorGroupGateway>() }
    single<GroupProfileGateway> { get<KtorGroupGateway>() }
    single { KtorGroupMembershipGateway(get()) }
    single<GroupMembershipGateway> { get<KtorGroupMembershipGateway>() }
    single { KtorAttendanceSharingGateway(get()) }
    single<AttendanceSharingGateway> { get<KtorAttendanceSharingGateway>() }
    single { KtorGroupPhotoGateway(get()) }
    single<GroupPhotoGateway> { get<KtorGroupPhotoGateway>() }
    single { KtorGameGateway(get()) }
    single<GameGateway> { get<KtorGameGateway>() }
    single { KtorAttendanceGateway(get()) }
    single<AttendanceGateway> { get<KtorAttendanceGateway>() }
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
