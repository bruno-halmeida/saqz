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

    private val declinedText = "Avisamos o grupo que você não vai. Sua vaga ficou livre para a fila de espera."
}
