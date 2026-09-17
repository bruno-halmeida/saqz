package br.com.saqz.androidapp

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** APP-R10 receipt + APP-R12 manual expense. Generation is a precondition, not a job test. */
@RunWith(AndroidJUnit4::class)
internal class PaymentE2eTest : InstalledE2e("payments") {
    private val chargesPath get() = "/api/groups/$group/charges"
    private val amount = "R$\u00A0123,45"
    private val expenseDescription = "Quadra QA payments"

    @Test
    fun receiptAndCourtExpensePersistOnceAndReconcileTheSameGroupCashbox() {
        login("owner")
        val original = api("owner", chargesPath).getJSONArray("charges").getJSONObject(0)
        assertEquals(data.getJSONArray("financialCharges").getString(0), original.getString("id"))
        assertEquals("PENDING", original.getString("status"))
        assertEquals(12345L, api("owner", chargesPath).getLong("pendingTotalCents"))
        assertStatement(0, 0, 0)
        openGroup()
        click("group-details-cashbox", scroll = true)
        waitText("Recebi")
        ui.onNode(hasText("Recebi") and hasClickAction()).performScrollTo().performClick()
        waitTag("finance-receipt-sheet")
        ui.onNode(hasText(amount) and hasAnyAncestor(hasTestTag("finance-receipt-amount")), true).assertIsDisplayed()
        ui.onNode(hasText("E2E payments-athlete") and hasAnyAncestor(hasTestTag("finance-receipt-sheet")), true)
            .assertIsDisplayed()
        click("finance-receipt-confirm")
        ui.waitUntil(20_000) {
            api("owner", chargesPath).getJSONArray("charges").getJSONObject(0).getString("status") == "PAID"
        }
        val paid = assertPaidCharge(original)
        assertStatement(1, 12345, 0)
        backFromCashbox()
        waitText("Saldo $amount")
        click("group-details-cashbox", scroll = true)
        waitTag("group-cashbox-generate-monthly")
        ui.onNodeWithText("Recebi").assertDoesNotExist()
        assertEquals(paid.toString(), assertPaidCharge(original).toString())
        recordCourtExpense()
        val expense = assertExpense()
        assertStatement(2, 12345, 12000)
        click("group-cashbox-statement", scroll = true)
        waitText(expenseDescription)
        ui.onNodeWithText(expenseDescription).assertIsDisplayed()
        back()
        waitText("R$\u00A03,45")
        ui.onNodeWithText("R$\u00A03,45").performScrollTo().assertIsDisplayed()
        backFromCashbox()
        waitText("Saldo R$\u00A03,45")
        ui.onNodeWithText("Saldo R$\u00A03,45").performScrollTo().assertIsDisplayed()
        assertEquals(expense.toString(), assertExpense().toString())
        assertEquals(paid.toString(), assertPaidCharge(original).toString())
        assertStatement(2, 12345, 12000)
        assertEmptySecondGroup()
        back()
        logout()

        login("athlete")
        tab("Perfil")
        click("own-profile-monthly-payments", scroll = true)
        val chargeTag = "group-details-own-charge-${paid.getString("id")}"
        waitTag(chargeTag)
        ui.onNodeWithTag(chargeTag).performScrollTo().assertIsDisplayed()
        ui.onNode(hasText("Paga") and hasAnyAncestor(hasTestTag(chargeTag)), true).assertIsDisplayed()
        ui.onNode(hasText(amount) and hasAnyAncestor(hasTestTag(chargeTag)), true).assertIsDisplayed()
        val own = api("athlete", "$chargesPath/me").getJSONArray("charges")
        assertEquals(1, own.length())
        assertEquals(paid.toString(), own.getJSONObject(0).toString())
        assertEmptySecondGroup()
    }

    private fun backFromCashbox() {
        // Closed sheets keep their root tags; wait for the animated receipt content to leave.
        ui.waitUntil(20_000) { ui.onAllNodesWithTag("finance-receipt-confirm").fetchSemanticsNodes().isEmpty() }
        ui.onNode(
            hasContentDescription("Voltar") and hasClickAction() and hasAnyAncestor(hasTestTag("group-cashbox")),
        ).assertIsDisplayed().assertIsEnabled().performClick()
        waitTag("group-details")
    }

    private fun assertPaidCharge(original: JSONObject): JSONObject {
        val result = api("owner", chargesPath)
        assertEquals(1, result.getJSONArray("charges").length())
        assertEquals(0L, result.getLong("pendingTotalCents"))
        assertEquals(12345L, result.getLong("paidTotalCents"))
        return result.getJSONArray("charges").getJSONObject(0).also {
            assertEquals(group, it.getString("groupId"))
            assertEquals(actor("athlete").getString("id"), it.getString("memberId"))
            assertEquals("MONTHLY", it.getString("kind"))
            assertEquals(data.getString("financialToday").take(7), it.getString("month"))
            assertEquals("${it.getString("month")}-12", it.getString("dueDate"))
            assertEquals(12345L, it.getLong("amountCents"))
            assertReceipt(original, it, actor("owner").getString("id"))
        }
    }

