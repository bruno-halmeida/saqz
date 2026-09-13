package br.com.saqz.receivables.application

import br.com.saqz.receivables.domain.FinancialAccount
import br.com.saqz.receivables.domain.FinancialDelegation
import br.com.saqz.receivables.domain.RegistrationStatus
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ManageWalletTest {
    private val now = Instant.parse("2026-09-13T12:00:00Z")
    private val owner = UUID.randomUUID()
    private val delegate = UUID.randomUUID()
    private val account = FinancialAccount(UUID.randomUUID(), owner, RegistrationStatus.APPROVED, false)
    private val destination = BankDestination(UUID.randomUUID(), account.id, details(), null, null)

    @Test
    fun `money remains readable and withdrawable after commercial cutoff`() {
        val fixture = fixture()
        val balance = fixture.service.balance(account.id, request(owner), now)
        assertEquals(12_345, assertIs<FinancialResult.Success<WalletBalance>>(balance).value.availableBalanceCents)

        val withdrawn = fixture.service.withdraw(account.id, request(owner), destination.id, 1_234,
            explicitlyAuthorized = true, recentlyAuthenticated = true, now)
        assertEquals(WithdrawalStatus.PROCESSING, assertIs<FinancialResult.Success<Withdrawal>>(withdrawn).value.status)
        assertEquals(1, fixture.provider.posts)
    }

    @Test
    fun `withdrawal requires server verified recent authentication and explicit consent`() {
        val fixture = fixture()
        val stale = fixture.service.withdraw(account.id, request(owner), destination.id, 1_00, true, false, now)
        assertEquals(FinancialError.RECENT_AUTHENTICATION_REQUIRED, assertIs<FinancialResult.Failure>(stale).error)
        val implicit = fixture.service.withdraw(account.id, request(owner), destination.id, 1_00, false, true, now)
        assertEquals(FinancialError.INVALID_INPUT, assertIs<FinancialResult.Failure>(implicit).error)
        assertEquals(0, fixture.provider.posts)
    }

    @Test
    fun `removed administrator cannot access delegated account`() {
        val fixture = fixture(admin = false)
        val response = fixture.service.balance(account.id, request(delegate), now)
        assertEquals(FinancialError.NOT_FOUND, assertIs<FinancialResult.Failure>(response).error)
        assertEquals(0, fixture.provider.balanceReads)
    }

    @Test
    fun `uncertain withdrawal is recovered without a second post`() {
        val fixture = fixture()
        fixture.provider.withdrawal = ProviderWithdrawalResult.Unknown
        val request = request(owner)
        val first = fixture.service.withdraw(account.id, request, destination.id, 2_00, true, true, now)
        assertEquals(FinancialError.RESULT_PENDING, assertIs<FinancialResult.Failure>(first).error)
        fixture.provider.recovery = ProviderWithdrawalResult.Known("transfer-1", WithdrawalStatus.COMPLETED, 99)
        val recovered = fixture.service.recoverByRequest(account.id, request.requestId, request(owner), now.plusSeconds(61))
        assertEquals(WithdrawalStatus.COMPLETED, assertIs<FinancialResult.Success<Withdrawal>>(recovered).value.status)
        assertEquals(1, fixture.provider.posts)
        assertEquals(1, fixture.provider.recoveries)
    }

    @Test
    fun `processing transfer advances by provider query without repeating withdrawal`() {
        val f = fixture(); val original = request(owner)
        f.service.withdraw(account.id, original, destination.id, 12345, true, true, now)
        f.provider.recovery = ProviderWithdrawalResult.Known("transfer", WithdrawalStatus.COMPLETED, 173)
        val result = f.service.recoverByRequest(account.id, original.requestId, request(owner), now.plusSeconds(61))
        val transfer = assertIs<FinancialResult.Success<Withdrawal>>(result).value
        assertEquals(WithdrawalStatus.COMPLETED, transfer.status)
        assertEquals(12345, transfer.amountCents); assertEquals(173, transfer.feeCents)
        assertEquals(1, f.provider.posts); assertEquals(1, f.provider.recoveries)
    }

    @Test
    fun `same request id never creates a second withdrawal and changed amount conflicts`() {
        val fixture = fixture()
        val request = request(owner)
        fixture.service.withdraw(account.id, request, destination.id, 5_00, true, true, now)
        fixture.service.withdraw(account.id, request, destination.id, 5_00, true, true, now.plusSeconds(1))
        assertEquals(1, fixture.store.created)
        val conflict = fixture.service.withdraw(account.id, request, destination.id, 6_00, true, true, now.plusSeconds(2))
        assertEquals(FinancialError.CONFLICT, assertIs<FinancialResult.Failure>(conflict).error)
    }

    @Test
    fun `local reservations reject concurrent amount above exact remote cents`() {
        val fixture = fixture()
        fixture.provider.withdrawal = ProviderWithdrawalResult.Unknown
        fixture.service.withdraw(account.id, request(owner), destination.id, 8_000, true, true, now)
        val second = fixture.service.withdraw(account.id, request(owner), destination.id, 4_346, true, true, now)
        assertEquals(FinancialError.INSUFFICIENT_BALANCE, assertIs<FinancialResult.Failure>(second).error)
    }

    @Test
    fun `revocation during balance read prevents reservation and provider post`() {
        val f = fixture()
        f.provider.afterBalance = f.revoke
        val result = f.service.withdraw(account.id, request(delegate), destination.id, 12345, true, true, now)
        assertEquals(FinancialError.NOT_FOUND, assertIs<FinancialResult.Failure>(result).error)
        assertEquals(0, f.store.created)
        assertEquals(0, f.provider.posts)
    }

    @Test
    fun `revocation during claim releases unsubmitted withdrawal without provider post`() {
        val f = fixture()
        f.store.afterClaim = f.revoke
        val result = f.service.withdraw(account.id, request(delegate), destination.id, 12345, true, true, now)
        assertEquals(FinancialError.NOT_FOUND, assertIs<FinancialResult.Failure>(result).error)
        assertEquals(12345, f.store.onlyWithdrawal().amountCents)
        assertEquals(WithdrawalStatus.REJECTED, f.store.onlyWithdrawal().status)
        assertEquals(0, f.provider.posts)
    }

    @Test
    fun `bank destination requires recent authentication before persistence or provider IO`() {
        val f = fixture()
        val result = f.service.saveDestination(account.id, request(owner), details(), false, now)
        assertEquals(FinancialError.RECENT_AUTHENTICATION_REQUIRED, assertIs<FinancialResult.Failure>(result).error)
        assertEquals(0, f.store.destinationSaves)
        assertEquals(0, f.provider.posts)
        assertEquals(0, f.provider.balanceReads)
        assertEquals(0, f.provider.recoveries)
    }

    private fun fixture(admin: Boolean = true): Fixture {
        var currentAdmin = admin
        val accounts = object : FinancialAccountRepository {
            override fun listForUser(userId: UUID) = listOf(account)
            override fun findById(accountId: UUID) = account.takeIf { it.id == accountId }
            override fun findByOwner(ownerUserId: UUID) = account.takeIf { it.ownerUserId == ownerUserId }
            override fun findDelegation(accountId: UUID, userId: UUID) =
                FinancialDelegation(accountId, userId, now).takeIf { accountId == account.id && userId == delegate }
        }
        val groups = object : ReceivablesGroups {
            override fun isOwner(groupId: UUID, userId: UUID) = false
            override fun isCurrentAdministratorOfOwner(userId: UUID, ownerUserId: UUID) = currentAdmin
        }
        val onboarding = object : FinancialOnboardingStore {
            override fun begin(request: FinancialRequest, termsVersion: String, registration: LegalRegistration, now: Instant) = account
            override fun findOwned(ownerUserId: UUID) = account
            override fun creationOperation(accountId: UUID) = error("unused")
            override fun registration(accountId: UUID) = error("unused")
            override fun credentials(accountId: UUID) = AccountCredentials("secret", now, "provider")
            override fun saveProviderAccount(accountId: UUID, providerAccount: ProviderAccount, now: Instant) = Unit
            override fun updateStatus(accountId: UUID, status: RegistrationStatus, now: Instant) = Unit
        }
        val store = MemoryWalletStore(destination)
        val provider = FakeWalletProvider()
        return Fixture(ManageWallet(accounts, groups, onboarding, store, provider), store, provider) { currentAdmin = false }
    }

    private fun request(actor: UUID) = FinancialRequest(UUID.randomUUID(), actor)
    private fun details() = BankDestinationDetails("001", BankAccountType.CHECKING, "Maria Silva",
        "12345678901", "1234", "98765", "0")
    private data class Fixture(val service: ManageWallet, val store: MemoryWalletStore, val provider: FakeWalletProvider, val revoke: () -> Unit)

    private class FakeWalletProvider : WalletProvider {
        var afterBalance: () -> Unit = {}
        var posts = 0; var recoveries = 0; var balanceReads = 0
        var withdrawal: ProviderWithdrawalResult = ProviderWithdrawalResult.Known("transfer", WithdrawalStatus.PROCESSING, 0)
        var recovery: ProviderWithdrawalResult = ProviderWithdrawalResult.Unknown
        override fun balance(apiKey: String) = (12_345L to 6_789L).also { balanceReads++; afterBalance() }
        override fun statement(apiKey: String, offset: Int, limit: Int) = WalletStatementPage(emptyList(), null)
        override fun withdraw(apiKey: String, operationId: UUID, amountCents: Long, destination: BankDestinationDetails) = withdrawal.also { posts++ }
        override fun recoverWithdrawal(apiKey: String, operationId: UUID) = recovery.also { recoveries++ }
    }

    private class MemoryWalletStore(private val destination: BankDestination) : WalletStore {
        private val withdrawals = mutableMapOf<UUID, Withdrawal>()
        private val requests = mutableMapOf<UUID, Withdrawal>()
        var created = 0
        var destinationSaves = 0
        var afterClaim: () -> Unit = {}
        override fun listDestinations(accountId: UUID) = listOf(destination)
        override fun findDestinationByRequest(accountId: UUID, requestId: UUID) = destination.takeIf { it.accountId == accountId }
        override fun saveDestination(accountId: UUID, request: FinancialRequest, details: BankDestinationDetails, now: Instant) = destination.also { destinationSaves++ }
        override fun prepareWithdrawal(accountId: UUID, request: FinancialRequest, destinationId: UUID, amountCents: Long,
            availableBalanceCents: Long, now: Instant): Withdrawal {
            requests[request.requestId]?.let { if (it.amountCents != amountCents || it.destinationId != destinationId) throw FinancialRequestConflict(); return it }
            val reserved = withdrawals.values.filter { it.status == WithdrawalStatus.UNKNOWN || it.status == WithdrawalStatus.REQUESTED }.sumOf { it.amountCents }
            if (amountCents + reserved > availableBalanceCents) throw WalletInsufficientBalance()
            return Withdrawal(UUID.randomUUID(), accountId, destinationId, UUID.randomUUID(), request.requestId,
                amountCents, 0, WithdrawalStatus.REQUESTED, null).also { withdrawals[it.id] = it; requests[request.requestId] = it; created++ }
        }
        override fun claimWithdrawal(accountId: UUID, withdrawalId: UUID, now: Instant): WithdrawalClaim? = withdrawals[withdrawalId]?.let {
            if (it.status !in setOf(WithdrawalStatus.REQUESTED, WithdrawalStatus.UNKNOWN, WithdrawalStatus.PROCESSING)) null
            else WithdrawalClaim(it, UUID.randomUUID(), it.status != WithdrawalStatus.REQUESTED, destination).also { afterClaim() }
        }
        override fun finishWithdrawal(claim: WithdrawalClaim, result: ProviderWithdrawalResult, now: Instant): Withdrawal {
            val value = when (result) {
                is ProviderWithdrawalResult.Known -> claim.withdrawal.copy(status = result.status, providerTransferId = result.reference, feeCents = result.feeCents)
                is ProviderWithdrawalResult.Rejected -> claim.withdrawal.copy(status = WithdrawalStatus.REJECTED, providerTransferId = result.reference)
                ProviderWithdrawalResult.Unknown -> claim.withdrawal.copy(status = WithdrawalStatus.UNKNOWN)
            }
            withdrawals[value.id] = value; requests[value.requestId] = value; return value
        }
        override fun findWithdrawal(accountId: UUID, withdrawalId: UUID) = withdrawals[withdrawalId]?.takeIf { it.accountId == accountId }
        override fun findWithdrawalByRequest(accountId: UUID, requestId: UUID) = requests[requestId]?.takeIf { it.accountId == accountId }
        fun onlyWithdrawal() = withdrawals.values.single()
    }
}
