package br.com.saqz.androidapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** APP-R08 FIFO edge: two waiting candidates distinguish FIFO from LIFO/arbitrary promotion. */
@RunWith(AndroidJUnit4::class)
internal class AttendanceOrderE2eTest : InstalledE2e("attendance-order") {
    @Test
    fun withdrawalPromotesFirstWaitingAthleteAndKeepsSecondInQueue() {
        val path = "/api/groups/$group/games/${data.getString("game")}/attendance"
        val before = api("owner", "$path/roster").getJSONArray("waitlisted")
        assertEquals(2, before.length())
        assertEquals(actor("athlete").getString("id"), before.getJSONObject(0).getString("memberId"))
        assertEquals(actor("reserve").getString("id"), before.getJSONObject(1).getString("memberId"))

        login("owner")
        openGroup()
        click("group-game-response-not-going", scroll = true)
        waitText("Você não vai jogar.")
        waitEnabled("group-game-response-not-going")
        ui.onNodeWithText("Você não vai jogar.").performScrollTo().assertIsDisplayed()

        assertEquals("CONFIRMED", api("athlete", path).getJSONObject("ownAttendance").getString("status"))
        assertEquals("WAITLISTED", api("reserve", path).getJSONObject("ownAttendance").getString("status"))
        val after = api("owner", "$path/roster")
        val confirmed = after.getJSONArray("confirmed")
        assertEquals(2, confirmed.length())
        assertEquals(
            setOf(actor("athlete").getString("id"), actor("other").getString("id")),
            (0 until confirmed.length()).map { confirmed.getJSONObject(it).getString("memberId") }.toSet(),
        )
        assertEquals(1, after.getJSONArray("waitlisted").length())
        assertEquals(actor("reserve").getString("id"), after.getJSONArray("waitlisted").getJSONObject(0).getString("memberId"))
    }
}
