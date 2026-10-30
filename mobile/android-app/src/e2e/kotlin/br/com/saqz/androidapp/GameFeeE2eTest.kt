package br.com.saqz.androidapp

import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * JOGO-008 (roteiro 13, V1–V10): o cartão "Defina o valor por jogo" só para o dono; o editor de jogo não salva sem
 * escolha nem com Cobrar diária vazio ou 0; Sem diária salva e o grupo sem valor passa a Sem diária. O avulso vê
 * "Diária de R$ 20, cobrada ao confirmar" no jogo com valor (não no Sem diária) e o Vou gera a diária de R$ 20.
 */
@RunWith(AndroidJUnit4::class)
internal class GameFeeE2eTest : InstalledE2e("game-fee") {
    private val gamesPath get() = "/api/groups/$group/games"

    @Test
    fun ownerMustChooseTheGameFeeAndTheAvulsoSeesAndPaysTheFee() {
        login("owner")
        openGroup()
        waitTag("group-details-game-fee-card")
        waitText("Só você e os admins veem")
        waitText("Defina o valor por jogo")
        waitText("Hoje o avulso joga sem diária.")
        shot("cartao-defina-o-valor")

        openEditor()
        pickTomorrowAt(20)
        input("game-editor-venue-name", "Quadra J1")
        waitTag("game-editor-game-fee")
        tap("game-editor-submit")
        waitTag("game-fee-error")
        waitText("Escolha o valor ou Sem diária.")
        shot("editor-sem-escolha")
        assertEquals(0, games().length())

        tap("game-fee-choice-charge")
        tap("game-editor-submit")
        waitText("Informe um valor maior que R$ 0,00.")
        input("game-fee-amount", "0")
        tap("game-editor-submit")
        waitText("Informe um valor maior que R$ 0,00.")
        shot("editor-cobrar-zero")
        assertEquals(0, games().length())

        tap("game-fee-choice-none")
        waitText("O avulso joga sem pagar por jogo.")
        shot("editor-sem-diaria")
        tap("game-editor-submit")
        waitGone("game-editor")
        // Depois de marcar, a tela volta ao grupo (ou ao jogo novo); o cartão do grupo some.
        val noFee = games().let { (0 until it.length()).map(it::getJSONObject) }.single()
        assertTrue(noFee.getBoolean("noGameFee"))
        assertTrue(noFee.isNull("gameFeeCents"))
        assertEquals("PUBLISHED", noFee.getString("status"))
        val defaults = api("owner", "/api/groups/$group").getJSONObject("financeDefaults")
        assertTrue(defaults.getBoolean("defaultNoGameFee"))
        assertFalse(defaults.getBoolean("gameFeeUndefined"))
        if (ui.onAllNodesWithTag("group-details").fetchSemanticsNodes().isEmpty()) back()
        waitTag("group-details")
        waitGone("group-details-game-fee-card")
        shot("grupo-sem-cartao")

        // V8: Editar grupo → Valor por jogo (em Sem diária) → Cobrar diária R$ 20 → salva.
        // Logo depois de marcar, o aviso "jogo marcado" ainda cobre o topo e pode engolir o primeiro toque.
        waitGone("group-details-toast")
        tap("group-details-edit-group")
        try {
            ui.waitUntil(5_000) { ui.onAllNodesWithTag("group-setup-submit").fetchSemanticsNodes().isNotEmpty() }
        } catch (_: ComposeTimeoutException) {
            tap("group-details-edit-group")
        }
        waitTag("group-setup-submit")
        waitTag("group-setup-game-fee")
        ui.onNodeWithTag("group-setup-game-fee").performScrollTo()
        ui.onNodeWithTag("game-fee-choice-none", useUnmergedTree = true).assertIsSelected()
        tap("game-fee-choice-charge")
        input("game-fee-amount", "20,00")
        shot("editar-grupo-cobrar-20")
        tap("group-setup-submit")
        waitGone("group-setup-submit")
        ui.waitUntil(20_000) {
            api("owner", "/api/groups/$group").getJSONObject("financeDefaults").optLong("defaultGameFeeCents") == 2000L
        }
        assertFalse(api("owner", "/api/groups/$group").getJSONObject("financeDefaults").getBoolean("defaultNoGameFee"))
        // O J1 continua Sem diária.
        assertTrue(api("owner", "$gamesPath/${noFee.getString("id")}").getBoolean("noGameFee"))

        // J2 com R$ 20 (precondição pela API: o valor no editor já foi coberto acima). Com dois jogos, a agenda do
        // grupo ganha o "Marcar jogo" (com um só, o topo mostra o jogo e não oferece marcar outro).
        val withFee = publishGame(data.getString("tomorrow"), "22:00", 2000, "Quadra J2")

        // V9: o jogo novo já vem em Cobrar diária com R$ 20; sai sem salvar.
        backToShell()
        openGroup()
        openEditor()
        waitTag("game-editor-game-fee")
        ui.onNodeWithTag("game-editor-game-fee").performScrollTo()
        ui.onNodeWithTag("game-fee-choice-charge", useUnmergedTree = true).assertIsSelected()
        ui.onNode(hasSetTextAction() and hasText("20,00", substring = true) and
            (hasTestTag("game-fee-amount") or hasAnyAncestor(hasTestTag("game-fee-amount"))), true).assertExists()
        shot("marcar-jogo-ja-com-20")
        back()
        if (ui.onAllNodesWithTag("game-editor").fetchSemanticsNodes().isNotEmpty()) back()
        waitGone("game-editor")
        assertEquals(2, games().length())
        backToShell()
        logout()

        login("athlete")
        openGroup()
        waitTag("group-details-hero")
        ui.onNodeWithTag("group-details-game-fee-card").assertDoesNotExist()
        shot("atleta-grupo-sem-cartao")

        // O J1 (20h) é o próximo jogo, no topo do grupo; o J2 (22h) fica na agenda.
        tap("group-details-view-game")
        waitTag("game-detail")
        waitTag("game-detail-attendance-going")
        ui.onNodeWithTag("game-detail-fee-note").assertDoesNotExist()
        shot("atleta-jogo-sem-diaria")
        back()

        openAgendaGame(withFee)
        waitTag("game-detail-fee-note")
        ui.onNode(hasText("cobrada ao confirmar", substring = true) and hasText("20", substring = true), useUnmergedTree = true)
            .assertExists()
        shot("atleta-jogo-diaria-20")
        tap("game-detail-attendance-going")
        ui.waitUntil(20_000) { ownCharges().length() == 1 }
        val charge = ownCharges().getJSONObject(0)
        assertEquals("GAME", charge.getString("kind"))
        assertEquals(withFee, charge.getString("gameId"))
        assertEquals(2000L, charge.getLong("amountCents"))
        assertEquals("PENDING", charge.getString("status"))
        assertEquals(data.getString("tomorrow"), charge.getString("dueDate"))
        dismissNotificationPrompt()
        waitTag("game-detail-attendance-change")
        shot("atleta-confirmado-com-diaria")
    }

