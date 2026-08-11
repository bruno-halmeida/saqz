package br.com.saqz.subscriptions.domain.subscription

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzError
import br.com.saqz.domain.SaqzResult

enum class Plan { Titular, Organizador, Ilimitado }

enum class SubscriptionCycle { Monthly, Annual }

enum class SubscriptionStatus { Active, PastDue, Canceled }

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
)

data class CanceledSubscription(
    val status: SubscriptionStatus,
    val canceledAt: String,
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
    data class Data(val error: DataError) : SubscriptionError
}

interface SubscriptionGateway {
    suspend fun mySubscription(): SaqzResult<MySubscription, SubscriptionError>

    suspend fun cancel(): SaqzResult<CanceledSubscription, SubscriptionError>

    /**
     * Uma página de recibos, do mais recente para o mais antigo. Sem tipo de página: quem
     * chama infere "tem mais?" comparando `resultado.size == limit` — página cheia pode ter
     * continuação, página curta acabou. `limit`/`offset` são obrigatórios de propósito
     * (VUL-120): o backend tem default próprio, e um cliente que omite volta a depender dele.
     */
    suspend fun receipts(limit: Int, offset: Int): SaqzResult<List<Receipt>, SubscriptionError>
}
