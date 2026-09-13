package br.com.saqz.receivables.domain

import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FinancialAccessTest {
    private val owner = UUID.randomUUID()
    private val delegate = UUID.randomUUID()
    private val account = FinancialAccount(UUID.randomUUID(), owner, RegistrationStatus.APPROVED, true)
    private val context = FinancialAccessContext(account, owner, null, false, true, true, true, rolloutEnabled = true)

    @Test fun `rollout off blocks new business for owner and delegate but preserves maintenance`() {
        for (actor in listOf(context, context.copy(actorUserId = delegate, delegateIsCurrentAdministrator = true,
            delegation = FinancialDelegation(account.id, delegate, Instant.EPOCH)))) {
            val off = actor.copy(rolloutEnabled = false)
            for (action in listOf(FinancialAction.ACTIVATE_GROUP, FinancialAction.ISSUE_ORDER, FinancialAction.START_RECURRENCE))
                assertEquals(UnavailabilityReason.OPERATIONS_DISABLED, FinancialAccess.permission(action,off).reason)
            for (action in listOf(FinancialAction.READ, FinancialAction.CANCEL, FinancialAction.WITHDRAW, FinancialAction.REFUND,
                FinancialAction.CORRECT_REGISTRATION, FinancialAction.RENEW_INSTRUMENT))
                assertTrue(FinancialAccess.permission(action,off).allowed)
        }
    }

    @Test
    fun `expired plan and disabled group preserve existing money and instruments`() {
        val expired = context.copy(commerciallyEligible = false, groupEnabled = false,
            account = account.copy(newOperationsEnabled = false))
        listOf(FinancialAction.READ, FinancialAction.WITHDRAW, FinancialAction.REFUND,
            FinancialAction.CANCEL, FinancialAction.CORRECT_REGISTRATION,
            FinancialAction.CHANGE_BANK_DESTINATION, FinancialAction.RENEW_INSTRUMENT).forEach {
            assertEquals(ActionPermission(true), FinancialAccess.permission(it, expired), it.name)
        }
        listOf(FinancialAction.ISSUE_ORDER, FinancialAction.START_RECURRENCE,
            FinancialAction.ACTIVATE_GROUP).forEach {
            assertEquals(ActionPermission(false, UnavailabilityReason.INELIGIBLE_PLAN),
                FinancialAccess.permission(it, expired))
        }
    }

    @Test
    fun `new charges require approval operational release and group activation`() {
        assertTrue(FinancialAccess.permission(FinancialAction.ISSUE_ORDER, context).allowed)
        assertEquals(UnavailabilityReason.REGISTRATION_NOT_APPROVED, FinancialAccess.permission(
            FinancialAction.ISSUE_ORDER, context.copy(account = account.copy(registration = RegistrationStatus.UNDER_REVIEW))).reason)
        assertEquals(UnavailabilityReason.OPERATIONS_DISABLED, FinancialAccess.permission(
            FinancialAction.ISSUE_ORDER, context.copy(account = account.copy(newOperationsEnabled = false))).reason)
        assertEquals(UnavailabilityReason.GROUP_NOT_ENABLED, FinancialAccess.permission(
            FinancialAction.ISSUE_ORDER, context.copy(groupEnabled = false)).reason)
        assertTrue(FinancialAccess.permission(FinancialAction.ACTIVATE_GROUP, context.copy(groupEnabled = false)).allowed)
    }

    @Test
    fun `account delegation grants operation but never redelegation or legal ownership`() {
        val delegated = context.copy(actorUserId = delegate, delegateIsCurrentAdministrator = true,
            delegation = FinancialDelegation(account.id, delegate, Instant.EPOCH))
        assertTrue(FinancialAccess.permission(FinancialAction.REFUND, delegated).allowed)
        listOf(FinancialAction.DELEGATE, FinancialAction.REGISTER_LEGAL_IDENTITY).forEach {
            assertEquals(UnavailabilityReason.UNAUTHORIZED, FinancialAccess.permission(it, delegated).reason)
        }
        assertEquals(UnavailabilityReason.UNAUTHORIZED, FinancialAccess.permission(FinancialAction.READ,
            delegated.copy(delegation = delegated.delegation!!.copy(revokedAt = Instant.now()))).reason)
        assertEquals(UnavailabilityReason.UNAUTHORIZED, FinancialAccess.permission(FinancialAction.READ,
            delegated.copy(delegateIsCurrentAdministrator = false)).reason)
    }

    @Test
    fun `new group owner and delegates of other accounts have no access`() {
        val stranger = context.copy(actorUserId = delegate, delegateIsCurrentAdministrator = true)
        assertEquals(UnavailabilityReason.UNAUTHORIZED, FinancialAccess.permission(FinancialAction.READ, stranger).reason)
        assertEquals(UnavailabilityReason.UNAUTHORIZED, FinancialAccess.permission(FinancialAction.READ,
            stranger.copy(delegation = FinancialDelegation(UUID.randomUUID(), delegate, Instant.EPOCH))).reason)
    }

    @Test
    fun `withdrawal and bank changes require recent authentication even for owner`() {
        listOf(FinancialAction.WITHDRAW, FinancialAction.CHANGE_BANK_DESTINATION).forEach {
            assertEquals(UnavailabilityReason.RECENT_AUTHENTICATION_REQUIRED,
                FinancialAccess.permission(it, context.copy(recentlyAuthenticated = false)).reason)
        }
        assertTrue(FinancialAccess.permission(FinancialAction.READ, context.copy(recentlyAuthenticated = false)).allowed)
    }
}
