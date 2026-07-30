package br.com.saqz.androidapp

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.designsystem.UiText
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.subscriptions.presentation.planactive.PlanActiveState
import br.com.saqz.subscriptions.presentation.ui.planactive.PlanActiveScreen
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * As cenas da 8d (VUL-111). Arquivo próprio, e não mais um bloco do `SaqzScreenshotTest`:
 * aquele é o catálogo do design system (VUL-92) e os quatro tickets de tela desta onda
 * (109/110/111/112) rodam em paralelo. Nome de cena prefixado com o código da tela pelo
 * mesmo motivo.
 *
 * Gravar: ./gradlew :android-app:recordRoborazziDevDebug
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = android.app.Application::class,
)
class PlanActive8dScreenshotTest {

    private companion object {
        const val SHUTTER_MILLIS = 600L
    }

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun planActiveLoading() = capture("8d-plano-ativo-carregando") {
        PlanActiveScreen(state = PlanActiveState(), onIntent = {})
    }

    @Test
    fun planActiveLoaded() = capture("8d-plano-ativo") {
        PlanActiveScreen(
            state = PlanActiveState(
                isLoading = false,
                planName = "Organizador",
                priceLabel = "R$ 17,91/mês",
                nextBillingLabel = "24 de agosto",
                groupsAvailableLabel = "3",
            ),
            onIntent = {},
        )
    }

    // Grupos disponíveis some quando o plano é ilimitado (usage.groupsLimit == null).
    @Test
    fun planActiveLoadedUnlimitedGroups() = capture("8d-plano-ativo-ilimitado") {
        PlanActiveScreen(
            state = PlanActiveState(
                isLoading = false,
                planName = "Ilimitado",
                priceLabel = "R$ 89,90/mês",
                nextBillingLabel = "24 de agosto",
                groupsAvailableLabel = "Ilimitado",
            ),
            onIntent = {},
        )
    }

    // Texto igual ao de `plan_active_error`, mas literal: o `Res` do módulo de
    // subscriptions é `internal` (só `access` é `publicResClass` hoje, pros testes
    // instrumentados que leem fonte/drawable herdados do design system apagado).
    @Test
    fun planActiveError() = capture("8d-plano-ativo-erro") {
        PlanActiveScreen(
            state = PlanActiveState(isLoading = false, error = UiText.Raw("Não foi possível carregar seu plano.")),
            onIntent = {},
        )
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { SaqzTheme { content() } }
        compose.mainClock.advanceTimeBy(SHUTTER_MILLIS)
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }
}
