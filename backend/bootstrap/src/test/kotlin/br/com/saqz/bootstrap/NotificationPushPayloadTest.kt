package br.com.saqz.bootstrap

import br.com.saqz.bootstrap.configuration.ATTENDANCE_CATEGORY
import br.com.saqz.bootstrap.configuration.ATTENDANCE_POSITIONS_CATEGORY
import br.com.saqz.bootstrap.configuration.attendanceCategory
import br.com.saqz.bootstrap.configuration.data
import br.com.saqz.bootstrap.configuration.offersAttendanceActions
import br.com.saqz.groups.application.communication.NotificationPush
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Dados do push que os apps leem. A janela das 24 h saiu no DEB-067: o push não leva mais `windowEndsAt`. */
class NotificationPushPayloadTest {
    private val push = NotificationPush(
        7, UUID.fromString("00000000-0000-0000-0000-000000000001"), "Saqz", "Corpo", "ATTENDANCE_WINDOW", "subject",
        gameId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
    )

    @Test fun `the payload no longer carries the window end`() {
        assertFalse("windowEndsAt" in push.data())
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
                br.com.saqz.bootstrap.configuration.classify(it))
        }
        (com.google.firebase.messaging.MessagingErrorCode.values().toList<com.google.firebase.messaging.MessagingErrorCode?>() + null)
            .filterNot { it in tokenMorto }.forEach {
                kotlin.test.assertEquals(br.com.saqz.groups.application.communication.PushDelivery.RETRY,
                    br.com.saqz.bootstrap.configuration.classify(it), "$it")
            }
    }
}
