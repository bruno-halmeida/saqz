package br.com.saqz.subscriptions.application

import br.com.saqz.sharedkernel.subscription.GameCreationHorizon
import br.com.saqz.sharedkernel.subscription.GroupPlanOwnerLookup
import br.com.saqz.sharedkernel.subscription.OrganizerTrialAccess
import br.com.saqz.sharedkernel.subscription.OrganizerTrialAccessLookup
import br.com.saqz.sharedkernel.subscription.TrialStatus
import br.com.saqz.subscriptions.domain.AppStoreEnvironment
import br.com.saqz.subscriptions.domain.SubscriptionStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/**
 * Jogos nascem mês a mês (plano anual e trial longo andam como se fossem mensais), mas nenhum jogo
 * começa depois do fim do plano ou do teste: o limite é 0h do dia do fim, no fuso do grupo.
 */
class MonthlyGameCreationHorizon(
    private val groups: GroupPlanOwnerLookup,
    private val trials: OrganizerTrialAccessLookup,
    private val subscriptions: SubscriptionRepository,
    private val clock: Clock,
    private val appStore: AppStoreSubscriptionRepository? = null,
    private val googlePlay: GooglePlaySubscriptionRepository? = null,
) : GameCreationHorizon {
    private class Window(val until: Instant, val end: Instant)

    override fun until(groupId: UUID): Instant? = window(groupId)?.let { (window, zone) ->
        minOf(window.until, limit(window.end, zone))
    }

    override fun startsBefore(groupId: UUID): Instant? = window(groupId)?.let { (window, zone) -> limit(window.end, zone) }

    private fun window(groupId: UUID): Pair<Window, ZoneId>? {
        val owner = groups.ownerOf(groupId) ?: return null
        val access = trials.forOwner(owner)
        val now = clock.instant()
        val window = when (access.status) {
            TrialStatus.EXPIRED -> return null
            TrialStatus.ACTIVE -> trialWindow(access, now)
            TrialStatus.SUBSCRIBED -> {
                val paidEnd = paidEnd(owner, now)
                val paid = Window(monthlyMarkBefore(paidEnd, now), paidEnd)
                // Quem assina no meio de um teste longo (cupom) não perde o que o teste ainda dá.
                val trial = access.endsAt?.takeIf { it.isAfter(now) }?.let { trialWindow(access, now) }
                if (trial != null && trial.end.isAfter(paid.end)) Window(maxOf(paid.until, trial.until), trial.end) else paid
            }
            // Dono sem teste nem assinatura que ainda escreve (legado): um mês corrido. A lacuna maior é o DEB-086.
            TrialStatus.AVAILABLE, TrialStatus.INELIGIBLE -> monthlyMarkAfter(now, now).let { Window(it, it) }
        }
        return window to groups.zoneOf(groupId)
    }

    /** Teste: o jogo vai até o `endsAt`; a série de um cupom longo continua andando mês a mês. */
    private fun trialWindow(access: OrganizerTrialAccess, now: Instant): Window {
        val end = access.endsAt ?: monthlyMarkAfter(now, now)
        return Window(minOf(end, monthlyMarkAfter(access.startedAt ?: now, now)), end)
    }

    /**
     * Fim do período já pago pela assinatura que dá acesso (Asaas, App Store ou Google Play; vale o maior).
     * A carência de atraso (Asaas em PAST_DUE, graça da Apple) não estende: o fim fica no passado e nenhum
     * jogo novo passa até pagar. Renovação do Asaas ainda não confirmada (ACTIVE com período vencido, o estado normal
     * entre o vencimento e a confirmação ou o PAYMENT_OVERDUE) também usa o `currentPeriodEnd`. Só a linha legada de
     * fato (PAST_DUE sem `past_due_since`, que nunca teve data de corte) e o fim desconhecido caem no mês corrido, para não travar quem
     * o app trata como assinante em dia. Compra de teste da loja (sandbox da Apple, testador do Play) renova em
     * minutos: o `expiresAt` dela não diz nada sobre o plano, então vale o mês corrido.
     */
    private fun paidEnd(owner: UUID, now: Instant): Instant {
        val ends = mutableListOf<Instant>()
        subscriptions.findByOwnerUserId(owner)?.takeIf { it.isEntitlingAt(now) }?.let {
            val legacy = it.status == SubscriptionStatus.PAST_DUE && it.pastDueSince == null
            ends += if (legacy) monthlyMarkAfter(now, now) else it.currentPeriodEnd
        }
        appStore?.findByOwner(owner).orEmpty().filter { it.isEntitlingAt(now) }.forEach {
            ends += if (it.environment == AppStoreEnvironment.PRODUCTION) it.expiresAt else monthlyMarkAfter(now, now)
        }
        googlePlay?.findByOwner(owner).orEmpty().filter { it.isEntitlingAt(now) }.forEach {
            ends += if (it.testPurchase) monthlyMarkAfter(now, now) else it.expiresAt
        }
        return ends.maxOrNull() ?: monthlyMarkAfter(now, now)
    }

    companion object {
        /** 0h do dia do [end] no fuso do grupo: o último dia que dá para marcar jogo é o anterior. */
        fun limit(end: Instant, zone: ZoneId): Instant = end.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()

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
