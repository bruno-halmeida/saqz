package br.com.saqz.androidapp.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.SaqzSegmented
import br.com.saqz.designsystem.SaqzSwitch
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
 * Evidência visual do VUL-49 — segmented nas três posições e switch ligado/desligado.
 *
 * Gravar: ./gradlew :android-app:recordRoborazziDevDebug
 * Saída:  android-app/screenshots/review/vul-49-controls.png
 *
 * ponytail: captura de review, não golden de regressão. O catálogo canônico
 * (`screenshots/ds-*.png`) é do VUL-43 e não se toca daqui.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = android.app.Application::class,
)
class ControlsShotTest {

    private companion object {
        // Mesma fase de obturador do catálogo: o deslize do thumb (280ms) e o do
        // knob (180ms) já assentaram, então o quadro é o estado final.
        const val SHUTTER_MILLIS = 600L
    }

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun controls() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SaqzTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SaqzTheme.colors.background)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val options = listOf("Masculino", "Feminino", "Misto")
                    SaqzSegmented(options, selected = 0, onSelect = {})
                    SaqzSegmented(options, selected = 1, onSelect = {})
                    SaqzSegmented(options, selected = 2, onSelect = {})
                    SaqzSwitch(checked = true, onCheckedChange = {}, label = "Jogo toda semana")
                    SaqzSwitch(checked = false, onCheckedChange = {}, label = "Avisar por push")
                    SaqzSwitch(checked = false, onCheckedChange = {}, label = "Bloqueado", enabled = false)
                }
            }
        }
        compose.mainClock.advanceTimeBy(SHUTTER_MILLIS)
        compose.onRoot().captureRoboImage("screenshots/review/vul-49-controls.png")
    }
}
