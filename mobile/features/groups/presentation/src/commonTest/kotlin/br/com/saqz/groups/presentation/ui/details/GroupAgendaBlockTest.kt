package br.com.saqz.groups.presentation.ui.details

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GroupAgendaBlockTest {
    @Test
    fun emptyAgendaEmitsNothingForMemberOrAdmin() = runComposeUiTest {
        setDetailsScreen(GroupDetailsPreviewData.admin)

        onAllNodesWithTag(GroupDetailsTags.Agenda).assertCountEquals(0)
        onAllNodesWithTag(GroupDetailsTags.AgendaCreate).assertCountEquals(0)
        onAllNodesWithTag(GroupDetailsTags.AgendaMore).assertCountEquals(0)
    }

    @Test
    fun twoRowsHaveNoMoreButton() = runComposeUiTest {
        val agenda = listOf(GroupAgendaPreviewData.pending, GroupAgendaPreviewData.going)
        setDetailsScreen(GroupDetailsPreviewData.member.copy(agenda = agenda))

        onNodeWithTag(GroupDetailsTags.Agenda).assertExists()
        onNodeWithTag(GroupDetailsTags.agendaGame("game-2")).assertExists()
        onNodeWithTag(GroupDetailsTags.agendaGame("game-3")).assertExists()
        onAllNodesWithTag(GroupDetailsTags.AgendaMore).assertCountEquals(0)
    }

    @Test
    fun fiveRowsShowThreeAndExpandInPlace() = runComposeUiTest {
        setDetailsScreen(GroupAgendaPreviewData.memberMore)

        onNodeWithTag(GroupDetailsTags.agendaGame("game-4")).assertExists()
        onAllNodesWithTag(GroupDetailsTags.agendaGame("game-5")).assertCountEquals(0)
        onAllNodesWithTag(GroupDetailsTags.agendaGame("game-6")).assertCountEquals(0)
        onNodeWithTag(GroupDetailsTags.AgendaMore)
            .assertTextEquals("Ver mais 2 jogos")
            .assertHeightIsAtLeast(48.dp)
            .performScrollTo()
            .performClick()

        listOf("game-2", "game-3", "game-4", "game-5", "game-6").forEach {
            onNodeWithTag(GroupDetailsTags.agendaGame(it)).assertExists()
        }
        onAllNodesWithTag(GroupDetailsTags.AgendaMore).assertCountEquals(0)
    }

    @Test
    fun fourRowsUseTheSingular() = runComposeUiTest {
        val agenda = GroupAgendaPreviewData.memberMore.agenda.take(4)
        setDetailsScreen(GroupDetailsPreviewData.member.copy(agenda = agenda))

        onNodeWithTag(GroupDetailsTags.AgendaMore).assertTextEquals("Ver mais 1 jogo")
    }

    @Test
    fun rowTapOpensThatGame() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupAgendaPreviewData.member) { intents += it }

        onNodeWithTag(GroupDetailsTags.agendaGame("game-3")).performScrollTo().performClick()

        assertEquals(listOf<GroupDetailsIntent>(GroupDetailsIntent.OpenAgendaGame("game-3")), intents)
    }

    @Test
    fun adminCreateActionEmitsCreateNextGame() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupAgendaPreviewData.adminDraft) { intents += it }

        onNode(hasText("Marcar jogo") and hasAnyAncestor(hasTestTag(GroupDetailsTags.AgendaCreate)))
            .performScrollTo()
            .performClick()

        assertEquals(listOf<GroupDetailsIntent>(GroupDetailsIntent.CreateNextGame), intents)
    }

    @Test
    fun memberHasNoCreateAction() = runComposeUiTest {
        setDetailsScreen(GroupAgendaPreviewData.member)

        onNodeWithTag(GroupDetailsTags.Agenda).assertExists()
        onAllNodesWithTag(GroupDetailsTags.AgendaCreate).assertCountEquals(0)
    }

    @Test
    fun chipShowsTheStatusLabelOfEachRow() = runComposeUiTest {
        val agenda = GroupAgendaPreviewData.memberMore.agenda.take(4) + GroupAgendaPreviewData.draft
        setDetailsScreen(GroupDetailsPreviewData.admin.copy(agenda = agenda))
        onNodeWithTag(GroupDetailsTags.AgendaMore).performScrollTo().performClick()

        chipOf("game-2", "Sem resposta").assertExists()
        chipOf("game-3", "Você vai").assertExists()
        chipOf("game-4", "Na espera").assertExists()
        chipOf("game-5", "Não vai").assertExists()
        chipOf("game-7", "Rascunho").assertExists()
    }

    // A linha é clicável e funde os filhos: o chip só é nó próprio na árvore não fundida.
    private fun ComposeUiTest.chipOf(gameId: String, label: String) = onNode(
        hasText(label) and hasAnyAncestor(hasTestTag(GroupDetailsTags.agendaGame(gameId))),
        useUnmergedTree = true,
    )
}
