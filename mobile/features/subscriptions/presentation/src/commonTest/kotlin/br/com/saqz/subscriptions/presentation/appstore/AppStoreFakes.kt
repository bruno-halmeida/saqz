package br.com.saqz.subscriptions.presentation.appstore

import br.com.saqz.domain.SaqzResult
import br.com.saqz.subscriptions.domain.appstore.AppStoreSubmissionError
import br.com.saqz.subscriptions.domain.appstore.AppStoreSubscriptionGateway
import br.com.saqz.subscriptions.domain.port.AppStoreManagementCallback
import br.com.saqz.subscriptions.domain.port.AppStoreProduct
import br.com.saqz.subscriptions.domain.port.AppStoreProductsCallback
import br.com.saqz.subscriptions.domain.port.AppStoreProductsResult
import br.com.saqz.subscriptions.domain.port.AppStorePurchaseCallback
import br.com.saqz.subscriptions.domain.port.AppStorePurchaseResult
import br.com.saqz.subscriptions.domain.port.AppStorePurchasesPort
import br.com.saqz.subscriptions.domain.port.AppStoreSignedTransaction
import br.com.saqz.subscriptions.domain.port.AppStoreTransactionListener
import br.com.saqz.subscriptions.domain.port.AppStoreTransactionsCallback
import br.com.saqz.subscriptions.domain.port.AppStoreTransactionsResult
import br.com.saqz.subscriptions.domain.subscription.AppStoreProductIds
import br.com.saqz.subscriptions.domain.subscription.CanceledSubscription
import br.com.saqz.subscriptions.domain.subscription.ChangedPlan
import br.com.saqz.subscriptions.domain.subscription.MySubscription
import br.com.saqz.subscriptions.domain.subscription.Plan
import br.com.saqz.subscriptions.domain.subscription.PlanCatalogItem
import br.com.saqz.subscriptions.domain.subscription.Receipt
import br.com.saqz.subscriptions.domain.subscription.SubscriptionCycle
import br.com.saqz.subscriptions.domain.subscription.SubscriptionError
import br.com.saqz.subscriptions.domain.subscription.SubscriptionGateway
import br.com.saqz.subscriptions.domain.subscription.SubscriptionProvider
import br.com.saqz.subscriptions.domain.subscription.SubscriptionStatus
import br.com.saqz.subscriptions.domain.subscription.SubscriptionUsage

internal const val ACCOUNT_TOKEN = "8f0c2c1e-0000-4000-8000-000000000001"
internal const val ORGANIZADOR_MENSAL = "app.saqz.organizador.mensal"
internal const val ORGANIZADOR_ANUAL = "app.saqz.organizador.anual"

internal fun transaction(id: String) = AppStoreSignedTransaction(transactionId = id, signedTransaction = "jws-$id")

internal fun appStoreSubscription(entitled: Boolean = true, plan: Plan = Plan.Organizador) = MySubscription(
    status = if (entitled) SubscriptionStatus.Active else SubscriptionStatus.Canceled,
    entitled = entitled,
    plan = plan,
    cycle = SubscriptionCycle.Monthly,
    currentPeriodEnd = "2026-11-05T00:00:00Z",
    usage = SubscriptionUsage(groupsUsed = 0, groupsLimit = 3),
    canceledAt = null,
    provider = SubscriptionProvider.AppStore,
    autoRenew = entitled,
)

internal class FakeAppStorePort : AppStorePurchasesPort {
    var canMakePayments = true
    var productsResult: AppStoreProductsResult = AppStoreProductsResult.Loaded(
        listOf(
            AppStoreProduct(ORGANIZADOR_MENSAL, "Organizador mensal", "R$ 59,90"),
            AppStoreProduct(ORGANIZADOR_ANUAL, "Organizador anual", "R$ 539,90"),
        ),
    )
    var purchaseResult: AppStorePurchaseResult = AppStorePurchaseResult.Purchased(transaction("tx-1"))
    var unfinished: AppStoreTransactionsResult = AppStoreTransactionsResult.Loaded(emptyList())
    var restored: AppStoreTransactionsResult = AppStoreTransactionsResult.Loaded(emptyList())
    val requestedProducts = mutableListOf<List<String>>()
    val purchases = mutableListOf<Pair<String, String>>()
    val finished = mutableListOf<String>()
    var listener: AppStoreTransactionListener? = null
    var managementOpened = 0

