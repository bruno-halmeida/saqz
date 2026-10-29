package br.com.saqz.groups.presentation.newentry

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.domain.finance.FinanceDirection
import br.com.saqz.groups.presentation.GroupUiError
import kotlinx.serialization.Serializable

enum class NewEntryDirection {
    In,
    Out,
}

/**
 * DEB-148: a Saída tem Quadra, Material, Árbitro e Outros; a Entrada, Patrocínio, Venda e Outros. Racha entra em
 * Outros (o extrato continua mostrando as saídas antigas de Racha).
 */
enum class NewEntryCategory {
    Court,
    Material,
    Referee,
    Sponsorship,
    Sale,
    Other,
    ;

    companion object {
        fun forDirection(direction: NewEntryDirection): List<NewEntryCategory> = when (direction) {
            NewEntryDirection.Out -> listOf(Court, Material, Referee, Other)
            NewEntryDirection.In -> listOf(Sponsorship, Sale, Other)
        }
    }
}

/** Atalhos de valor da Saída: R$ 80, 120 e 160. A Entrada não tem atalhos. */
internal val ExpenseShortcuts = listOf(8_000L, 12_000L, 16_000L)

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
    val datePickerOpen: Boolean = false,
    /** Hoje no fuso do aparelho: a data vem com hoje e o apoio diz "Hoje · terça, 27/10". */
    val today: String = date,
)

sealed interface NewEntryIntent {
    data class SelectDirection(val direction: NewEntryDirection) : NewEntryIntent
    data class AmountChanged(val value: String) : NewEntryIntent
    data class SelectAmountShortcut(val cents: Long) : NewEntryIntent
    data class DescriptionChanged(val value: String) : NewEntryIntent
    data class SelectCategory(val category: NewEntryCategory) : NewEntryIntent
    data class CustomCategoryChanged(val value: String) : NewEntryIntent
    data class DateChanged(val value: String) : NewEntryIntent
    data object OpenDatePicker : NewEntryIntent
    data object DismissDatePicker : NewEntryIntent
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
