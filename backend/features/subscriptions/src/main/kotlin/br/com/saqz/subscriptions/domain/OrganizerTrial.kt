package br.com.saqz.subscriptions.domain

import java.time.Duration
import java.time.Instant
import java.util.UUID

data class OrganizerTrial(val ownerUserId: UUID, val startedAt: Instant, val endsAt: Instant) {
    fun isActiveAt(now: Instant): Boolean = now < endsAt

    companion object {
        fun start(ownerUserId: UUID, now: Instant) =
            OrganizerTrial(ownerUserId, now, now.plus(Duration.ofDays(14)))
    }
}
