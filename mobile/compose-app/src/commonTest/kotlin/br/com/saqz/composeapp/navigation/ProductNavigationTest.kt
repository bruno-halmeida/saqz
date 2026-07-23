package br.com.saqz.composeapp.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.access.domain.session.AccessMembership
import br.com.saqz.access.domain.session.AccessMembershipRole
import br.com.saqz.access.domain.session.AccessSession
import br.com.saqz.access.domain.session.AccessUser
import br.com.saqz.access.presentation.AuthenticationState
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.composeapp.home.AuthenticatedHomeTag
import br.com.saqz.composeapp.startTestSaqzKoin
import br.com.saqz.composeapp.stopTestSaqzKoin
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.domain.GroupId
import br.com.saqz.groups.presentation.GroupAdministrationState
import br.com.saqz.groups.presentation.GroupSelectionState
import kotlin.test.Test

/**
 * T23 composition-root integration: the post-Wave-2 orchestrator, Koin route factories,
 * AppHome slot, and [ProductNavigationHost] resolve through a single DI graph and drive
 * the correct entry set. Rendered through the real [startTestSaqzKoin] graph (one DI/network
 * graph — done-when #3) so the whole binding chain is exercised, not mocked.
 */
@OptIn(ExperimentalTestApi::class)
class ProductNavigationTest {

    // ACCESSNAV-04: a non-Ready session keeps the ACCESS entry set (Login), never the
    // authenticated Home/Groups entries.
    @Test
    fun signedOutRendersAccessLoginNotAuthenticatedHome() = withKoin {
        setContent {
            SaqzTheme {
                ProductNavigation(
                    state = signedOut,
                    onIntent = {},
                )
            }
        }
        waitForIdle()

        onNodeWithTag("login-submit").assertExists()
        onNodeWithTag(AuthenticatedHomeTag).assertDoesNotExist()
    }

    // ACCESSNAV-04: reaching Ready switches the active entry set to the authenticated
    // Home entry instead of pushing Groups onto the access stack.
    @Test
    fun readySwitchesToAuthenticatedHomeEntries() = withKoin {
        setContent {
            SaqzTheme {
                ProductNavigation(
                    state = ready,
                    onIntent = {},
                )
            }
        }
        waitForIdle()

        onNodeWithTag(AuthenticatedHomeTag).assertExists()
        onNodeWithTag("login-submit").assertDoesNotExist()
    }

    // RESTORE-02: logout clears the authenticated stacks; the host disposes the
    // authenticated entries and reconciles back to the access Login route.
    @Test
    fun logoutClearsAuthenticatedStacksBackToLogin() = withKoin {
        var snapshot by mutableStateOf(ready)
        setContent {
            SaqzTheme {
                ProductNavigation(
                    state = snapshot,
                    onIntent = {},
                )
            }
        }
        waitForIdle()
        onNodeWithTag(AuthenticatedHomeTag).assertExists()

        runOnIdle { snapshot = signedOut }
        waitForIdle()

        onNodeWithTag("login-submit").assertExists()
        onNodeWithTag(AuthenticatedHomeTag).assertDoesNotExist()
    }

    private fun withKoin(block: ComposeUiTest.() -> Unit) = runComposeUiTest {
        startTestSaqzKoin()
        try {
            block()
        } finally {
            stopTestSaqzKoin()
        }
    }

    private companion object {
        val session = AccessSession(
            user = AccessUser("user", "user@example.test", "User"),
            memberships = listOf(AccessMembership(GroupId("alpha"), "Alpha", AccessMembershipRole("OWNER"))),
        )
        val signedOut = AccessRootSnapshot(
            authObserved = true,
            authentication = AuthenticationState(),
            session = SessionAccessState.SignedOut,
            selection = GroupSelectionState.NoGroup,
            administration = GroupAdministrationState(),
        )
        val ready = signedOut.copy(session = SessionAccessState.Ready(session))
    }
}
