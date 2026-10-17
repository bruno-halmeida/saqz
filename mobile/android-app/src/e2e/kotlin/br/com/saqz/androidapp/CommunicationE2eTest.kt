package br.com.saqz.androidapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** APP-C02 subset: real persistence, author, permission and cross-group isolation. O chat saiu do lançamento. */
@RunWith(AndroidJUnit4::class)
internal class CommunicationE2eTest : InstalledE2e("communication") {
    @Test
    fun noticesPersistForOtherAccountsWithCorrectPublishingPermissions() {
        val notice = "Jogo na quadra 2 — execução QA"
        login("owner")
        openGroup()
        click("group-details-shortcut-notices", scroll = true)
        send(notice)
        assertMessage("NOTICE", notice, "owner")
        back()
        back()
        logout()

        login("athlete")
        openGroup()
        click("group-details-shortcut-notices", scroll = true)
        waitText(notice)
        ui.onNodeWithText(notice).assertIsDisplayed()
        ui.onNodeWithTag("message-draft").assertDoesNotExist()
        ui.onNodeWithTag("message-send").assertDoesNotExist()
        api("athlete", "/api/groups/$group/messages?channel=NOTICE", "POST",
            JSONObject().put("requestId", UUID.randomUUID().toString()).put("body", "Publicação negada"), status = 403)
        assertMessage("NOTICE", notice, "owner")
        back()
        back()
        openGroup(secondGroup)
        click("group-details-shortcut-notices", scroll = true)
        waitText("Somente administradores publicam avisos.")
        ui.onNodeWithText(notice).assertDoesNotExist()
        assertEquals(0, api("athlete", "/api/groups/$secondGroup/messages?channel=NOTICE").getJSONArray("items").length())
    }

    private fun send(body: String) {
        input("message-draft", body, scroll = false)
        click("message-send")
        waitText(body)
        ui.onNodeWithText(body).assertIsDisplayed()
    }

    private fun assertMessage(channel: String, body: String, author: String) {
        val messages = api("owner", "/api/groups/$group/messages?channel=$channel").getJSONArray("items")
        assertEquals(1, messages.length())
        val message = messages.getJSONObject(0)
        assertEquals(body, message.getString("body"))
        assertEquals(group, message.getString("groupId"))
        assertEquals(channel, message.getString("channel"))
        assertEquals(actor(author).getString("id"), message.getString("authorId"))
    }
}
