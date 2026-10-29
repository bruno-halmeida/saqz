package br.com.saqz.androidapp

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FIN-008 (roteiro 13, X1–X8): com diária em aberto o atleta não vê a seção "Pix do grupo" (a chave está no cartão da
 * cobrança); depois do Recebi do gestor, a seção aparece logo depois de Minhas cobranças, com chave, tipo e Copiar. O
 * gestor a vê depois da Galera. No outro grupo, sem chave, ninguém vê.
 */
@RunWith(AndroidJUnit4::class)
internal class GroupPixE2eTest : InstalledE2e("group-pix") {
    private val charge get() = data.getString("charge")
    private val pixKey get() = data.getString("pixKey")

    @Test
    fun groupPixShowsOnlyWithoutOpenChargesAndCopiesTheKey() {
        login("athlete")
        openGroup()
        waitTag("group-details-own-charge-$charge")
        ui.onNodeWithTag("group-details-own-charge-$charge").performScrollTo()
        ui.onNodeWithTag("group-details-group-pix").assertDoesNotExist()
        waitContains(pixKey)
        shot("atleta-com-cobranca-sem-secao")
        backToShell()
        logout()

        login("owner")
        openGroup()
        tap("group-details-cashbox")
        tap("group-cashbox-tab-due")
        tap("group-cashbox-received-$charge")
        waitTag("finance-receipt-sheet")
        shot("recebi-diaria")
        tap("finance-receipt-confirm")
        ui.waitUntil(20_000) { api("owner", "/api/groups/$group/charges").getJSONArray("charges").getJSONObject(0).getString("status") == "PAID" }
        waitText("Ninguém devendo. Tudo recebido.")
        ui.onNode(hasContentDescription("Voltar") and hasClickAction() and hasAnyAncestor(hasTestTag("group-cashbox"))).performClick()
        waitGone("group-cashbox")
        waitTag("group-details-group-pix")
        ui.onNodeWithTag("group-details-group-pix").performScrollTo()
        assertBelow("group-details-group-pix", "group-details-people")
        shot("gestor-secao-depois-da-galera")
        backToShell()
        logout()

        login("athlete")
        openGroup()
        waitTag("group-details-group-pix")
        ui.onNodeWithTag("group-details-group-pix").performScrollTo()
        ui.onNode(hasText(pixKey, substring = true) and hasAnyAncestor(hasTestTag("group-details-group-pix")), true).assertExists()
        ui.onNode(hasText("E-mail", substring = true) and hasAnyAncestor(hasTestTag("group-details-group-pix")), true).assertExists()
        // Com tudo pago, "Minhas cobranças" pode ficar só com o histórico: a seção vem depois do que houver.
        val ownSection = listOf("group-details-own-charges-settled", "group-details-own-charges", "group-details-own-charges-history")
            .firstOrNull { ui.onAllNodesWithTag(it).fetchSemanticsNodes().isNotEmpty() }
        assertBelow("group-details-group-pix", checkNotNull(ownSection))
        shot("atleta-secao-pix-do-grupo")
        tap("group-details-group-pix-copy")
        assertEquals(pixKey, clipboard())
        shot("atleta-pix-copiado")
        backToShell()

        // Sem chave Pix (o grupo de praia), a seção não existe.
        openGroup(secondGroup)
        waitTag("group-details-people")
        ui.onNodeWithTag("group-details-group-pix").assertDoesNotExist()
        shot("grupo-sem-chave-sem-secao")
    }

    private fun assertBelow(lower: String, upper: String) {
        val bottom = ui.onNodeWithTag(lower).fetchSemanticsNode().boundsInRoot.top
        val top = ui.onNodeWithTag(upper).fetchSemanticsNode().boundsInRoot.top
        assertTrue("$lower deve vir depois de $upper", bottom > top)
    }
}

/** Lê a área de transferência no processo do app (em primeiro plano, então a leitura é permitida). */
internal fun clipboard(): String? {
    var text: String? = null
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.runOnMainSync {
        val manager = instrumentation.targetContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        text = manager.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
    }
    return text
}
