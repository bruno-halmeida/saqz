package br.com.saqz.androidapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** VUL-239: decisões A–F do CONTRATO — convidado de jogo pela UI, cascata e cobrança. */
@RunWith(AndroidJUnit4::class)
internal class GameGuestE2eTest : InstalledE2e("attendance-guest") {
    @Test
    fun hostBringsAGuestOrganizerRemovesItAndHostDeclineDropsTheRest() {
        val attendancePath = "/api/groups/$group/games/${data.getString("game")}/attendance"
        val host = actor("athlete").getString("id")

        // 1) anfitrião na fila leva "Rafa Moreira" pela UI → fila: anfitrião 1º, convidado 2º
        login("athlete")
        openGroup()
        click("group-details-view-game", scroll = true)
        click("game-guest-add", scroll = true)
        input("game-guest-name", "Rafa Moreira")
        click("game-guest-submit")
        waitText("Rafa Moreira entrou na lista de espera.")
        waitTag("game-guest-row-$host#1")
        ui.onNodeWithText("Convidado seu").performScrollTo().assertIsDisplayed()
        var roster = api("athlete", "$attendancePath/roster")
        var waitlisted = roster.getJSONArray("waitlisted")
        assertEquals(2, waitlisted.length())
        assertEquals(1, waitlisted.getJSONObject(1).getInt("guestSeq"))
        assertEquals("Rafa Moreira", waitlisted.getJSONObject(1).getString("displayName"))
        assertEquals(host, waitlisted.getJSONObject(1).getString("memberId"))
        assertEquals(2, api("athlete", attendancePath).getInt("waitlistCount"))
        // regra E: convidado não é "sem resposta" nem "não vai"
        assertEquals(0, api("athlete", attendancePath).getInt("declinedCount"))

        // 2) segundo convidado → guestSeq 2; sem limite
        click("game-guest-add", scroll = true)
        input("game-guest-name", "Ju Andrade")
        click("game-guest-submit")
        waitTag("game-guest-row-$host#2")
        back(); back()
        logout()

        // 3) gestor tira o 2º convidado pela UI; o 1º fica
        login("owner")
        openGroup()
        click("group-details-view-game", scroll = true)
        ui.onNodeWithText("Convidado de E2E attendance-guest-athlete").performScrollTo().assertIsDisplayed()
        click("game-guest-remove-$host#2", scroll = true)
        click("game-guest-remove-confirm")
        waitText("Ju Andrade saiu do jogo.")
        roster = api("owner", "$attendancePath/roster")
        waitlisted = roster.getJSONArray("waitlisted")
        assertEquals(2, waitlisted.length())
        // 4) gestor desiste (era CONFIRMADO) → FIFO promove o ANFITRIÃO (1º da fila), não o convidado
        back()
        click("group-game-response-change", scroll = true)
        click("group-game-response-not-going", scroll = true)
        waitText("Você não vai jogar.")
        roster = api("owner", "$attendancePath/roster")
        val confirmed = roster.getJSONArray("confirmed")
        assertEquals(2, confirmed.length())
        assertEquals(host, confirmed.getJSONObject(1).getString("memberId"))
        assertEquals(0, confirmed.getJSONObject(1).getInt("guestSeq"))
        assertEquals(1, roster.getJSONArray("waitlisted").length())
        // 5) gestor aumenta a capacidade para 3 → convidado promovido; cobrança nasce em nome do anfitrião
        val gamePath = "/api/groups/$group/games/${data.getString("game")}"
        val version = api("owner", gamePath).getLong("version")
        api("owner", "$gamePath/capacity", "PUT", JSONObject().put("requestId", UUID.randomUUID().toString()).put("capacity", 3), headers = mapOf("If-Match" to "\"$version\""))
        roster = api("owner", "$attendancePath/roster")
        assertEquals(3, roster.getJSONArray("confirmed").length())
        val charges = api("owner", "/api/groups/$group/charges").getJSONArray("charges")
        val guestCharge = (0 until charges.length()).map { charges.getJSONObject(it) }
            .single { it.optString("guestDisplayName", "") == "Rafa Moreira" }
        assertEquals(host, guestCharge.getString("memberId"))
        assertEquals("PENDING", guestCharge.getString("status"))
        assertEquals(2000L, guestCharge.getLong("amountCents"))
        logout()

        // 6) anfitrião desiste → convidado confirmado cai junto e a cobrança dele é cancelada (regras B e F)
        login("athlete")
        openGroup()
        click("group-game-response-change", scroll = true)
        click("group-game-response-not-going", scroll = true)
        waitText("Você não vai jogar.")
        roster = api("athlete", "$attendancePath/roster")
        assertEquals(1, roster.getJSONArray("confirmed").length())
        val after = api("owner", "/api/groups/$group/charges").getJSONArray("charges")
        val cancelled = (0 until after.length()).map { after.getJSONObject(it) }
            .single { it.optString("guestDisplayName", "") == "Rafa Moreira" }
        assertEquals("CANCELLED", cancelled.getString("status"))
        // 7) sem resposta própria, o botão fica desabilitado (regra A)
        click("group-details-view-game", scroll = true)
        ui.onNodeWithText("Responda “Vou” para poder levar alguém.").performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("game-guest-add").assertIsNotEnabled()
    }
}
