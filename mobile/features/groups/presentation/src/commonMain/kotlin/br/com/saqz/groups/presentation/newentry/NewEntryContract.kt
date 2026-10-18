package br.com.saqz.groups.presentation.newentry

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.domain.finance.FinanceDirection
import br.com.saqz.groups.presentation.GroupUiError
import kotlinx.serialization.Serializable

enum class NewEntryDirection {
    In,
    Out,
}

enum class NewEntryCategory {
    Court,
    Material,
    Racha,
    Other,
}

@Serializable
sealed interface NewEntryPrefill {
    @Serializable
    data class GameCourt(val localDate: String) : NewEntryPrefill
}

@Immutable
data class NewEntryState(
    val direction: NewEntryDirection = NewEntryDirection.In,
    val amountText: String = "",
    val description: String = "",
    val category: NewEntryCategory = NewEntryCategory.Other,
    val customCategory: String = "",
    val date: String,
    val isSaving: Boolean = false,
    val error: GroupUiError? = null,
    /** Modo correção: só valor e descrição mudam; o resto do lançamento vai como está. */
    val editing: Boolean = false,
    val isLoadingEntry: Boolean = false,
    val entryLoadFailed: Boolean = false,
)

sealed interface NewEntryIntent {
    data class SelectDirection(val direction: NewEntryDirection) : NewEntryIntent
    data class AmountChanged(val value: String) : NewEntryIntent
    data class SelectAmountShortcut(val cents: Long) : NewEntryIntent
    data class DescriptionChanged(val value: String) : NewEntryIntent
    data class SelectCategory(val category: NewEntryCategory) : NewEntryIntent
    data class CustomCategoryChanged(val value: String) : NewEntryIntent
    data class DateChanged(val value: String) : NewEntryIntent
    data class ApplyPrefill(val prefill: NewEntryPrefill, val description: String) : NewEntryIntent
    /** Abre o lançamento [expenseId] para correção; chamado de novo, só refaz a leitura da versão. */
    data class LoadEdit(val expenseId: String) : NewEntryIntent
    data object RetryLoadEdit : NewEntryIntent
    data object Save : NewEntryIntent
}

sealed interface NewEntryEffect {
    data object Saved : NewEntryEffect
}

internal fun NewEntryDirection.toFinanceDirection(): FinanceDirection = when (this) {
    NewEntryDirection.In -> FinanceDirection.In
    NewEntryDirection.Out -> FinanceDirection.Out
}
