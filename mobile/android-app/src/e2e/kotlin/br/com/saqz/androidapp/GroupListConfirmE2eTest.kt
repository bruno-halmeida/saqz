package br.com.saqz.androidapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * GER-003 (APP-LG): "Confirmar" na foto do grupo, na aba Grupos. O jogo do cenário está lotado (4 de 4), então o "Vou"
 * da folha entra na espera: a etiqueta troca para "Na espera" e a API confirma a posição 1.
 */
@RunWith(AndroidJUnit4::class)
internal class GroupListConfirmE2eTest : InstalledE2e("attendance-list") {
    @Test
    fun confirmOnTheGroupListOpensTheSheetAndGoingWaitlistsTheAthlete() {
        val attendancePath = "/api/groups/$group/games/${data.getString("game")}/attendance"
        login("athlete")
        groups()
        click("group-list-confirm-$group")
        waitTag("group-list-confirm-sheet")
        click("group-list-confirm-yes")

        // A etiqueta da foto se funde à faixa do grupo: procura na árvore sem fusão.
        val status = hasTestTag("group-list-status-$group")
        ui.waitUntil(20_000) {
            ui.onAllNodes(status, useUnmergedTree = true).fetchSemanticsNodes().size == 1 &&
                ui.onAllNodes(hasText("Na espera"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        ui.onNodeWithTag("group-list-status-$group", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(0, ui.onAllNodes(hasTestTag("group-list-confirm-$group")).fetchSemanticsNodes().size)

        val waiting = api("athlete", attendancePath)
        assertEquals("WAITLISTED", waiting.getJSONObject("ownAttendance").getString("status"))
        assertEquals(1L, waiting.getJSONObject("ownAttendance").getLong("waitlistPosition"))
        assertEquals(4, waiting.getInt("confirmedCount"))
    }
}
