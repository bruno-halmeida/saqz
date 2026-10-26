package br.com.saqz.receivables.application

import br.com.saqz.receivables.domain.FeeCalculator
import br.com.saqz.receivables.domain.PaymentMethod
import br.com.saqz.receivables.domain.RegistrationStatus
import br.com.saqz.sharedkernel.group.GroupChargeLedger
import br.com.saqz.sharedkernel.group.LedgerCharge
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class PixCode(val payload: String, val image: String)
data class CardCheckout(val checkoutUrl: String)
data class PaymentCodeView(val codeId: UUID, val method: PaymentMethod, val amountCents: Long, val chargeIds: List<UUID>,
    val status: String, val pix: PixCode?, val card: CardCheckout?)

data class OpenChargeView(val chargeId: UUID, val kind: String, val gameId: UUID?, val billingMonth: LocalDate?,
    val guestName: String?, val dueDate: LocalDate, val payableCents: Long, val overdue: Boolean)
data class AthletePaymentState(val active: Boolean, val payeeName: String?, val methods: List<PaymentMethod>,
    val pixReady: Boolean, val hasDocument: Boolean, val openCharges: List<OpenChargeView>, val totalCents: Long,
    /** Recorrência no cartão: nula até a F3. */
    val recurrence: Any? = null)
data class DocumentState(val hasDocument: Boolean)

/** Código guardado. `status` interno CREATING aparece como PENDING (ainda sem QR ou link). */
data class StoredPaymentCode(val id: UUID, val accountId: UUID, val groupId: UUID, val payerId: UUID,
    val method: PaymentMethod, val amountCents: Long, val commissionCents: Long, val chargeIds: List<UUID>,
    val status: String, val providerQrId: String?, val providerPaymentId: String?, val pix: PixCode?, val checkoutUrl: String?)

/** Conta que recebe pelo grupo, com o que decide se dá para gerar código novo. */
data class ReceivingAccount(val accountId: UUID, val ownerUserId: UUID, val registration: RegistrationStatus,
    val newOperationsEnabled: Boolean, val pixKey: String?, val webhookReady: Boolean)

data class CardCustomer(val reference: UUID, val providerId: String?, val name: String, val cpf: String)

interface PaymentCodeStore {
    fun <T> transaction(block: () -> T): T
    /** Vínculo ativo do grupo, ou nulo. */
    fun receivingAccount(groupId: UUID): ReceivingAccount?
    fun hasDocument(userId: UUID): Boolean
    fun document(userId: UUID): String?
    fun saveDocument(userId: UUID, cpf: String, at: Instant)
    fun requested(payerId: UUID, requestId: UUID): Pair<UUID, String>?
    fun lockPayer(groupId: UUID, payerId: UUID)
    fun live(groupId: UUID, payerId: UUID, method: PaymentMethod, chargesKey: String): StoredPaymentCode?
    fun insert(code: StoredPaymentCode, chargesKey: String, feeScheduleId: UUID?, at: Instant)
    fun remember(payerId: UUID, requestId: UUID, digest: String, codeId: UUID)
    fun find(id: UUID): StoredPaymentCode?
    fun lock(id: UUID): StoredPaymentCode?
    fun created(id: UUID, providerQrId: String?, providerPaymentId: String?, pix: PixCode?, checkoutUrl: String?)
    fun changeStatus(id: UUID, from: Set<String>, to: String, at: Instant): Boolean
    fun customer(accountId: UUID, payerId: UUID, name: String, cpf: String): CardCustomer
    fun saveCustomer(accountId: UUID, payerId: UUID, providerId: String)
}

data class PixQr(val id: String, val payload: String, val image: String)
data class CardCharge(val paymentId: String, val checkoutUrl: String)
data class PixKey(val id: String, val key: String, val active: Boolean)

/** Asaas isolado (plano B do sandbox fica local aqui): QR estático com valor, cobrança no cartão, chave da subconta. */
interface PaymentCodeProvider {
    fun createPixQr(accountId: UUID, addressKey: String, codeId: UUID, amountCents: Long, description: String): PixQr
    /** Cria o cliente se faltar e a cobrança CREDIT_CARD; resultado incerto é consultado por externalReference, nunca recriado. */
    fun createCardCharge(accountId: UUID, codeId: UUID, customer: CardCustomer, amountCents: Long, commissionCents: Long,
                         dueDate: LocalDate, description: String, savedCustomer: (String) -> Unit): CardCharge?
    fun cancelPixQr(accountId: UUID, qrId: String): Boolean
    fun cancelPayment(accountId: UUID, paymentId: String): Boolean
    fun ensurePixKey(accountId: UUID): PixKey?
}

class ProviderUnavailable : RuntimeException("Provider unavailable")

