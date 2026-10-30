package br.com.saqz.androidapp

import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * PROM-010 (roteiro 13, A1–A17): a opção só aparece na Escolha manual; ligada, o Vou do avulso vai para a espera com
 * "Pague R$ 20 para entrar" e a diária pendente. Na espera do gestor: Falta pagar → Recebi → Pagou → Colocar sem segunda
 * diária; convidado abre a folha "Colocar, já pagou" / "Colocar e cobrar depois"; mensalista entra sem folha. Voltar
 * para Ordem de chegada desliga com o aviso.
 */
@RunWith(AndroidJUnit4::class)
internal class AdvancePaymentE2eTest : InstalledE2e("advance-payment") {
    private val athleteId get() = actor("athlete").getString("id")
    private val mensalId get() = actor("mensal").getString("id")

    @Test
    fun avulsoPaysBeforeTheOrganizerPlacesThemAndGuestsUseTheSheet() {
        login("owner")
        openGroup()
        openEditGroup()
        ui.onNodeWithTag("group-game-config-advance-payment").assertDoesNotExist()
        shot("ordem-de-chegada-sem-opcao")
        selectOption("group-game-config-promotion-mode", "Escolha manual")
        waitTag("group-game-config-advance-payment")
        waitText("O avulso paga antes de entrar.")
        tap("group-game-config-advance-payment")
        waitText("Vale para avulso e convidado. A diária nasce no Vou, com o Pix do grupo.")
        shot("escolha-manual-antecipado-ligado")
        tap("group-setup-submit")
        waitGone("group-setup-submit")
        val config = api("owner", "/api/groups/$group").getJSONObject("gameConfig")
        assertTrue(config.getBoolean("advancePaymentRequired"))
        assertEquals("MANUAL", config.getString("promotionMode"))

        val game = publishGame(data.getString("tomorrow"), "20:00", null, "Quadra J3")
        val gamePath = "/api/groups/$group/games/$game"
        assertEquals(2000L, api("owner", gamePath).getLong("gameFeeCents"))
        backToShell()
        logout()

        // Avulso: Vou → espera com a caixa de pagar e a diária pendente.
        login("athlete")
        openGroup()
        tap("group-details-view-game")
        waitTag("game-detail")
        tap("game-detail-attendance-going")
        dismissNotificationPrompt()
        waitTag("game-detail-advance-payment")
        waitContains("Pague ${brl("20")} para entrar")
        waitText("A vaga é liberada pelo organizador depois do pagamento.")
        shot("avulso-espera-pague-para-entrar")
        tap("game-detail-advance-payment-copy-pix")
        // Diferença registrada: o roteiro (A4) espera "Pix do grupo copiado.", mas o app não mostra aviso nenhum
        // (a string advance_payment_pix_copied não é usada). A cópia em si é conferida na área de transferência.
        assertEquals(data.getString("pixKey"), clipboard())
        shot("avulso-pix-copiado")
        val own = api("athlete", "$gamePath/attendance").getJSONObject("ownAttendance")
        assertEquals("WAITLISTED", own.getString("status"))
        val athleteCharge = charges().single { it.getString("memberId") == athleteId }
        assertEquals(2000L, athleteCharge.getLong("amountCents"))
        assertEquals("PENDING", athleteCharge.getString("status"))
        assertEquals(data.getString("tomorrow"), athleteCharge.getString("dueDate"))
        backToShell()
        logout()

        // Os outros pela API: mensalista com Bia e Caio, e o outro avulso.
        attend("mensal")
        guest("Bia")
        guest("Caio")
        attend("other")
        val pending = charges()
        assertEquals(4, pending.size)
        assertTrue(pending.all { it.getString("status") == "PENDING" })
        assertTrue(pending.none { it.getString("memberId") == mensalId && it.isNull("guestDisplayName") })

        login("owner")
        openGroup()
        tap("group-details-view-game")
        waitTag("game-detail")
        tap("game-tab-waiting")
        waitTag("game-detail-advance-line-$athleteId")
        lineContains(athleteId, "Falta pagar")
        lineContains("$mensalId#1", "não pagou")
        ui.onNodeWithTag("game-detail-advance-line-$mensalId").assertDoesNotExist()
        waitText("Você escolhe quem entra. Pix e dinheiro chegam por fora: marque Recebi.")
        shot("gestor-espera-falta-pagar")

        tap("game-detail-advance-receive-$athleteId")
        lineContains(athleteId, "Pagou")
        ui.waitUntil(20_000) { charges().single { it.getString("memberId") == athleteId && it.isNull("guestDisplayName") }.getString("status") == "PAID" }
        shot("gestor-recebi-pagou")
        tap("game-detail-promote-$athleteId")
        ui.waitUntil(20_000) { status(athleteId) == "CONFIRMED" }
        ui.onNodeWithTag("game-detail-advance-place-sheet").assertDoesNotExist()
        assertEquals(1, charges().count { it.getString("memberId") == athleteId })

        tap("game-detail-promote-$mensalId#1")
        waitTag("game-detail-advance-place-paid")
        waitContains("ainda não pagou")
        waitText("Colocar e cobrar depois")
        shot("folha-colocar-convidada")
        tap("game-detail-advance-place-paid")
        ui.waitUntil(20_000) { guestCharge("Bia").getString("status") == "PAID" }
        tap("game-detail-promote-$mensalId#2")
        tap("game-detail-advance-place-later")
        ui.waitUntil(20_000) { status(mensalId, 2) == "CONFIRMED" }
        assertEquals("PENDING", guestCharge("Caio").getString("status"))
        tap("game-detail-promote-$mensalId")
        ui.waitUntil(20_000) { status(mensalId) == "CONFIRMED" }
        assertTrue(charges().none { it.getString("memberId") == mensalId && it.isNull("guestDisplayName") })
        shot("gestor-colocados")

        // Não vou depois de pagar não desfaz a baixa; o atleta não muda a regra do grupo.
        api("athlete", "$gamePath/attendance", "PUT", JSONObject().put("requestId", UUID.randomUUID().toString()).put("intent", "DECLINE"))
        val paid = charges().single { it.getString("memberId") == athleteId }
        assertEquals("PAID", paid.getString("status"))
        assertTrue(paid.getBoolean("withdrew"))
        assertTrue(charges().single { it.getString("memberId") == actor("other").getString("id") }.getBoolean("waitlisted"))
        // O atleta não dá Recebi: a rota de status com If-Match válido volta 403 e a cobrança não muda.
        val otherCharge = charges().single { it.getString("memberId") == actor("other").getString("id") }
        api("athlete", "/api/groups/$group/charges/${otherCharge.getString("id")}/status", "POST",
            JSONObject().put("status", "PAID").put("paidMethod", "PIX"), status = 403,
            headers = mapOf("If-Match" to "\"${otherCharge.getLong("version")}\""))
        assertEquals(otherCharge.toString(), charges().single { it.getString("id") == otherCharge.getString("id") }.toString())

        // Encerrado pela API, o acerto mostra quem desistiu e quem ficou na espera.
        val version = api("owner", gamePath).getLong("version")
        api("owner", "$gamePath/complete", "POST", headers = mapOf("If-Match" to "\"$version\""))
        // Encerrado antes do horário, o jogo não está em "Jogos anteriores" nem no topo: a tela do jogo, ainda aberta,
        // relê ao voltar ao primeiro plano e passa a oferecer o acerto.
        ui.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        ui.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        waitTag("game-detail")
        ui.waitUntil(20_000) {
            ui.onAllNodesWithTag("game-detail-settle").fetchSemanticsNodes().isNotEmpty() ||
                ui.onAllNodesWithTag("game-detail-settle-view").fetchSemanticsNodes().isNotEmpty()
        }
        tap(if (ui.onAllNodesWithTag("game-detail-settle").fetchSemanticsNodes().isNotEmpty()) "game-detail-settle" else "game-detail-settle-view")
        waitTag("game-settlement")
        settlementRow(paid.getString("id"), "Desistiu")
        settlementRow(otherCharge.getString("id"), "Na espera", "Não liberado")
        shot("acerto-desistiu-e-na-espera")
        backToShell()

        // Ordem de chegada desliga a opção, com o aviso.
        val before = charges().map { it.getString("id") to it.getString("status") }.toSet()
        openGroup()
        openEditGroup()
        selectOption("group-game-config-promotion-mode", "Ordem de chegada")
        waitTag("group-game-config-advance-payment-turned-off")
        waitText("A vaga volta a ser por ordem de chegada; quem está na espera sobe pela fila.")
        shot("ordem-de-chegada-desliga")
        tap("group-setup-submit")
        waitGone("group-setup-submit")
        val after = api("owner", "/api/groups/$group").getJSONObject("gameConfig")
        assertFalse(after.getBoolean("advancePaymentRequired"))
        assertEquals("FIFO", after.getString("promotionMode"))
        assertEquals(before, charges().map { it.getString("id") to it.getString("status") }.toSet())
    }

