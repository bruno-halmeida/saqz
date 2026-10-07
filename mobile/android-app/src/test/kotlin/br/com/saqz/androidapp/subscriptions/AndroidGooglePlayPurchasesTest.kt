package br.com.saqz.androidapp.subscriptions

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import br.com.saqz.subscriptions.domain.port.GooglePlayBasePlan
import br.com.saqz.subscriptions.domain.port.GooglePlayProductsResult
import br.com.saqz.subscriptions.domain.port.GooglePlayPurchase
import br.com.saqz.subscriptions.domain.port.GooglePlayPurchaseResult
import br.com.saqz.subscriptions.domain.port.GooglePlayPurchasesResult
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.AcknowledgePurchaseResponseListener
import com.android.billingclient.api.AlternativeBillingOnlyAvailabilityListener
import com.android.billingclient.api.AlternativeBillingOnlyInformationDialogListener
import com.android.billingclient.api.AlternativeBillingOnlyReportingDetailsListener
import com.android.billingclient.api.BillingChoiceInfoResponseListener
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingConfigResponseListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingFlowParams.SubscriptionUpdateParams.ReplacementMode
import com.android.billingclient.api.BillingProgramAvailabilityListener
import com.android.billingclient.api.BillingProgramInformationDialogListener
import com.android.billingclient.api.BillingProgramInformationDialogParams
import com.android.billingclient.api.BillingProgramReportingDetailsListener
import com.android.billingclient.api.BillingProgramReportingDetailsParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.ConsumeResponseListener
import com.android.billingclient.api.ExternalOfferAvailabilityListener
import com.android.billingclient.api.ExternalOfferInformationDialogListener
import com.android.billingclient.api.ExternalOfferReportingDetailsListener
import com.android.billingclient.api.GetBillingChoiceInfoParams
import com.android.billingclient.api.GetBillingConfigParams
import com.android.billingclient.api.InAppMessageParams
import com.android.billingclient.api.InAppMessageResponseListener
import com.android.billingclient.api.LaunchExternalLinkParams
import com.android.billingclient.api.LaunchExternalLinkResponseListener
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetailsResponseListener
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesResponseListener
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsResult
import com.android.billingclient.api.QueryPurchasesParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AndroidGooglePlayPurchasesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private lateinit var client: FakeBillingClient
    private val purchases = AndroidGooglePlayPurchases(context, { activity }) { listener ->
        FakeBillingClient(listener).also { client = it }
    }

    @Test
    fun loadsOnlyTheBasePlansWithThePlayPriceOverASingleConnection() {
        client.products = listOf(organizador())
        val results = mutableListOf<GooglePlayProductsResult>()

        purchases.loadGooglePlayProducts(listOf(ORGANIZADOR)) { results += it }
        idle()
        purchases.loadGooglePlayProducts(listOf(ORGANIZADOR)) { results += it }
        idle()

        val loaded = results.map { (it as GooglePlayProductsResult.Loaded).products.single() }
        assertEquals(
            listOf(
                GooglePlayBasePlan("mensal", "token-mensal", "R$ 59,90"),
                GooglePlayBasePlan("anual", "token-anual", "R$ 539,90"),
            ),
            loaded.first().basePlans,
        )
        assertEquals(2, loaded.size)
        assertEquals(1, client.connections)
    }

    @Test
    fun aFailedConnectionFailsEveryCallWaitingForIt() {
        client.setupResult = result(BillingResponseCode.BILLING_UNAVAILABLE)
        val products = mutableListOf<GooglePlayProductsResult>()
        val owned = mutableListOf<GooglePlayPurchasesResult>()
        client.holdSetup = true

        purchases.loadGooglePlayProducts(listOf(ORGANIZADOR)) { products += it }
        purchases.readGooglePlaySubscriptionPurchases { owned += it }
        client.finishSetup()
        idle()

        assertEquals(1, client.connections)
        assertTrue(products.single() is GooglePlayProductsResult.Failed)
        assertTrue(owned.single() is GooglePlayPurchasesResult.Failed)
    }

    @Test
    fun aPendingPaymentAnswersThePurchaseAndItsConclusionGoesToTheListener() {
        loadOrganizador()
        val results = mutableListOf<GooglePlayPurchaseResult>()

        purchases.purchaseGooglePlayProduct(ORGANIZADOR, "token-mensal", ACCOUNT) { results += it }
        idle()
        client.updates.onPurchasesUpdated(result(BillingResponseCode.OK), mutableListOf(purchase(pending = true)))
        idle()
        // Pagamento aprovado depois, ainda sem listener: fica guardado até ele chegar.
        client.updates.onPurchasesUpdated(result(BillingResponseCode.OK), mutableListOf(purchase()))
        idle()
        val updates = mutableListOf<GooglePlayPurchase>()
        purchases.listenForGooglePlayPurchases { updates += it }

        assertEquals(1, client.launches)
        assertEquals(listOf<GooglePlayPurchaseResult>(GooglePlayPurchaseResult.Pending), results)
        assertEquals(listOf(GooglePlayPurchase("compra-1", ORGANIZADOR, acknowledged = false, pending = false)), updates)
    }

    @Test
    fun aConcludedPurchaseGoesToTheCallerAndNeverToTheListener() {
        loadOrganizador()
        val updates = mutableListOf<GooglePlayPurchase>()
        purchases.listenForGooglePlayPurchases { updates += it }
        val results = mutableListOf<GooglePlayPurchaseResult>()

        purchases.purchaseGooglePlayProduct(ORGANIZADOR, "token-anual", ACCOUNT) { results += it }
        idle()
        client.updates.onPurchasesUpdated(result(BillingResponseCode.OK), mutableListOf(purchase()))
        idle()

        val purchased = GooglePlayPurchase("compra-1", ORGANIZADOR, acknowledged = false, pending = false)
        assertEquals(listOf<GooglePlayPurchaseResult>(GooglePlayPurchaseResult.Purchased(purchased)), results)
        assertTrue(updates.isEmpty())
    }

    @Test
    fun cancelledAndRefusedFlowsEndThePurchaseAndAllowTheNextOne() {
        loadOrganizador()
        val results = mutableListOf<GooglePlayPurchaseResult>()

        purchases.purchaseGooglePlayProduct(ORGANIZADOR, "token-mensal", ACCOUNT) { results += it }
        idle()
        client.updates.onPurchasesUpdated(result(BillingResponseCode.USER_CANCELED), null)
        idle()
        client.launchResult = result(BillingResponseCode.ITEM_ALREADY_OWNED)
        purchases.purchaseGooglePlayProduct(ORGANIZADOR, "token-mensal", ACCOUNT) { results += it }
        idle()

        assertEquals(GooglePlayPurchaseResult.Cancelled, results[0])
        assertTrue(results[1] is GooglePlayPurchaseResult.Failed)
        assertEquals(2, client.launches)
    }

    @Test
    fun anExistingSubscriptionIsReplacedInsteadOfBilledTwice() {
        loadOrganizador()
        client.purchases = listOf(purchase(token = "assinatura-atual", productId = TITULAR, accountId = ACCOUNT))
        val results = mutableListOf<GooglePlayPurchaseResult>()

        purchases.purchaseGooglePlayProduct(ORGANIZADOR, "token-mensal", ACCOUNT) { results += it }
        idle()

        assertEquals(1, client.launches)
        val update = requireNotNull(client.lastFlow?.subscriptionUpdate())
        assertTrue(update.has("assinatura-atual"))
        assertTrue(update.has(ReplacementMode.CHARGE_PRORATED_PRICE))
        assertTrue(results.isEmpty())
    }

    @Test
    fun aNewSubscriberBuysWithoutReplacingAnything() {
        loadOrganizador()

        purchases.purchaseGooglePlayProduct(ORGANIZADOR, "token-mensal", ACCOUNT) {}
        idle()

        assertEquals(1, client.launches)
        val update = client.lastFlow?.subscriptionUpdate()
        assertFalse(update != null && (update.has(ReplacementMode.CHARGE_PRORATED_PRICE) || update.has(ReplacementMode.DEFERRED)))
    }

    @Test
    fun aSubscriptionOfAnotherSaqzAccountIsNotTouched() {
        loadOrganizador()
        client.purchases = listOf(purchase(token = "de-outra-conta", productId = TITULAR, accountId = "outra-conta"))
        val results = mutableListOf<GooglePlayPurchaseResult>()

        purchases.purchaseGooglePlayProduct(ORGANIZADOR, "token-mensal", ACCOUNT) { results += it }
        idle()

        assertEquals(0, client.launches)
        assertEquals(listOf<GooglePlayPurchaseResult>(GooglePlayPurchaseResult.OwnedByAnotherAccount), results)
        // A vaga da compra foi liberada: a próxima tentativa segue.
        client.purchases = emptyList()
        purchases.purchaseGooglePlayProduct(ORGANIZADOR, "token-mensal", ACCOUNT) {}
        idle()
        assertEquals(1, client.launches)
    }

    @Test
    fun upgradesChargeTheDifferenceNowAndDowngradesWaitForRenewal() {
        val mode = AndroidGooglePlayPurchases::replacementMode
        assertEquals(ReplacementMode.CHARGE_PRORATED_PRICE, mode(TITULAR, ORGANIZADOR, "anual"))
        assertEquals(ReplacementMode.CHARGE_PRORATED_PRICE, mode(ORGANIZADOR, ILIMITADO, "mensal"))
        assertEquals(ReplacementMode.DEFERRED, mode(ILIMITADO, ORGANIZADOR, "mensal"))
        assertEquals(ReplacementMode.DEFERRED, mode(ORGANIZADOR, TITULAR, "anual"))
        // Mesmo plano: o mensal custa mais por mês que o anual.
        assertEquals(ReplacementMode.CHARGE_PRORATED_PRICE, mode(ORGANIZADOR, ORGANIZADOR, "mensal"))
        assertEquals(ReplacementMode.DEFERRED, mode(ORGANIZADOR, ORGANIZADOR, "anual"))
    }

    @Test
    fun aProductThatWasNotLoadedIsNotPurchased() {
        val results = mutableListOf<GooglePlayPurchaseResult>()

        purchases.purchaseGooglePlayProduct(ORGANIZADOR, "token-mensal", ACCOUNT) { results += it }
        idle()

        assertTrue(results.single() is GooglePlayPurchaseResult.Failed)
        assertEquals(0, client.launches)
    }

    @Test
    fun readsTheSubscriptionsWithAcknowledgementAndPendingState() {
        client.purchases = listOf(
            purchase(token = "reconhecida", acknowledged = true),
            purchase(token = "pendente", pending = true),
        )
        val results = mutableListOf<GooglePlayPurchasesResult>()

        purchases.readGooglePlaySubscriptionPurchases { results += it }
        idle()

        assertEquals(
            listOf(
                GooglePlayPurchase("reconhecida", ORGANIZADOR, acknowledged = true, pending = false),
                GooglePlayPurchase("pendente", ORGANIZADOR, acknowledged = false, pending = true),
            ),
            (results.single() as GooglePlayPurchasesResult.Loaded).purchases,
        )
    }

    @Test
    fun managementOpensThePlaySubscriptionCenterForTheProduct() {
        purchases.showGooglePlaySubscriptionManagement(ORGANIZADOR)
        val specific = shadowOf(activity).nextStartedActivity
        purchases.showGooglePlaySubscriptionManagement(null)
        val general = shadowOf(activity).nextStartedActivity

        assertEquals(Intent.ACTION_VIEW, specific.action)
        assertEquals(
            "https://play.google.com/store/account/subscriptions?package=${context.packageName}&sku=$ORGANIZADOR",
            specific.dataString,
        )
        assertEquals("https://play.google.com/store/account/subscriptions?package=${context.packageName}", general.dataString)
    }

    @Test
    fun theAppDeclaresThePlayBillingPermission() {
        val permissions = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty()

        assertTrue(permissions.contains("com.android.vending.BILLING"))
    }

    private fun loadOrganizador() {
        client.products = listOf(organizador())
        purchases.loadGooglePlayProducts(listOf(ORGANIZADOR)) {}
        idle()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private companion object {
        const val ORGANIZADOR = "app.saqz.organizador"
        const val TITULAR = "app.saqz.titular"
        const val ILIMITADO = "app.saqz.ilimitado"
        const val ACCOUNT = "3f0c2a9e-0000-4000-8000-000000000001"

        fun result(code: Int): BillingResult = BillingResult.newBuilder().setResponseCode(code).build()

        fun purchase(
            token: String = "compra-1",
            acknowledged: Boolean = false,
            pending: Boolean = false,
            productId: String = ORGANIZADOR,
            accountId: String? = null,
        ) = Purchase(
            """{"orderId":"GPA.1","packageName":"app.saqz","productId":"$productId","purchaseTime":1,""" +
                """"purchaseState":${if (pending) 4 else 0},"purchaseToken":"$token","acknowledged":$acknowledged,""" +
                (accountId?.let { """"obfuscatedAccountId":"$it",""" } ?: "") +
                """"autoRenewing":true}""",
            "assinatura",
        )

        /** Os getters do BillingFlowParams são ofuscados: a troca de assinatura é achada pelo tipo do campo. */
        fun BillingFlowParams.subscriptionUpdate(): BillingFlowParams.SubscriptionUpdateParams? = javaClass.declaredFields
            .firstOrNull { it.type == BillingFlowParams.SubscriptionUpdateParams::class.java }
            ?.apply { isAccessible = true }
            ?.get(this) as BillingFlowParams.SubscriptionUpdateParams?

        /** Token substituído ou modo de troca, entre os campos da própria troca. */
        fun BillingFlowParams.SubscriptionUpdateParams.has(value: Any): Boolean = javaClass.declaredFields
            .filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .any { field -> field.isAccessible = true; field.get(this) == value }

        /** Dois planos base e uma oferta, que o app ignora: o teste grátis é do backend. */
        fun organizador(): ProductDetails = productDetails(
            """{"productId":"$ORGANIZADOR","type":"subs","title":"Organizador (Saqz)","name":"Organizador",""" +
                """"description":"","skuDetailsToken":"detalhes","subscriptionOfferDetails":[""" +
                basePlan("mensal", "R$ 59,90", "P1M") + "," +
                basePlan("anual", "R$ 539,90", "P1Y") + "," +
                """{"basePlanId":"mensal","offerId":"promo","offerIdToken":"token-promo","offerTags":[],""" +
                """"pricingPhases":[${phase("R$ 0,00", "P1W")},${phase("R$ 59,90", "P1M")}]}""" +
                "]}",
        )

        fun basePlan(id: String, price: String, period: String) =
            """{"basePlanId":"$id","offerIdToken":"token-$id","offerTags":[],"pricingPhases":[${phase(price, period)}]}"""

        fun phase(price: String, period: String) =
            """{"formattedPrice":"$price","priceAmountMicros":1,"priceCurrencyCode":"BRL",""" +
                """"billingPeriod":"$period","billingCycleCount":0,"recurrenceMode":1}"""

        // O construtor é interno à biblioteca: é o JSON que o Play devolve.
        fun productDetails(json: String): ProductDetails = ProductDetails::class.java
            .getDeclaredConstructor(String::class.java)
            .apply { isAccessible = true }
            .newInstance(json)
    }
}

