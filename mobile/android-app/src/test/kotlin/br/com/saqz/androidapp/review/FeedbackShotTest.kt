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
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzEmptyState
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzOfflineBanner
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
 * Captura de review do VUL-47 — banner offline navy e badge do EmptyState.
 *
 * Gravar:   ./gradlew :android-app:recordRoborazziDevDebug
 * Saída:    android-app/screenshots/review/vul-47-feedback.png
 *
 * Arquivo próprio, fora do catálogo canônico (`ds-*.png`), que é do VUL-43.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Application puro: o SaqzApplication real inicia Koin/Firebase, estáticos na JVM.
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = android.app.Application::class,
)
class FeedbackShotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun feedback() {
        // autoAdvance desligado: sem isso o arco indeterminado do spinner é fotografado
        // no início do ciclo (varredura ~0) e vira um ponto. 600ms cai no meio do arco.
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
                    SaqzOfflineBanner()
                    // Cartão em volta do EmptyState porque é assim que o fluxo 10m o
                    // mostra — sobre `surface`, onde o badge ice se separa do fundo.
                    SaqzCard(padded = false) {
                        SaqzEmptyState(
                            title = "Nenhum jogo marcado por enquanto.",
                            description = "Quando a galera marcar, ele aparece aqui.",
                            icon = SaqzIcons.Calendar,
                            action = "Criar jogo",
                            onAction = {},
                        )
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(600L)
        compose.onRoot().captureRoboImage("screenshots/review/vul-47-feedback.png")
    }
}
