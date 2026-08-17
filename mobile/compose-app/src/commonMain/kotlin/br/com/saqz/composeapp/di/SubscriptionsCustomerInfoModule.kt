package br.com.saqz.composeapp.di

import br.com.saqz.access.domain.session.SessionGateway
import br.com.saqz.domain.onSuccess
import br.com.saqz.subscriptions.domain.subscription.CustomerInfo
import br.com.saqz.subscriptions.domain.subscription.CustomerInfoProvider
import org.koin.dsl.module

/**
 * A implementação real de [CustomerInfoProvider] — mora aqui, e não em
 * `subscriptions:presentation`, porque é o único módulo que enxerga tanto
 * `access:domain.SessionGateway` quanto `subscriptions:domain` (AGENTS.md §1: nenhuma
 * feature depende de outra). `SubscriptionGateViewModel` só conhece o contrato.
 */
internal class SessionCustomerInfoProvider(
    private val sessionGateway: SessionGateway,
) : CustomerInfoProvider {
    override suspend fun current(): CustomerInfo? {
        var info: CustomerInfo? = null
        sessionGateway.bootstrap().onSuccess { info = CustomerInfo(it.user.displayName, it.user.email) }
        return info
    }
}

internal val subscriptionsCustomerInfoModule = module {
    single<CustomerInfoProvider> { SessionCustomerInfoProvider(get()) }
}
