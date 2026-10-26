package br.com.saqz.receivables.domain

import br.com.saqz.domain.SaqzError
import br.com.saqz.domain.SaqzResult

/**
 * Recebimento pelo app v1 (FIN-011): o atleta logado paga as próprias cobranças em aberto num grupo com
 * recebimentos ativos. O valor vem pronto do servidor (`payableCents`); o app nunca soma taxa.
 */
enum class OpenChargeKind { GAME, MONTHLY, OTHER }

data class OpenCharge(
    val chargeId: String,
    val kind: OpenChargeKind,
    val gameId: String?,
    /** `"YYYY-MM"` na mensalidade. */
    val billingMonth: String?,
    /** Diária de um convidado do atleta; `null` na dele. */
    val guestName: String?,
    /** `"YYYY-MM-DD"`. */
    val dueDate: String,
    val payableCents: Long,
    val overdue: Boolean,
)

/**
 * `NONE`: mensalista que pode aderir (ou cancelou e pode retomar). `AUTHORIZING`: o cartão está sendo confirmado no
 * Asaas; não se oferece aderir de novo.
 */
enum class RecurrenceStatus { NONE, AUTHORIZING, ACTIVE, REFUSED }

data class RecurrenceCharge(val billingMonth: String?, val amountCents: Long, val chargedOn: String?, val atAdhesion: Boolean)

/**
 * Mensalidade no cartão (FIN-012, F3). Só mensalista recebe o objeto; nulo no `/me` (avulso, ou servidor antes da
 * F3) esconde a oferta inteira.
 */
data class AthleteRecurrence(
    val status: RecurrenceStatus,
    val amountCents: Long,
    val nextChargeDate: String?,
    val refusedChargeId: String?,
    val history: List<RecurrenceCharge>,
)

data class AthleteReceivables(
    val active: Boolean,
    /** Nome do grupo (acréscimo da rodada 2): o link do acerto e o push não sabem o nome. */
    val groupName: String? = null,
    val payeeName: String?,
    val methods: Set<ReceiptMethod>,
    val pixReady: Boolean,
    val hasDocument: Boolean,
    val openCharges: List<OpenCharge>,
    val totalCents: Long,
    val recurrence: AthleteRecurrence?,
)

enum class PaymentCodeStatus { PENDING, PAID, EXPIRED, CANCELLED }

data class PaymentCode(
    val codeId: String,
    val method: ReceiptMethod,
    val amountCents: Long,
    val chargeIds: List<String>,
    val status: PaymentCodeStatus,
    val pixPayload: String?,
    val pixImage: String?,
    val checkoutUrl: String?,
)

enum class PaymentError : SaqzError {
    /** `409 DOCUMENT_REQUIRED`: cartão sem CPF guardado. */
    DOCUMENT_REQUIRED,
    /** `409 CHARGE_NOT_PAYABLE`: alguma cobrança deixou de estar em aberto. */
    CHARGE_NOT_PAYABLE,
    /** `503 PIX_NOT_READY`: a chave da subconta ainda não existe; só cartão. */
    PIX_NOT_READY,
    /** `400` no CPF. */
    INVALID_DOCUMENT,
    /** `403` sem recebimentos, ou rota que ainda não existe no servidor (`404`). */
    UNAVAILABLE,
    NETWORK,
    INVALID,
    SIGNED_OUT,
}

interface AthletePaymentsGateway {
    suspend fun me(groupId: String): SaqzResult<AthleteReceivables, PaymentError>
    suspend fun createCode(groupId: String, requestId: String, method: ReceiptMethod, chargeIds: List<String>):
        SaqzResult<PaymentCode, PaymentError>
    suspend fun code(codeId: String): SaqzResult<PaymentCode, PaymentError>
    /** O CPF nunca volta do servidor; só o `hasDocument`. */
    suspend fun saveDocument(requestId: String, cpf: String): SaqzResult<Unit, PaymentError>
}

/** Motivo obrigatório do cancelamento (FIN-012), na ordem da tela. */
enum class AutopayCancelReason { STOP_PLAYING, PREFER_PIX, CARD_PROBLEM, TOO_EXPENSIVE, OTHER, PREFER_NOT_SAY }

enum class MemberAutopayStatus { ACTIVE, REFUSED }

data class MemberAutopay(val memberUserId: String, val status: MemberAutopayStatus)

interface AutopayGateway {
    suspend fun start(groupId: String, requestId: String): SaqzResult<String, PaymentError>
    suspend fun cancel(groupId: String, requestId: String, reason: AutopayCancelReason): SaqzResult<Unit, PaymentError>
    suspend fun changeCard(groupId: String, requestId: String): SaqzResult<String, PaymentError>
    suspend fun members(groupId: String, accountId: String): SaqzResult<List<MemberAutopay>, PaymentError>
}

/** O que o botão da hora do acerto (`/acerto/{codigo}`) abre (FIN-013, F4). */
data class SettleTarget(val groupId: String, val gameId: String?, val billingMonth: String?, val groupName: String? = null)

fun interface SettleLinkGateway {
    suspend fun resolve(code: String): SaqzResult<SettleTarget, PaymentError>
}

/** Cobranças do `/me` que um destino cobre: o jogo (dele e dos convidados), o mês, ou tudo. */
fun AthleteReceivables.chargesFor(gameId: String?, billingMonth: String?, chargeIds: List<String>?): List<OpenCharge> =
    openCharges.filter { charge ->
        when {
            !chargeIds.isNullOrEmpty() -> charge.chargeId in chargeIds
            gameId != null -> charge.gameId == gameId
            // O servidor manda o mês como `AAAA-MM-01` no `/me` e no link, e `AAAA-MM` no push: compara o `AAAA-MM`.
            billingMonth != null -> charge.kind == OpenChargeKind.MONTHLY && charge.billingMonth?.take(7) == billingMonth.take(7)
            else -> true
        }
    }

/** CPF: 11 dígitos e os dois verificadores. Só valida; quem guarda é o servidor. */
fun String.isValidCpf(): Boolean {
    if (length != 11 || any { !it.isDigit() } || all { it == first() }) return false
    val digits = map { it - '0' }
    fun check(size: Int): Int {
        val sum = (0 until size).sumOf { digits[it] * (size + 1 - it) }
        val rest = sum * 10 % 11
        return if (rest == 10) 0 else rest
    }
    return check(9) == digits[9] && check(10) == digits[10]
}

/** Como a cobrança paga foi quitada, para o histórico de Minhas cobranças. */
enum class PaidVia { APP_PIX, APP_CARD, PIX, CASH, OTHER }

data class PaidCharge(
    val chargeId: String,
    val kind: OpenChargeKind,
    val billingMonth: String?,
    val guestName: String?,
    val dueDate: String,
    val amountCents: Long,
    val via: PaidVia,
)

/** As cobranças já pagas do atleta no grupo (`GET /api/groups/{id}/charges/me`, a mesma rota de Minhas cobranças). */
fun interface OwnChargeHistoryGateway {
    suspend fun paid(groupId: String): SaqzResult<List<PaidCharge>, PaymentError>
}

/** "Quer deixar no automático?" aparece no máximo uma vez por mês (FIN-012). Fica no aparelho. */
interface AutopayOfferMemory {
    fun wasOffered(key: String, done: (Boolean) -> Unit)
    fun markOffered(key: String)
}