    private fun recordCourtExpense() {
        click("group-cashbox-register", scroll = true)
        waitTag("finance-new-entry-direction")
        ui.onNode(hasText("Saída") and isSelectable()).performScrollTo().performClick()
        input("finance-new-entry-amount", "120,00")
        input("finance-new-entry-description", expenseDescription)
        ui.onNode(hasText("Quadra") and isSelectable()).performScrollTo().performClick()
        val date = data.getString("financialToday").split('-')
        input("finance-new-entry-date", "${date[2]}/${date[1]}/${date[0]}")
        click("finance-new-entry-save")
        waitTag("group-cashbox-generate-monthly")
    }

    private fun assertExpense(): JSONObject {
        val expenses = api("owner", "/api/groups/$group/expenses")
        assertEquals(12000L, expenses.getLong("activeExpenseTotalCents"))
        val list = expenses.getJSONArray("expenses")
        assertEquals(1, list.length())
        return list.getJSONObject(0).also {
            assertEquals(group, it.getString("groupId"))
            assertEquals(expenseDescription, it.getString("description"))
            assertEquals(12000L, it.getLong("amountCents"))
            assertEquals(data.getString("financialToday"), it.getString("expenseDate"))
            assertEquals("VENUE", it.getString("category"))
            assertEquals("OUT", it.getString("direction"))
            assertEquals("ACTIVE", it.getString("status"))
            val events = it.getJSONArray("events")
            assertEquals(1, events.length())
            assertEquals("CREATED", events.getJSONObject(0).getString("action"))
            assertEquals(actor("owner").getString("id"), events.getJSONObject(0).getString("actorId"))
            assertTrue(events.getJSONObject(0).getString("occurredAt").isNotBlank())
        }
    }

    private fun assertStatement(count: Int, income: Long, expense: Long) {
        val statement = api("owner", "/api/groups/$group/finance/statement")
        assertLedger(statement, count, income, expense)
        val items = statement.getJSONArray("items")
        for (index in 0 until items.length()) {
            val item = items.getJSONObject(index)
            if (item.getString("type") == "CHARGE") {
                assertEquals(data.getJSONArray("financialCharges").getString(0), item.getString("id"))
                assertEquals("IN", item.getString("direction"))
                assertEquals("MONTHLY", item.getString("category"))
                assertEquals("PIX", item.getString("paidMethod"))
                assertEquals(12345L, item.getLong("amountCents"))
                assertEquals("Mensalidade · E2E payments-athlete", item.getString("title"))
            } else {
                assertEquals("EXPENSE", item.getString("type"))
                assertEquals(assertExpense().getString("id"), item.getString("id"))
                assertEquals("OUT", item.getString("direction"))
                assertEquals(-12000L, item.getLong("amountCents"))
                assertEquals(expenseDescription, item.getString("title"))
            }
        }
    }

    private fun assertEmptySecondGroup() {
        assertEquals(0, api("owner", "/api/groups/$secondGroup/charges").getJSONArray("charges").length())
        assertEquals(0, api("owner", "/api/groups/$secondGroup/expenses").getJSONArray("expenses").length())
        assertLedger(api("owner", "/api/groups/$secondGroup/finance/statement"), 0, 0, 0)
    }
}

