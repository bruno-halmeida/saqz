package br.com.saqz.receivables.data

import br.com.saqz.domain.SaqzResult
import br.com.saqz.network.*
import br.com.saqz.receivables.domain.*
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Rotas do atleta da seção H do plano v1 (`/me`, códigos de pagamento e CPF). */
class KtorAthletePaymentsGateway(private val network: AuthenticatedNetworkClient) : AthletePaymentsGateway {
    override suspend fun me(groupId: String) =
        envelope(network, HttpMethod.Get, "api/receivables/groups/${groupId.encodeURLPathPart()}/me",
            AthleteMeTransport.serializer()) { it.domain() }

    override suspend fun createCode(groupId: String, requestId: String, method: ReceiptMethod, chargeIds: List<String>) =
        envelope(network, HttpMethod.Post, "api/receivables/groups/${groupId.encodeURLPathPart()}/payment-codes",
            PaymentCodeTransport.serializer(), requestId,
            Json.encodeToString(PaymentCodeCommand(requestId, method.name, chargeIds))) { code ->
            code.domain().also { require(it.method == method && it.chargeIds.toSet() == chargeIds.toSet()) }
        }

    override suspend fun code(codeId: String) =
        envelope(network, HttpMethod.Get, "api/receivables/payment-codes/${codeId.encodeURLPathPart()}",
            PaymentCodeTransport.serializer()) { it.domain().also { code -> require(code.codeId == codeId) } }

    override suspend fun saveDocument(requestId: String, cpf: String): SaqzResult<Unit, PaymentError> {
        if (!cpf.isValidCpf()) return SaqzResult.Failure(PaymentError.INVALID_DOCUMENT)
        return envelope(network, HttpMethod.Put, "api/receivables/me/document", AthleteDocumentTransport.serializer(), requestId,
            Json.encodeToString(DocumentCommand(requestId, cpf))) { require(it.hasDocument) }
    }
}

/** Rotas da recorrência no cartão (seção I, F3). Sem elas no servidor, a resposta é `UNAVAILABLE`. */
class KtorAutopayGateway(private val network: AuthenticatedNetworkClient) : AutopayGateway {
    override suspend fun start(groupId: String, requestId: String) =
        envelope(network, HttpMethod.Post, recurrence(groupId), CheckoutTransport.serializer(), requestId,
            Json.encodeToString(RequestOnly(requestId))) { it.checkoutUrl.requireHosted() }

    override suspend fun cancel(groupId: String, requestId: String, reason: AutopayCancelReason) =
        envelope(network, HttpMethod.Post, "${recurrence(groupId)}/cancel", kotlinx.serialization.json.JsonElement.serializer(),
            requestId, Json.encodeToString(CancelCommand(requestId, reason.name))) { }

    override suspend fun changeCard(groupId: String, requestId: String) =
        envelope(network, HttpMethod.Post, "${recurrence(groupId)}/card", CheckoutTransport.serializer(), requestId,
            Json.encodeToString(RequestOnly(requestId))) { it.checkoutUrl.requireHosted() }

    override suspend fun members(groupId: String, accountId: String) =
        envelope(network, HttpMethod.Get, "api/receivables/groups/${groupId.encodeURLPathPart()}/recurrences",
            kotlinx.serialization.builtins.ListSerializer(MemberRecurrenceTransport.serializer()),
            query = mapOf("accountId" to accountId)) { list ->
            list.mapNotNull { item ->
                val status = MemberAutopayStatus.entries.firstOrNull { it.name == item.status } ?: return@mapNotNull null
                MemberAutopay(item.memberUserId, status)
            }
        }

    private fun recurrence(groupId: String) = "api/receivables/groups/${groupId.encodeURLPathPart()}/recurrence"
}

/** `GET /api/receivables/settle-links/{codigo}` (seção J, F4). */
class KtorSettleLinkGateway(private val network: AuthenticatedNetworkClient) : SettleLinkGateway {
    override suspend fun resolve(code: String) =
        envelope(network, HttpMethod.Get, "api/receivables/settle-links/${code.encodeURLPathPart()}",
            SettleTargetTransport.serializer()) {
            require(it.groupId.isNotBlank())
            SettleTarget(it.groupId, it.gameId?.takeIf(String::isNotBlank), it.billingMonth?.takeIf(String::isNotBlank)?.take(7),
                it.groupName?.takeIf(String::isNotBlank))
        }
}

/**
 * Envelope de setembro: `{value,requestId}` ou `{error,requestId}`; escrita só vale com o mesmo `requestId`.
 * Leitura repete em falha transitória; escrita com `requestId` é idempotente no servidor.
 */
