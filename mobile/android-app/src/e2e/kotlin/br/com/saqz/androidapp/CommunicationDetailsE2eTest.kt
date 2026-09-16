package br.com.saqz.androidapp

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** APP-N01/N03 subsets: persisted preferences, delivery, destinations and recipient isolation. */
@RunWith(AndroidJUnit4::class)
internal class NotificationsE2eTest : InstalledE2e("notification-settings") {
    @Test
    fun preferencesSurviveReopeningAndOnlyMyNotificationReadNavigatesToItsDestination() {
        login("athlete") // PAR in APP-N01/N03; peer is the independent recipient and other is FORA.
        val original = inbox("athlete")
        val peerBefore = inbox("peer").toString()
        assertEquals(setOf(data.getString("oldChat"), data.getString("oldNotice"), data.getString("reminder")),
            original.objects().map { it.getJSONObject("message").getString("id") }.toSet())
        assertTrue(original.objects().all { !it.getBoolean("read") })
        openSettings()
        ui.onNodeWithTag("preferences-messages").assertIsOn()
        click("preferences-messages", scroll = true)
        ui.onNodeWithTag("preferences-messages").assertIsOff()
        ui.onNodeWithText("Salvar preferências").performScrollTo().assertIsEnabled().performClick()
        waitText("Preferências salvas.")
        assertPreferences()
        back()
        // A fresh settings route forces a remote read after Activity recreation; no process-death claim.
        ui.activityRule.scenario.recreate()
        openSettings()
        ui.onNodeWithTag("preferences-messages").assertIsOff()
        ui.onNodeWithTag("preferences-notices").assertIsOn()
        ui.onNodeWithTag("preferences-reminders").assertIsOn()
        assertPreferences()
        back()
        logout()

        login("owner")
        openGroup()
        click("group-details-shortcut-chat", scroll = true)
        val chat = "Chat após desativar notificações QA"
        send(chat)
        back()
        click("group-details-shortcut-notices", scroll = true)
        val notice = "Aviso após salvar preferências QA"
        send(notice)
        val noticeId = api("owner", "/api/groups/$group/messages?channel=NOTICE").getJSONArray("items")
            .objects().single { it.getString("body") == notice }.getString("id")
        back()
        back()
        logout()

        login("athlete")
        val delivered = inbox("athlete")
        assertEquals(4, delivered.length())
        assertEquals(original.objects().map { it.getJSONObject("message").getString("id") }.toSet() + noticeId,
            delivered.objects().map { it.getJSONObject("message").getString("id") }.toSet())
        assertTrue(delivered.objects().none { it.getJSONObject("message").getString("body") == chat })
        val peerAfterPublications = inbox("peer")
        assertEquals(5, peerAfterPublications.length())
        assertTrue(peerAfterPublications.objects().all { !it.getBoolean("read") })
        // The original peer records remain unchanged when the new publications are excluded.
        assertEquals(peerBefore, JSONArray(peerAfterPublications.objects().drop(2)).toString())
        openGroup()
        click("group-details-shortcut-chat", scroll = true)
        waitText(chat)
        ui.onNodeWithText(chat).assertIsDisplayed()
        back()
        back()
        tab("Perfil")
        click("own-profile-notifications", scroll = true)
        waitTag("notification-center")
        showNotification(noticeId)
        ui.onNodeWithText(notice).assertIsDisplayed()

        val oldNotice = notification(data.getString("oldNotice"))
        assertEquals(0, inbox("other").length())
        // The API deliberately returns an opaque 204 for a foreign sequence; verify no state change.
        api("other", "/api/me/notifications/${oldNotice.getLong("sequence")}/read", "PUT", status = 204)
        assertFalse(notification(data.getString("oldNotice")).getBoolean("read"))
        assertEquals(0, inbox("other").length())
        val beforeRead = inbox("athlete").objects().associate { it.getLong("sequence") to it.getBoolean("read") }
        openNotification(data.getString("oldNotice"))
        waitText("Avisos do grupo")
        waitText("Somente administradores publicam avisos.")
        waitText(notice) // Unique G1 content proves the destination group as well as the channel.
        ui.onNodeWithTag("message-draft").assertDoesNotExist()
        assertOnlyReadChanged(beforeRead, oldNotice.getLong("sequence"))
        back()
        showNotification(data.getString("oldNotice"))
        assertReadInUi(oldNotice.getLong("sequence"))

        val reminder = notification(data.getString("reminder"))
        val beforeReminder = inbox("athlete").objects().associate { it.getLong("sequence") to it.getBoolean("read") }
        openNotification(data.getString("reminder"))
        waitTag("game-detail")
        waitText(data.getString("gameVenue"))
        ui.onNodeWithText(data.getString("gameVenue")).performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("group-thread").assertDoesNotExist()
        assertEquals(group, reminder.getJSONObject("message").getString("groupId"))
        assertEquals(data.getString("game"), reminder.getJSONObject("message").getString("gameId"))
        assertOnlyReadChanged(beforeReminder, reminder.getLong("sequence"))
        assertEquals(peerAfterPublications.toString(), inbox("peer").toString())
        back()
        back()
        click("own-profile-notifications", scroll = true)
        showNotification(data.getString("reminder"))
        assertReadInUi(reminder.getLong("sequence"))
    }

