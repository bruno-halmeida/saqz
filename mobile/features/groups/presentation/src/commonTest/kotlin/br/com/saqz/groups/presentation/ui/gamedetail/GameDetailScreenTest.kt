package br.com.saqz.groups.presentation.ui.gamedetail

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.presentation.gamedetail.GameDetailResponseStatus
import br.com.saqz.groups.presentation.gamedetail.GameDetailResponseUi
import br.com.saqz.groups.presentation.gamedetail.GameDetailState
import br.com.saqz.groups.presentation.gamedetail.GameDetailStatusTone
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
    fun `member response shows vou and nao vou without maybe`() = runComposeUiTest {
        setScreen(
            state = GameDetailPreviewData.admin.copy(
                isAdmin = false,
                memberResponse = GameDetailResponseUi(GameDetailResponseStatus.Waitlisted, 3),
                membershipType = AthleteMembershipType.AVULSO,
            ),
        )

        onNodeWithText("Você vai jogar?").assertExists()
        onNodeWithTag(GameResponseTags.Going).assertExists()
        onNodeWithTag(GameResponseTags.NotGoing).assertExists()
        onNodeWithText("Você é o 3º da reserva.").assertExists()
        onNodeWithText("Ao confirmar, a cobrança deste jogo será gerada.").assertExists()
        onAllNodesWithText("Talvez").assertCountEquals(0)
    }

    @Test
    fun `member response is locked after deadline`() = runComposeUiTest {
        setScreen(
            state = GameDetailPreviewData.admin.copy(
                header = GameDetailPreviewData.header.copy(confirmationOpen = false),
                memberResponse = GameDetailResponseUi(GameDetailResponseStatus.Confirmed),
            ),
        )

        onNodeWithText("As confirmações estão encerradas.").assertExists()
        onNodeWithTag(GameResponseTags.Going).assertExists()
        onNodeWithTag(GameResponseTags.NotGoing).assertExists()
    }

    @Test
    fun `auto confirmation is visible only for eligible member`() = runComposeUiTest {
        setScreen(
            state = GameDetailPreviewData.admin.copy(
                isAdmin = false,
                membershipType = AthleteMembershipType.MENSALISTA,
                autoConfirmationVisible = true,
            ),
        )

        onNodeWithTag(GameResponseTags.AutoConfirmation).assertExists()
    }

    private fun ComposeUiTest.setScreen(
        status: GameDetailStatusTone = GameDetailStatusTone.Published,
        cancelDialogOpen: Boolean = false,
        state: GameDetailState? = null,
    ) = setContent {
        SaqzTheme {
            GameDetailScreen(
                state = state ?: GameDetailPreviewData.admin.copy(
                        header = GameDetailPreviewData.header.copy(statusTone = status),
                        cancelDialogOpen = cancelDialogOpen,
                    ),
                onBack = {},
                onIntent = {},
            )
        }
    }
}
