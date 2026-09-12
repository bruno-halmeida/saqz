package br.com.saqz.receivables.domain

import java.time.Instant
import java.util.UUID

enum class FinancialAction {
    READ, CORRECT_REGISTRATION, CANCEL, REFUND, WITHDRAW, CHANGE_BANK_DESTINATION,
    DELEGATE, REGISTER_LEGAL_IDENTITY, ACTIVATE_GROUP, ISSUE_ORDER, START_RECURRENCE,
    RENEW_INSTRUMENT,
}

enum class UnavailabilityReason {
    UNAUTHORIZED, RECENT_AUTHENTICATION_REQUIRED, INELIGIBLE_PLAN,
    REGISTRATION_NOT_APPROVED, OPERATIONS_DISABLED, GROUP_NOT_ENABLED,
}

data class ActionPermission(val allowed: Boolean, val reason: UnavailabilityReason? = null) {
    init { require(allowed == (reason == null)) }
}

enum class RegistrationStatus { INCOMPLETE, UNDER_REVIEW, CORRECTION_REQUIRED, APPROVED, REJECTED }

data class FinancialAccount(
    val id: UUID,
    val ownerUserId: UUID,
    val registration: RegistrationStatus,
    val newOperationsEnabled: Boolean,
)

data class FinancialDelegation(
    val accountId: UUID,
    val userId: UUID,
    val grantedAt: Instant,
    val revokedAt: Instant? = null,
)

/** Input must be read afresh per operation, never cached in a login session. */
data class FinancialAccessContext(
    val account: FinancialAccount,
    val actorUserId: UUID,
    val delegation: FinancialDelegation?,
    val delegateIsCurrentAdministrator: Boolean,
    val recentlyAuthenticated: Boolean,
    val commerciallyEligible: Boolean,
    val groupEnabled: Boolean,
)

object FinancialAccess {
    fun permission(action: FinancialAction, context: FinancialAccessContext): ActionPermission {
        val owner = context.actorUserId == context.account.ownerUserId
        val delegated = context.delegation?.let {
            it.accountId == context.account.id && it.userId == context.actorUserId &&
                it.revokedAt == null && context.delegateIsCurrentAdministrator
        } == true
        if (!owner && !delegated) return deny(UnavailabilityReason.UNAUTHORIZED)
        if (action in ownerOnly && !owner) return deny(UnavailabilityReason.UNAUTHORIZED)
        if (action in recentAuthentication && !context.recentlyAuthenticated) {
            return deny(UnavailabilityReason.RECENT_AUTHENTICATION_REQUIRED)
        }
        if (action in newBusiness) {
            if (!context.commerciallyEligible) return deny(UnavailabilityReason.INELIGIBLE_PLAN)
            if (context.account.registration != RegistrationStatus.APPROVED) {
                return deny(UnavailabilityReason.REGISTRATION_NOT_APPROVED)
            }
            if (!context.account.newOperationsEnabled) return deny(UnavailabilityReason.OPERATIONS_DISABLED)
            if (action != FinancialAction.ACTIVATE_GROUP && !context.groupEnabled) {
                return deny(UnavailabilityReason.GROUP_NOT_ENABLED)
            }
        }
        return ActionPermission(true)
    }

    private fun deny(reason: UnavailabilityReason) = ActionPermission(false, reason)
    private val ownerOnly = setOf(FinancialAction.DELEGATE, FinancialAction.REGISTER_LEGAL_IDENTITY)
    private val recentAuthentication = setOf(FinancialAction.WITHDRAW, FinancialAction.CHANGE_BANK_DESTINATION)
    private val newBusiness = setOf(
        FinancialAction.ACTIVATE_GROUP, FinancialAction.ISSUE_ORDER, FinancialAction.START_RECURRENCE,
    )
}
