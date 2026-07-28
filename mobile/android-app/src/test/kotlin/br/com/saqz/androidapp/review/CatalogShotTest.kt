package br.com.saqz.androidapp.review

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.composeapp.catalog.SaqzCatalogScreen
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
 * Captura de review do catálogo do fluxo 10 (VUL-51) — o que o revisor abre antes de ler o
 * diff.
 *
 * Gravar:   ./gradlew :android-app:recordRoborazziDevDebug
 * Saída:    android-app/screenshots/review/
 *
 * Mesmo padrão do `SaqzScreenshotTest`: Application puro (o real inicia Koin/Firebase, que
 * são estáticos na JVM), relógio congelado em 600ms para o arco do indeterminado abrir e as
 * transições de entrada terminarem.
 *
 * O catálogo é uma página rolável de seis seções, muito mais alta que um Pixel 7. Em vez de
 * espremer, cada seção sai no seu próprio PNG, com a altura do quadro sobrescrita por seção
 * — a largura e a densidade continuam as do Pixel 7, que é o que importa para conferir
 * medida contra o export.
 *
 * ponytail: PNG de review, não golden — não existe `verify` em cima destes arquivos. O
 * catálogo é ferramenta de dev; regressão visual dos componentes é o `SaqzScreenshotTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = android.app.Application::class,
)
class CatalogShotTest {

    private companion object {
        // Fase do obturador, igual à do SaqzScreenshotTest: o ciclo do
        // CircularProgressIndicator indeterminado é de 1332ms, e em 600ms o arco está aberto.
        const val SHUTTER_MILLIS = 600L
    }

    @get:Rule
    val compose = createComposeRule()

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { SaqzTheme { content() } }
        compose.mainClock.advanceTimeBy(SHUTTER_MILLIS)
        compose.onRoot().captureRoboImage("screenshots/review/$name.png")
    }

    // A tela inteira, do topo até onde o quadro alcança: é o enquadramento real de quem abre
    // o catálogo no aparelho.
    @Test
    fun catalog() = capture("vul-51-catalogo") {
        SaqzCatalogScreen(onBack = {})
    }

    // As seções seguintes precisam de quadro alto porque a página não rola numa captura.
    // "+" mescla com o @Config da classe: só a altura muda.
    @Test
    @Config(qualifiers = "+h3800dp")
    fun catalogFull() = capture("vul-51-catalogo-completo") {
        SaqzCatalogScreen(onBack = {})
    }
}
