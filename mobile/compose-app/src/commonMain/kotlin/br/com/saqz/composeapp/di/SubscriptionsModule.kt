package br.com.saqz.composeapp.di

import br.com.saqz.subscriptions.data.purchase.KtorPurchaseInformationGateway
import br.com.saqz.subscriptions.data.subscription.KtorSubscriptionGateway
import br.com.saqz.subscriptions.data.trial.KtorTrialGateway
import br.com.saqz.subscriptions.domain.purchase.PurchaseInformationGateway
import br.com.saqz.subscriptions.domain.subscription.SubscriptionGateway
import br.com.saqz.subscriptions.domain.trial.TrialGateway
import org.koin.dsl.module

internal val subscriptionsDataModule = module {
    single<SubscriptionGateway> { KtorSubscriptionGateway(get()) }
    single<TrialGateway> { KtorTrialGateway(get()) }
    single<PurchaseInformationGateway> { KtorPurchaseInformationGateway(get()) }
}
