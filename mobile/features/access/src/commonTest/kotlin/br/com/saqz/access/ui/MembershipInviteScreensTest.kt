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
        var changed: Pair<String, PersistedRoleDto>? = null
        memberships(onRoleChange = { userId, role -> changed = userId to role })

        onNodeWithTag(MembershipInviteTags.role("athlete-id")).performClick()

        assertEquals("athlete-id" to PersistedRoleDto.ADMIN, changed)
    }

    @Test
    fun `admin toggle requests athlete for that member`() = runComposeUiTest {
        var changed: Pair<String, PersistedRoleDto>? = null
        memberships(onRoleChange = { userId, role -> changed = userId to role })

        onNodeWithTag(MembershipInviteTags.role("admin-id")).performClick()

        assertEquals("admin-id" to PersistedRoleDto.ATHLETE, changed)
    }

    @Test
    fun `changing one of several admins leaves the other targeted independently`() = runComposeUiTest {
        var changed: Pair<String, PersistedRoleDto>? = null
        memberships(
            state.copy(memberships = state.memberships + MembershipDto("admin-two", "Admin Two", GroupRoleDto.ADMIN)),
            onRoleChange = { userId, role -> changed = userId to role },
        )

        onNodeWithTag(MembershipInviteTags.role("admin-two")).performClick()

        assertEquals("admin-two" to PersistedRoleDto.ATHLETE, changed)
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
                    actions = actions,
                    state = InviteToolState(),
                    onGenerate = {},
                    onShare = {},
                    onExpireRequest = {},
                    onRetry = {},
                    onBack = {},
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
        var calls = 0
        invite(onGenerate = { calls++ })

        onNodeWithTag(MembershipInviteTags.Generate).performClick()

        assertEquals(1, calls)
    }

    @Test
    fun `generate action rotates an existing invite`() = runComposeUiTest {
        var calls = 0
        invite(InviteToolState(inviteUrl = firstUrl), onGenerate = { calls++ })

        onNodeWithTag(MembershipInviteTags.Rotate).performClick()

        assertEquals(1, calls)
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
        var shared: String? = null
        setContent {
            SaqzTheme {
                InviteManagementScreen(ownerActions, toolState, {}, { shared = it }, {}, {}, {})
            }
        }
        toolState = InviteToolState(inviteUrl = secondUrl)
        waitForIdle()

        onNodeWithTag(MembershipInviteTags.Share).performClick()

        assertEquals(secondUrl, shared)
    }

    @Test
    fun `expire action requests explicit confirmation`() = runComposeUiTest {
        var calls = 0
        invite(InviteToolState(inviteUrl = firstUrl), onExpireRequest = { calls++ })

        onNodeWithTag(MembershipInviteTags.Expire).performClick()

        assertEquals(1, calls)
    }

    @Test
    fun `expire dialog mutates only after confirmation`() = runComposeUiTest {
        var confirms = 0
        var cancels = 0
        setContent { SaqzTheme { ExpireInviteConfirmationDialog({ confirms++ }, { cancels++ }) } }

        onNodeWithTag(MembershipInviteTags.ExpireCancel).performClick()
        assertEquals(0, confirms)
        assertEquals(1, cancels)
        onNodeWithTag(MembershipInviteTags.ExpireConfirm).performClick()
        assertEquals(1, confirms)
    }

    @Test
    fun `invalid invite has stable feedback and a retry action`() = runComposeUiTest {
        var calls = 0
        invite(InviteToolState(error = InviteUiError.INVALID_OR_EXPIRED), onRetry = { calls++ })

        onNodeWithText("Convite invalido ou expirado").assertExists()
        onNodeWithTag(MembershipInviteTags.Retry).performClick()

        assertEquals(1, calls)
    }

    @Test
    fun `rate limited invite shows retry delay and blocks retry`() = runComposeUiTest {
        invite(InviteToolState(error = InviteUiError.ATTEMPT_LIMIT, retryAfterSeconds = 42))

        onNodeWithText("Tente novamente em 42 segundos").assertExists()
        onNodeWithTag(MembershipInviteTags.Retry).assertIsNotEnabled()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.memberships(
        membershipState: GroupAdministrationState = state,
        onRoleChange: (String, PersistedRoleDto) -> Unit = { _, _ -> },
    ) = setContent {
        SaqzTheme {
            MembershipAdministrationScreen(membershipState, onRoleChange, {})
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.invite(
        inviteState: InviteToolState = InviteToolState(),
        onGenerate: () -> Unit = {},
        onExpireRequest: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) = setContent {
        SaqzTheme {
            InviteManagementScreen(ownerActions, inviteState, onGenerate, {}, onExpireRequest, onRetry, {})
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
