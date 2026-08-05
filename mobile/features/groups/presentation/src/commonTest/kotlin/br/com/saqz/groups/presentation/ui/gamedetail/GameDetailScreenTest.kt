package br.com.saqz.groups.presentation.ui.gamedetail

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.domain.group.PromotionMode
import br.com.saqz.groups.presentation.gamedetail.GameDetailState
import br.com.saqz.groups.presentation.gamedetail.GameDetailStatusTone
import br.com.saqz.groups.presentation.gamedetail.GameDetailWaitlistUi
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class GameDetailScreenTest {
    @Test
    fun `cancel action is hidden for cancelled games`() = runComposeUiTest {
        setScreen(GameDetailStatusTone.Cancelled)

        onAllNodesWithText("Cancelar jogo").assertCountEquals(0)
        onNodeWithText("Editar jogo").assertExists()
    }

    @Test
    fun `cancel action is hidden for completed games`() = runComposeUiTest {
        setScreen(GameDetailStatusTone.Completed)

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
        setScreen(GameDetailStatusTone.Draft)

        onAllNodesWithText("Cancelar jogo").assertCountEquals(0)
        onNodeWithText("Editar jogo").assertExists()
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

        onNodeWithText("Reserva").assertExists()
        onNodeWithText("1º na fila").assertExists()
        onAllNodesWithText("Central").assertCountEquals(2)
        onNodeWithText("Mensalista").assertExists()
        onNodeWithText("Promover").assertExists()
    }

    @Test
    fun `fifo waitlist hides promote action while keeping capacity action`() = runComposeUiTest {
        setScreen()

        onNodeWithText("Reserva").assertExists()
        onAllNodesWithText("Promover").assertCountEquals(0)
        onNodeWithText("Ajustar vagas").assertExists()
    }

    @Test
    fun `empty waitlist does not render its section`() = runComposeUiTest {
        setScreen(waitlist = emptyList())

        onAllNodesWithText("Reserva").assertCountEquals(0)
    }

    @Test
    fun `game detail does not render member response controls`() = runComposeUiTest {
        setScreen(state = GameDetailPreviewData.admin.copy(isAdmin = false))

        onAllNodesWithText("Você vai jogar?").assertCountEquals(0)
        onAllNodesWithText("Confirmar presença automaticamente").assertCountEquals(0)
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
        onAllNodesWithText("Ajustar vagas").assertCountEquals(2)
        onNodeWithText("14").assertExists()
        onNodeWithText("Salvar vagas").assertExists()
    }

    private fun ComposeUiTest.setScreen(
        status: GameDetailStatusTone = GameDetailStatusTone.Published,
        cancelDialogOpen: Boolean = false,
        waitlist: List<GameDetailWaitlistUi> = GameDetailPreviewData.admin.waitlist,
        state: GameDetailState? = null,
    ) = setContent {
        SaqzTheme {
            GameDetailScreen(
                state = state ?: GameDetailPreviewData.admin.copy(
                    header = GameDetailPreviewData.header.copy(statusTone = status),
                    cancelDialogOpen = cancelDialogOpen,
                    waitlist = waitlist,
                    promotionMode = PromotionMode.FIFO,
                ),
                onBack = {},
                onIntent = {},
            )
        }
    }
}
