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
import br.com.saqz.groups.port.GroupAttendanceSharePort
import br.com.saqz.groups.port.GroupPhotoEncoderPort
import br.com.saqz.groups.port.GroupPhotoPreviewPort
import br.com.saqz.groups.port.GroupPhotoSelectionPort
import br.com.saqz.groups.port.LocalGroupStatePort
import br.com.saqz.groups.port.NativeGroupLinkPort
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

internal val groupsModule = module {
    single<GroupAttendanceSharePort> { get<SaqzNativePorts>().attendanceShare }
    single<GroupPhotoSelectionPort> { get<SaqzNativePorts>().groupPhotoSelection }
    single<GroupPhotoEncoderPort> { get<SaqzNativePorts>().groupPhotoEncoder }
    single<GroupPhotoPreviewPort> { get<SaqzNativePorts>().groupPhotoPreviews }
    single<NativeGroupLinkPort> { get<SaqzNativePorts>().groupLinks }
    single<LocalGroupStatePort> { get<SaqzNativePorts>().localGroupState }

    single { GroupApi(get()) }
    single<GroupGateway> { get<GroupApi>() }
    single<GroupProfileGateway> { get<GroupApi>() }
    single<GroupPhotoGateway> { GroupPhotoApi(get()) }
    single { RolesInvitesApi(get()) }
    single<RolesInvitesGateway> { get<RolesInvitesApi>() }
    single<AttendanceShareGateway> { AttendanceShareApi(get()) }
    single<GameGateway> { GameApi(get()) }
    single<AttendanceGateway> { AttendanceApi(get()) }

    single { GroupSelectionStateMachine(get(), get(), get()) }
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
