package br.com.saqz.sharedkernel.job

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JobLogTest {
    private val logger = LoggerFactory.getLogger("br.com.saqz.job") as Logger
    private val appender = ListAppender<ILoggingEvent>()

    @BeforeEach fun attach() {
        appender.start()
        logger.addAppender(appender)
    }

    @AfterEach fun detach() { logger.detachAppender(appender) }

    private fun lines() = appender.list.map { it.formattedMessage }

    @Test fun `cron job logs a run line even when nothing was processed`() {
        JobLog.cron("attendance_reminder", { 0 }) { mapOf("candidates" to it, "published" to it, "failed" to 0) }

        val line = lines().single()
        assertTrue(Regex("^job_run job=attendance_reminder candidates=0 published=0 failed=0 durationMs=\\d+$").matches(line), line)
    }

    @Test fun `frequent job stays silent with zero and logs the summary when it processed something`() {
        JobLog.frequent("notification_push", { 0 }) { mapOf("processed" to it) }
        assertEquals(emptyList(), lines())

        JobLog.frequent("notification_push", { 3 }) { mapOf("processed" to it, "failed" to 1) }
        assertTrue(Regex("^job_run job=notification_push processed=3 failed=1 durationMs=\\d+$").matches(lines().single()), lines().toString())
    }

    @Test fun `item lines carry only the ids that exist`() {
        val group = UUID.randomUUID()
        val message = UUID.randomUUID()

        JobLog.item("game_open", "game_open_published", "groupId" to group, "gameId" to null, "messageId" to message)

        assertEquals("job_item job=game_open event=game_open_published groupId=$group messageId=$message", lines().single())
        assertEquals(Level.INFO, appender.list.single().level)
    }

    @Test fun `failed item is a warning with the error class and never the message`() {
        val group = UUID.randomUUID()

        JobLog.itemFailed("notification_whatsapp", "whatsapp_failed", IllegalStateException("telefone +5511999999999"), "groupId" to group)

        assertEquals("job_item_failed job=notification_whatsapp event=whatsapp_failed groupId=$group error=IllegalStateException", lines().single())
        assertEquals(Level.WARN, appender.list.single().level)
    }

    @Test fun `a job that throws logs the failure with its class and rethrows`() {
        assertFailsWith<IllegalStateException> {
            JobLog.cron("monthly_charges", { error("segredo") }) { _: Nothing -> emptyMap() }
        }

        val line = lines().single()
        assertTrue(Regex("^job_run_failed job=monthly_charges durationMs=\\d+ error=IllegalStateException$").matches(line), line)
        assertEquals(Level.WARN, appender.list.single().level)
    }
}
