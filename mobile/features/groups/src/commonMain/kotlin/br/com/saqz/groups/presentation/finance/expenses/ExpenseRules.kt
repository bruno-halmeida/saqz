package br.com.saqz.groups.presentation.finance.expenses

import br.com.saqz.groups.data.finance.ExpenseCategoryDto
import br.com.saqz.groups.data.finance.ExpenseDto
import br.com.saqz.groups.data.finance.ExpenseWriteCommandDto

internal fun ExpenseDto.toExpenseForm() = ExpenseForm(
    description = description,
    amountBrl = amountCents.toBrl(),
    expenseDate = expenseDate,
    category = category,
    customCategory = customCategory.orEmpty(),
    notes = notes.orEmpty(),
)

internal fun ExpenseForm.toExpenseWriteCommand(key: String?) = ExpenseWriteCommandDto(
    key,
    description.trim(),
    requireNotNull(amountBrl.brlToCents()),
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

    val amount = amountBrl.brlToCents()
    if (amount == null || amount !in 1..99_999_999) {
        put("amountBrl", listOf("is invalid"))
    }

    if (!expenseDate.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) {
        put("expenseDate", listOf("is invalid"))
    }

    if (category == null) {
        put("category", listOf("is required"))
    }

    if (category == ExpenseCategoryDto.OTHER && customCategory.trim().let {
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

internal fun Long.toBrl() = "${this / 100},${(this % 100).toString().padStart(2, '0')}"

internal fun String.brlToCents(): Long? {
    val clean = trim().replace("R$", "").trim()
    if (!clean.matches(Regex("[0-9]+([,.][0-9]{1,2})?"))) return null

    val parts = clean.replace('.', ',').split(',')
    return parts[0].toLongOrNull()
        ?.times(100)
        ?.plus(parts.getOrNull(1)?.padEnd(2, '0')?.toLongOrNull() ?: 0)
}