/** APP-R11 authorization subset only: the production UI has no waive/cancel charge action. */
@RunWith(AndroidJUnit4::class)
internal class ChargeLifecycleE2eTest : InstalledE2e("charge-lifecycle") {
    @Test
    fun athleteCannotWaiveOrCancelOwnPendingChargesAndNeitherAttemptChangesTheLedger() {
        login("athlete")
        val path = "/api/groups/$group/charges"
        val original = api("owner", path)
        val charges = original.getJSONArray("charges")
        assertEquals(2, charges.length())
        assertEquals(25690L, original.getLong("pendingTotalCents"))
        assertLedger(api("owner", "/api/groups/$group/finance/statement"), 0, 0, 0)
        openGroup()
        waitTag("group-details-own-charge-${charges.getJSONObject(0).getString("id")}")
        ui.onNodeWithTag("group-details-cashbox").assertDoesNotExist()
        back()
        tab("Perfil")
        click("own-profile-monthly-payments", scroll = true)
        for ((index, status) in listOf("WAIVED", "CANCELLED").withIndex()) {
            val charge = charges.getJSONObject(index)
            val id = charge.getString("id")
            assertEquals(data.getJSONArray("financialCharges").getString(index), id)
            assertEquals(group, charge.getString("groupId"))
            assertEquals(actor("athlete").getString("id"), charge.getString("memberId"))
            assertEquals("PENDING", charge.getString("status"))
            assertEquals(12345L + index * 1000, charge.getLong("amountCents"))
            val tag = "group-details-own-charge-$id"
            waitTag(tag)
            ui.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
            ui.onNode(hasText("Em aberto") and hasAnyAncestor(hasTestTag(tag)), true).assertIsDisplayed()
            api("athlete", "$path/$id/status", "POST", JSONObject().put("status", status)
                .put("note", "Tentativa negada QA"), status = 403,
                headers = mapOf("If-Match" to "\"${charge.getLong("version")}\""))
            assertEquals(original.toString(), api("owner", path).toString())
            assertEquals(charges.toString(), api("athlete", "$path/me").getJSONArray("charges").toString())
            assertLedger(api("owner", "/api/groups/$group/finance/statement"), 0, 0, 0)
        }
        back()
        click("own-profile-monthly-payments", scroll = true)
        for (index in 0 until charges.length()) {
            val tag = "group-details-own-charge-${charges.getJSONObject(index).getString("id")}"
            waitTag(tag)
            ui.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
            ui.onNode(hasText("Em aberto") and hasAnyAncestor(hasTestTag(tag)), true).assertIsDisplayed()
        }
        assertEquals(0, api("athlete", "/api/groups/$secondGroup/charges/me").getJSONArray("charges").length())
        assertLedger(api("owner", "/api/groups/$secondGroup/finance/statement"), 0, 0, 0)
    }
}

/** APP-AC01 + APP-R12 settlement subset: confirmed receipt, remote re-entry and no closing write. */
@RunWith(AndroidJUnit4::class)
internal class SettlementE2eTest : InstalledE2e("settlement") {
    private val chargesPath get() = "/api/groups/$group/charges"
    private val gamePath get() = "/api/groups/$group/games/${data.getString("game")}"

