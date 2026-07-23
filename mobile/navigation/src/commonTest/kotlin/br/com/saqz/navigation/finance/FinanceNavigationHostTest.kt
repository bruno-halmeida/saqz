package br.com.saqz.navigation.finance

import androidx.compose.material.Text
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.navigation.FinanceRoute
import br.com.saqz.groups.navigation.GroupsRoute
import br.com.saqz.navigation.NavigationSession
import br.com.saqz.navigation.ProductRoute
import br.com.saqz.navigation.ProductTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * FINNAV-01..03: structural entry inventory, owner/athlete route resolution, actual
 * predecessor back behavior, and inertness (only the supplied placeholder is composed).
 */
@OptIn(ExperimentalTestApi::class)
class FinanceNavigationHostTest {

    private fun session(
        groups: MutableList<NavKey> = mutableListOf(GroupsRoute.GroupHome),
        more: MutableList<NavKey> = mutableListOf(GroupsRoute.More),
    ) = NavigationSession(
        stacks = mapOf(
            ProductTab.HOME to mutableListOf<NavKey>(ProductRoute.AppHome),
            ProductTab.GROUPS to groups,
            ProductTab.NOTICES to mutableListOf<NavKey>(GroupsRoute.Notices),
            ProductTab.MORE to more,
        ),
        initialTab = ProductTab.GROUPS,
    )

    @Test
    fun `installs finance and own charges as distinct entries`() {
        val provider = entryProvider<NavKey> {
            installFinanceEntries(session(), titleFor = { "" }, content = {})
        }
        val keys = listOf<NavKey>(FinanceRoute.Finance, FinanceRoute.OwnCharges)

        val entries: List<NavEntry<NavKey>> = keys.map(provider)

        assertEquals(2, entries.map { it.contentKey }.toSet().size)
    }

    @Test
    fun `resolves finance route by finance-management capability`() {
        assertEquals(FinanceRoute.Finance, resolveFinanceRoute(canManageFinance = true))
        assertEquals(FinanceRoute.OwnCharges, resolveFinanceRoute(canManageFinance = false))
    }

    @Test
    fun `finance back reveals the group home predecessor`() {
        val s = session()
        s.push(resolveFinanceRoute(canManageFinance = true))
        assertEquals(FinanceRoute.Finance, s.stackFor(ProductTab.GROUPS).last())

        assertTrue(s.goBack())

        assertEquals(GroupsRoute.GroupHome, s.stackFor(ProductTab.GROUPS).last())
    }

    @Test
    fun `own charges back reveals the more predecessor`() {
        val s = session()
        s.selectTab(ProductTab.MORE)
        s.push(resolveFinanceRoute(canManageFinance = false))
        assertEquals(FinanceRoute.OwnCharges, s.stackFor(ProductTab.MORE).last())

        assertTrue(s.goBack())

        assertEquals(GroupsRoute.More, s.stackFor(ProductTab.MORE).last())
    }

    @Test
    fun `finance entry composes only the supplied placeholder content`() {
        val provider = entryProvider<NavKey> {
            installFinanceEntries(
                session(),
                titleFor = { "Financeiro" },
                content = { Text("placeholder", modifier = androidx.compose.ui.Modifier.testTag("finance-placeholder")) },
            )
        }
        val entry = provider(FinanceRoute.Finance)

        runComposeUiTest {
            setContent { SaqzTheme { entry.Content() } }
            onNodeWithTag("finance-placeholder").assertIsDisplayed()
        }
    }
}
