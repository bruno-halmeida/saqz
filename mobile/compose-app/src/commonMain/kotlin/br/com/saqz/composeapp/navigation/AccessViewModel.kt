package br.com.saqz.composeapp.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.saqz.groups.data.GroupProfileGateway
import br.com.saqz.groups.data.GroupPhotoGateway
import br.com.saqz.groups.presentation.attendance.share.AttendanceLinkDestination
import br.com.saqz.groups.presentation.attendance.share.DeferredAttendanceLinkIntent
import br.com.saqz.groups.presentation.DeferredInviteIntent
import br.com.saqz.groups.presentation.GroupAdministrationIntent
import br.com.saqz.groups.presentation.GroupSelectionIntent
import br.com.saqz.groups.presentation.GroupSelectionState
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.access.presentation.SessionIntent
import br.com.saqz.groups.presentation.InviteToolState
import br.com.saqz.groups.ui.CreateGroupUiState
import br.com.saqz.network.SessionInvalidator
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

internal class AccessViewModel private constructor(
    runtimeFactory: (CoroutineScope) -> AccessRuntimeContract,
    testScope: CoroutineScope?,
) : ViewModel() {
    internal constructor(runtimeFactory: (CoroutineScope) -> AccessRuntimeContract) : this(
        runtimeFactory = runtimeFactory,
        testScope = null,
    )

    internal constructor(runtime: AccessRuntimeContract, scope: CoroutineScope) : this(
        runtimeFactory = { runtime },
        testScope = scope,
    )

    private val scope = testScope ?: viewModelScope
    private val runtime = runtimeFactory(scope)
    private val routeState = MutableStateFlow(AccessRouteState(createRequestId = runtime.newRequestId()))
    private val effectChannel = Channel<AccessUiEffect>(Channel.BUFFERED)
    val effects: Flow<AccessUiEffect> = effectChannel.receiveAsFlow()
    internal val groupProfileGateway: GroupProfileGateway get() = runtime.groupProfileGateway
    internal val groupPhotoGateway: GroupPhotoGateway get() = runtime.groupPhotoGateway
    internal val sessionInvalidator: SessionInvalidator get() = runtime.sessionInvalidator
    private var closed = false

    private val coreState = combine(
        runtime.authObservedState,
        runtime.authenticationState,
        runtime.sessionState,
        runtime.selectionState,
        runtime.administrationState,
    ) { authObserved, authentication, session, selection, administration ->
        AccessCoreState(authObserved, authentication, session, selection, administration)
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
        runtime.sessionState.onEach { session ->
            if (session == SessionAccessState.SignedOut) {
                updateRoute { copy(page = AccessPage.CONTEXT, showLogoutConfirmation = false) }
            }
        }.launchIn(scope)
        runtime.selectionState.onEach { selection ->
            if (selection is GroupSelectionState.Selected) updateRoute { copy(page = AccessPage.CONTEXT) }
        }.launchIn(scope)
        runtime.effects.onEach { effect ->
            if (effect is AccessOrchestratorEffect.OpenAttendanceGame) {
                effectChannel.trySend(AccessUiEffect.OpenAttendanceGame(effect.gameId))
            }
        }.launchIn(scope)
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

    private fun updateRoute(transform: AccessRouteState.() -> AccessRouteState) {
        routeState.update(transform)
    }
}
