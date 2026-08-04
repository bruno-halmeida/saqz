package br.com.saqz.groups.application.home

import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertSame
import org.junit.jupiter.api.Test

class HomeTest {
    private val actor = UUID.randomUUID()
    private val now = Instant.parse("2026-08-01T02:30:00Z")
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val expected = HomeReadModel(
        member = HomeMemberReadModel(null, null, emptyList()),
        admin = null,
    )

    @Test
    fun usesCurrentInstantAndBillingMonthInConfiguredZone() {
        val repository = RecordingRepository(expected)

        val result = HomeQuery(
            repository = repository,
            clock = Clock.fixed(now, ZoneId.of("UTC")),
            zoneId = zone,
        ).execute(actor)

        assertSame(expected, result)
        assertEquals(actor, repository.actorId)
        assertEquals(now, repository.now)
        assertEquals(YearMonth.of(2026, 7), repository.currentMonth)
    }

    private class RecordingRepository(
        private val result: HomeReadModel,
    ) : HomeRepository {
        var actorId: UUID? = null
        var now: Instant? = null
        var currentMonth: YearMonth? = null

        override fun find(actorId: UUID, now: Instant, currentMonth: YearMonth): HomeReadModel {
            this.actorId = actorId
            this.now = now
            this.currentMonth = currentMonth
            return result
        }
    }
}
