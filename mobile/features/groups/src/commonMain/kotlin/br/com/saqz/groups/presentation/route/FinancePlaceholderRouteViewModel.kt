package br.com.saqz.groups.presentation.route

import androidx.compose.runtime.Immutable
import br.com.saqz.core.common.mvi.MviViewModel

/** Distinguishes the two Finance placeholder routes so the Root can pick the matching label. */
enum class FinancePlaceholderMode { FINANCE, OWN_CHARGES }

@Immutable
data class FinancePlaceholderState(val mode: FinancePlaceholderMode)

/** Finance/OwnCharges are leaf placeholder routes: no command exists to dispatch (FINNAV-02). */
sealed interface FinancePlaceholderIntent

/** Neither placeholder route triggers forward navigation; back is owned by NavigationSession. */
sealed interface FinancePlaceholderEffect

/**
 * Inert Finance/OwnCharges route adapter (T15): every entry-owned instance only
 * carries its immutable [mode] -- it resolves no finance/expense gateway and
 * never activates `FinanceScreen`/`ExpenseScreen` (FINNAV-02, LIFE-01, LIFE-03,
 * LIFE-05). Which route a user reaches (Finance for an organizer, OwnCharges for
 * an athlete) is decided by the existing `GroupRoutePolicy`-based resolver
 * elsewhere (T13's More adapter, T20's effect handler); this adapter does not
 * duplicate that decision.
 */
class FinancePlaceholderRouteViewModel(
    mode: FinancePlaceholderMode,
) : MviViewModel<FinancePlaceholderState, FinancePlaceholderIntent, FinancePlaceholderEffect>(
    FinancePlaceholderState(mode),
) {
    override fun onIntent(intent: FinancePlaceholderIntent) {
        // FinancePlaceholderIntent has no cases: this placeholder is inert by construction.
    }
}
