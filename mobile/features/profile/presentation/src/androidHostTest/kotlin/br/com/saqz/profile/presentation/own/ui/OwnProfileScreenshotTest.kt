package br.com.saqz.profile.presentation.own.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import coil3.compose.LocalPlatformContext
import br.com.saqz.designsystem.SaqzBottomNav
import br.com.saqz.designsystem.SaqzNavItem
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.theme.SaqzTheme
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
class OwnProfileScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun filled() = capture("perfil-7a-cheio", OwnProfilePreviewData.filled)

    @Test
    fun noAttendance() = capture("perfil-7a-sem-presenca", OwnProfilePreviewData.noAttendance)

    @Test
    fun empty() = capture("perfil-7a-sem-grupo", OwnProfilePreviewData.empty)

    @Test
    fun loading() = capture("perfil-7a-carregando", OwnProfilePreviewData.loading)

    @Test
    fun failure() = capture("perfil-7a-falha-de-carga", OwnProfilePreviewData.error)

    private fun capture(name: String, state: br.com.saqz.profile.presentation.own.OwnProfileState) {
        compose.setContent {
            SaqzTheme {
                val context = LocalPlatformContext.current
                val imageLoader = remember(context) { screenshotImageLoader(context) }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SaqzTheme.colors.background),
                ) {
                    OwnProfileScreen(
                        state = state,
                        onIntent = {},
                        imageLoader = imageLoader,
                        modifier = Modifier.weight(1f),
                    )
                    SaqzBottomNav(
                        items = listOf(
                            SaqzNavItem("home", "Início", SaqzIcons.Home),
                            SaqzNavItem("games", "Jogos", SaqzIcons.Calendar),
                            SaqzNavItem("groups", "Grupos", SaqzIcons.Users),
                            SaqzNavItem("profile", "Perfil", SaqzIcons.User),
                        ),
                        activeId = "profile",
                        onSelect = {},
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/vul-128/$name.png")
    }
}
