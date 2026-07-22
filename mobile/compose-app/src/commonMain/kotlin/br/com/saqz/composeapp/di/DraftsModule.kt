package br.com.saqz.composeapp.di

import br.com.saqz.groups.port.GroupDraftStorePort
import br.com.saqz.groups.presentation.finance.charges.MonthlyChargeDraftStorePort
import br.com.saqz.groups.presentation.finance.expenses.ExpenseDraftStorePort
import br.com.saqz.groups.presentation.games.editor.GameDraftStorePort
import org.koin.dsl.module

class SaqzDraftStores(
    val groupDrafts: GroupDraftStorePort,
    val gameDrafts: GameDraftStorePort,
    val monthlyChargeDrafts: MonthlyChargeDraftStorePort,
    val expenseDrafts: ExpenseDraftStorePort,
)

internal val draftsModule = module {
    single<GroupDraftStorePort> { get<SaqzDraftStores>().groupDrafts }
    single<GameDraftStorePort> { get<SaqzDraftStores>().gameDrafts }
    single<MonthlyChargeDraftStorePort> { get<SaqzDraftStores>().monthlyChargeDrafts }
    single<ExpenseDraftStorePort> { get<SaqzDraftStores>().expenseDrafts }
}
