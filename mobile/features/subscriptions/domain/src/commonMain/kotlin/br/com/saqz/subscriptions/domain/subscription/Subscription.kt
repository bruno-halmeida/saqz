package br.com.saqz.subscriptions.domain.subscription

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzError
import br.com.saqz.domain.SaqzResult

enum class Plan { Titular, Organizador, Ilimitado }

enum class SubscriptionCycle { Monthly, Annual }

enum class SubscriptionStatus { Active, PastDue, Canceled }

/** Quem cobra: a web (Asaas), a App Store ou o Google Play. Troca de plano e cancelamento dependem disso. */
enum class SubscriptionProvider { Asaas, AppStore, GooglePlay }

/** Cortesia de plano dada pelo suporte: [endsAt] nulo é sem prazo. */
data class Courtesy(val plan: Plan, val endsAt: String?)

data class SubscriptionUsage(val groupsUsed: Int, val groupsLimit: Int?)

data class MySubscription(
    val status: SubscriptionStatus,
    /** Regra do backend (`Subscription.isEntitlingAt`): pode criar grupo se também houver vaga. */
    val entitled: Boolean,
    val plan: Plan,
    val cycle: SubscriptionCycle,
    val currentPeriodEnd: String,
    val usage: SubscriptionUsage,
    val canceledAt: String?,
    val pendingPlan: Plan? = null,
    val pendingPlanEffectiveAt: String? = null,
    val provider: SubscriptionProvider = SubscriptionProvider.Asaas,
    /** Renovação automática da loja (App Store ou Google Play); nulo numa assinatura da web. */
    val autoRenew: Boolean? = null,
    /**
     * Já houve pagamento confirmado. Falso só no checkout do site que nunca foi pago: o app trata como quem nunca assinou.
     * Backend anterior ao campo não manda: vale verdadeiro, como era.
     */
    val paymentConfirmed: Boolean = true,
    /** "Pagar agora": a fatura vencida do site em atraso (a mesma do aviso); nulo sem fatura. */
    val payUrl: String? = null,
    /** Cortesia ativa de quem também tem assinatura; o `/me` só manda o campo nesse caso. */
    val courtesy: Courtesy? = null,
)

/** Os dois produtos da App Store de um plano. O mapeamento é do backend (`GET /plans`). */
data class AppStoreProductIds(val monthly: String, val annual: String)

/** No Play um plano é uma assinatura com dois planos base. O mapeamento é do backend (`GET /plans`). */
data class GooglePlayProductIds(val productId: String, val monthlyBasePlanId: String, val annualBasePlanId: String)

data class PlanCatalogItem(
    val id: Plan,
    val monthlyPriceCents: Long,
    val annualPriceCents: Long,
    val maxGroups: Int?,
    val maxAthletes: Int?,
    val multiAdmin: Boolean,
    val reports: Boolean,
    val whatsappSla: Boolean,
    val appStoreProductIds: AppStoreProductIds? = null,
    val googlePlay: GooglePlayProductIds? = null,
)

data class ChangedPlan(
    val plan: Plan,
    val pendingPlan: Plan?,
    val pendingPlanEffectiveAt: String?,
    val pendingUpgradePlan: Plan?,
    val status: SubscriptionStatus,
    val chargedCents: Long?,
    val pixCopyPaste: String?,
    val invoiceUrl: String?,
    val pixQrCodeBase64: String?,
)

data class CanceledSubscription(
    val status: SubscriptionStatus,
    val canceledAt: String,
    val currentPeriodEnd: String,
)

/** Assinatura da web reativada: o fim do período pago passa a ser a próxima cobrança. */
data class ReactivatedSubscription(
    val status: SubscriptionStatus,
    val currentPeriodEnd: String,
)

data class Receipt(
    val asaasEventId: String,
    val asaasPaymentId: String?,
    val valueCents: Long?,
    val confirmedAt: String?,
    val processedAt: String,
)

sealed interface SubscriptionError : SaqzError {
    data class Validation(val error: DataError.Validation) : SubscriptionError
    data object NotFound : SubscriptionError // 404 SUBSCRIPTION_NOT_FOUND
    data object Conflict : SubscriptionError // 409 SUBSCRIPTION_CONFLICT
    data object DowngradeBlocked : SubscriptionError // 409 DOWNGRADE_BLOCKED
    data class Data(val error: DataError) : SubscriptionError
}

interface SubscriptionGateway {
    suspend fun mySubscription(): SaqzResult<MySubscription, SubscriptionError>

    /** Cortesia ativa, inclusive de quem não tem assinatura; `NotFound` sem cortesia. */
    suspend fun myCourtesy(): SaqzResult<Courtesy, SubscriptionError> =
        SaqzResult.Failure(SubscriptionError.NotFound)

    suspend fun listPlans(): SaqzResult<List<PlanCatalogItem>, SubscriptionError>

    suspend fun changePlan(
        requestId: String,
        targetPlan: Plan,
    ): SaqzResult<ChangedPlan, SubscriptionError>

    suspend fun cancel(): SaqzResult<CanceledSubscription, SubscriptionError>

    /** Desfaz o cancelamento da assinatura da web enquanto o período pago vale; sem efeito nas lojas. */
    suspend fun reactivate(): SaqzResult<ReactivatedSubscription, SubscriptionError> =
        SaqzResult.Failure(SubscriptionError.NotFound)

    /**
     * Uma página de recibos, do mais recente para o mais antigo. Sem tipo de página: quem
     * chama infere "tem mais?" comparando `resultado.size == limit` — página cheia pode ter
     * continuação, página curta acabou. `limit`/`offset` são obrigatórios de propósito
     * (VUL-120): o backend tem default próprio, e um cliente que omite volta a depender dele.
     */
    suspend fun receipts(limit: Int, offset: Int): SaqzResult<List<Receipt>, SubscriptionError>
}
