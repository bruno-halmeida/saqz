package br.com.saqz.androidapp

import android.app.Application
import android.app.NotificationManager
import com.google.firebase.messaging.RemoteMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
