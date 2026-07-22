package br.com.saqz.composeapp.navigation

import br.com.saqz.access.domain.port.AuthState
import br.com.saqz.access.domain.port.AuthStateListener
import br.com.saqz.access.domain.port.Cancelable
import br.com.saqz.access.domain.port.LocalAccessStatePort
import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.port.OperationResult
import br.com.saqz.access.domain.port.ResultCallback
import br.com.saqz.access.domain.session.AccessSession
import br.com.saqz.access.domain.session.AccessMembership
import br.com.saqz.access.domain.session.SessionInvalidator
import br.com.saqz.access.presentation.AuthenticationIntent
import br.com.saqz.access.presentation.AuthenticationState
import br.com.saqz.access.presentation.AuthenticationStateMachine
import br.com.saqz.access.presentation.AuthTransition
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.access.presentation.SessionAccessStateMachine
import br.com.saqz.access.presentation.SessionIntent
import br.com.saqz.groups.domain.photo.GroupPhotoGateway
import br.com.saqz.groups.domain.group.GroupProfileGateway
import br.com.saqz.groups.presentation.DeferredInviteIntent
import br.com.saqz.groups.presentation.DeferredInviteStateMachine
import br.com.saqz.groups.presentation.GroupAdministrationIntent
import br.com.saqz.groups.presentation.GroupAdministrationState
import br.com.saqz.groups.presentation.GroupAdministrationStateMachine
import br.com.saqz.groups.presentation.GroupSelectionIntent
import br.com.saqz.groups.presentation.GroupSelectionMembership
import br.com.saqz.groups.presentation.GroupSelectionState
import br.com.saqz.groups.presentation.GroupSelectionStateMachine
import br.com.saqz.groups.presentation.InviteToolState
import br.com.saqz.groups.presentation.InviteToolStateMachine
import br.com.saqz.groups.presentation.attendance.share.AttendanceLinkDestination
import br.com.saqz.groups.presentation.attendance.share.DeferredAttendanceLinkIntent
import br.com.saqz.groups.presentation.attendance.share.DeferredAttendanceLinkStateMachine
import br.com.saqz.composeapp.di.AttendanceDestinationStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

internal data class AccessOrchestratorState(
    val authObserved: Boolean,
    val authentication: AuthenticationState,
    val session: SessionAccessState,
    val selection: GroupSelectionState,
    val administration: GroupAdministrationState,
    val invite: InviteToolState,
)

internal sealed interface AccessOrchestratorEffect {
    data class OpenAttendanceGame(val gameId: String) : AccessOrchestratorEffect
}

