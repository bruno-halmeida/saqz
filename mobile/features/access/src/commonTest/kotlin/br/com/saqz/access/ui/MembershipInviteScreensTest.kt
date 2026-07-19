package br.com.saqz.access.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.access.data.GroupRoleDto
import br.com.saqz.access.data.MembershipDto
import br.com.saqz.access.data.PersistedRoleDto
import br.com.saqz.access.presentation.GroupActions
import br.com.saqz.access.presentation.GroupAdministrationState
import br.com.saqz.access.presentation.InviteUiError
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MembershipInviteScreensTest {
    @Test
    fun `membership administration is unreachable without owner role`() = runComposeUiTest {
        memberships(state.copy(actions = adminActions))

        onNodeWithText("Gerenciar acessos").assertDoesNotExist()
    }

    @Test
    fun `membership list shows every member with authoritative role`() = runComposeUiTest {
        memberships()

        onNodeWithText("Owner One").assertExists()
        onNodeWithText("OWNER").assertExists()
        onNodeWithText("Admin One").assertExists()
        onNodeWithText("ADMIN").assertExists()
        onNodeWithText("Athlete One").assertExists()
        onNodeWithText("ATHLETE").assertExists()
    }

    @Test
    fun `owner role has no mutable control`() = runComposeUiTest {
        memberships()

        onNodeWithTag(MembershipInviteTags.role("owner-id")).assertDoesNotExist()
    }

    @Test
    fun `athlete toggle requests admin for that member`() = runComposeUiTest {
        val intents = mutableListOf<MembershipAdministrationIntent>()
        memberships(onIntent = intents::add)

        onNodeWithTag(MembershipInviteTags.role("athlete-id")).performClick()

        assertEquals(listOf<MembershipAdministrationIntent>(MembershipAdministrationIntent.ChangeRole("athlete-id", PersistedRoleDto.ADMIN)), intents)
    }

    @Test
    fun `admin toggle requests athlete for that member`() = runComposeUiTest {
        val intents = mutableListOf<MembershipAdministrationIntent>()
        memberships(onIntent = intents::add)

        onNodeWithTag(MembershipInviteTags.role("admin-id")).performClick()

        assertEquals(listOf<MembershipAdministrationIntent>(MembershipAdministrationIntent.ChangeRole("admin-id", PersistedRoleDto.ATHLETE)), intents)
    }

    @Test
    fun `changing one of several admins leaves the other targeted independently`() = runComposeUiTest {
        val intents = mutableListOf<MembershipAdministrationIntent>()
        memberships(
            state.copy(memberships = state.memberships + MembershipDto("admin-two", "Admin Two", GroupRoleDto.ADMIN)),
            onIntent = intents::add,
        )

        onNodeWithTag(MembershipInviteTags.role("admin-two")).performClick()

        assertEquals(listOf<MembershipAdministrationIntent>(MembershipAdministrationIntent.ChangeRole("admin-two", PersistedRoleDto.ATHLETE)), intents)
        onNodeWithTag(MembershipInviteTags.role("admin-id")).assertExists()
    }

    @Test
    fun `loading membership state prevents duplicate role changes`() = runComposeUiTest {
        memberships(state.copy(isLoading = true))

        onNodeWithTag(MembershipInviteTags.role("athlete-id")).assertIsNotEnabled()
    }

    @Test
    fun `invite tool is available to owner and admin but not athlete`() = runComposeUiTest {
        var actions by mutableStateOf(ownerActions)
        setContent {
            SaqzTheme {
                InviteManagementScreen(
                    state = InviteManagementUiState(actions, InviteToolState()),
                    onIntent = {},
                )
            }
        }

        onNodeWithText("Convite do grupo").assertExists()
        actions = adminActions
        waitForIdle()
        onNodeWithText("Convite do grupo").assertExists()
        actions = athleteActions
        waitForIdle()
        onNodeWithText("Convite do grupo").assertDoesNotExist()
    }

    @Test
    fun `generate action requests first invite`() = runComposeUiTest {
        val intents = mutableListOf<InviteManagementIntent>()
        invite(onIntent = intents::add)

        onNodeWithTag(MembershipInviteTags.Generate).performClick()

        assertEquals(listOf<InviteManagementIntent>(InviteManagementIntent.Generate), intents)
    }

    @Test
    fun `generate action rotates an existing invite`() = runComposeUiTest {
        val intents = mutableListOf<InviteManagementIntent>()
        invite(InviteToolState(inviteUrl = firstUrl), onIntent = intents::add)

        onNodeWithTag(MembershipInviteTags.Rotate).performClick()

        assertEquals(listOf<InviteManagementIntent>(InviteManagementIntent.Generate), intents)
    }

    @Test
    fun `share is an accessible icon and raw invite is not rendered`() = runComposeUiTest {
        invite(InviteToolState(inviteUrl = firstUrl))

        onNodeWithContentDescription("Compartilhar convite").assertExists()
        onNodeWithText(firstUrl).assertDoesNotExist()
    }

    @Test
    fun `share always receives the latest complete invite url`() = runComposeUiTest {
        var toolState by mutableStateOf(InviteToolState(inviteUrl = firstUrl))
        val intents = mutableListOf<InviteManagementIntent>()
        setContent {
            SaqzTheme {
                InviteManagementScreen(InviteManagementUiState(ownerActions, toolState), intents::add)
            }
        }
        toolState = InviteToolState(inviteUrl = secondUrl)
        waitForIdle()

        onNodeWithTag(MembershipInviteTags.Share).performClick()

        assertEquals(listOf<InviteManagementIntent>(InviteManagementIntent.Share(secondUrl)), intents)
    }

    @Test
    fun `expire action requests explicit confirmation`() = runComposeUiTest {
        val intents = mutableListOf<InviteManagementIntent>()
        invite(InviteToolState(inviteUrl = firstUrl), onIntent = intents::add)

        onNodeWithTag(MembershipInviteTags.Expire).performClick()

        assertEquals(listOf<InviteManagementIntent>(InviteManagementIntent.RequestExpire), intents)
    }

    @Test
    fun `expire dialog mutates only after confirmation`() = runComposeUiTest {
        val intents = mutableListOf<ExpireInviteConfirmationIntent>()
        setContent { SaqzTheme { ExpireInviteConfirmationDialog(intents::add) } }

        onNodeWithTag(MembershipInviteTags.ExpireCancel).performClick()
        assertEquals(listOf<ExpireInviteConfirmationIntent>(ExpireInviteConfirmationIntent.Cancel), intents)
        onNodeWithTag(MembershipInviteTags.ExpireConfirm).performClick()
        assertEquals(
            listOf<ExpireInviteConfirmationIntent>(
                ExpireInviteConfirmationIntent.Cancel,
                ExpireInviteConfirmationIntent.Confirm,
            ),
            intents,
        )
    }

    @Test
    fun `invalid invite has stable feedback and a retry action`() = runComposeUiTest {
        val intents = mutableListOf<InviteManagementIntent>()
        invite(InviteToolState(error = InviteUiError.INVALID_OR_EXPIRED), onIntent = intents::add)

        onNodeWithText("Convite invalido ou expirado").assertExists()
        onNodeWithTag(MembershipInviteTags.Retry).performClick()

        assertEquals(listOf<InviteManagementIntent>(InviteManagementIntent.Retry), intents)
    }

    @Test
    fun `rate limited invite shows retry delay and blocks retry`() = runComposeUiTest {
        invite(InviteToolState(error = InviteUiError.ATTEMPT_LIMIT, retryAfterSeconds = 42))

        onNodeWithText("Tente novamente em 42 segundos").assertExists()
        onNodeWithTag(MembershipInviteTags.Retry).assertIsNotEnabled()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.memberships(
        membershipState: GroupAdministrationState = state,
        onIntent: (MembershipAdministrationIntent) -> Unit = {},
    ) = setContent {
        SaqzTheme {
            MembershipAdministrationScreen(membershipState, onIntent)
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.invite(
        inviteState: InviteToolState = InviteToolState(),
        onIntent: (InviteManagementIntent) -> Unit = {},
    ) = setContent {
        SaqzTheme {
            InviteManagementScreen(InviteManagementUiState(ownerActions, inviteState), onIntent)
        }
    }

    private companion object {
        const val firstUrl = "https://join.saqz.app/a/opaque-first"
        const val secondUrl = "https://join.saqz.app/a/opaque-second"
        val ownerActions = GroupActions(canEditSettings = true, canManageRoles = true, canManageInvite = true)
        val adminActions = GroupActions(canEditSettings = true, canManageRoles = false, canManageInvite = true)
        val athleteActions = GroupActions(canEditSettings = false, canManageRoles = false, canManageInvite = false)
        val state = GroupAdministrationState(
            actions = ownerActions,
            memberships = listOf(
                MembershipDto("owner-id", "Owner One", GroupRoleDto.OWNER),
                MembershipDto("admin-id", "Admin One", GroupRoleDto.ADMIN),
                MembershipDto("athlete-id", "Athlete One", GroupRoleDto.ATHLETE),
            ),
        )
    }
}
