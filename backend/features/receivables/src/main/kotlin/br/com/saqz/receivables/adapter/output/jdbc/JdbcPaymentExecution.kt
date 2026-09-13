package br.com.saqz.receivables.adapter.output.jdbc

import br.com.saqz.receivables.application.*
import br.com.saqz.receivables.domain.PaymentMethod
import br.com.saqz.receivables.adapter.output.crypto.FinancialSecrets
import br.com.saqz.sharedkernel.group.GroupChargePayments
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.Timestamp
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource

/** Durable provider execution adapter. Network calls are never made with a group/account transaction open. */
class JdbcPaymentExecution(dataSource: DataSource, private val store: JdbcPaymentStore,
    private val charges: GroupChargePayments, private val operations: JdbcFinancialOperationStore,
    private val provider: OneOffPaymentProvider, private val secrets: FinancialSecrets, private val clock: Clock,
    private val residualCosts: ReconcileExternalResidualCost) : PaymentExecution {
    private val jdbc = JdbcClient.create(dataSource)
    private val mapper = jacksonObjectMapper()
    override fun create(instrumentId: UUID) {
        val context = context(instrumentId) ?: return
        if (context.instrument.status !in setOf("CREATING", "UNKNOWN")) return
        val prepared = try { provider.prepareCustomer(context) } catch (_: DefinitivePaymentRejection) { rejectCreation(instrumentId); return } catch (_: Exception) { false }
        if (!prepared) { unknown(instrumentId); return }
        run(context, instrumentId, false)
    }
    override fun cancel(instrumentId: UUID, request: FinancialRequest) {
        val context = context(instrumentId) ?: return
        // Creation may still be uncertain. Never issue DELETE until the original create is recovered.
        if (context.instrument.paymentId == null) {
            val stopped = store.transaction {
                val i = lock(instrumentId) ?: return@transaction false
                val untouched = jdbc.sql("UPDATE receivable_operations SET status='REJECTED',failure_code='CANCELLED_BEFORE_CREATE' WHERE id=:id AND status='READY'")
                    .param("id", instrumentId).update() == 1
                if (untouched) {
                    jdbc.sql("UPDATE receivable_instruments SET status='CANCELLED' WHERE id=:id").param("id", instrumentId).update()
                    val order = store.order(i.orderId)!!
                    store.changeOrder(order.id, "CANCELLED"); charges.release(order.chargeId, order.id)
                }
                untouched
            }
            if (!stopped) reconcile(instrumentId)
            return
        }
        val id = UUID.nameUUIDFromBytes("cancel-instrument:$instrumentId".toByteArray())
        val originalActor = jdbc.sql("SELECT actor_user_id FROM receivable_operations WHERE id=:id").param("id", id)
            .query(UUID::class.java).optional().orElse(request.actorUserId)
        operations.register(FinancialOperation(id, context.instrument.accountId,
            UUID.nameUUIDFromBytes("cancel-request:$instrumentId".toByteArray()), originalActor,
            OperationKind.CANCEL_INSTRUMENT, instrumentId, paymentDigest("CANCEL_INSTRUMENT:$instrumentId")), clock.instant())
        run(context, id, true)
    }
    private fun run(context: ProviderPaymentContext, operationId: UUID, cancel: Boolean) {
        val claim = operations.claim(context.instrument.accountId, operationId, clock.instant(), clock.instant().plusSeconds(90)) ?: return
        val observed = try {
            if (cancel) provider.cancel(context) else if (claim.recoveryOnly) provider.recover(context) else provider.create(context)
        } catch (_: DefinitivePaymentRejection) {
            if (!cancel && !claim.recoveryOnly) { rejectCreation(context.instrument.id, claim); return }
            null
        } catch (_: Exception) { null }
        val applied = store.transaction {
            val i = lock(context.instrument.id) ?: return@transaction false
            // Fence stale workers before applying their result. A query/webhook can still recover it later.
            val succeeded = observed != null && (!cancel || observed.status !in setOf("ACTIVE", "UNKNOWN", "DISPUTED", "RECOVERY_PENDING"))
            val finished = jdbc.sql("""UPDATE receivable_operations SET status=:status,provider_reference=:ref,updated_at=:at,
                next_attempt_at=:retry,lease_token=NULL,lease_until=NULL WHERE id=:id AND lease_token=:token AND status='RUNNING'""")
                .param("status", if (succeeded) "SUCCEEDED" else "UNKNOWN").param("ref", observed?.paymentId)
                .param("at", Timestamp.from(clock.instant())).param("retry", Timestamp.from(clock.instant().plusSeconds(60)))
                .param("id", claim.operation.id).param("token", claim.token).update()
            if (finished != 1) return@transaction false
            if (observed != null) apply(i, observed) else { unknown(i.id); false }
        }
        if (applied && observed != null) reconcileResidualCosts(context.instrument, observed)
    }
    override fun reconcile(instrumentId: UUID): Boolean {
        var context = context(instrumentId) ?: return false
        val initialOrder = store.order(context.instrument.orderId) ?: return false
        if (initialOrder.status == "CANCEL_PENDING" && context.instrument.paymentId == null) {
            val ready = jdbc.sql("SELECT count(*) FROM receivable_operations WHERE id=:id AND status='READY'")
                .param("id", instrumentId).query(Int::class.java).single() == 1
            if (ready) {
                cancel(instrumentId, FinancialRequest(UUID.nameUUIDFromBytes("cancel-recovery:$instrumentId".toByteArray()), initialOrder.payerId))
                return store.order(initialOrder.id)?.status == "CANCELLED"
            }
        }
        if (context.instrument.status in setOf("CREATING", "UNKNOWN")) {
            create(instrumentId)
            context = context(instrumentId) ?: return false
        }
        val observed = try { provider.recover(context) } catch (_: Exception) { null } ?: return false
        val applied = store.transaction { lock(instrumentId)?.let { apply(it, observed) } ?: false }
        if (!applied) return false
        reconcileResidualCosts(context.instrument, observed)
        val order = store.order(context.instrument.orderId) ?: return false
        if (order.status == "CANCEL_PENDING") {
            val actor = jdbc.sql("SELECT approved_by FROM receivable_orders WHERE id=:id").param("id", order.id)
                .query(UUID::class.java).single()
            cancel(instrumentId, FinancialRequest(UUID.nameUUIDFromBytes("cancel-reconcile:$instrumentId".toByteArray()), actor))
        }
        return true
    }
    /** Administrative recovery probe: provider reads and correlated fact application only. */
    fun reconcileObserved(instrumentId: UUID): Boolean {
        val context = context(instrumentId) ?: return false
        val observed = try { provider.recover(context) } catch (_: Exception) { null } ?: return false
        val applied = store.transaction {
            val instrument = lock(instrumentId) ?: return@transaction false
            if (!apply(instrument, observed)) return@transaction false
            completeObservedOperations(instrument, observed)
            true
        }
        if (applied) reconcileResidualCosts(context.instrument, observed)
        return applied
    }
    private fun completeObservedOperations(instrument: PaymentInstrument, observed: ProviderPaymentObservation) {
        val exists = observed.paymentId != null || observed.checkoutId != null
        if (!exists || observed.status in setOf("UNKNOWN", "CREATING")) return
        completeObservedOperation(instrument, "CREATE_INSTRUMENT", "SUCCEEDED", observed.paymentId)
        when (observed.status) {
            "CANCELLED", "EXPIRED" -> completeObservedOperation(instrument, "CANCEL_INSTRUMENT", "SUCCEEDED", observed.paymentId)
            "CONFIRMED", "SETTLED", "AVAILABLE", "REFUNDED", "CHARGEBACK" ->
                completeObservedOperation(instrument, "CANCEL_INSTRUMENT", "REJECTED", observed.paymentId)
        }
    }
    private fun completeObservedOperation(instrument: PaymentInstrument, kind: String, status: String, reference: String?) {
        // Preserve a live execution lease. Only a proved existing resource can resolve uncertain work.
        jdbc.sql("""UPDATE receivable_operations SET status=:status, provider_reference=coalesce(:reference,provider_reference),
            updated_at=:at,lease_token=NULL,lease_until=NULL,failure_code=NULL
            WHERE account_id=:account AND resource_id=:resource AND kind=:kind
            AND (status='UNKNOWN' OR (status='RUNNING' AND lease_until<=:at))""")
            .param("status", status).param("reference", reference, java.sql.Types.VARCHAR)
            .param("at", Timestamp.from(clock.instant())).param("account", instrument.accountId)
            .param("resource", instrument.id).param("kind", kind).update()
    }
    private fun reconcileResidualCosts(instrument: PaymentInstrument, observed: ProviderPaymentObservation) {
        if (observed.status !in setOf("REFUNDED", "CHARGEBACK")) return
        observed.residualCosts.forEach { cost ->
            residualCosts.execute(ObservedResidualCost(instrument.accountId, instrument.id,
                cost.providerReference, cost.amountCents), clock.instant())
        }
    }
    override fun renewPix(instrumentId: UUID, dueDate: LocalDate, request: FinancialRequest) {
        val renewalId = jdbc.sql("""SELECT id FROM receivable_pix_renewals WHERE instrument_id=:instrument
            AND request_id=:request AND actor_user_id=:actor AND due_date=:due""").param("instrument", instrumentId)
            .param("request", request.requestId).param("actor", request.actorUserId).param("due", dueDate)
            .query(UUID::class.java).optional().orElse(null) ?: return
        executePixRenewal(renewalId)
    }
    fun recoverPixRenewals() {
        jdbc.sql("""SELECT id FROM receivable_pix_renewals WHERE status IN ('READY','UNKNOWN') AND next_attempt_at<=:at
            ORDER BY next_attempt_at,id LIMIT 100""").param("at", Timestamp.from(clock.instant())).query(UUID::class.java).list()
            .filterNotNull().forEach(::executePixRenewal)
    }
    private fun executePixRenewal(renewalId: UUID) {
        data class Renewal(val instrumentId: UUID, val dueDate: LocalDate, val token: UUID, val recoveryOnly: Boolean)
        val renewal = store.transaction {
            val row = jdbc.sql("""SELECT instrument_id,due_date,status FROM receivable_pix_renewals WHERE id=:id AND
                ((status IN ('READY','UNKNOWN') AND next_attempt_at<=:at) OR (status='RUNNING' AND lease_until<=:at))
                FOR UPDATE SKIP LOCKED""").param("id", renewalId).param("at", Timestamp.from(clock.instant()))
                .query { r, _ -> Triple(r.getObject("instrument_id", UUID::class.java), r.getObject("due_date", LocalDate::class.java), r.getString("status")) }
                .optional().orElse(null) ?: return@transaction null
            val token = UUID.randomUUID()
            jdbc.sql("""UPDATE receivable_pix_renewals SET status='RUNNING',attempts=attempts+1,lease_token=:token,
                lease_until=:lease,updated_at=:at WHERE id=:id""").param("token", token)
                .param("lease", Timestamp.from(clock.instant().plusSeconds(90))).param("at", Timestamp.from(clock.instant()))
                .param("id", renewalId).update()
            Renewal(row.first, row.second, token, row.third != "READY")
        } ?: return
        val context = context(renewal.instrumentId)
        val observed = if (context == null) null else try {
            if (!renewal.recoveryOnly) provider.renewPix(context, renewal.dueDate)
            else {
                val current = provider.recover(context)
                when {
                    current == null -> null
                    current.dueDate == renewal.dueDate -> current
                    current.status == "ACTIVE" -> provider.renewPix(context, renewal.dueDate)
                    else -> null
                }
            }
        } catch (_: Exception) { null }
        store.transaction {
            val instrument = context?.let { lock(renewal.instrumentId) }
            val valid = observed != null && instrument != null && observed.dueDate == renewal.dueDate && observed.status == "ACTIVE"
            val changed = jdbc.sql("""UPDATE receivable_pix_renewals SET status=:status,next_attempt_at=:next,
                lease_token=NULL,lease_until=NULL,updated_at=:at WHERE id=:id AND status='RUNNING' AND lease_token=:token""")
                .param("status", if (valid) "SUCCEEDED" else "UNKNOWN")
                .param("next", Timestamp.from(clock.instant().plusSeconds(60))).param("at", Timestamp.from(clock.instant()))
                .param("id", renewalId).param("token", renewal.token).update()
            if (changed == 1 && valid) {
                apply(instrument!!, observed!!)
                jdbc.sql("UPDATE receivable_instruments SET status='ACTIVE' WHERE id=:id AND status='EXPIRED'")
                    .param("id", instrument.id).update()
                val order = store.order(instrument.orderId)!!
                jdbc.sql("UPDATE receivable_orders SET due_date=:due WHERE id=:id").param("due", renewal.dueDate).param("id", order.id).update()
                jdbc.sql("UPDATE group_charges SET due_date=:due,updated_at=:at WHERE id=:id AND status='PENDING'")
                    .param("due", renewal.dueDate).param("at", Timestamp.from(clock.instant())).param("id", order.chargeId).update()
            }
        }
    }
    private fun context(id: UUID): ProviderPaymentContext? {
        val i = store.instrument(id) ?: return null; val order = store.order(i.orderId) ?: return null
        return ProviderPaymentContext(i, order.payerId, order.dueDate)
    }
    private fun lock(id: UUID): PaymentInstrument? {
        val i = store.instrument(id) ?: return null; val order = store.order(i.orderId) ?: return null
        charges.lock(order.chargeId) ?: return null
        jdbc.sql("SELECT id FROM receivable_accounts WHERE id=:id FOR UPDATE").param("id", i.accountId).query(UUID::class.java).single()
        jdbc.sql("SELECT id FROM receivable_instruments WHERE id=:id FOR UPDATE").param("id", id).query(UUID::class.java).single()
        return store.instrument(id)
    }
    private fun apply(current: PaymentInstrument, observed: ProviderPaymentObservation): Boolean {
        val order = store.order(current.orderId)!!
        val ref = observed.paymentId ?: current.id.toString()
        if (observed.reference != current.id.toString() || observed.method != current.quote.method ||
            observed.totalCents != current.quote.totalCents || (current.paymentId != null && current.paymentId != observed.paymentId)) {
            occurrence(current, "PAYMENT_MISMATCH", ref); return false
        }
        if (observed.providerFeeCents != null && observed.providerFeeCents != current.quote.providerFeeCents)
            occurrence(current, "PROVIDER_FEE_MISMATCH", ref)
        if (observed.splitCents != current.quote.commissionCents) occurrence(current, "SPLIT_MISMATCH", ref)
        if (current.status in setOf("CANCELLED", "EXPIRED") && observed.status in setOf("CONFIRMED", "SETTLED", "AVAILABLE"))
            occurrence(current, "PAID_AFTER_CANCELLATION", ref)
        if (observed.status in setOf("DISPUTED", "RECOVERY_PENDING")) occurrence(current, "CHARGEBACK_DISPUTE_PENDING", ref)
        val status = PaymentFacts.next(current, observed)
        val paid = observed.status in setOf("CONFIRMED", "SETTLED", "AVAILABLE")
        val settled = observed.status in setOf("SETTLED", "AVAILABLE")
        val reversed = status in setOf("REFUNDED", "CHARGEBACK")
        val payload = mapOf("pixPayload" to (observed.pixPayload ?: current.pixPayload),
            "pixImage" to (observed.pixImage ?: current.pixImage), "checkoutUrl" to (observed.checkoutUrl ?: current.checkoutUrl)).filterValues { it != null }
        jdbc.sql("""UPDATE receivable_instruments SET provider_payment_id=coalesce(provider_payment_id,:payment),
            provider_split_id=coalesce(provider_split_id,:split), expires_at=coalesce(:expires,expires_at), status=:status,payload_encrypted=:payload,
            confirmed=confirmed OR :paid,settled=settled OR :settled,available=available OR :available,
            split_settled=split_settled OR :splitSettled WHERE id=:id""")
            .param("expires", observed.expiresAt?.let(Timestamp::from)).param("payment", observed.paymentId).param("split", observed.splitId).param("status", status)
            .param("payload", secrets.encrypt(current.accountId, "payment-instrument", mapper.writeValueAsString(payload)))
            .param("paid", paid).param("settled", settled).param("available", observed.available)
            .param("splitSettled", observed.splitSettled && observed.splitCents == current.quote.commissionCents)
            .param("id", current.id).update()
        var cashConflict = false
        // Reversal can arrive before confirmation. Record the payment and its reversal in this same transaction.
        if ((paid || reversed) && order.status !in setOf("REFUNDED", "CHARGEBACK")) {
            if (charges.recordPayment(order.chargeId, order.id, order.payerId, current.quote.method == PaymentMethod.PIX, clock.instant())) {
                cashEffect(order.id, "PAYMENT")
                movement(current, "PAYMENT", current.quote.totalCents, ref)
                if (observed.providerFeeCents != null) movement(current, "PROVIDER_FEE", -observed.providerFeeCents, ref)
                else occurrence(current, "PROVIDER_FEE_PENDING", ref)
                store.changeOrder(order.id, "PAID")
            } else { occurrence(current, "CASH_CONFLICT", ref); cashConflict = true }
        }
        if (observed.splitSettled && observed.splitCents != null && observed.splitCents > 0) movement(current, "COMMISSION", -observed.splitCents, ref)
        if (observed.returnedCommissionCents != null) returnedCommission(current, observed.returnedCommissionCents, ref)
        if (settled && observed.providerFeeCents != null) movement(current, "SETTLEMENT", observed.totalCents - observed.providerFeeCents, ref)
        if (observed.available && observed.providerFeeCents != null) movement(current, "AVAILABILITY", observed.totalCents - observed.providerFeeCents, ref)
        if (reversed) {
            occurrence(current, "REVERSAL_PROVIDER_COST_PENDING", ref)
            if (current.quote.commissionCents > 0 && observed.returnedCommissionCents == null)
                occurrence(current, "REVERSAL_SPLIT_PENDING", ref)
            if (charges.recordReversal(order.chargeId, order.id, order.payerId, clock.instant())) {
                cashEffect(order.id, "REVERSAL"); movement(current, if (status == "REFUNDED") "REFUND" else "CHARGEBACK", -current.quote.totalCents, ref)
                store.changeOrder(order.id, status)
            } else { occurrence(current, "REVERSAL_CASH_CONFLICT", ref); cashConflict = true }
        }
        if (status in setOf("CANCELLED", "EXPIRED") && order.status == "CANCEL_PENDING") {
            store.changeOrder(order.id, "CANCELLED"); charges.release(order.chargeId, order.id)
        }
        return !cashConflict
    }
    private fun returnedCommission(i: PaymentInstrument, returned: Long, ref: String) {
        // A REFUNDED split alone does not prove that its commission was debited.
        // Only offset the effective debit recorded from a settled split, never the quote.
        val debit = jdbc.sql("""SELECT coalesce(-sum(amount_cents),0) FROM receivable_movements
            WHERE instrument_id=:id AND kind='COMMISSION' AND amount_cents<0""")
            .param("id", i.id).query(Long::class.java).single()
        val credit = jdbc.sql("""SELECT coalesce(sum(amount_cents),0) FROM receivable_movements
            WHERE instrument_id=:id AND kind='COMMISSION' AND amount_cents>0""")
            .param("id", i.id).query(Long::class.java).single()
        if (returned < 0 || returned > debit || (credit != 0L && credit != returned)) {
            occurrence(i, "REVERSAL_SPLIT_PENDING", ref)
            return
        }
        if (returned > 0 && credit == 0L) movement(i, "COMMISSION", returned, "$ref:refund")
    }
    private fun rejectCreation(id: UUID, claim: OperationClaim? = null) {
        store.transaction {
            val i = lock(id) ?: return@transaction
            if (i.paymentId != null) return@transaction
            val changed = jdbc.sql("""UPDATE receivable_operations SET status='REJECTED',failure_code='INVALID_PROVIDER_INPUT',lease_token=NULL,lease_until=NULL
                WHERE id=:id AND ((CAST(:token AS uuid) IS NULL AND status='READY') OR lease_token=:token)""")
                .param("id", id).param("token", claim?.token, java.sql.Types.OTHER).update()
            if (changed == 1) {
                jdbc.sql("UPDATE receivable_instruments SET status='CANCELLED' WHERE id=:id AND status IN ('CREATING','UNKNOWN')")
                    .param("id", id).update()
                val order = store.order(i.orderId)!!
                if (order.status == "CANCEL_PENDING") {
                    store.changeOrder(order.id, "CANCELLED"); charges.release(order.chargeId, order.id)
                }
            }
        }
    }
    private fun unknown(id: UUID) {
        jdbc.sql("UPDATE receivable_instruments SET status='UNKNOWN' WHERE id=:id AND status='CREATING'").param("id", id).update()
    }
    private fun cashEffect(order: UUID, effect: String) {
        jdbc.sql("INSERT INTO receivable_cash_effects VALUES (:order,:effect,:at) ON CONFLICT DO NOTHING")
            .param("order", order).param("effect", effect).param("at", Timestamp.from(clock.instant())).update()
    }
    private fun movement(i: PaymentInstrument, kind: String, cents: Long, ref: String) {
        jdbc.sql("""INSERT INTO receivable_movements(id,account_id,instrument_id,kind,amount_cents,provider_reference,occurred_at)
            VALUES (:id,:account,:instrument,:kind,:amount,:ref,:at) ON CONFLICT DO NOTHING""")
            .param("id", UUID.randomUUID()).param("account", i.accountId).param("instrument", i.id).param("kind", kind)
            .param("amount", cents).param("ref", ref).param("at", Timestamp.from(clock.instant())).update()
    }
    private fun occurrence(i: PaymentInstrument, code: String, reference: String) {
        jdbc.sql("INSERT INTO receivable_payment_occurrences VALUES (:id,:account,:instrument,:code,:ref,:at) ON CONFLICT DO NOTHING")
            .param("id", UUID.randomUUID()).param("account", i.accountId).param("instrument", i.id).param("code", code)
            .param("ref", reference).param("at", Timestamp.from(clock.instant())).update()
    }
}