internal suspend fun <T, R> envelope(
    network: AuthenticatedNetworkClient,
    method: HttpMethod,
    path: String,
    serializer: KSerializer<T>,
    requestId: String? = null,
    body: String? = null,
    query: Map<String, String> = emptyMap(),
    map: (T) -> R,
): SaqzResult<R, PaymentError> {
    val writing = requestId != null
    val result = retryTransport(if (writing) RetrySafety.IdempotentWrite else RetrySafety.Read) {
        network.execute(method, path, EnvelopeTransport.serializer(serializer), NetworkRequest(body = body, query = query))
    }
    return when (result) {
        is NetworkResult.Failure -> SaqzResult.Failure(result.error.athleteError())
        is NetworkResult.Success -> {
            val envelope = result.value
            when {
                envelope.error != null -> SaqzResult.Failure(envelope.error.athleteCode() ?: PaymentError.UNAVAILABLE)
                envelope.value == null || (writing && envelope.requestId != requestId) -> SaqzResult.Failure(PaymentError.INVALID)
                else -> try { SaqzResult.Success(map(envelope.value)) } catch (_: IllegalArgumentException) {
                    SaqzResult.Failure(PaymentError.INVALID)
                }
            }
        }
    }
}

internal fun NetworkError.athleteError(): PaymentError = when (this) {
    is NetworkError.ApiProblemError -> (problem.error ?: problem.code)?.athleteCode() ?: problem.status.athleteError()
    is NetworkError.HttpStatus -> status.athleteError()
    NetworkError.Unavailable -> PaymentError.SIGNED_OUT
    NetworkError.InvalidResponse, NetworkError.PayloadTooLarge -> PaymentError.INVALID
    NetworkError.Timeout, NetworkError.Connectivity, NetworkError.Unknown -> PaymentError.NETWORK
}

private fun String.athleteCode(): PaymentError? = when (this) {
    "DOCUMENT_REQUIRED" -> PaymentError.DOCUMENT_REQUIRED
    "CHARGE_NOT_PAYABLE" -> PaymentError.CHARGE_NOT_PAYABLE
    "PIX_NOT_READY" -> PaymentError.PIX_NOT_READY
    "INVALID_DOCUMENT" -> PaymentError.INVALID_DOCUMENT
    else -> null
}

private fun Int.athleteError(): PaymentError = when (this) {
    401 -> PaymentError.SIGNED_OUT
    400 -> PaymentError.INVALID
    409 -> PaymentError.CHARGE_NOT_PAYABLE
    403, 404, 405, 501 -> PaymentError.UNAVAILABLE
    else -> PaymentError.NETWORK
}

private fun String?.requireHosted(): String {
    require(this != null && Regex("https://(www\\.)?(sandbox\\.)?asaas\\.com/[^\\s]+").matches(this))
    return this
}

@Serializable internal data class AthleteMeTransport(
    val active: Boolean,
    val groupName: String? = null,
    val payeeName: String? = null,
    val methods: List<String> = emptyList(),
    val pixReady: Boolean = false,
    val hasDocument: Boolean = false,
    val openCharges: List<OpenChargeTransport> = emptyList(),
    val totalCents: Long = 0,
    val recurrence: RecurrenceTransport? = null,
)
@Serializable internal data class OpenChargeTransport(val chargeId: String, val kind: String, val gameId: String? = null,
    val billingMonth: String? = null, val guestName: String? = null, val dueDate: String, val payableCents: Long,
    val overdue: Boolean = false)
@Serializable internal data class RecurrenceTransport(val status: String, val amountCents: Long = 0,
    val nextChargeDate: String? = null, val refusedChargeId: String? = null,
    val history: List<RecurrenceChargeTransport> = emptyList())
/** `{month:"2026-10-01", amountCents, paidAt:"<instante>", atAdhesion}` como o backend devolve. */
@Serializable internal data class RecurrenceChargeTransport(val month: String? = null, val amountCents: Long,
    val paidAt: String? = null, val atAdhesion: Boolean = false)

