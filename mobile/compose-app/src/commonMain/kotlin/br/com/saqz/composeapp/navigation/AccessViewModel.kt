package br.com.saqz.composeapp.navigation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.saqz.groups.data.PersistedRoleDto
import br.com.saqz.groups.data.GroupProfileGateway
import br.com.saqz.groups.data.GroupPhotoGateway
import br.com.saqz.groups.presentation.attendance.share.AttendanceLinkDestination
import br.com.saqz.groups.presentation.attendance.share.DeferredAttendanceLinkIntent
import br.com.saqz.access.presentation.AuthenticationIntent
import br.com.saqz.access.presentation.AuthenticationState
import br.com.saqz.groups.presentation.DeferredInviteIntent
import br.com.saqz.groups.presentation.GroupAdministrationIntent
import br.com.saqz.groups.presentation.GroupAdministrationState
import br.com.saqz.groups.presentation.GroupSelectionIntent
import br.com.saqz.groups.presentation.GroupSelectionState
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.access.presentation.SessionIntent
import br.com.saqz.groups.ui.InviteToolState
import br.com.saqz.groups.ui.CreateGroupUiState
import br.com.saqz.composeapp.SaqzAppDependencies
import br.com.saqz.network.SessionDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

sealed interface AccessIntent {
    data class Authentication(val intent: AuthenticationIntent) : AccessIntent

    data class Session(val intent: SessionIntent) : AccessIntent

    data class Selection(val intent: GroupSelectionIntent) : AccessIntent

    data class Administration(val intent: GroupAdministrationIntent) : AccessIntent

    data class DeferredInvite(val intent: DeferredInviteIntent) : AccessIntent

    data class DeferredAttendance(val intent: DeferredAttendanceLinkIntent) : AccessIntent

    data object OpenCreateGroup : AccessIntent

    data class UpdateCreateName(val value: String) : AccessIntent

    data class UpdateCreateTimeZone(val value: String) : AccessIntent

    data object SubmitCreateGroup : AccessIntent

    data object ClosePage : AccessIntent

    data object SwitchGroup : AccessIntent

    data object OpenSettings : AccessIntent

    data object OpenMemberships : AccessIntent

    data object OpenInvite : AccessIntent

    data object RequestLogout : AccessIntent

    data object ConfirmLogout : AccessIntent

    data object CancelLogout : AccessIntent

    data class UpdateSettingsName(val value: String) : AccessIntent

    data class UpdateSettingsTimeZone(val value: String) : AccessIntent

    data object SaveSettings : AccessIntent

    data object ReloadSettings : AccessIntent

    data object GenerateInvite : AccessIntent

    data class ShareInvite(val url: String) : AccessIntent

    data class ShareFinished(val successful: Boolean) : AccessIntent

    data object RequestExpireInvite : AccessIntent

    data object ConfirmExpireInvite : AccessIntent

    data object CancelExpireInvite : AccessIntent

    data object RetryInvite : AccessIntent

    data class ChangeRole(val userId: String, val role: PersistedRoleDto) : AccessIntent
}

sealed interface AccessUiEffect {
    data class RequestShare(val text: String) : AccessUiEffect

    data class OpenAttendanceGame(val gameId: String) : AccessUiEffect
}

@Immutable
internal data class AccessUiState(
    val authObserved: Boolean = false,
    val authentication: AuthenticationState = AuthenticationState(),
    val session: SessionAccessState = SessionAccessState.SignedOut,
    val selection: GroupSelectionState = GroupSelectionState.NoGroup,
    val administration: GroupAdministrationState = GroupAdministrationState(),
    val page: AccessPage = AccessPage.CONTEXT,
    val createName: String = "",
    val createTimeZone: String = "",
    val createValidationAttempted: Boolean = false,
    val createFlowKey: String = "",
    val settingsName: String = "",
    val settingsTimeZone: String = "",
    val invite: InviteToolState = InviteToolState(),
    val showLogoutConfirmation: Boolean = false,
    val showExpireConfirmation: Boolean = false,
)

internal typealias AccessRootSnapshot = AccessUiState

internal sealed interface AccessRuntimeIntent {
    data object Start : AccessRuntimeIntent

    data object Close : AccessRuntimeIntent

    data class Authentication(val intent: AuthenticationIntent) : AccessRuntimeIntent

    data class Session(val intent: SessionIntent) : AccessRuntimeIntent