    override fun canMakeAppStorePayments() = canMakePayments

    override fun loadAppStoreProducts(productIds: List<String>, done: AppStoreProductsCallback) {
        requestedProducts += productIds
        done.onAppStoreProducts(productsResult)
    }

    override fun purchaseAppStoreProduct(productId: String, appAccountToken: String, done: AppStorePurchaseCallback) {
        purchases += productId to appAccountToken
        done.onAppStorePurchase(purchaseResult)
    }

    override fun finishAppStoreTransaction(transactionId: String) {
        finished += transactionId
    }

    override fun readUnfinishedAppStoreTransactions(done: AppStoreTransactionsCallback) =
        done.onAppStoreTransactions(unfinished)

    override fun restoreAppStorePurchases(done: AppStoreTransactionsCallback) = done.onAppStoreTransactions(restored)

    override fun listenForAppStoreTransactions(listener: AppStoreTransactionListener) {
        this.listener = listener
    }

    override fun showAppStoreSubscriptionManagement(done: AppStoreManagementCallback) {
        managementOpened++
        done.onAppStoreManagementClosed()
    }
}

internal class FakeAppStoreGateway : AppStoreSubscriptionGateway {
    var tokenResult: SaqzResult<String, AppStoreSubmissionError> = SaqzResult.Success(ACCOUNT_TOKEN)

    /** Resposta por JWS; o que não estiver aqui usa [defaultSubmission]. */
    val submissions = mutableMapOf<String, SaqzResult<MySubscription, AppStoreSubmissionError>>()
    var defaultSubmission: SaqzResult<MySubscription, AppStoreSubmissionError> = SaqzResult.Success(appStoreSubscription())
    val submitted = mutableListOf<String>()

    override suspend fun appAccountToken() = tokenResult

    override suspend fun submitTransaction(signedTransaction: String): SaqzResult<MySubscription, AppStoreSubmissionError> {
        submitted += signedTransaction
        return submissions[signedTransaction] ?: defaultSubmission
    }
}

internal class FakeCatalogGateway : SubscriptionGateway {
    var subscriptionResult: SaqzResult<MySubscription, SubscriptionError> = SaqzResult.Failure(SubscriptionError.NotFound)
    var plansResult: SaqzResult<List<PlanCatalogItem>, SubscriptionError> = SaqzResult.Success(
        listOf(
            PlanCatalogItem(
                id = Plan.Organizador,
                monthlyPriceCents = 5_990,
                annualPriceCents = 53_910,
                maxGroups = 3,
                maxAthletes = null,
                multiAdmin = false,
                reports = false,
                whatsappSla = false,
                appStoreProductIds = AppStoreProductIds(monthly = ORGANIZADOR_MENSAL, annual = ORGANIZADOR_ANUAL),
            ),
        ),
    )

    override suspend fun mySubscription() = subscriptionResult

    override suspend fun listPlans() = plansResult

    override suspend fun changePlan(requestId: String, targetPlan: Plan): SaqzResult<ChangedPlan, SubscriptionError> =
        error("A compra pela App Store nunca troca plano pelo backend")

    override suspend fun cancel(): SaqzResult<CanceledSubscription, SubscriptionError> =
        error("A compra pela App Store nunca cancela pelo backend")

    override suspend fun receipts(limit: Int, offset: Int): SaqzResult<List<Receipt>, SubscriptionError> =
        SaqzResult.Success(emptyList())
}
