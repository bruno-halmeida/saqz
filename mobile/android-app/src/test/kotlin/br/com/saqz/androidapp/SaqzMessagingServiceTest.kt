package br.com.saqz.androidapp

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.RemoteMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SaqzMessagingServiceTest {
    private val service = Robolectric.setupService(SaqzMessagingService::class.java)

    private fun receive(vararg data: Pair<String, String>) {
        val message = RemoteMessage.Builder("saqz@fcm.googleapis.com").setMessageId("m1")
            .addData("notificationId", "7").addData("groupId", "g1").addData("title", "Saqz").addData("body", "Oi")
        data.forEach { (key, value) -> message.addData(key, value) }
        service.onMessageReceived(message.build())
    }

    private fun posted() = shadowOf(service.getSystemService(NotificationManager::class.java)).getNotification("7".hashCode())

    @Test
    fun pushWithGameOffersConfirmAndDecline() {
        receive("gameId" to "game1")
        assertEquals(listOf("Confirmar", "Não vou"), posted().actions.map { it.title.toString() })
    }

    @Test
    fun pushWithoutGameHasNoActions() {
        receive()
        assertNull(posted().actions)
    }

    @Test
    fun windowPushBecomesAnOngoingPromotedNotificationThatExpiresAtTheWindowEnd() {
        receive("gameId" to "game1", "recipient" to "sub", "windowEndsAt" to (System.currentTimeMillis() + 3_600_000).toString())

        val window = shadowOf(service.getSystemService(NotificationManager::class.java)).getNotification("window:game1".hashCode())
        assertTrue(window.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(NotificationCompat.isRequestPromotedOngoing(window))
        assertTrue(window.timeoutAfter in 3_590_000..3_600_000)
        assertEquals(listOf("Confirmar", "Não vou"), window.actions.map { it.title.toString() })
    }

    @Test
    fun windowPushThatArrivesAfterTheWindowIsDropped() {
        receive("gameId" to "game1", "recipient" to "sub", "windowEndsAt" to (System.currentTimeMillis() - 1_000).toString())

        assertTrue(shadowOf(service.getSystemService(NotificationManager::class.java)).allNotifications.isEmpty())
    }

    @Test
    fun regularPushStaysDismissableAndNeverExpires() {
        receive("gameId" to "game1")

        assertEquals(0, posted().flags and Notification.FLAG_ONGOING_EVENT)
        assertEquals(0L, posted().timeoutAfter)
    }
}
