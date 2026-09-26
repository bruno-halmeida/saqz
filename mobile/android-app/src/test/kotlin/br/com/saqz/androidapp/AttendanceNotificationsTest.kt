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
    fun regularPushResultKeepsTheOldBehavior() {
        val result = context.attendanceOutcome(regular, failed = true, text = "Não deu para registrar.").build()

        assertNull(result.actions)
        assertEquals(0L, result.timeoutAfter)
    }
}
