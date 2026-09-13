package br.com.saqz.receivables.application

import br.com.saqz.receivables.domain.FinancialAccount
import br.com.saqz.receivables.domain.FinancialDelegation
import java.time.Instant
import java.util.UUID

/** Implemented at composition root using the central trial/subscription contract. */
fun interface ReceivablesEligibility {
    fun forOwner(ownerUserId: UUID, at: Instant): ReceivablesEntitlement
}

data class ReceivablesEntitlement(val eligible: Boolean, val effectiveCutoffAt: Instant?)

interface ReceivablesGroups {
    fun isOwner(groupId: UUID, userId: UUID): Boolean
    fun isCurrentAdministratorOfOwner(userId: UUID, ownerUserId: UUID): Boolean
}

interface FinancialAccountRepository {
    fun listForUser(userId: UUID): List<FinancialAccount>
    fun findById(accountId: UUID): FinancialAccount?
    fun findByOwner(ownerUserId: UUID): FinancialAccount?
    fun findDelegation(accountId: UUID, userId: UUID): FinancialDelegation?
}

/** Monetary API values are integer BRL cents. Every mutation carries a stable request ID. */
data class FinancialRequest(val requestId: UUID, val actorUserId: UUID)

enum class FinancialError {
    NOT_FOUND, UNAUTHORIZED, INVALID_INPUT, CONFLICT, INSUFFICIENT_BALANCE,
    REGISTRATION_RESTRICTED, PROVIDER_UNAVAILABLE, RESULT_PENDING, CONFIGURATION_UNAVAILABLE,
    RECENT_AUTHENTICATION_REQUIRED, INELIGIBLE_PLAN, OPERATIONS_DISABLED,
}

sealed interface FinancialResult<out T> {
    data class Success<T>(val value: T, val requestId: UUID) : FinancialResult<T>
    data class Failure(val error: FinancialError, val requestId: UUID) : FinancialResult<Nothing>
}
