package br.com.saqz.receivables.data

import br.com.saqz.domain.SaqzResult
import br.com.saqz.network.*
import br.com.saqz.receivables.domain.*
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class KtorPixRenewalGateway(private val network: AuthenticatedNetworkClient) : PixRenewalGateway {
    override suspend fun renew(order: MemberPaymentOrder, instrument: MemberPaymentInstrument, command: PixRenewalCommand):
        SaqzResult<PixRenewal, ReceiptError> {
        if (!valid(order, instrument, command.requestId, command.dueDate)) return SaqzResult.Failure(ReceiptError.INVALID)
        val body = Json.encodeToString(PixRenewalCommandTransport(command.requestId, command.dueDate))
        return call(HttpMethod.Post, path(order.id), order, instrument, command.requestId, body, command.dueDate)
    }

    override suspend fun recover(order: MemberPaymentOrder, instrument: MemberPaymentInstrument, requestId: String):
        SaqzResult<PixRenewal?, ReceiptError> {
        if (!valid(order, instrument, requestId, order.dueDate)) return SaqzResult.Failure(ReceiptError.INVALID)
        return when (val result = retryTransport(RetrySafety.Read) {
            network.execute(HttpMethod.Get, "${path(order.id)}/${requestId.encodeURLPathPart()}",
                EnvelopeTransport.serializer(PixRenewalTransport.serializer()))
        }) {
            is NetworkResult.Failure -> when (val error = result.error) {
                is NetworkError.HttpStatus -> if (error.status == 404) SaqzResult.Success(null)
                    else SaqzResult.Failure(error.paymentError(false))
                is NetworkError.ApiProblemError -> if (error.problem.status == 404) SaqzResult.Success(null)
                    else SaqzResult.Failure(error.paymentError(false))
                NetworkError.Unavailable -> SaqzResult.Failure(ReceiptError.SIGNED_OUT)
                else -> SaqzResult.Failure(ReceiptError.UNCERTAIN)
            }
            is NetworkResult.Success -> try {
                val envelope = result.value
                val renewal = envelope.value?.domain()
                if (!envelope.accepted(result.metadata.status, requestId) || renewal == null ||
                    !renewal.validFor(order, instrument)) SaqzResult.Failure(ReceiptError.UNCERTAIN)
                else SaqzResult.Success(renewal)
            } catch (_: IllegalArgumentException) { SaqzResult.Failure(ReceiptError.UNCERTAIN) }
        }
    }

    private suspend fun call(method: HttpMethod, path: String, order: MemberPaymentOrder,
        instrument: MemberPaymentInstrument, requestId: String, body: String?, dueDate: String):
        SaqzResult<PixRenewal, ReceiptError> = when (val result = network.execute(method, path,
            EnvelopeTransport.serializer(PixRenewalTransport.serializer()), NetworkRequest(body = body))) {
            is NetworkResult.Failure -> SaqzResult.Failure(result.error.paymentError(true))
            is NetworkResult.Success -> try {
                val envelope = result.value
                val renewal = envelope.value?.domain()
                if (!envelope.accepted(result.metadata.status, requestId) || renewal == null ||
                    !renewal.validFor(order, instrument) || renewal.dueDate != dueDate) SaqzResult.Failure(ReceiptError.UNCERTAIN)
                else SaqzResult.Success(renewal)
            } catch (_: IllegalArgumentException) {
                SaqzResult.Failure(ReceiptError.UNCERTAIN)
            }
        }

    private fun EnvelopeTransport<PixRenewalTransport>.accepted(status: Int, expectedRequest: String) =
        status != 202 && error == null && requestId == expectedRequest

    private fun valid(order: MemberPaymentOrder, instrument: MemberPaymentInstrument, requestId: String, dueDate: String) =
        requestId.isNotBlank() && dueDate.isPaymentDate() && order.id == instrument.orderId &&
            order.accountId == instrument.accountId && order.status == "ISSUED" && instrument.quote in order.quotes &&
            instrument.quote.method == ReceiptMethod.PIX && !instrument.paymentId.isNullOrBlank()
    private fun path(orderId: String) = "api/receivables/orders/${orderId.encodeURLPathPart()}/pix-renewal"
}
