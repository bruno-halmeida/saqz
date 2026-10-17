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

/** APP-N01/N03 subsets: persisted preferences, delivery, destinations and recipient isolation. Sem o chat, que saiu do lançamento. */
@RunWith(AndroidJUnit4::class)
internal class NotificationsE2eTest : InstalledE2e("notification-settings") {
    @Test
    fun preferencesSurviveReopeningAndOnlyMyNotificationReadNavigatesToItsDestination() {
        login("athlete") // PAR in APP-N01/N03; peer is the independent recipient and other is FORA.
        val original = inbox("athlete")
        val peerBefore = inbox("peer")
        // Avisos automáticos do sistema (jogo liberado, primeiro membro...) podem estar na caixa além dos semeados.
        val originalIds = original.objects().map { it.getJSONObject("message").getString("id") }.toSet()
        assertTrue(originalIds.containsAll(setOf(data.getString("oldNotice"), data.getString("reminder"))))
        assertTrue(original.objects().all { !it.getBoolean("read") })
        openSettings()
        ui.onNodeWithTag("preferences-reminders").assertIsOn()
        click("preferences-reminders", scroll = true)
        ui.onNodeWithTag("preferences-reminders").assertIsOff()
        ui.onNodeWithText("Salvar preferências").performScrollTo().assertIsEnabled().performClick()
        waitText("Preferências salvas.")
        assertPreferences()
        back()
        // A fresh settings route forces a remote read after Activity recreation; no process-death claim.
        ui.activityRule.scenario.recreate()
        openSettings()
        ui.onNodeWithTag("preferences-reminders").assertIsOff()
        ui.onNodeWithTag("preferences-notices").assertIsOn()
        assertPreferences()
        back()
        logout()

        login("owner")
        openGroup()
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
        assertEquals(originalIds + noticeId, delivered.objects().map { it.getJSONObject("message").getString("id") }.toSet())
        val peerAfterPublications = inbox("peer")
        assertEquals(peerBefore.length() + 1, peerAfterPublications.length())
        assertTrue(peerAfterPublications.objects().all { !it.getBoolean("read") })
        // The original peer records remain unchanged when the new publication is excluded.
        assertEquals(peerBefore.toString(), JSONArray(peerAfterPublications.objects().drop(1)).toString())
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
        waitTag("preferences-reminders")
    }

    private fun assertPreferences() {
        val preferences = api("athlete", "/api/me/notification-preferences")
        assertFalse(preferences.getBoolean("reminders"))
        assertTrue(preferences.getBoolean("notices"))
        assertTrue(api("peer", "/api/me/notification-preferences").getBoolean("reminders"))
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
        // Avisos automáticos do sistema (jogo liberado, primeiro membro...) já podem estar nas caixas: o que o
        // lembrete garante é só o que muda por causa dele, então a conta parte de uma linha de base.
        val inboxes = inboxIds()
        openGroup()
        click("group-details-notify-pending", scroll = true)
        waitText("Lembrete enviado no Saqz para 1 pessoa.")
        ui.onNodeWithText("Lembrete enviado no Saqz para 1 pessoa.").performScrollTo().assertIsDisplayed()
        val peerInbox = api("peer", "/api/me/notifications").getJSONArray("items")
        val peerNew = peerInbox.objects().filter { it.getJSONObject("message").getString("id") !in inboxes.getValue("peer") }
        assertEquals(1, peerNew.size)
        val notification = peerNew.single()
        assertFalse(notification.getBoolean("read"))
        val message = notification.getJSONObject("message")
        assertEquals(group, message.getString("groupId"))
        assertEquals(game, message.getString("gameId"))
        assertEquals("REMINDER", message.getString("channel"))
        assertEquals(actor("owner").getString("id"), message.getString("authorId"))
        assertEquals(1, message.getInt("recipientCount"))
        val body = message.getString("body")
        assertTrue(body, body.startsWith("Jogo: "))
        assertEquals(
            "Local: Arena QA\n\n✅ Confirmados:\nE2E reminders-admin\nE2E reminders-athlete\n\n" +
                "❌ Fora:\nE2E reminders-declined",
            body.substringAfter("\n"),
        )
        assertOnlyPeerReceived(inboxes)
        assertEquals(before, snapshot(attendancePath))
        api("athlete", "/api/groups/$group/games/$game/notify-pending", "POST", requestId(), status = 403)
        for (field in listOf("completedGame", "expiredGame", "foreignGame")) {
            api("owner", "/api/groups/$group/games/${data.getString(field)}/notify-pending", "POST", requestId(), status = 422)
        }
        assertEquals(peerInbox.toString(), api("peer", "/api/me/notifications").getJSONArray("items").toString())
        assertOnlyPeerReceived(inboxes)
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

    private val inboxRoles = listOf("owner", "athlete", "admin", "declined", "inactive", "other", "peer")

    private fun inboxIds(): Map<String, Set<String>> = inboxRoles.associateWith { role ->
        api(role, "/api/me/notifications").getJSONArray("items").objects()
            .map { it.getJSONObject("message").getString("id") }.toSet()
    }

    /** Ninguém além do convocado recebe algo novo; o convocado já foi conferido à parte. */
    private fun assertOnlyPeerReceived(before: Map<String, Set<String>>) {
        val now = inboxIds()
        inboxRoles.filter { it != "peer" }.forEach { role ->
            assertEquals("Inbox $role", before.getValue(role), now.getValue(role))
        }
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
