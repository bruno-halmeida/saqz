package br.com.saqz.bootstrap

import br.com.saqz.bootstrap.configuration.ATTENDANCE_CATEGORY
import br.com.saqz.bootstrap.configuration.ATTENDANCE_POSITIONS_CATEGORY
import br.com.saqz.bootstrap.configuration.attendanceCategory
import br.com.saqz.bootstrap.configuration.data
import br.com.saqz.bootstrap.configuration.liveActivityAps
import br.com.saqz.bootstrap.configuration.offersAttendanceActions
import br.com.saqz.groups.application.communication.LiveActivityGame
import br.com.saqz.groups.application.communication.NotificationPush
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** O fim da janela das 24 h viaja no `data` do push para o app expirar a notificação (VUL-263). */
class NotificationPushPayloadTest {
    private val push = NotificationPush(
        7, UUID.fromString("00000000-0000-0000-0000-000000000001"), "Saqz", "Corpo", "ATTENDANCE_WINDOW", "subject",
        gameId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
    )

    @Test fun `payment kind travels only when the notice has one`() {
        assertEquals("REVIEW", push.copy(channel = "PAYMENT", paymentKind = "REVIEW").data()["paymentKind"])
        assertFalse("paymentKind" in push.data())
    }

    @Test fun `the window end travels as epoch millis`() {
        val end = Instant.parse("2026-09-26T22:00:00Z")

        assertEquals(end.toEpochMilli().toString(), push.copy(windowEndsAt = end).data()["windowEndsAt"])
    }

    @Test fun `first confirmation notice opens the game without attendance actions`() {
        assertTrue(push.offersAttendanceActions())
        assertFalse(push.copy(channel = "FIRST_CONFIRMATION").offersAttendanceActions())
        assertFalse(push.copy(channel = "FIRST_MEMBER", gameId = null).offersAttendanceActions())
    }

    @Test fun `cancelled game notice leaves without attendance actions and without the game`() {
        val cancelled = push.copy(channel = "NOTICE")
        assertFalse(cancelled.offersAttendanceActions())
        assertFalse("gameId" in cancelled.data())
        assertEquals(setOf("notificationId", "groupId", "channel", "recipient", "title", "body"), cancelled.data().keys)
    }

    @Test fun `DEB-107 positions go only in attendance pushes of a game with positions`() {
        assertFalse("positions" in push.data())
        assertEquals(ATTENDANCE_CATEGORY, push.attendanceCategory())
        val fiveOne = push.copy(positions = true)
        assertEquals("true", fiveOne.data()["positions"])
        assertEquals(ATTENDANCE_POSITIONS_CATEGORY, fiveOne.attendanceCategory())
        // Sem botões de presença (primeira confirmação, aviso de cancelamento), o campo não vai.
        assertFalse("positions" in fiveOne.copy(channel = "FIRST_CONFIRMATION").data())
        assertFalse("positions" in fiveOne.copy(channel = "NOTICE").data())
    }

    @Test fun `other pushes with a game keep the game in the data`() {
        assertEquals(push.gameId.toString(), push.data()["gameId"])
        assertEquals(push.gameId.toString(), push.copy(channel = "FIRST_CONFIRMATION").data()["gameId"])
    }

    @Test fun `pushes without a window keep the payload they had`() {
        assertFalse("windowEndsAt" in push.data())
    }

    @Test fun `the live activity start carries the SaqzGameAttributes contract in unix seconds`() {
        val now = Instant.parse("2026-09-25T22:00:00Z")
        val startsAt = Instant.parse("2026-09-26T22:00:00Z")
        val end = Instant.parse("2026-09-26T00:00:00Z")
        val aps = push.copy(windowEndsAt = end, liveActivity = LiveActivityGame("Arena", startsAt, 9, 12, 2)).liveActivityAps(now)

        assertEquals(now.epochSecond, aps["timestamp"])
        assertEquals("start", aps["event"])
        assertEquals("SaqzGameAttributes", aps["attributes-type"])
        assertEquals(
            mapOf(
                "groupId" to "00000000-0000-0000-0000-000000000001", "gameId" to "00000000-0000-0000-0000-000000000002",
                "recipient" to "subject", "venue" to "Arena", "startsAt" to startsAt.epochSecond,
            ),
            aps["attributes"],
        )
        assertEquals(mapOf("confirmed" to 9, "capacity" to 12, "waitlisted" to 2, "status" to "PENDING"), aps["content-state"])
        assertEquals(end.epochSecond, aps["stale-date"])
        assertEquals(mapOf("title" to "Saqz", "body" to "Corpo", "sound" to "default"), aps["alert"])
    }

    @Test fun `DEB-113 the live activity of a game with positions carries the optional positions attribute`() {
        val window = push.copy(
            windowEndsAt = Instant.parse("2026-09-26T00:00:00Z"),
            liveActivity = LiveActivityGame("Arena", Instant.parse("2026-09-26T22:00:00Z"), 9, 12, 2),
        )
        val now = Instant.parse("2026-09-25T22:00:00Z")
        @Suppress("UNCHECKED_CAST")
        val plain = window.liveActivityAps(now)["attributes"] as Map<String, Any>
        assertFalse("positions" in plain)
        @Suppress("UNCHECKED_CAST")
        val fiveOne = window.copy(positions = true).liveActivityAps(now)["attributes"] as Map<String, Any>
        assertEquals(true, fiveOne["positions"])
        // O push da janela de jogo 5-1 também leva positions (Android abre a tela) e o fim da janela.
        val data = window.copy(positions = true).data()
        assertEquals("true", data["positions"])
        assertEquals(Instant.parse("2026-09-26T00:00:00Z").toEpochMilli().toString(), data["windowEndsAt"])
    }
}

class NotificationPushClassificationTest {
    @org.junit.jupiter.api.Test
    fun `token morto ou de outro projeto e descartado e o resto tenta de novo`() {
        val tokenMorto = listOf(
            com.google.firebase.messaging.MessagingErrorCode.UNREGISTERED,
            com.google.firebase.messaging.MessagingErrorCode.SENDER_ID_MISMATCH,
        )
        tokenMorto.forEach {
            kotlin.test.assertEquals(br.com.saqz.groups.application.communication.PushDelivery.INVALID_TOKEN,
                br.com.saqz.bootstrap.configuration.classify(it, false))
            kotlin.test.assertEquals(br.com.saqz.groups.application.communication.PushDelivery.INVALID_START_TOKEN,
                br.com.saqz.bootstrap.configuration.classify(it, true))
        }
        (com.google.firebase.messaging.MessagingErrorCode.values().toList<com.google.firebase.messaging.MessagingErrorCode?>() + null)
            .filterNot { it in tokenMorto }.forEach {
                kotlin.test.assertEquals(br.com.saqz.groups.application.communication.PushDelivery.RETRY,
                    br.com.saqz.bootstrap.configuration.classify(it, false), "$it")
            }
    }
}
