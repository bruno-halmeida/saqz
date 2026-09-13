package br.com.saqz.receivables.application

import br.com.saqz.receivables.domain.*
import br.com.saqz.sharedkernel.group.GroupAdministrationDirectory
import br.com.saqz.sharedkernel.group.GroupFinancialSetup
import br.com.saqz.sharedkernel.group.GroupFinancialSetupLookup
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class GroupReceivablesState(val accountId: UUID, val groupId: UUID, val enabled: Boolean,
                                 val pixEnabled: Boolean, val cardEnabled: Boolean)
data class GroupPriceQuotes(val kind: String, val baseCents: Long, val quotes: List<FeeQuote>)
data class GroupReceivablesReview(val state: GroupReceivablesState, val schedules: List<FeeSchedule>,
    val prices: List<GroupPriceQuotes>, val permissions: Map<FinancialAction, ActionPermission>,
    val effectiveCutoffAt: Instant?, val fingerprint: String)

interface GroupReceivablesStore {
    fun <T> transaction(block: () -> T): T
    fun lockAccount(id: UUID): FinancialAccount?
    fun state(accountId: UUID, groupId: UUID): GroupReceivablesState?
    /** Under the account lock; conflicting reuse fails instead of performing another mutation. */
    fun replay(accountId: UUID, request: FinancialRequest, digest: String): Boolean
    fun configure(accountId: UUID, groupId: UUID, request: FinancialRequest, digest: String,
                  review: GroupReceivablesReview?, at: Instant): GroupReceivablesState
}