private class FakeBillingClient(val updates: PurchasesUpdatedListener) : BillingClient() {
    var setupResult: BillingResult = BillingResult.newBuilder().setResponseCode(BillingResponseCode.OK).build()
    var holdSetup = false
    var products: List<ProductDetails> = emptyList()
    var purchases: List<Purchase> = emptyList()
    var launchResult: BillingResult = BillingResult.newBuilder().setResponseCode(BillingResponseCode.OK).build()
    var connections = 0
    var launches = 0
    var lastFlow: BillingFlowParams? = null
    private var ready = false
    private var heldSetup: BillingClientStateListener? = null

    fun finishSetup() {
        val listener = heldSetup ?: return
        heldSetup = null
        ready = setupResult.responseCode == BillingResponseCode.OK
        listener.onBillingSetupFinished(setupResult)
    }

    override fun startConnection(listener: BillingClientStateListener) {
        connections++
        heldSetup = listener
        if (!holdSetup) finishSetup()
    }

    override fun isReady(): Boolean = ready

    override fun getConnectionState(): Int = if (ready) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED

    override fun endConnection() {
        ready = false
    }

    override fun queryProductDetailsAsync(params: QueryProductDetailsParams, listener: ProductDetailsResponseListener) {
        listener.onProductDetailsResponse(ok(), QueryProductDetailsResult.create(products, emptyList()))
    }

