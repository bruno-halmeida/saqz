package br.com.saqz.bootstrap

import br.com.saqz.bootstrap.configuration.data
import br.com.saqz.groups.application.communication.NotificationPush
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** O fim da janela das 24 h viaja no `data` do push para o app expirar a notificação (VUL-263). */
class NotificationPushPayloadTest {
    private val push = NotificationPush(
        7, UUID.fromString("00000000-0000-0000-0000-000000000001"), "Saqz", "Corpo", "ATTENDANCE_WINDOW", "subject",
        gameId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
    )

    @Test fun `the window end travels as epoch millis`() {
        val end = Instant.parse("2026-09-26T22:00:00Z")

        assertEquals(end.toEpochMilli().toString(), push.copy(windowEndsAt = end).data()["windowEndsAt"])
    }

    @Test fun `pushes without a window keep the payload they had`() {
        assertFalse("windowEndsAt" in push.data())
    }
}
