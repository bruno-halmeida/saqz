package br.com.saqz.receivables.data

import br.com.saqz.domain.SaqzResult
import br.com.saqz.network.*
import br.com.saqz.receivables.domain.*
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class KtorMemberPaymentsGateway(private val network: AuthenticatedNetworkClient) : MemberPaymentsGateway {
    override suspend fun orders(after: String?) = call("api/receivables/orders", MemberPageTransport.serializer(),
        request = NetworkRequest(query = after?.let { mapOf("after" to it) }.orEmpty())) { it.domain() }

    override suspend fun detail(orderId: String) = call(orderPath(orderId), MemberDetailTransport.serializer()) { it.domain(orderId) }

    override suspend fun instrument(order: MemberPaymentOrder, command: MemberPaymentCommand):
        SaqzResult<MemberPaymentInstrument, ReceiptError> {
        if (!command.accepted || command.requestId.isBlank() || command.fingerprint != order.fingerprint ||
            order.quotes.none { it.method == command.method }) return SaqzResult.Failure(ReceiptError.INVALID)
        val body = Json.encodeToString(MemberCommandTransport(command.requestId, command.method.name, command.fingerprint,
            command.accepted, MemberPayerTransport(command.payer.name, command.payer.cpfCnpj)))
        return call("${orderPath(order.id)}/instruments", MemberInstrumentTransport.serializer(), command.requestId,
            NetworkRequest(body = body)) {
            val instrument = it.domain()
            require(instrument.orderId == order.id && instrument.accountId == order.accountId)
            require(instrument.quote.method == command.method && instrument.quote in order.quotes)
            instrument
        }
    }

    override suspend fun reconcile(orderId: String, requestId: String): SaqzResult<MemberPaymentDetail, ReceiptError> {
        if (requestId.isBlank()) return SaqzResult.Failure(ReceiptError.INVALID)
        return call("${orderPath(orderId)}/reconcile", MemberDetailTransport.serializer(), requestId,
            NetworkRequest(body = Json.encodeToString(MemberReconcileTransport(requestId)))) { it.domain(orderId) }
    }

    private suspend fun <T, R> call(path: String, serializer: KSerializer<T>, requestId: String? = null,
        request: NetworkRequest = NetworkRequest(), map: (T) -> R): SaqzResult<R, ReceiptError> {
        val writing = requestId != null
        val invalid = if (writing) ReceiptError.UNCERTAIN else ReceiptError.INVALID
        return when (val result = retryTransport(if (writing) RetrySafety.IdempotentWrite else RetrySafety.Read) {
            network.execute(if (writing) HttpMethod.Post else HttpMethod.Get, path, EnvelopeTransport.serializer(serializer), request)
        }) {
            is NetworkResult.Failure -> SaqzResult.Failure(result.error.paymentError(writing))
            is NetworkResult.Success -> {
                val envelope = result.value
                val wrongRequest = envelope.requestId.isBlank() || (writing && envelope.requestId != requestId)
                if (envelope.error != null || envelope.value == null || wrongRequest) SaqzResult.Failure(invalid)
                else try { SaqzResult.Success(map(envelope.value)) }
                catch (_: IllegalArgumentException) { SaqzResult.Failure(invalid) }
            }
        }
    }
}

private fun orderPath(id: String) = "api/receivables/orders/${id.encodeURLPathPart()}"
internal fun NetworkError.paymentError(writing: Boolean): ReceiptError = when (this) {
    is NetworkError.HttpStatus -> status.paymentError(writing)
    is NetworkError.ApiProblemError -> problem.status.paymentError(writing)
    NetworkError.Unavailable -> ReceiptError.SIGNED_OUT
    NetworkError.InvalidResponse, NetworkError.PayloadTooLarge -> if (writing) ReceiptError.UNCERTAIN else ReceiptError.INVALID
    NetworkError.Timeout, NetworkError.Connectivity, NetworkError.Unknown -> if (writing) ReceiptError.UNCERTAIN else ReceiptError.NETWORK
}
private fun Int.paymentError(writing: Boolean): ReceiptError = when (this) {
    401 -> ReceiptError.SIGNED_OUT
    403, 404 -> ReceiptError.DENIED
    409 -> ReceiptError.STALE
    400 -> ReceiptError.INVALID
    else -> if (writing) ReceiptError.UNCERTAIN else ReceiptError.UNAVAILABLE
}
