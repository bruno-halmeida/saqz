package br.com.saqz.composeapp.subscriptiongate

import br.com.saqz.domain.StoreLaunchPolicy
import br.com.saqz.subscriptions.domain.appstore.AppStoreSubscriptionGateway
import br.com.saqz.subscriptions.domain.googleplay.GooglePlaySubscriptionGateway
import br.com.saqz.subscriptions.domain.port.AppStorePurchasesPort
import br.com.saqz.subscriptions.domain.port.GooglePlayPurchasesPort
import br.com.saqz.subscriptions.domain.port.WebCheckoutPort
import br.com.saqz.subscriptions.domain.subscription.SubscriptionProvider
import br.com.saqz.subscriptions.presentation.appstore.AppStorePaywallStore
import br.com.saqz.subscriptions.presentation.appstore.AppStorePaywallViewModel
import br.com.saqz.subscriptions.presentation.appstore.AppStoreTransactionSync
import br.com.saqz.subscriptions.presentation.googleplay.GooglePlayPaywallStore
import br.com.saqz.subscriptions.presentation.googleplay.GooglePlayPurchaseSync
import br.com.saqz.subscriptions.presentation.store.PaywallStore
import br.com.saqz.subscriptions.presentation.store.StorePurchaseSync
import kotlinx.coroutines.CoroutineScope
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * O que o app sabe da loja deste aparelho: App Store no iOS, Google Play no Android. Sem port
 * nativo (testes, plataforma sem loja) este objeto existe sem loja e a navegação segue o
 * portão de assinatura de sempre.
 */
internal class StoreCheckout(
    /** A loja deste aparelho, como o backend a chama em `provider`. */
    val deviceStore: SubscriptionProvider?,
    val sync: StorePurchaseSync?,
    private val enabled: Boolean,
    private val manage: ((() -> Unit) -> Unit)?,
    /** Só no web app: o checkout do site no lugar da loja que o navegador não tem. */
    private val webCheckout: WebCheckoutPort? = null,
) {
    val purchasesAvailable: Boolean get() = sync != null && enabled

    /** Contratar pela web (Asaas), pela loja ou pelo checkout do site: libera "Conhecer planos" e afins. */
    val anyPurchaseAvailable: Boolean get() = StoreLaunchPolicy.purchases || purchasesAvailable || webCheckout != null

    /**
     * No web app, assinar abre o checkout do site em vez do portão com a loja. Devolve `false` onde não
     * há checkout web (Android e iOS), para quem chamou seguir para o portão de sempre.
     */
    fun openWebCheckout(done: () -> Unit): Boolean {
        val checkout = webCheckout ?: return false
        checkout.open(done)
        return true
    }

    val canManageSubscriptions: Boolean get() = manage != null

    fun manageSubscriptions(done: () -> Unit) {
        val open = manage ?: return done()
        open(done)
    }
}

internal fun storeCheckoutModule(
    appStore: AppStorePurchasesPort?,
    googlePlay: GooglePlayPurchasesPort?,
    webCheckout: WebCheckoutPort? = null,
): Module = module {
    when {
        appStore != null -> {
            single<AppStorePurchasesPort> { appStore }
            single { AppStoreTransactionSync(appStore, get<AppStoreSubscriptionGateway>(), get<CoroutineScope>()) }
            single<PaywallStore> { AppStorePaywallStore(appStore, get(), get()) }
            single {
                StoreCheckout(
                    deviceStore = SubscriptionProvider.AppStore,
                    sync = get<AppStoreTransactionSync>(),
                    enabled = StoreLaunchPolicy.appStorePurchases,
                    manage = { done -> appStore.showAppStoreSubscriptionManagement { done() } },
                )
            }
        }
        googlePlay != null -> {
            single<GooglePlayPurchasesPort> { googlePlay }
            single { GooglePlayPurchaseSync(googlePlay, get<GooglePlaySubscriptionGateway>(), get<CoroutineScope>()) }
            single<PaywallStore> { GooglePlayPaywallStore(googlePlay, get(), get()) }
            single {
                StoreCheckout(
                    deviceStore = SubscriptionProvider.GooglePlay,
                    sync = get<GooglePlayPurchaseSync>(),
                    enabled = StoreLaunchPolicy.googlePlayPurchases,
                    // A central do Play abre fora do app; a volta atualiza a tela pelo onResume.
                    manage = { done ->
                        googlePlay.showGooglePlaySubscriptionManagement(productId = null)
                        done()
                    },
                )
            }
        }
        else -> single { StoreCheckout(deviceStore = null, sync = null, enabled = false, manage = null, webCheckout = webCheckout) }
    }
    if (appStore != null || googlePlay != null) {
        viewModel { AppStorePaywallViewModel(store = get(), subscriptions = get()) }
    }
}
