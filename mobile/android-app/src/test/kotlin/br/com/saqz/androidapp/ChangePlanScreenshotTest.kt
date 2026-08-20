package br.com.saqz.androidapp

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.designsystem.UiText
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.subscriptions.domain.subscription.Plan
import br.com.saqz.subscriptions.presentation.changeplan.ChangePlanCardUi
import br.com.saqz.subscriptions.presentation.changeplan.ChangePlanState
import br.com.saqz.subscriptions.presentation.ui.changeplan.ChangePlanScreen
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = android.app.Application::class,
)
class ChangePlanScreenshotTest {

    private companion object {
        const val SHUTTER_MILLIS = 600L
    }

    @get:Rule
    val compose = createComposeRule()

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { SaqzTheme { content() } }
        compose.mainClock.advanceTimeBy(SHUTTER_MILLIS)
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    @Test
    fun changePlanCatalogoComPlanoAtual() = capture("changeplan-catalogo") {
        ChangePlanScreen(
            state = CATALOG,
            onBack = {},
            onIntent = {},
            onCopyPix = {},
            onOpenInvoice = {},
        )
    }

    @Test
    fun changePlanConfirmarTroca() = capture("changeplan-confirmar") {
        ChangePlanScreen(
            state = CATALOG.copy(confirmTarget = CATALOG.plans.last()),
            onBack = {},
            onIntent = {},
            onCopyPix = {},
            onOpenInvoice = {},
        )
    }
}

private val CATALOG = ChangePlanState(
    isLoading = false,
    currentPlan = Plan.Organizador,
    plans = listOf(
        ChangePlanCardUi(
            plan = Plan.Titular,
            name = "Titular",
            priceLabel = UiText.Raw("R$ 39,90/mês"),
            benefits = listOf(UiText.Raw("1 grupo"), UiText.Raw("Até 25 atletas por grupo")),
            isCurrent = false,
        ),
        ChangePlanCardUi(
            plan = Plan.Organizador,
            name = "Organizador",
            priceLabel = UiText.Raw("R$ 59,90/mês"),
            benefits = listOf(UiText.Raw("3 grupos"), UiText.Raw("Atletas ilimitados")),
            isCurrent = true,
        ),
        ChangePlanCardUi(
            plan = Plan.Ilimitado,
            name = "Ilimitado",
            priceLabel = UiText.Raw("R$ 89,90/mês"),
            benefits = listOf(UiText.Raw("Grupos ilimitados"), UiText.Raw("Atletas ilimitados")),
            isCurrent = false,
        ),
    ),
)
