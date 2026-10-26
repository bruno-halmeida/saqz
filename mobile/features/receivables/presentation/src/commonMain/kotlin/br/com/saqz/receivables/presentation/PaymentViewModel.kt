package br.com.saqz.receivables.presentation

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.formatting.formatBrl
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.SaqzResult
import br.com.saqz.receivables.domain.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Um código por meio e por conjunto de cobranças (FIN-011). O Pix nasce ao abrir a aba; o cartão, ao tocar
 * "Continuar para o cartão". A tela consulta o código a cada [POLL_MILLIS] até `PAID`; código vencido pede outro.
 */
@OptIn(ExperimentalUuidApi::class)
class PaymentViewModel(
    private val args: PaymentArgs,
    private val payments: AthletePaymentsGateway,
    private val offers: AutopayOfferMemory,
    private val clock: Clock,
    private val session: ReceivablesSessionContext,
) : MviViewModel<PaymentState, PaymentIntent, PaymentEffect>(PaymentState(args.groupName)) {
    private val sessionKey = session.currentKey()
    private var me: AthleteReceivables? = null
    private var charges: List<OpenCharge> = emptyList()
    private var codes = mutableMapOf<ReceiptMethod, PaymentCode>()
    private var generation = 0L
    private var polling: Job? = null

    init { load() }

    override fun handleIntent(intent: PaymentIntent) {
        val current = state.value
        if (!validSession()) return
        when (intent) {
            PaymentIntent.Retry -> load()
            is PaymentIntent.SelectTab -> selectTab(intent.tab)
            PaymentIntent.CopyPix -> current.pixPayload?.let {
                emit(PaymentEffect.Copy(it))
                update { state -> state.copy(copied = true) }
            }
            PaymentIntent.NewCode -> if (current.codeExpired) load()
            is PaymentIntent.CpfChanged -> update {
                it.copy(cpf = intent.value.filter(Char::isDigit).take(CPF_LENGTH), cpfInvalid = false)
            }
            PaymentIntent.ContinueCard -> if (current.canPayByCard) continueCard()
            PaymentIntent.Resumed -> if (current.waiting) current.activeCode()?.let { checkNow(it) }
            PaymentIntent.AcceptOffer -> if (current.confirmed?.offer == true) {
                update { it.copy(confirmed = it.confirmed?.copy(offer = false)) }
                emit(PaymentEffect.OpenAutopay)
            }
            PaymentIntent.DeclineOffer -> update { it.copy(confirmed = it.confirmed?.copy(offer = false)) }
        }
    }

    private fun PaymentState.activeCode(): PaymentCode? = codes[if (tab == PayTab.PIX) ReceiptMethod.PIX else ReceiptMethod.CARD]

    private fun load() {
        polling?.cancel()
        codes.clear()
        val expected = ++generation
        update { PaymentState(args.groupName, loading = true, cpf = it.cpf) }
        viewModelScope.launch {
            val result = payments.me(args.groupId)
            if (expected != generation || !validSession()) return@launch
            when (result) {
                is SaqzResult.Failure -> update { it.copy(loading = false, error = result.error) }
                is SaqzResult.Success -> {
                    val value = result.value
                    me = value
                    charges = value.chargesFor(args.gameId, args.billingMonth, args.chargeIds.ifEmpty { null })
                    if (!value.active || charges.isEmpty()) {
                        update { it.copy(loading = false, nothingOpen = true,
                            error = if (value.active) null else PaymentError.UNAVAILABLE) }
                        return@launch
                    }
                    val pix = value.pixReady && ReceiptMethod.PIX in value.methods
                    val card = ReceiptMethod.CARD in value.methods
                    update {
                        it.copy(loading = false, subject = subjectOf(charges), kicker = kickerOf(charges),
                            amountLabel = formatBrl(charges.sumOf { c -> c.payableCents }),
                            lines = charges.map { c -> PaymentLineUi(PaymentLabels.breakdown(c, charges.size == 1 ||
                                charges.count { o -> o.guestName == null } == 1), formatBrl(c.payableCents)) },
                            pixAvailable = pix, cardAvailable = card, tab = if (pix) PayTab.PIX else PayTab.CARD,
                            payeeName = value.payeeName, needsDocument = !value.hasDocument)
                    }
                    if (pix) createPix(expected)
                }
            }
        }
    }

    private fun selectTab(tab: PayTab) {
        val current = state.value
        if (current.loading || current.tab == tab || current.confirmed != null) return
        if (tab == PayTab.PIX && !current.pixAvailable || tab == PayTab.CARD && !current.cardAvailable) return
        polling?.cancel()
        update { it.copy(tab = tab, waitingCard = false, codeExpired = false) }
        if (tab == PayTab.PIX) {
            val pix = codes[ReceiptMethod.PIX]
            if (pix == null) createPix(generation) else poll(pix)
        }
    }

    private fun createPix(expected: Long) {
        update { it.copy(creatingCode = true, error = null) }
        viewModelScope.launch {
            val result = payments.createCode(args.groupId, Uuid.random().toString(), ReceiptMethod.PIX, charges.map { it.chargeId })
            if (expected != generation || !validSession()) return@launch
            when (result) {
                is SaqzResult.Success -> {
                    codes[ReceiptMethod.PIX] = result.value
                    update { it.copy(creatingCode = false, pixPayload = result.value.pixPayload, pixImage = result.value.pixImage) }
                    onStatus(result.value)
                }
                is SaqzResult.Failure -> when (result.error) {
                    // Chave da subconta ainda não existe: só cartão (B).
                    PaymentError.PIX_NOT_READY -> update {
                        it.copy(creatingCode = false, pixAvailable = false, tab = if (it.cardAvailable) PayTab.CARD else PayTab.PIX,
                            error = if (it.cardAvailable) null else PaymentError.PIX_NOT_READY)
                    }
                    PaymentError.CHARGE_NOT_PAYABLE -> update { it.copy(creatingCode = false, codeExpired = true) }
                    else -> update { it.copy(creatingCode = false, error = result.error) }
                }
            }
        }
    }

    private fun continueCard() {
        val current = state.value
        val expected = generation
        if (current.needsDocument && !current.cpf.isValidCpf()) {
            update { it.copy(cpfInvalid = true) }
            return
        }
        update { it.copy(creatingCode = true, error = null) }
        viewModelScope.launch {
            if (current.needsDocument) {
                val saved = payments.saveDocument(Uuid.random().toString(), current.cpf)
                if (expected != generation || !validSession()) return@launch
                if (saved is SaqzResult.Failure) {
                    update { it.copy(creatingCode = false, cpfInvalid = saved.error == PaymentError.INVALID_DOCUMENT,
                        error = saved.error.takeIf { e -> e != PaymentError.INVALID_DOCUMENT }) }
                    return@launch
                }
                update { it.copy(needsDocument = false, cpf = "") }
            }
            val existing = codes[ReceiptMethod.CARD]?.takeIf { it.status == PaymentCodeStatus.PENDING && it.checkoutUrl != null }
            val result = existing?.let { SaqzResult.Success(it) }
                ?: payments.createCode(args.groupId, Uuid.random().toString(), ReceiptMethod.CARD, charges.map { it.chargeId })
            if (expected != generation || !validSession()) return@launch
            when (result) {
                is SaqzResult.Success -> {
                    codes[ReceiptMethod.CARD] = result.value
                    update { it.copy(creatingCode = false, waitingCard = true) }
                    result.value.checkoutUrl?.let { emit(PaymentEffect.OpenUrl(it)) }
                    onStatus(result.value)
                }
                is SaqzResult.Failure -> when (result.error) {
                    PaymentError.DOCUMENT_REQUIRED -> update { it.copy(creatingCode = false, needsDocument = true) }
                    PaymentError.CHARGE_NOT_PAYABLE -> update { it.copy(creatingCode = false, codeExpired = true) }
                    else -> update { it.copy(creatingCode = false, error = result.error) }
                }
            }
        }
    }

    private fun onStatus(code: PaymentCode) {
        codes[code.method] = code
        when (code.status) {
            PaymentCodeStatus.PAID -> confirm()
            PaymentCodeStatus.EXPIRED, PaymentCodeStatus.CANCELLED -> {
                polling?.cancel()
                update { it.copy(codeExpired = true, waitingCard = false) }
            }
            PaymentCodeStatus.PENDING -> poll(code)
        }
    }

    private fun poll(code: PaymentCode) {
        polling?.cancel()
        val expected = generation
        polling = viewModelScope.launch {
            // Com teto: quem deixa a tela aberta e não paga não fica consultando para sempre; "Resumed" reconsulta.
            repeat(MAX_POLLS) {
                delay(POLL_MILLIS)
                if (!fetch(code, expected)) return@launch
            }
        }
    }

    private fun checkNow(code: PaymentCode) {
        val expected = generation
        viewModelScope.launch { fetch(code, expected) }
    }

    /** Devolve se continua consultando. Falha de rede não para a espera: a próxima volta tenta de novo. */
    private suspend fun fetch(code: PaymentCode, expected: Long): Boolean {
        val result = payments.code(code.codeId)
        if (expected != generation || !validSession() || state.value.confirmed != null) return false
        val value = (result as? SaqzResult.Success)?.value ?: return true
        if (value.status == PaymentCodeStatus.PENDING) return true
        onStatus(value)
        return false
    }

    private fun confirm() {
        polling?.cancel()
        val paid = charges
        val summary = "${formatBrl(paid.sumOf { it.payableCents })} · ${PaymentLabels.subject(paid)}"
        val monthly = paid.firstOrNull { it.kind == OpenChargeKind.MONTHLY }
        val eligible = monthly != null && me?.recurrence?.status == RecurrenceStatus.NONE
        val dueDay = monthly?.dueDate?.split('-')?.getOrNull(2)?.trimStart('0')
        update { it.copy(waitingCard = false, confirmed = PaymentConfirmedUi(summary, offer = false, dueDay = dueDay)) }
        if (!eligible) return
        val key = offerKey()
        offers.wasOffered(key) { offered ->
            if (offered || state.value.confirmed == null) return@wasOffered
            offers.markOffered(key)
            update { it.copy(confirmed = it.confirmed?.copy(offer = true)) }
        }
    }

    private fun offerKey(): String {
        val today = clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        return "autopay-offer:${args.groupId}:${today.year}-${today.monthNumber.toString().padStart(2, '0')}"
    }

    private fun subjectOf(charges: List<OpenCharge>) = when {
        charges.all { it.kind == OpenChargeKind.MONTHLY } && charges.size == 1 -> PaymentSubject.MONTHLY
        charges.none { it.kind == OpenChargeKind.MONTHLY } && charges.map { it.gameId ?: it.dueDate }.distinct().size == 1 ->
            PaymentSubject.GAME
        else -> PaymentSubject.MANY
    }

    /** Sem o nome do grupo (link do acerto, push), o kicker fica só com o jogo ou o mês. */
    private fun kickerOf(charges: List<OpenCharge>): String {
        val what = when (subjectOf(charges)) {
            PaymentSubject.GAME -> charges.first().dueDate.let { date ->
                PaymentLabels.weekday(date)?.let { "$it, ${PaymentLabels.dayMonth(date)}" }
            }
            PaymentSubject.MONTHLY, PaymentSubject.MANY -> PaymentLabels.subject(charges)
        }
        return listOfNotNull(args.groupName.takeIf(String::isNotBlank), what).joinToString(" · ")
    }

    private fun validSession(): Boolean {
        if (sessionKey != null && sessionKey == session.currentKey()) return true
        generation++
        polling?.cancel()
        update { PaymentState(args.groupName, loading = false, error = PaymentError.SIGNED_OUT) }
        return false
    }

    override fun onCleared() {
        polling?.cancel()
        super.onCleared()
    }

    internal companion object {
        const val POLL_MILLIS = 4_000L
        /** 15 minutos de espera na tela. */
        const val MAX_POLLS = 225
        const val CPF_LENGTH = 11
    }
}
