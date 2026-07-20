package br.com.saqz.composeapp

import br.com.saqz.groups.model.GroupSetupDraft
import br.com.saqz.groups.presentation.finance.charges.MonthlyChargeDraft
import br.com.saqz.groups.presentation.finance.expenses.ExpenseDraft
import br.com.saqz.groups.presentation.games.editor.GameEditorDraft
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class IOSGroupDraftCodec {
    private val json = Json { explicitNulls = false; ignoreUnknownKeys = false }

    fun encodeSetup(value: GroupSetupDraft): String = json.encodeToString(value)
    fun encodeGame(value: GameEditorDraft): String = json.encodeToString(value)
    fun encodeMonthly(value: MonthlyChargeDraft): String = json.encodeToString(value)
    fun encodeExpense(value: ExpenseDraft): String = json.encodeToString(value)

    @Throws(Exception::class)
    fun decodeSetup(value: String): GroupSetupDraft = json.decodeFromString(value)

    @Throws(Exception::class)
    fun decodeGame(value: String): GameEditorDraft = json.decodeFromString(value)

    @Throws(Exception::class)
    fun decodeMonthly(value: String): MonthlyChargeDraft = json.decodeFromString(value)

    @Throws(Exception::class)
    fun decodeExpense(value: String): ExpenseDraft = json.decodeFromString(value)
}
