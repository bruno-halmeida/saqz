package br.com.saqz.composeapp

import br.com.saqz.groups.domain.finance.ExpenseCategory
import br.com.saqz.groups.model.GroupComposition
import br.com.saqz.groups.model.GroupDraftResource
import br.com.saqz.groups.model.GroupModality
import br.com.saqz.groups.model.GroupSetupDraft
import br.com.saqz.groups.model.GroupSetupForm
import br.com.saqz.groups.presentation.finance.charges.MonthlyChargeDraft
import br.com.saqz.groups.presentation.finance.expenses.ExpenseDraft
import br.com.saqz.groups.presentation.finance.expenses.ExpenseForm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IOSGroupDraftCodecTest {
    private val codec = IOSGroupDraftCodec()

    @Test
    fun `setup draft round trips through private persisted schema`() {
        val draft = GroupSetupDraft(
            resource = GroupDraftResource.UPDATE_GROUP,
            groupId = GROUP,
            groupVersion = 7,
            etag = "\"7\"",
            commandKey = KEY,
            form = GroupSetupForm(
                name = "Vôlei",
                modality = GroupModality.COURT_VOLLEYBALL,
                composition = GroupComposition.MIXED,
            ),
        )

        assertEquals(draft, codec.decodeSetup(codec.encodeSetup(draft)))
    }

    @Test
    fun `monthly draft round trips selection review and command key`() {
        val draft = MonthlyChargeDraft(
            groupId = GROUP,
            commandKey = KEY,
            month = "2026-08",
            amountBrl = "70,00",
            dueDate = "2026-08-10",
            selectedMemberIds = setOf("member-1", "member-2"),
            reviewed = true,
        )

        assertEquals(draft, codec.decodeMonthly(codec.encodeMonthly(draft)))
    }

    @Test
    fun `expense draft preserves enum schema and quoted version`() {
        val draft = ExpenseDraft(
            groupId = GROUP,
            expenseId = "expense-1",
            etag = "\"3\"",
            commandKey = KEY,
            form = ExpenseForm(
                description = "Água",
                amountBrl = "123,45",
                expenseDate = "2026-08-12",
                category = ExpenseCategory.Other,
                customCategory = "Consumo",
            ),
        )

        val encoded = codec.encodeExpense(draft)

        assertTrue(encoded.contains("\"category\":\"OTHER\""))
        assertEquals(draft, codec.decodeExpense(encoded))
    }

    @Test
    fun `persisted payload excludes sensitive fields`() {
        val encoded = codec.encodeMonthly(
            MonthlyChargeDraft(
                groupId = GROUP,
                commandKey = KEY,
                month = "2026-08",
                amountBrl = "70,00",
                dueDate = "2026-08-10",
                selectedMemberIds = emptySet(),
                reviewed = false,
            ),
        )

        listOf("bearerToken", "inviteCode", "photoBytes", "paymentCredential", "rawServerError").forEach {
            assertFalse(encoded.contains(it))
        }
    }

    private companion object {
        const val GROUP = "group-1"
        const val KEY = "draft-key"
    }
}
