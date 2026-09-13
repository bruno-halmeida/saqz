package br.com.saqz.sharedkernel.group

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class PayableGroupCharge(val id: UUID, val groupId: UUID, val payerId: UUID,
    val baseCents: Long, val dueDate: LocalDate, val pending: Boolean, val version: Long,
    val reservedOrderId: UUID?, val groupActive: Boolean, val ownerUserId: UUID, val billingMonth: LocalDate?)

/** All methods participate in the caller's transaction. Lock order: group, charge, financial account, order.
 * Reservation also fences manual settlement and game cancellation in the groups feature. */
interface GroupChargePayments {
    fun lock(chargeId: UUID): PayableGroupCharge?
    fun reserve(chargeId: UUID, orderId: UUID): Boolean
    fun release(chargeId: UUID, orderId: UUID)
    fun recordPayment(chargeId: UUID, orderId: UUID, actorId: UUID, pix: Boolean, at: Instant): Boolean
    /** A reversal removes cash without reopening the debt. */
    fun recordReversal(chargeId: UUID, orderId: UUID, actorId: UUID, at: Instant): Boolean
}

/** Invoked by game cancellation inside the group/charge transaction. No network I/O. */
fun interface GroupChargePaymentCancellation {
    fun request(orderId: UUID, actorId: UUID, at: Instant)
}