/** O atleta paga pelo app (FIN-011): suas cobranças em aberto, código Pix ou cartão e o CPF uma vez. */
class AthletePayments(private val store: PaymentCodeStore, private val ledger: GroupChargeLedger,
    private val conditions: FinancialConditions, private val eligibility: ReceivablesEligibility,
    private val rollout: ReceivablesRolloutAccess, private val provider: PaymentCodeProvider,
    private val clock: Clock, private val zone: ZoneId = ZoneId.of("America/Sao_Paulo")) {

    fun me(groupId: UUID, request: FinancialRequest): FinancialResult<AthletePaymentState> = safely(request) {
        store.transaction {
            val group = ledger.group(groupId) ?: fail(FinancialError.NOT_FOUND)
            if (!ledger.isActiveMember(groupId, request.actorUserId)) fail(FinancialError.NOT_FOUND)
            val account = store.receivingAccount(groupId)
            val active = account != null && available(account)
            val today = LocalDate.now(clock.withZone(zone))
            val charges = ledger.openCharges(groupId, request.actorUserId).map { it.view(today) }
            AthletePaymentState(active, if (active) ledger.displayName(group.ownerUserId) else null,
                if (active) listOf(PaymentMethod.PIX, PaymentMethod.CARD) else emptyList(),
                active && account!!.pixKey != null, store.hasDocument(request.actorUserId), charges, charges.sumOf { it.payableCents })
        }
    }

    fun create(groupId: UUID, method: PaymentMethod, chargeIds: List<UUID>, request: FinancialRequest): FinancialResult<PaymentCodeView> = safely(request) {
        require(chargeIds.isNotEmpty() && chargeIds.size <= 100 && chargeIds.distinct().size == chargeIds.size)
        val key = chargeIds.sortedBy { it.toString() }.joinToString(",")
        val digest = paymentDigest("CODE:$groupId:$method:$key")
        val code = store.transaction {
            store.requested(request.actorUserId, request.requestId)?.let { (id, prior) ->
                if (prior != digest) fail(FinancialError.CONFLICT)
                return@transaction store.find(id)!!
            }
            ledger.group(groupId) ?: fail(FinancialError.NOT_FOUND)
            if (!ledger.isActiveMember(groupId, request.actorUserId)) fail(FinancialError.NOT_FOUND)
            val account = store.receivingAccount(groupId) ?: fail(FinancialError.OPERATIONS_DISABLED)
            if (!available(account)) fail(FinancialError.OPERATIONS_DISABLED)
            if (!account.webhookReady) fail(FinancialError.CONFIGURATION_UNAVAILABLE)
            store.lockPayer(groupId, request.actorUserId)
            val charges = ledger.lock(chargeIds)
            if (charges.size != chargeIds.size || charges.any { it.groupId != groupId || it.memberUserId != request.actorUserId || it.status != "PENDING" })
                fail(FinancialError.CHARGE_NOT_PAYABLE)
            if (method == PaymentMethod.PIX && account.pixKey == null) fail(FinancialError.PIX_NOT_READY)
            if (method == PaymentMethod.CARD && !store.hasDocument(request.actorUserId)) fail(FinancialError.DOCUMENT_REQUIRED)
            val amount = charges.sumOf { it.payableCents }
            val reusable = store.live(groupId, request.actorUserId, method, key)?.takeIf { it.amountCents == amount && it.status == "PENDING" }
            val stored = reusable ?: run {
                val schedule = conditions.current(setOf(method), clock.instant(), account.accountId).singleOrNull()
                    ?: fail(FinancialError.CONFIGURATION_UNAVAILABLE)
                StoredPaymentCode(UUID.randomUUID(), account.accountId, groupId, request.actorUserId, method, amount,
                    FeeCalculator.commission(amount, schedule), chargeIds, "CREATING", null, null, null, null)
                    .also { store.insert(it, key, schedule.id, clock.instant()) }
            }
            store.remember(request.actorUserId, request.requestId, digest, stored.id)
            stored
        }
        if (code.status == "CREATING") issue(code)
        view(store.transaction { refreshed(code.id) })
    }

    fun get(codeId: UUID, request: FinancialRequest): FinancialResult<PaymentCodeView> = safely(request) {
        val code = store.transaction { store.find(codeId)?.takeIf { it.payerId == request.actorUserId } ?: fail(FinancialError.NOT_FOUND) }
        if (code.status == "CREATING") issue(code, recoverOnly = true)
        view(store.transaction { refreshed(codeId) })
    }

    fun saveDocument(cpf: String, request: FinancialRequest): FinancialResult<DocumentState> = safely(request) {
        val digits = cpf.filter(Char::isDigit)
        require(validCpf(digits) && cpf.all { it.isDigit() || it in ".- " })
        store.transaction { store.saveDocument(request.actorUserId, digits, clock.instant()) }
        DocumentState(true)
    }

    /** Rede fora da transação. QR que falhou some (ninguém tem o código); cartão incerto fica CREATING até a consulta. */
    private fun issue(code: StoredPaymentCode, recoverOnly: Boolean = false) {
        val account = store.transaction { store.receivingAccount(code.groupId) }
        val description = "Saqz · " + (ledger.group(code.groupId)?.name ?: "grupo")
        when (code.method) {
            PaymentMethod.PIX -> {
                if (recoverOnly) return
                val qr = try { provider.createPixQr(code.accountId, account?.pixKey ?: fail(FinancialError.PIX_NOT_READY), code.id, code.amountCents, description) }
                catch (failure: Failed) { store.transaction { store.changeStatus(code.id, setOf("CREATING"), "CANCELLED", clock.instant()) }; throw failure }
                catch (_: Exception) {
                    store.transaction { store.changeStatus(code.id, setOf("CREATING"), "CANCELLED", clock.instant()) }
                    fail(FinancialError.PROVIDER_UNAVAILABLE)
                }
                store.transaction { store.created(code.id, qr.id, null, PixCode(qr.payload, qr.image), null) }
            }
            PaymentMethod.CARD -> {
                val customer = store.transaction {
                    val cpf = store.document(code.payerId) ?: fail(FinancialError.DOCUMENT_REQUIRED)
                    store.customer(code.accountId, code.payerId, ledger.displayName(code.payerId) ?: "Atleta Saqz", cpf)
                }
                val charge = try {
                    provider.createCardCharge(code.accountId, code.id, customer, code.amountCents, code.commissionCents,
                        LocalDate.now(clock.withZone(zone)), description) { id -> store.transaction { store.saveCustomer(code.accountId, code.payerId, id) } }
                } catch (_: Exception) { null }
                if (charge == null) {
                    // Nada encontrado: segue CREATING e a próxima leitura consulta de novo; o app pode pedir outro código.
                    if (!recoverOnly) fail(FinancialError.PROVIDER_UNAVAILABLE)
                    return
                }
                store.transaction { store.created(code.id, null, charge.paymentId, null, charge.checkoutUrl) }
            }
        }
    }

    /** Expira quando alguma cobrança do código deixou de estar em aberto (FIN-011): o app pede um novo. */
    private fun refreshed(id: UUID): StoredPaymentCode {
        val code = store.lock(id)!!
        if (code.status !in setOf("CREATING", "PENDING")) return code
        val charges = ledger.lock(code.chargeIds)
        if (charges.size == code.chargeIds.size && charges.all { it.status == "PENDING" }) return code
        store.changeStatus(id, setOf("CREATING", "PENDING"), "EXPIRED", clock.instant())
        return store.find(id)!!
    }

    private fun available(account: ReceivingAccount) = account.registration == RegistrationStatus.APPROVED &&
        account.newOperationsEnabled && eligibility.forOwner(account.ownerUserId, clock.instant()).eligible &&
        rollout.availability(account.ownerUserId).backendEnabled

    private fun view(code: StoredPaymentCode) = PaymentCodeView(code.id, code.method, code.amountCents, code.chargeIds,
        if (code.status == "CREATING") "PENDING" else code.status,
        code.pix?.takeIf { code.status == "PENDING" }, code.checkoutUrl?.takeIf { code.status == "PENDING" }?.let(::CardCheckout))

    private fun LedgerCharge.view(today: LocalDate) = OpenChargeView(id, kind, gameId, billingMonth, guestName, dueDate,
        payableCents, dueDate < today)

    private class Failed(val code: FinancialError) : RuntimeException()
    private fun fail(error: FinancialError): Nothing = throw Failed(error)
    private fun <T> safely(request: FinancialRequest, block: () -> T): FinancialResult<T> = try {
        FinancialResult.Success(block(), request.requestId)
    } catch (e: Failed) { FinancialResult.Failure(e.code, request.requestId) }
    catch (_: FinancialRequestConflict) { FinancialResult.Failure(FinancialError.CONFLICT, request.requestId) }
    catch (_: IllegalArgumentException) { FinancialResult.Failure(FinancialError.INVALID_INPUT, request.requestId) }

    companion object {
        fun validCpf(digits: String): Boolean {
            if (!digits.matches(Regex("[0-9]{11}")) || digits.toSet().size == 1) return false
            fun check(length: Int): Int {
                val sum = (0 until length).sumOf { (digits[it] - '0') * (length + 1 - it) }
                return (sum * 10 % 11).let { if (it == 10) 0 else it }
            }
            return check(9) == digits[9] - '0' && check(10) == digits[10] - '0'
        }
    }
}

/** Fatos do Asaas lidos de novo a cada aviso: o corpo do webhook só acorda o processamento. */
data class ObservedPayment(val id: String, val valueCents: Long, val netCents: Long?, val method: PaymentMethod?,
    val status: String, val externalReference: String?, val pixQrCodeId: String?, val payerName: String?, val deleted: Boolean)

interface PaymentFactsProvider {
    fun payment(accountId: UUID, paymentId: String): ObservedPayment
    /** Comissão do Pix para a carteira do Saqz; incerto → procura pela referência da operação, nunca envia de novo às cegas. */
    fun transfer(accountId: UUID, operationId: UUID, amountCents: Long): String?
    fun findTransfer(accountId: UUID, operationId: UUID): String?
}
