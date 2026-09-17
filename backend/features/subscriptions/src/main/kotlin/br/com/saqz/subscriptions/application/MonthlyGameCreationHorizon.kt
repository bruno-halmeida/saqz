package br.com.saqz.subscriptions.application

import br.com.saqz.sharedkernel.subscription.GameCreationHorizon
import br.com.saqz.sharedkernel.subscription.GroupPlanOwnerLookup
import br.com.saqz.sharedkernel.subscription.OrganizerTrialAccessLookup
import br.com.saqz.sharedkernel.subscription.TrialStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/** Jogos nascem mês a mês: plano anual e trial longo andam como se fossem mensais. */
class MonthlyGameCreationHorizon(
    private val groups: GroupPlanOwnerLookup,
    private val trials: OrganizerTrialAccessLookup,
    private val subscriptions: SubscriptionRepository,
    private val clock: Clock,
) : GameCreationHorizon {
    override fun until(groupId: UUID): Instant? {
        val owner = groups.ownerOf(groupId) ?: return null
        val access = trials.forOwner(owner)
        val now = clock.instant()
        return when (access.status) {
            TrialStatus.EXPIRED -> null
            TrialStatus.SUBSCRIBED -> subscriptions.findByOwnerUserId(owner)?.currentPeriodEnd
                ?.let { monthlyMarkBefore(it, now) } ?: monthlyMarkAfter(now, now)
            TrialStatus.ACTIVE -> monthlyMarkAfter(access.startedAt ?: now, now)
            // Dono sem trial nem assinatura e que ainda pode escrever (legado): um mês corrido.
            TrialStatus.AVAILABLE, TrialStatus.INELIGIBLE -> monthlyMarkAfter(now, now)
        }
    }

    companion object {
        /** Menor `periodEnd - k meses` ainda depois de `now`; período vencido (carência) não estende nada. */
        fun monthlyMarkBefore(periodEnd: Instant, now: Instant): Instant {
            val end = periodEnd.atZone(ZoneOffset.UTC)
            var months = 0L
            while (end.minusMonths(months + 1).toInstant().isAfter(now)) months++
            return end.minusMonths(months).toInstant()
        }

        /** Menor `start + k meses` (k >= 1) depois de `now`. */
        fun monthlyMarkAfter(start: Instant, now: Instant): Instant {
            val from = start.atZone(ZoneOffset.UTC)
            var months = 1L
            while (!from.plusMonths(months).toInstant().isAfter(now)) months++
            return from.plusMonths(months).toInstant()
        }
    }
}
