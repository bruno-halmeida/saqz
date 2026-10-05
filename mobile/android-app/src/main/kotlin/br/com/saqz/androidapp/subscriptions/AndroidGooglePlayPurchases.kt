package br.com.saqz.androidapp.subscriptions

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
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
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/**
 * Play Billing atrás do [GooglePlayPurchasesPort]. Nunca reconhece nem consome compra: quem
 * reconhece é o backend, depois de ler a assinatura no Google (`docs/subscriptions/google-play.md`).
 *
 * Todo callback volta na main thread, como os demais ports nativos. Uma compra por vez: o
 * resultado do `launchBillingFlow` chega por [onPurchasesUpdated] e vai para quem pediu; o que
 * chega sem compra em andamento (pagamento pendente que caiu, por exemplo) vai para o listener,
 * guardado até ele ser registrado.
 */
internal class AndroidGooglePlayPurchases(
    context: Context,
    private val activity: () -> Activity,
    clientFactory: (PurchasesUpdatedListener) -> BillingClient = { listener -> productionClient(context, listener) },
) : GooglePlayPurchasesPort, PurchasesUpdatedListener {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val client = clientFactory(this)
    private val detailsByProduct = mutableMapOf<String, ProductDetails>()
    private val waitingForConnection = mutableListOf<Pair<() -> Unit, (String) -> Unit>>()
    private var connecting = false
    private var inFlightPurchase: GooglePlayPurchaseCallback? = null
    private var listener: GooglePlayPurchaseListener? = null
    private val unclaimed = mutableListOf<GooglePlayPurchase>()

    override fun canMakeGooglePlayPayments(): Boolean = true

    override fun loadGooglePlayProducts(productIds: List<String>, done: GooglePlayProductsCallback) {
        connected(onFailure = { done.onGooglePlayProducts(GooglePlayProductsResult.Failed(it)) }) {
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(
                    productIds.map { id ->
                        QueryProductDetailsParams.Product.newBuilder().setProductId(id).setProductType(ProductType.SUBS).build()
                    },
                )
                .build()
            client.queryProductDetailsAsync(params) { result, details ->
                main.post {
                    if (result.responseCode != BillingResponseCode.OK) {
                        done.onGooglePlayProducts(GooglePlayProductsResult.Failed(result.describe()))
                    } else {
                        val products = details.productDetailsList
                        products.forEach { detailsByProduct[it.productId] = it }
                        done.onGooglePlayProducts(GooglePlayProductsResult.Loaded(products.map { it.toDomain() }))
                    }
                }
            }
        }
    }

    override fun purchaseGooglePlayProduct(
        productId: String,
        offerToken: String,
        obfuscatedAccountId: String,
        done: GooglePlayPurchaseCallback,
    ) {
        val details = detailsByProduct[productId]
            ?: return done.onGooglePlayPurchase(GooglePlayPurchaseResult.Failed("produto não carregado: $productId"))
        if (inFlightPurchase != null) {
            return done.onGooglePlayPurchase(GooglePlayPurchaseResult.Failed("compra em andamento"))
        }
        connected(onFailure = { done.onGooglePlayPurchase(GooglePlayPurchaseResult.Failed(it)) }) {
            val params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(details)
                            .setOfferToken(offerToken)
                            .build(),
                    ),
                )
                .setObfuscatedAccountId(obfuscatedAccountId)
                .build()
            inFlightPurchase = done
            val result = runCatching { client.launchBillingFlow(activity(), params) }
                .getOrElse { failure -> return@connected finishInFlight(GooglePlayPurchaseResult.Failed(failure.message.orEmpty())) }
            if (result.responseCode != BillingResponseCode.OK) finishInFlight(result.toPurchaseFailure())
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        main.post {
            val mapped = purchases.orEmpty().mapNotNull { it.toDomain() }
            if (inFlightPurchase == null) {
                if (result.responseCode == BillingResponseCode.OK) mapped.forEach(::publish)
                return@post
            }
            when (result.responseCode) {
                BillingResponseCode.OK -> {
                    val purchase = mapped.firstOrNull()
                    finishInFlight(
                        when {
                            purchase == null -> GooglePlayPurchaseResult.Failed("compra sem retorno do Google Play")
                            purchase.pending -> GooglePlayPurchaseResult.Pending
                            else -> GooglePlayPurchaseResult.Purchased(purchase)
                        },
                    )
                    // Pendente que concluir depois chega de novo por aqui e vai para o listener.
                    mapped.drop(1).forEach(::publish)
                }
                else -> finishInFlight(result.toPurchaseFailure())
            }
        }
    }

    override fun readGooglePlaySubscriptionPurchases(done: GooglePlayPurchasesCallback) {
        connected(onFailure = { done.onGooglePlayPurchases(GooglePlayPurchasesResult.Failed(it)) }) {
            val params = QueryPurchasesParams.newBuilder().setProductType(ProductType.SUBS).build()
            client.queryPurchasesAsync(params) { result, purchases ->
                main.post {
                    if (result.responseCode != BillingResponseCode.OK) {
                        done.onGooglePlayPurchases(GooglePlayPurchasesResult.Failed(result.describe()))
                    } else {
                        done.onGooglePlayPurchases(GooglePlayPurchasesResult.Loaded(purchases.mapNotNull { it.toDomain() }))
                    }
                }
            }
        }
    }

    override fun listenForGooglePlayPurchases(listener: GooglePlayPurchaseListener) {
        this.listener = listener
        val pending = unclaimed.toList()
        unclaimed.clear()
        pending.forEach(listener::onGooglePlayPurchaseUpdate)
        // Conecta já na abertura: compra pendente que concluir com o app aberto só chega com o
        // cliente conectado.
        connected(onFailure = {}) {}
    }

    override fun showGooglePlaySubscriptionManagement(productId: String?) {
        val url = buildString {
            append("https://play.google.com/store/account/subscriptions?package=").append(appContext.packageName)
            if (productId != null) append("&sku=").append(productId)
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        runCatching { activity().startActivity(intent) }
            .onFailure { appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun publish(purchase: GooglePlayPurchase) {
        val target = listener
        if (target == null) unclaimed += purchase else target.onGooglePlayPurchaseUpdate(purchase)
    }

    private fun finishInFlight(result: GooglePlayPurchaseResult) {
        val callback = inFlightPurchase ?: return
        inFlightPurchase = null
        callback.onGooglePlayPurchase(result)
    }

    private fun connected(onFailure: (String) -> Unit, block: () -> Unit) {
        if (client.isReady) return block()
        waitingForConnection += block to onFailure
        if (connecting) return
        connecting = true
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                main.post {
                    connecting = false
                    val waiting = waitingForConnection.toList()
                    waitingForConnection.clear()
                    if (result.responseCode == BillingResponseCode.OK) {
                        waiting.forEach { (run, _) -> run() }
                    } else {
                        waiting.forEach { (_, fail) -> fail(result.describe()) }
                    }
                }
            }

            // Com `enableAutoServiceReconnection` a próxima chamada reconecta sozinha.
            override fun onBillingServiceDisconnected() {
                main.post { connecting = false }
            }
        })
    }

    private companion object {
        fun productionClient(context: Context, listener: PurchasesUpdatedListener): BillingClient =
            BillingClient.newBuilder(context.applicationContext)
                .setListener(listener)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .enableAutoServiceReconnection()
                .build()
    }
}

