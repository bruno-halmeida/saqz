package br.com.saqz.receivables.application

import java.time.Instant
import java.util.UUID

enum class OperationStatus { READY, RUNNING, UNKNOWN, SUCCEEDED, REJECTED }
enum class OperationKind { CONFIGURE_WEBHOOK, ISSUE_ORDER, RECONCILE_PAYMENT, CANCEL_ORDER, ACTIVATE_GROUP, DEACTIVATE_GROUP, GRANT_DELEGATION, REVOKE_DELEGATION, CREATE_ACCOUNT, CREATE_INSTRUMENT, CANCEL_INSTRUMENT, START_RECURRENCE, STOP_RECURRENCE, WITHDRAW, REFUND }

data class FinancialOperation(
    val id: UUID,
    val accountId: UUID,
    val requestId: UUID,
    val actorUserId: UUID,
    val kind: OperationKind,
    val resourceId: UUID,
    val requestDigest: String,
    val status: OperationStatus = OperationStatus.READY,
    val providerReference: String? = null,
)

data class OperationClaim(val operation: FinancialOperation, val token: UUID, val recoveryOnly: Boolean)

interface FinancialOperationStore {
    /** Same request with different content or actor is a conflict, never a second operation. */
    fun register(operation: FinancialOperation, now: Instant): FinancialOperation
    fun claim(accountId: UUID, operationId: UUID, now: Instant, leaseUntil: Instant): OperationClaim?
    fun finish(claim: OperationClaim, status: OperationStatus, providerReference: String?, now: Instant, retryAt: Instant): Boolean
}

class FinancialRequestConflict : RuntimeException("Financial request already used with different content")

sealed interface ProviderOperationResult {
    data class Confirmed(val reference: String) : ProviderOperationResult
    data object Rejected : ProviderOperationResult
    data object Unknown : ProviderOperationResult
}

interface FinancialOperationProvider {
    fun execute(operation: FinancialOperation): ProviderOperationResult
    /** Absence in an eventually consistent list is UNKNOWN, not proof of rejection. */
    fun recover(operation: FinancialOperation): ProviderOperationResult
}

class RunFinancialOperation(private val store: FinancialOperationStore, private val provider: FinancialOperationProvider) {
    fun run(accountId: UUID, operationId: UUID, now: Instant): Boolean {
        val claim = store.claim(accountId, operationId, now, now.plusSeconds(90)) ?: return false
        val result = try {
            if (claim.recoveryOnly) provider.recover(claim.operation) else provider.execute(claim.operation)
        } catch (_: Exception) {
            ProviderOperationResult.Unknown
        }
        val status = when (result) {
            is ProviderOperationResult.Confirmed -> OperationStatus.SUCCEEDED
            ProviderOperationResult.Rejected -> OperationStatus.REJECTED
            ProviderOperationResult.Unknown -> OperationStatus.UNKNOWN
        }
        return store.finish(claim, status, (result as? ProviderOperationResult.Confirmed)?.reference,
            now, now.plusSeconds(60))
    }
}