class ManageGroupReceivables(private val accounts: FinancialAccountRepository,
    private val administrators: GroupAdministrationDirectory, private val groups: GroupFinancialSetupLookup,
    private val store: GroupReceivablesStore, private val conditions: FinancialConditions,
    private val eligibility: ReceivablesEligibility, private val clock: Clock, private val rollout: ReceivablesRolloutAccess) {

    fun preview(accountId: UUID, groupId: UUID, request: FinancialRequest,
                methods: Set<PaymentMethod>): FinancialResult<GroupReceivablesReview> = safely(request) {
        store.transaction {
            val group = groups.lockActive(groupId) ?: return@transaction hidden(request)
            val account = store.lockAccount(accountId) ?: return@transaction hidden(request)
            val context = context(account, groupId, request)
            if (group.ownerUserId != account.ownerUserId || !FinancialAccess.permission(FinancialAction.READ, context).allowed) {
                return@transaction hidden(request)
            }
            FinancialResult.Success(review(group, context, methods), request.requestId)
        }
    }

    fun activate(accountId: UUID, groupId: UUID, request: FinancialRequest, methods: Set<PaymentMethod>,
                 fingerprint: String, accepted: Boolean): FinancialResult<GroupReceivablesState> = safely(request) {
        if (!accepted || methods.isEmpty() || !fingerprint.matches(Regex("[a-f0-9]{64}"))) {
            return@safely FinancialResult.Failure(FinancialError.INVALID_INPUT, request.requestId)
        }
        store.transaction {
            val group = groups.lockActive(groupId) ?: return@transaction hidden(request)
            val account = store.lockAccount(accountId) ?: return@transaction hidden(request)
            val context = context(account, groupId, request)
            if (group.ownerUserId != account.ownerUserId || !FinancialAccess.permission(FinancialAction.READ, context).allowed) {
                return@transaction hidden(request)
            }
            val digest = digest("ACTIVATE_GROUP:$groupId:${methods.sortedBy { it.ordinal }}:$fingerprint")
            if (store.replay(accountId, request, digest)) {
                return@transaction FinancialResult.Success(store.state(accountId, groupId)!!, request.requestId)
            }
            val permission = FinancialAccess.permission(FinancialAction.ACTIVATE_GROUP, context)
            if (!permission.allowed) return@transaction FinancialResult.Failure(when (permission.reason) {
                UnavailabilityReason.INELIGIBLE_PLAN -> FinancialError.INELIGIBLE_PLAN
                UnavailabilityReason.REGISTRATION_NOT_APPROVED -> FinancialError.REGISTRATION_RESTRICTED
                UnavailabilityReason.OPERATIONS_DISABLED -> FinancialError.OPERATIONS_DISABLED
                else -> FinancialError.UNAUTHORIZED
            }, request.requestId)
            val review = review(group, context, methods)
            if (review.fingerprint != fingerprint) throw FinancialRequestConflict()
            FinancialResult.Success(store.configure(accountId, groupId, request, digest, review, clock.instant()), request.requestId)
        }
    }

    fun deactivate(accountId: UUID, groupId: UUID, request: FinancialRequest): FinancialResult<GroupReceivablesState> = safely(request) {
        store.transaction {
            val account = store.lockAccount(accountId) ?: return@transaction hidden(request)
            if (!FinancialAccess.permission(FinancialAction.CANCEL, context(account, groupId, request, false)).allowed) {
                return@transaction hidden(request)
            }
            val state = store.state(accountId, groupId) ?: return@transaction hidden(request)
            val digest = digest("DEACTIVATE_GROUP:$groupId")
            if (store.replay(accountId, request, digest)) return@transaction FinancialResult.Success(state, request.requestId)
            FinancialResult.Success(store.configure(accountId, groupId, request, digest, null, clock.instant()), request.requestId)
        }
    }

    private fun context(account: FinancialAccount, groupId: UUID, request: FinancialRequest, checkEntitlement: Boolean = true) = FinancialAccessContext(
        account, request.actorUserId, accounts.findDelegation(account.id, request.actorUserId),
        request.actorUserId != account.ownerUserId && administrators.isAdministrator(account.ownerUserId, request.actorUserId),
        false, checkEntitlement && eligibility.forOwner(account.ownerUserId, clock.instant()).eligible,
        store.state(account.id, groupId)?.enabled == true,
        checkEntitlement && rollout.availability(account.ownerUserId).backendEnabled,
    )

    private fun review(group: GroupFinancialSetup, context: FinancialAccessContext, methods: Set<PaymentMethod>): GroupReceivablesReview {
        require(methods.isNotEmpty())
        val schedules = conditions.current(methods, clock.instant()).sortedBy { it.method.ordinal }
        if (schedules.map { it.method }.toSet() != methods) throw ConditionsUnavailable()
        val prices = listOf("GAME" to group.gameFeeCents, "MONTHLY" to group.monthlyFeeCents)
            .filter { it.second != null && it.second!! > 0 }.map { (kind, cents) ->
                GroupPriceQuotes(kind, cents!!, schedules.map { FeeCalculator.quote(cents, it) })
            }
        val canonical = "${context.account.id}:${group.groupId}:${group.gameFeeCents}:${group.monthlyFeeCents}:" + schedules.joinToString("|") {
            "${it.id}:${it.method}:${it.termsVersion}:${it.providerRate.stripTrailingZeros().toPlainString()}:${it.providerFixedCents}:" +
                "${it.commissionRate.stripTrailingZeros().toPlainString()}:${it.commissionFixedCents}"
        }
        return GroupReceivablesReview(store.state(context.account.id, group.groupId)
            ?: GroupReceivablesState(context.account.id, group.groupId, false, false, false), schedules, prices,
            FinancialAction.entries.associateWith { FinancialAccess.permission(it, context) },
            eligibility.forOwner(context.account.ownerUserId, clock.instant()).effectiveCutoffAt, digest(canonical))
    }

    private fun hidden(request: FinancialRequest) = FinancialResult.Failure(FinancialError.NOT_FOUND, request.requestId)
    private fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
    private class ConditionsUnavailable : RuntimeException()
    private fun <T> safely(request: FinancialRequest, block: () -> FinancialResult<T>): FinancialResult<T> = try { block() }
    catch (_: FinancialRequestConflict) { FinancialResult.Failure(FinancialError.CONFLICT, request.requestId) }
    catch (_: ConditionsUnavailable) { FinancialResult.Failure(FinancialError.CONFIGURATION_UNAVAILABLE, request.requestId) }
    catch (_: IllegalArgumentException) { FinancialResult.Failure(FinancialError.INVALID_INPUT, request.requestId) }
    catch (_: ArithmeticException) { FinancialResult.Failure(FinancialError.INVALID_INPUT, request.requestId) }
}
