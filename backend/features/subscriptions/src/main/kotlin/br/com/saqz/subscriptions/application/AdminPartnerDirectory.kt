package br.com.saqz.subscriptions.application

import java.time.Instant
import java.util.UUID

data class Partner(
    val id: UUID,
    val name: String,
    val contactName: String?,
    val contactPhone: String?,
    val contactEmail: String?,
    val notes: String?,
    val active: Boolean,
    val createdAt: Instant,
)

data class PartnerCreate(
    val name: String,
    val contactName: String? = null,
    val contactPhone: String? = null,
    val contactEmail: String? = null,
    val notes: String? = null,
)

sealed interface PartnerCreateResult {
    data class Created(val partner: Partner) : PartnerCreateResult
    data object DuplicateName : PartnerCreateResult
}

/** Cupom de um parceiro, de desconto (DISCOUNT) ou de teste (TRIAL). */
data class PartnerCoupon(val id: UUID, val type: String, val code: String, val campaign: String?)

/** Números de quem veio do parceiro (primeiro toque, `customer_acquisition.partner_id`). */
data class PartnerMetrics(
    /** Usaram um cupom do parceiro (primeiro cupom de parceiro da pessoa). */
    val users: Int,
    /** Pagaram ao menos uma vez depois do primeiro uso. */
    val payers: Int,
    /** Com acesso pago hoje. */
    val activeSubscribers: Int,
    /** Receita bruta em BRL, sem teste nem estorno. */
    val revenueCents: Long,
    /** Receita por plataforma: SITE, APP_STORE, GOOGLE_PLAY. */
    val revenueBySource: Map<String, Long>,
    /** Pagamentos que a loja ainda não informou o valor (ou em outra moeda): contados, sem somar. */
    val paymentsWithoutValue: Int,
)

data class PartnerSummary(val partner: Partner, val coupons: List<PartnerCoupon>, val metrics: PartnerMetrics)

/** Pessoa que veio do parceiro e pagou. Dado pessoal: só no admin. */
data class PartnerPayer(
    val userId: UUID,
    val name: String?,
    val email: String?,
    val plan: String?,
    val cycle: String?,
    /** SITE, APP_STORE ou GOOGLE_PLAY do primeiro pagamento. */
    val source: String,
    val firstCouponUseAt: Instant,
    val firstPaymentAt: Instant,
    val totalPaidCents: Long,
    val paymentsWithoutValue: Int,
    /** ACTIVE, PAST_DUE ou CANCELED. */
    val status: String,
)

data class PartnerReport(
    val partner: Partner,
    val coupons: List<PartnerCoupon>,
    val from: Instant?,
    val to: Instant?,
    val metrics: PartnerMetrics,
    val payers: List<PartnerPayer>,
)

/**
 * Parceiros do adm-web. Atribuição pela view `customer_acquisition` (V185) e dinheiro pela
 * `subscription_payments`: nunca se infere pagamento pelo preço do plano.
 */
interface AdminPartnerDirectory {
    fun list(now: Instant): List<PartnerSummary>

    fun create(command: PartnerCreate): PartnerCreateResult

    fun exists(partnerId: UUID): Boolean

    /** Encerra a parceria (some das opções ao criar cupom); os cupons seguem como estão. */
    fun end(partnerId: UUID, now: Instant): Boolean

    /** [from]/[to] filtram o primeiro uso do cupom e os pagamentos; nulos = desde o início / até agora. */
    fun report(partnerId: UUID, from: Instant?, to: Instant?, now: Instant): PartnerReport?
}
