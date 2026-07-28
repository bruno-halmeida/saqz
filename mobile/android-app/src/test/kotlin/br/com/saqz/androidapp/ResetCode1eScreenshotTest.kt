package br.com.saqz.androidapp

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import br.com.saqz.access.presentation.resetcode.ResetCodeState
import br.com.saqz.access.ui.ResetCodeScreen
import br.com.saqz.designsystem.theme.SaqzTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * As cenas das telas 1e, 1f e 1k (VUL-89) — a mesma rota `ResetCode` nos três arranjos do
 * export, e mais as recusas do gateway **sozinhas**, que é como elas chegam na vida real.
 *
 * Arquivo próprio, e não mais uma cena no `SaqzScreenshotTest`: aquele é o catálogo do
 * design system e tem dono (VUL-92). Nome de cena prefixado pelo código da tela.
 *
 * Gravar: `./gradlew :android-app:recordRoborazziDevDebug` — regrava o catálogo inteiro.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = android.app.Application::class,
)
class ResetCode1eScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aguardandoCodigo() = capture("1e-aguardando-codigo", ResetCodeState(email = EMAIL, resendSeconds = 42))

    @Test
    fun codigoReenviado() = capture(
        name = "1f-codigo-reenviado",
        state = ResetCodeState(email = EMAIL, resendSeconds = 59, resent = true),
    )

    // O pior caso do mockup: código errado e expirado ao mesmo tempo, em elementos
    // separados.
    @Test
    fun erroDoCodigo() = capture(
        name = "1k-erro-codigo",
        state = ResetCodeState(
            email = EMAIL,
            code = "1359",
            resendSeconds = 0,
            remainingAttempts = 2,
            expired = true,
        ),
    )

    // As quatro recusas do gateway, cada uma sozinha — é assim que elas chegam. Estado que
    // não está na cena não está sendo conferido (AGENTS.md §11).
    @Test
    fun codigoIncorreto() = capture(
        name = "1k-codigo-incorreto",
        state = ResetCodeState(email = EMAIL, code = "1359", resendSeconds = 12, remainingAttempts = 2),
    )

    @Test
    fun codigoExpirado() = capture(
        name = "1k-codigo-expirado",
        state = ResetCodeState(email = EMAIL, code = "1359", resendSeconds = 0, expired = true),
    )

    // Teto de tentativas: o código morreu do mesmo jeito, e o único caminho é pedir outro.
    @Test
    fun limiteDeTentativas() = capture(
        name = "1k-limite-tentativas",
        state = ResetCodeState(email = EMAIL, code = "1359", resendSeconds = 0, expired = true),
    )

    // Reenvio recusado por janela: sem alerta, o contador do servidor é a explicação.
    @Test
    fun reenvioLimitado() = capture(
        name = "1e-reenvio-limitado",
        state = ResetCodeState(email = EMAIL, resendSeconds = 25),
    )

    // Contador zerado: "Reenviar código" vira link azul e passa a ser tocável.
    @Test
    fun reenvioLiberado() = capture(
        name = "1e-reenvio-liberado",
        state = ResetCodeState(email = EMAIL, resendSeconds = 0),
    )

    // Pedido em voo: as duas ações travadas e o spinner no botão.
    @Test
    fun verificando() = capture(
        name = "1e-verificando",
        state = ResetCodeState(email = EMAIL, code = "1359", resendSeconds = 0, verifying = true),
    )

    private fun capture(name: String, state: ResetCodeState) {
        // autoAdvance desligado pelo mesmo motivo do catálogo: o quadro capturado precisa
        // ser sempre o mesmo. A .32s do alerta do 1f já terminou aos 600ms.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SaqzTheme {
                ResetCodeScreen(state = state, onIntent = {}, onBack = {}, onSignIn = {})
            }
        }
        // O foco é dado de verdade, com um toque no campo: é ele que acende a caixa da vez
        // — borda azul, halo e o cursor de 2×26. Sem foco o print sai com quatro caixas
        // iguais, e o que o export mostra nunca apareceria (lição da cena do VUL-77). Com
        // o pedido em voo o campo está travado e não aceita toque, e é assim mesmo.
        if (!state.busy) compose.onNode(hasSetTextAction()).performClick()
        compose.mainClock.advanceTimeBy(SHUTTER_MILLIS)
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    private companion object {
        const val EMAIL = "ana@exemplo.com"
        const val SHUTTER_MILLIS = 600L
    }
}
