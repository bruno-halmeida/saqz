package br.com.saqz.composeapp.navigation

import androidx.navigation3.runtime.NavKey
import br.com.saqz.groups.presentation.details.GroupDetailsEffect
import br.com.saqz.groups.presentation.navigation.FinanceRoute
import kotlin.test.Test
import kotlin.test.assertEquals

class GroupOnboardingNavigationTest {
    @Test fun financeGuideOpensSettlementForTheCompletedGame() {
        val stack = mutableListOf<NavKey>()
        stack.onDetailsEffect(GroupDetailsEffect.OpenSettlement("group", "completed"), {})
        assertEquals(listOf<NavKey>(FinanceRoute.GameSettlement("group", "completed")), stack)
    }
}
