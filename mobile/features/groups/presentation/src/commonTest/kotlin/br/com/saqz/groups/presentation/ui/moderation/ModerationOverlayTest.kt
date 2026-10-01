package br.com.saqz.groups.presentation.ui.moderation

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.moderation.ReportReason
import br.com.saqz.groups.domain.moderation.ReportTargetType
import br.com.saqz.groups.presentation.moderation.BlockPromptUi
import br.com.saqz.groups.presentation.moderation.ModerationFeedback
import br.com.saqz.groups.presentation.moderation.ModerationIntent
import br.com.saqz.groups.presentation.moderation.ModerationState
import br.com.saqz.groups.presentation.moderation.ReportDraftUi
import br.com.saqz.groups.presentation.moderation.ReportTargetUi
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ModerationOverlayTest {
    private val notice = ReportTargetUi("group-1", ReportTargetType.MESSAGE, "message-1", "Bia Souza")

    @Test fun reportSheetNamesTheTargetAndWaitsForAReason() = runComposeUiTest {
        val intents = mutableListOf<ModerationIntent>()
        content(ModerationState(report = ReportDraftUi(notice))) { intents += it }

        onNodeWithText("Denunciar").assertExists()
        onNodeWithText("Aviso de Bia Souza").assertExists()
        onNodeWithText("Spam ou propaganda").assertExists()
        onNodeWithText("Conteúdo ofensivo ou impróprio").assertExists()
        onNodeWithText("Assédio ou ameaça").assertExists()
        onNodeWithText("Outro motivo").assertExists()
        onNodeWithTag(ModerationTags.Submit).assertIsNotEnabled()

        onNodeWithTag(ModerationTags.reason(ReportReason.HARASSMENT)).performClick()
        assertEquals(listOf<ModerationIntent>(ModerationIntent.SelectReason(ReportReason.HARASSMENT)), intents)
    }

    @Test fun pickedReasonEnablesTheSubmitAndShowsAsSelected() = runComposeUiTest {
        val intents = mutableListOf<ModerationIntent>()
        content(ModerationState(report = ReportDraftUi(notice, reason = ReportReason.SPAM))) { intents += it }

        onNodeWithTag(ModerationTags.reason(ReportReason.SPAM)).assertIsSelected()
        onNodeWithTag(ModerationTags.Submit).assertIsEnabled().performClick()
        assertEquals(ModerationIntent.SubmitReport, intents.last())
    }

    @Test fun failedReportShowsTheNetworkError() = runComposeUiTest {
        content(ModerationState(report = ReportDraftUi(notice, reason = ReportReason.SPAM, failed = true)))

        onNodeWithText("Não foi possível enviar a denúncia. Verifique sua conexão e tente de novo.").assertExists()
    }

    @Test fun groupReportNamesTheGroup() = runComposeUiTest {
        content(ModerationState(report = ReportDraftUi(ReportTargetUi("group-1", ReportTargetType.GROUP, "group-1", "Vôlei da Firma"))))

        onNodeWithText("Grupo Vôlei da Firma").assertExists()
    }

    @Test fun blockSheetExplainsTheConsequencesAndConfirms() = runComposeUiTest {
        val intents = mutableListOf<ModerationIntent>()
        content(ModerationState(block = BlockPromptUi("group-1", "bia", "Bia Souza"))) { intents += it }

        onNodeWithText("Bloquear Bia Souza?").assertExists()
        onNodeWithText(
            "Você deixa de ver os avisos e mensagens dessa pessoa e não recebe mais notificações dela. " +
                "Ela não é avisada. Nossa equipe também recebe um alerta para analisar.",
        ).assertExists()
        onNodeWithTag(ModerationTags.BlockConfirm).performClick()
        onNodeWithTag(ModerationTags.BlockCancel).performClick()
        assertEquals(listOf(ModerationIntent.ConfirmBlock, ModerationIntent.DismissBlock), intents)
    }

    @Test fun feedbackToastShowsTheResult() = runComposeUiTest {
        content(ModerationState(feedback = ModerationFeedback.ReportSent))

        onNodeWithText("Denúncia enviada. Nossa equipe analisa em até 24 horas.").assertExists()
    }

    private fun ComposeUiTest.content(state: ModerationState, onIntent: (ModerationIntent) -> Unit = {}) = setContent {
        SaqzTheme { ModerationOverlay(state = state, onIntent = onIntent) }
    }
}
