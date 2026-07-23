package br.com.saqz.groups.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Structural, feature-owned route keys for the Finance placeholders. Adding these
 * keys does not activate `FinanceScreen`/`ExpenseScreen`: no gateway, ViewModel, or
 * real screen wiring is introduced by this type (FINNAV-02).
 */
@Serializable
sealed interface FinanceRoute : NavKey {

    @Serializable
    data object Finance : FinanceRoute

    @Serializable
    data object OwnCharges : FinanceRoute
}
