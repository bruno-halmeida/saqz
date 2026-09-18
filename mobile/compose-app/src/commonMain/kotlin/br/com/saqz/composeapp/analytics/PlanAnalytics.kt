package br.com.saqz.composeapp.analytics

import br.com.saqz.core.common.analytics.SaqzAnalytics
import br.com.saqz.domain.SaqzResult
import br.com.saqz.subscriptions.domain.trial.TrialAccess
import br.com.saqz.subscriptions.domain.trial.TrialGateway
import br.com.saqz.subscriptions.domain.trial.TrialStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Instant

/**
 * O estado do plano no analytics: user properties `plan_state`/`acquisition_coupon` e os dois
 * fatos do funil trial → compra. Lê o `TrialAccess` que o servidor já manda; não guarda nada
 * em disco. `trial_started` sai da transição Available → Active vista no mesmo processo (criar
 * o 1º grupo inicia o trial); o número oficial de conversão é SQL no Postgres.
 */
internal class PlanAnalytics(
    private val trial: TrialGateway,
    private val scope: CoroutineScope,
) {
    private var lastStatus: TrialStatus? = null

    /** Chamar quando a sessão fica pronta e quando a lista de grupos muda. */
    fun refresh() {
        scope.launch { load()?.let(::publish) }
    }

    /** Chamar quando o portão de assinatura confirma a autorização (assinatura ativa). */
    fun purchased() {
        scope.launch {
            val access = load()
            SaqzAnalytics.track("purchase", purchaseParams(access))
            access?.let(::publish)
        }
    }

    private suspend fun load(): TrialAccess? = (trial.ownerTrial() as? SaqzResult.Success)?.value

    private fun publish(access: TrialAccess) {
        SaqzAnalytics.userProperty("plan_state", access.status.name.lowercase())
        access.selectedCouponCode?.let { SaqzAnalytics.userProperty("acquisition_coupon", it) }
        if (lastStatus == TrialStatus.Available && access.status == TrialStatus.Active) {
            SaqzAnalytics.track(
                "trial_started",
                buildMap {
                    put("trial_days", access.trialDays.toString())
                    access.selectedCouponCode?.let { put("coupon", it) }
                },
            )
        }
        lastStatus = access.status
    }
}

internal fun purchaseParams(access: TrialAccess?): Map<String, String> = buildMap {
    access?.selectedCouponCode?.let { put("coupon", it) }
    daysInTrial(access)?.let { put("days_in_trial", it.toString()) }
}

/** Dias corridos entre o início do trial e o relógio do servidor; `null` sem trial ou com data ilegível. */
internal fun daysInTrial(access: TrialAccess?): Long? {
    val started = access?.startedAt ?: return null
    return runCatching { (Instant.parse(access.serverTime) - Instant.parse(started)).inWholeDays }.getOrNull()
}
