package br.com.saqz.groups.presentation.ui.attendancehistory

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.attendancehistory.AttendanceHistoryIntent
import br.com.saqz.groups.presentation.attendancehistory.AttendanceHistoryRowUi
import br.com.saqz.groups.presentation.attendancehistory.AttendanceHistoryState
import br.com.saqz.groups.presentation.attendancehistory.AttendanceHistoryTone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** DEB-135 · tela "Histórico de respostas". */
@OptIn(ExperimentalTestApi::class)
class AttendanceHistoryScreenTest {
    private fun row(
        id: String,
        day: String,
        title: String,
        meta: String,
        status: String,
        tone: AttendanceHistoryTone,
        reason: String? = null,
        guest: Boolean = false,
    ) = AttendanceHistoryRowUi(id, day, "Felipe Nunes", null, guest, tone == AttendanceHistoryTone.Going, title, meta, reason, status, tone)

    private val loaded = AttendanceHistoryState(
        isLoading = false,
        rows = listOf(
            row("1", "HOJE", "Felipe Nunes subiu da espera", "19h32 · automático, a vaga de Carla abriu", "Vai", AttendanceHistoryTone.Going),
            row("2", "HOJE", "Bruno tirou Pedro Lima do jogo", "18h05 · organizador", "Não vai", AttendanceHistoryTone.NotGoing, reason = "Motivo: \"pediu para sair\""),
            row("3", "HOJE", "Felipe Nunes entrou na espera", "17h40 · jogo estava cheio", "Espera", AttendanceHistoryTone.Waiting),
            row("4", "ONTEM", "Ana Souza levou um convidado", "21h10 · convidado(a) de Ana", "Vai", AttendanceHistoryTone.Going, guest = true),
        ),
    )

    @Test
    fun barraDiasLinhasMotivoEEstados() = runComposeUiTest {
        setScreen(loaded)

        onNodeWithText("Histórico de respostas").assertExists()
        onNodeWithText("Jogo de quinta, 22/10 · 20h00").assertExists()
        onNodeWithTag(AttendanceHistoryTags.day("HOJE")).assertExists()
        onNodeWithTag(AttendanceHistoryTags.day("ONTEM")).assertExists()
        onNodeWithText("19h32 · automático, a vaga de Carla abriu").assertExists()
        onNodeWithText("Motivo: \"pediu para sair\"").assertExists()
        onNodeWithText("Espera").assertExists()
        onNodeWithText("Ana Souza levou um convidado").assertExists()
    }

    @Test
    fun vazioDizQueNinguemRespondeu() = runComposeUiTest {
        setScreen(AttendanceHistoryState(isLoading = false))
        onNodeWithText("Ninguém respondeu ainda.").assertExists()
    }

    @Test
    fun erroNaPrimeiraCargaMostraTentarDeNovo() = runComposeUiTest {
        setScreen(AttendanceHistoryState(isLoading = false, loadFailed = true, error = GroupUiError.Network))
        onNodeWithTag(AttendanceHistoryTags.LoadFailure).assertExists()
        onAllNodesWithTag(AttendanceHistoryTags.List).assertCountEquals(0)
    }

    @Test
    fun fimDaListaPedeAProximaEFalhaDeixaTentar() = runComposeUiTest {
        val intents = mutableListOf<AttendanceHistoryIntent>()
        setScreen(loaded.copy(canLoadMore = true, loadMoreFailed = true)) { intents += it }

        onNodeWithTag(AttendanceHistoryTags.List).performScrollToNode(hasTestTag(AttendanceHistoryTags.LoadMoreRetry))
        waitForIdle()
        onNodeWithTag(AttendanceHistoryTags.LoadMoreRetry).performClick()

        assertTrue(AttendanceHistoryIntent.LoadMore in intents)
        assertEquals(AttendanceHistoryIntent.LoadMore, intents.last())
    }

    @Test
    fun falhaAoAtualizarMostraOAviso() = runComposeUiTest {
        setScreen(loaded.copy(refreshFailed = true))
        onNodeWithText("Não foi possível atualizar. Verifique sua conexão e tente de novo.").assertExists()
    }

    private fun ComposeUiTest.setScreen(state: AttendanceHistoryState, onIntent: (AttendanceHistoryIntent) -> Unit = {}) = setContent {
        SaqzTheme { AttendanceHistoryScreen(state, "Jogo de quinta, 22/10 · 20h00", onIntent, {}) }
    }
}
