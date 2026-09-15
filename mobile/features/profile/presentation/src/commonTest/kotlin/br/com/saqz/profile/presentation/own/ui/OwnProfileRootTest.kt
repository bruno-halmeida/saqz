package br.com.saqz.profile.presentation.own.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.profile.fake.FakeProfileGateway
import br.com.saqz.profile.presentation.own.OwnProfileViewModel
import br.com.saqz.profile.presentation.own.OwnProfileIntent
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class OwnProfileRootTest {
    @Test
    fun receiptsEntryRoutesWhenAuthorizedEvenWithoutPlanOwnership() = runComposeUiTest {
        val gateway = FakeProfileGateway().apply { profile = profile.copy(memberships = emptyList()) }
        val viewModel = OwnProfileViewModel(gateway)
        var opens = 0
        setContent {
            val context = LocalPlatformContext.current
            val imageLoader = remember(context) { ImageLoader.Builder(context).build() }
            SaqzTheme {
                OwnProfileRoot(onOpenEditor = {}, onOpenPasswordRecovery = {}, onSignOut = {},
                    onOpenReceipts = { opens++ }, isPlanOwner = false, viewModel = viewModel, imageLoader = imageLoader)
            }
        }
        onNodeWithTag(OwnProfileTags.Receipts).performScrollTo().performClick()
        waitForIdle()
        assertEquals(1, opens)
    }

    @Test
    fun receiptsEntryDisappearsAndStopsRoutingWhenAuthorizationIsRemoved() = runComposeUiTest {
        val gateway = FakeProfileGateway().apply { profile = profile.copy(memberships = emptyList()) }
        val viewModel = OwnProfileViewModel(gateway)
        var opens = 0
        var onOpenReceipts by mutableStateOf<(() -> Unit)?>({ opens++ })
        setContent {
            val context = LocalPlatformContext.current
            val imageLoader = remember(context) { ImageLoader.Builder(context).build() }
            SaqzTheme {
                OwnProfileRoot(
                    onOpenEditor = {}, onOpenPasswordRecovery = {}, onSignOut = {},
                    onOpenReceipts = onOpenReceipts, viewModel = viewModel, imageLoader = imageLoader,
                )
            }
        }
        onNodeWithTag(OwnProfileTags.Receipts).performScrollTo().performClick()
        waitForIdle()
        assertEquals(1, opens)

        runOnIdle { onOpenReceipts = null }
        onNodeWithTag(OwnProfileTags.MonthlyPayments).performScrollTo().assertExists()
        onNodeWithTag(OwnProfileTags.Receipts).assertDoesNotExist()
        runOnIdle { viewModel.onIntent(OwnProfileIntent.OpenReceipts) }
        waitForIdle()
        assertEquals(1, opens)
    }

    @Test
    fun `refresh version reloads the retained profile after an editor save`() = runComposeUiTest {
        val gateway = FakeProfileGateway()
        val viewModel = OwnProfileViewModel(gateway)
        var refreshVersion by mutableIntStateOf(0)

        setContent {
            val context = LocalPlatformContext.current
            val imageLoader = remember(context) { ImageLoader.Builder(context).build() }
            SaqzTheme {
                OwnProfileRoot(
                    onOpenEditor = {},
                    onOpenPasswordRecovery = {},
                    onSignOut = {},
                    refreshVersion = refreshVersion,
                    viewModel = viewModel,
                    imageLoader = imageLoader,
                )
            }
        }

        waitForIdle()
        gateway.profile = gateway.profile.copy(
            user = gateway.profile.user.copy(
                displayName = "Novo nome",
                nickname = "Novo apelido",
                city = "Rio de Janeiro",
                photoUrl = "/api/session/photo?v=novo",
            ),
        )
        runOnIdle { refreshVersion = 1 }
        waitForIdle()

        onNodeWithText("Novo nome").assertExists()
        onNodeWithText("Novo apelido · Rio de Janeiro").assertExists()
        assertEquals("/api/session/photo?v=novo", viewModel.state.value.user?.photoUrl)
    }
}
