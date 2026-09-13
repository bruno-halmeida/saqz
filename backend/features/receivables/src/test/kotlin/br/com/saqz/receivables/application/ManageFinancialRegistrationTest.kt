package br.com.saqz.receivables.application

import br.com.saqz.receivables.domain.*
import br.com.saqz.sharedkernel.group.GroupAdministrationDirectory
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class ManageFinancialRegistrationTest {
    private val accountId = UUID.randomUUID()
    private val owner = UUID.randomUUID()
    private val delegate = UUID.randomUUID()
    private val now = Instant.parse("2026-09-13T12:00:00Z")
    private val account = FinancialAccount(accountId, owner, RegistrationStatus.CORRECTION_REQUIRED, false)
    private val correction = RegistrationCorrection("novo@example.test", null, "11999999999", null, 250001,
        "01001000", "Rua Nova", "10", null, "Centro")

    @Test fun `owner and current delegate correct allowed fields while revoked delegate is denied before provider IO`() {
        val fixture = Fixture()
        assertIs<FinancialResult.Success<RegistrationCorrectionResult>>(fixture.service.correct(accountId,
            FinancialRequest(UUID.randomUUID(), owner), correction, now))
        assertEquals(1, fixture.provider.posts)
        fixture.provider.current = commercial(correction)
        assertIs<FinancialResult.Success<RegistrationCorrectionResult>>(fixture.service.correct(accountId,
            FinancialRequest(UUID.randomUUID(), delegate), correction, now))
        fixture.admin = false
        assertEquals(FinancialError.NOT_FOUND, assertIs<FinancialResult.Failure>(fixture.service.correct(accountId,
            FinancialRequest(UUID.randomUUID(), delegate), correction, now)).error)
        assertEquals(1, fixture.provider.posts)
    }

    @Test fun `revocation between read and post prevents mutation in the same open session`() {
        val fixture = Fixture()
        fixture.provider.afterRead = { fixture.admin = false }
        val result = fixture.service.correct(accountId, FinancialRequest(UUID.randomUUID(), delegate), correction, now)
        assertEquals(FinancialError.NOT_FOUND, assertIs<FinancialResult.Failure>(result).error)
        assertEquals(0, fixture.provider.posts)
    }

    @Test fun `unknown update recovers by read without sending a second post and preserves request id`() {
        val fixture = Fixture()
        val requestId = UUID.randomUUID()
        fixture.provider.failPost = true
        val first = fixture.service.correct(accountId, FinancialRequest(requestId, owner), correction, now)
        assertEquals(FinancialError.RESULT_PENDING, assertIs<FinancialResult.Failure>(first).error)
        assertEquals(requestId, first.requestId); assertEquals(1, fixture.provider.posts)
        val replay = fixture.service.correct(accountId, FinancialRequest(requestId, owner), correction, now)
        assertEquals(FinancialError.RESULT_PENDING, assertIs<FinancialResult.Failure>(replay).error)
        assertEquals(1, fixture.provider.posts)
        fixture.provider.failPost = false; fixture.provider.current = commercial(correction)
        val recovered = assertIs<FinancialResult.Success<RegistrationCorrectionResult>>(fixture.service.recover(accountId,
            FinancialRequest(requestId, owner), now))
        assertEquals(requestId, recovered.requestId); assertEquals(RegistrationCorrectionStatus.SUCCEEDED, recovered.value.status)
        assertEquals(1, fixture.provider.posts)
    }

    @Test fun `same request with changed money is conflict and invalid fields never persist`() {
        val fixture = Fixture(); val requestId = UUID.randomUUID()
        fixture.provider.failPost = true
        fixture.service.correct(accountId, FinancialRequest(requestId, owner), correction, now)
        val changed = RegistrationCorrection(correction.email, null, correction.mobilePhone, null, 1,
            correction.postalCode, correction.address, correction.addressNumber, null, correction.province)
        assertEquals(FinancialError.CONFLICT, assertIs<FinancialResult.Failure>(fixture.service.correct(accountId,
            FinancialRequest(requestId, owner), changed, now)).error)
        assertEquals(FinancialError.INVALID_INPUT, assertIs<FinancialResult.Failure>(fixture.service.correct(accountId,
            FinancialRequest(UUID.randomUUID(), owner), RegistrationCorrection("bad", null, "x", null, -1,
                "x", "", "", null, ""), now)).error)
        assertEquals(1, fixture.store.operations.size)
    }

    @Test fun `recovery does not confirm changed legal identity even when all correction fields match`() {
        val f = Fixture(); val request = FinancialRequest(UUID.randomUUID(), owner)
        f.provider.failPost = true
        f.service.correct(accountId, request, correction, now)
        f.provider.current = CommercialRegistration("FISICA", "12345678901", "1990-01-01", null,
            "Changed legal name", "Changed regime", correction)
        assertEquals(FinancialError.RESULT_PENDING,
            assertIs<FinancialResult.Failure>(f.service.recover(accountId, request, now)).error)
        assertEquals(1, f.provider.posts)
        assertEquals(RegistrationCorrectionStatus.UNKNOWN, f.store.find(accountId, request.requestId)!!.status)
    }

    private inner class Fixture {
        var admin = true
        val repository = object : FinancialAccountRepository {
            override fun listForUser(userId: UUID) = listOf(account)
            override fun findById(accountId: UUID) = account.takeIf { accountId == this@ManageFinancialRegistrationTest.accountId }
            override fun findByOwner(ownerUserId: UUID) = account.takeIf { ownerUserId == owner }
            override fun findDelegation(accountId: UUID, userId: UUID) = FinancialDelegation(accountId, userId, now)
                .takeIf { accountId == this@ManageFinancialRegistrationTest.accountId && userId == delegate }
        }
        val groups = object : GroupAdministrationDirectory {
            override fun administrators(ownerUserId: UUID) = emptyList<br.com.saqz.sharedkernel.group.GroupAdministrator>()
            override fun ownerOf(groupId: UUID): UUID? = null
            override fun isAdministrator(ownerUserId: UUID, userId: UUID) = admin && ownerUserId == owner && userId == delegate
        }
        val onboarding = object : FinancialOnboardingStore {
            override fun begin(request: FinancialRequest, termsVersion: String, registration: LegalRegistration, now: Instant) = account
            override fun findOwned(ownerUserId: UUID) = account
            override fun creationOperation(accountId: UUID): FinancialOperation = error("unused")
            override fun registration(accountId: UUID): LegalRegistration = error("unused")
            override fun credentials(accountId: UUID) = AccountCredentials("secret", now, "provider")
            override fun saveProviderAccount(accountId: UUID, providerAccount: ProviderAccount, now: Instant) = Unit
            override fun updateStatus(accountId: UUID, status: RegistrationStatus, now: Instant) = Unit
        }
        val store = MemoryStore()
        val provider = FakeProvider()
        val service = ManageFinancialRegistration(repository, groups, onboarding, store, provider)
    }

    private inner class MemoryStore : FinancialManagementStore {
        override fun recordIdentitySnapshot(accountId: UUID, requestId: UUID, registration: CommercialRegistration) {
            val key = accountId to requestId
            operations[key] = operations.getValue(key).copy(identitySnapshot = registration)
        }
        val operations = mutableMapOf<Pair<UUID, UUID>, StoredRegistrationCorrection>()
        override fun begin(accountId: UUID, request: FinancialRequest, correction: RegistrationCorrection, now: Instant): StoredRegistrationCorrection {
            val key = accountId to request.requestId; val existing = operations[key]
            if (existing != null && (existing.actorUserId != request.actorUserId || existing.correction.incomeCents != correction.incomeCents))
                throw FinancialRequestConflict()
            return existing?.copy(shouldExecute = false) ?: StoredRegistrationCorrection(accountId, request.requestId,
                request.actorUserId, correction, RegistrationCorrectionStatus.READY, shouldExecute = true)
                .also { operations[key] = it.copy(shouldExecute = false) }
        }
        override fun find(accountId: UUID, requestId: UUID) = operations[accountId to requestId]
        override fun mark(accountId: UUID, requestId: UUID, status: RegistrationCorrectionStatus, now: Instant) {
            val key = accountId to requestId; operations[key] = operations.getValue(key).copy(status = status)
        }
    }
    private inner class FakeProvider : FinancialAccountsProvider {
        var current = commercial(RegistrationCorrection("old@example.test", null, "11888888888", null, 100,
            "01001000", "Rua Antiga", "1", null, "Centro"))
        var posts = 0; var failPost = false; var afterRead: () -> Unit = {}
        override fun readCommercialInfo(apiKey: String) = current.also { afterRead() }
        override fun updateCommercialInfo(apiKey: String, commercial: CommercialRegistration): CommercialRegistration {
            posts++; if (failPost) throw FinancialProviderUnavailable(); current = commercial; return commercial
        }
    }
    private fun commercial(c: RegistrationCorrection) = CommercialRegistration("FISICA", "12345678901",
        "1990-01-01", null, null, null, c)
}