internal class AccessOrchestrator(
    private val auth: NativeAuthPort,
    private val localAccessState: LocalAccessStatePort,
    override val groupProfileGateway: GroupProfileGateway,
    override val groupPhotoGateway: GroupPhotoGateway,
    override val sessionInvalidator: SessionInvalidator,
    private val authentication: AuthenticationStateMachine,
    private val session: SessionAccessStateMachine,
    private val selection: GroupSelectionStateMachine,
    private val administration: GroupAdministrationStateMachine,
    private val inviteTools: InviteToolStateMachine,
    private val invites: DeferredInviteStateMachine,
    private val attendanceLinks: DeferredAttendanceLinkStateMachine,
    private val attendanceDestinations: AttendanceDestinationStore,
    private val requestIds: RequestIdGenerator,
    scope: CoroutineScope,
) : AccessRuntimeContract {
    override val attendanceDestinationState = attendanceDestinations.destination
    override val inviteToolState = inviteTools.state
    private val mutableAuthObservedState = MutableStateFlow(false)
    override val authObservedState = mutableAuthObservedState.asStateFlow()
    override val authenticationState = authentication.state
    override val sessionState = session.state
    override val selectionState = selection.state
    override val administrationState = administration.state
    private val effectChannel = Channel<AccessOrchestratorEffect>(Channel.BUFFERED)
    override val effects: Flow<AccessOrchestratorEffect> = effectChannel.receiveAsFlow()
    private val coreState = combine(
        authObservedState,
        authenticationState,
        sessionState,
        selectionState,
        administrationState,
    ) { authObserved, authentication, session, selection, administration ->
        AccessOrchestratorState(authObserved, authentication, session, selection, administration, inviteToolState.value)
    }
    override val state: StateFlow<AccessOrchestratorState> = combine(coreState, inviteToolState) { core, invite ->
        core.copy(invite = invite)
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = AccessOrchestratorState(
            authObserved = authObservedState.value,
            authentication = authenticationState.value,
            session = sessionState.value,
            selection = selectionState.value,
            administration = administrationState.value,
            invite = inviteToolState.value,
        ),
    )
    private var authSubscription: Cancelable? = null

    init {
        sessionState.onEach(::reconcileSession).launchIn(scope)
        selectionState.onEach(::reconcileSelection).launchIn(scope)
        attendanceDestinationState.onEach(::reconcileAttendanceDestination).launchIn(scope)
    }

    override fun onIntent(intent: AccessRuntimeIntent) {
        when (intent) {
            AccessRuntimeIntent.Start -> start()
            AccessRuntimeIntent.Close -> close()
            is AccessRuntimeIntent.Authentication -> authentication.onIntent(intent.intent)
            is AccessRuntimeIntent.Session -> session.onIntent(intent.intent)
            is AccessRuntimeIntent.Selection -> selection.onIntent(intent.intent)
            is AccessRuntimeIntent.Administration -> administration.onIntent(intent.intent)
            is AccessRuntimeIntent.DeferredInvite -> invites.onIntent(intent.intent)
            is AccessRuntimeIntent.DeferredAttendance -> attendanceLinks.onIntent(intent.intent)
            AccessRuntimeIntent.ConsumeAttendanceDestination -> attendanceDestinations.consume()
            is AccessRuntimeIntent.ShowGroupSelector -> showGroupSelector(intent.session)
            AccessRuntimeIntent.RotateInvite -> inviteTools.rotate()
            AccessRuntimeIntent.ExpireInvite -> inviteTools.expire()
            is AccessRuntimeIntent.ShareFinished -> inviteTools.shareFinished(intent.successful)
        }
    }

    override fun newRequestId(): String = requestIds.next()

    private fun reconcileSession(current: SessionAccessState) {
        invites.onIntent(DeferredInviteIntent.SetSessionReady(current is SessionAccessState.Ready))
        attendanceLinks.onIntent(DeferredAttendanceLinkIntent.SetSessionReady(current is SessionAccessState.Ready))
        if (current is SessionAccessState.Ready) {
            selection.onIntent(GroupSelectionIntent.Reconcile(current.session.toGroupSelectionMemberships()))
        }
    }

    private fun reconcileSelection(current: GroupSelectionState) {
        val selected = current as? GroupSelectionState.Selected ?: return
        administration.onIntent(GroupAdministrationIntent.SetGroup(selected.group))
        reconcileAttendanceDestination(attendanceDestinationState.value)
    }

    private fun reconcileAttendanceDestination(destination: AttendanceLinkDestination?) {
        val selected = selectionState.value as? GroupSelectionState.Selected ?: return
        destination
            ?.takeIf { it.groupId == selected.group.group.id.value }
            ?.let {
                effectChannel.trySend(AccessOrchestratorEffect.OpenAttendanceGame(it.gameId))
                attendanceDestinations.consume()
            }
    }

    private fun start() {
        if (authSubscription != null) return
        authSubscription = auth.observe(object : AuthStateListener {
            override fun onStateChanged(state: AuthState) {
                mutableAuthObservedState.value = true
                when (state) {
                    AuthState.SignedOut -> authentication.onIntent(AuthenticationIntent.ShowLogin)
                    is AuthState.SignedIn -> session.onIntent(
                        SessionIntent.Accept(AuthTransition.Authenticated(state.user)),
                    )
                }
            }
        })
        invites.onIntent(DeferredInviteIntent.Start)
        invites.onIntent(DeferredInviteIntent.Restore)
        attendanceLinks.onIntent(DeferredAttendanceLinkIntent.Start)
        attendanceLinks.onIntent(DeferredAttendanceLinkIntent.Restore)
    }

    private fun close() {
        authSubscription?.cancel()
        authSubscription = null
        invites.onIntent(DeferredInviteIntent.Stop)
        attendanceLinks.onIntent(DeferredAttendanceLinkIntent.Stop)
    }

    private fun showGroupSelector(session: AccessSession) {
        localAccessState.writeSelectedGroupId(null, object : ResultCallback {
            override fun complete(result: OperationResult) {
                selection.onIntent(GroupSelectionIntent.Reconcile(session.toGroupSelectionMemberships()))
            }
        })
    }
}

internal fun AccessSession.toGroupSelectionMemberships(): List<GroupSelectionMembership> =
    memberships.toGroupSelectionMemberships()

internal fun List<AccessMembership>.toGroupSelectionMemberships(): List<GroupSelectionMembership> = map { membership ->
    GroupSelectionMembership(
        groupId = membership.groupId.value,
        groupName = membership.groupName,
        role = membership.role.value,
    )
}
