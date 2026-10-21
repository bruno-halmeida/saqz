package br.com.saqz.groups.presentation.ui.gamedetail

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.group.PromotionMode
import br.com.saqz.groups.presentation.gamedetail.GameDetailAttendanceUi
import br.com.saqz.groups.presentation.gamedetail.GameDetailIntent
import br.com.saqz.groups.presentation.gamedetail.GameDetailResponseUi
import br.com.saqz.groups.presentation.gamedetail.GameDetailState
import br.com.saqz.groups.presentation.gamedetail.GameDetailStatusTone
import br.com.saqz.groups.presentation.gamedetail.GameDetailWaitlistUi
import br.com.saqz.groups.presentation.ui.home.HomeWaitlistTags
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GameDetailScreenTest {
    @Test
    fun `cancel action is hidden for cancelled games`() = runComposeUiTest {
        setScreen(GameDetailStatusTone.Cancelled, menuOpen = true)

        onAllNodesWithText("Cancelar jogo").assertCountEquals(0)
        onNodeWithText("Editar jogo").assertExists()
    }

    @Test
    fun `cancel action is hidden for completed games`() = runComposeUiTest {
        setScreen(GameDetailStatusTone.Completed, menuOpen = true)

        onAllNodesWithText("Cancelar jogo").assertCountEquals(0)
        onNodeWithText("Editar jogo").assertExists()
        onNodeWithText("Encerrar jogo e acertar").assertExists()
    }

    @Test
    fun `settlement action is hidden for non completed games`() = runComposeUiTest {
        setScreen(GameDetailStatusTone.Published)

        onAllNodesWithText("Encerrar jogo e acertar").assertCountEquals(0)
    }

    @Test
    fun `cancelled game with charges to review warns the admin and opens the settlement`() = runComposeUiTest {
        val intents = mutableListOf<GameDetailIntent>()
        setScreen(state = cancelledReview(isAdmin = true, review = true), onIntent = intents::add)

        onNodeWithTag(GameDetailTags.FinanceReview).assertExists()
        onNodeWithText("Este jogo foi cancelado com cobranças já pagas ou dispensadas. Confira se precisa devolver algum valor.").assertExists()
        onNodeWithTag(GameDetailTags.FinanceReviewAction).performScrollTo().performClick()

        assertEquals(listOf<GameDetailIntent>(GameDetailIntent.OpenSettlement), intents)
    }

    @Test
    fun `cancelled game without charges to review has no warning`() = runComposeUiTest {
        setScreen(state = cancelledReview(isAdmin = true, review = false))

        onAllNodesWithTag(GameDetailTags.FinanceReview).assertCountEquals(0)
    }

    @Test
    fun `athlete never sees the finance review warning`() = runComposeUiTest {
        setScreen(state = cancelledReview(isAdmin = false, review = true))

        onAllNodesWithTag(GameDetailTags.FinanceReview).assertCountEquals(0)
    }

    private fun cancelledReview(isAdmin: Boolean, review: Boolean) = GameDetailPreviewData.admin.copy(
        isAdmin = isAdmin,
        header = GameDetailPreviewData.header.copy(
            statusTone = GameDetailStatusTone.Cancelled,
            financeReviewRequired = review,
        ),
    )

    @Test
    fun `settlement action is hidden from athletes`() = runComposeUiTest {
        setScreen(
            state = GameDetailPreviewData.admin.copy(
                isAdmin = false,
                header = GameDetailPreviewData.header.copy(statusTone = GameDetailStatusTone.Completed),
            ),
        )

        onAllNodesWithText("Encerrar jogo e acertar").assertCountEquals(0)
    }

    @Test
    fun `cancel action is hidden for draft games`() = runComposeUiTest {
        setScreen(GameDetailStatusTone.Draft, menuOpen = true)

        onAllNodesWithText("Cancelar jogo").assertCountEquals(0)
        onNodeWithText("Editar jogo").assertExists()
    }

    @Test
    fun `publish action is offered to admins on draft games only`() = runComposeUiTest {
        setScreen(GameDetailStatusTone.Draft)

        onNodeWithTag(GameDetailTags.Publish).assertExists()
        onNodeWithText("Só você vê este jogo até publicar.").assertExists()
    }

    @Test
    fun `publish action is hidden for published games and athletes`() = runComposeUiTest {
        setScreen(GameDetailStatusTone.Published)
        onAllNodesWithText("Publicar jogo").assertCountEquals(0)
    }

    @Test
    fun `athlete never sees the publish action`() = runComposeUiTest {
        setScreen(
            state = GameDetailPreviewData.admin.copy(
                isAdmin = false,
                header = GameDetailPreviewData.header.copy(statusTone = GameDetailStatusTone.Draft),
            ),
        )
        onAllNodesWithText("Publicar jogo").assertCountEquals(0)
    }

    @Test
    fun `publish failure replaces the hint`() = runComposeUiTest {
        setScreen(
            state = GameDetailPreviewData.admin.copy(
                publishFailed = true,
                header = GameDetailPreviewData.header.copy(statusTone = GameDetailStatusTone.Draft),
            ),
        )
        onNodeWithText("Não foi possível publicar o jogo. Tente novamente.").assertExists()
    }

    @Test
    fun `cancel sheet is hosted as a full screen overlay`() = runComposeUiTest {
        setScreen(cancelDialogOpen = true)

        onNodeWithTag(GameDetailTags.CancelSheet).assertExists()
        onNodeWithText("Cancelar o jogo?").assertExists()
    }

    @Test
    fun `waitlist renders queue position athlete position and manual action`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                GameDetailScreen(
                    state = GameDetailPreviewData.admin.copy(
                        waitlist = listOf(
                            GameDetailWaitlistUi("wait-1", "Caio Lima", 1, AthletePosition.CENTRAL, true),
                        ),
                        mensalistaPriority = true,
                        promotionMode = PromotionMode.MANUAL,
                    ),
                    onBack = {},
                    onIntent = {},
                )
            }
        }

        onNodeWithTag(GameDetailTags.TabWaiting).performClick()
        onNodeWithText("1º").assertExists()
        onNodeWithText("Caio Lima").assertExists()
        // O gestor vê o vínculo na linha; a posição sai da linha em jogo sem posições só para o atleta.
        onAllNodesWithText("Mensalista").assertCountEquals(1)
        onNodeWithText("Colocar").assertExists()
    }

    @Test
    fun `fifo waitlist hides promote action while keeping capacity action`() {
        runComposeUiTest {
            setScreen(menuOpen = true)
            onNodeWithText("Ajustar vagas").assertExists()
        }
        runComposeUiTest {
            setScreen()
            onNodeWithTag(GameDetailTags.TabWaiting).performClick()
            onAllNodesWithText("Colocar").assertCountEquals(0)
            onNodeWithText("Se alguém desistir, a fila anda pela ordem.").assertExists()
        }
    }

    @Test
    fun `after the game started the promote and capacity actions disappear and the reason stops promising`() {
        val started = GameDetailPreviewData.admin.copy(
            header = GameDetailPreviewData.header.copy(responseOpen = false, confirmationOpen = false),
            promotionMode = PromotionMode.MANUAL,
        )
        runComposeUiTest {
            setScreen(state = started.copy(gameMenuOpen = true))
            onAllNodesWithText("Ajustar vagas").assertCountEquals(0)
        }
        runComposeUiTest {
            setScreen(state = started)
            onNodeWithTag(GameDetailTags.TabWaiting).performClick()
            onAllNodesWithText("Colocar").assertCountEquals(0)
            onNodeWithTag(GameDetailTags.WaitlistNote, useUnmergedTree = true).assertExists()
            onNodeWithText("O jogo já começou. A fila não anda mais.").assertExists()
            onAllNodesWithText("O organizador escolhe quem entra.").assertCountEquals(0)
        }
    }

    @Test
    fun `before the game starts the manual reason and both actions stay`() {
        val manual = GameDetailPreviewData.admin.copy(promotionMode = PromotionMode.MANUAL)
        runComposeUiTest {
            setScreen(state = manual.copy(gameMenuOpen = true))
            onNodeWithText("Ajustar vagas").assertExists()
        }
        runComposeUiTest {
            setScreen(state = manual)
            onNodeWithTag(GameDetailTags.TabWaiting).performClick()
            onAllNodesWithText("Colocar").assertCountEquals(2)
            onNodeWithText("Você escolhe quem entra. Em posição completa, fica acima das vagas.").assertExists()
        }
    }

    @Test
    fun `empty waitlist does not render its section`() = runComposeUiTest {
        setScreen(waitlist = emptyList())

        onNodeWithTag(GameDetailTags.TabWaiting).performClick()
        onNodeWithText("Ninguém na lista de espera.").assertExists()
        onAllNodesWithTag(GameDetailTags.WaitlistNote).assertCountEquals(0)
    }

    @Test
    fun `response block stays hidden when the state does not offer it`() = runComposeUiTest {
        setScreen(state = GameDetailPreviewData.admin.copy(isAdmin = false, response = GameDetailResponseUi()))

        onAllNodesWithTag(GameDetailAttendanceTags.Going).assertCountEquals(0)
        onAllNodesWithText("Você vai jogar?").assertCountEquals(0)
        onAllNodesWithText("Confirmar presença automaticamente").assertCountEquals(0)
    }

    @Test
    fun `published game without an answer shows going and not going`() = runComposeUiTest {
        val intents = mutableListOf<GameDetailIntent>()
        setScreen(state = GameDetailResponsePreviewData.memberPending, onIntent = intents::add)

        onNodeWithTag(GameDetailAttendanceTags.Going).assertExists()
        onNodeWithTag(GameDetailAttendanceTags.Going).assertIsEnabled().performClick()
        onNodeWithTag(GameDetailAttendanceTags.NotGoing).assertIsEnabled().performClick()

        assertEquals<List<GameDetailIntent>>(
            listOf(
                GameDetailIntent.Respond(AttendanceIntent.Confirm),
                GameDetailIntent.Respond(AttendanceIntent.Decline),
            ),
            intents,
        )
        // Sem "Vou" não há "Levar convidado" (DEB-116: sai o botão desabilitado com a dica).
        onAllNodesWithTag(GameGuestTags.Add).assertCountEquals(0)
    }

    @Test
    fun `admin answers in the same place as the athlete`() = runComposeUiTest {
        setScreen(state = GameDetailResponsePreviewData.admin)

        onNodeWithTag(GameDetailAttendanceTags.Going).assertExists()
        onNodeWithTag(GameDetailAttendanceTags.NotGoing).assertExists()
    }

    @Test
    fun `answered game swaps the buttons for the answer and change`() = runComposeUiTest {
        val intents = mutableListOf<GameDetailIntent>()
        setScreen(state = GameDetailResponsePreviewData.memberConfirmed, onIntent = intents::add)

        onNodeWithText("Sua presença está confirmada.").assertExists()
        onAllNodesWithTag(GameDetailAttendanceTags.Going).assertCountEquals(0)
        onNodeWithTag(GameDetailAttendanceTags.Change).assertIsEnabled().performClick()
        onNodeWithTag(GameDetailAttendanceTags.NotGoing).performClick()

        assertEquals(listOf<GameDetailIntent>(GameDetailIntent.Respond(AttendanceIntent.Decline)), intents)
    }

    @Test
    fun `declined game says so`() = runComposeUiTest {
        setScreen(state = GameDetailResponsePreviewData.memberDeclined)

        onNodeWithText("Você não vai jogar.").assertExists()
        onAllNodesWithTag(GameDetailAttendanceTags.Going).assertCountEquals(0)
        onNodeWithTag(GameDetailAttendanceTags.Change).assertExists()
    }

    @Test
    fun `waitlisted game shows the queue and only offers to leave`() = runComposeUiTest {
        val intents = mutableListOf<GameDetailIntent>()
        setScreen(state = GameDetailResponsePreviewData.memberReserva, onIntent = intents::add)

        onNodeWithTag(HomeWaitlistTags.ReservaChip).assertExists()
        onNodeWithText("Lista de espera · 3º").assertExists()
        onAllNodesWithTag(HomeWaitlistTags.ReservaViewGame).assertCountEquals(0)
        onAllNodesWithText("Ver o jogo").assertCountEquals(0)
        onAllNodesWithTag(GameDetailAttendanceTags.Going).assertCountEquals(0)
        onNodeWithTag(HomeWaitlistTags.ReservaLeave).assertIsEnabled().performClick()

        assertEquals(listOf<GameDetailIntent>(GameDetailIntent.Respond(AttendanceIntent.Decline)), intents)
    }

    @Test
    fun `avulso waitlist has no view game button either`() = runComposeUiTest {
        setScreen(state = GameDetailResponsePreviewData.memberAvulso)

        onNodeWithTag(HomeWaitlistTags.AvulsoChip).assertExists()
        onAllNodesWithTag(HomeWaitlistTags.AvulsoViewGame).assertCountEquals(0)
        onNodeWithTag(HomeWaitlistTags.AvulsoLeave).assertExists()
        onNodeWithText("Sair da lista").assertExists()
    }

    @Test
    fun `after the deadline the buttons stay enabled and the card explains the waitlist`() = runComposeUiTest {
        setScreen(state = GameDetailResponsePreviewData.memberClosed)

        onNodeWithText("O prazo acabou: quem confirmar agora entra na lista de espera e o gestor escolhe quem sobe.").assertExists()
        onNodeWithTag(GameDetailAttendanceTags.Going).assertIsEnabled()
        onNodeWithTag(GameDetailAttendanceTags.NotGoing).assertIsEnabled()
    }

    @Test
    fun `after the deadline change stays enabled`() = runComposeUiTest {
        setScreen(state = GameDetailResponsePreviewData.memberClosedConfirmed)

        onNodeWithTag(GameDetailAttendanceTags.Change).assertIsEnabled()
    }

    @Test
    fun `once the game started the buttons are disabled and the card says why`() = runComposeUiTest {
        setScreen(state = GameDetailResponsePreviewData.memberStarted)

        onNodeWithText("Em andamento. Não dá mais para responder.").assertExists()
        onNodeWithTag(GameDetailAttendanceTags.Going).assertIsNotEnabled()
        onNodeWithTag(GameDetailAttendanceTags.NotGoing).assertIsNotEnabled()
    }

    // ---- Backend v.0.0.17: depois do prazo o servidor recusa o "Vou"/"Não vou" ----

    @Test
    fun `a deadline refused by the server closes the answers and the badge stays deadline closed`() = runComposeUiTest {
        setScreen(state = GameDetailResponsePreviewData.memberClosedByDeadline)

        onNodeWithText("As confirmações deste jogo encerraram. Fale com o organizador.").assertExists()
        onNodeWithText("PRAZO ENCERRADO").assertExists()
        onAllNodesWithText("EM ANDAMENTO").assertCountEquals(0)
        onAllNodesWithText("Em andamento. Não dá mais para responder.").assertCountEquals(0)
        onAllNodesWithText("O prazo acabou: quem confirmar agora entra na lista de espera e o gestor escolhe quem sobe.").assertCountEquals(0)
        onAllNodesWithText("Não foi possível salvar sua resposta. Tente novamente.").assertCountEquals(0)
        onNodeWithTag(GameDetailAttendanceTags.Going).assertIsNotEnabled()
        onNodeWithTag(GameDetailAttendanceTags.NotGoing).assertIsNotEnabled()
    }

    @Test
    fun `a deadline refused by the server also closes change for who answered`() = runComposeUiTest {
        setScreen(state = GameDetailResponsePreviewData.memberClosedByDeadlineConfirmed)

        onNodeWithTag(GameDetailAttendanceTags.Change).assertIsNotEnabled()
    }

    @Test
    fun `game in progress does not show the confirmation deadline anywhere`() = runComposeUiTest {
        setScreen(state = GameDetailResponsePreviewData.memberStarted)

        onAllNodesWithText("Prioridade dos mensalistas até hoje às 17h30.").assertCountEquals(0)
        onNodeWithText("Em andamento. Não dá mais para responder.").assertExists()
    }

    @Test
    fun `once the game started change is disabled`() = runComposeUiTest {
        setScreen(state = GameDetailResponsePreviewData.memberStartedConfirmed)

        onNodeWithTag(GameDetailAttendanceTags.Change).assertIsNotEnabled()
    }

    @Test
    fun `open confirmation shows the deadline once in the header`() = runComposeUiTest {
        setScreen(state = GameDetailResponsePreviewData.memberPending)

        onAllNodesWithText("Prioridade dos mensalistas até hoje às 17h30.").assertCountEquals(1)
        onAllNodesWithText("Em andamento. Não dá mais para responder.").assertCountEquals(0)
    }

    @Test
    fun `failed answer shows the warning`() = runComposeUiTest {
        setScreen(state = GameDetailResponsePreviewData.memberFailed)

        onNodeWithTag(GameDetailAttendanceTags.Error).assertExists()
        onNodeWithText("Não foi possível salvar sua resposta. Tente novamente.").assertExists()
    }

    @Test
    fun `answer in flight blocks both buttons`() = runComposeUiTest {
        setScreen(state = GameDetailResponsePreviewData.memberResponding)

        onNodeWithTag(GameDetailAttendanceTags.Change).assertIsNotEnabled()
    }

    @Test
    fun `draft game hides the block even if the state still carries an answer`() = runComposeUiTest {
        setScreen(
            state = GameDetailResponsePreviewData.memberPending.copy(
                header = GameDetailPreviewData.header.copy(statusTone = GameDetailStatusTone.Draft),
            ),
        )

        onAllNodesWithTag(GameDetailAttendanceTags.Going).assertCountEquals(0)
        onAllNodesWithTag(GameDetailAttendanceTags.Going).assertCountEquals(0)
    }

    @Test
    fun `cancelled game hides the block`() = runComposeUiTest {
        setScreen(
            state = GameDetailResponsePreviewData.memberConfirmed.copy(
                header = GameDetailPreviewData.header.copy(statusTone = GameDetailStatusTone.Cancelled),
            ),
        )

        onAllNodesWithTag(GameDetailAttendanceTags.Going).assertCountEquals(0)
    }

    @Test
    fun `completed game hides the block`() = runComposeUiTest {
        setScreen(
            state = GameDetailResponsePreviewData.memberConfirmed.copy(
                header = GameDetailPreviewData.header.copy(statusTone = GameDetailStatusTone.Completed),
            ),
        )

        onAllNodesWithTag(GameDetailAttendanceTags.Going).assertCountEquals(0)
    }

    @Test
    fun `a state that does not offer the answer hides the block on a published game`() = runComposeUiTest {
        setScreen(state = GameDetailResponsePreviewData.memberPending.copy(response = GameDetailResponseUi()))

        onAllNodesWithTag(GameDetailAttendanceTags.Going).assertCountEquals(0)
    }

    @Test
    fun `capacity sheet renders stepper controls`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                GameDetailScreen(
                    state = GameDetailPreviewData.admin.copy(capacitySheetOpen = true, capacityDraft = 14),
                    onBack = {},
                    onIntent = {},
                )
            }
        }

        onNodeWithTag(GameWaitlistTags.CapacitySheet).assertExists()
        onAllNodesWithText("Ajustar vagas").assertCountEquals(1)
        onNodeWithText("14").assertExists()
        onNodeWithText("Salvar vagas").assertExists()
    }

    @Test
    fun `capacity sheet explains the new minimum for an old game`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                GameDetailScreen(
                    state = GameDetailPreviewData.admin.copy(
                        attendance = GameDetailAttendanceUi(confirmed = 1, capacity = 2, availableSpots = 1),
                        capacitySheetOpen = true,
                        capacityDraft = 4,
                    ),
                    onBack = {},
                    onIntent = {},
                )
            }
        }

        onNodeWithText("Este jogo tem 2 vagas. O mínimo agora é 4, e salvar grava 4.").assertExists()
    }

    private fun ComposeUiTest.setScreen(
        status: GameDetailStatusTone = GameDetailStatusTone.Published,
        cancelDialogOpen: Boolean = false,
        menuOpen: Boolean = false,
        waitlist: List<GameDetailWaitlistUi> = GameDetailPreviewData.admin.waitlist,
        state: GameDetailState? = null,
        onIntent: (GameDetailIntent) -> Unit = {},
    ) = setContent {
        SaqzTheme {
            GameDetailScreen(
                state = state ?: GameDetailPreviewData.admin.copy(
                    header = GameDetailPreviewData.header.copy(statusTone = status),
                    cancelDialogOpen = cancelDialogOpen,
                    gameMenuOpen = menuOpen,
                    waitlist = waitlist,
                    promotionMode = PromotionMode.FIFO,
                ),
                onBack = {},
                onIntent = onIntent,
            )
        }
    }
}
