package br.com.saqz.groups.presentation.finance.charges

import kotlinx.serialization.Serializable

@Serializable
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
