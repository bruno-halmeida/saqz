package br.com.saqz.groups.application.profile

import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProfileStatsTest {
    private val userId = UUID.randomUUID()
    private val now = Instant.parse("2026-08-01T10:00:00Z")

    @Test
    fun `calculates integer attendance rate from games and eligible games`() {
        val result = GetProfileStats(
            repository = FakeProfileStatsRepository(ProfileStatsAggregate(42, 47, 3)),
            now = { now },
        ).execute(userId)

        assertEquals(ProfileStats(42, 89, 3), result)
    }

    @Test
    fun `returns null attendance rate without eligible games`() {
        val result = GetProfileStats(
            repository = FakeProfileStatsRepository(ProfileStatsAggregate(0, 0, 1)),
            now = { now },
        ).execute(userId)

        assertEquals(0, result.games)
        assertNull(result.attendanceRate)
        assertEquals(1, result.groups)
    }

    private class FakeProfileStatsRepository(
        private val aggregate: ProfileStatsAggregate,
    ) : ProfileStatsRepository {
        override fun find(userId: UUID, now: Instant): ProfileStatsAggregate = aggregate
    }
}