    @Test
    fun pendingGameChargeBlocksClosingAndPaidSettlementReopensWithoutNewFinancialEntries() {
        login("owner")
        val before = api("owner", chargesPath)
        val original = before.getJSONArray("charges").getJSONObject(0)
        assertEquals(1, before.getJSONArray("charges").length())
        assertEquals(7000L, before.getLong("pendingTotalCents"))
        assertEquals(data.getJSONArray("financialCharges").getString(0), original.getString("id"))
        assertEquals(group, original.getString("groupId"))
        assertEquals(data.getString("game"), original.getString("gameId"))
        assertEquals(actor("athlete").getString("id"), original.getString("memberId"))
        assertEquals("GAME", original.getString("kind"))
        assertEquals(7000L, original.getLong("amountCents"))
        assertEquals("PENDING", original.getString("status"))
        val game = api("owner", gamePath)
        assertEquals("COMPLETED", game.getString("status"))
        assertEquals(game.getString("localDate"), original.getString("dueDate"))
        val roster = api("owner", "$gamePath/attendance/roster")
        assertEquals(1, roster.getJSONArray("confirmed").length())
        assertEquals(actor("athlete").getString("id"), roster.getJSONArray("confirmed").getJSONObject(0).getString("memberId"))
        assertLedger(api("owner", "/api/groups/$group/finance/statement"), 0, 0, 0)
        openCompletedGame()
        openSettlement()
        waitTag("game-settlement-end")
        ui.onNodeWithTag("game-settlement-end").performScrollTo().assertIsNotEnabled()
        ui.onNodeWithTag("game-settlement-summary").assertDoesNotExist()
        ui.onNodeWithText("0 de 1 diarista acertou · faltam R$\u00A070,00").performScrollTo().assertIsDisplayed()
        assertEquals(before.toString(), api("owner", chargesPath).toString())
        click("game-settlement-receipt-${original.getString("id")}", scroll = true)
        waitTag("finance-receipt-amount")
        ui.onNode(hasText("R$\u00A070,00") and hasAnyAncestor(hasTestTag("finance-receipt-amount")), true).assertIsDisplayed()
        click("finance-receipt-confirm")
        waitTag("game-settlement-summary")
        waitEnabled("game-settlement-end")
        val paid = api("owner", chargesPath)
        assertEquals(1, paid.getJSONArray("charges").length())
        assertEquals(0L, paid.getLong("pendingTotalCents"))
        assertEquals(7000L, paid.getLong("paidTotalCents"))
        assertReceipt(original, paid.getJSONArray("charges").getJSONObject(0), actor("owner").getString("id"))
        val statement = api("owner", "/api/groups/$group/finance/statement")
        assertLedger(statement, 1, 7000, 0)
        val item = statement.getJSONArray("items").getJSONObject(0)
        assertEquals(original.getString("id"), item.getString("id"))
        assertEquals("CHARGE", item.getString("type"))
        assertEquals("GAME", item.getString("category"))
        assertEquals("IN", item.getString("direction"))
        assertEquals(7000L, item.getLong("amountCents"))
        assertEquals("Cobrança · E2E settlement-athlete", item.getString("title"))
        ui.onNodeWithTag("game-settlement-end").performScrollTo().assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick) { close -> close(); close() }
        waitTag("game-detail")
        ui.onNodeWithTag("game-settlement").assertDoesNotExist()
        openSettlement()
        waitTag("game-settlement-summary")
        waitEnabled("game-settlement-end")
        ui.onNodeWithText("ENCERRADO").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Recebido").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("1 diarista × R$\u00A070,00").performScrollTo().assertIsDisplayed()
        assertSettlementResult()
        assertEquals(paid.toString(), api("owner", chargesPath).toString())
        assertEquals(statement.toString(), api("owner", "/api/groups/$group/finance/statement").toString())
        assertEquals(game.toString(), api("owner", gamePath).toString())
        assertEquals(roster.toString(), api("owner", "$gamePath/attendance/roster").toString())
        assertEquals(paid.getJSONArray("charges").toString(), api("athlete", "$chargesPath/me").getJSONArray("charges").toString())
        assertEquals(0, api("owner", "/api/groups/$group/expenses").getJSONArray("expenses").length())
        assertEquals(0, api("owner", "/api/groups/$secondGroup/charges").getJSONArray("charges").length())
        assertLedger(api("owner", "/api/groups/$secondGroup/finance/statement"), 0, 0, 0)
    }

    private fun assertSettlementResult() {
        val inSummary = hasAnyAncestor(hasTestTag("game-settlement-summary"))
        val label = ui.onNode(hasText("Resultado") and inSummary, true)
            .performScrollTo().assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        // SummaryLine rows have no semantics node, so all summary values share a parent.
        val besideResult = SemanticsMatcher("value to the right of Resultado on the same visible row") {
            val value = it.boundsInRoot
            value.left >= label.right && value.top < label.bottom && value.bottom > label.top
        }
        ui.onNode(hasText("R$\u00A070,00") and inSummary and hasAnySibling(hasText("Resultado")) and besideResult, true)
            .assertIsDisplayed()
    }

    private fun openCompletedGame() {
        tab("Perfil")
        click("own-profile-notifications", scroll = true)
        val tag = "notification-${data.getLong("settlementNotification")}"
        waitTag(tag)
        ui.onNode(hasText("Abrir") and hasClickAction() and hasAnyAncestor(hasTestTag(tag)))
            .performScrollTo().performClick()
        waitTag("game-detail")
    }

    private fun openSettlement() {
        waitText("Encerrar jogo e acertar")
        ui.onNode(hasText("Encerrar jogo e acertar") and hasClickAction()).performScrollTo().performClick()
        waitTag("game-settlement")
    }
}

private fun assertReceipt(original: JSONObject, paid: JSONObject, ownerId: String) {
    for (field in listOf("id", "groupId", "memberId", "kind", "gameId", "month", "amountCents", "dueDate")) {
        assertEquals("Receipt must preserve $field", original.opt(field), paid.opt(field))
    }
    assertEquals("PAID", paid.getString("status"))
    assertEquals("PIX", paid.getString("paidMethod"))
    assertEquals(original.getLong("version") + 1, paid.getLong("version"))
    val before = original.getJSONArray("events")
    val events = paid.getJSONArray("events")
    assertEquals(before.length() + 1, events.length())
    for (index in 0 until before.length()) assertEquals(before.get(index).toString(), events.get(index).toString())
    val receipt = events.getJSONObject(events.length() - 1)
    assertEquals(ownerId, receipt.getString("actorId"))
    assertEquals("PENDING", receipt.getString("oldStatus"))
    assertEquals("PAID", receipt.getString("newStatus"))
    assertTrue(receipt.getString("occurredAt").isNotBlank())
}

private fun assertLedger(statement: JSONObject, count: Int, income: Long, expense: Long) {
    val items = statement.getJSONArray("items")
    assertEquals(count, items.length())
    val entries = (0 until items.length()).map { items.getJSONObject(it) }
    assertEquals(count, entries.map { it.getString("id") }.toSet().size)
    assertEquals(income - expense, entries.sumOf { it.getLong("amountCents") })
    val summary = statement.getJSONObject("summary")
    assertEquals(income, summary.getLong("totalInCents"))
    assertEquals(expense, summary.getLong("totalOutCents"))
    assertEquals(income - expense, summary.getLong("periodBalanceCents"))
    assertEquals(income - expense, summary.getLong("accumulatedBalanceCents"))
}
