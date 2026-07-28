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
import br.com.saqz.designsystem.SaqzAttendance
import br.com.saqz.designsystem.SaqzAttendanceSelector
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
 * Captura de review do VUL-45 — os três sólidos do seletor de presença mais a
 * linha em repouso.
 *
 * Gravar: ./gradlew :android-app:recordRoborazziDevDebug
 * Saída:  android-app/screenshots/review/vul-45-attendance.png
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = android.app.Application::class,
)
class AttendanceSelectorShotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun attendance() {
        // Obturador congelado: com autoAdvance ligado o quadro sai no início da
        // transição de cor e o sólido aparece lavado.
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
                    SaqzAttendanceSelector(value = null, onSelect = {})
                    SaqzAttendanceSelector(value = SaqzAttendance.Going, onSelect = {})
                    SaqzAttendanceSelector(value = SaqzAttendance.Maybe, onSelect = {})
                    SaqzAttendanceSelector(value = SaqzAttendance.Out, onSelect = {})
                    // Prazo encerrado com a resposta já dada — o caso que o review do
                    // Codex pegou: o sólido segue legível, recuam as outras duas.
                    SaqzAttendanceSelector(
                        value = SaqzAttendance.Out,
                        onSelect = {},
                        enabled = false,
                    )
                }
            }
        }
        compose.mainClock.advanceTimeBy(600L)
        compose.onRoot().captureRoboImage("screenshots/review/vul-45-attendance.png")
    }
}
