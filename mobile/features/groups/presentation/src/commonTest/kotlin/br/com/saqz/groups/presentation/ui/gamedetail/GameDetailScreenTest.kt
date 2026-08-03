package br.com.saqz.groups.presentation.ui.gamedetail

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
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

    private fun ComposeUiTest.setScreen(
        status: GameDetailStatusTone = GameDetailStatusTone.Published,
        cancelDialogOpen: Boolean = false,
    ) = setContent {
        SaqzTheme {
            GameDetailScreen(
                state = GameDetailPreviewData.admin.copy(
                    header = GameDetailPreviewData.header.copy(statusTone = status),
                    cancelDialogOpen = cancelDialogOpen,
                ),
                onBack = {},
                onIntent = {},
            )
        }
    }
}
