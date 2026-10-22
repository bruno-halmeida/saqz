package br.com.saqz.androidapp

import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** JOGO-011 + PRES-016: Jogos anteriores → marcar falta → Dispensar no acerto; o atleta não vê nada disso. */
@RunWith(AndroidJUnit4::class)
internal class PastGamesNoShowE2eTest : InstalledE2e("past-games-no-show") {
    private val game get() = data.getString("game")
    private val charge get() = data.getString("noShowCharge")
    private val athleteId get() = actor("athlete").getString("id")
    private val gamePath get() = "/api/groups/$group/games/$game"
    private val chargesPath get() = "/api/groups/$group/charges"

    @Test
    fun organizerMarksNoShowWaivesTheFeeAndUndoesWhileTheAthleteSeesNothing() {
        login("owner")
        openPastGames()
        assertPending(true)
        click("past-games-game-$game")
        waitTag("game-detail")
        waitText("Encerrar jogo e acertar")
        // Sem falta marcada não há a aba Faltaram.
        waitTag("game-tab-going")
        ui.onNodeWithTag("game-tab-no-show").assertDoesNotExist()

        markNoShow()
        assertTrue(rosterEntry().getBoolean("noShow"))
        click("game-tab-no-show", scroll = true)
        waitTag("game-no-show-tag-$athleteId")
        waitTag("game-no-show-footer")

        ui.onNode(hasText("Encerrar jogo e acertar") and hasClickAction()).performScrollTo().performClick()
        waitTag("game-settlement")
        waitTag("game-settlement-no-show-$charge")
        val before = api("owner", chargesPath).getJSONArray("charges").getJSONObject(0)
        assertTrue(before.getBoolean("noShow"))
        assertEquals("PENDING", before.getString("status"))
        click("game-settlement-waive-$charge", scroll = true)
        click("finance-waive-confirm")
        ui.waitUntil(20_000) { ui.onAllNodesWithTag("game-settlement-waive-$charge").fetchSemanticsNodes().isEmpty() }
        val waived = api("owner", chargesPath)
        assertEquals("WAIVED", waived.getJSONArray("charges").getJSONObject(0).getString("status"))
        assertEquals(0L, waived.getLong("pendingTotalCents"))

        back()
        waitTag("game-detail")
        back()
        waitTag("past-games-list")
        assertPending(false)
        assertEquals(0, pastGame().getInt("pendingChargeCount"))
        // A lista de anteriores e o grupo cobrem as abas: volta até elas para sair.
        back()
        waitTag("group-details")
        back()
        logout()

        login("athlete")
        openFromNotification()
        // O atleta continua em Jogaram, sem a aba, a marca nem o menu.
        waitTag("game-tab-going")
        ui.onNode(hasText("Jogaram") and hasAnyAncestor(hasTestTag("game-tab-going")), true).assertExists()
        waitTag("game-detail-row-$athleteId")
        ui.onNodeWithTag("game-tab-no-show").assertDoesNotExist()
        ui.onNodeWithTag("game-no-show-tag-$athleteId").assertDoesNotExist()
        ui.onNodeWithTag("game-detail-more-$athleteId").assertDoesNotExist()
        ui.onNodeWithText("Faltou").assertDoesNotExist()
        assertFalse(api("athlete", "$gamePath/attendance/roster").toString().contains("\"noShow\":true"))
        api("athlete", "/api/groups/$group/games/past", status = 403)
        // Jogo e central cobrem as abas.
        back()
        back()
        logout()

        login("owner")
        openPastGames()
        click("past-games-game-$game")
        waitTag("game-detail")
        click("game-tab-no-show", scroll = true)
        click("game-detail-more-$athleteId", scroll = true)
        click("game-no-show-undo-$athleteId")
        ui.waitUntil(20_000) { ui.onAllNodesWithTag("game-tab-no-show").fetchSemanticsNodes().isEmpty() }
        waitTag("game-detail-row-$athleteId")
        assertFalse(rosterEntry().getBoolean("noShow"))
        // Desfazer a falta não ressuscita a diária dispensada.
        assertEquals("WAIVED", api("owner", chargesPath).getJSONArray("charges").getJSONObject(0).getString("status"))
    }

    private fun openPastGames() {
        openGroup()
        click("group-details-agenda-past", scroll = true)
        waitTag("past-games-list")
        waitTag("past-games-game-$game")
    }

    private fun assertPending(pending: Boolean) {
        val row = hasAnyAncestor(hasTestTag("past-games-game-$game")) or hasTestTag("past-games-game-$game")
        val matcher = hasText("Acerto pendente", substring = true) and row
        if (pending) {
            ui.waitUntil(20_000) { ui.onAllNodes(matcher, true).fetchSemanticsNodes().isNotEmpty() }
        } else {
            ui.waitUntil(20_000) { ui.onAllNodes(matcher, true).fetchSemanticsNodes().isEmpty() }
        }
    }

    private fun markNoShow() {
        click("game-detail-more-$athleteId", scroll = true)
        waitTag("game-detail-person-sheet")
        click("game-no-show-mark-$athleteId")
        waitTag("game-tab-no-show")
    }

    private fun openFromNotification() {
        tab("Perfil")
        click("own-profile-notifications", scroll = true)
        val tag = "notification-${data.getLong("athleteNotification")}"
        waitTag(tag)
        ui.onNode(hasText("Abrir") and hasClickAction() and hasAnyAncestor(hasTestTag(tag)))
            .performScrollTo().performClick()
        waitTag("game-detail")
    }

    private fun rosterEntry(): JSONObject {
        val confirmed = api("owner", "$gamePath/attendance/roster").getJSONArray("confirmed")
        assertEquals(1, confirmed.length())
        return confirmed.getJSONObject(0).also { assertEquals(athleteId, it.getString("memberId")) }
    }

    private fun pastGame(): JSONObject {
        val games = api("owner", "/api/groups/$group/games/past").getJSONArray("games")
        assertEquals(1, games.length())
        return games.getJSONObject(0).also { assertEquals(game, it.getString("id")) }
    }
}
