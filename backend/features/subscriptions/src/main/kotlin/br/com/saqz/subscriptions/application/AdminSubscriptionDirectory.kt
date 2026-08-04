package br.com.saqz.subscriptions.application

import java.time.Instant
import java.util.UUID

data class AdminSubscriptionSummary(
    val ownerUserId: UUID,
    val ownerName: String?,
    val ownerEmail: String?,
    val plan: String,
    val cycle: String,
    val status: String,
    val couponCode: String?,
    /** Preço do ciclo em centavos, já com desconto de cupom ativo aplicado. */
    val priceCents: Long,
    val currentPeriodEnd: Instant,
    val canceledAt: Instant?,
    val pastDueSince: Instant?,
    val createdAt: Instant,
)

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
 * do dono; filtros por plano e status reais; página 1-based.
 */
interface AdminSubscriptionDirectory {
    fun list(query: String?, plan: String?, status: String?, page: Int, size: Int): AdminSubscriptionPage

    fun find(ownerUserId: UUID): AdminSubscriptionDetail?
}

/**
 * Cancelamento administrativo delegado ao CancelSubscription do Fluxo 8.
 * Null quando o gateway Asaas não está configurado neste ambiente.
 */
fun interface AdminSubscriptionCanceler {
    fun cancel(ownerUserId: UUID): CancelSubscriptionResult?
}
