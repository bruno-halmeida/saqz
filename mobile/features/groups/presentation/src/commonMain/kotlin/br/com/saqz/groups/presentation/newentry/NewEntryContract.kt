package br.com.saqz.groups.presentation.newentry

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.domain.finance.FinanceDirection
import br.com.saqz.groups.presentation.GroupUiError

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

@Immutable
data class NewEntryState(
    val direction: NewEntryDirection = NewEntryDirection.In,
    val amountText: String = "",
    val description: String = "",
    val category: NewEntryCategory = NewEntryCategory.Other,
    val date: String,
    val isSaving: Boolean = false,
    val error: GroupUiError? = null,
)

sealed interface NewEntryIntent {
    data class SelectDirection(val direction: NewEntryDirection) : NewEntryIntent
    data class AmountChanged(val value: String) : NewEntryIntent
    data class SelectAmountShortcut(val cents: Long) : NewEntryIntent
    data class DescriptionChanged(val value: String) : NewEntryIntent
    data class SelectCategory(val category: NewEntryCategory) : NewEntryIntent
    data class DateChanged(val value: String) : NewEntryIntent
    data object Save : NewEntryIntent
}

sealed interface NewEntryEffect {
    data object Saved : NewEntryEffect
}

internal fun NewEntryDirection.toFinanceDirection(): FinanceDirection = when (this) {
    NewEntryDirection.In -> FinanceDirection.In
    NewEntryDirection.Out -> FinanceDirection.Out
}