    /** Linha de "Não jogaram" no acerto com uma das etiquetas. */
    private fun settlementRow(chargeId: String, vararg labels: String) {
        val tag = "game-settlement-not-played-$chargeId"
        waitTag(tag)
        ui.onNodeWithTag(tag).performScrollTo()
        val inRow = hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))
        ui.waitUntil(20_000) {
            labels.any { ui.onAllNodes(hasText(it) and inRow, true).fetchSemanticsNodes().isNotEmpty() }
        }
    }

    private fun openEditGroup() {
        tap("group-details-edit-group")
        waitTag("group-setup-submit")
        waitTag("group-game-config-promotion-mode")
        ui.onNodeWithTag("group-game-config-promotion-mode").performScrollTo()
    }

    private fun lineContains(rowId: String, text: String) {
        val tag = "game-detail-advance-line-$rowId"
        waitTag(tag)
        ui.onNodeWithTag(tag).performScrollTo()
        ui.waitUntil(20_000) {
            ui.onAllNodes(hasText(text, substring = true) and (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))), true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun attend(role: String) {
        api(role, "/api/groups/$group/games/${game()}/attendance", "PUT",
            JSONObject().put("requestId", UUID.randomUUID().toString()).put("intent", "CONFIRM"))
    }

    private fun guest(name: String) {
        api("mensal", "/api/groups/$group/games/${game()}/attendance/guests", "POST",
            JSONObject().put("requestId", UUID.randomUUID().toString()).put("displayName", name), status = 201)
    }

    private fun game(): String = charges().first().getString("gameId")

    private fun status(memberId: String, guestSeq: Int = 0): String {
        val roster = api("owner", "/api/groups/$group/games/${game()}/attendance/roster")
        for (list in listOf("confirmed", "waitlisted")) {
            val items = roster.getJSONArray(list)
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                if (item.getString("memberId") == memberId && item.optInt("guestSeq") == guestSeq) {
                    return if (list == "confirmed") "CONFIRMED" else "WAITLISTED"
                }
            }
        }
        return "NONE"
    }

    private fun guestCharge(name: String) = charges().single { it.optString("guestDisplayName") == name }

    private fun charges(): List<JSONObject> {
        val list = api("owner", "/api/groups/$group/charges").getJSONArray("charges")
        return (0 until list.length()).map(list::getJSONObject)
    }
}
