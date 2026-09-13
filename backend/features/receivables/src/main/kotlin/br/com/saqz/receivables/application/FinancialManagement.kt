package br.com.saqz.receivables.application

import br.com.saqz.receivables.domain.FinancialAccount
import br.com.saqz.sharedkernel.group.GroupAdministrationDirectory
import java.time.Instant
import java.util.UUID

/** Mutable commercial fields supported by Asaas. Legal identity is deliberately absent. */
class RegistrationCorrection(
    val email: String,
    val phone: String?,
    val mobilePhone: String,
    val site: String?,
    val incomeCents: Long,
    val postalCode: String,
    val address: String,
    val addressNumber: String,
    val complement: String?,
    val province: String,
) {
    fun isValid() = email.length in 3..200 && email.contains('@') &&
        phone?.let { it.matches(Regex("[0-9]{10,13}")) } != false &&
        mobilePhone.matches(Regex("[0-9]{10,13}")) &&
        site?.let { it.length <= 200 && (it.startsWith("https://") || it.startsWith("http://")) } != false &&
        incomeCents >= 0 && postalCode.matches(Regex("[0-9]{8}")) &&
        address.isNotBlank() && address.length <= 200 && addressNumber.isNotBlank() && addressNumber.length <= 30 &&
        complement?.length?.let { it <= 100 } != false && province.isNotBlank() && province.length <= 100

    override fun toString() = "RegistrationCorrection(redacted)"
}

class CommercialRegistration(
    val personType: String,
    val cpfCnpj: String,
    val birthDate: String?,
    val companyType: String?,
    val companyName: String?,
    val taxRegime: String?,
    val correction: RegistrationCorrection,
) {
    override fun toString() = "CommercialRegistration(redacted)"
}

enum class FinancialManagementRole { OWNER, DELEGATE }
enum class RegistrationCorrectionStatus { READY, UNKNOWN, SUCCEEDED, REJECTED }
data class FinancialManagementView(
    val accountId: UUID,
    val role: FinancialManagementRole,
    val correction: RegistrationCorrection,
)
data class RegistrationCorrectionResult(val accountId: UUID, val status: RegistrationCorrectionStatus)
data class StoredRegistrationCorrection(
    val accountId: UUID,
    val requestId: UUID,
    val actorUserId: UUID,
    val correction: RegistrationCorrection,
    val status: RegistrationCorrectionStatus,
    val shouldExecute: Boolean = false,
    val identitySnapshot: CommercialRegistration? = null,
)

interface FinancialAccountsProvider {
    fun readCommercialInfo(apiKey: String): CommercialRegistration
    fun updateCommercialInfo(apiKey: String, commercial: CommercialRegistration): CommercialRegistration
}

interface FinancialManagementStore {
    fun recordIdentitySnapshot(accountId: UUID, requestId: UUID, registration: CommercialRegistration)
    fun begin(accountId: UUID, request: FinancialRequest, correction: RegistrationCorrection, now: Instant): StoredRegistrationCorrection
    fun find(accountId: UUID, requestId: UUID): StoredRegistrationCorrection?
    fun mark(accountId: UUID, requestId: UUID, status: RegistrationCorrectionStatus, now: Instant)
}

class FinancialProviderRejected : RuntimeException()
class FinancialProviderUnavailable : RuntimeException()

