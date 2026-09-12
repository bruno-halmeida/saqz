package br.com.saqz.receivables.application

import br.com.saqz.receivables.domain.FinancialAccount
import br.com.saqz.receivables.domain.RegistrationStatus
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Sensitive values deliberately have no generated toString/copy/component methods. */
class LegalRegistration(
    val name: String, val email: String, val cpfCnpj: String, val mobilePhone: String,
    val incomeCents: Long, val address: String, val addressNumber: String, val province: String,
    val postalCode: String, val birthDate: LocalDate? = null, val companyType: String? = null,
) {
    fun isValid(): Boolean = name.isNotBlank() && email.contains('@') &&
        cpfCnpj.matches(Regex("[0-9]{11}|[0-9]{14}")) && mobilePhone.matches(Regex("[0-9]{10,13}")) &&
        incomeCents >= 0 && address.isNotBlank() && addressNumber.isNotBlank() && province.isNotBlank() &&
        postalCode.matches(Regex("[0-9]{8}")) &&
        (if (cpfCnpj.length == 11) birthDate != null && companyType == null
        else birthDate == null && companyType in setOf("MEI", "LIMITED", "INDIVIDUAL", "ASSOCIATION"))
}

class ProviderAccount(val id: String, val walletId: String, val apiKey: String)
data class FinancialDocument(val id: String, val type: String, val status: String, val onboardingUrl: String?)
class AccountCredentials(val apiKey: String, val createdAt: Instant, val providerAccountId: String)

interface FinancialOnboardingProvider {
    val canCreateAccounts: Boolean
    fun create(registration: LegalRegistration): ProviderAccount
    fun documents(apiKey: String): List<FinancialDocument>
    fun status(apiKey: String): RegistrationStatus
    fun upload(apiKey: String, documentId: String, type: String, contentType: String, bytes: ByteArray)
}

interface FinancialOnboardingStore {
    /** Atomically persists owner, accepted terms and the unique creation operation. */
    fun begin(request: FinancialRequest, termsVersion: String, registration: LegalRegistration, now: Instant): FinancialAccount
    fun findOwned(ownerUserId: UUID): FinancialAccount?
    fun creationOperation(accountId: UUID): FinancialOperation
    fun registration(accountId: UUID): LegalRegistration
    fun credentials(accountId: UUID): AccountCredentials?
    fun saveProviderAccount(accountId: UUID, providerAccount: ProviderAccount, now: Instant)
    fun updateStatus(accountId: UUID, status: RegistrationStatus, now: Instant)
}

class FinancialTermsUnavailable : RuntimeException("Financial terms unavailable")

class OnboardFinancialAccount(
    private val store: FinancialOnboardingStore,
    private val operations: FinancialOperationStore,
    private val provider: FinancialOnboardingProvider,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun begin(request: FinancialRequest, accepted: Boolean, termsVersion: String,
              registration: LegalRegistration, now: Instant): FinancialResult<FinancialAccount> {
        if (!accepted || termsVersion.isBlank() || !registration.isValid()) {
            return FinancialResult.Failure(FinancialError.INVALID_INPUT, request.requestId)
        }
        if (!provider.canCreateAccounts && store.findOwned(request.actorUserId) == null) {
            return FinancialResult.Failure(FinancialError.PROVIDER_UNAVAILABLE, request.requestId)
        }
        return try {
            FinancialResult.Success(store.begin(request, termsVersion, registration, now), request.requestId)
        } catch (_: FinancialTermsUnavailable) {
            FinancialResult.Failure(FinancialError.INVALID_INPUT, request.requestId)
        } catch (_: FinancialRequestConflict) {
            FinancialResult.Failure(FinancialError.CONFLICT, request.requestId)
        }
    }

    fun provision(accountId: UUID, now: Instant): Boolean {
        if (!provider.canCreateAccounts && store.credentials(accountId) == null) return false
        val operation = store.creationOperation(accountId)
        val gateway = object : FinancialOperationProvider {
            override fun execute(operation: FinancialOperation): ProviderOperationResult {
                val result = provider.create(store.registration(operation.accountId))
                store.saveProviderAccount(operation.accountId, result, clock.instant())
                return ProviderOperationResult.Confirmed(result.id)
            }
            override fun recover(operation: FinancialOperation): ProviderOperationResult =
                if (store.credentials(operation.accountId) != null) {
                    ProviderOperationResult.Confirmed(store.credentials(operation.accountId)!!.providerAccountId)
                } else ProviderOperationResult.Unknown
        }
        return RunFinancialOperation(operations, gateway).run(accountId, operation.id, now)
    }

    fun refresh(request: FinancialRequest, now: Instant): FinancialResult<List<FinancialDocument>> {
        val account = store.findOwned(request.actorUserId)
            ?: return FinancialResult.Failure(FinancialError.NOT_FOUND, request.requestId)
        val credentials = store.credentials(account.id)
            ?: return FinancialResult.Failure(FinancialError.RESULT_PENDING, request.requestId)
        if (now < credentials.createdAt.plusSeconds(15)) {
            return FinancialResult.Failure(FinancialError.RESULT_PENDING, request.requestId)
        }
        return try {
            val documents = provider.documents(credentials.apiKey)
            store.updateStatus(account.id, provider.status(credentials.apiKey), now)
            FinancialResult.Success(documents, request.requestId)
        } catch (_: Exception) {
            FinancialResult.Failure(FinancialError.PROVIDER_UNAVAILABLE, request.requestId)
        }
    }

    fun upload(request: FinancialRequest, documentId: String, type: String, contentType: String,
               bytes: ByteArray, now: Instant): FinancialResult<Unit> {
        if (bytes.isEmpty() || bytes.size > 5 * 1024 * 1024 ||
            contentType !in setOf("application/pdf", "image/jpeg", "image/png")) {
            return FinancialResult.Failure(FinancialError.INVALID_INPUT, request.requestId)
        }
        val documents = refresh(request, now)
        if (documents is FinancialResult.Failure) return documents
        documents as FinancialResult.Success
        val target = documents.value.singleOrNull { it.id == documentId }
        if (target == null || target.onboardingUrl != null) {
            return FinancialResult.Failure(FinancialError.INVALID_INPUT, request.requestId)
        }
        val account = store.findOwned(request.actorUserId)!!
        val credentials = store.credentials(account.id)!!
        return try {
            provider.upload(credentials.apiKey, documentId, type, contentType, bytes)
            FinancialResult.Success(Unit, request.requestId)
        } catch (_: Exception) {
            FinancialResult.Failure(FinancialError.RESULT_PENDING, request.requestId)
        }
    }
}
