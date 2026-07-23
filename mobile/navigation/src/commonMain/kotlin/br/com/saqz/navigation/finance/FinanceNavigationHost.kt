package br.com.saqz.navigation.finance

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import br.com.saqz.groups.navigation.FinanceRoute
import br.com.saqz.navigation.NavigationSession
import br.com.saqz.navigation.groups.GroupsScopedScaffold

/**
 * FINNAV-01/02: resolves which structural Finance placeholder a member reaches.
 * Organizers (finance managers) reach `Finance`; everyone else reaches `OwnCharges`.
 * This is the same owner/athlete split the existing `GroupRoutePolicy` finance
 * resolver applies; it activates no `FinanceScreen`/`ExpenseScreen` or gateway.
 */
fun resolveFinanceRoute(canManageFinance: Boolean): FinanceRoute =
    if (canManageFinance) FinanceRoute.Finance else FinanceRoute.OwnCharges

/**
 * MODNAV-01/FINNAV-01..03: installs the Finance/OwnCharges structural entries into
 * the shared product entry provider. The placeholder content ([content], the existing
 * `RoutePage`) and localized [titleFor] are composition-root bindings because Groups
 * strings/screens are module-internal to `:features:groups`.
 *
 * FINNAV-03: a finance key is pushed onto whichever authenticated stack launched it
 * (via `NavigationSession.push`), so its predecessor is the real launcher (`GroupHome`
 * or `More`); the shared back callback ([NavigationSession.goBack]) removes the finance
 * key and reveals that predecessor. Finance entries never compose a real finance/expense
 * screen or resolve a gateway (FINNAV-02).
 */
fun EntryProviderScope<NavKey>.installFinanceEntries(
    session: NavigationSession,
    titleFor: @Composable (NavKey) -> String,
    content: @Composable (NavKey) -> Unit,
) {
    @Composable
    fun scoped(key: NavKey) {
        // Finance keys are always pushed on top of a launcher (depth > 1), so back is
        // always available and pops to the actual predecessor.
        GroupsScopedScaffold(title = titleFor(key), canGoBack = true, onBack = session::goBack) {
            content(key)
        }
    }
    entry<FinanceRoute.Finance> { scoped(FinanceRoute.Finance) }
    entry<FinanceRoute.OwnCharges> { scoped(FinanceRoute.OwnCharges) }
}
