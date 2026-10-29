package br.com.saqz.androidapp

import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FIN-006 (roteiro 13, C1–C13): o caixa zerado pergunta o saldo inicial; ⋯ → Saldo inicial corrige; Saída de quadra com
 * o atalho de R$ 120 e a data de ontem pelo calendário; Entrada de patrocínio sem atalhos; o placar troca a lista; o
 * extrato fixa o saldo inicial e filtra as saídas. O atleta recebe 403 no saldo inicial.
 */
@RunWith(AndroidJUnit4::class)
internal class CashboxE2eTest : InstalledE2e("cashbox") {
    private val openingPath get() = "/api/groups/$group/finance/opening-balance"

    @Test
    fun openingBalanceExpenseYesterdayIncomeScoreboardAndStatement() {
        assertNull(api("owner", openingPath).opt("amountCents").takeIf { it != JSONObject.NULL })
        login("owner")
        openGroup()
        click("group-details-cashbox", scroll = true)
        waitTag("group-cashbox-opening-question")
        waitText("Quanto o grupo já tem em caixa?")
        waitText("Entra como saldo inicial e soma no saldo.")
        waitTag("group-cashbox-income")
        waitTag("group-cashbox-expense")
        shot("caixa-zerado-pergunta")

        input("group-cashbox-opening-input", "250,00")
        click("group-cashbox-opening-save", scroll = true)
        waitGone("group-cashbox-opening-question")
        waitContains("Inclui o saldo inicial de ${brl("250")}")
        assertEquals(25000L, api("owner", openingPath).getLong("amountCents"))
        shot("saldo-inicial-250")

        click("group-cashbox-menu")
        waitTag("group-cashbox-menu-opening")
        waitText("O Pix do grupo fica na página do grupo.")
        shot("menu-do-caixa")
        click("group-cashbox-menu-opening")
        waitText("Quanto o grupo tinha antes do Saqz. Soma no saldo.")
        input("group-cashbox-opening-input", "300,00", scroll = false)
        click("group-cashbox-opening-save")
        ui.waitUntil(20_000) { api("owner", openingPath).getLong("amountCents") == 30000L }
        waitContains("Inclui o saldo inicial de ${brl("300")}")
        shot("saldo-inicial-300")

        // Saída: atalhos, quatro categorias e a data de ontem pelo calendário.
        click("group-cashbox-expense", scroll = true)
        waitTag("finance-new-entry")
        waitText("Nova saída")
        waitText("Toque para escolher outro dia.")
        for (label in listOf("Quadra", "Material", "Árbitro", "Outros")) segment("finance-new-entry-category", label)
        shot("nova-saida")
        click("finance-new-entry-date", scroll = true)
        waitTag("finance-new-entry-date-picker")
        waitText("Data do lançamento")
        click("game-picker-day-${data.getString("yesterday")}", scroll = true)
        shot("calendario-ontem")
        click("finance-new-entry-date-save")
        waitGone("finance-new-entry-date-save")
        selectSegment("finance-new-entry-category", "Quadra")
        ui.onNode(hasText("120", substring = true) and isSelectable() and hasAnyAncestor(hasTestTag("finance-new-entry-shortcuts")))
            .performScrollTo().performClick()
        input("finance-new-entry-description", "quadra de terça")
        shot("saida-preenchida")
        click("finance-new-entry-save")
        waitTag("group-cashbox-tab-out")
        val expense = expenses().single()
        assertEquals("VENUE", expense.getString("category"))
        assertEquals("OUT", expense.getString("direction"))
        assertEquals(12000L, expense.getLong("amountCents"))
        assertEquals(data.getString("yesterday"), expense.getString("expenseDate"))
        waitContains(brl("180,00"))
        shot("caixa-saiu-120")

        // Entrada: Patrocínio, Venda e Outros, sem atalhos de valor.
        click("group-cashbox-income", scroll = true)
        waitTag("finance-new-entry")
        waitText("Nova entrada")
        for (label in listOf("Patrocínio", "Venda", "Outros")) segment("finance-new-entry-category", label)
        ui.onNodeWithTag("finance-new-entry-shortcuts").assertDoesNotExist()
        input("finance-new-entry-amount", "50,00")
        selectSegment("finance-new-entry-category", "Patrocínio")
        input("finance-new-entry-description", "patrocínio da loja")
        shot("nova-entrada")
        click("finance-new-entry-save")
        waitTag("group-cashbox-tab-in")
        val income = expenses().single { it.getString("direction") == "IN" }
        assertEquals("SPONSORSHIP", income.getString("category"))
        assertEquals(5000L, income.getLong("amountCents"))
        waitContains(brl("230,00"))

        // O placar é aba: cada toque troca a lista de baixo.
        click("group-cashbox-tab-in")
        waitContains("Entradas de")
        waitText("patrocínio da loja")
        shot("aba-entrou")
        click("group-cashbox-tab-out")
        waitContains("Saídas de")
        waitText("quadra de terça")
        shot("aba-saiu")
        click("group-cashbox-tab-due")
        waitText("Ninguém devendo. Tudo recebido.")

        // Saída antiga de Racha (app antigo) e categoria de entrada com OUT recusada.
        val racha = JSONObject().put("requestId", java.util.UUID.randomUUID().toString()).put("description", "racha antigo")
            .put("amountCents", 3000).put("expenseDate", data.getString("today")).put("category", "RACHA").put("direction", "OUT")
        api("owner", "/api/groups/$group/expenses", "POST", racha, status = 201)
        api("owner", "/api/groups/$group/expenses", "POST",
            JSONObject(racha.toString()).put("requestId", java.util.UUID.randomUUID().toString()).put("category", "SPONSORSHIP"),
            status = 400)

        // Reabre para ler a saída nova e vai ao extrato.
        ui.onNode(hasContentDescription("Voltar") and hasClickAction() and hasAnyAncestor(hasTestTag("group-cashbox")))
            .performClick()
        waitGone("group-cashbox")
        click("group-details-cashbox", scroll = true)
        // "Ver extrato" fica embaixo da lista de lançamentos (abas Entrou e Saiu).
        click("group-cashbox-tab-out")
        click("group-cashbox-statement", scroll = true)
        waitTag("finance-statement-opening")
        waitText("Antes do Saqz")
        waitText("quadra de terça")
        waitText("racha antigo")
        shot("extrato-tudo")
        selectSegment("finance-statement-filter", "Saídas")
        ui.waitUntil(20_000) { ui.onAllNodesWithText("patrocínio da loja").fetchSemanticsNodes().isEmpty() }
        waitText("quadra de terça")
        waitText("racha antigo")
        shot("extrato-saidas")

        val summary = api("owner", "/api/groups/$group/finance/statement").getJSONObject("summary")
        assertEquals(30000L, summary.getLong("openingBalanceCents"))
        assertEquals(30000L + 5000 - 12000 - 3000, summary.getLong("accumulatedBalanceCents"))
        val version = api("owner", openingPath).getLong("version")
        api("owner", openingPath, "PUT", JSONObject().put("amountCents", -100), status = 400, headers = mapOf("If-Match" to "\"$version\""))
        api("athlete", openingPath, status = 403)
    }

    /** A opção existe no segmentado (sem tocar). */
    private fun segment(tag: String, label: String) {
        ui.onNode(hasText(label) and isSelectable() and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).assertExists()
    }

    private fun selectSegment(tag: String, label: String) {
        ui.onNode(hasText(label) and isSelectable() and hasAnyAncestor(hasTestTag(tag))).performScrollTo().performClick()
    }

    private fun expenses(): List<JSONObject> {
        val list = api("owner", "/api/groups/$group/expenses").getJSONArray("expenses")
        return (0 until list.length()).map { list.getJSONObject(it) }
    }
}