    private fun openSettings() {
        tab("Perfil")
        click("own-profile-settings")
        waitTag("preferences-messages")
    }

    private fun assertPreferences() {
        val preferences = api("athlete", "/api/me/notification-preferences")
        assertFalse(preferences.getBoolean("messages"))
        assertTrue(preferences.getBoolean("notices"))
        assertTrue(preferences.getBoolean("reminders"))
        assertTrue(api("peer", "/api/me/notification-preferences").getBoolean("messages"))
    }

    private fun send(body: String) {
        input("message-draft", body, scroll = false)
        click("message-send")
        waitText(body)
    }

    private fun inbox(role: String) = api(role, "/api/me/notifications").getJSONArray("items")
    private fun notification(id: String) = inbox("athlete").objects().single { it.getJSONObject("message").getString("id") == id }

    private fun showNotification(id: String): Long {
        val sequence = notification(id).getLong("sequence")
        ui.waitUntil(20_000) { ui.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().size == 1 }
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("notification-$sequence"))
        ui.onNodeWithTag("notification-$sequence").assertIsDisplayed()
        return sequence
    }

    private fun openNotification(id: String) {
        val sequence = showNotification(id)
        ui.onNode(hasText("Abrir") and hasAnyAncestor(hasTestTag("notification-$sequence")))
            .performScrollTo().assertIsEnabled().performClick()
    }

    private fun assertReadInUi(sequence: Long) {
        ui.onNode(hasText("Não lida") and hasAnyAncestor(hasTestTag("notification-$sequence"))).assertDoesNotExist()
    }

    private fun assertOnlyReadChanged(before: Map<Long, Boolean>, sequence: Long) {
        val after = inbox("athlete").objects().associate { it.getLong("sequence") to it.getBoolean("read") }
        assertEquals(before + (sequence to true), after)
    }
}

