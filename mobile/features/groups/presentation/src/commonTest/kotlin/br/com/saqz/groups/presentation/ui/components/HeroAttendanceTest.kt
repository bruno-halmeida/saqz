package br.com.saqz.groups.presentation.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.presentation.home.HomeWaitlistKind
import br.com.saqz.groups.presentation.ui.home.HomeWaitlistTags
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class HeroAttendanceTest {
    // De propósito a cópia do detalhe do grupo, não a da Início: prova que nada está cravado.
    private val texts = HeroAttendanceTexts(
        yes = "Vou",
        no = "Não vou",
        confirmed = "Sua presença está confirmada.",
        declined = "Você não vai jogar.",
        change = "Alterar",
        cancel = "Cancelar",
        error = "Não foi possível salvar sua resposta. Tente novamente.",
    )
    private val tags = HeroAttendanceTags(
        yes = "t-yes",
        no = "t-no",
        change = "t-change",
        cancel = "t-cancel",
        error = "t-error",
    )

    @Test
    fun unansweredShowsBothBigButtonsWithTheGivenTagsAndTexts() = runComposeUiTest {
        val responses = mutableListOf<AttendanceIntent>()
        setControls(status = null, onRespond = responses::add)

        onNodeWithText("Vou").assertIsDisplayed()
        onNodeWithText("Não vou").assertIsDisplayed()
        onNodeWithTag("t-yes").assertHeightIsEqualTo(52.dp).performClick()
        onNodeWithTag("t-no").assertHeightIsEqualTo(52.dp).performClick()
        onAllNodesWithTag("t-change").assertCountEquals(0)
        onAllNodesWithTag("t-error").assertCountEquals(0)

        assertEquals(listOf(AttendanceIntent.Confirm, AttendanceIntent.Decline), responses)
    }

    @Test
    fun confirmedShowsTheGivenConfirmedTextAndTheChangeButton() = runComposeUiTest {
        setControls(status = AttendanceStatus.Confirmed)

        onNodeWithText("Sua presença está confirmada.").assertIsDisplayed()
        onNodeWithTag("t-change").assertIsDisplayed()
        onNodeWithText("Alterar").assertIsDisplayed()
        onAllNodesWithTag("t-yes").assertCountEquals(0)
        onAllNodesWithTag("t-no").assertCountEquals(0)
    }

    @Test
    fun changeRevealsTheSmallButtonsAndCancelGoesBackToThePanel() = runComposeUiTest {
        val responses = mutableListOf<AttendanceIntent>()
        setControls(status = AttendanceStatus.Confirmed, onRespond = responses::add)

        onNodeWithTag("t-change").performClick()

        onNodeWithTag("t-yes").assertHeightIsEqualTo(44.dp)
        onNodeWithTag("t-no").assertHeightIsEqualTo(44.dp)
        onNodeWithText("Cancelar").assertIsDisplayed()
        onAllNodesWithTag("t-change").assertCountEquals(0)

        onNodeWithTag("t-cancel").performClick()

        onNodeWithTag("t-change").assertIsDisplayed()
        onAllNodesWithTag("t-yes").assertCountEquals(0)
        assertEquals(emptyList<AttendanceIntent>(), responses)
    }

    @Test
    fun changingTheAnswerEmitsTheNewChoice() = runComposeUiTest {
        val responses = mutableListOf<AttendanceIntent>()
        setControls(status = AttendanceStatus.Confirmed, onRespond = responses::add)

        onNodeWithTag("t-change").performClick()
        onNodeWithTag("t-no").performClick()

        assertEquals(listOf(AttendanceIntent.Decline), responses)
    }

    @Test
    fun closedConfirmationDisablesBothButtons() = runComposeUiTest {
        setControls(status = null, confirmationOpen = false)

        onNodeWithTag("t-yes").assertIsNotEnabled()
        onNodeWithTag("t-no").assertIsNotEnabled()
    }

    @Test
    fun closedConfirmationDisablesChange() = runComposeUiTest {
        setControls(status = AttendanceStatus.Confirmed, confirmationOpen = false)

        onNodeWithTag("t-change").assertIsNotEnabled()
    }

    @Test
    fun waitlistedShowsTheQueueBlockInsteadOfTheYesButton() = runComposeUiTest {
        val responses = mutableListOf<AttendanceIntent>()
        var gameViews = 0
        setControls(
            status = AttendanceStatus.Waitlisted,
            waitlistKind = HomeWaitlistKind.Reserva,
            waitlistPosition = 1,
            onRespond = responses::add,
            onViewGame = { gameViews += 1 },
        )

        onAllNodesWithTag("t-yes").assertCountEquals(0)
        onAllNodesWithTag("t-no").assertCountEquals(0)
        onAllNodesWithTag("t-change").assertCountEquals(0)
        onNodeWithTag(HomeWaitlistTags.ReservaChip).assertIsDisplayed()
        onNodeWithTag(HomeWaitlistTags.ReservaViewGame).performClick()
        onNodeWithTag(HomeWaitlistTags.ReservaLeave).performClick()

        assertEquals(1, gameViews)
        assertEquals(listOf(AttendanceIntent.Decline), responses)
    }

    @Test
    fun responseFailureRendersTheGivenErrorUnderTheGivenTag() = runComposeUiTest {
        setControls(status = null, responseFailed = true)

        onNodeWithTag("t-error").assertIsDisplayed()
        onNodeWithText("Não foi possível salvar sua resposta. Tente novamente.").assertIsDisplayed()
    }

    @Test
    fun alertLineComposesTheActionSlot() = runComposeUiTest {
        setContent {
            SaqzTheme {
                HeroAlertLine(text = "Não foi possível abrir o mapa.", tag = null) {
                    Box(modifier = Modifier.size(24.dp).testTag("t-action"))
                }
            }
        }

        onNodeWithText("Não foi possível abrir o mapa.").assertIsDisplayed()
        onNodeWithTag("t-action").assertIsDisplayed()
    }

    private fun ComposeUiTest.setControls(
        status: AttendanceStatus?,
        confirmationOpen: Boolean = true,
        responding: Boolean = false,
        responseFailed: Boolean = false,
        waitlistKind: HomeWaitlistKind? = null,
        waitlistPosition: Long? = null,
        onRespond: (AttendanceIntent) -> Unit = {},
        onViewGame: () -> Unit = {},
    ) = setContent {
        SaqzTheme {
            Column {
                HeroAttendanceControls(
                    status = status,
                    confirmationOpen = confirmationOpen,
                    responding = responding,
                    responseFailed = responseFailed,
                    waitlistKind = waitlistKind,
                    waitlistPosition = waitlistPosition,
                    onRespond = onRespond,
                    onViewGame = onViewGame,
                    texts = texts,
                    tags = tags,
                )
            }
        }
    }
}
