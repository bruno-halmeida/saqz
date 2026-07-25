package br.com.saqz.groups.model

import br.com.saqz.groups.domain.finance.ExpenseCategory
import br.com.saqz.groups.domain.game.GameVenue
import br.com.saqz.groups.domain.game.GameVersionToken
import br.com.saqz.groups.domain.game.SeriesBoundaryScope
import br.com.saqz.groups.domain.game.WeeklySlot

data class MonthlyChargeDraft(
    val schemaVersion: Int = CURRENT_SCHEMA,
    val groupId: String,
    val commandKey: String,
    val month: String = "",
    val amountBrl: String = "",
    val dueDate: String = "",
    val selectedMemberIds: Set<String> = emptySet(),
    val reviewed: Boolean = false,
) {
    companion object {
        const val CURRENT_SCHEMA = 1
    }
}

data class ExpenseForm(
    val description: String = "",
    val amountBrl: String = "",
    val expenseDate: String = "",
    val category: ExpenseCategory? = null,
    val customCategory: String = "",
    val notes: String = "",
)

data class ExpenseDraft(
    val schemaVersion: Int = CURRENT_SCHEMA,
    val groupId: String,
    val expenseId: String? = null,
    val etag: String? = null,
    val commandKey: String,
    val form: ExpenseForm,
) {
    companion object {
        const val CURRENT_SCHEMA = 1
    }
}

enum class GameEditorMode {
    ONE_TIME,
    WEEKLY,
}

data class GameEditorForm(
    val title: String = "",
    val venue: GameVenue? = null,
    val localDate: String = "",
    val localTime: String = "",
    val zoneId: String = "",
    val startsAt: String = "",
    val durationMinutes: String = "",
    val capacity: String = "",
    val confirmationDeadline: String = "",
    val gameFeeBrl: String = "",
    val notes: String = "",
    val localEndDate: String = "",
    val slots: List<WeeklySlot> = emptyList(),
)

data class GameEditorDraft(
    val schemaVersion: Int = CURRENT_SCHEMA,
    val groupId: String,
    val gameId: String?,
    val seriesId: String?,
    val commandKey: String,
    val version: GameVersionToken?,
    val mode: GameEditorMode,
    val form: GameEditorForm,
    val scope: SeriesBoundaryScope? = null,
) {
    companion object {
        const val CURRENT_SCHEMA = 1
    }
}
