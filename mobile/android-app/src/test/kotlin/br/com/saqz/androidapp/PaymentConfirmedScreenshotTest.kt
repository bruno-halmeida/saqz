package br.com.saqz.androidapp

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.receivables.presentation.*
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** "Pagamento confirmado" na faixa verde (proposta A do canvas do financeiro, DEB-149), com e sem a oferta do automático. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7, application = Application::class)
class PaymentConfirmedScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test fun monthlyPaymentShowsTheGreenBandAndTheOfferAsAWhiteCard() {
        val intents = mutableListOf<PaymentIntent>()
        var closed = 0
        compose.setContent { SaqzTheme { PaymentScreen(monthly, intents::add, { closed++ }) } }
        // O título aparece uma vez só: a barra de cima perdeu o "Pagamento confirmado" repetido.
        compose.onNodeWithText("Pagamento confirmado").assertIsDisplayed()
        compose.onNodeWithText("R$ 100,00 · mensalidade de agosto").assertIsDisplayed()
        compose.onNodeWithText("O organizador já vê no caixa do grupo.").assertIsDisplayed()
        compose.onNodeWithText("No mês que vem, a mensalidade se paga sozinha no cartão, todo dia 10. Cancele quando quiser.")
            .assertIsDisplayed()
        capture("confirmado-oferta")
        compose.onNodeWithTag(PaymentScreenTags.AcceptOffer).performClick()
        compose.onNodeWithTag(PaymentScreenTags.DeclineOffer).performClick()
        assertEquals(listOf(PaymentIntent.AcceptOffer, PaymentIntent.DeclineOffer), intents)
        compose.onNodeWithContentDescription("Fechar").assertHasClickAction()
        compose.onNodeWithTag(PaymentScreenTags.Close).performClick()
        compose.onNodeWithTag(PaymentScreenTags.Done).performClick()
        assertEquals(2, closed)
    }

    @Test fun gamePaymentHasNoOfferAndKeepsBackToGroupAsTheMainAction() {
        compose.setContent { SaqzTheme { PaymentScreen(game, {}, {}) } }
        compose.onNodeWithText("R$ 20,00 · diária de terça, 27/10").assertIsDisplayed()
        compose.onNodeWithTag(PaymentScreenTags.Offer).assertDoesNotExist()
        compose.onNodeWithTag(PaymentScreenTags.Done).assertIsDisplayed()
        capture("confirmado-diaria")
    }

    @Test fun largeFontScrollsTheOfferAndKeepsTheFooter() {
        compose.setContent {
            val density = LocalDensity.current
            // Com 1,3 tudo cabe na tela; com 2 a faixa e a oferta passam da altura e a tela precisa rolar.
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                SaqzTheme { PaymentScreen(monthly, {}, {}) }
            }
        }
        compose.onNodeWithTag(PaymentScreenTags.Done).assertIsDisplayed()
        capture("confirmado-fonte-grande")
        compose.onNodeWithTag(PaymentScreenTags.DeclineOffer).performScrollTo().assertIsDisplayed()
        capture("confirmado-fonte-grande-rolada")
    }

    private fun capture(name: String) = compose.onRoot().captureRoboImage("screenshots/deb-149/$name.png")

    private val monthly = PaymentState(groupName = "Vôlei do CERET", loading = false,
        confirmed = PaymentConfirmedUi("R$ 100,00 · mensalidade de agosto", offer = true, dueDay = "10"))
    private val game = PaymentState(groupName = "Vôlei do CERET", loading = false,
        confirmed = PaymentConfirmedUi("R$ 20,00 · diária de terça, 27/10", offer = false, dueDay = null))
}
