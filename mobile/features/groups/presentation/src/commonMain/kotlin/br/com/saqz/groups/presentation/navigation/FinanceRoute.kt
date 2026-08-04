package br.com.saqz.groups.presentation.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Destinos de fiação do Fluxo 5; as telas completas entram nos tickets da onda C. */
@Serializable
sealed interface FinanceRoute : NavKey {
    @Serializable
    data class GroupCashbox(val groupId: String) : FinanceRoute

    @Serializable
    data class Statement(val groupId: String) : FinanceRoute

    @Serializable
    data class NewEntry(val groupId: String) : FinanceRoute

    @Serializable
    data class GameSettlement(val groupId: String, val gameId: String) : FinanceRoute
}