class ManageFinancialRegistration(
    private val accounts: FinancialAccountRepository,
    private val groups: GroupAdministrationDirectory,
    private val onboarding: FinancialOnboardingStore,
    private val store: FinancialManagementStore,
    private val provider: FinancialAccountsProvider,
) {
    fun view(accountId: UUID, request: FinancialRequest): FinancialResult<FinancialManagementView> {
        val access = access(accountId, request.actorUserId) ?: return denied(request)
        val credentials = onboarding.credentials(accountId) ?: return pending(request)
        return try {
            FinancialResult.Success(FinancialManagementView(accountId, access.second,
                provider.readCommercialInfo(credentials.apiKey).correction), request.requestId)
        } catch (_: Exception) {
            FinancialResult.Failure(FinancialError.PROVIDER_UNAVAILABLE, request.requestId)
        }
    }

    fun correct(accountId: UUID, request: FinancialRequest, correction: RegistrationCorrection, now: Instant):
        FinancialResult<RegistrationCorrectionResult> {
        if (!correction.isValid()) return FinancialResult.Failure(FinancialError.INVALID_INPUT, request.requestId)
        if (access(accountId, request.actorUserId) == null) return denied(request)
        val operation = try { store.begin(accountId, request, correction, now) }
        catch (_: FinancialRequestConflict) { return FinancialResult.Failure(FinancialError.CONFLICT, request.requestId) }
        if (operation.status == RegistrationCorrectionStatus.SUCCEEDED || operation.status == RegistrationCorrectionStatus.REJECTED) {
            return terminal(operation, request)
        }
        if (!operation.shouldExecute) return pending(request)
        val credentials = onboarding.credentials(accountId) ?: return pending(request)
        return try {
            val current = provider.readCommercialInfo(credentials.apiKey)
            store.recordIdentitySnapshot(accountId, request.requestId, current)
            if (current.correction.sameValues(correction)) {
                store.mark(accountId, request.requestId, RegistrationCorrectionStatus.SUCCEEDED, now)
                success(accountId, request)
            } else {
                // Revocation/removal during the first provider read takes effect before the mutating call.
                if (access(accountId, request.actorUserId) == null) return denied(request)
                val updated = provider.updateCommercialInfo(credentials.apiKey, CommercialRegistration(
                    current.personType, current.cpfCnpj, current.birthDate, current.companyType,
                    current.companyName, current.taxRegime, correction,
                ))
                check(updated.sameIdentity(current) && updated.correction.sameValues(correction))
                store.mark(accountId, request.requestId, RegistrationCorrectionStatus.SUCCEEDED, now)
                success(accountId, request)
            }
        } catch (_: FinancialProviderRejected) {
            store.mark(accountId, request.requestId, RegistrationCorrectionStatus.REJECTED, now)
            FinancialResult.Failure(FinancialError.INVALID_INPUT, request.requestId)
        } catch (_: Exception) {
            store.mark(accountId, request.requestId, RegistrationCorrectionStatus.UNKNOWN, now)
            pending(request)
        }
    }

    /** Recovery is read-only at the provider and never repeats the commercial-info POST. */
    fun recover(accountId: UUID, request: FinancialRequest, now: Instant): FinancialResult<RegistrationCorrectionResult> {
        if (access(accountId, request.actorUserId) == null) return denied(request)
        val operation = store.find(accountId, request.requestId) ?: return denied(request)
        if (operation.actorUserId != request.actorUserId) return denied(request)
        if (operation.status == RegistrationCorrectionStatus.SUCCEEDED || operation.status == RegistrationCorrectionStatus.REJECTED) {
            return terminal(operation, request)
        }
        val credentials = onboarding.credentials(accountId) ?: return pending(request)
        return try {
            val snapshot = operation.identitySnapshot ?: return pending(request)
            val observed = provider.readCommercialInfo(credentials.apiKey)
            if (observed.sameIdentity(snapshot) && observed.correction.sameValues(operation.correction)) {
                store.mark(accountId, request.requestId, RegistrationCorrectionStatus.SUCCEEDED, now)
                success(accountId, request)
            } else pending(request)
        } catch (_: Exception) { pending(request) }
    }

    private fun access(accountId: UUID, actor: UUID): Pair<FinancialAccount, FinancialManagementRole>? {
        val account = accounts.findById(accountId) ?: return null
        if (account.ownerUserId == actor) return account to FinancialManagementRole.OWNER
        val delegation = accounts.findDelegation(accountId, actor)
        return if (delegation?.revokedAt == null && delegation != null && groups.isAdministrator(account.ownerUserId, actor))
            account to FinancialManagementRole.DELEGATE else null
    }
    private fun CommercialRegistration.sameIdentity(other: CommercialRegistration) =
        personType == other.personType && cpfCnpj == other.cpfCnpj && birthDate == other.birthDate &&
            companyType == other.companyType && companyName == other.companyName && taxRegime == other.taxRegime
    private fun RegistrationCorrection.sameValues(other: RegistrationCorrection) =
        email == other.email && phone == other.phone && mobilePhone == other.mobilePhone && site == other.site &&
            incomeCents == other.incomeCents && postalCode == other.postalCode && address == other.address &&
            addressNumber == other.addressNumber && complement == other.complement && province == other.province
    private fun success(accountId: UUID, request: FinancialRequest) = FinancialResult.Success(
        RegistrationCorrectionResult(accountId, RegistrationCorrectionStatus.SUCCEEDED), request.requestId)
    private fun pending(request: FinancialRequest) = FinancialResult.Failure(FinancialError.RESULT_PENDING, request.requestId)
    private fun denied(request: FinancialRequest) = FinancialResult.Failure(FinancialError.NOT_FOUND, request.requestId)
    private fun terminal(operation: StoredRegistrationCorrection, request: FinancialRequest) =
        if (operation.status == RegistrationCorrectionStatus.SUCCEEDED) success(operation.accountId, request)
        else FinancialResult.Failure(FinancialError.INVALID_INPUT, request.requestId)
}
