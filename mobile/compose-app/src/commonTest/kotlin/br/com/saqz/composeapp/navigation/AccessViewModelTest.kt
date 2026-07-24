package br.com.saqz.composeapp.navigation

import br.com.saqz.access.domain.session.AccessMembership
import br.com.saqz.access.domain.session.AccessMembershipRole
import br.com.saqz.access.domain.session.AccessSession
import br.com.saqz.access.domain.session.AccessUser
import br.com.saqz.access.domain.session.SessionInvalidator
import br.com.saqz.access.presentation.AuthScreen
import br.com.saqz.access.presentation.AuthenticationIntent
import br.com.saqz.access.presentation.AuthenticationState
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.access.presentation.SessionIntent
import br.com.saqz.domain.GroupId
import br.com.saqz.groups.domain.group.GroupProfileGateway
import br.com.saqz.groups.domain.photo.GroupPhotoGateway
import br.com.saqz.groups.presentation.DeferredInviteIntent
import br.com.saqz.groups.presentation.GroupAdministrationIntent
import br.com.saqz.groups.presentation.GroupAdministrationState
import br.com.saqz.groups.presentation.GroupSelectionIntent
import br.com.saqz.groups.presentation.GroupSelectionState
import br.com.saqz.groups.presentation.InviteToolState
import br.com.saqz.groups.presentation.attendance.share.AttendanceLinkDestination
import br.com.saqz.groups.presentation.attendance.share.DeferredAttendanceLinkIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Orchestrator-scope tests (T24). Per-route screen behaviors formerly asserted here
 * migrated with their state to the route adapters and are covered by
 * GroupSetupViewModelTest (create form), GroupAdministrationRouteViewModelTest
 * (settings/memberships), GroupInviteRouteViewModelTest (invite/share/expire), and
 * GroupHomeRouteViewModelTest (logout confirmation dialog).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccessViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `V1 initialization starts runtime once and background scope owns collectors`() = runTest(mainDispatcher) {
        val runtime = FakeRuntime().apply {
            authObserved.value = true
            authentication.value = AuthenticationState(screen = AuthScreen.REGISTRATION, email = "person@example.test")
        }

        val viewModel = AccessViewModel { runtime }
        runCurrent()

        assertEquals(1, runtime.intents.count { it == AccessRuntimeIntent.Start })
        assertTrue(viewModel.state.value.authObserved)
        assertEquals(AuthScreen.REGISTRATION, viewModel.state.value.authentication.screen)
        assertEquals("person@example.test", viewModel.state.value.authentication.email)
    }

    @Test
    fun `authentication and session inputs forward exact typed payloads`() = runTest(mainDispatcher) {
        val (viewModel, runtime) = fixture()
        val authentication = AuthenticationIntent.UpdateEmail("new@example.test")
        val session = SessionIntent.UpdateName("Person")

        viewModel.onIntent(AccessIntent.Authentication(authentication))
        viewModel.onIntent(AccessIntent.Session(session))

        assertEquals(
            listOf(AccessRuntimeIntent.Authentication(authentication), AccessRuntimeIntent.Session(session)),
            runtime.intents,
        )
    }

    @Test
    fun `selection administration and deferred invite inputs remain typed`() = runTest(mainDispatcher) {
        val (viewModel, runtime) = fixture()
        val selection = GroupSelectionIntent.Select(GROUP_ID)
        val administration = GroupAdministrationIntent.LoadMemberships
        val invite = DeferredInviteIntent.Retry

        viewModel.onIntent(AccessIntent.Selection(selection))
        viewModel.onIntent(AccessIntent.Administration(administration))
        viewModel.onIntent(AccessIntent.DeferredInvite(invite))

        assertEquals(
            listOf(
                AccessRuntimeIntent.Selection(selection),
                AccessRuntimeIntent.Administration(administration),
                AccessRuntimeIntent.DeferredInvite(invite),
            ),
            runtime.intents,
        )
    }

    @Test
    fun `ready session remains visible to the route`() = runTest(mainDispatcher) {
        val (viewModel, runtime) = fixture()

        runtime.session.value = SessionAccessState.Ready(session)
        runCurrent()

        assertIs<SessionAccessState.Ready>(viewModel.state.value.session)
    }

    @Test
    fun `attendance destination opens exact game once selected group matches`() = runTest(mainDispatcher) {
        val (viewModel, runtime) = fixture()

        runtime.emit(AccessOrchestratorEffect.OpenAttendanceGame("game-42"))
        runCurrent()

        assertEquals(AccessUiEffect.OpenAttendanceGame("game-42"), viewModel.effects.first())
    }

    @Test
    fun `switch group requires ready session and sends authoritative session`() = runTest(mainDispatcher) {
        val (viewModel, runtime) = fixture()

        viewModel.onIntent(AccessIntent.SwitchGroup)
        runtime.session.value = SessionAccessState.Ready(session)
        runCurrent()
        runtime.intents.clear()
        viewModel.onIntent(AccessIntent.SwitchGroup)

        assertEquals(listOf<AccessRuntimeIntent>(AccessRuntimeIntent.ShowGroupSelector(session)), runtime.intents)
    }

    @Test
    fun `confirmed logout routes both cleanup intents before session logout`() = runTest(mainDispatcher) {
        val (viewModel, runtime) = fixture()

        viewModel.onIntent(AccessIntent.ConfirmLogout)
        runCurrent()

        assertEquals(
            listOf(
                AccessRuntimeIntent.DeferredInvite(DeferredInviteIntent.Logout),
                AccessRuntimeIntent.DeferredAttendance(DeferredAttendanceLinkIntent.Logout),
                AccessRuntimeIntent.Session(SessionIntent.Logout),
            ),
            runtime.intents,
        )
    }

    @Test
    fun `clearing viewmodel closes runtime exactly once`() = runTest(mainDispatcher) {
        val (viewModel, runtime) = fixture()

        viewModel.clearForTest()
        viewModel.clearForTest()

        assertEquals(1, runtime.intents.count { it == AccessRuntimeIntent.Close })
    }

    @Test
    fun `session invalidator exposed to route delegates to runtime invalidator`() = runTest(mainDispatcher) {
        val runtime = FakeRuntime()
        val viewModel = AccessViewModel { runtime }

        viewModel.sessionInvalidator.invalidate()

        assertEquals(1, runtime.invalidatorCalls)
    }

    private fun kotlinx.coroutines.test.TestScope.fixture(): Fixture {
        val runtime = FakeRuntime()
        val viewModel = AccessViewModel { runtime }
        runCurrent()
        runtime.intents.clear()
        return Fixture(viewModel, runtime)
    }

    private data class Fixture(val viewModel: AccessViewModel, val runtime: FakeRuntime)

    private class FakeRuntime : AccessRuntimeContract, SessionInvalidator {
        val authObserved = MutableStateFlow(false)
        val authentication = MutableStateFlow(AuthenticationState())
        val session = MutableStateFlow<SessionAccessState>(SessionAccessState.SignedOut)
        val selection = MutableStateFlow<GroupSelectionState>(GroupSelectionState.NoGroup)
        val administration = MutableStateFlow(GroupAdministrationState())
        val invite = MutableStateFlow(InviteToolState())
        val attendanceDestination = MutableStateFlow<AttendanceLinkDestination?>(null)
        private val effectChannel = Channel<AccessOrchestratorEffect>(Channel.BUFFERED)
        val intents = mutableListOf<AccessRuntimeIntent>()
        var invalidatorCalls = 0
        private var requestCounter = 0

        override val authObservedState = authObserved
        override val authenticationState = authentication
        override val sessionState = session
        override val selectionState = selection
        override val administrationState = administration
        override val inviteToolState = invite
        override val attendanceDestinationState = attendanceDestination
        override val effects = effectChannel.receiveAsFlow()
        override val groupProfileGateway: GroupProfileGateway get() = error("not used by AccessViewModel tests")
        override val groupPhotoGateway: GroupPhotoGateway get() = error("not used by AccessViewModel tests")
        override val sessionInvalidator: SessionInvalidator = this

        override fun invalidate() { invalidatorCalls += 1 }

        override fun onIntent(intent: AccessRuntimeIntent) {
            intents += intent
        }

        override fun newRequestId(): String = "request-${++requestCounter}"

        fun emit(effect: AccessOrchestratorEffect) {
            effectChannel.trySend(effect)
        }
    }

    private companion object {
        const val GROUP_ID = "group-id"
        const val USER_ID = "user-id"
        val session = AccessSession(
            user = AccessUser(USER_ID, "person@example.test", "Person"),
            memberships = listOf(AccessMembership(GroupId(GROUP_ID), "Group", AccessMembershipRole("OWNER"))),
        )
    }
}
