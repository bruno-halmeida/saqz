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
        onNodeWithText("Você não vai neste jogo").assertDoesNotExist()
        // Atrás da folha fica só o cartão do jogo: sem texto de fase e sem "Ver jogo".
        onNodeWithTag(AttendanceLinkTags.OpenGame).assertDoesNotExist()
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
        onNodeWithText("Sua vaga está confirmada").assertExists()
        onNodeWithText("Seu nome está na lista do jogo de Ter, 04/08 · 19h30.").assertExists()
        onNodeWithText("Não vou mais").assertExists()
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
        // O texto do local que vem do backend fica de fora: quem aguarda não vê o local.
        onNodeWithText("Local após a aprovação").assertDoesNotExist()
        onNodeWithText("Local liberado na aprovação").assertExists()
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

    private val declinedText = "Seu nome saiu da lista. Valeu por avisar a turma."

    private val game = AttendanceLinkGame("THURSDAY", "22/10", "21h00", "Arena", "Quadra 2", capacity = 14, confirmedCount = 12)

    @Test fun pendingShowsTheStepsTheFooterAndTheBarTitle() = runComposeUiTest {
        val intents = mutableListOf<AttendanceLinkIntent>()
        var advanced: String? = null
        val state = AttendanceLinkState(
            AttendanceLinkPhase.Pending, game = game.copy(groupNext = true), pendingGroupId = "g1",
            pendingGroupName = "Vôlei da Quinta", pendingInviterName = "Ana Lima",
        )
        setContent { SaqzTheme { AttendanceLinkScreen(state, {}, intents::add, {}, { advanced = it }) } }
        // Título da barra e primeiro passo.
        onAllNodesWithText("Pedido enviado").assertCountEquals(2)
        onNodeWithText("Falta a aprovação da organização").assertExists()
        onNodeWithText("Seu pedido para entrar no Vôlei da Quinta já chegou. Você recebe um aviso no app quando ele for aprovado.").assertExists()
        onNodeWithText("Aguardando").assertExists()
        onNodeWithText("Ana Lima recebeu seu pedido").assertExists()
        onNodeWithText("Dá para adiantar agora").assertExists()
        onNodeWithText("Presença no jogo de quinta").assertExists()
        onNodeWithText("Confirmada quando você voltar aqui depois da aprovação").assertExists()
        onNodeWithText("Adiantar meu cadastro").performClick()
        assertEquals("g1", advanced)
        onNodeWithTag(AttendanceLinkTags.Refresh).performClick()
        assertEquals(listOf(AttendanceLinkIntent.Refresh), intents)
    }

    @Test fun pendingWithTheRegistrationSavedOffersToReviewIt() = runComposeUiTest {
        val state = AttendanceLinkState(AttendanceLinkPhase.Pending, pendingGroupId = "g1", pendingRegistrationSaved = true)
        setContent { SaqzTheme { AttendanceLinkScreen(state, {}, {}, {}) } }
        onNodeWithText("Cadastro guardado no pedido").assertExists()
        onNodeWithText("Revisar meu cadastro").assertExists()
        onNodeWithText("Adiantar meu cadastro").assertDoesNotExist()
        onNodeWithText("Seu pedido para entrar no grupo já chegou. Você recebe um aviso no app quando ele for aprovado.").assertExists()
        onNodeWithText("Presença no jogo").assertExists()
    }

    @Test fun pendingWithoutAGroupHasNoPrimaryButton() = runComposeUiTest {
        setContent { SaqzTheme { AttendanceLinkScreen(AttendanceLinkState(AttendanceLinkPhase.Pending), {}, {}, {}) } }
        onNodeWithTag(AttendanceLinkTags.AdvanceRegistration).assertDoesNotExist()
        onNodeWithTag(AttendanceLinkTags.Refresh).assertExists()
    }

    @Test fun confirmedShowsTheSpotsMeterOnlyWhenTheCardHasTheCounts() = runComposeUiTest {
        setContent {
            SaqzTheme { AttendanceLinkScreen(AttendanceLinkState(AttendanceLinkPhase.Confirmed, destination, game), {}, {}, {}) }
        }
        onNodeWithText("Vagas preenchidas").assertExists()
        onNodeWithText("12 de 14").assertExists()
        onNodeWithText("Ver jogo").assertExists()
    }

    @Test fun confirmedWithoutCountsOrGameHasNoMeterAndKeepsTheGenericText() = runComposeUiTest {
        setContent { SaqzTheme { AttendanceLinkScreen(AttendanceLinkState(AttendanceLinkPhase.Confirmed, destination), {}, {}, {}) } }
        onNodeWithTag(AttendanceLinkTags.Spots).assertDoesNotExist()
        onNodeWithText("Seu nome está na lista deste jogo.").assertExists()
    }

    @Test fun waitlistedShowsThePositionAndHowManyAreAhead() {
        mapOf(
            1L to "Ninguém na sua frente", 2L to "1 pessoa na sua frente", 3L to "2 pessoas na sua frente",
        ).forEach { (position, ahead) ->
            runComposeUiTest {
                val state = AttendanceLinkState(AttendanceLinkPhase.Waitlisted, destination, game, waitlistPosition = position)
                setContent { SaqzTheme { AttendanceLinkScreen(state, {}, {}, {}) } }
                onNodeWithText("Você está na lista de espera").assertExists()
                onNodeWithText("${position}º").assertExists()
                onNodeWithText("Sua vez na fila").assertExists()
                onNodeWithText(ahead).assertExists()
                onNodeWithText("Sair da lista de espera").assertExists()
            }
        }
    }

    @Test fun waitlistedWithoutAPositionHasNoTurnBox() = runComposeUiTest {
        setContent { SaqzTheme { AttendanceLinkScreen(AttendanceLinkState(AttendanceLinkPhase.Waitlisted, destination), {}, {}, {}) } }
        onNodeWithTag(AttendanceLinkTags.WaitlistTurn).assertDoesNotExist()
    }

    @Test fun declinedHasOnlyTheOpenGameButton() = runComposeUiTest {
        setContent { SaqzTheme { AttendanceLinkScreen(AttendanceLinkState(AttendanceLinkPhase.Declined, destination, game), {}, {}, {}) } }
        onNodeWithText("Você não vai neste jogo").assertExists()
        onNodeWithTag(AttendanceLinkTags.OpenGame).assertExists()
        onNodeWithTag(AttendanceLinkTags.Undo).assertDoesNotExist()
    }

    @Test fun declineSheetNamesTheGameWhenThereIsOne() = runComposeUiTest {
        setContent { SaqzTheme { AttendanceLinkScreen(AttendanceLinkState(AttendanceLinkPhase.DeclineSheet, destination, game), {}, {}, {}) } }
        onNodeWithText("Seu nome sai da lista do jogo de Qui, 22/10 · 21h00.").assertExists()
    }
}
