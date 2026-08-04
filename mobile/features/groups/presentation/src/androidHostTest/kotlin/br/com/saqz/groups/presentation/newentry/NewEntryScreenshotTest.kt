package br.com.saqz.groups.presentation.newentry

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
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
class NewEntryScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun newEntryForm() {
        compose.setContent {
            SaqzTheme {
                NewEntryScreen(
                    state = NewEntryState(
                        date = "2026-08-04",
                        amountText = "80,00",
                        description = "Aluguel da quadra",
                        category = NewEntryCategory.Court,
                    ),
                    onBack = {},
                    onIntent = {},
                    modifier = Modifier.fillMaxSize().background(SaqzTheme.colors.background),
                )
            }
        }
        compose.onRoot().captureRoboImage("screenshots/vul-180/finance-new-entry.png")
    }
}
