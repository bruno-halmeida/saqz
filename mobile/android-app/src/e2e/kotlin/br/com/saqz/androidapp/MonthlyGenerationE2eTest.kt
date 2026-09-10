package br.com.saqz.androidapp

import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** APP-MG01/MG02-selection/F01 subsets: no provider payment or lost-response simulation. */
@RunWith(AndroidJUnit4::class)
internal class MonthlyGenerationE2eTest : InstalledE2e("finance") {
    private val month = "2026-08"
    private val due = "12/08/2026"
    private val amount = "R$\u00A0123,45"

    @Test
    fun reviewedMonthlyChargeIsIsolatedAndRepeatCannotDuplicateOrRepriceIt() {
        login("owner")
        openGroup()
        click("group-details-cashbox", scroll = true)
        assertEquals(0, charges("owner").getJSONArray("charges").length())
        assertNoIncomeOrMessages()

        review("123,45")
        assertEquals(0, charges("owner").getJSONArray("charges").length())
        click("monthly-generation-edit", scroll = true)
        click("monthly-generation-review", scroll = true)
        assertReview()
        assertEquals(0, charges("owner").getJSONArray("charges").length())
        confirm()
        val original = assertCharge()
        assertNoIncomeOrMessages()

        review("200,00")
        click("monthly-generation-confirm", scroll = true)
        waitTag("group-cashbox-generate-monthly")
        val repeated = assertCharge()
        assertEquals(original.getString("id"), repeated.getString("id"))
        assertEquals(original.getLong("version"), repeated.getLong("version"))
        assertNoIncomeOrMessages()
        back()
        back()
        logout()

        login("athlete")
        tab("Perfil")
        click("own-profile-monthly-payments", scroll = true)
        val chargeTag = "group-details-own-charge-${original.getString("id")}"
        waitTag(chargeTag)
        ui.onNodeWithTag(chargeTag).performScrollTo().assertIsDisplayed()
        ui.onNode(hasText(amount) and hasAnyAncestor(hasTestTag(chargeTag)), useUnmergedTree = true).assertIsDisplayed()
        ui.onNode(hasText("Em aberto") and hasAnyAncestor(hasTestTag(chargeTag)), useUnmergedTree = true).assertIsDisplayed()
        val own = charges("athlete").getJSONArray("charges")
        assertEquals(1, own.length())
        assertEquals(original.getString("id"), own.getJSONObject(0).getString("id"))
        assertEquals(0, charges("other").getJSONArray("charges").length())
        assertEquals(0, api("athlete", "/api/groups/$secondGroup/charges/me").getJSONArray("charges").length())
    }

    private fun review(value: String) {
        click("group-cashbox-generate-monthly", scroll = true)
        val selected = "monthly-generation-member-${actor("athlete").getString("id")}"
        val unselected = "monthly-generation-member-${actor("other").getString("id")}"
        waitTag(selected)
        ui.onNodeWithTag(selected).assertIsOff()
        ui.onNodeWithTag(unselected).assertIsOff()
        ui.onNodeWithTag("monthly-generation-member-${actor("owner").getString("id")}").assertDoesNotExist()
        ui.onNodeWithTag("monthly-generation-review").assertIsNotEnabled()
        input("monthly-generation-month", month)
        input("monthly-generation-due", due)
        input("monthly-generation-amount", value)
        click(selected, scroll = true)
        ui.onNodeWithTag(selected).assertIsOn()
        ui.onNodeWithTag(unselected).assertIsOff()
        click("monthly-generation-review", scroll = true)
        waitTag("monthly-generation-summary")
        assertReview("R$\u00A0$value")
    }

    private fun assertReview(reviewAmount: String = amount) {
        ui.onNodeWithText("Mês $month · Vencimento $due\n$reviewAmount por pessoa · Pessoas selecionadas: 1")
            .performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("E2E finance-athlete").assertIsDisplayed()
        ui.onNodeWithText("E2E finance-other").assertDoesNotExist()
    }

    private fun confirm() {
        click("monthly-generation-confirm", scroll = true)
        waitTag("group-cashbox-generate-monthly")
    }

    private fun charges(role: String) = api(role, "/api/groups/$group/charges${if (role == "owner") "" else "/me"}")

    private fun assertCharge(): JSONObject {
        val result = charges("owner")
        val list = result.getJSONArray("charges")
        assertEquals(1, list.length())
        assertEquals(12345L, result.getLong("pendingTotalCents"))
        assertEquals(0L, result.getLong("paidTotalCents"))
        return list.getJSONObject(0).also { charge ->
            assertEquals(group, charge.getString("groupId"))
            assertEquals(actor("athlete").getString("id"), charge.getString("memberId"))
            assertEquals("MONTHLY", charge.getString("kind"))
            assertEquals(month, charge.getString("month"))
            assertEquals("2026-08-12", charge.getString("dueDate"))
            assertEquals(12345L, charge.getLong("amountCents"))
            assertEquals("PENDING", charge.getString("status"))
        }
    }

    private fun assertNoIncomeOrMessages() {
        for (query in listOf("", "?month=$month")) {
            val statement = api("owner", "/api/groups/$group/finance/statement$query")
            assertEquals(0, statement.getJSONArray("items").length())
            assertEquals(0L, statement.getJSONObject("summary").getLong("totalInCents"))
            assertEquals(0L, statement.getJSONObject("summary").getLong("accumulatedBalanceCents"))
        }
        for (channel in listOf("CHAT", "NOTICE")) {
            assertEquals(0, api("owner", "/api/groups/$group/messages?channel=$channel").getJSONArray("items").length())
        }
    }
}
