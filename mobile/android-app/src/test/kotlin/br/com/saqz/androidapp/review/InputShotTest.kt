package br.com.saqz.androidapp.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.SaqzInput
import br.com.saqz.designsystem.SaqzInputKind
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
 * Captura de review do VUL-50: o toggle de senha nos dois estados do olho, agora
 * desenhado com `SaqzIcons.Eye` / `EyeOff` em vez dos drawables do Material.
 *
 * Gravar: ./gradlew :android-app:recordRoborazziDevDebug
 * Saída:  android-app/screenshots/review/vul-50-input.png
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = android.app.Application::class,
)
class InputShotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun passwordToggle() {
        compose.setContent {
            SaqzTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SaqzTheme.colors.background)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SaqzInput(
                        TextFieldValue("segredo"), {},
                        label = "Senha (oculta)", kind = SaqzInputKind.Password,
                    )
                    SaqzInput(
                        TextFieldValue("segredo"), {},
                        label = "Senha (revelada)", kind = SaqzInputKind.Password,
                    )
                }
            }
        }
        // `revealed` é estado interno: o segundo campo só mostra o EyeOff depois do clique.
        // O clique acontece com o relógio andando — parado, o gesto não chega a ser processado.
        compose.onAllNodesWithContentDescription("Mostrar senha")[1].performClick()
        compose.waitForIdle()
        // Só então o obturador congela, como no catálogo: o quadro é sempre o mesmo.
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(600L)
        compose.onRoot().captureRoboImage("screenshots/review/vul-50-input.png")
    }
}
