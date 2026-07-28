package br.com.saqz.androidapp.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIconButton
import br.com.saqz.designsystem.SaqzIcons
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
 * Evidência visual do VUL-48 — o bloco 10d/10e do fluxo 10 recomposto.
 *
 * Gravar: ./gradlew :android-app:recordRoborazziDevDebug
 * Saída:  android-app/screenshots/review/vul-48-buttons.png
 *
 * Arquivo próprio, fora do catálogo canônico do VUL-43: seis tickets gravam em
 * paralelo e um PNG compartilhado seria conflito binário garantido.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = android.app.Application::class,
)
class ButtonShotTest {

    // Mesma fase de obturador do catálogo: com o relógio livre o indeterminado é
    // fotografado no início do ciclo de 1332ms e o spinner do loading vira um ponto.
    private companion object {
        const val SHUTTER_MILLIS = 600L
    }

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun buttons() {
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
                    SaqzButton("Confirmar presença", onClick = {}, fullWidth = true)
                    SaqzButton("Editar", onClick = {}, variant = SaqzButtonVariant.Secondary, fullWidth = true)
                    SaqzButton("Excluir grupo", onClick = {}, variant = SaqzButtonVariant.Danger, fullWidth = true)
                    SaqzButton("Cancelar", onClick = {}, variant = SaqzButtonVariant.Ghost, fullWidth = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SaqzButton("Criar jogo", onClick = {}, size = SaqzButtonSize.Sm)
                        SaqzButton("Criar grupo", onClick = {}, enabled = false)
                    }
                    SaqzButton("Criando grupo…", onClick = {}, loading = true, fullWidth = true)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SaqzIconButton({}, "Voltar") { SaqzIcon(SaqzIcons.ChevronLeft) }
                        SaqzIconButton({}, "Avisos novos", dot = true) { SaqzIcon(SaqzIcons.Bell) }
                        SaqzIconButton({}, "Buscar", soft = true) { SaqzIcon(SaqzIcons.Search) }
                        SaqzIconButton({}, "Criar jogo", filled = true) {
                            SaqzIcon(SaqzIcons.Plus, tint = SaqzTheme.colors.onPrimary)
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(SHUTTER_MILLIS)
        compose.onRoot().captureRoboImage("screenshots/review/vul-48-buttons.png")
    }
}
