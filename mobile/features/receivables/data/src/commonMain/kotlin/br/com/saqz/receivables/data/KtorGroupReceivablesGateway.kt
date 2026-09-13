package br.com.saqz.receivables.data

import br.com.saqz.domain.SaqzResult
import br.com.saqz.network.*
import br.com.saqz.receivables.domain.*
import io.ktor.http.HttpMethod
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

class KtorGroupReceivablesGateway(private val network: AuthenticatedNetworkClient) : GroupReceivablesGateway {
    override suspend fun accounts() = call("api/receivables/accounts", ListSerializer(AccountTransport.serializer())) {
        it.map { account -> ReceiptAccount(account.id, AccountRegistration.valueOf(account.registration), account.newOperationsEnabled) }
    }
    override suspend fun status(groupId: String, accountId: String) = call(
        "api/receivables/groups/$groupId", StatusTransport.serializer(), query = mapOf("accountId" to accountId),
    ) { ReceiptStatus(it.state.domain(), it.permissions.mapValues { entry -> entry.value.domain() }) }
    override suspend fun preview(groupId: String, command: ReceiptCommand) = call(
        "api/receivables/groups/$groupId/preview", ReviewTransport.serializer(), command,
    ) { review ->
        ReceiptReview(review.state.domain(), review.schedules.map { it.domain() }, review.prices.flatMap { price ->
            price.quotes.map { ReceiptPrice(price.kind, ReceiptMethod.valueOf(it.method), it.baseCents,
                it.feesCents, it.totalCents, it.expectedNetCents) }
        }, review.permissions.mapValues { it.value.domain() }, review.effectiveCutoffAt, review.fingerprint)
    }
    override suspend fun terms(version: String) = call("api/receivables/terms/$version", TermsTransport.serializer()) {
        ReceiptTerms(it.version, it.content)
    }
    override suspend fun activate(groupId: String, command: ReceiptCommand) = call(
        "api/receivables/groups/$groupId/activate", ConfigurationTransport.serializer(), command,
    ) { it.domain() }
    override suspend fun deactivate(groupId: String, command: ReceiptCommand) = call(
        "api/receivables/groups/$groupId/deactivate", ConfigurationTransport.serializer(), command,
    ) { it.domain() }

    private suspend fun <T, R> call(path: String, serializer: KSerializer<T>, command: ReceiptCommand? = null,
        query: Map<String, String> = emptyMap(), map: (T) -> R): SaqzResult<R, ReceiptError> {
        val request = NetworkRequest(query = query, body = command?.let { Json.encodeToString(CommandTransport(
            it.requestId, it.accountId, it.methods.map { method -> method.name }.toSet(), it.fingerprint, it.accepted,
        )) })
        val safety = if (command == null) RetrySafety.Read else RetrySafety.IdempotentWrite
        return when (val result = retryTransport(safety) {
            network.execute(if (command == null) HttpMethod.Get else HttpMethod.Post,
                path, EnvelopeTransport.serializer(serializer), request)
        }) {
            is NetworkResult.Failure -> SaqzResult.Failure(result.error.receiptError())
            is NetworkResult.Success -> {
                val envelope = result.value
                if (envelope.error != null) SaqzResult.Failure(ReceiptError.DENIED)
                else if (envelope.value == null || envelope.requestId.isBlank() ||
                    (command != null && envelope.requestId != command.requestId))
                    SaqzResult.Failure(if (command == null) ReceiptError.INVALID else ReceiptError.UNCERTAIN)
                else try { SaqzResult.Success(map(envelope.value)) }
                catch (_: IllegalArgumentException) {
                    SaqzResult.Failure(if (command == null) ReceiptError.INVALID else ReceiptError.UNCERTAIN)
                }
            }
        }
    }
}

@Serializable internal data class EnvelopeTransport<T>(val value: T? = null, val error: String? = null, val requestId: String)
@Serializable internal data class CommandTransport(val requestId: String, val accountId: String,
    val methods: Set<String>, val fingerprint: String?, val accepted: Boolean)
@Serializable internal data class AccountTransport(val id: String, val registration: String, val newOperationsEnabled: Boolean)
@Serializable internal data class ConfigurationTransport(val accountId: String, val groupId: String, val enabled: Boolean,
    val pixEnabled: Boolean, val cardEnabled: Boolean)
private fun ConfigurationTransport.domain() = ReceiptConfiguration(accountId, groupId, enabled, pixEnabled, cardEnabled)
@Serializable internal data class PermissionTransport(val allowed: Boolean, val reason: String? = null)
private fun PermissionTransport.domain() = ReceiptPermission(allowed, reason)
@Serializable internal data class StatusTransport(val state: ConfigurationTransport, val permissions: Map<String, PermissionTransport>)
@Serializable internal data class ScheduleTransport(val method: String, val termsVersion: String,
    val providerRate: JsonPrimitive, val providerFixedCents: Long, val commissionRate: JsonPrimitive, val commissionFixedCents: Long)
private fun ScheduleTransport.domain() = ReceiptSchedule(ReceiptMethod.valueOf(method), termsVersion, providerRate.decimalRate(),
        providerFixedCents, commissionRate.decimalRate(), commissionFixedCents)
@Serializable internal data class PriceTransport(val kind: String, val quotes: List<QuoteTransport>)
@Serializable internal data class QuoteTransport(val method: String, val baseCents: Long, val feesCents: Long,
    val totalCents: Long, val expectedNetCents: Long)
@Serializable internal data class ReviewTransport(val state: ConfigurationTransport, val schedules: List<ScheduleTransport>,
    val prices: List<PriceTransport>, val permissions: Map<String, PermissionTransport>,
    val effectiveCutoffAt: String? = null, val fingerprint: String)
@Serializable internal data class TermsTransport(val version: String, val content: String)

private fun NetworkError.receiptError(): ReceiptError = when (this) {
    is NetworkError.HttpStatus -> status.receiptError()
    is NetworkError.ApiProblemError -> problem.status.receiptError()
    NetworkError.Unavailable -> ReceiptError.SIGNED_OUT
    NetworkError.InvalidResponse, NetworkError.PayloadTooLarge -> ReceiptError.UNCERTAIN
    NetworkError.Timeout, NetworkError.Connectivity, NetworkError.Unknown -> ReceiptError.NETWORK
}
private fun Int.receiptError(): ReceiptError = when (this) {
    401 -> ReceiptError.SIGNED_OUT
    403, 404 -> ReceiptError.DENIED
    409 -> ReceiptError.STALE
    400 -> ReceiptError.INVALID
    else -> ReceiptError.UNAVAILABLE
}

private fun JsonPrimitive.decimalRate(): String {
    require(!isString && content.length <= 100 && content.matches(Regex("[0-9]+([.][0-9]+)?([eE][+-]?[0-9]{1,2})?")))
    return content
}
