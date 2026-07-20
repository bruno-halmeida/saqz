package br.com.saqz.composeapp.navigation

import br.com.saqz.groups.data.GroupDto
import br.com.saqz.groups.data.GroupRoleDto
import br.com.saqz.groups.data.PersistedRoleDto
import br.com.saqz.groups.data.GroupProfileGateway
import br.com.saqz.groups.data.GroupPhotoGateway
import br.com.saqz.groups.data.VersionedGroupDto
import br.com.saqz.access.presentation.AuthScreen
import br.com.saqz.access.presentation.AuthenticationIntent
import br.com.saqz.access.presentation.AuthenticationState
import br.com.saqz.groups.presentation.DeferredInviteIntent
import br.com.saqz.groups.presentation.GroupActions
import br.com.saqz.groups.presentation.GroupAdministrationIntent
import br.com.saqz.groups.presentation.GroupAdministrationState
import br.com.saqz.groups.presentation.GroupSelectionIntent
import br.com.saqz.groups.presentation.GroupSelectionState
import br.com.saqz.groups.presentation.InviteUiError
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.access.presentation.SessionIntent
import br.com.saqz.groups.ui.InviteToolState
import br.com.saqz.network.SessionDto
import br.com.saqz.network.SessionMembershipDto
import br.com.saqz.network.SessionUserDto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AccessViewModelTest {
    @Test
    fun `V1 initialization starts runtime once and background scope owns collectors`() = runTest {
        val runtime = FakeRuntime().apply {
            authObserved.value = true
            authentication.value = AuthenticationState(screen = AuthScreen.REGISTRATION, email = "person@example.test")
        }

        val viewModel = AccessViewModel(runtime, backgroundScope)
        runCurrent()

        assertEquals(1, runtime.intents.count { it == AccessRuntimeIntent.Start })
        assertTrue(viewModel.state.value.authObserved)
        assertEquals(AuthScreen.REGISTRATION, viewModel.state.value.authentication.screen)
        assertEquals("person@example.test", viewModel.state.value.authentication.email)
    }

    @Test
    fun `authentication and session inputs forward exact typed payloads`() = runTest {
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
    fun `selection administration and deferred invite inputs remain typed`() = runTest {
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
    fun `ready session enables invite and reconciles exact memberships`() = runTest {
        val (viewModel, runtime) = fixture()

        runtime.session.value = SessionAccessState.Ready(session)
        runCurrent()

        assertEquals(
            listOf(
                AccessRuntimeIntent.DeferredInvite(DeferredInviteIntent.SetSessionReady(true)),
                AccessRuntimeIntent.Selection(GroupSelectionIntent.Reconcile(session)),
            ),
            runtime.intents,
        )
        assertIs<SessionAccessState.Ready>(viewModel.state.value.session)
    }

    @Test
    fun `signed out session closes page and logout dialog`() = runTest {
        val (viewModel, runtime) = fixture()
        runtime.session.value = SessionAccessState.Ready(session)
        runCurrent()
        viewModel.onIntent(AccessIntent.OpenCreateGroup)
        viewModel.onIntent(AccessIntent.RequestLogout)

        runtime.intents.clear()
        runtime.session.value = SessionAccessState.SignedOut
        runCurrent()

        assertEquals(AccessPage.CONTEXT, viewModel.state.value.page)
        assertFalse(viewModel.state.value.showLogoutConfirmation)
        assertEquals(
            listOf<AccessRuntimeIntent>(
                AccessRuntimeIntent.DeferredInvite(DeferredInviteIntent.SetSessionReady(false)),
            ),
            runtime.intents,
        )
    }

    @Test
    fun `selected group becomes administration context and closes child page`() = runTest {
        val (viewModel, runtime) = fixture()
        viewModel.onIntent(AccessIntent.OpenCreateGroup)

        runtime.selection.value = GroupSelectionState.Selected(group)
        runCurrent()

        assertEquals(AccessPage.CONTEXT, viewModel.state.value.page)
        assertEquals(
            listOf<AccessRuntimeIntent>(
                AccessRuntimeIntent.Administration(GroupAdministrationIntent.SetGroup(group)),
            ),
            runtime.intents,
        )
    }

    @Test
    fun `create form owns values and submits one stable request id`() = runTest {
        val (viewModel, runtime) = fixture()

        viewModel.onIntent(AccessIntent.OpenCreateGroup)
        viewModel.onIntent(AccessIntent.UpdateCreateName("New Group"))
        viewModel.onIntent(AccessIntent.UpdateCreateTimeZone("Europe/Lisbon"))
        viewModel.onIntent(AccessIntent.SubmitCreateGroup)
        runCurrent()

        assertEquals(AccessPage.CREATE_GROUP, viewModel.state.value.page)
        assertEquals("New Group", viewModel.state.value.createName)
        assertEquals("Europe/Lisbon", viewModel.state.value.createTimeZone)
        assertEquals(
            AccessRuntimeIntent.Administration(
                GroupAdministrationIntent.CreateGroup("request-2", "New Group", "Europe/Lisbon"),
            ),
            runtime.intents.single(),
        )
    }

    @Test
    fun `duplicate create submit while pending remains single flight`() = runTest {
        val (viewModel, runtime) = fixture()
        viewModel.onIntent(AccessIntent.OpenCreateGroup)
        viewModel.onIntent(AccessIntent.UpdateCreateName("New Group"))
        viewModel.onIntent(AccessIntent.UpdateCreateTimeZone("Europe/Lisbon"))

        viewModel.onIntent(AccessIntent.SubmitCreateGroup)
        viewModel.onIntent(AccessIntent.SubmitCreateGroup)

        assertEquals(1, runtime.intents.count { it is AccessRuntimeIntent.Administration })
    }

    @Test
    fun `invalid create submit exposes validation without runtime operation`() = runTest {
        val (viewModel, runtime) = fixture()
        viewModel.onIntent(AccessIntent.OpenCreateGroup)

        viewModel.onIntent(AccessIntent.SubmitCreateGroup)
        runCurrent()

        assertTrue(viewModel.state.value.createValidationAttempted)
        assertTrue(runtime.intents.isEmpty())
    }

    @Test
    fun `settings are controlled and save exact edited values`() = runTest {
        val (viewModel, runtime) = fixture()
        runtime.administration.value = ownerAdministration

        viewModel.onIntent(AccessIntent.OpenSettings)
        viewModel.onIntent(AccessIntent.UpdateSettingsName("Renamed"))
        viewModel.onIntent(AccessIntent.UpdateSettingsTimeZone("America/Sao_Paulo"))
        viewModel.onIntent(AccessIntent.SaveSettings)
        runCurrent()

        assertEquals(AccessPage.SETTINGS, viewModel.state.value.page)
        assertEquals("Renamed", viewModel.state.value.settingsName)
        assertEquals("America/Sao_Paulo", viewModel.state.value.settingsTimeZone)
        assertEquals(
            AccessRuntimeIntent.Administration(
                GroupAdministrationIntent.UpdateSettings("Renamed", "America/Sao_Paulo"),
            ),
            runtime.intents.single(),
        )
    }

    @Test
    fun `memberships and role changes dispatch exact administration intents`() = runTest {
        val (viewModel, runtime) = fixture()

        viewModel.onIntent(AccessIntent.OpenMemberships)
        viewModel.onIntent(AccessIntent.ChangeRole(USER_ID, PersistedRoleDto.ADMIN))
        runCurrent()

        assertEquals(AccessPage.MEMBERSHIPS, viewModel.state.value.page)
        assertEquals(
            listOf<AccessRuntimeIntent>(
                AccessRuntimeIntent.Administration(GroupAdministrationIntent.LoadMemberships),
                AccessRuntimeIntent.Administration(
                    GroupAdministrationIntent.ChangeRole(USER_ID, PersistedRoleDto.ADMIN),
                ),
            ),
            runtime.intents,
        )
    }

    @Test
    fun `switch group requires ready session and sends authoritative session`() = runTest {
        val (viewModel, runtime) = fixture()

        viewModel.onIntent(AccessIntent.SwitchGroup)
        runtime.session.value = SessionAccessState.Ready(session)
        runCurrent()
        runtime.intents.clear()
        viewModel.onIntent(AccessIntent.SwitchGroup)

        assertEquals(listOf<AccessRuntimeIntent>(AccessRuntimeIntent.ShowGroupSelector(session)), runtime.intents)
    }

    @Test
    fun `confirmed logout clears dialog and routes both cleanup intents`() = runTest {
        val (viewModel, runtime) = fixture()
        viewModel.onIntent(AccessIntent.RequestLogout)
        runCurrent()
        assertTrue(viewModel.state.value.showLogoutConfirmation)

        viewModel.onIntent(AccessIntent.ConfirmLogout)
        runCurrent()

        assertFalse(viewModel.state.value.showLogoutConfirmation)
        assertEquals(
            listOf(
                AccessRuntimeIntent.DeferredInvite(DeferredInviteIntent.Logout),
                AccessRuntimeIntent.Session(SessionIntent.Logout),
            ),
            runtime.intents,
        )
    }

    @Test
    fun `invite generation and retry share one loading guard`() = runTest {
        val (viewModel, runtime) = fixture()

        viewModel.onIntent(AccessIntent.GenerateInvite)
        viewModel.onIntent(AccessIntent.RetryInvite)
        runCurrent()

        assertEquals(listOf<AccessRuntimeIntent>(AccessRuntimeIntent.RotateInvite), runtime.intents)
        assertTrue(viewModel.state.value.invite.isLoading)
    }

    @Test
    fun `share request is buffered once and never replayed`() = runTest {
        val (viewModel, _) = fixture()

        viewModel.onIntent(AccessIntent.ShareInvite("https://example.test/invite"))

        assertEquals(
            AccessUiEffect.RequestShare("https://example.test/invite"),
            viewModel.effects.first(),
        )
        var replayed: AccessUiEffect? = null
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.effects.collect {
                replayed = it
                cancel()
            }
        }
        runCurrent()
        assertNull(replayed)
        collector.cancel()
    }

    @Test
    fun `failed native share result returns through intent and updates state`() = runTest {
        val (viewModel, runtime) = fixture()

        viewModel.onIntent(AccessIntent.ShareFinished(successful = false))
        runCurrent()

        assertEquals(listOf<AccessRuntimeIntent>(AccessRuntimeIntent.ShareFinished(false)), runtime.intents)
        assertEquals(InviteUiError.UNAVAILABLE, viewModel.state.value.invite.error)
    }

    @Test
    fun `clearing viewmodel closes runtime exactly once`() = runTest {
        val (viewModel, runtime) = fixture()

        viewModel.clearForTest()
        viewModel.clearForTest()

        assertEquals(1, runtime.intents.count { it == AccessRuntimeIntent.Close })
    }

    private suspend fun kotlinx.coroutines.test.TestScope.fixture(): Fixture {
        val runtime = FakeRuntime()
        val viewModel = AccessViewModel(runtime, backgroundScope)
        runCurrent()
        runtime.intents.clear()
        return Fixture(viewModel, runtime)
    }

    private data class Fixture(val viewModel: AccessViewModel, val runtime: FakeRuntime)

    private class FakeRuntime : AccessRuntimeContract {
        val authObserved = MutableStateFlow(false)
        val authentication = MutableStateFlow(AuthenticationState())
        val session = MutableStateFlow<SessionAccessState>(SessionAccessState.SignedOut)
        val selection = MutableStateFlow<GroupSelectionState>(GroupSelectionState.NoGroup)
        val administration = MutableStateFlow(GroupAdministrationState())
        val invite = MutableStateFlow(InviteToolState())
        val intents = mutableListOf<AccessRuntimeIntent>()
        private var requestCounter = 0

        override val authObservedState = authObserved
        override val authenticationState = authentication
        override val sessionState = session
        override val selectionState = selection
        override val administrationState = administration
        override val inviteToolState = invite
        override val groupProfileGateway: GroupProfileGateway get() = error("not used by AccessViewModel tests")
        override val groupPhotoGateway: GroupPhotoGateway get() = error("not used by AccessViewModel tests")

        override fun onIntent(intent: AccessRuntimeIntent) {
            intents += intent
            when (intent) {
                is AccessRuntimeIntent.Administration -> {
                    if (intent.intent is GroupAdministrationIntent.CreateGroup) {
                        administration.value = administration.value.copy(isLoading = true)
                    }
                }
                AccessRuntimeIntent.RotateInvite -> invite.value = invite.value.copy(isLoading = true)
                is AccessRuntimeIntent.ShareFinished -> if (!intent.successful) {
                    invite.value = invite.value.copy(error = InviteUiError.UNAVAILABLE)
                }
                else -> Unit
            }
        }

        override fun newRequestId(): String = "request-${++requestCounter}"
    }

    private companion object {
        const val GROUP_ID = "group-id"
        const val USER_ID = "user-id"
        val session = SessionDto(
            user = SessionUserDto(USER_ID, "person@example.test", "Person"),
            memberships = listOf(SessionMembershipDto(GROUP_ID, "Group", "OWNER")),
        )
        val group = VersionedGroupDto(
            group = GroupDto(GROUP_ID, "Group", "UTC", 7, GroupRoleDto.OWNER),
            etag = "\"7\"",
        )
        val ownerAdministration = GroupAdministrationState(
            group = group,
            actions = GroupActions(true, true, true),
        )
    }
}
