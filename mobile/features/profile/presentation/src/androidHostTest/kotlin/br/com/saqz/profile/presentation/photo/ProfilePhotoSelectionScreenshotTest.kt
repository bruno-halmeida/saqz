package br.com.saqz.profile.presentation.photo

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
class ProfilePhotoSelectionScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun selectionSheetWithPhoto() = capture("7b-folha-foto") {
        ProfilePhotoSelectionSheet(
            open = true,
            photoUrl = "/api/session/photo?v=preview",
            onClose = {},
            onTakePhoto = {},
            onChooseFromGallery = {},
            onRemovePhoto = {},
        )
    }

    @Test
    fun selectionSheetWithoutPhoto() = capture("7b-folha-foto-sem-foto") {
        ProfilePhotoSelectionSheet(
            open = true,
            photoUrl = null,
            onClose = {},
            onTakePhoto = {},
            onChooseFromGallery = {},
            onRemovePhoto = {},
        )
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            SaqzTheme {
                Box(Modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
                    content()
                }
            }
        }
        compose.onRoot().captureRoboImage("screenshots/vul-130/$name.png")
    }
}
