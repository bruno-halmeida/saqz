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
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzGameSummaryCard
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
 * Captura de review do VUL-46 — a linha de estatística que substituiu os chips.
 *
 * Gravar:  ./gradlew :android-app:recordRoborazziDevDebug --tests '*GameSummaryCardShotTest'
 * Saída:   android-app/screenshots/review/vul-46-game-summary.png
 *
 * Arquivo próprio, fora do catálogo canônico (`ds-*.png`, do VUL-43), para não
 * disputar PNG com os outros tickets da onda.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = android.app.Application::class,
)
class GameSummaryCardShotTest {

    private companion object {
        const val SHUTTER_MILLIS = 600L
    }

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun gameSummaryCard() {
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
                    // Os mesmos números do bloco 10l do export.
                    SaqzGameSummaryCard(
                        eyebrow = "PRÓXIMO JOGO",
                        title = "Ter, 28/07 · 19h30",
                        venue = "CERET — Quadra 2",
                        address = "Tatuapé, São Paulo",
                        going = 9,
                        maybe = 2,
                        out = 1,
                    ) {
                        SaqzButton("Confirmar presença", onClick = {}, fullWidth = true)
                    }
                    // Sem contagem: a linha inteira some.
                    SaqzGameSummaryCard(
                        eyebrow = "PRÓXIMO JOGO",
                        title = "Sem jogo marcado",
                    )
                }
            }
        }
        compose.mainClock.advanceTimeBy(SHUTTER_MILLIS)
        compose.onRoot().captureRoboImage("screenshots/review/vul-46-game-summary.png")
    }
}
