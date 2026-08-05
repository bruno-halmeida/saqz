package br.com.saqz.groups.presentation.ui.home

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * O desempate das duas faixas do shell (VUL-202). A regra é por estado da dívida e não por
 * ordem fixa: a faixa de e-mail **não** acaba sozinha (o VUL-76 tirou a trava do backend),
 * então uma ordem fixa esconderia dívida vencida para sempre de quem nunca confirma.
 */
class HomeOwnChargesBannerPriorityTest {
    @Test
    fun `vencida ganha da faixa de e-mail`() {
        assertTrue(ownChargesBannerWins(previewOwnChargesOverdue(), hasEmailBanner = true))
    }

    @Test
    fun `no prazo cede a vez para a faixa de e-mail`() {
        assertFalse(ownChargesBannerWins(previewOwnCharges(), hasEmailBanner = true))
    }

    @Test
    fun `sem faixa de e-mail a cobrança fica vencida ou não`() {
        assertTrue(ownChargesBannerWins(previewOwnCharges(), hasEmailBanner = false))
        assertTrue(ownChargesBannerWins(previewOwnChargesOverdue(), hasEmailBanner = false))
    }

    @Test
    fun `sem pendência nenhuma a vez é sempre da faixa de e-mail`() {
        assertFalse(ownChargesBannerWins(charges = null, hasEmailBanner = true))
        assertFalse(ownChargesBannerWins(charges = null, hasEmailBanner = false))
    }
}