    override fun queryPurchasesAsync(params: QueryPurchasesParams, listener: PurchasesResponseListener) {
        listener.onQueryPurchasesResponse(ok(), purchases)
    }

    override fun launchBillingFlow(activity: Activity, params: BillingFlowParams): BillingResult {
        launches++
        lastFlow = params
        return launchResult
    }

    // Quem reconhece a compra é o backend: o app nunca chama nenhum dos dois.
    override fun acknowledgePurchase(params: AcknowledgePurchaseParams, listener: AcknowledgePurchaseResponseListener) =
        error("o app não reconhece compras")

    override fun consumeAsync(params: ConsumeParams, listener: ConsumeResponseListener) = error("o app não consome compras")

    override fun isFeatureSupported(feature: String): BillingResult = unused()

    override fun showAlternativeBillingOnlyInformationDialog(
        activity: Activity,
        listener: AlternativeBillingOnlyInformationDialogListener,
    ): BillingResult = unused()

    override fun showExternalOfferInformationDialog(
        activity: Activity,
        listener: ExternalOfferInformationDialogListener,
    ): BillingResult = unused()

    override fun showInAppMessages(
        activity: Activity,
        params: InAppMessageParams,
        listener: InAppMessageResponseListener,
    ): BillingResult = unused()

    override fun createAlternativeBillingOnlyReportingDetailsAsync(
        listener: AlternativeBillingOnlyReportingDetailsListener,
    ) = unused()

