package br.com.saqz.composeapp.subscriptiongate

import br.com.saqz.domain.StoreLaunchPolicy
import br.com.saqz.subscriptions.domain.appstore.AppStoreSubscriptionGateway
import br.com.saqz.subscriptions.domain.port.AppStorePurchasesPort
import br.com.saqz.subscriptions.presentation.appstore.AppStorePaywallViewModel
import br.com.saqz.subscriptions.presentation.appstore.AppStoreTransactionSync
import kotlinx.coroutines.CoroutineScope
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * O que o app sabe da App Store neste aparelho. O port só existe no iOS; no Android este
 * objeto existe sem ele, e a navegação segue o portão de assinatura de sempre.
 */
internal class AppStoreCheckout(
    private val port: AppStorePurchasesPort?,
    val sync: AppStoreTransactionSync?,
) {
    val purchasesAvailable: Boolean get() = port != null && sync != null && StoreLaunchPolicy.appStorePurchases

    /** Contratar pela web (Asaas) ou pela App Store: é o que libera "Conhecer planos" e afins. */
    val anyPurchaseAvailable: Boolean get() = StoreLaunchPolicy.purchases || purchasesAvailable

    val canManageSubscriptions: Boolean get() = port != null

    fun manageSubscriptions(done: () -> Unit) {
        val target = port ?: return done()
        target.showAppStoreSubscriptionManagement { done() }
    }
}

internal fun appStoreCheckoutModule(port: AppStorePurchasesPort?): Module = module {
    if (port == null) {
        single { AppStoreCheckout(port = null, sync = null) }
    } else {
        single<AppStorePurchasesPort> { port }
        single { AppStoreTransactionSync(port, get<AppStoreSubscriptionGateway>(), get<CoroutineScope>()) }
        single { AppStoreCheckout(port = port, sync = get()) }
        viewModel { AppStorePaywallViewModel(port = port, sync = get(), appStore = get(), subscriptions = get()) }
    }
}
