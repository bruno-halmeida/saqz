package br.com.saqz.groups.presentation.ui.invite

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.onboarding_invite_message
import org.jetbrains.compose.resources.stringResource
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.invite.GroupInviteState
import br.com.saqz.groups.presentation.invite.InvitePreviewState
import br.com.saqz.groups.presentation.invite.InvitePreviewIntent
import br.com.saqz.groups.presentation.invite.InviteQrState
import br.com.saqz.groups.presentation.invite.InviteStatus
import br.com.saqz.groups.presentation.invite.JoinedAtUnit
import br.com.saqz.groups.presentation.invite.PendingEntryRequestUi
import br.com.saqz.groups.presentation.invite.RecentMemberUi
import br.com.saqz.groups.presentation.invite.renderQr
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7, application = Application::class)
class GroupInviteScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test
    @Config(qualifiers = "+h1800dp")
    fun screen3a() = capture("invite-3a") {
        GroupInviteScreen(
            state = GroupInviteState(
                isLoading = false,
                groupName = "Vôlei do CERET",
                inviteStatus = InviteStatus.Active,
                expiresLabel = "09/08",
                inviteUrl = "https://saqz.app/invite/ceret",
                entryRequiresApproval = true,
                pendingRequests = listOf(PendingEntryRequestUi("u1", "Ana Lima", "01/08 · 19:30")),
                recentMembers = listOf(RecentMemberUi("u2", "Bruno", 2, JoinedAtUnit.Hours)),
            ),
            onBack = {},
            onIntent = {},
        )
    }

    @Test
    fun permanentInvite() {
        capture("invite-permanent") {
            GroupInviteScreen(
                state = GroupInviteState(isLoading = false, groupName = "Vôlei do CERET", inviteStatus = InviteStatus.Active,
                    inviteUrl = "https://saqz.app/invite/ceret"),
                onBack = {}, onIntent = {},
            )
        }
        compose.onNodeWithText("Link não expira").assertIsDisplayed()
    }

    @Test
    fun emptyPermanentInvite() {
        capture("invite-empty-permanent") {
            GroupInviteScreen(
                state = GroupInviteState(isLoading = false, groupName = "Vôlei do CERET", inviteStatus = InviteStatus.Empty),
                onBack = {}, onIntent = {},
            )
        }
        compose.onNodeWithText("O convite não expira. Você pode desativá-lo quando quiser.").assertIsDisplayed()
    }

    @Test
    fun screen3b() = capture("invite-3b") {
        InvitePreviewMessageScreen(
            state = InvitePreviewState("Vôlei do CERET", "https://saqz.app/invite/ceret", "Vem jogar com a gente!"),
            onIntent = {},
            onBack = {},
        )
    }

    @Test
    fun defaultOnboardingMessageIsReadableAcrossMultipleLines() {
        capture("invite-onboarding-message") {
            InvitePreviewMessageScreen(
                state = InvitePreviewState("Vôlei do CERET", "https://saqz.app/invite/ceret",
                    stringResource(Res.string.onboarding_invite_message, "Vôlei do CERET")),
                onIntent = {}, onBack = {},
            )
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNode(hasSetTextAction()).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("The invitation must wrap instead of hiding text in one horizontal line", layouts.single().lineCount > 1)
        compose.onNodeWithText("Enviar no WhatsApp").assertIsDisplayed()
    }

    @Test
    fun restrictedHeightAllowsScrollingToShareAndCountsEmojiConsistently() {
        val intents = mutableListOf<InvitePreviewIntent>()
        compose.setContent {
            SaqzTheme {
                Box(Modifier.height(300.dp)) {
                    InvitePreviewMessageScreen(
                        state = InvitePreviewState("CERET", "https://saqz.app/invite/ceret", "🏐".repeat(300)),
                        onIntent = intents::add, onBack = {},
                    )
                }
            }
        }
        compose.onNodeWithText("Enviar no WhatsApp").assertIsNotDisplayed()
        compose.onNodeWithText("300/300").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Enviar no WhatsApp").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(listOf(InvitePreviewIntent.Share), intents)
    }

    @Test
    fun screen3c() = capture("invite-3c") {
        InviteQrScreen(
            state = InviteQrState("Vôlei do CERET", "https://saqz.app/invite/ceret", renderQr("https://saqz.app/invite/ceret")),
            onIntent = {},
            onBack = {},
        )
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            SaqzTheme {
                Box(Modifier.fillMaxSize().background(SaqzTheme.colors.background)) { content() }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/vul-141/$name.png")
    }
}
