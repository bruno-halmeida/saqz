package br.com.saqz.subscriptions.application

import java.time.Instant
import java.util.UUID

data class AdminSubscriptionSummary(
    val ownerUserId: UUID,
    val ownerName: String?,
    val ownerEmail: String?,
    val plan: String,
    /** Nulo em cortesia e teste gratuito. */
    val cycle: String?,
    val status: String,
    val couponCode: String?,
    /** Preço do ciclo em centavos, já com desconto de cupom ativo aplicado; só do site. */
    val priceCents: Long?,
    /** Fim ou renovação: período do site, expiração da loja, fim da cortesia ou do teste (nulo = sem prazo). */
    val currentPeriodEnd: Instant?,
    val canceledAt: Instant?,
    val pastDueSince: Instant?,
    val createdAt: Instant,
    /** `SITE` (Asaas), `APP_STORE`, `GOOGLE_PLAY`, `COURTESY` ou `TRIAL`. */
    val source: String = SOURCE_SITE,
    /** `REAL` (houve cobrança de verdade), `TEST` (sandbox, compra de teste ou site sem pagamento) ou `NO_CHARGE` (cortesia e teste gratuito). */
    val billing: String = BILLING_REAL,
) {
    companion object {
        const val SOURCE_SITE = "SITE"
        const val BILLING_REAL = "REAL"
        val SOURCES = setOf(SOURCE_SITE, "APP_STORE", "GOOGLE_PLAY", "COURTESY", "TRIAL")
    }
}

data class AdminSubscriptionPage(
    val items: List<AdminSubscriptionSummary>,
    val total: Long,
    val page: Int,
    val size: Int,
)

data class AdminReceipt(
    val asaasEventId: String,
    val valueCents: Long?,
    val processedAt: Instant,
)

data class AdminSubscriptionDetail(
    val summary: AdminSubscriptionSummary,
    val receipts: List<AdminReceipt>,
)

/**
 * Diretório administrativo de assinaturas (adm-web · Receita). Busca por nome/e-mail
 * do dono; filtros por plano, status e origem; página 1-based. Lista todas as origens (site, lojas, cortesia e teste ativos); detalhe só do site.
 */
interface AdminSubscriptionDirectory {
    fun list(query: String?, plan: String?, status: String?, page: Int, size: Int, source: String? = null): AdminSubscriptionPage

    fun find(ownerUserId: UUID): AdminSubscriptionDetail?
}

/**
 * Cancelamento administrativo delegado ao CancelSubscription do Fluxo 8.
 * Null quando o gateway Asaas não está configurado neste ambiente.
 */
fun interface AdminSubscriptionCanceler {
    fun cancel(ownerUserId: UUID): CancelSubscriptionResult?
}
