package br.com.saqz.groups.presentation.finance.expenses

import br.com.saqz.core.common.formatting.formatBrlPlain
import br.com.saqz.core.common.formatting.parseBrlToCents
import br.com.saqz.groups.domain.finance.Expense
import br.com.saqz.groups.domain.finance.ExpenseCategory
import br.com.saqz.groups.domain.finance.ExpenseWriteCommand

internal fun Expense.toExpenseForm() = ExpenseForm(
    description = description,
    amountBrl = amountCents.let(::formatBrlPlain),
    expenseDate = expenseDate,
    category = category,
    customCategory = customCategory.orEmpty(),
    notes = notes.orEmpty(),
)

internal fun ExpenseForm.toExpenseWriteCommand(key: String?) = ExpenseWriteCommand(
    key,
    description.trim(),
    requireNotNull(parseBrlToCents(amountBrl)),
    expenseDate,
    requireNotNull(category),
    customCategory.trim().ifBlank { null },
    notes.trim().ifBlank { null },
)

internal fun ExpenseForm.validate() = buildMap<String, List<String>> {
    val normalizedDescription = description.trim()
    if (normalizedDescription.length !in 2..160 || normalizedDescription.any(Char::isISOControl)) {
        put("description", listOf("is invalid"))
    }

    val amount = parseBrlToCents(amountBrl)
    if (amount == null || amount !in 1..99_999_999) {
        put("amountBrl", listOf("is invalid"))
    }

    if (!expenseDate.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) {
        put("expenseDate", listOf("is invalid"))
    }

    if (category == null) {
        put("category", listOf("is required"))
    }

    if (category == ExpenseCategory.Other && customCategory.trim().let {
            it.length !in 2..40 || it.any(Char::isISOControl)
        }) {
        put("customCategory", listOf("is invalid"))
    }

    if (notes.trim().let {
            it.isNotEmpty() && (it.length !in 2..500 || it.any(Char::isISOControl))
        }) {
        put("notes", listOf("is invalid"))
    }
}
