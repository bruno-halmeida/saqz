package br.com.saqz.composeapp.di

import br.com.saqz.subscriptions.data.purchase.KtorPurchaseInformationGateway
import br.com.saqz.subscriptions.data.subscription.KtorSubscriptionGateway
import br.com.saqz.subscriptions.domain.purchase.PurchaseInformationGateway
import br.com.saqz.subscriptions.domain.subscription.SubscriptionGateway
import org.koin.dsl.module

internal val subscriptionsDataModule = module {
    single<SubscriptionGateway> { KtorSubscriptionGateway(get()) }
    single<PurchaseInformationGateway> { KtorPurchaseInformationGateway(get()) }
}
