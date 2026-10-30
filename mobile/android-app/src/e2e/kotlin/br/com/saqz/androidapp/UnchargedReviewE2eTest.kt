package br.com.saqz.androidapp

import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FIN-016 (roteiro 13, R1–R11): o J1 era Sem diária com dois avulsos e um mensalista com a convidada Bia confirmados;
 * passou a R$ 20 e foi encerrado (fixture). O caixa e o acerto mostram "3 jogaram sem cobrança"; a revisão cobra R$ 10 do
 * atleta, dá Já pagou em Dinheiro ao outro avulso e não cobra a Bia; reabrir cobra a Bia. O mesmo requestId não tem
 * segundo efeito, o atleta recebe 403 e não ganha aviso; ele vê a diária em Minhas cobranças.
 */
@RunWith(AndroidJUnit4::class)
internal class UnchargedReviewE2eTest : InstalledE2e("uncharged-review") {
    private val game get() = data.getString("game")
    private val athleteId get() = actor("athlete").getString("id")
    private val otherId get() = actor("other").getString("id")
    private val mensalId get() = data.getJSONObject("mensal").getString("id")
    private val unchargedPath get() = "/api/groups/$group/games/$game/uncharged"

    @Test
    fun organizerReviewsWhoPlayedWithoutAChargeAndReopensTheSkippedGuest() {
        val inboxBefore = api("athlete", "/api/me/notifications").getJSONArray("items").length()
        login("owner")
        openGroup()
        tap("group-details-cashbox")
        waitTag("group-cashbox-uncharged")
        waitText("3 jogaram sem cobrança")
        shot("caixa-faixa-retroativa")
        ui.onNode(hasContentDescription("Voltar") and hasClickAction() and hasAnyAncestor(hasTestTag("group-cashbox")))
            .performClick()
        waitGone("group-cashbox")

        openSettlement()
        waitTag("game-settlement-uncharged-strip")
        waitText("JOGO ENCERRADO")
        waitText("Confirmaram antes do valor")
        waitContains("cobertos pela mensalidade")
        shot("acerto-faixa-retroativa")

        tap("game-settlement-uncharged-review")
        waitTag("uncharged-review")
        waitText("Quem jogou sem cobrança")
        waitText("O jogo não tinha valor quando confirmaram.")
        waitText("Mensalistas ficam de fora: já pagam a mensalidade.")
        waitContains("Convite · paga")
        waitContains("3 cobranças")
        // R5: o resumo só com as partes diferentes de zero.
        ui.onNodeWithTag("uncharged-review-summary").assertTextEquals("3 cobranças")
        for (key in listOf("$athleteId/0", "$otherId/0", "$mensalId/1")) waitTag("uncharged-review-pill-$key")
        shot("revisao-aberta")

        input("uncharged-review-amount", "10,00")
        choose("$otherId/0", "PAID")
        waitTag("uncharged-review-method-$otherId/0")
        waitContains("Entra no caixa em")
        // R6: o toque no meio abre o menu "Como pagou".
        tap("uncharged-review-method-$otherId/0")
        waitText("Como pagou")
        waitTag("uncharged-review-method-option-Cash")
        shot("revisao-como-pagou")
        tap("uncharged-review-method-option-Cash")
        waitGone("uncharged-review-method-option-Cash")
        ui.waitUntil(20_000) {
            ui.onAllNodes(hasText("Dinheiro") and hasAnyAncestor(hasTestTag("uncharged-review-method-$otherId/0")) or
                (hasText("Dinheiro") and hasTestTag("uncharged-review-method-$otherId/0")), true).fetchSemanticsNodes().isNotEmpty()
        }
        choose("$mensalId/1", "SKIP")
        waitContains("1 cobrança")
        waitContains("1 já pagou")
        waitContains("1 sem cobrança")
        shot("revisao-decidida")
        tap("uncharged-review-confirm")
        waitGone("uncharged-review")
        waitTag("game-settlement")
        waitGone("game-settlement-uncharged-strip")
        waitTag("game-settlement-uncharged-$mensalId/1")
        waitText("Não cobrar")
        shot("acerto-sem-cobranca-bia")

        val charges = gameCharges()
        assertEquals(2, charges.size)
        val own = charges.single { it.getString("memberId") == athleteId }
        assertEquals(1000L, own.getLong("amountCents"))
        assertEquals("PENDING", own.getString("status"))
        assertEquals(data.getString("today"), own.getString("dueDate"))
        val paid = charges.single { it.getString("memberId") == otherId }
        assertEquals(1000L, paid.getLong("amountCents"))
        assertEquals("PAID", paid.getString("status"))
        assertEquals("CASH", paid.getString("paidMethod"))
        assertEquals(0, api("owner", "/api/groups/$group/finance/uncharged").getInt("peopleCount"))
        val bia = api("owner", unchargedPath).getJSONArray("people").let { list ->
            (0 until list.length()).map(list::getJSONObject).single { it.getInt("guestSeq") == 1 }
        }
        assertEquals("SKIP", bia.getString("decision"))

        // Reabrir e cobrar a convidada.
        ui.onNode(hasText("Reabrir a revisão") and hasClickAction()).performScrollTo().performClick()
        waitTag("uncharged-review")
        // Reaberta, a revisão volta a sugerir o valor do jogo (R$ 20); o roteiro espera os R$ 10 do lote: digita de novo.
        input("uncharged-review-amount", "10,00")
        choose("$mensalId/1", "CHARGE")
        shot("revisao-reaberta-bia")
        tap("uncharged-review-confirm")
        waitGone("uncharged-review")
        waitTag("game-settlement")
        ui.waitUntil(20_000) { gameCharges().size == 3 }
        val guest = gameCharges().single { it.optString("guestDisplayName") == "Bia" }
        assertEquals(mensalId, guest.getString("memberId"))
        assertEquals(1000L, guest.getLong("amountCents"))
        assertEquals("PENDING", guest.getString("status"))
        waitGone("game-settlement-uncharged-$mensalId/1")
        shot("acerto-bia-cobrada")

        // Mesmo requestId: mesmo resultado, sem segundo efeito; o atleta não lê nem revisa.
        val repeat = JSONObject().put("requestId", "6f1d2c3b-0000-4000-8000-000000000013").put("amountCents", 1000)
            .put("people", org.json.JSONArray().put(JSONObject().put("memberId", athleteId).put("guestSeq", 0).put("decision", "CHARGE")))
        val first = api("owner", "$unchargedPath/review", "POST", repeat)
        val second = api("owner", "$unchargedPath/review", "POST", repeat)
        assertEquals(first.toString(), second.toString())
        assertEquals(1, first.getInt("ignored"))
        assertEquals(3, gameCharges().size)
        api("athlete", unchargedPath, status = 403)
        // Nada vai ao atleta (FIN-016).
        assertEquals(inboxBefore, api("athlete", "/api/me/notifications").getJSONArray("items").length())

        backToShell()
        logout()
        login("athlete")
        openGroup()
        val tag = "group-details-own-charge-${own.getString("id")}"
        waitTag(tag)
        ui.onNodeWithTag(tag).performScrollTo()
        ui.onNode(hasText(brl("10"), substring = true) and hasAnyAncestor(hasTestTag(tag)), true).assertExists()
        shot("atleta-minhas-cobrancas")
    }

