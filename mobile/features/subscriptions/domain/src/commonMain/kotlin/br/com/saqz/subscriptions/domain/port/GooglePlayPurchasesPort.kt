package br.com.saqz.subscriptions.domain.port

/** Um plano base de uma assinatura do Play, com o preço já formatado pelo Google. */
data class GooglePlayBasePlan(
    val basePlanId: String,
    /** `offerToken` do plano base (sem oferta): é o que a compra precisa. */
    val offerToken: String,
    val formattedPrice: String,
)

data class GooglePlayProduct(
    val productId: String,
    val name: String,
    val basePlans: List<GooglePlayBasePlan>,
)

/** Uma compra de assinatura como o Play a devolve. Quem reconhece é o backend, nunca o app. */
data class GooglePlayPurchase(
    val purchaseToken: String,
    val productId: String,
    val acknowledged: Boolean,
    /** Pagamento ainda pendente (boleto, por exemplo): não vai ao backend até concluir. */
    val pending: Boolean,
)

sealed interface GooglePlayProductsResult {
    data class Loaded(val products: List<GooglePlayProduct>) : GooglePlayProductsResult
    data class Failed(val message: String) : GooglePlayProductsResult
}

sealed interface GooglePlayPurchaseResult {
    data class Purchased(val purchase: GooglePlayPurchase) : GooglePlayPurchaseResult

    /** Pagamento pendente: a compra conclui depois e chega por [GooglePlayPurchaseListener]. */
    data object Pending : GooglePlayPurchaseResult
    data object Cancelled : GooglePlayPurchaseResult

    /** A conta Google já assina o Saqz por outra conta do app: trocar de plano mexeria na assinatura dela. */
    data object OwnedByAnotherAccount : GooglePlayPurchaseResult
    data class Failed(val message: String) : GooglePlayPurchaseResult
}

sealed interface GooglePlayPurchasesResult {
    data class Loaded(val purchases: List<GooglePlayPurchase>) : GooglePlayPurchasesResult
    data class Failed(val message: String) : GooglePlayPurchasesResult
}

fun interface GooglePlayProductsCallback { fun onGooglePlayProducts(result: GooglePlayProductsResult) }
fun interface GooglePlayPurchaseCallback { fun onGooglePlayPurchase(result: GooglePlayPurchaseResult) }
fun interface GooglePlayPurchasesCallback { fun onGooglePlayPurchases(result: GooglePlayPurchasesResult) }
fun interface GooglePlayPurchaseListener { fun onGooglePlayPurchaseUpdate(purchase: GooglePlayPurchase) }

/**
 * Google Play Billing, implementado no `:android-app`. Só o Android entrega este port; no
 * iOS ele não existe. Callbacks em vez de `suspend` pela mesma regra dos outros ports nativos.
 */
interface GooglePlayPurchasesPort {
    fun canMakeGooglePlayPayments(): Boolean
    fun loadGooglePlayProducts(productIds: List<String>, done: GooglePlayProductsCallback)

    /** [obfuscatedAccountId] é o UUID da conta no Saqz: prende a compra a ela. */
    fun purchaseGooglePlayProduct(
        productId: String,
        offerToken: String,
        obfuscatedAccountId: String,
        done: GooglePlayPurchaseCallback,
    )

    /** `queryPurchasesAsync(SUBS)`: as assinaturas ativas desta conta Google. */
    fun readGooglePlaySubscriptionPurchases(done: GooglePlayPurchasesCallback)

    /** Compras que concluem fora da tela de compra (pagamento pendente que caiu, por exemplo). */
    fun listenForGooglePlayPurchases(listener: GooglePlayPurchaseListener)

    /** Central de assinaturas do Play; [productId] abre direto a assinatura quando conhecido. */
    fun showGooglePlaySubscriptionManagement(productId: String?)
}
