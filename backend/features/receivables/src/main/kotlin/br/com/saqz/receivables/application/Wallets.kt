package br.com.saqz.receivables.application

import br.com.saqz.receivables.domain.FinancialAccess
import br.com.saqz.receivables.domain.FinancialAccessContext
import br.com.saqz.receivables.domain.FinancialAction
import br.com.saqz.receivables.domain.UnavailabilityReason
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class WalletBalance(
    val accountId: UUID,
    val availableBalanceCents: Long,
    val pendingReceivablesCents: Long,
    val refreshedAt: Instant,
)

data class WalletStatementItem(
    val id: String,
    val kind: String,
    val amountCents: Long,
    val balanceCents: Long,
    val occurredOn: LocalDate,
    val description: String,
)

data class WalletStatementPage(val items: List<WalletStatementItem>, val nextOffset: Int?)

enum class BankAccountType { CHECKING, SAVINGS }

data class BankDestinationDetails(
    val bankCode: String,
    val accountType: BankAccountType,
    val ownerName: String,
    val cpfCnpj: String,
    val agency: String,
    val account: String,
    val accountDigit: String,
) {
    fun isValid(): Boolean = bankCode.matches(Regex("[0-9]{3}")) && ownerName.trim().length in 2..120 &&
        cpfCnpj.filter(Char::isDigit).matches(Regex("[0-9]{11}|[0-9]{14}")) &&
        agency.matches(Regex("[0-9A-Za-z-]{1,12}")) && account.matches(Regex("[0-9A-Za-z-]{1,20}")) &&
        accountDigit.matches(Regex("[0-9A-Za-z]{1,2}"))
}

data class BankDestination(
    val id: UUID,
    val accountId: UUID,
    val details: BankDestinationDetails,
    val verifiedAt: Instant?,
    val disabledAt: Instant?,
)

enum class WithdrawalStatus { REQUESTED, UNKNOWN, PROCESSING, COMPLETED, REJECTED, CANCELLED }

data class Withdrawal(
    val id: UUID,
    val accountId: UUID,
    val destinationId: UUID,
    val operationId: UUID,
    val requestId: UUID,
    val amountCents: Long,
    val feeCents: Long,
    val status: WithdrawalStatus,
    val providerTransferId: String?,
)

data class WithdrawalClaim(val withdrawal: Withdrawal, val token: UUID, val recoveryOnly: Boolean,
                           val destination: BankDestination)

sealed interface ProviderWithdrawalResult {
    data class Known(val reference: String, val status: WithdrawalStatus, val feeCents: Long) : ProviderWithdrawalResult
    data class Rejected(val code: String, val reference: String? = null) : ProviderWithdrawalResult
    data object Unknown : ProviderWithdrawalResult
}

interface WalletProvider {
    fun balance(apiKey: String): Pair<Long, Long>
    fun statement(apiKey: String, offset: Int, limit: Int): WalletStatementPage
    fun withdraw(apiKey: String, operationId: UUID, amountCents: Long,
                 destination: BankDestinationDetails): ProviderWithdrawalResult
    fun recoverWithdrawal(apiKey: String, operationId: UUID): ProviderWithdrawalResult
}

interface WalletStore {
    fun listDestinations(accountId: UUID): List<BankDestination>
    fun findDestinationByRequest(accountId: UUID, requestId: UUID): BankDestination?
    fun saveDestination(accountId: UUID, request: FinancialRequest, details: BankDestinationDetails,
                        now: Instant): BankDestination
    fun prepareWithdrawal(accountId: UUID, request: FinancialRequest, destinationId: UUID,
                          amountCents: Long, availableBalanceCents: Long, now: Instant): Withdrawal
    fun claimWithdrawal(accountId: UUID, withdrawalId: UUID, now: Instant): WithdrawalClaim?
    fun finishWithdrawal(claim: WithdrawalClaim, result: ProviderWithdrawalResult, now: Instant): Withdrawal
    fun findWithdrawal(accountId: UUID, withdrawalId: UUID): Withdrawal?
    fun findWithdrawalByRequest(accountId: UUID, requestId: UUID): Withdrawal?
}

class WalletProviderFailure : RuntimeException()
class WalletInsufficientBalance : RuntimeException()
class WalletDestinationMismatch : RuntimeException()

