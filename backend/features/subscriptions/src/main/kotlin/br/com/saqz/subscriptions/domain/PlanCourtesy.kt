package br.com.saqz.subscriptions.domain

import java.time.Instant
import java.util.UUID

/**
 * Cortesia de plano (DEB-097): acesso dado à mão pela administração, sem passar pelas lojas. Ativa = não revogada e sem fim
 * ou com fim no futuro. Espelhada no SQL de `JdbcSubscriptionPlanLookup`, `JdbcSubscriptionPastDueRepository` e
 * `MonthlyGameCreationHorizon`.
 */
data class PlanCourtesy(
    val id: UUID,
    val ownerUserId: UUID,
    val plan: Plan,
    val endsAt: Instant?,
    val reason: String,
    val grantedBy: UUID,
    val createdAt: Instant,
    val revokedAt: Instant? = null,
    val revokedBy: UUID? = null,
    val revokedReason: String? = null,
) {
    fun isActiveAt(now: Instant): Boolean = revokedAt == null && (endsAt == null || endsAt.isAfter(now))
}
