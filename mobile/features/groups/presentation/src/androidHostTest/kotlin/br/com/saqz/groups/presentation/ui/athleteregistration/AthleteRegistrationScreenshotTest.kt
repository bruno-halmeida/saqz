package br.com.saqz.groups.presentation.ui.athleteregistration

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.designsystem.theme.SaqzTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = Application::class,
)
class AthleteRegistrationScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun courtWomen() = capture("3j-cadastro-atleta-feminino") {
        AthleteRegistrationScreen(AthleteRegistrationSamples.womenCourt, {}, {})
    }

    @Test
    fun courtMen() = capture("3j-cadastro-atleta-masculino") {
        AthleteRegistrationScreen(AthleteRegistrationSamples.menCourt, {}, {})
    }

    @Test
    fun courtMixed() = capture("3j-cadastro-atleta-misto") {
        AthleteRegistrationScreen(AthleteRegistrationSamples.mixedCourt, {}, {})
    }

    @Test
    fun beach() = capture("3k-cadastro-atleta-areia") {
        AthleteRegistrationScreen(AthleteRegistrationSamples.beach, {}, {})
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            SaqzTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SaqzTheme.colors.background),
                ) {
                    content()
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/vul-143/$name.png")
    }
}
