package br.com.saqz.composeapp.di

import br.com.saqz.domain.onSuccess
import br.com.saqz.groups.domain.group.GroupCreationEntitlement
import br.com.saqz.subscriptions.domain.subscription.SubscriptionGateway
import org.koin.dsl.module

/**
 * A implementação real de [GroupCreationEntitlement] — mora aqui, e não em
 * `groups:presentation`, porque é o único módulo que enxerga tanto o contrato da
 * apresentação de grupos quanto `subscriptions:domain.SubscriptionGateway` (AGENTS.md §1:
 * nenhuma feature depende de outra). `GroupListViewModel` só conhece o contrato.
 *
 * O backend revalida no POST do 2a — esta porta é só roteamento do "+" de 2n, então toda
 * falha (erro, `NotFound`, `entitled = false` ou limite atingido) cai no Fluxo 8, que é
 * sempre um desvio seguro. `entitled` vem do próprio backend (`Subscription.isEntitlingAt`),
 * a mesma regra do POST — o cliente não reconstrói o predicado.
 */
internal val groupCreationEntitlementModule = module {
    single<GroupCreationEntitlement> {
        val gateway = get<SubscriptionGateway>()
        GroupCreationEntitlement {
            var can = false
            gateway.mySubscription().onSuccess { sub ->
                val limit = sub.usage.groupsLimit
                can = sub.entitled && (limit == null || sub.usage.groupsUsed < limit)
            }
            can
        }
    }
}
