package br.com.saqz.bootstrap

import br.com.saqz.groups.adapter.input.scheduling.RemindStalledEntryRequestsJob
import org.junit.jupiter.api.Test
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.support.CronExpression
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.assertEquals

/** DEB-068: o segundo aviso de pedido parado roda uma vez por dia, às 10h de São Paulo. */
class EntryRequestReminderScheduleTest {
    private val scheduled = RemindStalledEntryRequestsJob::class.java.getMethod("run").getAnnotation(Scheduled::class.java)

    @Test fun `runs once a day at 10h Sao Paulo time`() {
        val cron = CronExpression.parse(scheduled.cron.removePrefix("\${saqz.notifications.entry-request-reminder.cron:").removeSuffix("}"))
        val zone = ZoneId.of(scheduled.zone)
        val first = cron.next(ZonedDateTime.of(2026, 10, 19, 0, 0, 0, 0, zone))!!

        assertEquals("America/Sao_Paulo", scheduled.zone)
        assertEquals("2026-10-19T10:00", first.toLocalDateTime().toString())
        assertEquals("2026-10-20T10:00", cron.next(first)!!.toLocalDateTime().toString())
    }
}