private fun AthleteMeTransport.domain(): AthleteReceivables {
    val charges = openCharges.map {
        require(it.chargeId.isNotBlank() && it.payableCents > 0)
        OpenCharge(it.chargeId, OpenChargeKind.entries.firstOrNull { kind -> kind.name == it.kind } ?: OpenChargeKind.OTHER,
            it.gameId, it.billingMonth, it.guestName?.takeIf(String::isNotBlank), it.dueDate, it.payableCents, it.overdue)
    }
    require(charges.map { it.chargeId }.distinct().size == charges.size && totalCents == charges.sumOf { it.payableCents })
    return AthleteReceivables(
        active = active,
        groupName = groupName?.takeIf(String::isNotBlank),
        payeeName = payeeName?.takeIf(String::isNotBlank),
        methods = methods.mapNotNull { name -> ReceiptMethod.entries.firstOrNull { it.name == name } }.toSet(),
        pixReady = pixReady,
        hasDocument = hasDocument,
        openCharges = charges,
        totalCents = totalCents,
        recurrence = recurrence?.let { r ->
            AthleteRecurrence(RecurrenceStatus.entries.firstOrNull { it.name == r.status } ?: RecurrenceStatus.NONE,
                r.amountCents, r.nextChargeDate, r.refusedChargeId,
                r.history.map { RecurrenceCharge(it.month?.take(7), it.amountCents, it.paidAt?.take(10), it.atAdhesion) })
        },
    )
}

@Serializable internal data class PaymentCodeCommand(val requestId: String, val method: String, val chargeIds: List<String>)
@Serializable internal data class PixTransport(val payload: String, val image: String? = null)
@Serializable internal data class CardTransport(val checkoutUrl: String)
@Serializable internal data class PaymentCodeTransport(val codeId: String, val method: String, val amountCents: Long,
    val chargeIds: List<String>, val status: String, val pix: PixTransport? = null, val card: CardTransport? = null)

private fun PaymentCodeTransport.domain(): PaymentCode {
    val method = ReceiptMethod.valueOf(this.method)
    val status = PaymentCodeStatus.valueOf(this.status)
    require(codeId.isNotBlank() && amountCents > 0 && chargeIds.isNotEmpty())
    if (status == PaymentCodeStatus.PENDING) require(if (method == ReceiptMethod.PIX) !pix?.payload.isNullOrBlank() else card != null)
    return PaymentCode(codeId, method, amountCents, chargeIds, status, pix?.payload, pix?.image,
        card?.checkoutUrl?.requireHosted())
}

@Serializable internal data class DocumentCommand(val requestId: String, val cpf: String) {
    override fun toString() = "DocumentCommand(redacted)"
}
@Serializable internal data class AthleteDocumentTransport(val hasDocument: Boolean)
@Serializable internal data class RequestOnly(val requestId: String)
@Serializable internal data class CancelCommand(val requestId: String, val reason: String)
@Serializable internal data class CheckoutTransport(val checkoutUrl: String)
@Serializable internal data class MemberRecurrenceTransport(val memberUserId: String, val status: String)
@Serializable internal data class SettleTargetTransport(val groupId: String, val gameId: String? = null,
    val billingMonth: String? = null, val groupName: String? = null)

/** Histórico do atleta: as pagas de `charges/me`, com `settledBy`/`appMethod` do v1 (aditivos, A). */
class KtorOwnChargeHistoryGateway(private val network: AuthenticatedNetworkClient) : OwnChargeHistoryGateway {
    override suspend fun paid(groupId: String): SaqzResult<List<PaidCharge>, PaymentError> =
        when (val result = retryTransport(RetrySafety.Read) {
            network.execute(HttpMethod.Get, "api/groups/${groupId.encodeURLPathPart()}/charges/me",
                OwnChargesTransport.serializer())
        }) {
            is NetworkResult.Failure -> SaqzResult.Failure(result.error.athleteError())
            is NetworkResult.Success -> SaqzResult.Success(result.value.charges.filter { it.status == "PAID" }.map { charge ->
                PaidCharge(
                    chargeId = charge.id,
                    kind = if (charge.kind == "MONTHLY") OpenChargeKind.MONTHLY else OpenChargeKind.GAME,
                    billingMonth = charge.month,
                    guestName = charge.guestDisplayName?.takeIf(String::isNotBlank),
                    dueDate = charge.dueDate,
                    amountCents = charge.payableCents ?: charge.amountCents,
                    via = when {
                        charge.settledBy == "APP" && charge.appMethod == "CARD" -> PaidVia.APP_CARD
                        charge.settledBy == "APP" -> PaidVia.APP_PIX
                        charge.paidMethod == "PIX" -> PaidVia.PIX
                        charge.paidMethod == "CASH" -> PaidVia.CASH
                        else -> PaidVia.OTHER
                    },
                )
            }.sortedByDescending { it.dueDate })
        }
}

@Serializable internal data class OwnChargeTransport(val id: String, val kind: String, val month: String? = null,
    val amountCents: Long, val dueDate: String, val status: String, val paidMethod: String? = null,
    val guestDisplayName: String? = null, val payableCents: Long? = null, val settledBy: String? = null,
    val appMethod: String? = null)
@Serializable internal data class OwnChargesTransport(val charges: List<OwnChargeTransport>)