    data class Selection(val intent: GroupSelectionIntent) : AccessRuntimeIntent

    data class Administration(val intent: GroupAdministrationIntent) : AccessRuntimeIntent

    data class DeferredInvite(val intent: DeferredInviteIntent) : AccessRuntimeIntent

    data class DeferredAttendance(val intent: DeferredAttendanceLinkIntent) : AccessRuntimeIntent

    data object ConsumeAttendanceDestination : AccessRuntimeIntent

    data class ShowGroupSelector(val session: SessionDto) : AccessRuntimeIntent

    data object RotateInvite : AccessRuntimeIntent

    data object ExpireInvite : AccessRuntimeIntent

    data class ShareFinished(val successful: Boolean) : AccessRuntimeIntent

}

internal interface AccessRuntimeContract {
    val authObservedState: StateFlow<Boolean>
    val authenticationState: StateFlow<AuthenticationState>
    val sessionState: StateFlow<SessionAccessState>
    val selectionState: StateFlow<GroupSelectionState>
    val administrationState: StateFlow<GroupAdministrationState>
    val inviteToolState: StateFlow<InviteToolState>
    val attendanceDestinationState: StateFlow<AttendanceLinkDestination?>
    val groupProfileGateway: GroupProfileGateway
    val groupPhotoGateway: GroupPhotoGateway

    fun onIntent(intent: AccessRuntimeIntent)

    fun newRequestId(): String
}

