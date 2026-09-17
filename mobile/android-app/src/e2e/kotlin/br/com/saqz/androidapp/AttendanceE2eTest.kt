package br.com.saqz.androidapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** APP-R08: real third confirmation and UI withdrawal with authoritative FIFO promotion. */
@RunWith(AndroidJUnit4::class)
internal class AttendanceE2eTest : InstalledE2e("attendance") {
    @Test
    fun fullGameWaitlistsAthleteAndWithdrawalPromotesThemWithoutOverbooking() {
        val attendancePath = "/api/groups/$group/games/${data.getString("game")}/attendance"
        login("athlete")
        openGroup()
        click("group-game-response-going", scroll = true)
        // Em espera os dois botões somem: ficam o chip com a posição e "Sair da lista de espera".
        waitText("Lista de espera · 1º")
        waitEnabled("home-reserva-leave")
        ui.onNodeWithText("Lista de espera · 1º").performScrollTo().assertIsDisplayed()
        val waiting = api("athlete", attendancePath)
        assertEquals(2, waiting.getInt("confirmedCount"))
        assertEquals(0, waiting.getInt("availableSpots"))
        assertEquals(1, waiting.getInt("waitlistCount"))
        assertEquals("WAITLISTED", waiting.getJSONObject("ownAttendance").getString("status"))
        assertEquals(1L, waiting.getJSONObject("ownAttendance").getLong("waitlistPosition"))
        back()
        logout()

        login("owner")
        openGroup()
        // O fixture entrega o dono CONFIRMADO: o hero mostra o painel, e os botões só voltam em "Alterar".
        click("group-game-response-change", scroll = true)
        click("group-game-response-not-going", scroll = true)
        waitText("Você não vai jogar.")
        waitEnabled("group-game-response-change")
        ui.onNodeWithText("Você não vai jogar.").performScrollTo().assertIsDisplayed()
        assertEquals("DECLINED", api("owner", attendancePath).getJSONObject("ownAttendance").getString("status"))
        val promoted = api("athlete", attendancePath)
        assertEquals("CONFIRMED", promoted.getJSONObject("ownAttendance").getString("status"))
        assertEquals(2, promoted.getInt("confirmedCount"))
        assertEquals(0, promoted.getInt("waitlistCount"))
        assertEquals(0, promoted.getInt("availableSpots"))
        val roster = api("owner", "$attendancePath/roster")
        val confirmed = roster.getJSONArray("confirmed")
        assertEquals(
            setOf(actor("athlete").getString("id"), actor("other").getString("id")),
            (0 until confirmed.length()).map { confirmed.getJSONObject(it).getString("memberId") }.toSet(),
        )
        assertEquals(2, confirmed.length())
        assertEquals(0, roster.getJSONArray("waitlisted").length())
        back()
        logout()

        login("athlete")
        openGroup()
        waitText("Sua presença está confirmada.")
        ui.onNodeWithText("Sua presença está confirmada.").performScrollTo().assertIsDisplayed()
    }
}
