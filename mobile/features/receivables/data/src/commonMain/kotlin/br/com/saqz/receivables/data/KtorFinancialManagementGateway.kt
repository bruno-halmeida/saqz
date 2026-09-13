package br.com.saqz.receivables.data

import br.com.saqz.domain.SaqzResult
import br.com.saqz.network.*
import br.com.saqz.receivables.domain.*
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class KtorFinancialManagementGateway(private val network: AuthenticatedNetworkClient) : FinancialManagementGateway {
    override suspend fun candidates(accountId: String) = read("${path(accountId)}/delegations/candidates",
        ListSerializer(AdministratorTransport.serializer())) { rows ->
        require(rows.map { it.userId }.distinct().size == rows.size)
        rows.map { it.domain() }
    }
    override suspend fun accounts() = read("api/receivables/accounts", ListSerializer(ManagedAccountTransport.serializer())) {
        it.map(ManagedAccountTransport::domain)
    }
    override suspend fun management(accountId: String) = read("${path(accountId)}/management", ManagementTransport.serializer()) {
        it.domain(accountId)
    }
    override suspend fun delegations(accountId: String) = read("${path(accountId)}/delegations",
        ListSerializer(DelegationTransport.serializer())) { it.map { d -> d.domain(accountId) } }
    override suspend fun terms() = read("api/receivables/terms", TermsTransport.serializer()) {
        require(it.version.isNotBlank() && it.content.isNotBlank()); ReceiptTerms(it.version, it.content)
    }
    override suspend fun correct(accountId: String, command: ReceiptCorrectionCommand): SaqzResult<Unit, ReceiptError> {
        if (command.requestId.isBlank() || !command.correction.valid()) return SaqzResult.Failure(ReceiptError.INVALID)
        val c = command.correction
        return write("${path(accountId)}/corrections", CorrectionResultTransport.serializer(), command.requestId, HttpMethod.Post,
            Json.encodeToString(CorrectionTransport(command.requestId, c.email, c.phone, c.mobilePhone, c.site,
                c.incomeCents, c.postalCode, c.address, c.addressNumber, c.complement, c.province))) {
            require(it.accountId == accountId && it.status == "SUCCEEDED")
        }
    }
    override suspend fun recover(accountId: String, requestId: String) = write(
        "${path(accountId)}/corrections/${requestId.encodeURLPathPart()}/recover", CorrectionResultTransport.serializer(), requestId,
        HttpMethod.Post, Json.encodeToString(RecoveryTransport(requestId))) {
            require(it.accountId == accountId && it.status == "SUCCEEDED")
        }
    override suspend fun grant(accountId: String, userId: String, termsVersion: String, requestId: String): SaqzResult<Unit, ReceiptError> {
        if (userId.isBlank() || termsVersion.isBlank()) return SaqzResult.Failure(ReceiptError.INVALID)
        return write("${path(accountId)}/delegations", DelegationTransport.serializer(), requestId, HttpMethod.Post,
            Json.encodeToString(GrantTransport(requestId, userId, termsVersion, true))) {
            require(it.accountId == accountId && it.userId == userId && it.revokedAt == null)
        }
    }
    override suspend fun revoke(accountId: String, userId: String, requestId: String) = write(
        "${path(accountId)}/delegations/${userId.encodeURLPathPart()}", UnitTransport.serializer(), requestId, HttpMethod.Delete,
        null, NetworkRequest(query = mapOf("requestId" to requestId))) { Unit }

    private suspend fun <T, R> read(url: String, serializer: KSerializer<T>, map: (T) -> R): SaqzResult<R, ReceiptError> =
        decode(retryTransport(RetrySafety.Read) {
            network.execute(HttpMethod.Get, url, EnvelopeTransport.serializer(serializer))
        }, null, map)
    private suspend fun <T, R> write(url: String, serializer: KSerializer<T>, requestId: String, method: HttpMethod,
        body: String?, request: NetworkRequest = NetworkRequest(body = body), map: (T) -> R): SaqzResult<R, ReceiptError> {
        if (requestId.isBlank()) return SaqzResult.Failure(ReceiptError.INVALID)
        return decode(retryTransport(RetrySafety.IdempotentWrite) {
            network.execute(method, url, EnvelopeTransport.serializer(serializer), request)
        }, requestId, map)
    }
    private fun <T, R> decode(result: NetworkResult<EnvelopeTransport<T>>, requestId: String?,
        map: (T) -> R): SaqzResult<R, ReceiptError> = when (result) {
        is NetworkResult.Failure -> SaqzResult.Failure(result.error.paymentError(requestId != null))
        is NetworkResult.Success -> {
            val e = result.value
            if (e.error == "RESULT_PENDING") SaqzResult.Failure(ReceiptError.UNCERTAIN)
            else if (e.error != null || e.value == null || invalidRequestId(e.requestId, requestId))
                SaqzResult.Failure(if (requestId == null) ReceiptError.INVALID else ReceiptError.UNCERTAIN)
            else try { SaqzResult.Success(map(e.value)) } catch (_: IllegalArgumentException) {
                SaqzResult.Failure(if (requestId == null) ReceiptError.INVALID else ReceiptError.UNCERTAIN)
            }
        }
    }
    private fun invalidRequestId(actual: String, expected: String?) =
        actual.isBlank() || expected != null && actual != expected
    private fun path(accountId: String): String {
        require(accountId.isNotBlank()); return "api/receivables/accounts/${accountId.encodeURLPathPart()}"
    }
}

