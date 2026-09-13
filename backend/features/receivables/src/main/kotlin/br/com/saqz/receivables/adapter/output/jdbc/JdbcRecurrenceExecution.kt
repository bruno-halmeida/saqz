package br.com.saqz.receivables.adapter.output.jdbc

import br.com.saqz.receivables.application.*
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class JdbcRecurrenceExecution(private val store: RecurrenceStore, private val provider: RecurrenceProvider,
    private val paymentExecution: PaymentExecution, private val accounts: FinancialAccountRepository,
    private val eligibility: ReceivablesEligibility, private val clock: Clock,
    private val businessZone: ZoneId = ZoneId.of("America/Sao_Paulo")) : RecurrenceExecution {

    override fun start(id: UUID) {
        val claim = store.claim(id, clock.instant(), clock.instant().plusSeconds(90)) ?: return
        if (claim.authorization.recurrence.status == "STOP_PENDING") { executeStop(claim); return }
        val result = try {
            if (!claim.recoveryOnly) {
                if (!provider.prepareCustomer(claim.authorization)) null else provider.create(claim.authorization)
            } else provider.recover(claim.authorization)
        } catch (_: Exception) { null }
        store.transaction { store.finish(claim, result, false, clock.instant()) }
    }

    override fun stop(id: UUID) {
        val claim = store.claim(id, clock.instant(), clock.instant().plusSeconds(90)) ?: return
        executeStop(claim)
    }

    private fun executeStop(claim: RecurrenceClaim) {
        val stopped = try {
            provider.stop(claim.authorization, LocalDate.ofInstant(claim.authorization.recurrence.cutoffAt ?: clock.instant(), businessZone))
        } catch (_: Exception) { false }
        val result = if (stopped) ProviderRecurrenceResult(subscriptionId = claim.authorization.recurrence.providerSubscriptionId,
            checkoutId = claim.authorization.providerCheckoutId, checkoutUrl = claim.authorization.recurrence.hostedCheckoutUrl,
            status = "STOPPED") else null
        store.transaction { store.finish(claim, result, true, clock.instant()) }
    }

    override fun recoverDue() {
        store.recoveryDue(clock.instant(), 100).forEach { id ->
            val status = store.transaction { store.find(id)?.recurrence?.status }
            if (status == "STOP_PENDING") stop(id) else start(id)
        }
    }

    override fun synchronize() {
        store.active(100).forEach { id ->
            try {
                val recurrence = store.transaction { store.find(id) } ?: return@forEach
                val instruments = provider.payments(recurrence).let { payments ->
                    store.transaction { store.materialize(recurrence, payments, clock.instant()) }
                }
                instruments.forEach(paymentExecution::reconcile)
            } catch (_: Exception) { /* next bounded schedule retries by stable provider identifiers */ }
        }
    }

    override fun enforceCutoffs() {
        val now = clock.instant()
        val candidates = store.cutoffCandidates(now, 100).toMutableList()
        store.active(100).forEach { id ->
            val recurrence = store.transaction { store.find(id) } ?: return@forEach
            val owner = accounts.findById(recurrence.recurrence.accountId)?.ownerUserId ?: return@forEach
            if (!eligibility.forOwner(owner, now).eligible && candidates.none { it.first == id }) candidates += id to "INELIGIBLE"
        }
        candidates.forEach { (id, reason) ->
            val recurrence = store.transaction { store.find(id) } ?: return@forEach
            val request = FinancialRequest(UUID.nameUUIDFromBytes("recurrence-cutoff:$id:$reason".toByteArray()), recurrence.recurrence.memberUserId)
            try {
                store.transaction { store.requestStop(id, request, reason, paymentDigest("STOP:$id:$reason"), now) }
                stop(id)
            } catch (_: FinancialRequestConflict) { /* a concurrent cutoff already owns this transition */ }
        }
    }
}
