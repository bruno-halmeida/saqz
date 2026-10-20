package br.com.saqz.groups.presentation.ui.members

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.membership.MonthlyInviteLinkState
import br.com.saqz.groups.domain.membership.MonthlyInviteeStatus
import br.com.saqz.groups.presentation.members.GroupInviteesIntent
import br.com.saqz.groups.presentation.members.GroupInviteesState
import br.com.saqz.groups.presentation.members.GroupMembersFilter
import br.com.saqz.groups.presentation.members.GroupMembersIntent
import br.com.saqz.groups.presentation.members.GroupMembersState
import br.com.saqz.groups.presentation.members.InviteeUi
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GroupInviteesSectionTest {
    private val members = GroupMembersState(isLoading = false, totalCount = 26, adminCount = 2, canReviewRequests = true)
    private val six = listOf(
        InviteeUi("1", "Larissa Prado", "11911122233", MonthlyInviteeStatus.WAITING),
        InviteeUi("2", "Marcos Vieira", "11973334455", MonthlyInviteeStatus.QUEUED),
        InviteeUi("3", "Felipe Andrade", "11955556677", MonthlyInviteeStatus.FAILED),
        InviteeUi("4", "Renata Souza", "11946667788", MonthlyInviteeStatus.NO_WHATSAPP),
        InviteeUi("5", "Bianca Torres", "11964445566", MonthlyInviteeStatus.PAUSED),
        InviteeUi("6", "Diego Farias", "11982223344", MonthlyInviteeStatus.EXPIRED),
    )
    private val active = GroupInviteesState(visible = true, invitees = six, linkState = MonthlyInviteLinkState.ACTIVE, linkUntil = "27 de out")

    @Test fun `owner with invitees sees the chip the section and the six states`() = runComposeUiTest {
        content(invitees = active)
        onNodeWithText("Convidados · 6").assertExists()
        onNodeWithText("Todos · 26").assertExists()
        onNodeWithText("O link de mensalistas vale até 27 de out. Cada envio renova o prazo.").assertExists()
        listOf("Aguardando", "Na fila", "Falhou", "Sem WhatsApp", "Pausado", "Venceu").forEach { onNodeWithText(it).assertExists() }
        onNodeWithTag(GroupInviteesTags.Invalidate).assertExists()
    }

    @Test fun `no invitees shows the entry card and no chip`() = runComposeUiTest {
        var intent: GroupInviteesIntent? = null
        content(invitees = GroupInviteesState(visible = true)) { intent = it }
        onNodeWithText("Mensalistas pelo WhatsApp").assertExists()
        onNodeWithText("Convidados · 0").assertDoesNotExist()
        onNodeWithText("Convidar mensalista").performClick()
        assertEquals(GroupInviteesIntent.InviteMonthly, intent)
    }

    @Test fun `athlete sees neither section nor chip`() = runComposeUiTest {
        content(invitees = GroupInviteesState(visible = false))
        onNodeWithText("Mensalistas pelo WhatsApp").assertDoesNotExist()
        onNodeWithTag(GroupInviteesTags.Section).assertDoesNotExist()
    }

    @Test fun `invited filter shows only the section`() = runComposeUiTest {
        var intent: GroupMembersIntent? = null
        content(members = members, invitees = active, onMembersIntent = { intent = it })
        onNodeWithText("Convidados · 6").performClick()
        assertEquals(GroupMembersIntent.SelectFilter(GroupMembersFilter.Invited), intent)
    }

    @Test fun `paused queue shows the notice with copy link`() = runComposeUiTest {
        var intent: GroupInviteesIntent? = null
        content(invitees = active.copy(pausedRetryAt = "14h32")) { intent = it }
        onNodeWithText("O WhatsApp do Saqz falhou 3 vezes seguidas. Tentamos de novo às 14h32. Se preferir, copie o link e mande você mesmo.").assertExists()
        onNodeWithTag(GroupInviteesTags.CopyLink).performClick()
        assertEquals(GroupInviteesIntent.CopyLink, intent)
    }

    @Test fun `expired link shows the notice with resend all`() = runComposeUiTest {
        val expired = six.filter { it.status == MonthlyInviteeStatus.EXPIRED }
        content(invitees = GroupInviteesState(visible = true, invitees = expired, linkState = MonthlyInviteLinkState.EXPIRED))
        onNodeWithText("O link venceu").assertExists()
        onNodeWithText("Reenviar para todos").assertExists()
    }

    @Test fun `row opens the action sheet and invalidate asks for confirmation`() = runComposeUiTest {
        content(invitees = active.copy(selected = six[2], confirmInvalidate = false))
        onNodeWithText("Reenviar convite").assertExists()
        onNodeWithTag(GroupInviteesTags.SheetCopyLink).assertExists()
        onNodeWithText("Remover da lista").assertExists()
        onNodeWithText("(11) 95555-6677 · não saiu depois de 3 tentativas.").assertExists()
    }

    @Test fun `invalidate confirmation has the texts`() = runComposeUiTest {
        content(invitees = active.copy(confirmInvalidate = true))
        onNodeWithText("Invalidar o link?").assertExists()
        onNodeWithText("Quem ainda não entrou precisará receber o link de novo. Quem já entrou continua no grupo.").assertExists()
    }

    private fun ComposeUiTest.content(
        members: GroupMembersState = this@GroupInviteesSectionTest.members,
        invitees: GroupInviteesState,
        onMembersIntent: (GroupMembersIntent) -> Unit = {},
        onInviteesIntent: (GroupInviteesIntent) -> Unit = {},
    ) = setContent {
        SaqzTheme {
            GroupMembersScreen(
                state = members,
                onIntent = onMembersIntent,
                onBack = {},
                invitees = invitees,
                onInviteesIntent = onInviteesIntent,
            )
        }
    }
}
