package br.com.saqz.composeapp.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import br.com.saqz.access.data.GroupDto
import br.com.saqz.access.data.GroupRoleDto
import br.com.saqz.access.data.VersionedGroupDto
import br.com.saqz.access.presentation.AuthScreen
import br.com.saqz.access.presentation.AuthenticationIntent
import br.com.saqz.access.presentation.AuthenticationState
import br.com.saqz.access.presentation.GroupActions
import br.com.saqz.access.presentation.GroupAdministrationState
import br.com.saqz.access.presentation.GroupSelectionState
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.access.port.NativeSharePort
import br.com.saqz.access.port.OperationResult
import br.com.saqz.access.port.ResultCallback
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.network.SessionDto
import br.com.saqz.network.SessionMembershipDto
import br.com.saqz.network.SessionUserDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class AuthenticatedAccessRootTest {
    @Test
    fun `starting renders one loading destination`() = runComposeUiTest {
        root(snapshot(authObserved = false))

        onNodeWithContentDescription("Carregando").assertExists()
        assertEquals(1, onAllNodes(hasTestTag(AccessRootTag)).fetchSemanticsNodes().size)
    }

    @Test
    fun `persisted session starts bootstrap without flashing login`() = runComposeUiTest {
        root(snapshot(session = SessionAccessState.Bootstrapping))

        onNodeWithTag("bootstrap-loading").assertExists()
        onNodeWithText("Entrar").assertDoesNotExist()
    }

    @Test
    fun `absence of session renders login without protected shell`() = runComposeUiTest {
        root(snapshot())

        onNodeWithTag("login-submit").assertExists()
        onNodeWithText("Current Group").assertDoesNotExist()
        onNodeWithText("Componentes").assertDoesNotExist()
    }

    @Test
    fun `registration back returns through one callback`() = runComposeUiTest {
        val intents = mutableListOf<AccessIntent>()
        root(
            snapshot(authentication = AuthenticationState(screen = AuthScreen.REGISTRATION)),
            intents::add,
        )

        onNodeWithText("Voltar para entrar").performClick()

        assertEquals(
            listOf<AccessIntent>(AccessIntent.Authentication(AuthenticationIntent.ShowLogin)),
            intents,
        )
    }

    @Test
    fun `password reset back returns through one callback`() = runComposeUiTest {
        val intents = mutableListOf<AccessIntent>()
        root(
            snapshot(authentication = AuthenticationState(screen = AuthScreen.PASSWORD_RESET)),
            intents::add,
        )

        onNodeWithText("Voltar para entrar").performClick()

        assertEquals(
            listOf<AccessIntent>(AccessIntent.Authentication(AuthenticationIntent.ShowLogin)),
            intents,
        )
    }

    @Test
    fun `password reset system back maps to login`() {
        assertEquals(
            AccessIntent.Authentication(AuthenticationIntent.ShowLogin),
            AccessDestination.PASSWORD_RESET.systemBackIntent(),
        )
    }

    @Test
    fun `login does not consume system back`() {
        assertNull(AccessDestination.LOGIN.systemBackIntent())
    }

    @Test
    fun `zero memberships offers only group creation`() = runComposeUiTest {
        val intents = mutableListOf<AccessIntent>()
        root(ready(GroupSelectionState.NoGroup), intents::add)

        onNodeWithText("Voce ainda nao participa de um grupo").assertExists()
        onNodeWithText("Criar grupo").performClick()

        assertEquals(listOf<AccessIntent>(AccessIntent.OpenCreateGroup), intents)
    }

    @Test
    fun `multiple memberships show every group and role`() = runComposeUiTest {
        root(ready(selector))

        onNodeWithText("Alpha").assertExists()
        onNodeWithText("OWNER").assertExists()
        onNodeWithText("Beta").assertExists()
        onNodeWithText("ATHLETE").assertExists()
    }

    @Test
    fun `selector keeps create group available to verified user`() = runComposeUiTest {
        val intents = mutableListOf<AccessIntent>()
        root(ready(selector), intents::add)

        onNodeWithText("Criar grupo").performClick()

        assertEquals(listOf<AccessIntent>(AccessIntent.OpenCreateGroup), intents)
    }

    @Test
    fun `one selected membership renders current group and role`() = runComposeUiTest {
        root(active(ownerAdministration))

        onNodeWithText("Current Group").assertExists()
        onNodeWithText("OWNER").assertExists()
    }

    @Test
    fun `group switch routes through one action`() = runComposeUiTest {
        val intents = mutableListOf<AccessIntent>()
        root(active(ownerAdministration), intents::add)

        onNodeWithTag("context-switch").performClick()

        assertEquals(listOf<AccessIntent>(AccessIntent.SwitchGroup), intents)
    }

    @Test
    fun `switch loading removes previous protected content before new load`() = runComposeUiTest {
        root(ready(GroupSelectionState.Loading("beta")))

        onNodeWithText("Current Group").assertDoesNotExist()
        onNodeWithTag("group-loading").assertExists()
    }

    @Test
    fun `discarded stale selection renders current selector only`() = runComposeUiTest {
        root(ready(selector))

        onNodeWithText("Removed Group").assertDoesNotExist()
        onNodeWithText("Alpha").assertExists()
        onNodeWithText("Beta").assertExists()
    }

    @Test
    fun `owner context exposes settings roles and invite`() = runComposeUiTest {
        root(active(ownerAdministration))

        onNodeWithTag("context-settings").assertExists()
        onNodeWithTag("context-roles").assertExists()
        onNodeWithTag("context-invite").assertExists()
    }

    @Test
    fun `role refresh to athlete removes every privileged action`() = runComposeUiTest {
        root(active(athleteAdministration))

        onNodeWithTag("context-settings").assertDoesNotExist()
        onNodeWithTag("context-roles").assertDoesNotExist()
        onNodeWithTag("context-invite").assertDoesNotExist()
    }

    @Test
    fun `logout replaces protected history with login only`() {
        val stack = AccessDestinationStack(AccessDestination.GROUP_CONTEXT)

        stack.replace(AccessDestination.SETTINGS)
        stack.replace(AccessDestination.LOGIN)

        assertEquals(listOf(AccessDestination.LOGIN), stack.entries)
    }

    @Test
    fun `reselection and repeated auth callbacks never duplicate destinations`() {
        val stack = AccessDestinationStack(AccessDestination.LOGIN)

        repeat(3) {
            stack.replace(AccessDestination.REGISTRATION)
            stack.replace(AccessDestination.LOGIN)
            stack.replace(AccessDestination.LOGIN)
        }

        assertEquals(listOf(AccessDestination.LOGIN), stack.entries)
    }

    @Test
    fun `access resources resolve through umbrella packaging`() = runComposeUiTest {
        root(snapshot())

        onNodeWithText("Organize seu grupo.", substring = true).assertIsDisplayed()
    }

    @Test
    fun `maximum text scale keeps login actions reachable`() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                Box(Modifier) {
                    SaqzTheme { AuthenticatedAccessRoot(snapshot()) {} }
                }
            }
        }

        onNodeWithText("Entrar com Google").performScrollTo().assertIsDisplayed()
        onNodeWithText("Criar conta").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `share effect invokes native adapter once and returns through access intent`() {
        val share = RecordingSharePort(OperationResult.Success)
        val intents = mutableListOf<AccessIntent>()

        handleAccessEffect(AccessUiEffect.RequestShare("https://example.test/invite"), share, intents::add)

        assertEquals(listOf("https://example.test/invite"), share.values)
        assertEquals(listOf<AccessIntent>(AccessIntent.ShareFinished(successful = true)), intents)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.root(
        state: AccessRootSnapshot,
        onIntent: (AccessIntent) -> Unit = {},
    ) = setContent { SaqzTheme { AuthenticatedAccessRoot(state, onIntent) } }

    private class RecordingSharePort(private val result: OperationResult) : NativeSharePort {
        val values = mutableListOf<String>()

        override fun share(text: String, done: ResultCallback) {
            values += text
            done.complete(result)
        }
    }

    private fun snapshot(
        authObserved: Boolean = true,
        authentication: AuthenticationState = AuthenticationState(),
        session: SessionAccessState = SessionAccessState.SignedOut,
    ) = AccessRootSnapshot(
        authObserved = authObserved,
        authentication = authentication,
        session = session,
        selection = GroupSelectionState.NoGroup,
        administration = GroupAdministrationState(),
    )

    private fun ready(selection: GroupSelectionState) = snapshot(session = SessionAccessState.Ready(session)).copy(
        selection = selection,
    )

    private fun active(administration: GroupAdministrationState) = ready(GroupSelectionState.Selected(group)).copy(
        administration = administration,
    )

    private companion object {
        val session = SessionDto(
            user = SessionUserDto("user", "user@example.test", "User"),
            memberships = listOf(
                SessionMembershipDto("alpha", "Alpha", "OWNER"),
                SessionMembershipDto("beta", "Beta", "ATHLETE"),
            ),
        )
        val selector = GroupSelectionState.Selector(session.memberships)
        val group = VersionedGroupDto(
            GroupDto("current", "Current Group", "America/Sao_Paulo", 1, GroupRoleDto.OWNER),
            "\"1\"",
        )
        val ownerAdministration = GroupAdministrationState(
            group = group,
            actions = GroupActions(true, true, true),
        )
        val athleteAdministration = GroupAdministrationState(
            group = group.copy(group = group.group.copy(role = GroupRoleDto.ATHLETE)),
            actions = GroupActions(false, false, false),
        )
    }
}
