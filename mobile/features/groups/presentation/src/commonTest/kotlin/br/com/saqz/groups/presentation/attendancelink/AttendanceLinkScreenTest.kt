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

    @Test fun failedAfterDeclineTapDoesNotShowTheDeclinedText() = runComposeUiTest {
        setContent { SaqzTheme { AttendanceLinkScreen(AttendanceLinkState(AttendanceLinkPhase.Failed, destination), {}, {}, {}) } }
        onNodeWithText(declinedText).assertDoesNotExist()
    }

    @Test fun declineSheetPromisesOnlyWhatIsTrueForEveryone() = runComposeUiTest {
        setContent { SaqzTheme { AttendanceLinkScreen(AttendanceLinkState(AttendanceLinkPhase.DeclineSheet, destination), {}, {}, {}) } }
        onNodeWithText("Sua vaga libera na hora", substring = true).assertDoesNotExist()
        onNodeWithText("Se você tinha vaga", substring = true).assertExists()
    }

    private val declinedText = "Registramos que você não vai. Se você tinha vaga, ela passa para o primeiro da lista de espera."
}
