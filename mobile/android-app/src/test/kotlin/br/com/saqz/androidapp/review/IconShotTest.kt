package br.com.saqz.androidapp.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzStepper
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
 * Captura de review do VUL-54: os 19 conceitos de `SaqzIcons` num tamanho em que o
 * glifo se lê, agora vindos da Lucide em vez dos paths transcritos do fluxo 10.
 *
 * O catálogo canônico mostra os ícones no tamanho de uso (22dp), pequeno demais
 * para comparar desenho; esta cena existe só para o olho do review.
 *
 * O stepper vai junto porque é onde `Plus`/`Minus` deixaram de ter traço próprio
 * (2,2) e passaram ao 2,0 uniforme da biblioteca — o par se confere aqui.
 *
 * Gravar: ./gradlew :android-app:recordRoborazziDevDebug
 * Saída:  android-app/screenshots/review/vul-54-icones.png
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = android.app.Application::class,
)
class IconShotTest {

    @get:Rule
    val compose = createComposeRule()

    private val concepts: List<Pair<String, ImageVector>> = listOf(
        "Home" to SaqzIcons.Home,
        "Calendar" to SaqzIcons.Calendar,
        "Users" to SaqzIcons.Users,
        "User" to SaqzIcons.User,
        "Bell" to SaqzIcons.Bell,
        "Search" to SaqzIcons.Search,
        "Megaphone" to SaqzIcons.Megaphone,
        "Pin" to SaqzIcons.Pin,
        "Mail" to SaqzIcons.Mail,
        "Lock" to SaqzIcons.Lock,
        "Trash" to SaqzIcons.Trash,
        "Eye" to SaqzIcons.Eye,
        "EyeOff" to SaqzIcons.EyeOff,
        "ChevronLeft" to SaqzIcons.ChevronLeft,
        "ChevronRight" to SaqzIcons.ChevronRight,
        "Close" to SaqzIcons.Close,
        "Plus" to SaqzIcons.Plus,
        "Minus" to SaqzIcons.Minus,
        "Check" to SaqzIcons.Check,
    )

    @Test
    fun iconSet() {
        compose.setContent {
            SaqzTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SaqzTheme.colors.background)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    concepts.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            row.forEach { (name, icon) ->
                                Column(
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    SaqzIcon(icon, size = 48.dp)
                                    Text(
                                        text = name,
                                        style = SaqzTheme.typography.caption,
                                        color = SaqzTheme.colors.textSecondary,
                                    )
                                }
                            }
                        }
                    }
                    SaqzStepper(value = 12, onValueChange = {}, label = "Vagas")
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.onRoot().captureRoboImage("screenshots/review/vul-54-icones.png")
    }
}
