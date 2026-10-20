package br.com.saqz.groups.presentation.attendancelink

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.domain.GroupId
import br.com.saqz.groups.domain.attendance.share.AttendanceLinkDestination
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AttendanceLinkScreenTest {
    private val offer = "Quer lembrete no celular? Baixe o app Saqz e entre com a mesma conta."
    private val destination = AttendanceLinkDestination(GroupId("group"), "game")

    @Test fun confirmedAndWaitlistedOfferTheAppWhenThereIsAStore() {
        for (phase in listOf(AttendanceLinkPhase.Confirmed, AttendanceLinkPhase.Waitlisted)) runComposeUiTest {
            val intents = mutableListOf<AttendanceLinkIntent>()
            setContent {
                SaqzTheme { AttendanceLinkScreen(AttendanceLinkState(phase, destination), {}, intents::add, {}, appDownloadAvailable = true) }
            }
            onNodeWithText(offer).assertExists()
            onNodeWithTag(AttendanceLinkTags.Download).performClick()
            assertEquals(listOf(AttendanceLinkIntent.OpenAppDownload), intents)
        }
    }

    @Test fun noOfferWithoutAStoreOrBeforeTheAnswer() = runComposeUiTest {
        setContent { SaqzTheme { AttendanceLinkScreen(AttendanceLinkState(AttendanceLinkPhase.Confirmed, destination), {}, {}, {}) } }
        onNodeWithTag(AttendanceLinkTags.Download).assertDoesNotExist()
    }

    @Test fun noOfferWhileLoadingOrOnFailure() {
        for (phase in listOf(AttendanceLinkPhase.Loading, AttendanceLinkPhase.Failed, AttendanceLinkPhase.Invalid, AttendanceLinkPhase.Declined)) runComposeUiTest {
            setContent { SaqzTheme { AttendanceLinkScreen(AttendanceLinkState(phase), {}, {}, {}, appDownloadAvailable = true) } }
            onNodeWithTag(AttendanceLinkTags.Download).assertDoesNotExist()
        }
    }

    @Test fun declineSheetNeverClaimsTheGroupWasNotifiedBeforeTheTap() = runComposeUiTest {
        setContent { SaqzTheme { AttendanceLinkScreen(AttendanceLinkState(AttendanceLinkPhase.DeclineSheet, destination), {}, {}, {}) } }
        onNodeWithText(declinedText).assertDoesNotExist()
        onAllNodesWithText("Confirme abaixo se você não vai.").assertCountEquals(1)
    }

    @Test fun declinedShowsTheFinalTextOnlyAfterTheServerRecorded() = runComposeUiTest {
        setContent { SaqzTheme { AttendanceLinkScreen(AttendanceLinkState(AttendanceLinkPhase.Declined, destination), {}, {}, {}) } }
        onNodeWithText(declinedText).assertExists()
    }

    @Test fun declineSheetPromisesNeitherAGroupNoticeNorAFreeSpot() = runComposeUiTest {
        setContent { SaqzTheme { AttendanceLinkScreen(AttendanceLinkState(AttendanceLinkPhase.DeclineSheet, destination), {}, {}, {}) } }
        onNodeWithText("Não vai poder ir?").assertExists()
        onNodeWithText("Vamos tirar seu nome da lista deste jogo.").assertExists()
        onNodeWithText("Se mudar de ideia, toque em Vou no lembrete.").assertExists()
        onNodeWithText("Vamos avisar o grupo que você não vai.").assertDoesNotExist()
        onNodeWithText("Sua vaga libera na hora", substring = true).assertDoesNotExist()
    }

    @Test fun confirmedByLinkNamesTheGameAndOffersUndo() = runComposeUiTest {
        val intents = mutableListOf<AttendanceLinkIntent>()
        val game = AttendanceLinkGame("TUESDAY", "04/08", "19h30", "CERET", "Quadra 2")
        setContent { SaqzTheme { AttendanceLinkScreen(AttendanceLinkState(AttendanceLinkPhase.Confirmed, destination, game), {}, intents::add, {}) } }
        onNodeWithText("Confirmamos sua presença em Ter, 04/08 · 19h30.").assertExists()
        onNodeWithTag(AttendanceLinkTags.Undo).performClick()
        assertEquals(listOf(AttendanceLinkIntent.Undo), intents)
    }

    @Test fun noUndoAfterDeclinedOrWithoutTheAnswer() = runComposeUiTest {
        setContent { SaqzTheme { AttendanceLinkScreen(AttendanceLinkState(AttendanceLinkPhase.Declined, destination), {}, {}, {}) } }
        onNodeWithTag(AttendanceLinkTags.Undo).assertDoesNotExist()
    }

    @Test fun gameCardShowsDayTimeAndVenueAboveTheState() {
        val game = AttendanceLinkGame("TUESDAY", "04/08", "19h30", "CERET", "Quadra 2")
        for (phase in listOf(AttendanceLinkPhase.Confirmed, AttendanceLinkPhase.Waitlisted, AttendanceLinkPhase.DeclineSheet, AttendanceLinkPhase.Declined)) runComposeUiTest {
            setContent { SaqzTheme { AttendanceLinkScreen(AttendanceLinkState(phase, destination, game), {}, {}, {}) } }
            onNodeWithTag(AttendanceLinkTags.GameCard).assertExists()
            onNodeWithText("Ter, 04/08 · 19h30").assertExists()
            onNodeWithText("CERET").assertExists()
            onNodeWithText("Quadra 2").assertExists()
            onNodeWithText("Jogo").assertExists()
        }
    }

    @Test fun gameCardOfAPendingEntryShowsTheHiddenVenueAndTheNextGameLabel() = runComposeUiTest {
        val game = AttendanceLinkGame("TUESDAY", "04/08", "19h30", "Local após a aprovação", null, groupNext = true)
        setContent { SaqzTheme { AttendanceLinkScreen(AttendanceLinkState(AttendanceLinkPhase.Pending, game = game), {}, {}, {}) } }
        onNodeWithTag(AttendanceLinkTags.GameCard).assertExists()
        onNodeWithText("Local após a aprovação").assertExists()
        onNodeWithText("Próximo jogo").assertExists()
    }

    @Test fun noGameCardWhenTheGameIsUnknown() = runComposeUiTest {
        setContent { SaqzTheme { AttendanceLinkScreen(AttendanceLinkState(AttendanceLinkPhase.Confirmed, destination), {}, {}, {}) } }
        onNodeWithTag(AttendanceLinkTags.GameCard).assertDoesNotExist()
    }

    @Test fun notReceivingShowsTheNeutralTextWithoutRetry() = runComposeUiTest {
        setContent { SaqzTheme { AttendanceLinkScreen(AttendanceLinkState(AttendanceLinkPhase.NotReceiving), {}, {}, {}) } }
        onNodeWithText("Este grupo não está recebendo novos atletas agora. Fale com o organizador.").assertExists()
        onNodeWithText("Não foi possível concluir. Tente novamente.").assertDoesNotExist()
    }

    @Test fun failedAfterDeclineTapDoesNotShowTheDeclinedText() = runComposeUiTest {
        setContent { SaqzTheme { AttendanceLinkScreen(AttendanceLinkState(AttendanceLinkPhase.Failed, destination), {}, {}, {}) } }
        onNodeWithText(declinedText).assertDoesNotExist()
    }

    @Test fun closedGamePhasesShowTheirOwnTextAndNotTheGenericOne() {
        val generic = "Não foi possível confirmar. O prazo pode ter encerrado ou este jogo não está disponível para sua conta."
        mapOf(
            AttendanceLinkPhase.GameStarted to "O jogo já começou.",
            AttendanceLinkPhase.GameCancelled to "Esse jogo foi cancelado.",
            AttendanceLinkPhase.GameUnpublished to "Esse jogo não está mais aberto.",
        ).forEach { (phase, text) ->
            runComposeUiTest {
                setContent { SaqzTheme { AttendanceLinkScreen(AttendanceLinkState(phase), {}, {}, {}) } }
                onNodeWithText(text).assertExists()
                onNodeWithText(generic).assertDoesNotExist()
            }
        }
    }

    private val declinedText = "Pronto: seu nome saiu da lista deste jogo."
}
