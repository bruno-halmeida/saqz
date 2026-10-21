package br.com.saqz.groups.presentation.ui.gamedetail

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.gamedetail.GameDetailIntent
import br.com.saqz.groups.presentation.gamedetail.GameDetailState
import br.com.saqz.groups.presentation.gamedetail.GameGuestRemovalUi
import kotlin.test.Test
import kotlin.test.assertEquals

private const val PromotionReason = "Escolha do organizador"

@OptIn(ExperimentalTestApi::class)
class GameGuestSectionTest {
    @Test
    fun buttonIsAbsentWhenNotVisible() = runComposeUiTest {
        setScreen(GameDetailPreviewData.admin.copy(isAdmin = false))

        onAllNodesWithTag(GameGuestTags.Add).assertCountEquals(0)
        onAllNodesWithTag(GameGuestTags.Hint).assertCountEquals(0)
    }

    @Test
    fun withoutAnswerOrWithClosedConfirmationsTheRowDoesNotShow() {
        // DEB-116: sem "Vou" (ou com as confirmações fechadas) a linha "Levar convidado" some, sem botão desabilitado.
        runComposeUiTest {
            setScreen(GameGuestPreviewData.needAnswer)
            onAllNodesWithTag(GameGuestTags.Add).assertCountEquals(0)
        }
        runComposeUiTest {
            setScreen(GameGuestPreviewData.closed)
            onAllNodesWithTag(GameGuestTags.Add).assertCountEquals(0)
        }
    }

    @Test
    fun tappingTheButtonAsksToOpenTheSheet() = runComposeUiTest {
        val intents = mutableListOf<GameDetailIntent>()
        setScreen(GameGuestPreviewData.empty, intents::add)

        onNodeWithTag(GameGuestTags.Add).performScrollTo().performClick()

        assertEquals(GameDetailIntent.OpenGuestSheet, intents.single())
    }

    @Test
    fun sheetSubmitIsDisabledUntilTheNameIsValid() {
        runComposeUiTest {
            setScreen(GameGuestPreviewData.sheetEmpty)

            onNodeWithTag(GameGuestTags.Submit).assertIsNotEnabled()
        }
        runComposeUiTest {
            setScreen(GameGuestPreviewData.sheetFilled)

            onNodeWithTag(GameGuestTags.Submit).assertIsEnabled()
        }
    }

    @Test
    fun typingEmitsUpdateGuestName() = runComposeUiTest {
        val intents = mutableListOf<GameDetailIntent>()
        setScreen(GameGuestPreviewData.sheetEmpty, intents::add)

        onNode(hasSetTextAction(), useUnmergedTree = true).performTextInput("Rafa")

        assertEquals(GameDetailIntent.UpdateGuestName("Rafa"), intents.single())
    }

    @Test
    fun feeLineOnlyAppearsWhenTheGameHasAFee() {
        runComposeUiTest {
            setScreen(GameGuestPreviewData.sheetFilled)

            onNodeWithText("Se o convidado entrar no jogo, a taxa de R$ 25,00 vem para você.").assertExists()
        }
        runComposeUiTest {
            setScreen(GameGuestPreviewData.noFee)

            onAllNodesWithText("vem para você.", substring = true).assertCountEquals(0)
        }
    }

    @Test
    fun myGuestShowsRemoveAndOthersDoNot() {
        runComposeUiTest {
            setScreen(GameGuestPreviewData.inQueue)
            onNodeWithTag(GameDetailTags.TabWaiting).performClick()

            onNodeWithTag(GameDetailTags.more("1#1")).assertExists()
        }
        runComposeUiTest {
            setScreen(GameGuestPreviewData.othersView)
            onNodeWithTag(GameDetailTags.TabWaiting).performClick()

            onAllNodesWithTag(GameDetailTags.more("1#1")).assertCountEquals(0)
        }
    }

    @Test
    fun removeAsksWithTheRowId() {
        runComposeUiTest {
            val intents = mutableListOf<GameDetailIntent>()
            setScreen(GameGuestPreviewData.inQueue, intents::add)
            onNodeWithTag(GameDetailTags.TabWaiting).performClick()

            onNodeWithTag(GameDetailTags.more("1#1")).performScrollTo().performClick()

            assertEquals(GameDetailIntent.OpenPersonActions("1#1"), intents.single())
        }
        runComposeUiTest {
            val intents = mutableListOf<GameDetailIntent>()
            setScreen(GameGuestPreviewData.inQueue.copy(personActionsId = "1#1"), intents::add)

            onNodeWithText("Tirar convidado(a)").assertExists()
            onNodeWithTag(GameGuestTags.remove("1#1")).performClick()

            assertEquals(GameDetailIntent.RequestRemoveGuest("1#1"), intents.single())
        }
    }

