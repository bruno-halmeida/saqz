package br.com.saqz.androidapp

import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DEB-141/142/149 com o recebimento pelo app desligado (padrão do stack e da produção): o gestor não vê oferta nem
 * carteira do recebimento no grupo, no caixa nem em Editar grupo; o atleta com diária vê a chave Pix para copiar, sem
 * "Pagar" pelo app.
 */
@RunWith(AndroidJUnit4::class)
internal class ReceivablesOffE2eTest : InstalledE2e("receivables-off") {
    private val receivingTags = listOf("group-receiving", "group-receiving-offer", "cashbox-wallet", "cashbox-review", "group-fee-preview")

    @Test
    fun organizerSeesNoInAppReceivingAndAthleteCopiesTheGroupPix() {
        login("owner")
        openGroup()
        waitTag("group-details-people")
        assertNoReceiving()
        shot("gestor-grupo-sem-recebimento")
        tap("group-details-cashbox")
        waitTag("group-cashbox-hero")
        assertNoReceiving()
        shot("gestor-caixa-sem-recebimento")
        ui.onNode(hasContentDescription("Voltar") and hasClickAction() and hasAnyAncestor(hasTestTag("group-cashbox"))).performClick()
        waitGone("group-cashbox")
        tap("group-details-edit-group")
        waitTag("group-setup-submit")
        ui.onNodeWithTag("group-setup-pix-key").performScrollTo()
        assertNoReceiving()
        shot("gestor-editar-grupo-so-chave-pix")
        backToShell()
        logout()

        login("athlete")
        openGroup()
        val tag = "group-details-own-charge-${data.getString("charge")}"
        waitTag(tag)
        ui.onNodeWithTag(tag).performScrollTo()
        ui.onNodeWithTag("group-details-own-charges-pay").assertDoesNotExist()
        ui.onNodeWithText("Pagar").assertDoesNotExist()
        tap("group-details-own-charges-pix-copy")
        assertEquals(data.getString("pixKey"), clipboard())
        shot("atleta-copia-pix-sem-pagar")
    }

    private fun assertNoReceiving() {
        for (tag in receivingTags) ui.onNodeWithTag(tag).assertDoesNotExist()
    }
}
