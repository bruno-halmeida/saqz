package br.com.saqz.composeapp.di

import br.com.saqz.groups.domain.group.GroupCreationEntitlement
import br.com.saqz.groups.domain.group.GroupTrialAccessInfo
import br.com.saqz.groups.domain.group.GroupTrialAccessPort
import br.com.saqz.domain.GroupId
import br.com.saqz.subscriptions.domain.trial.TrialGateway
import org.koin.dsl.module

/**
 * A implementação real de [GroupCreationEntitlement] — mora aqui, e não em
 * `groups:presentation`, porque é o único módulo que enxerga tanto o contrato da
 * apresentação de grupos quanto `subscriptions:domain.TrialGateway` (AGENTS.md §1:
 * nenhuma feature depende de outra). `GroupListViewModel` só conhece o contrato.
 *
 * O backend revalida no POST do 2a — esta porta é só roteamento do "+" de 2n, então toda
 * falha (erro, `NotFound`, `entitled = false` ou limite atingido) cai no Fluxo 8, que é
 * sempre um desvio seguro. A elegibilidade e a vaga vêm do read-model de trial; o POST segue
 * sendo a autoridade final.
 */
internal val groupCreationEntitlementModule = module {
    single<GroupCreationEntitlement> {
        TrialGroupCreationEntitlement(get())
    }
    single<GroupTrialAccessPort> {
        val gateway = get<TrialGateway>()
        GroupTrialAccessPort { groupId: GroupId ->
            when (val result = gateway.groupTrial(groupId)) {
                is br.com.saqz.domain.SaqzResult.Success ->
                    GroupTrialAccessInfo(result.value.endsAt, result.value.readOnly, result.value.isOwner)
                is br.com.saqz.domain.SaqzResult.Failure -> null
            }
        }
    }
}
