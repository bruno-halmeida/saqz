package br.com.saqz.profile.presentation.edit.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.profile.domain.PhoneVisibility
import br.com.saqz.profile.fake.FakeProfileGateway
import br.com.saqz.profile.presentation.edit.EditProfileFieldError
import br.com.saqz.profile.presentation.edit.EditProfileState
import br.com.saqz.profile.presentation.screenshotImageLoader
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
class EditProfileScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun loadedDefault() = capture("7b-editar-dados") {
        EditProfileScreen(
            state = loadedState(),
            onIntent = {},
            onPickPhoto = {},
            onBack = {},
            imageLoader = it,
        )
    }

    @Test
    fun loadedEveryone() = capture("7b-editar-dados-everyone") {
        EditProfileScreen(
            state = loadedState().copy(
                form = loadedState().form.copy(phoneVisibility = PhoneVisibility.EVERYONE),
                originalForm = loadedState().originalForm.copy(phoneVisibility = PhoneVisibility.EVERYONE),
            ),
            onIntent = {},
            onPickPhoto = {},
            onBack = {},
            imageLoader = it,
        )
    }

    @Test
    fun requiredFieldErrors() = capture("7b-editar-dados-erros") {
        EditProfileScreen(
            state = loadedState().copy(
                form = loadedState().form.copy(displayName = "", phone = ""),
                fieldErrors = setOf(
                    EditProfileFieldError.NameRequired,
                    EditProfileFieldError.PhoneRequired,
                ),
            ),
            onIntent = {},
            onPickPhoto = {},
            onBack = {},
            imageLoader = it,
        )
    }

    @Test
    fun saving() = capture("7b-editar-dados-salvando") {
        EditProfileScreen(
            state = loadedState().copy(
                form = loadedState().form.copy(nickname = "Rafinha"),
                isSaving = true,
            ),
            onIntent = {},
            onPickPhoto = {},
            onBack = {},
            imageLoader = it,
        )
    }

    private fun capture(name: String, content: @Composable (ImageLoader) -> Unit) {
        compose.setContent {
            SaqzTheme {
                val context = LocalPlatformContext.current
                val imageLoader = remember(context) { screenshotImageLoader(context) }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SaqzTheme.colors.background),
                ) {
                    content(imageLoader)
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/vul-129/$name.png")
    }

    private fun loadedState() = EditProfileState.loaded(FakeProfileGateway().profile).let { state ->
        state.copy(
            form = state.form.copy(phone = "11987654321"),
            originalForm = state.originalForm.copy(phone = "11987654321"),
        )
    }
}