    override fun createBillingProgramReportingDetailsAsync(
        params: BillingProgramReportingDetailsParams,
        listener: BillingProgramReportingDetailsListener,
    ) = unused()

    override fun createExternalOfferReportingDetailsAsync(listener: ExternalOfferReportingDetailsListener) = unused()

    override fun getBillingChoiceInfoAsync(params: GetBillingChoiceInfoParams, listener: BillingChoiceInfoResponseListener) =
        unused()

    override fun getBillingConfigAsync(params: GetBillingConfigParams, listener: BillingConfigResponseListener) = unused()

    override fun isAlternativeBillingOnlyAvailableAsync(listener: AlternativeBillingOnlyAvailabilityListener) = unused()

    override fun isBillingProgramAvailableAsync(program: Int, listener: BillingProgramAvailabilityListener) = unused()

    override fun isExternalOfferAvailableAsync(listener: ExternalOfferAvailabilityListener) = unused()

    override fun launchExternalLink(
        activity: Activity,
        params: LaunchExternalLinkParams,
        listener: LaunchExternalLinkResponseListener,
    ) = unused()

    override fun showBillingProgramInformationDialog(
        activity: Activity,
        params: BillingProgramInformationDialogParams,
        listener: BillingProgramInformationDialogListener,
    ) = unused()

    private fun ok() = BillingResult.newBuilder().setResponseCode(BillingResponseCode.OK).build()

    private fun unused(): Nothing = throw UnsupportedOperationException()
}
