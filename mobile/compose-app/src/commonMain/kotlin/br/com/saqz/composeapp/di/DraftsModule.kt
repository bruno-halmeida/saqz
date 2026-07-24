package br.com.saqz.composeapp.di

import br.com.saqz.groups.port.ExpenseDraftStorePort
import br.com.saqz.groups.port.GameDraftStorePort
import br.com.saqz.groups.port.GroupDraftStorePort
import br.com.saqz.groups.port.MonthlyChargeDraftStorePort
import org.koin.dsl.module

class SaqzDraftStores(
    val groupDrafts: GroupDraftStorePort,
    val gameDrafts: GameDraftStorePort,
    val monthlyChargeDrafts: MonthlyChargeDraftStorePort,
    val expenseDrafts: ExpenseDraftStorePort,
)

internal val platformDraftsModule = module {
    single<GroupDraftStorePort> { get<SaqzDraftStores>().groupDrafts }
    single<GameDraftStorePort> { get<SaqzDraftStores>().gameDrafts }
    single<MonthlyChargeDraftStorePort> { get<SaqzDraftStores>().monthlyChargeDrafts }
    single<ExpenseDraftStorePort> { get<SaqzDraftStores>().expenseDrafts }
}
