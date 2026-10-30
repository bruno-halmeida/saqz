package br.com.saqz.androidapp

import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * GER-003 (DEB-145, `tests/roteiros/geral.md`): G1 com foto e jogo sem resposta vem primeiro, G2 sem foto (já com
 * "Vou") depois e G3 sem jogo no fim, sob "3 grupos · 1 esperando sua resposta". Tocar fora da folha do Confirmar fecha
 * sem responder; Vou com vaga vira "Você vai"; Não vou (pela API, como no roteiro) vira "Não vai". "Admin" só para o dono.
 */
@RunWith(AndroidJUnit4::class)
internal class GroupListE2eTest : InstalledE2e("group-list") {
    private val third get() = data.getString("thirdGroup")

    @Test
    fun listOrdersGroupsConfirmsFromTheSheetAndShowsAdminOnlyToTheOwner() {
        login("athlete")
        groups()
        waitTag("group-list-summary")
        waitContains("3 grupos")
        waitContains("1 esperando sua resposta")
        for (id in listOf(group, secondGroup, third)) waitTag("group-list-group-$id")
        assertOrder(group, secondGroup, third)
        waitText("Nenhum jogo marcado por enquanto.")
        status(secondGroup, "Você vai")
        assertEquals(0, ui.onAllNodes(hasTestTag("group-list-admin-$group"), true).fetchSemanticsNodes().size)
        shot("atleta-lista-tres-grupos")

        tap("group-list-confirm-$group")
        waitTag("group-list-confirm-sheet")
        waitText("Você vai nesse jogo?")
        shot("folha-confirmar")
        // Toque fora da folha, na lista escurecida acima dela (o topo tem o aviso de e-mail, fora do véu).
        shell("input tap 540 1000")
        ui.waitUntil(20_000) { ui.onAllNodesWithTag("group-list-confirm-yes").fetchSemanticsNodes().isEmpty() }
        waitTag("group-list-confirm-$group")
        assertTrue(api("athlete", attendancePath()).isNull("ownAttendance"))
        shot("folha-fechada-sem-resposta")

        tap("group-list-confirm-$group")
        tap("group-list-confirm-yes")
        status(group, "Você vai")
        waitGone("group-list-confirm-$group")
        ui.waitUntil(20_000) {
            ui.onAllNodes(hasText("esperando sua resposta", substring = true), true).fetchSemanticsNodes().isEmpty()
        }
        assertEquals("CONFIRMED", api("athlete", attendancePath()).getJSONObject("ownAttendance").getString("status"))
        shot("vou-voce-vai")

        api("athlete", attendancePath(), "PUT", JSONObject().put("requestId", UUID.randomUUID().toString()).put("intent", "DECLINE"))
        // A aba relê ao voltar a ela.
        tab("Início")
        groups()
        status(group, "Não vai")
        shot("nao-vou-nao-vai")
        logout()

        login("owner")
        groups()
        waitTag("group-list-group-$group")
        ui.waitUntil(20_000) { ui.onAllNodes(hasTestTag("group-list-admin-$group"), true).fetchSemanticsNodes().isNotEmpty() }
        shot("dono-etiqueta-admin")
    }

    private fun attendancePath() = "/api/groups/$group/games/${data.getString("game")}/attendance"

    private fun status(id: String, text: String) {
        val tag = "group-list-status-$id"
        ui.waitUntil(20_000) {
            ui.onAllNodes(hasText(text) and (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))), true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertOrder(vararg ids: String) {
        val tops = ids.map { ui.onNodeWithTag("group-list-group-$it").fetchSemanticsNode().boundsInRoot.top }
        assertEquals(tops.sorted(), tops)
    }
}
