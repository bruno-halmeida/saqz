package br.com.saqz.androidapp

import android.app.Application
import android.app.Notification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Resultado do toque na janela das 24 h e no push comum (VUL-265). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AttendanceNotificationsTest {
    private val context = RuntimeEnvironment.getApplication()
    private val window = AttendancePush(1, "g1", "game1", "Saqz", "Jogo amanhã", "sub", System.currentTimeMillis() + 3_600_000)
    private val regular = window.copy(windowEndsAt = null)

    @Test
    fun windowResultStaysTwoMinutesWithoutButtons() {
        val result = context.attendanceOutcome(window, failed = false, text = "Presença confirmada.").build()

        assertEquals(FINAL_STATE_MS, result.timeoutAfter)
        assertNull(result.actions)
        assertEquals(0, result.flags and Notification.FLAG_ONGOING_EVENT)
    }

    @Test
    fun windowFailureGivesTheButtonsBackUntilTheWindowEnds() {
        val result = context.attendanceOutcome(window, failed = true, text = "Não deu para registrar.").build()

        assertEquals(listOf("Confirmar", "Não vou"), result.actions.map { it.title.toString() })
        assertTrue(result.timeoutAfter in 3_590_000..3_600_000)
    }

    @Test
    fun organizerFirstConfirmationOpensTheGameWithoutAttendanceButtons() {
        val organizer = regular.copy(channel = "FIRST_CONFIRMATION")

        assertNull(context.attendanceNotification(organizer).build().actions)
        assertEquals(2, context.attendanceNotification(regular).build().actions.size)
        assertEquals("FIRST_CONFIRMATION", AttendancePush.from(mapOf("channel" to "FIRST_CONFIRMATION", "gameId" to "game1"), "Saqz", "Corpo", "m").channel)
    }

    @Test
    fun gameWithPositionsOpensTheAppOnVouAndKeepsTheReceiverOnNaoVou() {
        val fiveOne = AttendancePush.from(
            mapOf("gameId" to "game1", "groupId" to "g1", "positions" to "true", "notificationId" to "9"), "Saqz", "Corpo", "m",
        )
        assertTrue(fiveOne.positions)
        val actions = context.attendanceNotification(fiveOne).build().actions
        val shadowVou = org.robolectric.Shadows.shadowOf(actions[0].actionIntent)
        assertTrue(shadowVou.isActivityIntent)
        assertEquals(MainActivity::class.java.name, shadowVou.savedIntent.component?.className)
        assertTrue(shadowVou.savedIntent.getBooleanExtra(EXTRA_PICK_POSITION, false))
        assertEquals("game1", shadowVou.savedIntent.getStringExtra(EXTRA_GAME_ID))
        val shadowNaoVou = org.robolectric.Shadows.shadowOf(actions[1].actionIntent)
        assertTrue(shadowNaoVou.isBroadcastIntent)
        assertEquals(ACTION_DECLINE, shadowNaoVou.savedIntent.action)
        // Sem posições, o "Vou" segue pelo receiver (confirma em segundo plano).
        val plain = context.attendanceNotification(regular).build().actions
        assertTrue(org.robolectric.Shadows.shadowOf(plain[0].actionIntent).isBroadcastIntent)
        assertEquals(ACTION_CONFIRM, org.robolectric.Shadows.shadowOf(plain[0].actionIntent).savedIntent.action)
        assertTrue(!AttendancePush.from(mapOf("gameId" to "game1"), "Saqz", "Corpo", "m").positions)
    }

    /** DEB-113: a janela das 24 h de jogo 5-1 é Live Update e o "Vou" dela abre o app na tela de posição. */
    @Test
    fun windowOfAGameWithPositionsOpensThePositionScreenOnVou() {
        val end = System.currentTimeMillis() + 3_600_000
        val push = AttendancePush.from(
            mapOf("gameId" to "game1", "groupId" to "g1", "positions" to "true", "windowEndsAt" to end.toString()),
            "Saqz", "Corpo", "m",
        )
        assertTrue(push.isWindow)
        val built = context.attendanceNotification(push).build()
        assertTrue(built.flags and Notification.FLAG_ONGOING_EVENT != 0)
        val vou = org.robolectric.Shadows.shadowOf(built.actions[0].actionIntent)
        assertTrue(vou.isActivityIntent)
        assertTrue(vou.savedIntent.getBooleanExtra(EXTRA_PICK_POSITION, false))
        assertEquals(ACTION_DECLINE, org.robolectric.Shadows.shadowOf(built.actions[1].actionIntent).savedIntent.action)
        // A falha de um toque devolve os mesmos botões, inclusive o que abre a tela.
        val retry = context.attendanceOutcome(push, failed = true, text = "Não deu para registrar.").build()
        assertTrue(org.robolectric.Shadows.shadowOf(retry.actions[0].actionIntent).isActivityIntent)
    }

    @Test
    fun regularPushResultKeepsTheOldBehavior() {
        val result = context.attendanceOutcome(regular, failed = true, text = "Não deu para registrar.").build()

        assertNull(result.actions)
        assertEquals(0L, result.timeoutAfter)
    }
}
