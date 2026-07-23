package br.com.saqz.groups.ui.games.detail

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.domain.GroupId
import br.com.saqz.groups.domain.attendance.AttendanceDetail
import br.com.saqz.groups.domain.attendance.AttendanceEntry
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.game.Game
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.game.GameVenue
import br.com.saqz.groups.domain.game.GameVersionToken
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.presentation.games.detail.*
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class GameDetailScreenTest {
    @Test fun `member sees localized authoritative snapshot`() =
        runComposeUiTest {
            screen(state())
            onNodeWithText("Treino").assertExists()
            onNodeWithText("12/08/2026 às 19:30").assertExists()
            onNodeWithText("Arena").assertExists()
            onNodeWithText("21 vagas").assertExists()
            onNodeWithText("Valor: R$ 25,00").assertExists()
        }

    @Test fun `athlete never sees organizer actions`() =
        runComposeUiTest {
            screen(state(role = GroupRole.ATHLETE))
            onNodeWithTag(GameDetailTags.Edit).assertDoesNotExist()
            onNodeWithTag(GameDetailTags.Publish).assertDoesNotExist()
        }

    @Test fun `draft organizer sees edit and publish only`() =
        runComposeUiTest {
            screen(state())
            onNodeWithTag(GameDetailTags.Edit).assertExists()
            onNodeWithTag(GameDetailTags.Publish).assertExists()
            onNodeWithTag(GameDetailTags.Cancel).assertDoesNotExist()
            onNodeWithTag(GameDetailTags.Complete).assertDoesNotExist()
        }

    @Test fun `published organizer sees edit cancel and complete`() =
        runComposeUiTest {
            screen(state(status = GameStatus.Published))
            onNodeWithTag(GameDetailTags.Edit).assertExists()
            onNodeWithTag(GameDetailTags.Cancel).assertExists()
            onNodeWithTag(GameDetailTags.Complete).assertExists()
            onNodeWithTag(GameDetailTags.Publish).assertDoesNotExist()
        }

    @Test fun `cancel confirmation explains manual finance review`() =
        runComposeUiTest {
            val intents = mutableListOf<GameDetailIntent>()
            screen(state(status = GameStatus.Published).copy(pendingAction = GameLifecycleAction.CANCEL), intents::add)
            onNodeWithText("Cancelar este jogo?").assertExists()
            onNodeWithText("O cancelamento exige revisão financeira manual. Nenhum reembolso é automático.").assertExists()
            onNodeWithTag(GameDetailTags.Confirm).performClick()
            waitForIdle()
            assertEquals(GameDetailIntent.ConfirmLifecycle, intents.single())
        }

    @Test fun `cancelled and completed games are read only`() =
        runComposeUiTest {
            screen(state(status = GameStatus.Cancelled))
            onNodeWithText("Este jogo está encerrado e não pode mais ser alterado.").assertExists()
            onNodeWithText("O cancelamento exige revisão financeira manual. Nenhum reembolso é automático.").assertExists()
            onNodeWithTag(GameDetailTags.Edit).assertDoesNotExist()
            onNodeWithTag(GameDetailTags.Cancel).assertDoesNotExist()
        }

    @Test fun `conflict offers typed reload without hiding snapshot`() =
        runComposeUiTest {
            val intents = mutableListOf<GameDetailIntent>()
            screen(state().copy(error = GameDetailError.CONFLICT, reloadAvailable = true), intents::add)
            onNodeWithText("Treino").assertExists()
            onNodeWithText("O jogo mudou. Recarregue antes de continuar.").assertExists()
            onNodeWithTag(GameDetailTags.Reload).performScrollTo().performClick()
            waitForIdle()
            assertEquals(GameDetailIntent.Reload, intents.single())
        }

    @Test fun `unanswered member sees authoritative counts waitlist and legal actions`() =
        runComposeUiTest {
            screen(attendanceState())
            onNodeWithText("3 confirmados • 1 vagas disponíveis").assertExists()
            onNodeWithText("Lista de espera: 2").assertExists()
            onNodeWithTag(GameDetailTags.AttendConfirm).assertExists()
            onNodeWithTag(GameDetailTags.AttendDecline).assertExists()
            onNodeWithTag(GameDetailTags.AttendWithdraw).assertDoesNotExist()
        }

    @Test fun `confirmed member sees status and withdrawal only`() =
        runComposeUiTest {
            screen(attendanceState(AttendanceStatus.Confirmed))
            onNodeWithText("Presença confirmada").assertExists()
            onNodeWithTag(GameDetailTags.AttendWithdraw).assertExists()
            onNodeWithTag(GameDetailTags.AttendConfirm).assertDoesNotExist()
            onNodeWithTag(GameDetailTags.AttendDecline).assertDoesNotExist()
        }

    @Test fun `waitlisted member sees exact position and withdrawal`() =
        runComposeUiTest {
            screen(attendanceState(AttendanceStatus.Waitlisted, 4))
            onNodeWithText("Você está na lista de espera • posição 4").assertExists()
            onNodeWithTag(GameDetailTags.AttendWithdraw).assertExists()
        }

    @Test fun `declined member may confirm again but cannot decline twice`() =
        runComposeUiTest {
            screen(attendanceState(AttendanceStatus.Declined))
            onNodeWithText("Você informou que não vai").assertExists()
            onNodeWithTag(GameDetailTags.AttendConfirm).assertExists()
            onNodeWithTag(GameDetailTags.AttendDecline).assertDoesNotExist()
        }

    @Test fun `deadline closed state hides member response controls`() =
        runComposeUiTest {
            screen(attendanceState().copy(attendanceOpen = false))
            onNodeWithText("Prazo para responder encerrado").assertExists()
            assertNoAttendanceActions()
        }

    @Test fun `terminal game shows frozen state and hides member controls`() =
        runComposeUiTest {
            screen(attendanceState(status = GameStatus.Cancelled).copy(attendanceOpen = false))
            onNodeWithText("Respostas encerradas para este jogo").assertExists()
            assertNoAttendanceActions()
        }

    @Test fun `withdrawal dialog states tracked charge remains pending without automatic claims`() =
        runComposeUiTest {
            screen(attendanceState(AttendanceStatus.Confirmed).copy(pendingAttendanceAction = AttendanceAction.WITHDRAW))
            onNodeWithText("Desistir deste jogo?").assertExists()
            onNodeWithText(
                "Sua cobrança registrada continuará pendente. Nenhum estorno, reembolso ou pagamento é automático.",
            ).assertExists()
        }

    @Test fun `attendance dialog confirmation dispatches one confirm intent`() =
        runComposeUiTest {
            val intents = mutableListOf<GameDetailIntent>()
            screen(attendanceState().copy(pendingAttendanceAction = AttendanceAction.CONFIRM), intents::add)
            onNodeWithTag(GameDetailTags.AttendDialogConfirm).performClick()
            waitForIdle()
            assertEquals(GameDetailIntent.ConfirmAttendance, intents.single())
        }

    @Test fun `decline action requests explicit attendance state`() =
        runComposeUiTest {
            val intents = mutableListOf<GameDetailIntent>()
            screen(attendanceState(), intents::add)
            onNodeWithTag(GameDetailTags.AttendDecline).performClick()
            waitForIdle()
            assertEquals(GameDetailIntent.RequestAttendance(AttendanceAction.DECLINE), intents.single())
        }

    @Test fun `withdraw action requests explicit attendance state`() =
        runComposeUiTest {
            val intents = mutableListOf<GameDetailIntent>()
            screen(attendanceState(AttendanceStatus.Confirmed), intents::add)
            onNodeWithTag(GameDetailTags.AttendWithdraw).performClick()
            waitForIdle()
            assertEquals(GameDetailIntent.RequestAttendance(AttendanceAction.WITHDRAW), intents.single())
        }

    @Test fun `athlete never sees organizer attendance controls`() =
        runComposeUiTest {
            screen(attendanceState(role = GroupRole.ATHLETE))
            onNodeWithTag(GameDetailTags.OverrideMember).assertDoesNotExist()
            onNodeWithTag(GameDetailTags.Capacity).assertDoesNotExist()
        }

    @Test fun `organizer sees share actions and athlete sees none`() =
        runComposeUiTest {
            screen(attendanceState(role = GroupRole.OWNER))
            onNodeWithTag(GameDetailTags.ShareLink).assertExists()
            onNodeWithTag(GameDetailTags.ShareList).assertExists()
            screen(attendanceState(role = GroupRole.ATHLETE))
            onNodeWithTag(GameDetailTags.ShareLink).assertDoesNotExist()
            onNodeWithTag(GameDetailTags.ShareList).assertDoesNotExist()
        }

    @Test fun `share list privacy dialog requires explicit continue`() =
        runComposeUiTest {
            val intents = mutableListOf<GameDetailIntent>()
            screen(attendanceState(role = GroupRole.OWNER).copy(showAttendanceSharePrivacy = true), intents::add)
            onNodeWithText("Compartilhar lista de presença?").assertExists()
            onNodeWithText(
                "Os nomes sairão do Saqz ao abrir a folha de compartilhamento. Continue apenas se você quiser enviar esta lista.",
            ).assertExists()
            onNodeWithTag(GameDetailTags.SharePrivacyConfirm).performClick()
            waitForIdle()
            assertEquals(GameDetailIntent.ConfirmAttendanceImageShare, intents.single())
        }

    @Test fun `organizer override stays disabled until member and valid reason exist`() =
        runComposeUiTest {
            screen(attendanceState(role = GroupRole.OWNER))
            onNodeWithTag(GameDetailTags.OverrideConfirm).performScrollTo().assertIsNotEnabled()
            field("Identificador do membro").performScrollTo().performTextInput("member-2")
            field("Motivo do ajuste").performScrollTo().performTextInput("X")
            onNodeWithTag(GameDetailTags.OverrideConfirm).performScrollTo().assertIsNotEnabled()
            field("Motivo do ajuste").performTextInput("Y")
            onNodeWithTag(GameDetailTags.OverrideConfirm).assertIsEnabled()
        }

    @Test fun `organizer confirm override dispatches member state and reason`() =
        runComposeUiTest {
            val intents = mutableListOf<GameDetailIntent>()
            screen(attendanceState(role = GroupRole.OWNER), intents::add)
            field("Identificador do membro").performScrollTo().performTextInput("member-2")
            field("Motivo do ajuste").performScrollTo().performTextInput("Ajuste manual")
            onNodeWithTag(GameDetailTags.OverrideConfirm).performScrollTo().performClick()
            waitForIdle()
            assertEquals(GameDetailIntent.OverrideAttendance("member-2", AttendanceIntent.Confirm, "Ajuste manual"), intents.single())
        }

    @Test fun `organizer decline override dispatches explicit decline`() =
        runComposeUiTest {
            val intents = mutableListOf<GameDetailIntent>()
            screen(attendanceState(role = GroupRole.ADMIN), intents::add)
            field("Identificador do membro").performScrollTo().performTextInput("member-3")
            field("Motivo do ajuste").performScrollTo().performTextInput("Não compareceu")
            onNodeWithTag(GameDetailTags.OverrideDecline).performScrollTo().performClick()
            waitForIdle()
            assertEquals(GameDetailIntent.OverrideAttendance("member-3", AttendanceIntent.Decline, "Não compareceu"), intents.single())
        }

    @Test fun `capacity below confirmed warns and never offers demotion`() =
        runComposeUiTest {
            val intents = mutableListOf<GameDetailIntent>()
            screen(attendanceState(role = GroupRole.OWNER), intents::add)
            field("Capacidade").performScrollTo().performTextReplacement("2")
            onNodeWithTag(GameDetailTags.CapacityWarning).performScrollTo().assertExists()
            onNodeWithText("A capacidade não pode ficar abaixo dos 3 já confirmados. Ninguém será removido.").assertExists()
            onNodeWithTag(GameDetailTags.SaveCapacity).performScrollTo().assertIsNotEnabled()
            onAllNodesWithText("Remover membro").assertCountEquals(0)
            assertTrue(intents.isEmpty())
        }

    @Test fun `valid capacity dispatches update`() =
        runComposeUiTest {
            val intents = mutableListOf<GameDetailIntent>()
            screen(attendanceState(role = GroupRole.OWNER), intents::add)
            field("Capacidade").performScrollTo().performTextReplacement("30")
            onNodeWithTag(GameDetailTags.SaveCapacity).performScrollTo().performClick()
            waitForIdle()
            assertEquals(GameDetailIntent.ChangeCapacity(30), intents.single())
        }

    @Test fun `organizer reason is capped at backend maximum`() =
        runComposeUiTest {
            screen(attendanceState(role = GroupRole.OWNER))
            val node = field("Motivo do ajuste")
            node.performScrollTo().performTextInput("x".repeat(501))
            assertEquals(
                500,
                node
                    .fetchSemanticsNode()
                    .config[SemanticsProperties.EditableText]
                    .text.length,
            )
        }

    @Test fun `member controls retain semantic order and minimum touch height`() =
        runComposeUiTest {
            screen(attendanceState())
            val confirm = onNodeWithTag(GameDetailTags.AttendConfirm).fetchSemanticsNode().boundsInRoot
            val decline = onNodeWithTag(GameDetailTags.AttendDecline).fetchSemanticsNode().boundsInRoot
            assertTrue(confirm.top < decline.top)
            assertTrue(confirm.height >= 48f)
            assertTrue(decline.height >= 48f)
        }

    private fun androidx.compose.ui.test.ComposeUiTest.screen(
        value: GameDetailState,
        onIntent: (GameDetailIntent) -> Unit = {
        },
    ) = setContent { SaqzTheme { GameDetailScreen(value, onIntent) } }

    private fun androidx.compose.ui.test.ComposeUiTest.field(label: String) = onNodeWithContentDescription(label, useUnmergedTree = true)

    private fun androidx.compose.ui.test.ComposeUiTest.assertNoAttendanceActions() {
        onNodeWithTag(GameDetailTags.AttendConfirm).assertDoesNotExist()
        onNodeWithTag(GameDetailTags.AttendDecline).assertDoesNotExist()
        onNodeWithTag(GameDetailTags.AttendWithdraw).assertDoesNotExist()
    }

    private fun attendanceState(
        own: AttendanceStatus? = null,
        position: Long? = null,
        role: GroupRole = GroupRole.ATHLETE,
        status: GameStatus = GameStatus.Published,
    ): GameDetailState {
        val base = state(role, status)
        val detail = AttendanceDetail(own?.let { AttendanceEntry("member", it, position, 1) }, 3, 1, 2, 4)
        return base.copy(
            attendance = detail,
            attendanceOpen =
                status == GameStatus.Published,
        )
    }

    private fun state(
        role: GroupRole = GroupRole.OWNER,
        status: GameStatus = GameStatus.Draft,
    ): GameDetailState {
        val game =
            Game(
                "game",
                GroupId("group"),
                "Treino",
                GameVenue(null, "Arena", "Rua 1"),
                "2026-08-12",
                "19:30:00",
                "America/Sao_Paulo",
                "2026-08-12T22:30:00Z",
                90,
                24,
                "2026-08-12T19:00:00Z",
                2500,
                "Notas",
                status,
                7,
                3,
                21,
                2,
                status == GameStatus.Cancelled,
            )
        ;return GameDetailState("group", "game", role, game, GameVersionToken("\"7\""), isLoading = false)
    }
}
