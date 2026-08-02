package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.profile.GetProfileStats
import br.com.saqz.groups.application.profile.ProfileStatsAggregate
import br.com.saqz.groups.application.profile.ProfileStatsRepository
import br.com.saqz.sharedkernel.RequestIdentity
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileStatsControllerTest {
    private val actor = UUID.randomUUID()
    private val now = Instant.parse("2026-08-01T10:00:00Z")
    private val identity = RequestIdentity("subject", emailVerified = true, displayName = "Player")

    @Test
    fun `returns all profile stats fields`() {
        val controller = ProfileStatsController(
            actorResolver = VerifiedGroupActorResolver { actor },
            getProfileStats = GetProfileStats(
                ProfileStatsRepository { _, _ -> ProfileStatsAggregate(42, 47, 3) },
                { now },
            ),
        )

        assertEquals(ProfileStatsResponse(42, 89, 3), controller.get(identity))
    }
}
