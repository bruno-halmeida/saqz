package br.com.saqz.subscriptions.domain

import java.time.Duration
import java.time.Instant
import java.util.UUID

data class OrganizerTrial(val ownerUserId: UUID, val startedAt: Instant, val endsAt: Instant) {
    fun isActiveAt(now: Instant): Boolean = now < endsAt

    companion object {
        val plan: Plan = Plan.ORGANIZADOR

        fun start(ownerUserId: UUID, now: Instant, days: Int = 14): OrganizerTrial {
            require(days in 1..365)
            return OrganizerTrial(ownerUserId, now, now.plus(Duration.ofDays(days.toLong())))
        }
    }
}