/** APP-C04: real 50+3 cursor pages, every rendered row, ordering and input validation. */
@RunWith(AndroidJUnit4::class)
internal class MessagePaginationE2eTest : InstalledE2e("message-pagination") {
    @Test
    fun fiftyThreeMessagesLoadInDescendingPagesWithoutDuplicatesAndInvalidInputCannotPersist() {
        val expected = data.getJSONArray("messages").objects()
        assertEquals(53, expected.size)
        login("athlete")
        openGroup()
        click("group-details-shortcut-chat", scroll = true)
        waitTag("message-send")
        val first = api("athlete", messagePath())
        assertPage(expected.take(50), first)
        assertEquals(expected[49].getLong("sequence"), first.getLong("nextCursor"))
        assertRenderedRows(expected.take(50))
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Carregar anteriores"))
        ui.onNodeWithText("Carregar anteriores").assertIsDisplayed().assertIsEnabled().performClick()
        ui.waitUntil(20_000) { ui.onAllNodesWithText("Carregar anteriores").fetchSemanticsNodes().isEmpty() }
        val remaining = api("athlete", "${messagePath()}&before=${first.getLong("nextCursor")}")
        assertPage(expected.drop(50), remaining)
        assertTrue(remaining.isNull("nextCursor"))
        assertEquals(53, (first.getJSONArray("items").objects() + remaining.getJSONArray("items").objects())
            .map { it.getString("id") }.toSet().size)
        assertRenderedRows(expected)
        ui.onNodeWithText("Carregar anteriores").assertDoesNotExist()
        ui.onNodeWithTag("message-send").assertIsNotEnabled()
        input("message-draft", "   ", scroll = false)
        ui.onNodeWithTag("message-send").assertIsNotEnabled()
        input("message-draft", "x".repeat(2001), scroll = false)
        ui.onNode(hasSetTextAction() and (hasTestTag("message-draft") or hasAnyAncestor(hasTestTag("message-draft"))), useUnmergedTree = true)
            .assertTextEquals("x".repeat(2000))
        input("message-draft", "", scroll = false)
        // Text replacement focuses the real editor and opens the IME; restore the viewport before refresh.
        ui.activityRule.scenario.onActivity { activity ->
            val keyboard = requireNotNull(activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java))
            keyboard.hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
        }
        ui.waitForIdle()
        ui.onNodeWithTag("message-send").assertIsNotEnabled()
        for (invalid in listOf("", "   ", "x".repeat(2001))) {
            api("athlete", messagePath(), "POST", JSONObject().put("requestId", UUID.randomUUID().toString())
                .put("body", invalid), status = 422)
        }
        ui.waitUntil(20_000) { ui.onNodeWithText("Atualizar").isDisplayed() }
        ui.onNodeWithText("Atualizar").assertIsDisplayed().assertIsEnabled().performClick()
        waitTag("message-send")
        assertPage(expected.take(50), api("athlete", messagePath()))
        assertPage(expected.drop(50), api("athlete", "${messagePath()}&before=${first.getLong("nextCursor")}"))
        assertRenderedRows(expected.take(50))
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Carregar anteriores"))
        ui.onNodeWithText("Carregar anteriores").assertIsDisplayed()
        assertEquals(0, api("athlete", "/api/groups/$secondGroup/messages?channel=CHAT").getJSONArray("items").length())
    }

    private fun messagePath() = "/api/groups/$group/messages?channel=CHAT"

    private fun assertPage(expected: List<JSONObject>, actual: JSONObject) {
        val items = actual.getJSONArray("items").objects()
        assertEquals(expected.map { it.getString("id") }, items.map { it.getString("id") })
        assertEquals(expected.map { it.getString("body") }, items.map { it.getString("body") })
        assertEquals(expected.map { it.getLong("sequence") }, items.map { it.getLong("sequence") })
        assertTrue(items.all { it.getString("groupId") == group && it.getString("channel") == "CHAT"
            && it.getString("authorId") == actor("owner").getString("id") })
        assertTrue(items.zipWithNext().all { (newer, older) -> newer.getLong("sequence") > older.getLong("sequence") })
    }

    private fun assertRenderedRows(expected: List<JSONObject>) {
        val tags = expected.map { "message-${it.getString("id")}" }
        val seen = mutableSetOf<String>()
        val messageCard = SemanticsMatcher("message UUID card") {
            it.config.getOrElse(SemanticsProperties.TestTag) { "" }.matches(Regex("message-[0-9a-f-]{36}"))
        }
        // Scroll by item index so every row is visited, including rows outside LazyColumn composition.
        expected.forEachIndexed { index, message ->
            ui.onNode(hasScrollToIndexAction()).performScrollToIndex(index)
            ui.onNodeWithTag(tags[index]).assertIsDisplayed()
            ui.onNode(hasText(message.getString("body")) and hasAnyAncestor(hasTestTag(tags[index])))
                .assertIsDisplayed()
            val visible = ui.onAllNodes(messageCard).fetchSemanticsNodes()
                .filter { it.boundsInRoot.height > 0f }.sortedBy { it.boundsInRoot.top }
                .map { it.config[SemanticsProperties.TestTag] }
            assertEquals("No repeated card in viewport $index", visible.size, visible.toSet().size)
            assertTrue("Only loaded page rows may be composed", visible.all { it in tags })
            assertEquals("Descending visual order in viewport $index", visible.map(tags::indexOf).sorted(), visible.map(tags::indexOf))
            seen += visible
        }
        assertEquals(tags.toSet(), seen)
    }
}