class ManageWallet(
    private val accounts: FinancialAccountRepository,
    private val groups: ReceivablesGroups,
    private val onboarding: FinancialOnboardingStore,
    private val store: WalletStore,
    private val provider: WalletProvider,
) {
    fun balance(accountId: UUID, request: FinancialRequest, now: Instant): FinancialResult<WalletBalance> = safely(request) {
        val account = authorized(accountId, request.actorUserId) ?: return@safely hidden(request)
        val credentials = onboarding.credentials(account.id) ?: return@safely unavailable(request)
        val (available, pending) = provider.balance(credentials.apiKey)
        FinancialResult.Success(WalletBalance(account.id, available, pending, now), request.requestId)
    }

    fun statement(accountId: UUID, request: FinancialRequest, offset: Int, limit: Int): FinancialResult<WalletStatementPage> = safely(request) {
        val account = authorized(accountId, request.actorUserId) ?: return@safely hidden(request)
        if (offset < 0 || limit !in 1..100) return@safely invalid(request)
        val credentials = onboarding.credentials(account.id) ?: return@safely unavailable(request)
        FinancialResult.Success(provider.statement(credentials.apiKey, offset, limit), request.requestId)
    }

    fun destinations(accountId: UUID, request: FinancialRequest): FinancialResult<List<BankDestination>> {
        authorized(accountId, request.actorUserId) ?: return hidden(request)
        return FinancialResult.Success(store.listDestinations(accountId), request.requestId)
    }

    fun saveDestination(accountId: UUID, request: FinancialRequest, details: BankDestinationDetails,
                        recentlyAuthenticated: Boolean, now: Instant): FinancialResult<BankDestination> = safely(request) {
        val account = authorized(accountId, request.actorUserId) ?: return@safely hidden(request)
        val permission = FinancialAccess.permission(FinancialAction.CHANGE_BANK_DESTINATION,
            context(account, request.actorUserId, recentlyAuthenticated))
        if (!permission.allowed) return@safely denied(permission.reason, request)
        if (!details.isValid()) return@safely invalid(request)
        FinancialResult.Success(store.saveDestination(accountId, request, details, now), request.requestId)
    }

    fun recoverDestination(accountId: UUID, originalRequestId: UUID,
                           request: FinancialRequest): FinancialResult<BankDestination> {
        authorized(accountId, request.actorUserId) ?: return hidden(request)
        return store.findDestinationByRequest(accountId, originalRequestId)?.let {
            FinancialResult.Success(it, request.requestId)
        } ?: hidden(request)
    }

    fun withdraw(accountId: UUID, request: FinancialRequest, destinationId: UUID, amountCents: Long,
                 explicitlyAuthorized: Boolean, recentlyAuthenticated: Boolean, now: Instant): FinancialResult<Withdrawal> = safely(request) {
        val account = authorized(accountId, request.actorUserId) ?: return@safely hidden(request)
        val permission = FinancialAccess.permission(FinancialAction.WITHDRAW,
            context(account, request.actorUserId, recentlyAuthenticated))
        if (!permission.allowed) return@safely denied(permission.reason, request)
        if (!explicitlyAuthorized || amountCents <= 0) return@safely invalid(request)
        val credentials = onboarding.credentials(account.id) ?: return@safely unavailable(request)
        val available = provider.balance(credentials.apiKey).first
        val withdrawal = store.prepareWithdrawal(accountId, request, destinationId, amountCents, available, now)
        execute(credentials.apiKey, withdrawal, now, request)
    }

    fun recover(accountId: UUID, withdrawalId: UUID, request: FinancialRequest, now: Instant): FinancialResult<Withdrawal> = safely(request) {
        val account = authorized(accountId, request.actorUserId) ?: return@safely hidden(request)
        val withdrawal = store.findWithdrawal(accountId, withdrawalId) ?: return@safely hidden(request)
        if (withdrawal.status !in setOf(WithdrawalStatus.REQUESTED, WithdrawalStatus.UNKNOWN, WithdrawalStatus.PROCESSING)) {
            return@safely FinancialResult.Success(withdrawal, request.requestId)
        }
        val credentials = onboarding.credentials(account.id) ?: return@safely unavailable(request)
        execute(credentials.apiKey, withdrawal, now, request)
    }

    fun recoverByRequest(accountId: UUID, originalRequestId: UUID, request: FinancialRequest,
                         now: Instant): FinancialResult<Withdrawal> = safely(request) {
        val account = authorized(accountId, request.actorUserId) ?: return@safely hidden(request)
        val withdrawal = store.findWithdrawalByRequest(accountId, originalRequestId) ?: return@safely hidden(request)
        if (withdrawal.status !in setOf(WithdrawalStatus.REQUESTED, WithdrawalStatus.UNKNOWN, WithdrawalStatus.PROCESSING)) {
            return@safely FinancialResult.Success(withdrawal, request.requestId)
        }
        val credentials = onboarding.credentials(account.id) ?: return@safely unavailable(request)
        execute(credentials.apiKey, withdrawal, now, request)
    }

    private fun execute(apiKey: String, withdrawal: Withdrawal, now: Instant,
                        request: FinancialRequest): FinancialResult<Withdrawal> {
        val claim = store.claimWithdrawal(withdrawal.accountId, withdrawal.id, now)
            ?: return store.findWithdrawal(withdrawal.accountId, withdrawal.id)?.let {
                if (it.status == WithdrawalStatus.UNKNOWN || it.status == WithdrawalStatus.REQUESTED)
                    FinancialResult.Failure(FinancialError.RESULT_PENDING, request.requestId)
                else FinancialResult.Success(it, request.requestId)
            } ?: hidden(request)
        val result = try {
            if (claim.recoveryOnly) provider.recoverWithdrawal(apiKey, claim.withdrawal.operationId)
            else provider.withdraw(apiKey, claim.withdrawal.operationId, claim.withdrawal.amountCents,
                claim.destination.details)
        } catch (_: Exception) { ProviderWithdrawalResult.Unknown }
        val finished = store.finishWithdrawal(claim, result, now)
        return when {
            finished.status == WithdrawalStatus.UNKNOWN -> FinancialResult.Failure(FinancialError.RESULT_PENDING, request.requestId)
            finished.status == WithdrawalStatus.REJECTED && result is ProviderWithdrawalResult.Rejected &&
                result.code == "INSUFFICIENT_BALANCE" -> FinancialResult.Failure(FinancialError.INSUFFICIENT_BALANCE, request.requestId)
            finished.status == WithdrawalStatus.REJECTED -> FinancialResult.Failure(FinancialError.REGISTRATION_RESTRICTED, request.requestId)
            else -> FinancialResult.Success(finished, request.requestId)
        }
    }

    private fun authorized(accountId: UUID, actor: UUID) = accounts.findById(accountId)?.takeIf { account ->
        account.ownerUserId == actor || accounts.findDelegation(accountId, actor)?.let {
            it.revokedAt == null && groups.isCurrentAdministratorOfOwner(actor, account.ownerUserId)
        } == true
    }

    private fun context(account: br.com.saqz.receivables.domain.FinancialAccount, actor: UUID, recent: Boolean) =
        FinancialAccessContext(account, actor, accounts.findDelegation(account.id, actor),
            groups.isCurrentAdministratorOfOwner(actor, account.ownerUserId), recent,
            commerciallyEligible = true, groupEnabled = true, rolloutEnabled = true)

    private fun denied(reason: UnavailabilityReason?, request: FinancialRequest): FinancialResult.Failure = when (reason) {
        UnavailabilityReason.RECENT_AUTHENTICATION_REQUIRED -> FinancialResult.Failure(FinancialError.RECENT_AUTHENTICATION_REQUIRED, request.requestId)
        else -> hidden(request)
    }
    private fun hidden(request: FinancialRequest) = FinancialResult.Failure(FinancialError.NOT_FOUND, request.requestId)
    private fun invalid(request: FinancialRequest) = FinancialResult.Failure(FinancialError.INVALID_INPUT, request.requestId)
    private fun unavailable(request: FinancialRequest) = FinancialResult.Failure(FinancialError.CONFIGURATION_UNAVAILABLE, request.requestId)
    private fun <T> safely(request: FinancialRequest, block: () -> FinancialResult<T>): FinancialResult<T> = try { block() }
    catch (_: WalletInsufficientBalance) { FinancialResult.Failure(FinancialError.INSUFFICIENT_BALANCE, request.requestId) }
    catch (_: WalletDestinationMismatch) { hidden(request) }
    catch (_: FinancialRequestConflict) { FinancialResult.Failure(FinancialError.CONFLICT, request.requestId) }
    catch (_: WalletProviderFailure) { FinancialResult.Failure(FinancialError.PROVIDER_UNAVAILABLE, request.requestId) }
}
