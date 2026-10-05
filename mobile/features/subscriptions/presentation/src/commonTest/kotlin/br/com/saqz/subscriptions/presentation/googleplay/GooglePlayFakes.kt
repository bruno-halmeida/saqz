package br.com.saqz.subscriptions.presentation.googleplay

import br.com.saqz.domain.SaqzResult
import br.com.saqz.subscriptions.domain.googleplay.GooglePlaySubmissionError
import br.com.saqz.subscriptions.domain.googleplay.GooglePlaySubscriptionGateway
import br.com.saqz.subscriptions.domain.port.GooglePlayBasePlan
import br.com.saqz.subscriptions.domain.port.GooglePlayProduct
import br.com.saqz.subscriptions.domain.port.GooglePlayProductsCallback
import br.com.saqz.subscriptions.domain.port.GooglePlayProductsResult
import br.com.saqz.subscriptions.domain.port.GooglePlayPurchase
import br.com.saqz.subscriptions.domain.port.GooglePlayPurchaseCallback
import br.com.saqz.subscriptions.domain.port.GooglePlayPurchaseListener
import br.com.saqz.subscriptions.domain.port.GooglePlayPurchaseResult
import br.com.saqz.subscriptions.domain.port.GooglePlayPurchasesCallback
import br.com.saqz.subscriptions.domain.port.GooglePlayPurchasesPort
import br.com.saqz.subscriptions.domain.port.GooglePlayPurchasesResult
import br.com.saqz.subscriptions.domain.subscription.GooglePlayProductIds
import br.com.saqz.subscriptions.domain.subscription.MySubscription
import br.com.saqz.subscriptions.domain.subscription.Plan
import br.com.saqz.subscriptions.domain.subscription.PlanCatalogItem
import br.com.saqz.subscriptions.domain.subscription.SubscriptionCycle
import br.com.saqz.subscriptions.domain.subscription.SubscriptionProvider
import br.com.saqz.subscriptions.domain.subscription.SubscriptionStatus
import br.com.saqz.subscriptions.domain.subscription.SubscriptionUsage

internal const val PLAY_ACCOUNT = "8f0c2c1e-0000-4000-8000-000000000001"
internal const val PLAY_ORGANIZADOR = "app.saqz.organizador"

internal fun playPurchase(token: String, acknowledged: Boolean = false, pending: Boolean = false) =
    GooglePlayPurchase(purchaseToken = token, productId = PLAY_ORGANIZADOR, acknowledged = acknowledged, pending = pending)

internal fun playSubscription(entitled: Boolean = true) = MySubscription(
    status = if (entitled) SubscriptionStatus.Active else SubscriptionStatus.Canceled,
    entitled = entitled,
    plan = Plan.Organizador,
    cycle = SubscriptionCycle.Monthly,
    currentPeriodEnd = "2026-11-05T00:00:00Z",
    usage = SubscriptionUsage(groupsUsed = 0, groupsLimit = 3),
    canceledAt = null,
    provider = SubscriptionProvider.GooglePlay,
    autoRenew = entitled,
)

internal fun playCatalogItem() = PlanCatalogItem(
    id = Plan.Organizador,
    monthlyPriceCents = 5_990,
    annualPriceCents = 53_910,
    maxGroups = 3,
    maxAthletes = null,
    multiAdmin = false,
    reports = false,
    whatsappSla = false,
    googlePlay = GooglePlayProductIds(productId = PLAY_ORGANIZADOR, monthlyBasePlanId = "mensal", annualBasePlanId = "anual"),
)

internal class FakeGooglePlayPort : GooglePlayPurchasesPort {
    var canMakePayments = true
    var productsResult: GooglePlayProductsResult = GooglePlayProductsResult.Loaded(
        listOf(
            GooglePlayProduct(
                productId = PLAY_ORGANIZADOR,
                name = "Organizador",
                basePlans = listOf(
                    GooglePlayBasePlan("mensal", "offer-mensal", "R$ 59,90"),
                    GooglePlayBasePlan("anual", "offer-anual", "R$ 539,90"),
                ),
            ),
        ),
    )
    var purchaseResult: GooglePlayPurchaseResult = GooglePlayPurchaseResult.Purchased(playPurchase("token-1"))
    var purchasesResult: GooglePlayPurchasesResult = GooglePlayPurchasesResult.Loaded(emptyList())
    val requestedProducts = mutableListOf<List<String>>()
    val purchases = mutableListOf<Triple<String, String, String>>()
    var listener: GooglePlayPurchaseListener? = null
    val managementOpened = mutableListOf<String?>()

    override fun canMakeGooglePlayPayments() = canMakePayments

    override fun loadGooglePlayProducts(productIds: List<String>, done: GooglePlayProductsCallback) {
        requestedProducts += productIds
        done.onGooglePlayProducts(productsResult)
    }

    override fun purchaseGooglePlayProduct(
        productId: String,
        offerToken: String,
        obfuscatedAccountId: String,
        done: GooglePlayPurchaseCallback,
    ) {
        purchases += Triple(productId, offerToken, obfuscatedAccountId)
        done.onGooglePlayPurchase(purchaseResult)
    }

    override fun readGooglePlaySubscriptionPurchases(done: GooglePlayPurchasesCallback) =
        done.onGooglePlayPurchases(purchasesResult)

    override fun listenForGooglePlayPurchases(listener: GooglePlayPurchaseListener) {
        this.listener = listener
    }

    override fun showGooglePlaySubscriptionManagement(productId: String?) {
        managementOpened += productId
    }
}

internal class FakeGooglePlayGateway : GooglePlaySubscriptionGateway {
    var accountResult: SaqzResult<String, GooglePlaySubmissionError> = SaqzResult.Success(PLAY_ACCOUNT)

    /** Resposta por token; o que não estiver aqui usa [defaultSubmission]. */
    val submissions = mutableMapOf<String, SaqzResult<MySubscription, GooglePlaySubmissionError>>()
    var defaultSubmission: SaqzResult<MySubscription, GooglePlaySubmissionError> = SaqzResult.Success(playSubscription())
    val submitted = mutableListOf<Pair<String, String>>()

    override suspend fun obfuscatedAccountId() = accountResult

    override suspend fun submitPurchase(productId: String, purchaseToken: String): SaqzResult<MySubscription, GooglePlaySubmissionError> {
        submitted += productId to purchaseToken
        return submissions[purchaseToken] ?: defaultSubmission
    }
}