internal class AccessViewModel private constructor(
    runtimeFactory: (CoroutineScope) -> AccessRuntimeContract,
    testScope: CoroutineScope?,
) : ViewModel() {
    constructor(dependencies: SaqzAppDependencies) : this(
        runtimeFactory = { AccessRuntime(dependencies, it) },
        testScope = null,
    )

    internal constructor(runtime: AccessRuntimeContract, scope: CoroutineScope) : this(
        runtimeFactory = { runtime },
        testScope = scope,
    )

    private val scope = testScope ?: viewModelScope
    private val runtime = runtimeFactory(scope)
    private val routeState = MutableStateFlow(RouteState(createRequestId = runtime.newRequestId()))
    private val effectChannel = Channel<AccessUiEffect>(Channel.BUFFERED)
    val effects: Flow<AccessUiEffect> = effectChannel.receiveAsFlow()
    internal val groupProfileGateway: GroupProfileGateway get() = runtime.groupProfileGateway
    internal val groupPhotoGateway: GroupPhotoGateway get() = runtime.groupPhotoGateway
    private var closed = false

    private val coreState = combine(
        runtime.authObservedState,
        runtime.authenticationState,
        runtime.sessionState,
        runtime.selectionState,
        runtime.administrationState,
    ) { authObserved, authentication, session, selection, administration ->
        CoreState(authObserved, authentication, session, selection, administration)
    }

    val state: StateFlow<AccessUiState> = combine(
        coreState,
        runtime.inviteToolState,
        routeState,
    ) { core, invite, route ->
        AccessUiState(
            authObserved = core.authObserved,
            authentication = core.authentication,
            session = core.session,
            selection = core.selection,
            administration = core.administration,
            page = route.page,
            createName = route.createName,
            createTimeZone = route.createTimeZone,
            createValidationAttempted = route.createValidationAttempted,
            createFlowKey = route.createRequestId,
            settingsName = route.settingsName,
            settingsTimeZone = route.settingsTimeZone,
            invite = invite,
            showLogoutConfirmation = route.showLogoutConfirmation,
            showExpireConfirmation = route.showExpireConfirmation,
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = initialState(),
    )

    init {
        runtime.sessionState.onEach(::reconcileSession).launchIn(scope)
        runtime.selectionState.onEach(::reconcileSelection).launchIn(scope)
        runtime.attendanceDestinationState.onEach(::reconcileAttendanceDestination).launchIn(scope)
        runtime.onIntent(AccessRuntimeIntent.Start)
    }

    fun onIntent(intent: AccessIntent) {
        when (intent) {
            is AccessIntent.Authentication -> runtime.onIntent(AccessRuntimeIntent.Authentication(intent.intent))
            is AccessIntent.Session -> runtime.onIntent(AccessRuntimeIntent.Session(intent.intent))
            is AccessIntent.Selection -> runtime.onIntent(AccessRuntimeIntent.Selection(intent.intent))
            is AccessIntent.Administration -> runtime.onIntent(AccessRuntimeIntent.Administration(intent.intent))
            is AccessIntent.DeferredInvite -> runtime.onIntent(AccessRuntimeIntent.DeferredInvite(intent.intent))
            is AccessIntent.DeferredAttendance -> runtime.onIntent(AccessRuntimeIntent.DeferredAttendance(intent.intent))
            AccessIntent.OpenCreateGroup -> openCreateGroup()
            is AccessIntent.UpdateCreateName -> updateRoute { copy(createName = intent.value) }
            is AccessIntent.UpdateCreateTimeZone -> updateRoute { copy(createTimeZone = intent.value) }
            AccessIntent.SubmitCreateGroup -> submitCreateGroup()
            AccessIntent.ClosePage -> updateRoute { copy(page = AccessPage.CONTEXT) }
            AccessIntent.SwitchGroup -> switchGroup()
            AccessIntent.OpenSettings -> openSettings()
            AccessIntent.OpenMemberships -> openMemberships()
            AccessIntent.OpenInvite -> updateRoute { copy(page = AccessPage.INVITE) }
            AccessIntent.RequestLogout -> updateRoute { copy(showLogoutConfirmation = true) }
            AccessIntent.ConfirmLogout -> confirmLogout()
            AccessIntent.CancelLogout -> updateRoute { copy(showLogoutConfirmation = false) }
            is AccessIntent.UpdateSettingsName -> updateRoute { copy(settingsName = intent.value) }
            is AccessIntent.UpdateSettingsTimeZone -> updateRoute { copy(settingsTimeZone = intent.value) }
            AccessIntent.SaveSettings -> saveSettings()
            AccessIntent.ReloadSettings -> loadSettingsFromGroup()
            AccessIntent.GenerateInvite,
            AccessIntent.RetryInvite,
            -> rotateInvite()
            is AccessIntent.ShareInvite -> effectChannel.trySend(AccessUiEffect.RequestShare(intent.url))
            is AccessIntent.ShareFinished -> runtime.onIntent(AccessRuntimeIntent.ShareFinished(intent.successful))
            AccessIntent.RequestExpireInvite -> updateRoute { copy(showExpireConfirmation = true) }
            AccessIntent.ConfirmExpireInvite -> confirmExpireInvite()
            AccessIntent.CancelExpireInvite -> updateRoute { copy(showExpireConfirmation = false) }
            is AccessIntent.ChangeRole -> runtime.onIntent(
                AccessRuntimeIntent.Administration(GroupAdministrationIntent.ChangeRole(intent.userId, intent.role)),
            )
        }
    }

    internal fun newCommandKey(): String = runtime.newRequestId()

    override fun onCleared() {
        closeRuntimeOnce()
    }

    internal fun clearForTest() {
        closeRuntimeOnce()
    }

    private fun initialState(): AccessUiState = AccessUiState(
        authObserved = runtime.authObservedState.value,
        authentication = runtime.authenticationState.value,
        session = runtime.sessionState.value,
        selection = runtime.selectionState.value,
        administration = runtime.administrationState.value,
        invite = runtime.inviteToolState.value,
    )

    private fun reconcileSession(session: SessionAccessState) {
        runtime.onIntent(AccessRuntimeIntent.DeferredInvite(DeferredInviteIntent.SetSessionReady(session is SessionAccessState.Ready)))
        runtime.onIntent(AccessRuntimeIntent.DeferredAttendance(DeferredAttendanceLinkIntent.SetSessionReady(session is SessionAccessState.Ready)))
        when (session) {
            is SessionAccessState.Ready -> runtime.onIntent(
                AccessRuntimeIntent.Selection(GroupSelectionIntent.Reconcile(session.session)),
            )
            SessionAccessState.SignedOut -> updateRoute {
                copy(page = AccessPage.CONTEXT, showLogoutConfirmation = false)
            }
            else -> Unit
        }
    }

    private fun reconcileSelection(selection: GroupSelectionState) {
        val selected = selection as? GroupSelectionState.Selected ?: return
        runtime.onIntent(
            AccessRuntimeIntent.Administration(GroupAdministrationIntent.SetGroup(selected.group)),
        )
        reconcileAttendanceDestination(runtime.attendanceDestinationState.value)
        updateRoute { copy(page = AccessPage.CONTEXT) }
    }

    private fun reconcileAttendanceDestination(destination: AttendanceLinkDestination?) {
        val selected = runtime.selectionState.value as? GroupSelectionState.Selected ?: return
        destination
            ?.takeIf { it.groupId == selected.group.group.id }
            ?.let {
                effectChannel.trySend(AccessUiEffect.OpenAttendanceGame(it.gameId))
                runtime.onIntent(AccessRuntimeIntent.ConsumeAttendanceDestination)
            }
    }

    private fun openCreateGroup() {
        updateRoute {
            copy(
                page = AccessPage.CREATE_GROUP,
                createName = "",
                createTimeZone = "",
                createValidationAttempted = false,
                createRequestId = runtime.newRequestId(),
            )
        }
    }

    private fun submitCreateGroup() {
        val route = routeState.value
        updateRoute { copy(createValidationAttempted = true) }
        val form = CreateGroupUiState(
            administration = runtime.administrationState.value,
            name = route.createName,
            timeZone = route.createTimeZone,
            validationAttempted = true,
        )
        if (!form.isValid) return
        if (runtime.administrationState.value.isLoading) return
        runtime.onIntent(
            AccessRuntimeIntent.Administration(
                GroupAdministrationIntent.CreateGroup(
                    requestId = route.createRequestId,
                    name = route.createName,
                    timeZone = route.createTimeZone,
                ),
            ),
        )
    }

    private fun switchGroup() {
        val ready = runtime.sessionState.value as? SessionAccessState.Ready ?: return
        updateRoute { copy(page = AccessPage.CONTEXT) }
        runtime.onIntent(AccessRuntimeIntent.ShowGroupSelector(ready.session))
    }

    private fun openSettings() {
        val group = runtime.administrationState.value.group?.group ?: return
        updateRoute {
            copy(
                page = AccessPage.SETTINGS,
                settingsName = group.name,
                settingsTimeZone = group.timeZone,
            )
        }
    }

    private fun openMemberships() {
        runtime.onIntent(AccessRuntimeIntent.Administration(GroupAdministrationIntent.LoadMemberships))
        updateRoute { copy(page = AccessPage.MEMBERSHIPS) }
    }

    private fun confirmLogout() {
        updateRoute { copy(showLogoutConfirmation = false) }
        runtime.onIntent(AccessRuntimeIntent.DeferredInvite(DeferredInviteIntent.Logout))
        runtime.onIntent(AccessRuntimeIntent.DeferredAttendance(DeferredAttendanceLinkIntent.Logout))
        runtime.onIntent(AccessRuntimeIntent.Session(SessionIntent.Logout))
    }

    private fun saveSettings() {
        val route = routeState.value
        runtime.onIntent(
            AccessRuntimeIntent.Administration(
                GroupAdministrationIntent.UpdateSettings(route.settingsName, route.settingsTimeZone),
            ),
        )
    }

    private fun loadSettingsFromGroup() {
        val group = runtime.administrationState.value.group?.group ?: return
        updateRoute { copy(settingsName = group.name, settingsTimeZone = group.timeZone) }
    }

    private fun rotateInvite() {
        if (runtime.inviteToolState.value.isLoading) return
        runtime.onIntent(AccessRuntimeIntent.RotateInvite)
    }

    private fun confirmExpireInvite() {
        updateRoute { copy(showExpireConfirmation = false) }
        runtime.onIntent(AccessRuntimeIntent.ExpireInvite)
    }

    private fun closeRuntimeOnce() {
        if (closed) return
        closed = true
        runtime.onIntent(AccessRuntimeIntent.Close)
    }

    private fun updateRoute(transform: RouteState.() -> RouteState) {
        routeState.update(transform)
    }

    private data class CoreState(
        val authObserved: Boolean,
        val authentication: AuthenticationState,
        val session: SessionAccessState,
        val selection: GroupSelectionState,
        val administration: GroupAdministrationState,
    )

    private data class RouteState(
        val page: AccessPage = AccessPage.CONTEXT,
        val createName: String = "",
        val createTimeZone: String = "",
        val createValidationAttempted: Boolean = false,
        val createRequestId: String,
        val settingsName: String = "",
        val settingsTimeZone: String = "",
        val showLogoutConfirmation: Boolean = false,
        val showExpireConfirmation: Boolean = false,
    )
}