private fun BillingResult.describe(): String = "Play Billing $responseCode: $debugMessage"

private fun BillingResult.toPurchaseFailure(): GooglePlayPurchaseResult = when (responseCode) {
    BillingResponseCode.USER_CANCELED -> GooglePlayPurchaseResult.Cancelled
    else -> GooglePlayPurchaseResult.Failed(describe())
}

/** Só planos base (sem oferta): o teste grátis é do backend, não do Play. */
private fun ProductDetails.toDomain() = GooglePlayProduct(
    productId = productId,
    name = name,
    basePlans = subscriptionOfferDetails.orEmpty()
        .filter { it.offerId == null }
        .mapNotNull { offer ->
            val price = offer.pricingPhases.pricingPhaseList.lastOrNull()?.formattedPrice ?: return@mapNotNull null
            GooglePlayBasePlan(basePlanId = offer.basePlanId, offerToken = offer.offerToken, formattedPrice = price)
        },
)

internal fun Purchase.toDomain(): GooglePlayPurchase? {
    val productId = products.firstOrNull() ?: return null
    return GooglePlayPurchase(
        purchaseToken = purchaseToken,
        productId = productId,
        acknowledged = isAcknowledged,
        pending = purchaseState != Purchase.PurchaseState.PURCHASED,
    )
}