    @Test
    fun promoteOnAGuestSendsHostIdAndSeq() = runComposeUiTest {
        val intents = mutableListOf<GameDetailIntent>()
        setScreen(GameGuestPreviewData.organizerQueue, intents::add)
        onNodeWithTag(GameDetailTags.TabWaiting).performClick()

        onNodeWithTag(GameWaitlistTags.promote("1#1")).performScrollTo().performClick()

        assertEquals(GameDetailIntent.Promote("1", PromotionReason, 1), intents.single())
    }

    @Test
    fun removeSheetCopyDependsOnStatusAndFee() {
        runComposeUiTest {
            setScreen(GameGuestPreviewData.removeWaitlisted)

            onNodeWithText("Sai da lista de espera. Nada é cobrado.").assertExists()
        }
        runComposeUiTest {
            setScreen(GameGuestPreviewData.removeConfirmed)

            onNodeWithText(
                "Sai dos confirmados, a vaga vai para o próximo da lista e a cobrança de R$ 25,00 em seu nome é cancelada.",
            ).assertExists()
        }
    }

    @Test
    fun noticeShowsJoinedAndRemovedCopy() {
        runComposeUiTest {
            setScreen(GameGuestPreviewData.inQueue)

            onNodeWithText("Rafa Moreira entrou na lista de espera.").assertExists()
        }
        runComposeUiTest {
            setScreen(
                GameGuestPreviewData.empty.copy(
                    guest = GameGuestPreviewData.guestUi.copy(noticeName = "Rafa Moreira", noticeJoined = false),
                ),
            )

            onNodeWithText("Rafa Moreira saiu do jogo.").assertExists()
        }
    }

    @Test
    fun removeSheetNamesTheHostOnlyWhenTheOrganizerRemoves() = runComposeUiTest {
        val removal = GameGuestRemovalUi("1#1", "1", 1, "Rafa Moreira", confirmed = false, notifiedHostName = "Ana")
        setScreen(GameGuestPreviewData.inQueue.copy(guest = GameGuestPreviewData.guestUi.copy(removal = removal)))
        onNodeWithText("O anfitrião Ana vai receber um aviso, ok?", substring = true).assertExists()
    }

    @Test
    fun removeSheetHasNoHostNoticeWhenTheHostRemoves() = runComposeUiTest {
        val removal = GameGuestRemovalUi("1#1", "1", 1, "Rafa Moreira", confirmed = false)
        setScreen(GameGuestPreviewData.inQueue.copy(guest = GameGuestPreviewData.guestUi.copy(removal = removal)))
        onAllNodesWithText("vai receber um aviso", substring = true).assertCountEquals(0)
    }

    @Test
    fun everyGuestTagIsUnique() = runComposeUiTest {
        // Quem olha está na espera: a aba abre nela e a linha do convidado aparece.
        val state = GameGuestPreviewData.inQueue.copy(
            response = GameDetailResponsePreviewData.reserva,
            guest = GameGuestPreviewData.guestUi.copy(
                sheetOpen = true,
                name = "Rafa Moreira",
                addFailed = true,
                removal = GameGuestRemovalUi("1#1", "1", 1, "Rafa Moreira", confirmed = false),
                noticeName = "Rafa Moreira",
            ),
        )
        setScreen(state)

        listOf(
            GameGuestTags.Sheet,
            GameGuestTags.Name,
            GameGuestTags.Submit,
            GameGuestTags.AddFailed,
            GameGuestTags.RemoveSheet,
            GameGuestTags.RemoveConfirm,
            GameGuestTags.Notice,
            GameGuestTags.row("1#1"),
        ).forEach { onAllNodesWithTag(it).assertCountEquals(1) }
    }

    private fun ComposeUiTest.setScreen(state: GameDetailState, onIntent: (GameDetailIntent) -> Unit = {}) = setContent {
        SaqzTheme { GameDetailScreen(state, {}, onIntent) }
    }
}
