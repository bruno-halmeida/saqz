package br.com.saqz.groups.presentation.ui.athleteregistration

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

    /** DEB-103: a folha da altura (seleção em 1,70 m, sem altura salva), normal e em celular pequeno (320x568dp). */
    @Test
    fun heightSheet() = captureHeightSheet("3j-altura-seletor")

    @Test
    @Config(sdk = [35], qualifiers = "w320dp-h568dp-normal-notlong-port-xhdpi", application = Application::class)
    fun heightSheetSmallPhone() = captureHeightSheet("3j-altura-seletor-small-phone")

    @Test
    fun heightFieldError() = capture("3j-altura-erro-no-campo", "deb-103") {
        AthleteRegistrationScreen(AthleteRegistrationSamples.womenCourt.copy(heightCm = 75, heightError = true), {}, {})
    }

    private fun captureHeightSheet(name: String) {
        compose.setContent {
            SaqzTheme {
                Box(Modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
                    AthleteRegistrationScreen(AthleteRegistrationSamples.womenCourt.copy(heightCm = null), {}, {})
                }
            }
        }
        compose.onNodeWithTag(AthleteRegistrationTags.Height).performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onRoot().captureRoboImage("screenshots/deb-103/$name.png")
    }

    private fun capture(name: String, folder: String = "vul-143", content: @Composable () -> Unit) {
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
        compose.onRoot().captureRoboImage("screenshots/$folder/$name.png")
    }
}