    private fun openEditor() {
        waitTag("group-details")
        ui.waitUntil(20_000) {
            ui.onAllNodesWithTag("group-details-agenda-create").fetchSemanticsNodes().isNotEmpty() ||
                ui.onAllNodesWithTag("group-details-create-next-game").fetchSemanticsNodes().isNotEmpty()
        }
        if (ui.onAllNodesWithTag("group-details-create-next-game").fetchSemanticsNodes().isNotEmpty()) {
            tap("group-details-create-next-game")
        } else {
            // A tag fica no cabeçalho da agenda; o clicável ("Marcar jogo") é descendente dele.
            ui.onNodeWithTag("group-details-agenda-create").performScrollTo()
            ui.onNode(hasClickAction() and hasAnyAncestor(hasTestTag("group-details-agenda-create"))).performClick()
        }
        waitTag("game-editor")
    }

    private fun pickTomorrowAt(hour: Int) {
        tap("game-editor-date")
        waitTag("game-editor-date-time-picker")
        tap("game-picker-day-${data.getString("tomorrow")}")
        tap("game-picker-hour-$hour")
        tap("game-picker-minute-0")
        ui.onNode(hasText("Salvar data e horário") and hasClickAction()).performClick()
        waitGone("game-picker-hour-$hour")
    }

    private fun openAgendaGame(id: String) {
        val tag = "group-details-agenda-game-$id"
        waitTag(tag)
        tap(tag)
        waitTag("game-detail")
    }

    private fun games(): JSONArray = JSONArray(apiRaw("owner", gamesPath))

    private fun ownCharges(): JSONArray = api("athlete", "/api/groups/$group/charges/me").getJSONArray("charges")
}
