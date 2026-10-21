package br.com.saqz.bootstrap

import br.com.saqz.bootstrap.configuration.AutomaticAttendanceReminder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.support.CronExpression
import java.time.ZoneId
import java.time.ZonedDateTime

/** DEB-066: o lembrete automático dispara 3 vezes por dia, às 9h, às 12h30 e às 18h de São Paulo. */
class AttendanceReminderScheduleTest {
    private val scheduled = AutomaticAttendanceReminder::class.java.getMethod("run").getAnnotation(Scheduled::class.java)
    private val zone = ZoneId.of(scheduled.zone)
    private val cron = CronExpression.parse(scheduled.cron.removePrefix("\${saqz.notifications.reminder.cron:").removeSuffix("}"))

    @Test fun `scheduler runs in the Sao Paulo time zone`() {
        assertEquals("America/Sao_Paulo", scheduled.zone)
    }

    @Test fun `fires only at 9h and 18h São Paulo time, twice a day`() {
        var next = ZonedDateTime.of(2026, 10, 19, 0, 0, 0, 0, zone)
        val fires = (1..4).map { cron.next(next)!!.also { next = it } }

        assertEquals(
            listOf("2026-10-19T09:00", "2026-10-19T18:00", "2026-10-20T09:00", "2026-10-20T18:00"),
            fires.map { it.toLocalDateTime().toString() },
        )
    }

    private val midday = AutomaticAttendanceReminder::class.java.getMethod("runMidday").getAnnotation(Scheduled::class.java)
    private val middayCron = CronExpression.parse(midday.cron.removePrefix("\${saqz.notifications.reminder.midday-cron:").removeSuffix("}"))

    @Test fun `the midday run fires only at 12h30 São Paulo time`() {
        assertEquals("America/Sao_Paulo", midday.zone)
        var next = ZonedDateTime.of(2026, 10, 19, 0, 0, 0, 0, zone)
        val fires = (1..2).map { middayCron.next(next)!!.also { next = it } }
        assertEquals(listOf("2026-10-19T12:30", "2026-10-20T12:30"), fires.map { it.toLocalDateTime().toString() })
    }

    @Test fun `9h in Sao Paulo is 12h UTC, so the zone is applied`() {
        val fire = cron.next(ZonedDateTime.of(2026, 10, 19, 0, 0, 0, 0, zone))!!
        assertEquals("2026-10-19T12:00Z", fire.withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime().toString() + "Z")
    }
}