/** APP-C05 subset: actual UI reminder, exact recipients, permissions, and unchanged attendance/finance. */
@RunWith(AndroidJUnit4::class)
internal class ReminderE2eTest : InstalledE2e("reminders") {
    @Test
    fun reminderReachesOnlyActiveUnansweredPeerAndPreservesAttendanceAndCharges() {
        login("owner")
        val game = data.getString("game")
        val attendancePath = "/api/groups/$group/games/$game/attendance"
        val before = snapshot(attendancePath)
        val charges = api("owner", "/api/groups/$group/charges").getJSONArray("charges").objects()
        assertTrue(charges.any { it.getString("gameId") == game && it.getString("memberId") == actor("admin").getString("id")
            && it.getString("kind") == "GAME" && it.getString("status") == "PENDING" && it.getLong("amountCents") == 2000L })
        val roster = api("owner", "$attendancePath/roster")
        assertEquals(setOf(actor("athlete").getString("id"), actor("admin").getString("id")),
            roster.getJSONArray("confirmed").objects().map { it.getString("memberId") }.toSet())
        assertEquals("DECLINED", api("declined", attendancePath).getJSONObject("ownAttendance").getString("status"))
        assertTrue(api("peer", attendancePath).isNull("ownAttendance"))
        assertTrue(api("owner", attendancePath).isNull("ownAttendance"))
        assertNoNotifications()
        openGroup()
        click("group-details-notify-pending", scroll = true)
        waitText("Lembrete enviado no Saqz para 1 pessoa(s).")
        ui.onNodeWithText("Lembrete enviado no Saqz para 1 pessoa(s).").performScrollTo().assertIsDisplayed()
        val peerInbox = api("peer", "/api/me/notifications").getJSONArray("items")
        assertEquals(1, peerInbox.length())
        val notification = peerInbox.getJSONObject(0)
        assertFalse(notification.getBoolean("read"))
        val message = notification.getJSONObject("message")
        assertEquals(group, message.getString("groupId"))
        assertEquals(game, message.getString("gameId"))
        assertEquals("REMINDER", message.getString("channel"))
        assertEquals(actor("owner").getString("id"), message.getString("authorId"))
        assertEquals(1, message.getInt("recipientCount"))
        assertEquals(
            "*${data.getString("gameTitle")}*\n\n" +
                "✅ Confirmados:\nE2E reminders-admin, E2E reminders-athlete\n\n" +
                "❌ Fora:\nE2E reminders-declined\n\n" +
                "⏳ A confirmar:\nE2E reminders-owner, E2E reminders-peer",
            message.getString("body"),
        )
        assertNoNotifications(exceptPeer = true)
        assertEquals(before, snapshot(attendancePath))
        api("athlete", "/api/groups/$group/games/$game/notify-pending", "POST", requestId(), status = 403)
        for (field in listOf("completedGame", "expiredGame", "foreignGame")) {
            api("owner", "/api/groups/$group/games/${data.getString(field)}/notify-pending", "POST", requestId(), status = 422)
        }
        assertEquals(peerInbox.toString(), api("peer", "/api/me/notifications").getJSONArray("items").toString())
        assertNoNotifications(exceptPeer = true)
        assertEquals(before, snapshot(attendancePath))
        back()
        logout()
        login("peer")
        tab("Perfil")
        click("own-profile-notifications", scroll = true)
        waitTag("notification-${notification.getLong("sequence")}")
        ui.onNodeWithText(message.getString("body")).assertIsDisplayed()
    }

    private fun requestId() = JSONObject().put("requestId", UUID.randomUUID().toString())

    private fun assertNoNotifications(exceptPeer: Boolean = false) {
        val roles = listOf("owner", "athlete", "admin", "declined", "inactive", "other") + if (exceptPeer) emptyList() else listOf("peer")
        roles.forEach { role -> assertEquals("Inbox $role", 0, api(role, "/api/me/notifications").getJSONArray("items").length()) }
    }

    private fun snapshot(attendancePath: String): Map<String, String> {
        val attendance = listOf("owner", "athlete", "admin", "peer", "declined").associateWith { api(it, attendancePath).toString() }
        return attendance + mapOf(
            "roster" to api("owner", "$attendancePath/roster").toString(),
            "charges" to api("owner", "/api/groups/$group/charges").toString(),
            "statement" to api("owner", "/api/groups/$group/finance/statement").toString(),
        )
    }
}

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)
