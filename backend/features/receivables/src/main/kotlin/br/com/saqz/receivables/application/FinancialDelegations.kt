package br.com.saqz.receivables.application

import br.com.saqz.receivables.domain.FinancialAccount
import br.com.saqz.receivables.domain.FinancialDelegation
import br.com.saqz.sharedkernel.group.GroupAdministrationDirectory
import java.time.Instant
import java.util.UUID

interface FinancialDelegationStore {
    fun <T> transaction(block: () -> T): T
    fun lockAccount(accountId: UUID): FinancialAccount?
    fun list(accountId: UUID): List<FinancialDelegation>
    fun grant(accountId: UUID, request: FinancialRequest, userId: UUID, termsVersion: String, now: Instant): FinancialDelegation
    fun revoke(accountId: UUID, request: FinancialRequest, userId: UUID, now: Instant)
}

class ManageFinancialDelegations(
    private val accounts: FinancialAccountRepository,
    private val groups: GroupAdministrationDirectory,
    private val store: FinancialDelegationStore,
) {
    fun accounts(request: FinancialRequest): FinancialResult<List<FinancialAccount>> = FinancialResult.Success(
        accounts.listForUser(request.actorUserId).filter {
            it.ownerUserId == request.actorUserId || groups.isAdministrator(it.ownerUserId, request.actorUserId)
        }, request.requestId,
    )

    fun list(accountId: UUID, request: FinancialRequest): FinancialResult<List<FinancialDelegation>> {
        val account = accounts.findById(accountId) ?: return denied(request)
        if (account.ownerUserId != request.actorUserId) {
            val delegation = accounts.findDelegation(accountId, request.actorUserId)
            if (delegation == null || delegation.revokedAt != null ||
                !groups.isAdministrator(account.ownerUserId, request.actorUserId)) return denied(request)
        }
        return FinancialResult.Success(store.list(accountId), request.requestId)
    }

    fun grant(accountId: UUID, request: FinancialRequest, userId: UUID, termsVersion: String,
              acknowledgedWholeAccount: Boolean, now: Instant): FinancialResult<FinancialDelegation> = safely(request) {
        store.transaction {
            val account = store.lockAccount(accountId) ?: return@transaction denied(request)
            if (account.ownerUserId != request.actorUserId) return@transaction denied(request)
            if (!acknowledgedWholeAccount || userId == account.ownerUserId ||
                !groups.isAdministrator(account.ownerUserId, userId)) {
                return@transaction FinancialResult.Failure(FinancialError.INVALID_INPUT, request.requestId)
            }
            FinancialResult.Success(store.grant(accountId, request, userId, termsVersion, now), request.requestId)
        }
    }

    fun revoke(accountId: UUID, request: FinancialRequest, userId: UUID, now: Instant): FinancialResult<Unit> = safely(request) {
        store.transaction {
            val account = store.lockAccount(accountId) ?: return@transaction denied(request)
            if (account.ownerUserId != request.actorUserId) return@transaction denied(request)
            store.revoke(accountId, request, userId, now)
            FinancialResult.Success(Unit, request.requestId)
        }
    }

    private fun denied(request: FinancialRequest) = FinancialResult.Failure(FinancialError.NOT_FOUND, request.requestId)
    private fun <T> safely(request: FinancialRequest, block: () -> FinancialResult<T>): FinancialResult<T> = try { block() }
    catch (_: FinancialTermsUnavailable) { FinancialResult.Failure(FinancialError.INVALID_INPUT, request.requestId) }
    catch (_: FinancialRequestConflict) { FinancialResult.Failure(FinancialError.CONFLICT, request.requestId) }
}