    private fun openSettlement() {
        tap("group-details-agenda-past")
        waitTag("past-games-list")
        tap("past-games-game-$game")
        waitTag("game-detail")
        ui.waitUntil(20_000) {
            ui.onAllNodesWithTag("game-detail-settle").fetchSemanticsNodes().isNotEmpty() ||
                ui.onAllNodesWithTag("game-detail-settle-view").fetchSemanticsNodes().isNotEmpty()
        }
        if (ui.onAllNodesWithTag("game-detail-settle").fetchSemanticsNodes().isNotEmpty()) {
            tap("game-detail-settle")
        } else {
            tap("game-detail-settle-view")
        }
        waitTag("game-settlement")
    }

    private fun choose(key: String, decision: String) {
        tap("uncharged-review-pill-$key")
        // A tag da opção não chega à árvore (a ação da folha não repassa o modifier): toca pelo texto da opção.
        val label = mapOf("CHARGE" to "Cobrar", "PAID" to "Já pagou", "SKIP" to "Não cobrar").getValue(decision)
        val option = hasText(label) and hasClickAction() and !hasTestTag("uncharged-review-pill-$key")
        ui.waitUntil(20_000) { ui.onAllNodes(option).fetchSemanticsNodes().any { it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.TestTag).not() } }
        val nodes = ui.onAllNodes(option)
        val index = nodes.fetchSemanticsNodes().indexOfLast { !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.TestTag) }
        nodes[index].performClick()
        ui.waitForIdle()
        Thread.sleep(600)
    }

    private fun gameCharges(): List<JSONObject> {
        val list = api("owner", "/api/groups/$group/charges").getJSONArray("charges")
        return (0 until list.length()).map(list::getJSONObject).filter { it.optString("gameId") == game }
    }
}