@Serializable internal data class ManagedAccountTransport(val id: String, val ownerUserId: String,
    val registration: String, val newOperationsEnabled: Boolean) {
    fun domain(): ManagedReceiptAccount { require(id.isNotBlank() && ownerUserId.isNotBlank())
        return ManagedReceiptAccount(id, ownerUserId, AccountRegistration.valueOf(registration), newOperationsEnabled) }
}
@Serializable internal data class CorrectionFieldsTransport(val email: String, val phone: String? = null,
    val mobilePhone: String, val site: String? = null, val incomeCents: Long, val postalCode: String,
    val address: String, val addressNumber: String, val complement: String? = null, val province: String) {
    fun domain() = ReceiptRegistrationCorrection(email, phone, mobilePhone, site, incomeCents, postalCode,
        address, addressNumber, complement, province).also { require(it.valid()) }
}
@Serializable internal data class ManagementTransport(val accountId: String, val role: String,
    val correction: CorrectionFieldsTransport) {
    fun domain(expected: String): ReceiptManagementView { require(accountId == expected)
        return ReceiptManagementView(accountId, ReceiptManagementRole.valueOf(role), correction.domain()) }
}
@Serializable internal data class DelegationTransport(val accountId: String, val userId: String,
    val grantedAt: String = "", val revokedAt: String? = null) {
    fun domain(expected: String): ReceiptDelegation { require(accountId == expected && userId.isNotBlank())
        return ReceiptDelegation(accountId, userId, revokedAt != null) }
}
@Serializable internal data class CorrectionTransport(val requestId: String, val email: String,
    val phone: String?, val mobilePhone: String, val site: String?, val incomeCents: Long, val postalCode: String,
    val address: String, val addressNumber: String, val complement: String?, val province: String)
@Serializable internal data class CorrectionResultTransport(val accountId: String, val status: String)
@Serializable internal data class GrantTransport(val requestId: String, val userId: String,
    val termsVersion: String, val acknowledgedWholeAccount: Boolean)
@Serializable internal data class RecoveryTransport(val requestId: String)
@Serializable internal class UnitTransport

@Serializable internal data class AdministratorTransport(val userId: String, val displayName: String, val groupNames: List<String>) {
    fun domain(): ReceiptAdministrator {
        require(userId.isNotBlank() && displayName.isNotBlank() && groupNames.isNotEmpty())
        require(groupNames.all { it.isNotBlank() })
        return ReceiptAdministrator(userId, displayName, groupNames)
    }
}
