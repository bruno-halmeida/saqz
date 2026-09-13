package br.com.saqz.receivables.data

import br.com.saqz.domain.SaqzResult
import br.com.saqz.network.*
import br.com.saqz.receivables.domain.*
import io.ktor.http.*
import io.ktor.utils.io.ByteReadChannel
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class KtorFinancialOnboardingGateway(private val network: AuthenticatedNetworkClient) : FinancialOnboardingGateway {
    override suspend fun mine(ownerId: String): SaqzResult<OwnedReceiptAccount?, ReceiptError> {
        val result = retryTransport(RetrySafety.Read) {
            network.execute(HttpMethod.Get, "$ACCOUNTS/me", EnvelopeTransport.serializer(OwnedAccountTransport.serializer()))
        }
        if (result is NetworkResult.Failure && result.error.statusCode == 404) return SaqzResult.Success(null)
        return decode(result) { it.domain(ownerId) }
    }
    override suspend fun currentTerms() = call("api/receivables/terms", TermsTransport.serializer()) {
        require(it.version.isNotBlank() && it.content.isNotBlank()); ReceiptTerms(it.version, it.content)
    }
    override suspend fun create(ownerId: String, command: ReceiptRegistrationCommand): SaqzResult<OwnedReceiptAccount, ReceiptError> {
        if (!command.accepted || command.requestId.isBlank() || command.termsVersion.isBlank() || !command.registration.valid()) {
            return SaqzResult.Failure(ReceiptError.INVALID)
        }
        val r = command.registration
        return call(ACCOUNTS, OwnedAccountTransport.serializer(), command.requestId, Json.encodeToString(RegistrationTransport(
            command.requestId, true, command.termsVersion, r.name, r.email, r.cpfCnpj, r.mobilePhone, r.incomeCents,
            r.address.street, r.address.number, r.address.province, r.address.postalCode, r.birthDate, r.companyType))) {
            it.domain(ownerId)
        }
    }
    override suspend fun recover(ownerId: String, requestId: String): SaqzResult<OwnedReceiptAccount, ReceiptError> {
        if (requestId.isBlank()) return SaqzResult.Failure(ReceiptError.INVALID)
        return call("$ACCOUNTS/me/recover", OwnedAccountTransport.serializer(), requestId,
            Json.encodeToString(OnboardingRecoveryTransport(requestId))) { it.domain(ownerId) }
    }
    override suspend fun documents() = call("$ACCOUNTS/me/documents", ListSerializer(DocumentTransport.serializer())) { list ->
        require(list.map { it.id }.distinct().size == list.size)
        list.map { it.domain() }
    }
    override suspend fun upload(document: ReceiptDocument, requestId: String, file: ReceiptDocumentFile): SaqzResult<Unit, ReceiptError> {
        val canUpload = document.onboardingUrl == null && document.needsSubmission &&
            document.id.matches(Regex("[A-Za-z0-9_-]{1,128}")) && document.type.matches(Regex("[A-Z_]{1,64}"))
        if (requestId.isBlank() || !file.valid() || !canUpload) {
            return SaqzResult.Failure(ReceiptError.INVALID)
        }
        val bytes = file.bytes.copyOf()
        val upload = NetworkMediaUpload("documentFile", "document", ContentType.parse(file.contentType), bytes.size.toLong(),
            openChannel = { ByteReadChannel(bytes) })
        // No transport retry: an absent upload response is not proof that no file was accepted.
        val result = network.uploadMediaDecoded(HttpMethod.Post, "$ACCOUNTS/me/documents/${document.id}", upload,
            EnvelopeTransport.serializer(Unit.serializer()),
            NetworkRequest(query = mapOf("requestId" to requestId, "type" to document.type)))
        return decode(result, requestId) { it }
    }
    private suspend fun <T, R> call(path: String, serializer: KSerializer<T>, requestId: String? = null,
        body: String? = null, map: (T) -> R): SaqzResult<R, ReceiptError> = decode(retryTransport(
        if (requestId == null) RetrySafety.Read else RetrySafety.IdempotentWrite) {
        network.execute(if (requestId == null) HttpMethod.Get else HttpMethod.Post, path,
            EnvelopeTransport.serializer(serializer), NetworkRequest(body = body))
    }, requestId, map)

    private fun <T, R> decode(result: NetworkResult<EnvelopeTransport<T>>, requestId: String? = null,
        map: (T) -> R): SaqzResult<R, ReceiptError> {
        val writing = requestId != null
        val invalid = if (writing) ReceiptError.UNCERTAIN else ReceiptError.INVALID
        return when (result) {
            is NetworkResult.Failure -> SaqzResult.Failure(result.error.paymentError(writing))
            is NetworkResult.Success -> {
                val e = result.value
                val wrongRequest = e.requestId.isBlank() || (writing && e.requestId != requestId)
                if (e.error == "RESULT_PENDING") SaqzResult.Failure(ReceiptError.UNCERTAIN)
                else if (e.error != null || e.value == null || wrongRequest)
                    SaqzResult.Failure(invalid)
                else try { SaqzResult.Success(map(e.value)) } catch (_: IllegalArgumentException) { SaqzResult.Failure(invalid) }
            }
        }
    }
    private companion object { const val ACCOUNTS = "api/receivables/accounts" }
}
private val NetworkError.statusCode: Int? get() = when (this) {
    is NetworkError.HttpStatus -> status
    is NetworkError.ApiProblemError -> problem.status
    else -> null
}
@Serializable internal data class OwnedAccountTransport(val id: String, val ownerUserId: String,
    val registration: String, val newOperationsEnabled: Boolean)
private fun OwnedAccountTransport.domain(owner: String): OwnedReceiptAccount {
    require(id.isNotBlank() && owner.isNotBlank() && ownerUserId == owner)
    return OwnedReceiptAccount(id, ownerUserId, AccountRegistration.valueOf(registration), newOperationsEnabled)
}
@Serializable internal data class DocumentTransport(val id: String, val type: String, val status: String,
    val onboardingUrl: String? = null, val description: String? = null)
private fun DocumentTransport.domain(): ReceiptDocument {
    require(id.matches(Regex("[A-Za-z0-9_-]{1,128}")) && type.matches(Regex("[A-Z_]{1,64}")) && status.isNotBlank())
    onboardingUrl?.let { link ->
        val url = Url(link)
        require(url.protocol == URLProtocol.HTTPS && url.user.isNullOrEmpty() && url.password.isNullOrEmpty() &&
            url.port == 443 && (url.host == "asaas.com" || url.host.endsWith(".asaas.com")))
    }
    return ReceiptDocument(id, type, status, onboardingUrl, description)
}
@Serializable internal data class OnboardingRecoveryTransport(val requestId: String)
@Serializable internal data class RegistrationTransport(val requestId: String, val acceptedTerms: Boolean, val termsVersion: String,
    val name: String, val email: String, val cpfCnpj: String, val mobilePhone: String, val incomeCents: Long,
    val address: String, val addressNumber: String, val province: String, val postalCode: String,
    val birthDate: String?, val companyType: String?) {
    override fun toString() = "RegistrationTransport(redacted)"
}
