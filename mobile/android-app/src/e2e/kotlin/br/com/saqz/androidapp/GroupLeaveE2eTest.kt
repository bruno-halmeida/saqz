package br.com.saqz.androidapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** APP-L01/L02/L03 and repeated-DELETE subset of L04; no simulated timeout claim. */
@RunWith(AndroidJUnit4::class)
internal class GroupLeaveE2eTest : InstalledE2e("leave") {
    @Test
    fun cancellationDepartureAndOwnerProtectionPersistAcrossSessions() {
        val gameId = data.getString("game")
        val gamePath = "/api/groups/$group/games/$gameId"
        val chatPath = "/api/groups/$group/messages?channel=CHAT"
        login("athlete")
        openGroup()
        assertEquals(gameId, api("athlete", gamePath).getString("id"))
        api("athlete", chatPath)

        click("group-details-leave", scroll = true)
        click("group-leave-cancel")
        ui.onNodeWithTag("group-leave-confirm").assertDoesNotExist()
        assertEquals(setOf(group, secondGroup), membershipIds("athlete"))
        assertEquals(gameId, api("athlete", gamePath).getString("id"))

        click("group-details-leave", scroll = true)
        click("group-leave-confirm")
        waitTag("group-list-group-$secondGroup")
        ui.onNodeWithTag("group-list-group-$group").assertDoesNotExist()
        ui.onNodeWithTag("group-list-group-$secondGroup").assertIsDisplayed()
        assertEquals(setOf(secondGroup), membershipIds("athlete"))
        api("athlete", gamePath, status = 404)
        api("athlete", chatPath, status = 404)
        api("athlete", "/api/groups/$group/memberships/me", "DELETE", status = 204)
        assertEquals(setOf(secondGroup), membershipIds("athlete"))

        ui.activityRule.scenario.recreate()
        groups()
        waitTag("group-list-group-$secondGroup")
        ui.onNodeWithTag("group-list-group-$group").assertDoesNotExist()
        openGroup(secondGroup)
        waitTag("group-details-shortcut-chat")
        ui.onNodeWithTag("group-details").assertIsDisplayed()
        back()
        logout()

        login("owner")
        openGroup()
        waitTag("group-game-response-going") // Positive loaded-state sentinel before absence assertion.
        ui.onNodeWithTag("group-details-leave").assertDoesNotExist()
        api("owner", "/api/groups/$group/memberships/me", "DELETE", status = 403)
        assertEquals(setOf(group, secondGroup), membershipIds("owner"))
        val historicalGame = api("owner", gamePath)
        assertEquals(gameId, historicalGame.getString("id"))
        assertEquals(group, historicalGame.getString("groupId"))
        assertEquals("PUBLISHED", historicalGame.getString("status"))
    }
}
