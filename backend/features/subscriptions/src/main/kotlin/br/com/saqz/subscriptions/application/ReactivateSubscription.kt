package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.application.SubscriptionPricing.discountedPriceCents
import br.com.saqz.subscriptions.domain.Subscription
import br.com.saqz.subscriptions.domain.SubscriptionStatus
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

sealed interface ReactivateSubscriptionResult {
    data class Success(val subscription: Subscription) : ReactivateSubscriptionResult
    data object NotFound : ReactivateSubscriptionResult

    /** A assinatura não está cancelada: nada a reativar (também é o que a segunda chamada recebe). */
    data object NotCanceled : ReactivateSubscriptionResult

    /** O período pago já acabou (ou foi estornado): o caminho é assinar de novo, não reativar. */
    data object AccessEnded : ReactivateSubscriptionResult

    /** Assinatura de cartão sem o token guardado: sem ele o Asaas não cobra sozinho. */
    data object CardUnavailable : ReactivateSubscriptionResult

    /** Já existe acesso por outra assinatura (loja): reativar a web seria cobrar duas vezes. */
    data object AlreadySubscribed : ReactivateSubscriptionResult
}

/**
 * Desfaz o cancelamento enquanto o período pago ainda vale. O cancelamento apaga a assinatura no
 * Asaas e isso não volta; então cria-se outra, com a primeira cobrança no dia em que o período
 * pago acaba — nunca antes, para não cobrar duas vezes o mesmo período — e a linha local passa a
 * apontar para ela. A trava da linha serializa com o webhook SUBSCRIPTION_DELETED do cancelamento.
 */
class ReactivateSubscription(
    private val subscriptions: SubscriptionRepository,
    private val asaasGateway: AsaasGateway,
    private val transaction: SubscriptionsTransactionRunner,
    private val clock: Clock,
    private val coupons: CouponRepository? = null,
    private val cardTokens: CreditCardTokenLookup = CreditCardTokenLookup { null },
    private val appStoreSubscriptions: AppStoreSubscriptionRepository? = null,
    private val googlePlaySubscriptions: GooglePlaySubscriptionRepository? = null,
) {
    fun execute(ownerUserId: UUID, remoteIp: String?): ReactivateSubscriptionResult = transaction.inTransaction {
        subscriptions.lockOwner(ownerUserId)
        val current = subscriptions.findByOwnerUserIdForUpdate(ownerUserId)
            ?: return@inTransaction ReactivateSubscriptionResult.NotFound
        val now = clock.instant()
        if (current.canceledAt == null) return@inTransaction ReactivateSubscriptionResult.NotCanceled
        if (!current.isEntitlingAt(now) || current.firstConfirmedAt == null) {
            return@inTransaction ReactivateSubscriptionResult.AccessEnded
        }
        if (appStoreSubscriptions?.findByOwner(ownerUserId)?.any { it.isEntitlingAt(now) } == true ||
            googlePlaySubscriptions?.findByOwner(ownerUserId)?.any { it.isEntitlingAt(now) } == true
        ) {
            return@inTransaction ReactivateSubscriptionResult.AlreadySubscribed
        }
        val billingType = current.billingType ?: AsaasBillingType.PIX
        val token = if (billingType == AsaasBillingType.CREDIT_CARD) {
            cardTokens.tokenFor(current.asaasSubscriptionId)
                ?: return@inTransaction ReactivateSubscriptionResult.CardUnavailable
        } else {
            null
        }
        // O Asaas rejeita vencimento no passado; no último dia do período a cobrança sai hoje.
        val endDate = LocalDate.ofInstant(current.currentPeriodEnd, ZoneOffset.UTC)
        val today = LocalDate.ofInstant(now, ZoneOffset.UTC)
        val plan = current.pendingPlan ?: current.plan
        val newId = asaasGateway.createSubscriptionStartingAt(
            asaasCustomerId = current.asaasCustomerId,
            plan = plan,
            cycle = current.cycle,
            valueCents = discountedPriceCents(plan, current.cycle, activeCoupon(current)),
            billingType = billingType,
            nextDueDate = if (endDate.isBefore(today)) today else endDate,
            // Mesma chave para o mesmo cancelamento: um retry depois de falha local não cria outra no Asaas.
            idempotencyKey = "subscription-reactivate:$ownerUserId:${current.asaasSubscriptionId}",
            creditCardToken = token,
            remoteIp = remoteIp,
        )
        val reactivated = current.copy(
            asaasSubscriptionId = newId,
            status = SubscriptionStatus.ACTIVE,
            canceledAt = null,
        )
        subscriptions.save(reactivated)
        ReactivateSubscriptionResult.Success(reactivated)
    }

    private fun activeCoupon(current: Subscription) =
        if (SubscriptionPricing.hasActiveCouponDiscount(current.couponId, current.couponCyclesRemaining)) {
            current.couponId?.let { coupons?.findById(it) }
        } else {
            null
        }
}
